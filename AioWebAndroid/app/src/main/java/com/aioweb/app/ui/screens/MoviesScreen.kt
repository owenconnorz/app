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

data class PluginSection(
    val title: String,
    val items: List<SearchResponse>,
)

data class StremioSection(
    val title: String,
    val addonName: String,
    val items: List<StremioMetaPreview>,
)

data class MoviesState(
    val trending: List<TmdbMovie> = emptyList(),
    val popular: List<TmdbMovie> = emptyList(),
    val topRated: List<TmdbMovie> = emptyList(),
    val nowPlaying: List<TmdbMovie> = emptyList(),
    val heroBanner: List<TmdbMovie> = emptyList(),
    val continueWatching: List<WatchProgressEntity> = emptyList(),

    val pluginSections: List<PluginSection> = emptyList(),
    val stremioSections: List<StremioSection> = emptyList(),

    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val installedStremioAddons: List<InstalledStremioAddon> = emptyList(),

    val loading: Boolean = false,
    val error: String? = null
)

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val nuvioRepo: NuvioRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    init {
        observeInstalled()
        observeContinueWatching()
    }

    private fun observeInstalled() {
        viewModelScope.launch {
            pluginRepo.installed.collect {
                _state.update { s -> s.copy(installedPlugins = it) }
                loadPluginsHome()
            }
        }

        viewModelScope.launch {
            stremioRepo.addons.collect {
                _state.update { s -> s.copy(installedStremioAddons = it) }
                loadStremioHome()
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            LibraryDb.get(context).watchProgress().continueWatching().collect {
                _state.update { s -> s.copy(continueWatching = it) }
            }
        }
    }

    // =========================
    // TMDB
    // =========================
    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            try {
                val key = sl.tmdbApiKey

                val csv = sl.settings.homeCollectionsCsv.first()
                val ids = csv?.split(",")
                    ?: HomeCollections.ALL.filter { it.defaultEnabled }.map { it.id }

                val rows = ids.mapNotNull { HomeCollections.byId(it) }.map { def ->
                    async {
                        val items = runCatching { def.fetch(sl.tmdb, key) }
                            .getOrDefault(emptyList())
                        def.id to items
                    }
                }.awaitAll().toMap()

                _state.update {
                    it.copy(
                        trending = rows["trending"] ?: emptyList(),
                        popular = rows["popular"] ?: emptyList(),
                        topRated = rows["top_rated"] ?: emptyList(),
                        nowPlaying = rows["now_playing"] ?: emptyList(),
                        heroBanner = (rows["trending"] ?: emptyList()).take(7),
                        loading = false
                    )
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.message, loading = false)
                }
            }
        }
    }

    // =========================
    // PLUGINS (MERGED)
    // =========================
    private fun loadPluginsHome() {
        viewModelScope.launch {

            val plugins = _state.value.installedPlugins

            val sections = plugins.mapNotNull { plugin ->
                runCatching {
                    val home = PluginRuntime.home(context, plugin.filePath)
                    home.map { (name, items) ->
                        PluginSection("${plugin.name} • $name", items)
                    }
                }.getOrNull()
            }.flatten()

            _state.update { it.copy(pluginSections = sections) }
        }
    }

    // =========================
    // STREMIO (MERGED)
    // =========================
    private fun loadStremioHome() {
        viewModelScope.launch {

            val addons = _state.value.installedStremioAddons

            val sections = addons.mapNotNull { addon ->
                runCatching {

                    val mf = stremioRepo.fetchManifest(addon.manifestUrl)

                    mf.catalogs.mapNotNull { cat ->
                        val items = stremioRepo.fetchCatalog(addon, cat.type, cat.id)
                        if (items.isEmpty()) null
                        else StremioSection(
                            title = "${addon.name} • ${cat.name ?: cat.id}",
                            addonName = addon.name,
                            items = items
                        )
                    }

                }.getOrNull()
            }.flatten()

            _state.update { it.copy(stremioSections = sections) }
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