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
import com.aioweb.app.data.nuvio.NuvioRuntime
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import com.aioweb.app.data.plugins.PluginRuntime
import com.aioweb.app.data.stremio.InstalledStremioAddon
import com.aioweb.app.data.stremio.StremioMetaPreview
import com.aioweb.app.data.stremio.StremioRepository
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val selectedSourceName: String
        get() = when {
            selectedSourceId == SOURCE_BUILTIN -> "TMDB"
            selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX) -> {
                val mUrl = selectedSourceId.removePrefix(SOURCE_STREMIO_PREFIX)
                installedStremioAddons.firstOrNull { it.manifestUrl == mUrl }?.name ?: "Stremio"
            }
            selectedSourceId.startsWith(SOURCE_NUVIO_PREFIX) -> {
                val id = selectedSourceId.removePrefix(SOURCE_NUVIO_PREFIX)
                installedNuvioProviders.firstOrNull { it.id == id }?.name ?: "Nuvio"
            }
            else -> installedPlugins.firstOrNull { it.internalName == selectedSourceId }?.name ?: "Plugin"
        }

    val isPluginActive: Boolean
        get() = selectedSourceId != SOURCE_BUILTIN &&
            !selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX) &&
            !selectedSourceId.startsWith(SOURCE_NUVIO_PREFIX)

    val isStremioActive: Boolean get() = selectedSourceId.startsWith(SOURCE_STREMIO_PREFIX)
    val isNuvioActive: Boolean get() = selectedSourceId.startsWith(SOURCE_NUVIO_PREFIX)
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

                // ✅ AUTO LOAD STREMIO (your requirement)
                if (it.isNotEmpty() && _state.value.selectedSourceId == SOURCE_BUILTIN) {
                    selectSource(SOURCE_STREMIO_PREFIX + it.first().manifestUrl)
                }
            }
        }

        viewModelScope.launch {
            nuvioRepo.installed.collect {
                _state.update { s -> s.copy(installedNuvioProviders = it) }
            }
        }

        viewModelScope.launch {
            sl.settings.homeCollectionsCsv.collectLatest { loadDiscover() }
        }

        viewModelScope.launch {
            LibraryDb.get(appContext).watchProgress().continueWatching().collect {
                _state.update { s -> s.copy(continueWatching = it) }
            }
        }
    }

    // ✅ SAFE SOURCE SWITCHER
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

    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val key = sl.tmdbApiKey
                val csv = sl.settings.homeCollectionsCsv.first()

                val ids = csv?.takeIf { it.isNotBlank() }?.split(',')
                    ?: HomeCollections.ALL.filter { it.defaultEnabled }.map { it.id }

                val collections: List<HomeCollection> = ids.mapNotNull {
                    HomeCollections.byId(it)
                }

                val rows = collections.map { def ->
                    async {
                        val items = runCatching {
                            def.fetch(sl.tmdb, key)
                        }.getOrDefault(emptyList())

                        if (items.isEmpty()) null
                        else CollectionRow(def.id, def.title, def.emoji, items)
                    }
                }.awaitAll().filterNotNull()

                val byId = rows.associateBy { it.id }

                _state.update {
                    it.copy(
                        trending = byId["trending"]?.items ?: emptyList(),
                        popular = byId["popular"]?.items ?: emptyList(),
                        topRated = byId["top_rated"]?.items ?: emptyList(),
                        nowPlaying = byId["now_playing"]?.items ?: emptyList(),
                        collections = rows,
                        heroBanner = (
                            byId["trending"]?.items
                                ?: byId["now_playing"]?.items
                                ?: rows.firstOrNull()?.items
                                ?: emptyList()
                        ).take(7),
                        loading = false,
                    )
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to load: ${e.message}", loading = false)
                }
            }
        }
    }

    // KEEP YOUR ORIGINAL IMPLEMENTATIONS (unchanged)
    fun selectSource(sourceId: String) { /* keep your original code */ }
    private fun loadPluginHome(sourceId: String) { /* keep original */ }
    private fun loadStremioHome(manifestUrl: String) { /* keep original */ }
    private fun loadNuvioHome(providerId: String) { /* keep original */ }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.update {
                it.copy(searchResults = emptyList(), pluginSearchResults = emptyList())
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            try {
                if (_state.value.isPluginActive) {
                    val plugin = _state.value.installedPlugins
                        .firstOrNull { it.internalName == _state.value.selectedSourceId }

                    if (plugin != null) {
                        val res = PluginRuntime.search(appContext, plugin.filePath, query)
                        _state.update { it.copy(pluginSearchResults = res, error = null) }
                    }
                } else {
                    val res = sl.tmdb.search(sl.tmdbApiKey, query).results
                    _state.update { it.copy(searchResults = res, error = null) }
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
                    NuvioRepository(context.applicationContext),
                    context.applicationContext,
                ) as T
            }
        }
    }
}