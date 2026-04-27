package com.aioweb.app.data.nuvio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioweb.app.data.network.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.nuvioStore by preferencesDataStore("aioweb_nuvio")
private val KEY_INSTALLED = stringPreferencesKey("installed_json")

/**
 * Repository for Nuvio JS providers. Two-step install flow:
 *   1. User pastes a manifest URL → we fetch + parse the repo's `manifest.json`.
 *   2. User picks providers from the list → each .js file is downloaded once and
 *      cached at /files/nuvio/<id>.js. The runtime just reads the cached file.
 */
class NuvioRepository(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun cacheDir(): File =
        File(context.filesDir, "nuvio").apply { mkdirs() }

    val installed: Flow<List<InstalledNuvioProvider>> = context.nuvioStore.data.map { prefs ->
        prefs[KEY_INSTALLED]?.let {
            runCatching {
                Net.json.decodeFromString(ListSerializer(InstalledNuvioProvider.serializer()), it)
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun fetchManifest(repoUrl: String): NuvioRepoManifest = withContext(Dispatchers.IO) {
        val url = normaliseRepoUrl(repoUrl)
        val body = httpGet(url)
        Net.json.decodeFromString(NuvioRepoManifest.serializer(), body)
    }

    suspend fun installProvider(repoUrl: String, entry: NuvioProviderEntry): InstalledNuvioProvider =
        withContext(Dispatchers.IO) {
            val manifestUrl = normaliseRepoUrl(repoUrl)
            val absDl = resolveDownloadUrl(manifestUrl, entry)
                ?: error("Provider entry has no downloadUrl/url")
            val safeId = entry.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val outFile = File(cacheDir(), "$safeId.js")
            val text = httpGet(absDl)
            outFile.writeText(text)

            val rec = InstalledNuvioProvider(
                id = entry.id, name = entry.name,
                repoUrl = manifestUrl, downloadUrl = absDl,
                filePath = outFile.absolutePath, installedAt = System.currentTimeMillis(),
                logo = entry.logo ?: entry.icon, description = entry.description,
            )
            val list = installed.first().filterNot { it.id == entry.id } + rec
            save(list)
            rec
        }

    suspend fun uninstall(id: String) {
        val list = installed.first()
        list.firstOrNull { it.id == id }?.let { File(it.filePath).delete() }
        save(list.filterNot { it.id == id })
    }

    /** Run every installed provider in parallel against [tmdbId] and aggregate streams. */
    suspend fun resolveAll(
        tmdbId: String,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
    ): List<Pair<InstalledNuvioProvider, NuvioStream>> = coroutineScope {
        val list = installed.first()
        list.map { provider ->
            async(Dispatchers.IO) {
                val js = runCatching { File(provider.filePath).readText() }.getOrNull()
                    ?: return@async emptyList()
                val streams = NuvioRuntime.runProvider(js, tmdbId, mediaType, season, episode)
                streams.map { provider to it }
            }
        }.awaitAll().flatten()
    }

    private suspend fun save(list: List<InstalledNuvioProvider>) {
        val text = Net.json.encodeToString(ListSerializer(InstalledNuvioProvider.serializer()), list)
        context.nuvioStore.edit { it[KEY_INSTALLED] = text }
    }

    private fun normaliseRepoUrl(s: String): String {
        val t = s.trim()
        return when {
            t.endsWith("manifest.json") -> t
            t.startsWith("http") -> "$t/manifest.json".replace("//manifest.json", "/manifest.json")
            else -> "https://$t/manifest.json"
        }
    }

    /** Resolve the .js downloadUrl relative to the manifest URL when needed. */
    private fun resolveDownloadUrl(manifestUrl: String, e: NuvioProviderEntry): String? {
        val raw = e.downloadUrl ?: e.downloadUrlSnake ?: e.url ?: e.filename ?: return null
        if (raw.startsWith("http")) return raw
        // Relative — resolve against manifest's base.
        val base = manifestUrl.substringBeforeLast('/')
        return "$base/${raw.trimStart('/')}"
    }

    private fun httpGet(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "StreamCloud/1.0 (Nuvio compatible)")
            .header("Accept", "application/json, text/javascript, */*")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} from $url")
            return resp.body?.string().orEmpty()
        }
    }
}
