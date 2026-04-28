package com.aioweb.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.aioweb.app.audio.MusicController
import kotlinx.coroutines.delay

/**
 * App-wide mini-player bar. Renders at the bottom of every tab as soon as a track is
 * actively playing or paused in our foreground [MusicPlaybackService].
 *
 * Listens to the shared [MusicController] (a `MediaController` that binds to the
 * service) so it works even when the user navigates away from the Music tab —
 * Metrolist parity.
 *
 * It intentionally stays light: tap-to-toggle, skip-next, and tap-thumbnail to
 * open the full player. More advanced controls (like/download/artwork swipe) live
 * inside the Music tab's rich MiniPlayer that we already had.
 */
@Composable
fun GlobalMiniPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<Player?>(null) }
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // Bind to the shared controller once; failure to bind simply hides the bar.
    LaunchedEffect(Unit) {
        runCatching { MusicController.get(context.applicationContext) }
            .onSuccess { c ->
                controller = c
                title = c.mediaMetadata.title?.toString()
                artist = c.mediaMetadata.artist?.toString()
                artworkUri = c.mediaMetadata.artworkUri?.toString()
                isPlaying = c.isPlaying
                positionMs = c.currentPosition
                durationMs = c.duration.coerceAtLeast(0L)
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaMetadataChanged(md: androidx.media3.common.MediaMetadata) {
                        title = md.title?.toString()
                        artist = md.artist?.toString()
                        artworkUri = md.artworkUri?.toString()
                    }
                })
            }
    }
    // Poll the player position so the progress bar advances.
    LaunchedEffect(controller, isPlaying) {
        while (controller != null) {
            positionMs = controller!!.currentPosition
            durationMs = controller!!.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = title != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onExpand)
                // Vertical drag detector — an upward fling/drag past ~24dp
                // triggers the same expand action as tapping. Matches the
                // Metrolist / Spotify gesture vocabulary.
                .pointerInput(Unit) {
                    var accumulated = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDragEnd = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                    ) { _, drag ->
                        accumulated += drag
                        // Negative drag = upward swipe. ~60px is roughly 24dp on
                        // most phones and prevents accidental triggers when the
                        // user is just trying to scroll the page underneath.
                        if (accumulated < -60f) {
                            accumulated = 0f
                            onExpand()
                        }
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Skip next",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // Thin progress under the row.
            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
