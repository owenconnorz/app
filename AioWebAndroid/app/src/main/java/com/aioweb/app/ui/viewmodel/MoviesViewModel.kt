package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.collections.HomeCollection
import com.aioweb.app.data.collections.HomeCollections
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.library.WatchProgressEntity
import com.aioweb.app.data.nuvio.InstalledNuvioProvider
import com.aioweb.app.data.nuvio.NuvioRepository
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioMetaPreview
import com.aioweb.app.data.stremio.StremioRepository
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

const val SOURCE_BUILTIN = "builtin"
const val SOURCE_STREMIO_PREFIX = "stremio_"
const val SOURCE_NUVIO_PREFIX = "nuvio_"

data class PluginSection(
    val title: String,
    val items: List<SearchResponse>,
)

data class StremioSection(
    val title: String,
    val addonName: String,
    val items: List<StremioMetaPreview>,
)

data class NuvioSection(
    val title: String,
    val providerName: String,
    val items: List<StremioMetaPreview>,
)

data class CollectionRow(
    val id: String,
    val title: String,
    val emoji: String,
    val items: List<TmdbMovie>,
)

data class MoviesState(
    val trending: List<TmdbMovie> = emptyList(),
    val popular: List<TmdbMovie> = emptyList(),
    val topRated: List<TmdbMovie> = emptyList(),
    val nowPlaying: List<TmdbMovie> = emptyList(),
    val collections: List<CollectionRow> = emptyList(),
    val heroBanner: List<TmdbMovie> = emptyList(),
    val continueWatching: List<WatchProgressEntity> = emptyList(),
    val searchResults: List<TmdbMovie> = emptyList(),
    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val installedNuvioProviders: List<InstalledNuvioProvider> = emptyList(),
    val installedStremioAddons: List<InstalledStremioAddon> = emptyList(),
    val selectedSourceId: String = SOURCE_BUILTIN,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,

    val pluginSections: List<PluginSection> = emptyList(),
    val pluginSearchResults: List<SearchResponse> = emptyList(),
    val pluginLoading: Boolean = false,
    val pluginError: String? = null,

    val stremioSections: List<StremioSection> = emptyList(),
    val stremioLoading: Boolean = false,
    val stremioError: String? = null,

    val nuvioSections: List<NuvioSection> = emptyList(),
    val nuvioLoading: Boolean = false,
    val nuvioError: String? = null,
) {
    val isPluginActive: Boolean
        get() = selectedSourceId != SOURCE_BUILTIN &&
            !selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX) &&
            !selectedSourceId.startsWith(SOURCE_NUVIO_PREFIX)

    val isStremioActive: Boolean
        get() = selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX)

    val isNuvioActive: Boolean
        get() = selectedSourceId.startsWith(SOURCE_NUVIO_PREFIX)
}

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val nuvioRepo: NuvioRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            pluginRepo.installed.collect {
                _state.update { s -> s.copy(installedPlugins = it) }
            }
        }
        viewModelScope.launch {
            stremioRepo.addons.collect {
                _state.update { s -> s.copy(installedStremioAddons = it) }
            }
        }
        viewModelScope.launch {
            nuvioRepo.installed.collect {
                _state.update { s -> s.copy(installedNuvioProviders = it) }
            }
        }
        viewModelScope.launch {
            LibraryDb.get(appContext).watchProgress().continueWatching().collect {
                _state.update { s -> s.copy(continueWatching = it) }
            }
        }
    }

    // =========================
    // ✅ FIX ADDED HERE
    // =========================
    fun setSource(source: String) {
        when (source) {
            "tmdb", SOURCE_BUILTIN -> selectSource(SOURCE_BUILTIN)

            "plugin" -> {
                val plugin = _state.value.installedPlugins.firstOrNull()
                plugin?.let { selectSource(it.internalName) }
            }

            "stremio" -> {
                val addon = _state.value.installedStremioAddons.firstOrNull()
                addon?.let { selectSource(SOURCE_STREMIO_PREFIX + it.manifestUrl) }
            }

            "nuvio" -> {
                val provider = _state.value.installedNuvioProviders.firstOrNull()
                provider?.let { selectSource(SOURCE_NUVIO_PREFIX + it.id) }
            }

            else -> selectSource(source)
        }
    }

    // =========================
    // EXISTING LOGIC (UNCHANGED)
    // =========================
    fun selectSource(sourceId: String) {
        _state.update {
            it.copy(
                selectedSourceId = sourceId,
                pluginSections = emptyList(),
                stremioSections = emptyList(),
                nuvioSections = emptyList()
            )
        }

        when {
            sourceId == SOURCE_BUILTIN -> loadDiscover()
            sourceId.startsWith(SOURCE_STREMIO_PREFIX) -> loadStremioHome()
            sourceId.startsWith(SOURCE_NUVIO_PREFIX) -> loadNuvioHome()
            else -> loadPluginHome(sourceId)
        }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val res = sl.tmdb.getTrending(sl.tmdbApiKey)
            _state.update { it.copy(trending = res, loading = false) }
        }
    }

    private fun loadPluginHome(sourceId: String) {
        val plugin = _state.value.installedPlugins.firstOrNull { it.internalName == sourceId }
        if (plugin == null) return

        viewModelScope.launch {
            val sections = PluginRuntime.home(appContext, plugin.filePath)
            _state.update {
                it.copy(pluginSections = sections.map { (n, l) -> PluginSection(n, l) })
            }
        }
    }

    private fun loadStremioHome() {}
    private fun loadNuvioHome() {}

    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            val res = sl.tmdb.search(sl.tmdbApiKey, query).results
            _state.update { it.copy(searchResults = res) }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MoviesViewModel(
                    ServiceLocator.get(context),
                    PluginRepository(context),
                    StremioRepository(context),
                    NuvioRepository(context),
                    context
                ) as T
            }
        }
    }
}