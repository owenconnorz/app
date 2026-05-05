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
    val heroBanner: List<TmdbMovie> = emptyList(),
    val continueWatching: List<WatchProgressEntity> = emptyList(),

    val pluginSections: List<PluginSection> = emptyList(),
    val stremioSections: List<StremioSection> = emptyList(),

    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val installedStremioAddons: List<InstalledStremioAddon> = emptyList(),

    val loading: Boolean = false,
)

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state

    init {
        loadTMDB()
        observePlugins()
        observeStremio()
        observeContinueWatching()
    }

    private fun loadTMDB() {
        viewModelScope.launch {
            val key = sl.tmdbApiKey

            val rows = HomeCollections.ALL.map { def ->
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
                    heroBanner = (rows["trending"] ?: emptyList()).take(5),
                    loading = false
                )
            }
        }
    }

    private fun observePlugins() {
        viewModelScope.launch {
            pluginRepo.installed.collect { plugins ->
                _state.update { it.copy(installedPlugins = plugins) }

                // auto load first plugin
                if (plugins.isNotEmpty()) {
                    loadPluginHome(plugins.first().internalName)
                }
            }
        }
    }

    private fun observeStremio() {
        viewModelScope.launch {
            stremioRepo.addons.collect { addons ->

                val sections = mutableListOf<StremioSection>()

                for (addon in addons) {
                    val mf = runCatching {
                        stremioRepo.fetchManifest(addon.manifestUrl)
                    }.getOrNull() ?: continue

                    for (cat in mf.catalogs) {
                        val items = runCatching {
                            stremioRepo.fetchCatalog(addon, cat.type, cat.id)
                        }.getOrDefault(emptyList())

                        if (items.isNotEmpty()) {
                            sections.add(
                                StremioSection(
                                    title = "${addon.name} • ${cat.name ?: cat.id}",
                                    items = items
                                )
                            )
                        }
                    }
                }

                _state.update { it.copy(stremioSections = sections) }
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

    fun loadPluginHome(pluginId: String) {
        val plugin = _state.value.installedPlugins
            .firstOrNull { it.internalName == pluginId }
            ?: return

        viewModelScope.launch {
            val sections = runCatching {
                PluginRuntime.home(context, plugin.filePath)
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    pluginSections = sections.map { (name, items) ->
                        PluginSection(name, items)
                    }
                )
            }
        }
    }

    fun setSource(source: String) {
        if (source == "tmdb") {
            loadTMDB()
        } else {
            loadPluginHome(source)
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
                    context
                ) as T
            }
        }
    }
}