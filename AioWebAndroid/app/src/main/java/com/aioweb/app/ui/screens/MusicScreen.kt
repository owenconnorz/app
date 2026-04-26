package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.aioweb.app.data.newpipe.YtTrack
import com.aioweb.app.ui.viewmodel.MusicViewModel

private val SUGGESTIONS = listOf(
    "Top hits 2026", "Lo-fi beats", "Chill", "Workout",
    "Throwback", "K-pop", "Hip hop", "Jazz", "EDM", "Acoustic"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val vm: MusicViewModel = viewModel(factory = MusicViewModel.factory(context))
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var showFullPlayer by remember { mutableStateOf(false) }

    val player = remember {
        buildMusicExoPlayer(context)
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerError = "Audio playback failed (${error.errorCodeName}): ${error.message}"
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    val nowPlaying = state.tracks.firstOrNull { it.url == state.nowPlayingUrl }
        ?: state.homeFeed.firstOrNull { it.url == state.nowPlayingUrl }

    if (showFullPlayer && nowPlaying != null) {
        FullScreenPlayer(
            track = nowPlaying,
            isPlaying = isPlaying,
            player = player,
            onClose = { showFullPlayer = false },
            onSkipNext = {
                val allTracks = (state.tracks.takeIf { it.isNotEmpty() } ?: state.homeFeed)
                val idx = allTracks.indexOfFirst { it.url == nowPlaying.url }
                allTracks.getOrNull(idx + 1)?.let { next ->
                    vm.play(next) { audioUrl -> playTrack(player, next, audioUrl) }
                }
            },
            onSkipPrev = {
                val allTracks = (state.tracks.takeIf { it.isNotEmpty() } ?: state.homeFeed)
                val idx = allTracks.indexOfFirst { it.url == nowPlaying.url }
                allTracks.getOrNull(idx - 1)?.let { prev ->
                    vm.play(prev) { audioUrl -> playTrack(player, prev, audioUrl) }
                }
            }
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (nowPlaying != null) 96.dp else 12.dp),
            ) {
                item { MusicHeader() }
                item {
                    MusicSearchField(
                        query = query,
                        loading = state.loading,
                        onQueryChange = { query = it },
                    )
                    LaunchedEffect(query) {
                        if (query.length >= 2) {
                            kotlinx.coroutines.delay(400)
                            vm.search(query)
                        }
                    }
                }
                
                val combinedError = state.error ?: playerError
                if (combinedError != null) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                combinedError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { playerError = null; vm.search(query) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                if (query.isBlank() && state.tracks.isEmpty()) {
                    item { SuggestionsRow(onPick = { query = it; vm.search(it) }) }

                    if (state.homeFeed.isNotEmpty()) {
                        item { SectionTitle("Trending today") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.homeFeed.take(10), key = { "home_${it.url}" }) { track ->
                                    HeroCard(
                                        track = track,
                                        isPlaying = isPlaying && state.nowPlayingUrl == track.url,
                                        onClick = {
                                            if (state.nowPlayingUrl == track.url && player.isPlaying) player.pause()
                                            else if (state.nowPlayingUrl == track.url) player.play()
                                            else vm.play(track) { audioUrl -> playTrack(player, track, audioUrl) }
                                        }
                                    )
                                }
                            }
                        }
                        item { SectionTitle("More from YouTube") }
                        items(state.homeFeed.drop(10), key = { "homerow_${it.url}" }) { track ->
                            SongRow(
                                track = track,
                                nowPlayingUrl = state.nowPlayingUrl,
                                isPlaying = isPlaying && state.nowPlayingUrl == track.url,
                                loading = state.resolvingUrl == track.url,
                                onClick = {
                                    if (state.nowPlayingUrl == track.url && player.isPlaying) player.pause()
                                    else if (state.nowPlayingUrl == track.url) player.play()
                                    else vm.play(track) { audioUrl -> playTrack(player, track, audioUrl) }
                                }
                            )
                        }
                    } else if (state.homeLoading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(40.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    } else {
                        item {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(96.dp).clip(CircleShape).background(
                                        Brush.linearGradient(
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                        )
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Tap a vibe or search", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                                Text("Stream from YouTube · audio only", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (state.tracks.isNotEmpty()) {
                    item {
                        SectionTitle("Top results")
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.tracks.take(6), key = { "hero_${it.url}" }) { track ->
                                HeroCard(
                                    track = track,
                                    isPlaying = isPlaying && state.nowPlayingUrl == track.url,
                                    onClick = {
                                        if (state.nowPlayingUrl == track.url && player.isPlaying) {
                                            player.pause()
                                        } else if (state.nowPlayingUrl == track.url) {
                                            player.play()
                                        } else {
                                            vm.play(track) { audioUrl -> playTrack(player, track, audioUrl) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    item { SectionTitle("All songs") }
                    items(state.tracks.drop(6), key = { it.url }) { track ->
                        SongRow(
                            track = track,
                            nowPlayingUrl = state.nowPlayingUrl,
                            isPlaying = isPlaying && state.nowPlayingUrl == track.url,
                            loading = state.resolvingUrl == track.url,
                            onClick = {
                                if (state.nowPlayingUrl == track.url && player.isPlaying) {
                                    player.pause()
                                } else if (state.nowPlayingUrl == track.url) {
                                    player.play()
                                } else {
                                    vm.play(track) { audioUrl -> playTrack(player, track, audioUrl) }
                                }
                            }
                        )
                    }
                }
            }

            nowPlaying?.let { track ->
                MiniPlayer(
                    track = track,
                    isPlaying = isPlaying,
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onExpand = { showFullPlayer = true },
                    onSkipNext = {
                        val allTracks = (state.tracks.takeIf { it.isNotEmpty() } ?: state.homeFeed)
                        val idx = allTracks.indexOfFirst { it.url == track.url }
                        allTracks.getOrNull(idx + 1)?.let { next ->
                            vm.play(next) { audioUrl -> playTrack(player, next, audioUrl) }
                        }
                    },
                    onSkipPrev = {
                        val allTracks = (state.tracks.takeIf { it.isNotEmpty() } ?: state.homeFeed)
                        val idx = allTracks.indexOfFirst { it.url == track.url }
                        allTracks.getOrNull(idx - 1)?.let { prev ->
                            vm.play(prev) { audioUrl -> playTrack(player, prev, audioUrl) }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun FullScreenPlayer(
    track: YtTrack,
    isPlaying: Boolean,
    player: ExoPlayer,
    onClose: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ExpandLess, "Collapse", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(48.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                track.uploader,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSkipPrev, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { if (player.isPlaying) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            
            IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildMusicExoPlayer(context: android.content.Context): ExoPlayer {
    val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val mediaSourceFactory =
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpFactory)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .apply { playWhenReady = true }
}

private fun playTrack(player: ExoPlayer, track: YtTrack, audioUrl: String) {
    player.setMediaItem(
        MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.uploader)
                    .setArtworkUri(track.thumbnail?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    )
    player.prepare()
    player.play()
}

@Composable
private fun MusicHeader() {
    Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)) {
        Text(
            "Listen now",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Your music. From everywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSearchField(query: String, loading: Boolean, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search songs, artists, albums") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (loading) CircularProgressIndicator(
                Modifier.size(20.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@Composable
private fun SuggestionsRow(onPick: (String) -> Unit) {
    Column {
        SectionTitle("Trending searches")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SUGGESTIONS) { s ->
                SuggestionChip(label = s, onClick = { onPick(s) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun HeroCard(track: YtTrack, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.uploader,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SongRow(
    track: YtTrack,
    nowPlayingUrl: String?,
    isPlaying: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val highlighted = nowPlayingUrl == track.url
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (track.thumbnail != null) {
                AsyncImage(
                    model = track.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    track: YtTrack,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSkipPrev) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onSkipNext) {
            Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}