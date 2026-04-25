package com.aioweb.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioweb.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("aioweb_settings")

object SettingsKeys {
    val BACKEND_URL = stringPreferencesKey("backend_url")
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val AI_MODEL = stringPreferencesKey("ai_model")
    val NSFW_ENABLED = booleanPreferencesKey("nsfw_enabled")
    val VIDEO_QUALITY = stringPreferencesKey("video_quality")     // auto / 1080 / 720 / 480
    val AUDIO_QUALITY = stringPreferencesKey("audio_quality")     // high / medium / low
    val EXTERNAL_LINKS_IN_BROWSER = booleanPreferencesKey("ext_links_in_browser")
    val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
    val SUBTITLES_ENABLED = booleanPreferencesKey("subs_enabled")
    val DOWNLOAD_OVER_WIFI_ONLY = booleanPreferencesKey("dl_wifi_only")
    val SAFE_MODE_PIN = stringPreferencesKey("safe_mode_pin")     // 4-digit PIN for NSFW
    val THEME = stringPreferencesKey("theme")                     // dark / system
    val FAL_API_KEY = stringPreferencesKey("fal_api_key")         // user-supplied fal.ai key for NSFW image gen
}

class SettingsRepository(private val context: Context) {
    val backendUrl: Flow<String> = context.dataStore.data.map {
        it[SettingsKeys.BACKEND_URL] ?: BuildConfig.DEFAULT_BACKEND_URL
    }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[SettingsKeys.AI_PROVIDER] ?: "openai" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[SettingsKeys.AI_MODEL] ?: "gpt-5.1" }
    val nsfwEnabled: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.NSFW_ENABLED] ?: false }
    val videoQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.VIDEO_QUALITY] ?: "auto" }
    val audioQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.AUDIO_QUALITY] ?: "high" }
    val externalLinksInBrowser: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.EXTERNAL_LINKS_IN_BROWSER] ?: true }
    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.AUTOPLAY_NEXT] ?: true }
    val subtitlesEnabled: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.SUBTITLES_ENABLED] ?: true }
    val downloadOverWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.DOWNLOAD_OVER_WIFI_ONLY] ?: true }
    val safeModePin: Flow<String> = context.dataStore.data.map { it[SettingsKeys.SAFE_MODE_PIN] ?: "" }
    val theme: Flow<String> = context.dataStore.data.map { it[SettingsKeys.THEME] ?: "dark" }
    val falApiKey: Flow<String> = context.dataStore.data.map { it[SettingsKeys.FAL_API_KEY] ?: "" }

    suspend fun setBackendUrl(url: String) = context.dataStore.edit { it[SettingsKeys.BACKEND_URL] = url }
    suspend fun setAiProvider(p: String) = context.dataStore.edit { it[SettingsKeys.AI_PROVIDER] = p }
    suspend fun setAiModel(m: String) = context.dataStore.edit { it[SettingsKeys.AI_MODEL] = m }
    suspend fun setNsfwEnabled(b: Boolean) = context.dataStore.edit { it[SettingsKeys.NSFW_ENABLED] = b }
    suspend fun setVideoQuality(q: String) = context.dataStore.edit { it[SettingsKeys.VIDEO_QUALITY] = q }
    suspend fun setAudioQuality(q: String) = context.dataStore.edit { it[SettingsKeys.AUDIO_QUALITY] = q }
    suspend fun setExternalLinksInBrowser(b: Boolean) = context.dataStore.edit { it[SettingsKeys.EXTERNAL_LINKS_IN_BROWSER] = b }
    suspend fun setAutoplayNext(b: Boolean) = context.dataStore.edit { it[SettingsKeys.AUTOPLAY_NEXT] = b }
    suspend fun setSubtitlesEnabled(b: Boolean) = context.dataStore.edit { it[SettingsKeys.SUBTITLES_ENABLED] = b }
    suspend fun setDownloadOverWifiOnly(b: Boolean) = context.dataStore.edit { it[SettingsKeys.DOWNLOAD_OVER_WIFI_ONLY] = b }
    suspend fun setSafeModePin(p: String) = context.dataStore.edit { it[SettingsKeys.SAFE_MODE_PIN] = p }
    suspend fun setTheme(t: String) = context.dataStore.edit { it[SettingsKeys.THEME] = t }
    suspend fun setFalApiKey(k: String) = context.dataStore.edit { it[SettingsKeys.FAL_API_KEY] = k }
}
