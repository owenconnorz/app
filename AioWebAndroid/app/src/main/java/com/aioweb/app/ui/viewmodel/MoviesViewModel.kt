package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.collections.*
import com.aioweb.app.data.library.*
import com.aioweb.app.data.plugins.*
import com.aioweb.app.data.stremio.*
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.*
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

data class PluginSection(val title: String, val items: List<SearchResponse>)
data class StremioSection(val title: String, val items: List<StremioMetaPreview>)
data class CollectionRow(val id: String, val title: String, val emoji: String, val items: List<TmdbMovie>)

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
    private val stremioRepo: StremioRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state

    init {
        viewModelScope.launch {
            pluginRepo.installed.collect {
                _state.update { s -> s.copy(installedPlugins = it) }
                loadPlugins()
            }
        }

        viewModelScope.launch {
            stremioRepo.addons.collect {
                _state.update { s -> s.copy(installedStremioAddons = it) }
                loadStremio()
            }
        }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            val movies = sl.tmdb.trending(sl.tmdbApiKey).results
            _state.update {
                it.copy(
                    trending = movies,
                    heroBanner = movies.take(5)
                )
            }
        }
    }

    private fun loadPlugins() {
        viewModelScope.launch {
            val sections = _state.value.installedPlugins.mapNotNull {
                val res = PluginRuntime.home(context, it.filePath)
                if (res.isEmpty()) null else PluginSection(it.name, res.flatten())
            }
            _state.update { it.copy(pluginSections = sections) }
        }
    }

    private fun loadStremio() {
        viewModelScope.launch {
            val sections = _state.value.installedStremioAddons.mapNotNull {
                val items = stremioRepo.fetchCatalog(it, "movie", "top")
                if (items.isEmpty()) null else StremioSection(it.name, items)
            }
            _state.update { it.copy(stremioSections = sections) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val res = sl.tmdb.search(sl.tmdbApiKey, query).results
            _state.update { it.copy(searchResults = res) }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
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