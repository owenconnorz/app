package com.aioweb.app.data.downloads

import android.content.Context
import com.aioweb.app.data.library.LibraryDb
import com.aioweb.app.data.newpipe.NewPipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Single-shot music downloader. Resolves the audio stream via NewPipe, then writes it
 * to /Android/data/.../files/music/<sanitised>.m4a and stores the path in Room
 * (TrackEntity.localPath) so it can be replayed without internet.
 *
 * Per-URL progress is exposed via [progressFlow] for the Library tab UI.
 */
object MusicDownloader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progressFlow: Flow<Map<String, Float>> = _progress.asStateFlow()

    private fun setProgress(url: String, fraction: Float?) {
        _progress.value = _progress.value.toMutableMap().also { m ->
            if (fraction == null) m.remove(url) else m[url] = fraction
        }
    }

    private fun musicDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "music").apply { mkdirs() }

    fun isDownloaded(context: Context, url: String): Boolean {
        val name = url.hashCode().toString().replace("-", "n")
        return File(musicDir(context), "$name.m4a").exists()
    }

    suspend fun download(context: Context, url: String, title: String): File =
        withContext(Dispatchers.IO) {
            val dao = LibraryDb.get(context).tracks()
            val name = url.hashCode().toString().replace("-", "n")
            val outFile = File(musicDir(context), "$name.m4a")
            if (outFile.exists() && outFile.length() > 0) {
                dao.setLocalPath(url, outFile.absolutePath)
                return@withContext outFile
            }

            setProgress(url, 0f)
            try {
                val audio = NewPipeRepository.resolveAudioStream(url)
                val req = Request.Builder().url(audio)
                    .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val total = resp.body?.contentLength() ?: -1L
                    val tmp = File(outFile.absolutePath + ".part")
                    resp.body!!.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var written = 0L
                            while (true) {
                                read = input.read(buf)
                                if (read < 0) break
                                out.write(buf, 0, read)
                                written += read
                                if (total > 0) setProgress(url, written.toFloat() / total)
                            }
                        }
                    }
                    tmp.renameTo(outFile)
                }
                dao.setLocalPath(url, outFile.absolutePath)
                outFile
            } finally {
                setProgress(url, null)
            }
        }

    suspend fun delete(context: Context, url: String) = withContext(Dispatchers.IO) {
        val name = url.hashCode().toString().replace("-", "n")
        File(musicDir(context), "$name.m4a").delete()
        LibraryDb.get(context).tracks().setLocalPath(url, null)
    }
}
