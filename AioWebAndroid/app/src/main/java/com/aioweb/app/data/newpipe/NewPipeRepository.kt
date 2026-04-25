package com.aioweb.app.data.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class YtTrack(
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val url: String,
    val thumbnail: String?,
)

object NewPipeRepository {

    /**
     * Search YouTube Music and return tracks. Uses the "music_songs" content filter so
     * results are MV-stripped songs (matches Metrolist's home/search behaviour).
     */
    suspend fun searchMusic(query: String): List<YtTrack> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = SearchInfo.getInfo(
            service,
            service.searchQHFactory.fromQuery(
                query,
                listOf("music_songs"),
                "",
            ),
        )
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            try {
                YtTrack(
                    title = item.name ?: "Untitled",
                    uploader = item.uploaderName ?: "",
                    durationSec = item.duration,
                    url = item.url ?: return@mapNotNull null,
                    thumbnail = item.thumbnails?.firstOrNull()?.url,
                )
            } catch (_: Exception) { null }
        }
    }

    /**
     * Pulls YouTube Music's "Trending" home feed. This is what Metrolist's home tab shows
     * before the user searches anything. NewPipe exposes it through the Trending kiosk.
     */
    suspend fun homeFeed(): List<YtTrack> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val kiosks = service.kioskList
        val kioskUrl = kiosks.getListLinkHandlerFactoryByType("Trending").fromId("Trending")
        val kiosk = kiosks.getExtractorByUrl(kioskUrl.url, null)
        kiosk.fetchPage()
        val items = KioskInfo.getInfo(service, kioskUrl.url).relatedItems
        items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            try {
                YtTrack(
                    title = item.name ?: "Untitled",
                    uploader = item.uploaderName ?: "",
                    durationSec = item.duration,
                    url = item.url ?: return@mapNotNull null,
                    thumbnail = item.thumbnails?.firstOrNull()?.url,
                )
            } catch (_: Exception) { null }
        }
    }

    /**
     * Resolves a playable audio stream URL for the given YouTube video page URL.
     * Throws on failure with a descriptive message — the ViewModel surfaces it to the user.
     *
     * Strategy mirrors Metrolist's: prefer M4A audio-only at the highest bitrate that
     * ExoPlayer's M4A muxer reliably handles (≤ 256kbps). Fall back to WEBM/OPUS if no
     * M4A is offered.
     */
    suspend fun resolveAudioStream(url: String): String = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(NewPipe.getService(0), url)
        if (info.audioStreams.isNullOrEmpty()) {
            error("YouTube returned no audio streams for this track. (PoToken/age-gate or region block)")
        }
        // Prefer M4A first (mostly works flawlessly in ExoPlayer), fall back to WEBM.
        val m4a = info.audioStreams.filter { it.format?.suffix?.equals("m4a", true) == true }
        val pool = if (m4a.isNotEmpty()) m4a else info.audioStreams
        val best = pool.maxByOrNull { it.averageBitrate }
            ?: error("Could not pick an audio stream from ${info.audioStreams.size} candidates.")
        best.content?.takeIf { it.isNotBlank() }
            ?: error("Selected audio stream had a blank URL. Try another track.")
    }
}
