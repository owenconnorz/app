package com.aioweb.app.data.nuvio

import android.util.Log
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Executes Nuvio JavaScript providers (the `getStreams` contract documented at
 * yoruix/nuvio-providers — and the D3adlyRocket / phisher98 forks).
 *
 * Backed by QuickJS (via [dokar3/quickjs-kt](https://github.com/dokar3/quickjs-kt))
 * — the same C engine used by Bun and many edge runtimes. We need it because
 * every modern Nuvio provider uses `async`/`await` and ES2018+ destructuring,
 * which Rhino can't parse.
 *
 * Exposed JS surface:
 *   • `console.{log,info,warn,error,debug}` — bridges to logcat
 *   • `fetch(url, opts)` — async OkHttp call, returns a Response with
 *     `.text()`, `.json()`, `.ok`, `.status`, `.headers.get(name)`.
 *   • `fetchv2(url, headers, method, body, encodeUrl, encoding)` — same OkHttp
 *     call but with the positional signature D3adlyRocket providers expect.
 *   • `module.exports`, `exports`, `global`, `globalThis` — CommonJS / browser
 *     compat bindings.
 */
object NuvioRuntime {
    private const val TAG = "NuvioRuntime"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Pre-amble JS injected before every provider script. Sets up the CommonJS
     * surface and the `fetch` / `fetchv2` Response wrappers (the actual HTTP
     * call is delegated to the Kotlin-side `_httpRaw` async binding).
     */
    private val PREAMBLE = """
        var module = { exports: {} };
        var exports = module.exports;
        // global / globalThis are both bound to the QuickJS global by default,
        // but some providers do `var global = ...` shadowing — guard against
        // that by re-exporting before they run.
        var globalThis = (typeof globalThis !== 'undefined') ? globalThis : this;
        var global = globalThis;
        var self = globalThis;

        function _wrapResp(raw, url) {
          return {
            ok: raw.status >= 200 && raw.status < 300,
            status: raw.status,
            url: raw.finalUrl || url,
            headers: { get: function (name) {
              if (!raw.headers) return null;
              var k = String(name).toLowerCase();
              for (var key in raw.headers) {
                if (String(key).toLowerCase() === k) return raw.headers[key];
              }
              return null;
            } },
            text: function () { return raw.text; },
            json: function () { try { return JSON.parse(raw.text); } catch (e) { return null; } },
          };
        }
        async function fetch(url, opts) {
          var raw = await _httpRaw({
            url: url,
            method: (opts && opts.method) ? opts.method : 'GET',
            headers: (opts && opts.headers) ? opts.headers : null,
            body: (opts && opts.body) ? String(opts.body) : null,
          });
          return _wrapResp(raw, url);
        }
        async function fetchv2(url, headers, method, body, encodeUrl, encoding) {
          var raw = await _httpRaw({
            url: url,
            method: method || 'GET',
            headers: headers || null,
            body: body == null ? null : String(body),
          });
          return _wrapResp(raw, url);
        }
    """.trimIndent()

    suspend fun runProvider(
        scriptText: String,
        tmdbId: String,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
    ): List<NuvioStream> {
        return try {
            quickJs(Dispatchers.IO) {
                // Console binding — bridges to logcat.
                define("console") {
                    listOf("log", "info", "warn", "error", "debug").forEach { level ->
                        function(level) { args ->
                            val msg = args.joinToString(" ") { it?.toString() ?: "null" }
                            when (level) {
                                "warn" -> Log.w("$TAG/$level", msg)
                                "error" -> Log.e("$TAG/$level", msg)
                                "debug" -> Log.d("$TAG/$level", msg)
                                else -> Log.i("$TAG/$level", msg)
                            }
                            null
                        }
                    }
                }

                // Low-level HTTP shim. JS-side fetch/fetchv2 wrap this in a
                // Response-like object with .text()/.json()/headers.get().
                asyncFunction("_httpRaw") { args ->
                    @Suppress("UNCHECKED_CAST")
                    val opts = args.firstOrNull() as? JsObject
                        ?: return@asyncFunction emptyMap<String, Any?>().toJsObject()
                    val url = opts["url"] as? String ?: return@asyncFunction errorResp("missing url")
                    val method = (opts["method"] as? String)?.uppercase() ?: "GET"
                    val headers = (opts["headers"] as? Map<*, *>)
                        ?.mapNotNull { (k, v) ->
                            val ks = k?.toString() ?: return@mapNotNull null
                            ks to (v?.toString() ?: "")
                        }
                        ?.toMap() ?: emptyMap()
                    val body = opts["body"] as? String

                    val builder = Request.Builder().url(url)
                    headers.forEach { (k, v) -> builder.header(k, v) }
                    builder.method(method, body?.toRequestBody())
                    try {
                        http.newCall(builder.build()).execute().use { resp ->
                            val text = resp.body?.string().orEmpty()
                            val hdrs = resp.headers.associate { (n, v) -> n to v }
                            mapOf(
                                "status" to resp.code.toLong(),
                                "finalUrl" to resp.request.url.toString(),
                                "headers" to hdrs.toJsObject(),
                                "text" to text,
                            ).toJsObject()
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "fetch($url) failed: ${e.message}")
                        errorResp(e.message ?: "fetch failed")
                    }
                }

                // Setup + provider script. We do these as one evaluate so
                // top-level `function getStreams(...)` and `module.exports`
                // are visible to our follow-up call.
                evaluate<Any?>(PREAMBLE + "\n" + scriptText)

                // Locate getStreams (top-level OR module.exports), call it,
                // and JSON-stringify the result. Because quickjs-kt's
                // `evaluate<T>` does NOT unwrap Promises (it returns the raw
                // Promise object), we cannot just return from an async IIFE.
                // Instead we bridge the result back through `_setResult`,
                // which lets the host coroutine await the JS event loop.
                var streamsJson = "[]"
                asyncFunction("_setResult") { args ->
                    streamsJson = (args.firstOrNull() as? String) ?: "[]"
                    null
                }
                val seasonJs = season?.toString() ?: "undefined"
                val episodeJs = episode?.toString() ?: "undefined"
                evaluate<Any?>(
                    """
                    (async function () {
                      try {
                        var fn = (typeof getStreams === 'function')
                          ? getStreams
                          : (module && module.exports && module.exports.getStreams);
                        if (typeof fn !== 'function') {
                          await _setResult(JSON.stringify({ error: 'no_getStreams' }));
                          return;
                        }
                        var arr = await fn(${jsString(tmdbId)}, ${jsString(mediaType)}, $seasonJs, $episodeJs);
                        await _setResult(JSON.stringify(arr || []));
                      } catch (e) {
                        await _setResult(JSON.stringify({ error: String(e && e.message || e) }));
                      }
                    })()
                    """.trimIndent(),
                )
                parseStreams(streamsJson)
            }
        } catch (e: QuickJsException) {
            Log.w(TAG, "QuickJS error: ${e.message}", e)
            emptyList()
        } catch (e: Throwable) {
            Log.w(TAG, "Provider crashed: ${e.message}", e)
            emptyList()
        }
    }

    private fun errorResp(msg: String): JsObject = mapOf(
        "status" to 0L,
        "finalUrl" to "",
        "headers" to emptyMap<String, Any?>().toJsObject(),
        "text" to "",
        "error" to msg,
    ).toJsObject()

    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Parse the JSON array produced by the provider's `getStreams`. */
    private fun parseStreams(json: String): List<NuvioStream> {
        if (json.isBlank() || json == "null") return emptyList()
        val element = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(json)
        }.getOrNull() ?: return emptyList()
        val arr = element as? kotlinx.serialization.json.JsonArray ?: run {
            // Provider returned an error object — log and bail.
            (element as? kotlinx.serialization.json.JsonObject)
                ?.get("error")
                ?.let { Log.w(TAG, "Provider returned error: $it") }
            return emptyList()
        }
        return arr.mapNotNull { item ->
            val obj = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val url = (obj["url"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val title = (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val quality = (obj["quality"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val headers = (obj["headers"] as? kotlinx.serialization.json.JsonObject)
                ?.mapValues { (_, v) ->
                    (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                }
            NuvioStream(name = name, title = title, url = url, quality = quality, headers = headers)
        }
    }
}

/** Convenience: kept as no-op since we now import the real `toJsObject` extension above. */

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this.isString || this.toString() != "null") this.content else null
