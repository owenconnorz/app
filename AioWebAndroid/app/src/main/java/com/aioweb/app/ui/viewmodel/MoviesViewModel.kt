package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.collections.CollectionRow
import com.aioweb.app.data.library.*
import com.aioweb.app.data.plugins.*
import com.aioweb.app.data.stremio.*
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

const val SOURCE_BUILTIN = "builtin"
const val SOURCE_STREMIO_PREFIX = "stremio_"

data class MoviesState(
    val trending: List<TmdbMovie> = emptyList(),
    val collections: List<CollectionRow> = emptyList(),
    val heroBanner: List<TmdbMovie> = emptyList(),
    val searchResults: List<TmdbMovie> = emptyList(),
    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val installedStremioAddons: List<InstalledStremioAddon> = emptyList(),
    val pluginSections: List<PluginSection> = emptyList(),
    val stremioSections: List<StremioSection> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class PluginSection(
    val title: String,
    val items: List<SearchResponse>
)

data class StremioSection(
    val title: String,
    val items: List<StremioMetaPreview>
)

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    init {
        observePlugins()
        observeStremio()
    }

    // =============================
    // OBSERVERS
    // =============================

    private fun observePlugins() {
        viewModelScope.launch {
            pluginRepo.installed.collect { plugins ->
                _state.update { it.copy(installedPlugins = plugins) }
                loadPlugins()
            }
        }
    }

    private fun observeStremio() {
        viewModelScope.launch {
            stremioRepo.addons.collect { addons ->
                _state.update { it.copy(installedStremioAddons = addons) }
                loadStremio()
            }
        }
    }

    // =============================
    // TMDB HOME
    // =============================

    fun loadDiscover() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            try {
                val movies = sl.tmdb.trending(sl.tmdbApiKey).results

                _state.update {
                    it.copy(
                        trending = movies,
                        heroBanner = movies.take(5),
                        loading = false
                    )
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    // =============================
    // PLUGINS (FIXED)
    // =============================

    private fun loadPlugins() {
        viewModelScope.launch {
            try {
                val sections = _state.value.installedPlugins.mapNotNull { plugin ->

                    val home = runCatching {
                        PluginRuntime.home(context, plugin.filePath)
                    }.getOrDefault(emptyList())

                    // 🔥 FIX: flatten SAFELY
                    val items = home.flatMap { it }

                    if (items.isEmpty()) null
                    else PluginSection(plugin.name, items)
                }

                _state.update { it.copy(pluginSections = sections) }

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // =============================
    // STREMIO (SAFE)
    // =============================

    private fun loadStremio() {
        viewModelScope.launch {
            try {
                val sections = _state.value.installedStremioAddons.mapNotNull { addon ->

                    val items = runCatching {
                        stremioRepo.fetchCatalog(addon, "movie", "top")
                    }.getOrDefault(emptyList())

                    if (items.isEmpty()) null
                    else StremioSection(addon.name, items)
                }

                _state.update { it.copy(stremioSections = sections) }

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // =============================
    // SEARCH
    // =============================

    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            try {
                val res = sl.tmdb.search(sl.tmdbApiKey, query).results
                _state.update { it.copy(searchResults = res) }

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // =============================
    // FACTORY
    // =============================

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MoviesViewModel(
                    ServiceLocator.get(context),
                    PluginRepository(context.applicationContext),
                    StremioRepository(context.applicationContext),
                    context.applicationContext
                ) as T
            }
        }
    }
}