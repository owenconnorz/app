package com.aioweb.app.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aioweb.app.player.settings.PlayerSettingsManager
import com.aioweb.app.player.settings.TopBarStyle
import com.aioweb.app.player.ui.BrightnessHUD
import com.aioweb.app.player.ui.BufferingHUD
import com.aioweb.app.player.ui.NuvioTopBarA
import com.aioweb.app.player.ui.NuvioTopBarB
import com.aioweb.app.player.ui.NuvioTopBarC
import com.aioweb.app.player.ui.PlayerGestureLayer
import com.aioweb.app.player.ui.RippleSeekIndicator
import com.aioweb.app.player.ui.SourceSelectorPopup
import com.aioweb.app.player.ui.VolumeHUD
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val settings = remember { PlayerSettingsManager(context) }

    // Force landscape while in player
    DisposableEffect(Unit) {
        val oldOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.requestedOrientation = oldOrientation
        }
    }

    val topBarStyle by settings.topBarStyleFlow.collectAsState(initial = TopBarStyle.A)

    // Player instance
    val exoPlayer = remember(streamUrl) {
        PlayerSource.createPlayer(
            context = context,
            videoUrl = streamUrl,
            isAdult = false
        )
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var isBuffering by remember { mutableStateOf(false) }

    var brightnessHUD by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableStateOf(0.5f) }

    var volumeHUD by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf(0.5f) }

    var rippleForward by remember { mutableStateOf(false) }
    var rippleBack by remember { mutableStateOf(false) }

    var isRemoteMode by remember { mutableStateOf(false) }

    val config = LocalConfiguration.current
    val isLargeScreen = config.screenWidthDp > 700

    // Auto-hide controls (only in touch mode)
    LaunchedEffect(controlsVisible, isRemoteMode) {
        if (controlsVisible && !isRemoteMode) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                isBuffering = isLoading
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Progress updater
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.takeIf { it > 0 } ?: 1L
            delay(200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture layer
        PlayerGestureLayer(
            modifier = Modifier.fillMaxSize(),
            context = context,
            isLargeScreen = isLargeScreen,
            onToggleControls = { controlsVisible = !controlsVisible },
            onSeekForward = { exoPlayer.seekTo(exoPlayer.currentPosition + 10_000) },
            onSeekBack = { exoPlayer.seekTo(exoPlayer.currentPosition - 10_000) },
            onSeekForwardRipple = {
                rippleForward = true
                scope.launch {
                    delay(350)
                    rippleForward = false
                }
            },
            onSeekBackRipple = {
                rippleBack = true
                scope.launch {
                    delay(350)
                    rippleBack = false
                }
            },
            onBrightnessChanged = {
                brightnessLevel = it
                brightnessHUD = true
                scope.launch {
                    delay(800)
                    brightnessHUD = false
                }
            },
            onVolumeChanged = {
                volumeLevel = it
                volumeHUD = true
                scope.launch {
                    delay(800)
                    volumeHUD = false
                }
            },
            onRemoteInputDetected = {
                isRemoteMode = true
                controlsVisible = true
            },
            onTouchInputDetected = {
                isRemoteMode = false
            }
        )

        // Overlay UI (Nuvio)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {

                // Top bar
                when (topBarStyle) {
                    TopBarStyle.A -> NuvioTopBarA(title, subtitle, onBack)
                    TopBarStyle.B -> NuvioTopBarB(title, subtitle, onBack)
                    TopBarStyle.C -> NuvioTopBarC(title, onBack)
                }

                // Center play/pause
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    ) {
                        Icon(
                            imageVector = if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLargeScreen) 96.dp else 64.dp)
                        )
                    }
                }

                // Bottom seek bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Slider(
                        value = currentPosition.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White)
                        Text(formatTime(duration), color = Color.White)
                    }
                }

                // Source selector (bottom-right)
                SourceSelectorPopup(
                    sources = sources,
                    selectedSourceId = selectedSourceId,
                    onSwitchSource = onSwitchSource,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }

        // Ripple indicators
        if (rippleForward) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 80.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                RippleSeekIndicator(isForward = true, trigger = rippleForward, isLargeScreen = isLargeScreen)
            }
        }

        if (rippleBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 80.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                RippleSeekIndicator(isForward = false, trigger = rippleBack, isLargeScreen = isLargeScreen)
            }
        }

        // Brightness + Volume HUD
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                BrightnessHUD(visible = brightnessHUD, level = brightnessLevel, isLargeScreen = isLargeScreen)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VolumeHUD(visible = volumeHUD, level = volumeLevel, isLargeScreen = isLargeScreen)
            }
        }

        // Buffering HUD
        BufferingHUD(isBuffering = isBuffering)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}