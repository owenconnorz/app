package com.aioweb.app.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import androidx.media3.ui.PlayerView
import com.aioweb.app.player.settings.PlayerSettingsManager
import com.aioweb.app.player.settings.TopBarStyle
import com.aioweb.app.player.ui.*
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
    val scope = rememberCoroutineScope()
    val settings = remember { PlayerSettingsManager(context) }

    // ------------------------------------------------------------
    // READ USER TOP BAR SETTING
    // ------------------------------------------------------------
    val topBarStyle by settings.topBarStyleFlow.collectAsState(initial = TopBarStyle.A)

    // ------------------------------------------------------------
    // PLAYER INSTANCE
    // ------------------------------------------------------------
    val player = remember(streamUrl) {
        PlayerSource.createPlayer(
            context = context,
            videoUrl = streamUrl,
            isAdult = false
        )
    }

    // ------------------------------------------------------------
    // UI STATE
    // ------------------------------------------------------------
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

    // ------------------------------------------------------------
    // AUTO-HIDE CONTROLS
    // ------------------------------------------------------------
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && !isRemoteMode) {
            delay(3000)
            controlsVisible = false
        }
    }

    // ------------------------------------------------------------
    // PLAYER LISTENERS
    // ------------------------------------------------------------
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                isBuffering = isLoading
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ------------------------------------------------------------
    // PROGRESS UPDATER
    // ------------------------------------------------------------
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 1L
            delay(200)
        }
    }

    // ------------------------------------------------------------
    // MAIN UI LAYOUT
    // ------------------------------------------------------------
    Box(modifier = modifier.fillMaxSize()) {

        // ------------------------------------------------------------
        // PLAYER VIEW
        // ------------------------------------------------------------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = this@NativePlayerScreen.player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ------------------------------------------------------------
        // GESTURE LAYER
        // ------------------------------------------------------------
        PlayerGestureLayer(
            modifier = Modifier.fillMaxSize(),
            context = context,
            isLargeScreen = isLargeScreen,
            onToggleControls = { controlsVisible = !controlsVisible },
            onSeekForward = { player.seekTo(player.currentPosition + 10_000) },
            onSeekBack = { player.seekTo(player.currentPosition - 10_000) },
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

        // ------------------------------------------------------------
        // OVERLAY UI
        // ------------------------------------------------------------
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

                // -------------------------
                // TOP BAR (A/B/C)
                // -------------------------
                when (topBarStyle) {
                    TopBarStyle.A -> NuvioTopBarA(title, subtitle, onBack)
                    TopBarStyle.B -> NuvioTopBarB(title, subtitle, onBack)
                    TopBarStyle.C -> NuvioTopBarC(title, onBack)
                }

                // -------------------------
                // CENTER PLAY/PAUSE
                // -------------------------
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    ) {
                        Icon(
                            imageVector =
                                if (player.isPlaying)
                                    Icons.Default.Pause
                                else
                                    Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLargeScreen) 96.dp else 64.dp)
                        )
                    }
                }

                // -------------------------
                // BOTTOM SEEK BAR
                // -------------------------
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = {
                            player.seekTo(it.toLong())
                        },
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

                // -------------------------
                // SOURCE SELECTOR (BOTTOM RIGHT)
                // -------------------------
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

        // ------------------------------------------------------------
        // RIPPLE INDICATORS
        // ------------------------------------------------------------
        if (rippleForward) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 80.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                RippleSeekIndicator(true, rippleForward, isLargeScreen)
            }
        }

        if (rippleBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 80.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                RippleSeekIndicator(false, rippleBack, isLargeScreen)
            }
        }

        // ------------------------------------------------------------
        // BRIGHTNESS + VOLUME HUD
        // ------------------------------------------------------------
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                BrightnessHUD(brightnessHUD, brightnessLevel, isLargeScreen)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VolumeHUD(volumeHUD, volumeLevel, isLargeScreen)
            }
        }

        // ------------------------------------------------------------
        // BUFFERING HUD
        // ------------------------------------------------------------
        BufferingHUD(isBuffering)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}