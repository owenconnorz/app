package com.aioweb.app.data.nuvio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes Nuvio JavaScript providers (the `getStreams` contract documented at
 * yoruix/nuvio-providers). Pure JVM via Mozilla Rhino — no native libs.
 *
 * **Design summary:**
 *   • Each provider runs in a fresh sandboxed scope (no shared globals).
 *   • Bridges installed: `console.{log,info,warn,error,debug}`, `fetch(url, opts)`
 *     with sync OkHttp under the hood, `Promise.resolve(value)` shim returning
 *     a thenable that pipes synchronously, plus `setTimeout` no-op.
 *   • Hermes-transpiled async/await (regenerator) → these providers expect
 *     `Promise.resolve(...).then(cb)` semantics. Our shim calls the callback
 *     immediately, so the generator drains in one synchronous flush.
 *   • Optimization level forced to -1 — Rhino's bytecode compiler hits the
 *     dex method-count limit on Android.
 *
 * Best-effort: complex providers using node-forge, cheerio, real Promises, or
 * timers will fail gracefully via the try/catch around `getStreams`. The result
 * is logged and the resolver falls through to other addons / providers.
 */
object NuvioRuntime {
    private const val TAG = "NuvioRuntime"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun runProvider(
        scriptText: String,
        tmdbId: String,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
    ): List<NuvioStream> = withContext(Dispatchers.IO) {
        val ctx = Context.enter()
        try {
            ctx.optimizationLevel = -1
            ctx.languageVersion = Context.VERSION_ES6
            val scope: Scriptable = ctx.initStandardObjects()

            installConsole(ctx, scope)
            installFetch(ctx, scope)
            installPromiseShim(ctx, scope)
            installTimers(ctx, scope)
            // CommonJS-style `module.exports` so providers using `module.exports = { getStreams }` work.
            val module = ctx.newObject(scope) as NativeObject
            ScriptableObject.putProperty(module, "exports", ctx.newObject(scope))
            ScriptableObject.putProperty(scope, "module", module)
            ScriptableObject.putProperty(scope, "exports", module["exports"])

            ctx.evaluateString(scope, scriptText, "<provider>", 1, null)

            // Locate getStreams either as a top-level binding or on module.exports.
            val getStreamsFn = scope.get("getStreams", scope)
                ?.takeIf { it is Function } as? Function
                ?: ((module["exports"] as? Scriptable)?.get("getStreams", scope)
                    as? Function)
                ?: error("Provider does not export `getStreams`")

            val args = arrayOf<Any?>(
                tmdbId,
                mediaType,
                season ?: org.mozilla.javascript.Undefined.instance,
                episode ?: org.mozilla.javascript.Undefined.instance,
            )
            val raw = getStreamsFn.call(ctx, scope, scope, args)
            normaliseResult(raw)
        } catch (e: Throwable) {
            Log.w(TAG, "Provider crashed: ${e.message}", e)
            emptyList()
        } finally {
            Context.exit()
        }
    }

    // ───────────────────────── globals: console ─────────────────────────
    private fun installConsole(ctx: Context, scope: Scriptable) {
        val console = ctx.newObject(scope) as NativeObject
        listOf("log", "info", "warn", "error", "debug").forEach { level ->
            val fn = org.mozilla.javascript.BaseFunction { _, _, _, args ->
                val msg = args.joinToString(" ") { Context.toString(it) }
                Log.println(when (level) {
                    "warn" -> Log.WARN; "error" -> Log.ERROR; "debug" -> Log.DEBUG
                    else -> Log.INFO
                }, "$TAG/$level", msg)
                org.mozilla.javascript.Undefined.instance
            }
            ScriptableObject.putProperty(console, level, fn)
        }
        ScriptableObject.putProperty(scope, "console", console)
    }

    // ───────────────────────── globals: fetch ─────────────────────────
    /**
     * Synchronous fetch that returns an object exposing `.text()`, `.json()`,
     * `.ok`, `.status`, `.headers.get(name)`. Wraps it in our Promise shim so
     * `await fetch(url)` just works under the regenerator transpile.
     */
    private fun installFetch(ctx: Context, scope: Scriptable) {
        val fetchFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val url = Context.toString(args.getOrNull(0))
            val opts = args.getOrNull(1) as? Scriptable
            val method = (opts?.get("method", opts) as? String)?.uppercase() ?: "GET"
            val builder = Request.Builder().url(url)
            // headers
            (opts?.get("headers", opts) as? Scriptable)?.let { hs ->
                hs.ids.forEach { k ->
                    val name = k.toString()
                    builder.header(name, Context.toString(hs.get(name, hs)))
                }
            }
            // body
            val body = opts?.get("body", opts)?.takeIf { it !is org.mozilla.javascript.Undefined }
            val rb = body?.let {
                val s2 = Context.toString(it)
                s2.toRequestBody()
            }
            builder.method(method, rb)
            try {
                val resp = http.newCall(builder.build()).execute()
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val text = String(bytes)
                val responseObj = c.newObject(s) as NativeObject
                ScriptableObject.putProperty(responseObj, "ok", resp.isSuccessful)
                ScriptableObject.putProperty(responseObj, "status", resp.code)
                ScriptableObject.putProperty(responseObj, "url", resp.request.url.toString())
                // headers.get(name)
                val headersObj = c.newObject(s) as NativeObject
                val getFn = org.mozilla.javascript.BaseFunction { _, _, _, hArgs ->
                    val n = Context.toString(hArgs.getOrNull(0))
                    resp.header(n) ?: org.mozilla.javascript.Undefined.instance
                }
                ScriptableObject.putProperty(headersObj, "get", getFn)
                ScriptableObject.putProperty(responseObj, "headers", headersObj)
                ScriptableObject.putProperty(responseObj, "text",
                    org.mozilla.javascript.BaseFunction { _, _, _, _ -> resolved(c, s, text) })
                ScriptableObject.putProperty(responseObj, "json",
                    org.mozilla.javascript.BaseFunction { _, _, _, _ ->
                        val parsed = ctx.evaluateString(s, "($text)", "<json>", 1, null)
                        resolved(c, s, parsed)
                    })
                resp.close()
                resolved(c, s, responseObj)
            } catch (e: Throwable) {
                Log.w(TAG, "fetch($url) failed: ${e.message}")
                rejected(c, s, e.message ?: "fetch failed")
            }
        }
        ScriptableObject.putProperty(scope, "fetch", fetchFn)
    }

    // ───────────────────────── Promise shim ─────────────────────────
    /**
     * Tiny Promise-like with synchronous .then/.catch chaining. Sufficient
     * for regenerator-transpiled async functions: each `await` calls .then,
     * we invoke the callback immediately with the resolved value.
     */
    private fun installPromiseShim(ctx: Context, scope: Scriptable) {
        val promiseObj = ctx.newObject(scope) as NativeObject
        val resolveFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            resolved(c, s, args.getOrNull(0))
        }
        val rejectFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            rejected(c, s, Context.toString(args.getOrNull(0)))
        }
        val allFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val arr = args.getOrNull(0) as? NativeArray
            val out = NativeArray(arr?.length ?: 0)
            arr?.indices?.forEach { i ->
                val item = arr[i]
                val v = if (item is Scriptable && item.has("then", item)) {
                    var captured: Any? = null
                    val tFn = (item.get("then", item) as? Function) ?: return@forEach
                    tFn.call(c, s, item, arrayOf(
                        org.mozilla.javascript.BaseFunction { _, _, _, a ->
                            captured = a.getOrNull(0); org.mozilla.javascript.Undefined.instance
                        },
                    ))
                    captured
                } else item
                ScriptableObject.putProperty(out, i, v)
            }
            resolved(c, s, out)
        }
        ScriptableObject.putProperty(promiseObj, "resolve", resolveFn)
        ScriptableObject.putProperty(promiseObj, "reject", rejectFn)
        ScriptableObject.putProperty(promiseObj, "all", allFn)
        ScriptableObject.putProperty(scope, "Promise", promiseObj)
    }

    private fun installTimers(ctx: Context, scope: Scriptable) {
        // No-op timers — providers that gate on setTimeout will just call the cb immediately.
        val st = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val cb = args.getOrNull(0) as? Function
            cb?.call(c, s, s, emptyArray())
            0
        }
        ScriptableObject.putProperty(scope, "setTimeout", st)
        ScriptableObject.putProperty(scope, "clearTimeout",
            org.mozilla.javascript.BaseFunction { _, _, _, _ -> org.mozilla.javascript.Undefined.instance })
    }

    private fun resolved(ctx: Context, scope: Scriptable, value: Any?): Scriptable {
        val obj = ctx.newObject(scope) as NativeObject
        val thenFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val cb = args.getOrNull(0) as? Function
            val mapped = cb?.call(c, s, s, arrayOf(value)) ?: value
            // If the mapped value is itself a thenable, return as-is so the chain continues.
            if (mapped is Scriptable && mapped.has("then", mapped)) mapped
            else resolved(c, s, mapped)
        }
        val catchFn = org.mozilla.javascript.BaseFunction { c, s, _, _ ->
            resolved(c, s, value)
        }
        ScriptableObject.putProperty(obj, "then", thenFn)
        ScriptableObject.putProperty(obj, "catch", catchFn)
        return obj
    }

    private fun rejected(ctx: Context, scope: Scriptable, reason: String): Scriptable {
        val obj = ctx.newObject(scope) as NativeObject
        val thenFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val errCb = args.getOrNull(1) as? Function
            if (errCb != null) {
                val mapped = errCb.call(c, s, s, arrayOf(reason))
                resolved(c, s, mapped)
            } else rejected(c, s, reason)
        }
        val catchFn = org.mozilla.javascript.BaseFunction { c, s, _, args ->
            val cb = args.getOrNull(0) as? Function
            val mapped = cb?.call(c, s, s, arrayOf(reason)) ?: org.mozilla.javascript.Undefined.instance
            resolved(c, s, mapped)
        }
        ScriptableObject.putProperty(obj, "then", thenFn)
        ScriptableObject.putProperty(obj, "catch", catchFn)
        return obj
    }

    // ───────────────────────── result coercion ─────────────────────────
    private fun normaliseResult(raw: Any?): List<NuvioStream> {
        // If the call returned a thenable (Promise), drain it.
        var value: Any? = raw
        var safety = 0
        while (value is Scriptable && value.has("then", value) && safety++ < 8) {
            var captured: Any? = null
            val tFn = value.get("then", value) as? Function ?: break
            tFn.call(Context.getCurrentContext(), value, value, arrayOf(
                org.mozilla.javascript.BaseFunction { _, _, _, args ->
                    captured = args.getOrNull(0); org.mozilla.javascript.Undefined.instance
                },
            ))
            value = captured
        }
        val arr = (value as? NativeArray) ?: return emptyList()
        val out = mutableListOf<NuvioStream>()
        for (i in 0 until arr.length.toInt()) {
            val item = arr[i] as? Scriptable ?: continue
            val url = (item["url"] as? String)?.takeIf { it.isNotBlank() } ?: continue
            val name = item["name"] as? String
            val title = item["title"] as? String
            val quality = item["quality"] as? String
            val headers = (item["headers"] as? Scriptable)?.let { h ->
                h.ids.associate { k ->
                    k.toString() to Context.toString(h.get(k.toString(), h))
                }
            }
            out += NuvioStream(name = name, title = title, url = url, quality = quality, headers = headers)
        }
        return out
    }
}

private operator fun NativeArray.get(index: Int): Any? = this.get(index, this)
private val NativeArray.indices: IntRange get() = 0 until length.toInt()
private operator fun Scriptable.get(name: String): Any? = this.get(name, this)
