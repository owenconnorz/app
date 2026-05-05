package com.aioweb.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
data class PlayerSource(
    val id: String,
    val url: String,
    val label: String,
    val addonName: String,
    val qualityTag: String?,
    val isMagnet: Boolean = false,
) {
    companion object {

        fun createPlayer(
            context: Context,
            videoUrl: String,
            isAdult: Boolean = false,
            extraHeaders: Map<String, String> = emptyMap(),
        ): ExoPlayer {

            val headers = if (isAdult) {
                mapOf(
                    "Referer" to "https://www.eporner.com/",
                    "Origin" to "https://www.eporner.com",
                    "Accept" to "*/*",
                ) + extraHeaders
            } else extraHeaders

            val dataSourceFactory: DataSource.Factory =
                PlayerPlaybackNetworking.createDataSourceFactory(
                    context = context,
                    defaultHeaders = headers
                )

            val mime = when {
                videoUrl.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                videoUrl.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
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