// app/src/main/java/com/aioweb/app/player/NativePlayerScreen.kt
package com.aioweb.app.player

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun NativePlayerScreen(
    videoUrl: String,
    isAdultContent: Boolean = false,   // ← Pass true for NSFW pages
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        PlayerSource.createPlayer(context, videoUrl, isAdultContent)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Lock to landscape for better video experience
    SideEffect {
        // You can call activity.requestedOrientation here if needed
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("StreamCloud Player") }, navigationIcon = {
                IconButton(onClick = onBack) { /* Back icon */ }
            })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerAutoShow = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}