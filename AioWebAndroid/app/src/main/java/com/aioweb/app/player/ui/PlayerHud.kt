package com.aioweb.app.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RippleSeekIndicator(
    isForward: Boolean,
    trigger: Boolean,
    isLargeScreen: Boolean
) {
    val rippleScale by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(350, easing = LinearOutSlowInEasing)
    )

    val rippleAlpha by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(350)
    )

    val baseSize = if (isLargeScreen) 260.dp else 160.dp

    if (rippleAlpha > 0f) {
        Box(
            modifier = Modifier
                .size(baseSize)
                .scale(rippleScale)
                .background(Color.White.copy(alpha = rippleAlpha * 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White.copy(alpha = rippleAlpha),
                modifier = Modifier.size(if (isLargeScreen) 96.dp else 64.dp)
            )
        }
    }
}

@Composable
fun BrightnessHUD(
    visible: Boolean,
    level: Float,
    isLargeScreen: Boolean
) {
    AnimatedVisibility(visible = visible) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (level > 0.5f) Icons.Default.BrightnessHigh else Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isLargeScreen) 64.dp else 48.dp)
                )
                Text(
                    text = "${(level * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun VolumeHUD(
    visible: Boolean,
    level: Float,
    isLargeScreen: Boolean
) {
    AnimatedVisibility(visible = visible) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (level > 0.05f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isLargeScreen) 64.dp else 48.dp)
                )
                Text(
                    text = "${(level * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun BufferingHUD(
    isBuffering: Boolean
) {
    AnimatedVisibility(visible = isBuffering) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 4.dp
            )
        }
    }
}