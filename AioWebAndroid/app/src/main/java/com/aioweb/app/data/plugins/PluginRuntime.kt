package com.aioweb.app.data.plugins

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PluginRuntime {

    private data class LoadedPlugin(
        val plugin: Plugin,
        val apis: List<MainAPI>
    )

    private val cache = mutableMapOf<String, LoadedPlugin>()

    suspend fun load(context: Context, filePath: String): List<MainAPI> =
        withContext(Dispatchers.IO) {

            cache[filePath]?.let { return@withContext it.apis }

            val file = File(filePath)

            val optimizedDir = File(context.codeCacheDir, "plugins").apply { mkdirs() }

            val loader = DexClassLoader(
                file.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )

            val pluginClass = loader.loadClass("Plugin")
            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin

            plugin.load(context)

            val apis = plugin.apis.toList()

            cache[filePath] = LoadedPlugin(plugin, apis)

            return@withContext apis
        }

    suspend fun home(
        context: Context,
        filePath: String
    ): List<Pair<String, List<SearchResponse>>> {

        val apis = load(context, filePath)

        val result = mutableListOf<Pair<String, List<SearchResponse>>>()

        apis.forEach { api ->
            api.mainPage.forEach { req ->
                val page = api.getMainPage(1, req)

                page?.items?.forEach {
                    if (it.list.isNotEmpty()) {
                        result += it.name to it.list
                    }
                }
            }
        }

        return result
    }

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

    suspend fun loadLinks(
        context: Context,
        filePath: String,
        url: String,
        onLink: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ) {

        val apis = load(context, filePath)

        apis.forEach { api ->
            try {
                val load = api.load(url) ?: return@forEach

                api.loadLinks(
                    data = load.url ?: return@forEach,
                    isCasting = false,
                    subtitleCallback = {},
                    callback = { link -> onLink(link) }
                )
            } catch (_: Throwable) {
            }
        }
    }
}