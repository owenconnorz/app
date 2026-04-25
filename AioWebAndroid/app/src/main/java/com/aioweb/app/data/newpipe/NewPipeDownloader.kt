package com.aioweb.app.data.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/** Minimal OkHttp-based Downloader required by NewPipe Extractor. */
class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NPRequest): Response {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { v -> builder.addHeader(name, v) }
        }
        val body = request.dataToSend()?.toRequestBody()
        when (request.httpMethod().uppercase()) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody())
            "HEAD" -> builder.head()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            val responseHeaders = resp.headers.toMultimap()
            val bodyStr = resp.body?.string() ?: ""
            return Response(
                resp.code,
                resp.message,
                responseHeaders,
                bodyStr,
                resp.request.url.toString()
            )
        }
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }
    }
}
