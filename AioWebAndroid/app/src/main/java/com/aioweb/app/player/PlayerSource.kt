// app/src/main/java/com/aioweb/app/player/PlayerSource.kt
package com.aioweb.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
object PlayerSource {

    fun createPlayer(
        context: Context,
        videoUrl: String,
        isAdult: Boolean = false
    ): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        if (isAdult) {
            httpDataSourceFactory.setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://example-adult-site.com/", // ← CHANGE THIS to your adult source
                    "Origin" to "https://example-adult-site.com",
                    "Accept" to "*/*"
                )
            )
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (videoUrl.contains(".m3u8") || videoUrl.contains("hls")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }
}