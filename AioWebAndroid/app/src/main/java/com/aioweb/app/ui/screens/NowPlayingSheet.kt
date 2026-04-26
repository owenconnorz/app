package com.aioweb.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.aioweb.app.data.newpipe.YtTrack
import com.aioweb.app.ui.viewmodel.MusicState
import kotlinx.coroutines.delay

/**
 * Full-screen "Now Playing" — replaces Metrolist's bottom sheet.
 * Shows artwork, lyrics (synced or plain), seek bar, repeat/shuffle, sleep-timer chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    track: YtTrack,
    player: Player?,
    state: MusicState,
    onDismiss: () -> Unit,
    onSetSleepTimer: (minutes: Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onLike: () -> Unit,
) {
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            player?.let {
                positionMs = it.currentPosition.coerceAtLeast(0L)
                durationMs = it.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }
    var showSleep by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Top row: chevron-down + sleep-timer button
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ExpandMore, "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.weight(1f))
                state.sleepTimerEndTs?.let { _ ->
                    val mins = (state.sleepTimerRemainingMs / 60_000L) + 1
                    AssistChip(
                        onClick = { onCancelSleepTimer() },
                        label = { Text("Sleep in ${mins}m · tap to cancel") },
                        leadingIcon = { Icon(Icons.Default.Bedtime, null) },
                    )
                } ?: AssistChip(
                    onClick = { showSleep = true },
                    label = { Text("Sleep timer") },
                    leadingIcon = { Icon(Icons.Default.Bedtime, null) },
                )
            }
            Spacer(Modifier.height(16.dp))
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                track.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.uploader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            // Progress slider
            Slider(
                value = if (durationMs > 0) positionMs / durationMs.toFloat() else 0f,
                onValueChange = { v -> player?.seekTo((v * durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Text(formatTime(durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    val next = when (state.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player?.repeatMode = next
                }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { player?.seekToPreviousMediaItem() }) {
                    Icon(Icons.Default.SkipPrevious, "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { if (player?.isPlaying == true) player.pause() else player?.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (player?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player?.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = {
                    val newShuffle = !state.shuffleEnabled
                    player?.shuffleModeEnabled = newShuffle
                }) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Like button row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (state.isCurrentLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (state.isCurrentLiked) "Unlike" else "Like",
                        tint = if (state.isCurrentLiked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Lyrics
            Text(
                "Lyrics",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            LyricsView(state = state, positionMs = positionMs)
        }
    }
    if (showSleep) {
        SleepTimerDialog(
            onDismiss = { showSleep = false },
            onPick = { mins -> onSetSleepTimer(mins); showSleep = false },
        )
    }
}

@Composable
private fun LyricsView(state: MusicState, positionMs: Long) {
    when {
        state.lyricsLoading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        state.lyrics?.syncedLyrics?.isNotBlank() == true -> {
            val parsed = remember(state.lyrics) {
                com.aioweb.app.data.lyrics.LyricsRepository.parseLrc(state.lyrics.syncedLyrics)
            }
            val activeIdx = remember(positionMs, parsed) {
                parsed.indexOfLast { it.first <= positionMs }.coerceAtLeast(0)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(parsed.size) { i ->
                    val (_, line) = parsed[i]
                    Text(
                        line,
                        color = if (i == activeIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = if (i == activeIdx) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        state.lyrics?.plainLyrics?.isNotBlank() == true -> {
            val text = state.lyrics.plainLyrics
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        else -> Text(
            "No lyrics found.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { mins ->
                    TextButton(
                        onClick = { onPick(mins) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("$mins minutes", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
