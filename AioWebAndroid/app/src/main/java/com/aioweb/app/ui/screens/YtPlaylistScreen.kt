package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.aioweb.app.data.ytmusic.YtMusicLibraryRepository
import com.aioweb.app.data.ytmusic.YtmSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * YouTube Music playlist detail — Metrolist parity. Shows a large hero with the
 * playlist title, Play / Shuffle actions, and a vertical list of tracks.
 *
 * Tapping a track hands off to [onPlay] which the host wires into the music playback
 * service (TODO — currently stubbed while we refactor the single-track session).
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                            onClick = { list.firstOrNull()?.let(onPlay) },
                            enabled = list.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { list.randomOrNull()?.let(onPlay) },
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(s) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = s.thumbnail,
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
                                s.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = buildString {
                                append(s.artist)
                                if (!s.album.isNullOrBlank()) append(" · ").also { append(s.album) }
                            }
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow, "Play",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
