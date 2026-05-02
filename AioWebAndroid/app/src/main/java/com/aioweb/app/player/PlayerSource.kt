// app/src/main/java/com/aioweb/app/player/PlayerSource.kt
package com.aioweb.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
object PlayerSource {

    fun createPlayer(context: Context, videoUrl: String, isAdult: Boolean = false): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        if (isAdult) {
            // Adult sites often need Referer + stronger headers
            httpDataSourceFactory.setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://common-adult-domain.com/", // ← Change to match your adult scraper source
                    "Origin" to "https://common-adult-domain.com",
                    "Accept" to "application/x-mpegURL, video/*",
                    "Sec-Fetch-Site" to "same-origin"
                )
            )
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (videoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()

        val mediaSource = if (videoUrl.contains(".m3u8") || videoUrl.contains("hls")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(10)) // More retries for flaky adult streams
            .build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }
}