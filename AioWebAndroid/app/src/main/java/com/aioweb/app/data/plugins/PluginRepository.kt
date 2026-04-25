package com.aioweb.app.data.plugins

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioweb.app.data.network.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.pluginStore by preferencesDataStore("aioweb_plugins")
private val KEY_REPOS = stringPreferencesKey("repos_json")
private val KEY_INSTALLED = stringPreferencesKey("installed_json")

class PluginRepository(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val pluginsDir: File = File(context.filesDir, "plugins").apply { mkdirs() }

    val repos: Flow<List<CloudStreamRepo>> = context.pluginStore.data.map { prefs ->
        prefs[KEY_REPOS]?.let {
            runCatching {
                Net.json.decodeFromString(ListSerializer(CloudStreamRepo.serializer()), it)
            }.getOrDefault(emptyList())
        } ?: defaultRepos()
    }

    val installed: Flow<List<InstalledPlugin>> = context.pluginStore.data.map { prefs ->
        prefs[KEY_INSTALLED]?.let {
            runCatching {
                Net.json.decodeFromString(ListSerializer(InstalledPlugin.serializer()), it)
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    private fun defaultRepos(): List<CloudStreamRepo> = listOf(
        CloudStreamRepo(
            id = "official",
            name = "Recloudstream Official",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/builds/repo.json",
        )
    )

    suspend fun addRepo(name: String, url: String) {
        val current = repos.first()
        val newRepo = CloudStreamRepo(id = UUID.randomUUID().toString(), name = name, url = url)
        saveRepos(current + newRepo)
    }

    suspend fun removeRepo(id: String) {
        val current = repos.first().filterNot { it.id == id }
        saveRepos(current)
    }

    private suspend fun saveRepos(list: List<CloudStreamRepo>) {
        val text = Net.json.encodeToString(ListSerializer(CloudStreamRepo.serializer()), list)
        context.pluginStore.edit { it[KEY_REPOS] = text }
    }

    private suspend fun saveInstalled(list: List<InstalledPlugin>) {
        val text = Net.json.encodeToString(ListSerializer(InstalledPlugin.serializer()), list)
        context.pluginStore.edit { it[KEY_INSTALLED] = text }
    }

    /**
     * Fetch the repository's plugin manifest. Most CS repos serve a simple JSON array
     * of plugin objects; some serve `{ pluginLists: [...], repos: [...] }`.
     */
    suspend fun fetchPluginList(repoUrl: String): List<CloudStreamPlugin> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(repoUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            // Try array form first
            runCatching {
                Net.json.decodeFromString(ListSerializer(CloudStreamPlugin.serializer()), body)
            }.getOrElse {
                // Some repos wrap inside a "pluginLists" or root object — try a fallback shape
                runCatching {
                    val element = Net.json.parseToJsonElement(body)
                    val arr = when {
                        element is kotlinx.serialization.json.JsonArray -> element
                        element is kotlinx.serialization.json.JsonObject &&
                            element["pluginLists"] is kotlinx.serialization.json.JsonArray ->
                            element["pluginLists"] as kotlinx.serialization.json.JsonArray
                        else -> return@runCatching emptyList()
                    }
                    arr.map {
                        Net.json.decodeFromJsonElement(CloudStreamPlugin.serializer(), it)
                    }
                }.getOrDefault(emptyList())
            }
        }
    }

    suspend fun installPlugin(repo: CloudStreamRepo, plugin: CloudStreamPlugin): InstalledPlugin =
        withContext(Dispatchers.IO) {
            val safeName = (plugin.internalName ?: plugin.name)
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "plugin_${System.currentTimeMillis()}" }
            val outFile = File(pluginsDir, "$safeName.cs3")
            val req = Request.Builder().url(plugin.url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed HTTP ${resp.code}")
                val body = resp.body ?: error("Empty body")
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            val installed = InstalledPlugin(
                name = plugin.name,
                internalName = plugin.internalName ?: safeName,
                version = plugin.version,
                filePath = outFile.absolutePath,
                sourceRepoId = repo.id,
                sourceUrl = plugin.url,
                installedAt = System.currentTimeMillis(),
            )
            val list = installed.let { i ->
                this@PluginRepository.installed.first()
                    .filterNot { it.internalName == i.internalName } + i
            }
            saveInstalled(list)
            installed
        }

    suspend fun uninstallPlugin(internalName: String) = withContext(Dispatchers.IO) {
        val current = installed.first()
        current.firstOrNull { it.internalName == internalName }?.let {
            File(it.filePath).delete()
        }
        saveInstalled(current.filterNot { it.internalName == internalName })
    }
}
