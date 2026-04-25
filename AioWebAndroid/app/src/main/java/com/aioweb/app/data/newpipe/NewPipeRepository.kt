package com.aioweb.app.data.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
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

    suspend fun searchMusic(query: String): List<YtTrack> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = SearchInfo.getInfo(
            service,
            service.searchQHFactory.fromQuery(
                query,
                listOf("music_songs"),
                ""
            )
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

    suspend fun resolveAudioStream(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(NewPipe.getService(0), url)
            // Pick the highest-bitrate audio-only stream
            info.audioStreams.maxByOrNull { it.averageBitrate }?.content
        } catch (e: Exception) {
            null
        }
    }
}
