package com.aioweb.app.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aioweb.app.MainActivity
import java.io.File

/**
 * Foreground media-session service for music playback.
 *
 * Responsibilities:
 *  - Owns the singleton ExoPlayer so audio survives navigation / activity recreation
 *  - Streams via a `CacheDataSource` (256 MB on-disk LRU cache) so repeats and seeks
 *    don't re-download
 *  - Publishes a [MediaSession] which Android automatically renders as a system
 *    media notification (play/pause/skip on lock screen + notification shade)
 */
@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var cache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()

        // ---- Audio cache (LRU, 256 MB) -------------------------------------------------------
        val cacheDir = File(cacheDir, "audio-cache").apply { mkdirs() }
        cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), // 256 MB
            StandaloneDatabaseProvider(this),
        )

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Music defaults: play when ready + repeat OFF (toggle-able from UI later).
                playWhenReady = false
            }

        // Tap on the notification reopens MainActivity.
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
            session = null
        }
        cache?.release()
        cache = null
        super.onDestroy()
    }
}
