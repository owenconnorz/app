package com.aioweb.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * Unified native player surface.
 *
 * The UI currently passes a rich set of parameters (title, subtitle, sources,
 * etc.) from AioWebApp, but this composable only needs the stream URL to
 * function. Extra parameters are accepted so the call sites compile and can
 * be wired into a richer UI later without breaking the API again.
 */
@OptIn(UnstableApi::class)
@Composable
fun NativePlayerScreen(
    streamUrl: String,
    title: String,
    subtitle: String? = null,
    sources: List<PlayerSource> = emptyList(),
    selectedSourceId: String? = null,
    onSwitchSource: (PlayerSource) -> Unit = {},
    progressKey: WatchProgressKey? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Recreate the player when the stream URL changes.
    val exoPlayer = remember(streamUrl) {
        PlayerSource.createPlayer(
            context = context,
            videoUrl = streamUrl,
            isAdult = false, // Eporner route could be toggled to true later if needed.
        )
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier,
    )
}