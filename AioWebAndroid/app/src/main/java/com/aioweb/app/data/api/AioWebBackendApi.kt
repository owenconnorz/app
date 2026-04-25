package com.aioweb.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class ChatRequest(
    val message: String,
    @SerialName("session_id") val sessionId: String? = null,
    val provider: String = "openai",
    val model: String = "gpt-5.1",
    @SerialName("system_message")
    val systemMessage: String = "You are AioWeb's helpful AI assistant. Be concise and friendly.",
)

@Serializable
data class ChatResponse(
    @SerialName("session_id") val sessionId: String,
    val response: String,
)

@Serializable
data class ImageRequest(
    val prompt: String,
    val model: String = "gemini-3.1-flash-image-preview",
)

@Serializable
data class NsfwImageRequest(
    val prompt: String,
    @SerialName("fal_key") val falKey: String,
    val model: String = "fal-ai/fast-sdxl",
    @SerialName("image_size") val imageSize: String = "square_hd",
    @SerialName("num_inference_steps") val numInferenceSteps: Int = 28,
    @SerialName("negative_prompt") val negativePrompt: String? =
        "blurry, low quality, distorted, watermark",
)

@Serializable
data class ImageResponse(
    val text: String,
    val images: List<String>,
    @SerialName("mime_type") val mimeType: String = "image/png",
)

interface AioWebBackendApi {
    @POST("api/ai/chat")
    suspend fun chat(@Body req: ChatRequest): ChatResponse

    @POST("api/ai/image")
    suspend fun image(@Body req: ImageRequest): ImageResponse

    @POST("api/ai/image_nsfw")
    suspend fun imageNsfw(@Body req: NsfwImageRequest): ImageResponse
}
