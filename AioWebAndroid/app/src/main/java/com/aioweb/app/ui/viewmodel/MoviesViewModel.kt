package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioMetaPreview
import com.aioweb.app.data.stremio.StremioRepository
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val SOURCE_BUILTIN = "builtin"
const val SOURCE_STREMIO_PREFIX = "stremio:"   // followed by manifest URL

data class PluginSection(
    val title: String,
    val items: List<SearchResponse>,
)

/** A flat poster row used for both Stremio addons and TMDB. */
data class StremioSection(
    val title: String,
    val items: List<StremioMetaPreview>,
)

data class MoviesState(
    val trending: List<TmdbMovie> = emptyList(),
    val popular: List<TmdbMovie> = emptyList(),
    val topRated: List<TmdbMovie> = emptyList(),
    val nowPlaying: List<TmdbMovie> = emptyList(),
    val searchResults: List<TmdbMovie> = emptyList(),
    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val installedStremioAddons: List<InstalledStremioAddon> = emptyList(),
    val selectedSourceId: String = SOURCE_BUILTIN,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    // Plugin-driven state
    val pluginSections: List<PluginSection> = emptyList(),
    val pluginSearchResults: List<SearchResponse> = emptyList(),
    val pluginLoading: Boolean = false,
    val pluginError: String? = null,
    // Stremio-driven state
    val stremioSections: List<StremioSection> = emptyList(),
    val stremioSearchResults: List<StremioMetaPreview> = emptyList(),
) {
    /** Display name of the currently selected source. */
    val selectedSourceName: String
        get() = when {
            selectedSourceId == SOURCE_BUILTIN -> "TMDB"
            selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX) -> installedStremioAddons.firstOrNull {
                "$SOURCE_STREMIO_PREFIX${it.manifestUrl}" == selectedSourceId
            }?.name ?: "Stremio addon"
            else -> installedPlugins.firstOrNull { it.internalName == selectedSourceId }?.name ?: "Plugin"
        }

    val isPluginActive: Boolean get() =
        selectedSourceId != SOURCE_BUILTIN && !selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX)

    val isStremioActive: Boolean get() = selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX)
}

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadDiscover()
        viewModelScope.launch {
            pluginRepo.installed.collect { list ->
                _state.update { it.copy(installedPlugins = list) }
            }
        }
        viewModelScope.launch {
            stremioRepo.addons.collect { list ->
                _state.update { it.copy(installedStremioAddons = list) }
            }
        }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val key = sl.tmdbApiKey
                val trending = sl.tmdb.trending("week", key).results
                val popular = sl.tmdb.popular(key).results
                val topRated = sl.tmdb.topRated(key).results
                val nowPlaying = sl.tmdb.nowPlaying(key).results
                _state.update { it.copy(
                    trending = trending,
                    popular = popular,
                    topRated = topRated,
                    nowPlaying = nowPlaying,
                    loading = false,
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to load: ${e.message}", loading = false) }
            }
        }
    }

    fun selectSource(sourceId: String) {
        _state.update {
            it.copy(
                selectedSourceId = sourceId,
                pluginSections = emptyList(),
                pluginSearchResults = emptyList(),
                stremioSections = emptyList(),
                stremioSearchResults = emptyList(),
                pluginError = null,
            )
        }
        when {
            sourceId == SOURCE_BUILTIN -> _state.update { it.copy(notice = null) }
            sourceId.startsWith(SOURCE_STREMIO_PREFIX) -> loadStremioHome(sourceId)
            else -> loadPluginHome(sourceId)
        }
    }

    private fun loadStremioHome(sourceId: String) {
        val manifest = sourceId.removePrefix(SOURCE_STREMIO_PREFIX)
        val addon = _state.value.installedStremioAddons.firstOrNull { it.manifestUrl == manifest }
        if (addon == null) {
            _state.update { it.copy(pluginError = "Stremio addon not found.") }
            return
        }
        _state.update { it.copy(pluginLoading = true, pluginError = null, notice = null) }
        viewModelScope.launch {
            try {
                val mf = stremioRepo.fetchManifest(addon.manifestUrl)
                val sections = mf.catalogs
                    .filter { c -> c.extra?.none { it.isRequired && it.name.lowercase() == "search" } != false }
                    .take(6)   // keep it snappy
                    .mapNotNull { def ->
                        runCatching {
                            val items = stremioRepo.fetchCatalog(addon, def.type, def.id)
                            if (items.isEmpty()) null
                            else StremioSection(def.name ?: "${def.type} · ${def.id}", items)
                        }.getOrNull()
                    }
                if (sections.isEmpty()) {
                    _state.update {
                        it.copy(
                            pluginLoading = false,
                            stremioSections = emptyList(),
                            notice = "Addon loaded — no catalogs available. Use search.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            pluginLoading = false,
                            stremioSections = sections,
                            pluginError = null,
                            notice = null,
                        )
                    }
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        pluginLoading = false,
                        pluginError = "Stremio addon failed: ${e::class.simpleName}: ${e.message}",
                    )
                }
            }
        }
    }

    private fun loadPluginHome(sourceId: String) {
        val plugin = _state.value.installedPlugins.firstOrNull { it.internalName == sourceId }
        if (plugin == null) {
            _state.update { it.copy(pluginError = "Plugin not found.") }
            return
        }
        _state.update { it.copy(pluginLoading = true, pluginError = null, notice = null) }
        viewModelScope.launch {
            try {
                val sections = PluginRuntime.home(appContext, plugin.filePath)
                if (sections.isEmpty()) {
                    val err = PluginRuntime.lastErrorFor(plugin.filePath)
                    _state.update {
                        it.copy(
                            pluginLoading = false,
                            pluginSections = emptyList(),
                            pluginError = err
                                ?: "Plugin loaded but returned no home content. Try search instead.",
                            notice = "Plugin loaded — but its home feed is empty. Use search.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            pluginLoading = false,
                            pluginSections = sections.map { (n, l) -> PluginSection(n, l) },
                            pluginError = null,
                            notice = null,
                        )
                    }
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        pluginLoading = false,
                        pluginError = "Plugin failed: ${e::class.simpleName}: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(
                searchResults = emptyList(),
                pluginSearchResults = emptyList(),
                stremioSearchResults = emptyList(),
            ) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                when {
                    _state.value.isStremioActive -> {
                        val manifest = _state.value.selectedSourceId.removePrefix(SOURCE_STREMIO_PREFIX)
                        val addon = _state.value.installedStremioAddons.firstOrNull { it.manifestUrl == manifest }
                        if (addon != null) {
                            val mf = stremioRepo.fetchManifest(addon.manifestUrl)
                            // Find the first catalog declaring `search` as an extra arg.
                            val first = mf.catalogs.firstOrNull { c ->
                                c.extra?.any { it.name.lowercase() == "search" } == true
                            } ?: mf.catalogs.firstOrNull()
                            if (first != null) {
                                val res = stremioRepo.fetchCatalog(addon, first.type, first.id, search = query)
                                _state.update { it.copy(stremioSearchResults = res, error = null) }
                            }
                        }
                    }
                    _state.value.isPluginActive -> {
                        val plugin = _state.value.installedPlugins
                            .firstOrNull { it.internalName == _state.value.selectedSourceId }
                        if (plugin != null) {
                            val res = PluginRuntime.search(appContext, plugin.filePath, query)
                            _state.update { it.copy(pluginSearchResults = res, error = null) }
                        }
                    }
                    else -> {
                        val res = sl.tmdb.search(sl.tmdbApiKey, query).results
                        _state.update { it.copy(searchResults = res, error = null) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Search failed: ${e.message}") }
            }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MoviesViewModel(
                    ServiceLocator.get(context),
                    PluginRepository(context.applicationContext),
                    StremioRepository(context.applicationContext),
                    context.applicationContext,
                ) as T
            }
        }
    }
}
