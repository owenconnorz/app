package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.api.TmdbMovie
import com.aioweb.app.data.plugins.InstalledPlugin
import com.aioweb.app.data.plugins.PluginRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val SOURCE_BUILTIN = "builtin"

data class MoviesState(
    val trending: List<TmdbMovie> = emptyList(),
    val popular: List<TmdbMovie> = emptyList(),
    val topRated: List<TmdbMovie> = emptyList(),
    val nowPlaying: List<TmdbMovie> = emptyList(),
    val searchResults: List<TmdbMovie> = emptyList(),
    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val selectedSourceId: String = SOURCE_BUILTIN,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class MoviesViewModel(
    private val sl: ServiceLocator,
    private val pluginRepo: PluginRepository,
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
        _state.update { it.copy(selectedSourceId = sourceId) }
        if (sourceId != SOURCE_BUILTIN) {
            val name = _state.value.installedPlugins.firstOrNull { it.internalName == sourceId }?.name ?: sourceId
            _state.update {
                it.copy(notice = "Source: $name. Plugin streaming runtime ships in a future update — for now content is from the built-in catalogue, filtered by this plugin's media types.")
            }
        } else {
            _state.update { it.copy(notice = null) }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val res = sl.tmdb.search(sl.tmdbApiKey, query).results
                _state.update { it.copy(searchResults = res, error = null) }
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
                ) as T
            }
        }
    }
}
