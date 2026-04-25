package com.aioweb.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class EpornerThumb(val src: String? = null, val size: String? = null)

@Serializable
data class EpornerVideo(
    val id: String,
    val title: String,
    val keywords: String? = null,
    val views: Long = 0,
    val rate: String? = null,
    val url: String,                 // page URL
    val embed: String,               // embed URL — used for in-app WebView playback
    @SerialName("default_thumb") val defaultThumb: EpornerThumb? = null,
    @SerialName("length_sec") val lengthSec: Long = 0,
    @SerialName("length_min") val lengthMin: String? = null,
)

@Serializable
data class EpornerSearchResponse(
    val count: Int = 0,
    val total_count: Int = 0,
    val per_page: Int = 30,
    val videos: List<EpornerVideo> = emptyList(),
)

interface EpornerApi {
    @GET("api/v2/video/search/")
    suspend fun search(
        @Query("query") query: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("thumbsize") thumbsize: String = "medium",
        @Query("order") order: String = "most-popular",
        @Query("format") format: String = "json",
    ): EpornerSearchResponse
}
