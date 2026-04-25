package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.newpipe.NewPipeRepository
import com.aioweb.app.data.newpipe.YtTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicState(
    val tracks: List<YtTrack> = emptyList(),
    val homeFeed: List<YtTrack> = emptyList(),
    val loading: Boolean = false,
    val homeLoading: Boolean = false,
    val error: String? = null,
    val nowPlayingUrl: String? = null,
    val resolvingUrl: String? = null,
)

class MusicViewModel : ViewModel() {
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init { loadHomeFeed() }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true) }
            try {
                val feed = NewPipeRepository.homeFeed()
                _state.update { it.copy(homeFeed = feed, homeLoading = false) }
            } catch (e: Exception) {
                // Home feed failure is non-fatal — search still works.
                _state.update { it.copy(homeLoading = false) }
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = NewPipeRepository.searchMusic(query)
                _state.update { it.copy(tracks = results, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Search failed: ${e.message}") }
            }
        }
    }

    fun play(track: YtTrack, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(resolvingUrl = track.url, error = null) }
            try {
                val audio = NewPipeRepository.resolveAudioStream(track.url)
                _state.update { it.copy(nowPlayingUrl = track.url, resolvingUrl = null) }
                onResolved(audio)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        resolvingUrl = null,
                        error = "Playback failed: ${e.message ?: e::class.simpleName}",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(@Suppress("UNUSED_PARAMETER") context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MusicViewModel() as T
            }
        }
    }
}
