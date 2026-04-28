package com.aioweb.app.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Centralized Retrofit client used by the app.
 * This matches the original setup your app used before the build broke.
 */

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val contentType = "application/json".toMediaType()

private val okHttp = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .build()

private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.aioweb.app/") // Replace with your actual backend URL
    .addConverterFactory(json.asConverterFactory(contentType))
    .client(okHttp)
    .build()

// Example API interface — adjust to your actual endpoints
interface ApiService {
    @GET("tmdb/{id}")
    suspend fun getTmdbInfo(@Path("id") id: String): TmdbResponse
}

// Expose the API instance
val api: ApiService = retrofit.create(ApiService::class.java)