package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioweb.app.data.api.EpornerApi
import com.aioweb.app.data.api.EpornerVideo
import com.aioweb.app.data.network.Net
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdultState(
    val videos: List<EpornerVideo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val resolvingId: String? = null,
)

class AdultViewModel : ViewModel() {
    private val _state = MutableStateFlow(AdultState())
    val state: StateFlow<AdultState> = _state.asStateFlow()

    private val api: EpornerApi =
        Net.retrofit("https://www.eporner.com/").create(EpornerApi::class.java)

    private var searchJob: Job? = null

    init { search("popular") }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(loading = true, error = null) }
            try {
                val q = if (query.isBlank()) "popular" else query
                val r = api.search(query = q, perPage = 30)
                _state.update { it.copy(videos = r.videos, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Failed: ${e.message}") }
            }
        }
    }

    /**
     * Resolves a direct MP4 URL for the given video.
     * Returns `null` if no direct stream is exposed by Eporner for that video
     * (rare — but happens for some studio-exclusive content).
     */
    suspend fun resolveStreamUrl(videoId: String, fallbackEmbed: String): String? {
        _state.update { it.copy(resolvingId = videoId, error = null) }
        return try {
            val resp = api.details(id = videoId)
            resp.videos.firstOrNull()?.bestMp4()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Stream resolve failed: ${e.message}") }
            null
        } finally {
            _state.update { it.copy(resolvingId = null) }
        }
    }

    companion object {
        fun factory(@Suppress("UNUSED_PARAMETER") context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AdultViewModel() as T
            }
        }
    }
}
