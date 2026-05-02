package com.aioweb.app.player.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun PlayerGestureLayer(
    modifier: Modifier = Modifier,
    context: Context,
    isLargeScreen: Boolean,
    onToggleControls: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForwardRipple: () -> Unit,
    onSeekBackRipple: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onRemoteInputDetected: () -> Unit,
    onTouchInputDetected: () -> Unit
) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val activity = context as Activity

    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapThreshold = 250L

    // Remote / DPAD detection
    LaunchedEffect(Unit) {
        activity.window.decorView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onRemoteInputDetected()
                        return@setOnKeyListener false
                    }
                }
            }
            false
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onTouchInputDetected()
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < doubleTapThreshold) {
                            onToggleControls()
                        } else {
                            onToggleControls()
                        }
                        lastTapTime = now
                    },
                    onDoubleTap = { offset ->
                        onTouchInputDetected()

                        val width = size.width
                        val leftZone = if (isLargeScreen) width * 0.45f else width * 0.4f
                        val rightZone = if (isLargeScreen) width * 0.55f else width * 0.6f

                        when {
                            offset.x < leftZone -> {
                                onSeekBack()
                                onSeekBackRipple()
                            }
                            offset.x > rightZone -> {
                                onSeekForward()
                                onSeekForwardRipple()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    onTouchInputDetected()

                    val width = size.width
                    val x = change.position.x

                    val leftZone = if (isLargeScreen) width * 0.45f else width * 0.4f
                    val rightZone = if (isLargeScreen) width * 0.55f else width * 0.6f

                    // Brightness (left)
                    if (x < leftZone) {
                        val lp = activity.window.attributes
                        val newBrightness = (lp.screenBrightness - dragAmount / 2000f)
                            .coerceIn(0.05f, 1f)
                        lp.screenBrightness = newBrightness
                        activity.window.attributes = lp
                        onBrightnessChanged(newBrightness)
                    }
                    // Volume (right)
                    else if (x > rightZone) {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val newVol = (currentVol - dragAmount / 80f)
                            .toInt()
                            .coerceIn(0, maxVol)

                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        onVolumeChanged(newVol.toFloat() / maxVol.toFloat())
                    }
                }
            }
    )
}