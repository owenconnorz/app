// app/src/main/java/com/aioweb/app/player/PlayerSource.kt
package com.aioweb.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Represents a concrete playable source (HTTP, HLS, magnet, YouTube, etc.)
 * plus some metadata used by the unified player UI.
 */
@UnstableApi
data class PlayerSource(
    val id: String,
    val url: String,
    val label: String,
    val addonName: String,
    val qualityTag: String?,
    val isMagnet: Boolean,
) {
    companion object {

        /**
         * This is the FIXED version of createPlayer.
         * It now uses PlayerPlaybackNetworking (OkHttp) instead of DefaultHttpDataSource.
         */
        fun createPlayer(
            context: Context,
            videoUrl: String,
            isAdult: Boolean = false,
            extraHeaders: Map<String, String> = emptyMap(),
        ): ExoPlayer {

            // Merge adult headers if needed
            val headers = if (isAdult) {
                mapOf(
                    "Referer" to "https://www.eporner.com/",
                    "Origin" to "https://www.eporner.com",
                    "Accept" to "*/*",
                ) + extraHeaders
            } else {
                extraHeaders
            }

            // 🔥 Use your new OkHttp-powered DataSource
            val dataSourceFactory =
                PlayerPlaybackNetworking.createDataSourceFactory(
                    context = context,
                    defaultHeaders = headers
                )

            // Detect MIME type
            val mime = when {
                videoUrl.contains(".m3u8", ignoreCase = true) ||
                videoUrl.contains("hls", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8

                videoUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD

                else -> MimeTypes.VIDEO_MP4
            }

            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(mime)
                .build()

            return ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
        }
    }
}