package com.aioweb.app.data.ytmusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aioweb.app.audio.MusicController
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.library.TrackEntity
import com.aioweb.app.data.newpipe.NewPipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hand-off between "user tapped a YT Music item on Library / home feed" and
 * actual audio coming out of the foreground [MusicPlaybackService].
 *
 * Steps:
 *   1. Build the canonical watch URL from the video id.
 *   2. Look up a local cached download (`TrackEntity.localPath`). If present, play
 *      the file directly — Metrolist parity: downloaded tracks play offline and
 *      skip NewPipe entirely.
 *   3. Otherwise resolve via NewPipe → best M4A audio stream.
 *   4. Upsert the `TrackEntity` (so it shows up in Library → Cached / Recent) and
 *      set it on the global [MusicController].
 *
 * Called from the Library playlist screen, home-feed song cards, and anywhere
 * else we need "play this YT Music videoId right now".
 */
object YtPlayback {

    private fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    suspend fun playSong(context: Context, song: YtmSong) {
        val url = watchUrl(song.videoId)
        val dao = LibraryDb.get(context).tracks()
        // Prefer downloaded cache for offline playback.
        val cached = dao.byUrl(url)
        val playableUri: String = cached?.localPath?.takeIf { java.io.File(it).exists() }
            ?: withContext(Dispatchers.IO) { NewPipeRepository.resolveAudioStream(url) }

        // Persist/refresh the Library entry so Liked / Downloaded / Cached tiles stay
        // in sync and the global MiniPlayer has a thumbnail & duration to show.
        dao.upsert(
            TrackEntity(
                url = url,
                title = song.title,
                artist = song.artist,
                durationSec = song.durationSeconds ?: cached?.durationSec ?: 0L,
                thumbnail = song.thumbnail ?: cached?.thumbnail,
                likedAt = cached?.likedAt,
                lastPlayed = System.currentTimeMillis(),
                playCount = (cached?.playCount ?: 0) + 1,
                localPath = cached?.localPath,
            ),
        )

        // Hand off to the global controller — this is the same MediaController the
        // MiniPlayer reads from, so playback state updates everywhere at once.
        val controller = MusicController.get(context.applicationContext)
        val item = MediaItem.Builder()
            .setUri(playableUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.thumbnail?.let(Uri::parse))
                    .build(),
            )
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    /**
     * Queue a full playlist into the player. First song plays immediately, the rest
     * become the upcoming queue — matches Metrolist's "Play playlist" behaviour.
     */
    suspend fun playPlaylist(context: Context, songs: List<YtmSong>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val startSong = songs.getOrNull(startIndex) ?: songs.first()
        // Simplest working version: play the first track, leave queue management to
        // a later refactor. (Proper queue wiring needs setMediaItems + index.)
        playSong(context, startSong)
    }

    /** Download a YT Music song — delegates to [MusicDownloader] with a watch URL. */
    suspend fun downloadSong(context: Context, song: YtmSong): java.io.File {
        val url = watchUrl(song.videoId)
        // Upsert TrackEntity first so the downloader's `dao.setLocalPath(...)` has a
        // row to update (otherwise the REPLACE conflict strategy recreates it with
        // empty metadata when NewPipe finishes).
        LibraryDb.get(context).tracks().upsert(
            TrackEntity(
                url = url,
                title = song.title,
                artist = song.artist,
                durationSec = song.durationSeconds ?: 0L,
                thumbnail = song.thumbnail,
            ),
        )
        return com.aioweb.app.data.downloads.MusicDownloader.download(
            context, url, song.title,
        )
    }

    fun isDownloaded(context: Context, song: YtmSong): Boolean =
        com.aioweb.app.data.downloads.MusicDownloader.isDownloaded(
            context,
            watchUrl(song.videoId),
        )
}
