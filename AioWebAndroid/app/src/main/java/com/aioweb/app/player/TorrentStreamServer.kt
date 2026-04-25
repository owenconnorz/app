package com.aioweb.app.player

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Streams a magnet/torrent through libtorrent4j and exposes the largest media
 * file via a small embedded HTTP server, so ExoPlayer can play it like any URL.
 *
 * One instance handles a single torrent at a time. Call [stop] when done.
 */
class TorrentStreamServer(context: Context) {

    private val downloadDir: File = File(context.cacheDir, "torrents").apply { mkdirs() }
    private val sessionManager = SessionManager()
    private var nano: HttpServer? = null
    private val handleRef = AtomicReference<TorrentHandle?>(null)
    private val targetFileRef = AtomicReference<File?>(null)
    @Volatile private var totalBytes: Long = 0L

    /**
     * Starts the torrent and the local HTTP server.
     * Returns a `http://127.0.0.1:<port>/stream` URL that can be fed to ExoPlayer,
     * or `null` if metadata couldn't be fetched within [metadataTimeoutMs].
     */
    suspend fun start(
        magnetOrUrl: String,
        metadataTimeoutMs: Long = 60_000L,
    ): String? {
        sessionManager.start()

        // 1. Resolve metadata (magnet → TorrentInfo bytes; .torrent URL → fetched bytes).
        val infoBytes: ByteArray = when {
            magnetOrUrl.startsWith("magnet:", ignoreCase = true) ->
                sessionManager.fetchMagnet(magnetOrUrl, (metadataTimeoutMs / 1000).toInt().coerceAtLeast(15))
                    ?: return null
            magnetOrUrl.startsWith("http", ignoreCase = true) ->
                runCatching { URL(magnetOrUrl).openStream().use { it.readBytes() } }.getOrNull()
                    ?: return null
            else -> File(magnetOrUrl).takeIf { it.exists() }?.readBytes() ?: return null
        }

        val torrentInfo = runCatching { TorrentInfo.bdecode(infoBytes) }.getOrNull() ?: return null

        // 2. Wait for TorrentHandle to be ready and largest file index identified.
        val handle = withTimeoutOrNull(metadataTimeoutMs) {
            startTorrentAndAwaitHandle(torrentInfo)
        } ?: return null

        // 3. Pick the largest file as the playback target.
        val files = torrentInfo.files()
        var largestIdx = 0
        var largestSize = 0L
        for (i in 0 until files.numFiles()) {
            val size = files.fileSize(i)
            if (size > largestSize) {
                largestSize = size
                largestIdx = i
            }
        }
        totalBytes = largestSize
        val relPath = files.filePath(largestIdx)
        val absFile = File(downloadDir, relPath)
        targetFileRef.set(absFile)

        // 4. Sequentially prioritise that file; deprioritise others to save bandwidth.
        val prios = Array(files.numFiles()) { Priority.IGNORE }
        prios[largestIdx] = Priority.TOP_PRIORITY
        handle.prioritizeFiles(prios)
        // libtorrent4j flag for sequential download:
        runCatching { handle.setFlags(handle.flags().or_(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD)) }

        // 5. Start NanoHTTPD on a free port.
        val server = HttpServer(absFile) { totalBytes }
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, /* daemon */ true)
        nano = server
        val port = server.listeningPort
        val name = URLEncoder.encode(absFile.name, "UTF-8")
        return "http://127.0.0.1:$port/stream/$name"
    }

    private suspend fun startTorrentAndAwaitHandle(torrentInfo: TorrentInfo): TorrentHandle? =
        suspendCancellableCoroutine { cont ->
            val listener = object : AlertListener {
                override fun types(): IntArray = intArrayOf(
                    AlertType.ADD_TORRENT.swig(),
                    AlertType.METADATA_RECEIVED.swig(),
                )
                override fun alert(alert: Alert<*>) {
                    val h = sessionManager.find(torrentInfo.infoHash()) ?: return
                    if (h.isValid && handleRef.compareAndSet(null, h)) {
                        if (cont.isActive) cont.resume(h)
                    }
                }
            }
            sessionManager.addListener(listener)
            sessionManager.download(torrentInfo, downloadDir)
            // Fast-path: handle may already exist
            sessionManager.find(torrentInfo.infoHash())?.let {
                if (it.isValid && handleRef.compareAndSet(null, it) && cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { runCatching { sessionManager.removeListener(listener) } }
        }

    fun stop() {
        runCatching { nano?.stop() }
        runCatching { handleRef.get()?.let { sessionManager.remove(it) } }
        runCatching { sessionManager.stop() }
    }

    /**
     * Tiny HTTP server: serves [target] with HTTP Range support. If a requested
     * byte isn't yet downloaded by libtorrent, blocks (with timeout) until it is.
     */
    private class HttpServer(
        private val target: File,
        private val totalSizeProvider: () -> Long,
    ) : NanoHTTPD(0) {

        override fun serve(session: IHTTPSession): Response {
            val total = totalSizeProvider().takeIf { it > 0 } ?: target.length().coerceAtLeast(1L)
            val rangeHeader = session.headers["range"]

            val (start, end) = parseRange(rangeHeader, total)
            val length = end - start + 1
            waitForBytes(start + length, timeoutMs = 30_000L)

            val raf = RandomAccessFile(target, "r")
            raf.seek(start)
            val stream = object : java.io.InputStream() {
                private var remaining = length
                override fun read(): Int {
                    if (remaining <= 0) return -1
                    val v = raf.read(); if (v >= 0) remaining--
                    return v
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0) return -1
                    val toRead = minOf(len.toLong(), remaining).toInt()
                    val n = raf.read(b, off, toRead)
                    if (n > 0) remaining -= n
                    return n
                }
                override fun close() { runCatching { raf.close() } }
            }

            val status = if (rangeHeader != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val resp = newFixedLengthResponse(status, "video/*", stream, length)
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Content-Range", "bytes $start-$end/$total")
            resp.addHeader("Content-Length", length.toString())
            return resp
        }

        private fun parseRange(header: String?, total: Long): Pair<Long, Long> {
            if (header == null || !header.startsWith("bytes=")) return 0L to (total - 1)
            val raw = header.removePrefix("bytes=").substringBefore(",")
            val parts = raw.split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull() ?: (total - 1)
            return start.coerceIn(0, total - 1) to end.coerceIn(start, total - 1)
        }

        private fun waitForBytes(byteCount: Long, timeoutMs: Long) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (target.length() < byteCount) {
                if (System.currentTimeMillis() > deadline) return
                try { Thread.sleep(250) } catch (_: InterruptedException) { return }
            }
        }
    }

    companion object { private const val TAG = "TorrentStream" }
}
