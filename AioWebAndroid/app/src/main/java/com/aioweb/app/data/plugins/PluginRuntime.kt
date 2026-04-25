package com.aioweb.app.data.plugins

import android.content.Context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Runtime for executing CloudStream `.cs3` plugins.
 *
 * Pipeline:
 *   1. The `.cs3` file is a renamed JAR/DEX (zip with classes.dex inside)
 *   2. Use [DexClassLoader] with our app's classloader as parent so plugin classes
 *      can resolve `com.lagradost.cloudstream3.MainAPI` etc. (we ship those stubs)
 *   3. Find the class annotated with `@CloudstreamPlugin` via the manifest entry
 *      `Plugin-Class` in `META-INF/MANIFEST.MF` (newer plugins) OR by scanning
 *      classes for a `Plugin` subclass (legacy)
 *   4. Instantiate it, call `load(context)` so the plugin registers its [MainAPI]
 *   5. Cache the loaded [MainAPI]s so future calls are instant
 */
object PluginRuntime {

    private data class LoadedPlugin(val plugin: Plugin, val apis: List<MainAPI>)
    private val cache = mutableMapOf<String, LoadedPlugin>()
    private val lastErrors = mutableMapOf<String, String>()

    fun lastErrorFor(filePath: String): String? = lastErrors[filePath]

    suspend fun load(context: Context, filePath: String): List<MainAPI> = withContext(Dispatchers.IO) {
        cache[filePath]?.let { return@withContext it.apis }
        try {
            val file = File(filePath)
            if (!file.exists()) error("Plugin file missing: $filePath")
            val optimizedDir = File(context.codeCacheDir, "plugins-opt").apply { mkdirs() }
            val loader = DexClassLoader(
                file.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader,
            )
            val pluginClassName = readPluginClassName(file)
                ?: error("Could not find plugin class — no `Plugin-Class` in MANIFEST.MF")
            val klass = loader.loadClass(pluginClassName)
            val instance = klass.getDeclaredConstructor().newInstance() as? Plugin
                ?: error("Class `$pluginClassName` is not a subclass of `Plugin`")
            // Plugins usually invoke registerMainAPI from load(context).
            instance.beforeLoad()
            instance.load(context)
            instance.afterLoad()
            val loaded = LoadedPlugin(instance, instance.apis.toList())
            cache[filePath] = loaded
            lastErrors.remove(filePath)
            loaded.apis
        } catch (e: Throwable) {
            lastErrors[filePath] = "${e::class.simpleName}: ${e.message}"
            emptyList()
        }
    }

    private fun readPluginClassName(file: File): String? {
        // 1. Look in MANIFEST.MF for `Plugin-Class:` attribute.
        return try {
            ZipFile(file).use { zf ->
                val entry = zf.getEntry("META-INF/MANIFEST.MF") ?: return null
                zf.getInputStream(entry).bufferedReader().useLines { lines ->
                    lines.firstOrNull { it.startsWith("Plugin-Class:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                }
            }
        } catch (_: Exception) { null }
    }

    suspend fun search(context: Context, filePath: String, query: String): List<SearchResponse> {
        val apis = load(context, filePath)
        return apis.flatMap { api ->
            try { api.search(query).orEmpty() } catch (_: Throwable) { emptyList() }
        }
    }

    suspend fun home(context: Context, filePath: String): List<Pair<String, List<SearchResponse>>> {
        val apis = load(context, filePath)
        val out = mutableListOf<Pair<String, List<SearchResponse>>>()
        apis.forEach { api ->
            api.mainPage.forEach { req: MainPageRequest ->
                try {
                    val page = api.getMainPage(1, req)
                    page?.items?.forEach { hpl -> out += hpl.name to hpl.list }
                } catch (_: Throwable) { /* ignore individual section failures */ }
            }
        }
        return out
    }

    suspend fun loadDetail(context: Context, filePath: String, url: String): LoadResponse? {
        val apis = load(context, filePath)
        return apis.firstNotNullOfOrNull { api ->
            try { api.load(url) } catch (_: Throwable) { null }
        }
    }

    fun clear(filePath: String) {
        cache.remove(filePath)
        lastErrors.remove(filePath)
    }
}
