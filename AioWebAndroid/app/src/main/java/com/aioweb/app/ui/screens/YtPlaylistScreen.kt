package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioweb.app.data.ServiceLocator
import com.aioweb.app.data.downloads.MusicDownloader
import com.aioweb.app.data.ytmusic.YtMusicLibraryRepository
import com.aioweb.app.data.ytmusic.YtPlayback
import com.aioweb.app.data.ytmusic.YtmSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * YouTube Music playlist detail — Metrolist parity: hero row, Play / Shuffle actions,
 * per-row download icon with progress + downloaded states, offline-first playback.
 *
 * Playback routes through [YtPlayback.playSong] → the global [MusicController], so
 * starting a song here immediately drives the app-wide MiniPlayer on every tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtPlaylistScreen(
    playlistId: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (YtmSong) -> Unit = {},
) {
    val context = LocalContext.current
    val sl = remember(context) { ServiceLocator.get(context) }
    val cookie by sl.settings.ytMusicCookie.collectAsState(initial = "")
    var tracks by remember(playlistId) { mutableStateOf<List<YtmSong>?>(null) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Per-URL download progress (0f..1f). Null = not downloading. Collected from the
    // MusicDownloader singleton so we can render the in-progress ring next to each row.
    val downloadProgress by MusicDownloader.progressFlow
        .collectAsState(initial = emptyMap())

    LaunchedEffect(playlistId, cookie) {
        if (cookie.isBlank()) { error = "Not signed in."; return@LaunchedEffect }
        error = null
        tracks = withContext(Dispatchers.IO) {
            runCatching { YtMusicLibraryRepository.playlistTracks(cookie, playlistId) }
                .getOrElse {
                    error = it.message
                    emptyList()
                }
        }
    }

    fun playSongHandoff(s: YtmSong) {
        // Call the caller-supplied hook for telemetry/analytics, THEN run the
        // actual playback. Keeps the `onPlay` contract stable for any other caller.
        onPlay(s)
        scope.launch {
            runCatching { YtPlayback.playSong(context, s) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    val list = tracks
                    if (!list.isNullOrEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                // "Download all" — walk the list and kick off a
                                // download per track. MusicDownloader guards against
                                // re-downloading files that already exist.
                                list.forEach { s ->
                                    runCatching { YtPlayback.downloadSong(context, s) }
                                }
                            }
                        }) {
                            Icon(Icons.Default.CloudDownload, "Download playlist")
                        }
                    }
                },
            )
        }
    ) { padding ->
        val list = tracks
        when {
            list == null && error == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    error ?: "Couldn't load playlist",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            list != null -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { list.firstOrNull()?.let(::playSongHandoff) },
                            enabled = list.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { list.randomOrNull()?.let(::playSongHandoff) },
                            enabled = list.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
                items(list, key = { "pt_${it.videoId}" }) { s ->
                    PlaylistTrackRow(
                        song = s,
                        isDownloading = downloadProgress["https://music.youtube.com/watch?v=${s.videoId}"],
                        onPlay = { playSongHandoff(s) },
                        onDownload = {
                            scope.launch { runCatching { YtPlayback.downloadSong(context, s) } }
                        },
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    song: YtmSong,
    isDownloading: Float?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    // Snapshot "is this song downloaded" reactively — poll on recomposition so the
    // icon flips to `DownloadDone` the moment the file lands.
    var downloaded by remember(song.videoId) {
        mutableStateOf(YtPlayback.isDownloaded(context, song))
    }
    LaunchedEffect(song.videoId, isDownloading) {
        // Re-check after the progress flow drops to null (= download finished/aborted).
        if (isDownloading == null) downloaded = YtPlayback.isDownloaded(context, song)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                append(song.artist)
                if (!song.album.isNullOrBlank()) { append(" · "); append(song.album) }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Download state: in-progress ring ▸ downloaded check ▸ download icon.
        when {
            isDownloading != null -> Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = isDownloading.coerceIn(0f, 1f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            }
            downloaded -> IconButton(onClick = {}) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            else -> IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
