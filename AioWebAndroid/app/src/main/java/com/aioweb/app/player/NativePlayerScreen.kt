// app/src/main/java/com/aioweb/app/player/NativePlayerScreen.kt
package com.aioweb.app.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun NativePlayerScreen(
    videoUrl: String,
    // Add any other parameters your original version had here
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val exoPlayer = remember(videoUrl) {
        PlayerSource.createPlayer(context, videoUrl, isAdult = false) // default false
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx: Context ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}