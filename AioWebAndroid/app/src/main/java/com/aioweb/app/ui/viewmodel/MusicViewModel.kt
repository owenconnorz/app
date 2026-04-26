package com.aioweb.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.library.TrackDao
import com.aioweb.app.data.library.TrackEntity
import com.aioweb.app.data.lyrics.LrcEntry
import com.aioweb.app.data.lyrics.LyricsRepository
import com.aioweb.app.data.newpipe.NewPipeRepository
import com.aioweb.app.data.newpipe.YtTrack
import kotlinx.coroutines.Job
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
    val nowPlayingTrack: YtTrack? = null,
    val resolvingUrl: String? = null,

    // Lyrics
    val lyrics: LrcEntry? = null,
    val lyricsLoading: Boolean = false,

    // Sleep timer
    val sleepTimerEndTs: Long? = null,
    val sleepTimerRemainingMs: Long = 0,

    // Repeat / Shuffle (mirrored from MediaController)
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,

    // Library
    val recent: List<TrackEntity> = emptyList(),
    val liked: List<TrackEntity> = emptyList(),
    val mostPlayed: List<TrackEntity> = emptyList(),
    val isCurrentLiked: Boolean = false,
)

class MusicViewModel(context: Context) : ViewModel() {
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private val dao: TrackDao = LibraryDb.get(context).tracks()
    private var sleepJob: Job? = null

    init {
        loadHomeFeed()
        viewModelScope.launch {
            dao.recent().collect { list -> _state.update { it.copy(recent = list) } }
        }
        viewModelScope.launch {
            dao.liked().collect { list -> _state.update { it.copy(liked = list) } }
        }
        viewModelScope.launch {
            dao.mostPlayed().collect { list -> _state.update { it.copy(mostPlayed = list) } }
        }
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true) }
            try {
                val feed = NewPipeRepository.homeFeed()
                _state.update { it.copy(homeFeed = feed, homeLoading = false) }
            } catch (_: Exception) {
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
                _state.update {
                    it.copy(
                        nowPlayingUrl = track.url,
                        nowPlayingTrack = track,
                        resolvingUrl = null,
                    )
                }
                onResolved(audio)
                // Library: persist + bump play count
                val ts = System.currentTimeMillis()
                dao.upsert(
                    TrackEntity(
                        url = track.url, title = track.title, artist = track.uploader,
                        durationSec = track.durationSec, thumbnail = track.thumbnail,
                    )
                )
                dao.bumpPlayed(track.url, ts)
                fetchLyrics(track)
                refreshLikedFlag(track.url)
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

    private fun fetchLyrics(track: YtTrack) {
        _state.update { it.copy(lyricsLoading = true, lyrics = null) }
        viewModelScope.launch {
            val lrc = runCatching {
                LyricsRepository.fetch(track.title, track.uploader, track.durationSec)
            }.getOrNull()
            _state.update { it.copy(lyrics = lrc, lyricsLoading = false) }
        }
    }

    // ---- Like / Unlike --------------------------------------------------------------------
    fun toggleLikeCurrent() {
        val url = _state.value.nowPlayingUrl ?: return
        viewModelScope.launch {
            val currentlyLiked = _state.value.isCurrentLiked
            dao.setLikedAt(url, if (currentlyLiked) null else System.currentTimeMillis())
            _state.update { it.copy(isCurrentLiked = !currentlyLiked) }
        }
    }

    private fun refreshLikedFlag(url: String) {
        viewModelScope.launch {
            dao.isLiked(url).collect { liked ->
                _state.update { it.copy(isCurrentLiked = liked == true) }
            }
        }
    }

    // ---- Sleep timer ---------------------------------------------------------------------
    fun startSleepTimer(minutes: Int, onElapsed: () -> Unit) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerEndTs = null, sleepTimerRemainingMs = 0) }
            return
        }
        val endTs = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepTimerEndTs = endTs, sleepTimerRemainingMs = endTs - System.currentTimeMillis()) }
        sleepJob = viewModelScope.launch {
            while (true) {
                val remaining = endTs - System.currentTimeMillis()
                if (remaining <= 0) {
                    _state.update { it.copy(sleepTimerEndTs = null, sleepTimerRemainingMs = 0) }
                    onElapsed()
                    return@launch
                }
                _state.update { it.copy(sleepTimerRemainingMs = remaining) }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _state.update { it.copy(sleepTimerEndTs = null, sleepTimerRemainingMs = 0) }
    }

    // ---- Repeat / Shuffle mirroring (called by Composable when controller events fire) ---
    fun setRepeatMode(mode: Int) { _state.update { it.copy(repeatMode = mode) } }
    fun setShuffle(enabled: Boolean) { _state.update { it.copy(shuffleEnabled = enabled) } }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MusicViewModel(context.applicationContext) as T
            }
        }
    }
}
