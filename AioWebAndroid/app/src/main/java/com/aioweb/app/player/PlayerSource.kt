// app/src/main/java/com/aioweb/app/player/PlayerSource.kt
package com.aioweb.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
         * Simple factory used by the NativePlayerScreen to spin up an ExoPlayer
         * for a given URL. The higher‑level UI decides whether this is "adult"
         * content and can toggle headers accordingly.
         */
        fun createPlayer(
            context: Context,
            videoUrl: String,
            isAdult: Boolean = false,
        ): ExoPlayer {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
                )
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            if (isAdult) {
                httpDataSourceFactory.setDefaultRequestProperties(
                    mapOf(
                        // You can tune these per‑site if needed.
                        "Referer" to "https://example-adult-site.com/",
                        "Origin" to "https://example-adult-site.com",
                        "Accept" to "*/*",
                    )
                )
            }

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(
                    if (videoUrl.contains(".m3u8", ignoreCase = true) ||
                        videoUrl.contains("hls", ignoreCase = true)
                    ) {
                        MimeTypes.APPLICATION_M3U8
                    } else {
                        MimeTypes.VIDEO_MP4
                    }
                )
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