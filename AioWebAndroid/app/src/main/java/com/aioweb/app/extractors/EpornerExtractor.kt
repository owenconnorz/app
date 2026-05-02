package com.aioweb.app.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object EpornerExtractor {

    suspend fun extractVideoUrl(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(embedUrl)
                .userAgent("Mozilla/5.0")
                .get()

            val script = doc.select("script")
                .firstOrNull { it.data().contains("videoSources") }
                ?.data()
                ?: return@withContext null

            val regex = Regex("\"src\":\"(.*?)\"")
            val match = regex.find(script) ?: return@withContext null

            return@withContext match.groupValues[1]
                .replace("\\/", "/") // unescape
        } catch (e: Exception) {
            null
        }
    }
}