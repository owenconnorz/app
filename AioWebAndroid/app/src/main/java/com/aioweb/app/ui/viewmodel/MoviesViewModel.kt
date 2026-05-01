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
    // CloudStream plugin-driven state
    val pluginSections: List<PluginSection> = emptyList(),
    val pluginSearchResults: List<SearchResponse> = emptyList(),
    val pluginLoading: Boolean = false,
    val pluginError: String? = null,
    // Stremio addon-driven state
    val stremioSections: List<StremioSection> = emptyList(),
    val stremioLoading: Boolean = false,
    val stremioError: String? = null,
    // Nuvio provider-driven state (when selected as a source)
    val nuvioSections: List<NuvioSection> = emptyList(),
    val nuvioLoading: Boolean = false,
    val nuvioError: String? = null,
) {
    /** Display name of the currently selected source. */
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
            pluginRepo.installed.collect { list ->
                _state.update { it.copy(installedPlugins = list) }
            }
        }
        viewModelScope.launch {
            stremioRepo.addons.collect { list ->
                _state.update { it.copy(installedStremioAddons = list) }
            }
        }
        viewModelScope.launch {
            nuvioRepo.installed.collect { list ->
                _state.update { it.copy(installedNuvioProviders = list) }
            }
        }
        // Auto-reload home rows whenever the user toggles a collection in Settings.
        viewModelScope.launch {
            sl.settings.homeCollectionsCsv.collectLatest { loadDiscover() }
        }
        // Continue-watching row is fed by the player whenever the user pauses/exits.
        viewModelScope.launch {
            LibraryDb.get(appContext).watchProgress().continueWatching().collect { rows ->
                _state.update { it.copy(continueWatching = rows) }
            }
        }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val key = sl.tmdbApiKey

                // Resolve which collections to render based on user settings — fall back to defaults.
                val csv = sl.settings.homeCollectionsCsv.first()
                val ids = csv?.takeIf { it.isNotBlank() }?.split(',')
                    ?: HomeCollections.ALL.filter { it.defaultEnabled }.map { it.id }
                val collections: List<HomeCollection> = ids.mapNotNull { HomeCollections.byId(it) }

                // Fan out — fetch all enabled rows in parallel.
                val rows = collections.map { def ->
                    async {
                        val items = runCatching { def.fetch(sl.tmdb, key) }.getOrDefault(emptyList())
                        if (items.isEmpty()) null
                        else CollectionRow(def.id, def.title, def.emoji, items)
                    }
                }.awaitAll().filterNotNull()

                // Compatibility shim: keep populated trending/popular/topRated/nowPlaying for any
                // older UI paths still pulling from them directly.
                val byId = rows.associateBy { it.id }
                _state.update {
                    it.copy(
                        trending = byId["trending"]?.items ?: emptyList(),
                        popular = byId["popular"]?.items ?: emptyList(),
                        topRated = byId["top_rated"]?.items ?: emptyList(),
                        nowPlaying = byId["now_playing"]?.items ?: emptyList(),
                        collections = rows,
                        heroBanner = (byId["trending"]?.items
                            ?: byId["now_playing"]?.items
                            ?: rows.firstOrNull()?.items
                            ?: emptyList()).take(7),
                        loading = false,
                    )
                }
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
                pluginError = null,
                stremioSections = emptyList(),
                stremioError = null,
                nuvioSections = emptyList(),
                nuvioError = null,
            )
        }
        when {
            sourceId == SOURCE_BUILTIN -> _state.update { it.copy(notice = null) }
            sourceId.startsWith(SOURCE_STREMIO_PREFIX) ->
                loadStremioHome(sourceId.removePrefix(SOURCE_STREMIO_PREFIX))
            sourceId.startsWith(SOURCE_NUVIO_PREFIX) ->
                loadNuvioHome(sourceId.removePrefix(SOURCE_NUVIO_PREFIX))
            else -> loadPluginHome(sourceId)
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
                            // Suppress the duplicate "Use search." notice when the error card
                            // is already shown below — we used to render both stacked.
                            notice = null,
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

    private fun loadStremioHome(manifestUrl: String) {
        val addon = _state.value.installedStremioAddons.firstOrNull { it.manifestUrl == manifestUrl }
        if (addon == null) {
            _state.update { it.copy(stremioError = "Stremio addon not found.") }
            return
        }
        _state.update { it.copy(stremioLoading = true, stremioError = null) }
        viewModelScope.launch {
            try {
                val mf = stremioRepo.fetchManifest(addon.manifestUrl)
                // Fetch all non-search-required catalogs in parallel
                val sections = mf.catalogs
                    .filter { c -> c.extra?.none { it.isRequired && it.name.lowercase() == "search" } != false }
                    .map { cat ->
                        async {
                            runCatching {
                                val items = stremioRepo.fetchCatalog(addon, cat.type, cat.id)
                                if (items.isNotEmpty())
                                    StremioSection(
                                        title = cat.name ?: cat.id,
                                        addonName = addon.name,
                                        items = items,
                                    )
                                else null
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()

                if (sections.isEmpty()) {
                    _state.update {
                        it.copy(
                            stremioLoading = false,
                            stremioError = "${addon.name} returned no catalog items.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(stremioLoading = false, stremioSections = sections, stremioError = null)
                    }
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        stremioLoading = false,
                        stremioError = "Stremio failed: ${e::class.simpleName}: ${e.message}",
                    )
                }
            }
        }
    }

    private fun loadNuvioHome(providerId: String) {
        val provider = _state.value.installedNuvioProviders.firstOrNull { it.id == providerId }
        if (provider == null) {
            _state.update { it.copy(nuvioError = "Nuvio provider not found.") }
            return
        }
        _state.update { it.copy(nuvioLoading = true, nuvioError = null) }
        viewModelScope.launch {
            try {
                // Run catalog fetch via the Stremio-compatible catalog endpoint exposed by Nuvio.
                // Nuvio providers that expose a /manifest.json catalog are fetched as Stremio addons.
                val fakeAddon = com.aioweb.app.data.stremio.InstalledStremioAddon(
                    id = provider.id,
                    name = provider.name,
                    manifestUrl = provider.downloadUrl.substringBeforeLast("/") + "/manifest.json",
                    baseUrl = provider.downloadUrl.substringBeforeLast("/"),
                    logo = provider.logo,
                    installedAt = provider.installedAt,
                )
                val mf = runCatching { stremioRepo.fetchManifest(fakeAddon.manifestUrl) }.getOrNull()
                val sections = mf?.catalogs
                    ?.filter { c -> c.extra?.none { it.isRequired && it.name.lowercase() == "search" } != false }
                    ?.mapNotNull { cat ->
                        runCatching {
                            val items = stremioRepo.fetchCatalog(fakeAddon, cat.type, cat.id)
                            if (items.isNotEmpty())
                                NuvioSection(
                                    title = cat.name ?: cat.id,
                                    providerName = provider.name,
                                    items = items,
                                )
                            else null
                        }.getOrNull()
                    } ?: emptyList()

                if (sections.isEmpty()) {
                    _state.update {
                        it.copy(
                            nuvioLoading = false,
                            nuvioError = "${provider.name} returned no catalog content. " +
                                "This provider may only support stream resolution, not browsing.",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(nuvioLoading = false, nuvioSections = sections, nuvioError = null)
                    }
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        nuvioLoading = false,
                        nuvioError = "Nuvio catalog failed: ${e::class.simpleName}: ${e.message}",
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
            _state.update { it.copy(searchResults = emptyList(), pluginSearchResults = emptyList()) }
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
