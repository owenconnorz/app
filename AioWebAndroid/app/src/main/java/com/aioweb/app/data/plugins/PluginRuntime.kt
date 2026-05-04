package com.aioweb.app.data.plugins

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ExtractorLink
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PluginRuntime {

private const val TAG = "PluginRuntime"

private data class LoadedPlugin(
    val plugin: Plugin,
    val apis: List<MainAPI>
)

private val cache = mutableMapOf<String, LoadedPlugin>()
private val lastErrors = mutableMapOf<String, String>()

fun lastErrorFor(path: String): String? = lastErrors[path]

// ==============================
// LOAD PLUGIN
// ==============================
suspend fun load(
    context: Context,
    filePath: String
): List<MainAPI> = withContext(Dispatchers.IO) {

    cache[filePath]?.let { return@withContext it.apis }

    try {
        val src = File(filePath)
        if (!src.exists()) {
            error("Plugin file not found: $filePath")
        }

        // Android 14 fix: copy to read-only dir
        val roDir = File(context.codeCacheDir, "plugins-ro").apply { mkdirs() }
        val roFile = File(roDir, src.name)

        if (!roFile.exists() || roFile.length() != src.length()) {
            src.copyTo(roFile, overwrite = true)
        }
        roFile.setReadOnly()

        val optDir = File(context.codeCacheDir, "plugins-opt").apply { mkdirs() }

        val loader = DexClassLoader(
            roFile.absolutePath,
            optDir.absolutePath,
            null,
            context.classLoader
        )

        val pluginClassName = findPluginClass(loader, roFile.absolutePath)
            ?: error("No Plugin class found in $filePath")

        val clazz = loader.loadClass(pluginClassName)

        val plugin = clazz.getDeclaredConstructor().newInstance() as? Plugin
            ?: error("Class is not Plugin")

        plugin.load(context)

        val apis = plugin.apis.toList()

        cache[filePath] = LoadedPlugin(plugin, apis)
        lastErrors.remove(filePath)

        return@withContext apis

    } catch (e: Throwable) {
        Log.e(TAG, "Plugin load failed", e)
        lastErrors[filePath] = "${e::class.simpleName}: ${e.message}"
        return@withContext emptyList()
    }
}

// ==============================
// FIND PLUGIN CLASS (FIXED)
// ==============================
private fun findPluginClass(
    loader: DexClassLoader,
    path: String
): String? {
    return try {
        val dex = DexFile(path)
        val entries = dex.entries()

        while (entries.hasMoreElements()) {
            val name = entries.nextElement()

            val clazz = runCatching { loader.loadClass(name) }.getOrNull() ?: continue

            if (Plugin::class.java.isAssignableFrom(clazz)
                && !clazz.isInterface
                && !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)
            ) {
                return name
            }
        }

        null
    } catch (e: Throwable) {
        Log.e(TAG, "Dex scan failed", e)
        null
    }
}

// ==============================
// HOME
// ==============================
suspend fun home(
    context: Context,
    filePath: String
): List<Pair<String, List<SearchResponse>>> {

    val apis = load(context, filePath)
    val result = mutableListOf<Pair<String, List<SearchResponse>>>()

    apis.forEach { api ->
        val requests = if (api.mainPage.isNotEmpty()) api.mainPage
        else listOf(MainPageRequest(api.name, ""))

        requests.forEach { req ->
            try {
                val page = api.getMainPage(1, req)

                page?.items?.forEach { section ->
                    if (section.list.isNotEmpty()) {
                        result += section.name to section.list
                    }
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Home error: ${api.name}", e)
            }
        }
    }

    return result
}

// ==============================
// SEARCH
// ==============================
suspend fun search(
    context: Context,
    filePath: String,
    query: String
): List<SearchResponse> {

    val apis = load(context, filePath)

    return apis.flatMap {
        runCatching { it.search(query) }.getOrNull().orEmpty()
    }
}

// ==============================
// LOAD LINKS (STREAMS)
// ==============================
suspend fun loadLinks(
    context: Context,
    filePath: String,
    url: String,
    onLink: (ExtractorLink) -> Unit
) {

    val apis = load(context, filePath)

    apis.forEach { api ->
        try {
            val load = api.load(url) ?: return@forEach

            api.loadLinks(
                data = load.url ?: return@forEach,
                isCasting = false,
                subtitleCallback = {},
                callback = { link ->
                    onLink(link)
                }
            )

        } catch (e: Throwable) {
            Log.e(TAG, "loadLinks error: ${api.name}", e)
        }
    }
}

// ==============================
// CLEAR CACHE
// ==============================
fun clear(path: String) {
    cache.remove(path)
    lastErrors.remove(path)
}

}