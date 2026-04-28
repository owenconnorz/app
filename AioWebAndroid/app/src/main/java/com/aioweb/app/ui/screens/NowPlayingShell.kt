package com.aioweb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.aioweb.app.audio.PlaybackBus
import kotlinx.coroutines.delay

/**
 * Lean now-playing UI driven entirely by a Media3 [Player] reference + the
 * global [PlaybackBus]. No ViewModel. Used by the GlobalNowPlayingSheet so
 * the full player can open on any tab — Library, Movies, AI, Settings — not
 * just when the Music tab is in the foreground.
 *
 * The richer in-Music-tab version (lyrics, sleep timer, etc.) still exists in
 * [NowPlayingSheet]; this is the universal fallback with the core controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun NowPlayingShell(
    controller: Player,
    onClose: () -> Unit,
) {
    val isPlaying by PlaybackBus.isPlaying.collectAsState()
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var title by remember { mutableStateOf<String?>(controller.mediaMetadata.title?.toString()) }
    var artist by remember { mutableStateOf<String?>(controller.mediaMetadata.artist?.toString()) }
    var artwork by remember { mutableStateOf<String?>(controller.mediaMetadata.artworkUri?.toString()) }

    LaunchedEffect(controller) {
        controller.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(md: MediaMetadata) {
                title = md.title?.toString()
                artist = md.artist?.toString()
                artwork = md.artworkUri?.toString()
            }
        })
        while (true) {
            positionMs = controller.currentPosition.coerceAtLeast(0L)
            durationMs = controller.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top row: chevron-down to dismiss
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = Color.White,
                )
            }
            Text(
                "Now Playing",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.width(48.dp)) // visual balance for the chevron
        }

        Spacer(Modifier.height(24.dp))

        // ── Album artwork (square, big)
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Title + artist
        Text(
            title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            artist.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))

        // ── Seek bar
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { v ->
                if (durationMs > 0) controller.seekTo((v * durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                positionMs.formatTime(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                durationMs.formatTime(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Transport
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { controller.shuffleModeEnabled = !controller.shuffleModeEnabled }) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White.copy(alpha = 0.7f))
            }
            IconButton(onClick = { controller.seekToPreviousMediaItem() }) {
                Icon(
                    Icons.Default.SkipPrevious, "Previous",
                    tint = Color.White, modifier = Modifier.size(40.dp),
                )
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { controller.seekToNextMediaItem() }) {
                Icon(
                    Icons.Default.SkipNext, "Next",
                    tint = Color.White, modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(48.dp)) // balance the row
        }

        Spacer(Modifier.weight(1f))
    }
}

private fun Long.formatTime(): String {
    val s = (this / 1000).coerceAtLeast(0)
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}
