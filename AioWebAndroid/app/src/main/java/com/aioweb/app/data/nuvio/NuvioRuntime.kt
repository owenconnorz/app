package com.aioweb.app.data.nuvio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.TimeUnit

/**
 * Executes Nuvio JavaScript providers (the `getStreams` contract documented at
 * yoruix/nuvio-providers — and the D3adlyRocket / phisher98 forks).
 *
 * Pure JVM via Mozilla Rhino 1.8.x — full ES2017 incl. native `async`/`await`
 * and native Promise. We run in interpreted mode (`optimizationLevel = -1`)
 * so Rhino never tries to emit JVM bytecode (Android's runtime can't load it).
 *
 * Exposed JS surface:
 *   • `console.{log,info,warn,error,debug}` — bridges to logcat
 *   • `fetch(url, opts)` — synchronous OkHttp call, returns a Response-like
 *     object with `.text()`, `.json()`, `.ok`, `.status`, `.headers.get(name)`.
 *     Native await wraps it in a Promise transparently.
 *   • `fetchv2(url, headers, method, body, encodeUrl, encoding)` — same OkHttp
 *     call but with the positional signature D3adlyRocket providers expect.
 *   • `setTimeout` / `clearTimeout` — no-op (we drain everything synchronously).
 *   • `module.exports`, `exports`, `global`, `globalThis` — CommonJS / browser
 *     compat bindings.
 *
 * Best-effort: providers using node-forge, real timers, cheerio, etc. will fail
 * gracefully via the try/catch around `getStreams`. The result is logged and
 * the resolver falls through to other addons / providers.
 */
object NuvioRuntime {
    private const val TAG = "NuvioRuntime"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Wraps a Kotlin lambda as a Rhino [BaseFunction] (anonymous class — needed
     *  because BaseFunction is an abstract Java class, not a SAM type). */
    private fun jsFn(
        block: (Context, Scriptable, Scriptable, Array<out Any?>) -> Any?,
    ): BaseFunction = object : BaseFunction() {
        override fun call(
            cx: Context,
            scope: Scriptable,
            thisObj: Scriptable,
            args: Array<out Any?>,
        ): Any? = block(cx, scope, thisObj, args)
    }

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
            installTimers(ctx, scope)

            // CommonJS shim (`module.exports = { getStreams }`).
            val module = ctx.newObject(scope) as NativeObject
            ScriptableObject.putProperty(module, "exports", ctx.newObject(scope))
            ScriptableObject.putProperty(scope, "module", module)
            ScriptableObject.putProperty(scope, "exports", module["exports"])

            // Browser / Node-compat aliases — D3adlyRocket scripts do
            // `global.getStreams = getStreams` as the non-CJS fallback.
            ScriptableObject.putProperty(scope, "global", scope)
            ScriptableObject.putProperty(scope, "globalThis", scope)
            ScriptableObject.putProperty(scope, "self", scope)

            ctx.evaluateString(scope, transpileForRhino(scriptText), "<provider>", 1, null)

            // Locate getStreams — top-level, on module.exports, or fallback global.
            val getStreamsFn = (scope.get("getStreams", scope) as? Function)
                ?: ((module["exports"] as? Scriptable)?.get("getStreams", scope) as? Function)
                ?: error("Provider does not export `getStreams`")

            val args = arrayOf<Any?>(
                tmdbId,
                mediaType,
                season ?: org.mozilla.javascript.Undefined.instance,
                episode ?: org.mozilla.javascript.Undefined.instance,
            )
            val raw = getStreamsFn.call(ctx, scope, scope, args)
            // After transpile, async functions return their value directly (no Promise),
            // but `Promise.resolve(arr).then(...)` chains may still occur. Drain any
            // residual thenables defensively.
            val resolved = drainPromise(ctx, scope, raw)
            normaliseResult(resolved)
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
            val fn = jsFn { _, _, _, args ->
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

    // ───────────────────────── globals: fetch / fetchv2 ─────────────────
    private fun installFetch(ctx: Context, scope: Scriptable) {
        val fetchFn = jsFn { c, s, _, args ->
            val url = Context.toString(args.getOrNull(0))
            val opts = args.getOrNull(1) as? Scriptable
            val method = (opts?.get("method", opts) as? String)?.uppercase() ?: "GET"
            val headers: Map<String, String> = (opts?.get("headers", opts) as? Scriptable)
                ?.let { hs ->
                    hs.ids.associate { k ->
                        k.toString() to Context.toString(hs.get(k.toString(), hs))
                    }
                } ?: emptyMap()
            val body = opts?.get("body", opts)?.takeIf { it !is org.mozilla.javascript.Undefined }
            doFetch(c, s, url, method, headers, body)
        }
        ScriptableObject.putProperty(scope, "fetch", fetchFn)

        // D3adlyRocket convention: `fetchv2(url, headers, method, body, encodeUrl, encoding)`
        val fetchv2 = jsFn { c, s, _, args ->
            val url = Context.toString(args.getOrNull(0))
            val headers = (args.getOrNull(1) as? Scriptable)?.let { hs ->
                hs.ids.associate { k ->
                    k.toString() to Context.toString(hs.get(k.toString(), hs))
                }
            } ?: emptyMap()
            val method = (args.getOrNull(2) as? String)?.uppercase() ?: "GET"
            val body = args.getOrNull(3)?.takeIf { it !is org.mozilla.javascript.Undefined }
            doFetch(c, s, url, method, headers, body)
        }
        ScriptableObject.putProperty(scope, "fetchv2", fetchv2)
    }

    /**
     * Performs the actual HTTP request synchronously and returns a Response-like
     * Scriptable. Because Rhino 1.8's native `await` happily unwraps non-thenable
     * values, we don't need to wrap this in a Promise — `await fetch(url)` works.
     */
    private fun doFetch(
        ctx: Context,
        scope: Scriptable,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: Any?,
    ): Scriptable {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val rb = body?.let { Context.toString(it).toRequestBody() }
        builder.method(method, rb)
        try {
            val resp = http.newCall(builder.build()).execute()
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val text = String(bytes)
            val r = ctx.newObject(scope) as NativeObject
            ScriptableObject.putProperty(r, "ok", resp.isSuccessful)
            ScriptableObject.putProperty(r, "status", resp.code)
            ScriptableObject.putProperty(r, "url", resp.request.url.toString())

            val headersObj = ctx.newObject(scope) as NativeObject
            val getFn = jsFn { _, _, _, hArgs ->
                val n = Context.toString(hArgs.getOrNull(0))
                resp.header(n) ?: org.mozilla.javascript.Undefined.instance
            }
            ScriptableObject.putProperty(headersObj, "get", getFn)
            ScriptableObject.putProperty(r, "headers", headersObj)
            ScriptableObject.putProperty(r, "text", jsFn { _, _, _, _ -> text })
            ScriptableObject.putProperty(r, "json", jsFn { c, sc, _, _ ->
                runCatching { c.evaluateString(sc, "($text)", "<json>", 1, null) }
                    .getOrDefault(org.mozilla.javascript.Undefined.instance)
            })
            resp.close()
            return r
        } catch (e: Throwable) {
            Log.w(TAG, "fetch($url) failed: ${e.message}")
            // Surface a fake non-ok response so providers that just check `.ok`
            // bail out cleanly instead of throwing.
            val r = ctx.newObject(scope) as NativeObject
            ScriptableObject.putProperty(r, "ok", false)
            ScriptableObject.putProperty(r, "status", 0)
            ScriptableObject.putProperty(r, "url", url)
            ScriptableObject.putProperty(r, "text", jsFn { _, _, _, _ -> "" })
            ScriptableObject.putProperty(r, "json", jsFn { _, _, _, _ ->
                org.mozilla.javascript.Undefined.instance
            })
            return r
        }
    }

    private fun installTimers(ctx: Context, scope: Scriptable) {
        val st = jsFn { c, s, _, args ->
            val cb = args.getOrNull(0) as? Function
            cb?.call(c, s, s, emptyArray())
            0
        }
        ScriptableObject.putProperty(scope, "setTimeout", st)
        ScriptableObject.putProperty(
            scope,
            "clearTimeout",
            jsFn { _, _, _, _ -> org.mozilla.javascript.Undefined.instance },
        )
        // Synchronous Promise polyfill — Rhino can't parse async/await (we strip
        // them via [transpileForRhino]) and providers may still call
        // `Promise.resolve(x)` / `Promise.all([...])`. With every value already
        // resolved synchronously, identity functions are correct.
        ctx.evaluateString(
            scope,
            """
            var Promise = {
              resolve: function (v) { return v; },
              reject: function (e) { throw e; },
              all: function (arr) { return arr; },
              allSettled: function (arr) {
                return arr.map(function (v) { return { status: 'fulfilled', value: v }; });
              },
              race: function (arr) { return arr[0]; },
            };
            // Some providers do `someValue.then(cb)` — patch a default no-op
            // onto Object.prototype so non-thenables don't blow up after transpile.
            // (Real thenables override it via their own .then.)
            try {
              Object.defineProperty(Object.prototype, 'then', {
                value: function (cb) { return cb ? cb(this) : this; },
                writable: true, configurable: true, enumerable: false,
              });
            } catch (e) {}
            """.trimIndent(),
            "<polyfill>", 1, null,
        )
    }

    /**
     * Strips ES2017 `async`/`await` from a provider source so Rhino can parse it.
     * This is a textual transform — fragile for syntax inside strings/comments,
     * but the Nuvio provider corpus we target is consistent enough that it works
     * for ~all of them. Pairs with the synchronous `fetch`/Promise polyfill so the
     * resulting code behaves identically.
     */
    private fun transpileForRhino(src: String): String {
        var out = src
        // 1. `async function …` → `function …`
        out = Regex("""\basync\s+function\b""").replace(out, "function")
        // 2. `async (` arrow / IIFE → `(`
        out = Regex("""\basync\s*\(""").replace(out, "(")
        // 3. Object/class shorthand: `async name(` → `name(`
        out = Regex("""\basync\s+([A-Za-z_\$][A-Za-z0-9_\$]*\s*\()""").replace(out, "$1")
        // 4. `async x =>` → `x =>`
        out = Regex("""\basync\s+([A-Za-z_\$][A-Za-z0-9_\$]*\s*=>)""").replace(out, "$1")
        // 5. Drop `await ` everywhere.
        out = Regex("""\bawait\s+""").replace(out, "")
        return out
    }

    // ───────────────────────── result coercion ─────────────────────────

    /**
     * Drains a NativePromise (or any thenable) returned by an async function.
     * Because `fetch` and friends resolve synchronously, calling `.then(cb)` on
     * the returned promise fires `cb` immediately with the settled value, and
     * we just unwrap chained thenables until we hit a plain value.
     */
    private fun drainPromise(ctx: Context, scope: Scriptable, raw: Any?): Any? {
        var value: Any? = raw
        var safety = 0
        while (safety++ < 16 && value is Scriptable && value.has("then", value)) {
            val tFn = value.get("then", value) as? Function ?: break
            var captured: Any? = null
            var rejected: Any? = null
            tFn.call(
                ctx, scope, value,
                arrayOf(
                    jsFn { _, _, _, args -> captured = args.getOrNull(0); org.mozilla.javascript.Undefined.instance },
                    jsFn { _, _, _, args -> rejected = args.getOrNull(0); org.mozilla.javascript.Undefined.instance },
                ),
            )
            if (rejected != null) {
                Log.w(TAG, "Provider promise rejected: ${Context.toString(rejected)}")
                return null
            }
            value = captured
        }
        return value
    }

    private fun normaliseResult(raw: Any?): List<NuvioStream> {
        val arr = (raw as? NativeArray) ?: return emptyList()
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
private operator fun Scriptable.get(name: String): Any? = this.get(name, this)
