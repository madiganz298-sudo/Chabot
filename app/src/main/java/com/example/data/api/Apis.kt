package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// --- OpenRouter Network Models ---

@JsonClass(generateAdapter = true)
data class OpenRouterMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OpenRouterDelta(
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterChoiceDelta(
    val delta: OpenRouterDelta? = null,
    val finish_reason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterStreamResponse(
    val choices: List<OpenRouterChoiceDelta>? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterChoice(
    val message: OpenRouterMessage? = null,
    val finish_reason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>? = null,
    val error: OpenRouterError? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterError(
    val message: String? = null,
    val code: Int? = null
)

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String, // "Bearer <KEY>"
        @Header("HTTP-Referer") referer: String = "https://github.com/m4diuciha/chatai",
        @Header("X-Title") title: String = "M4DiChat AI",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse

    @Streaming
    @POST("chat/completions")
    suspend fun getChatCompletionStream(
        @Header("Authorization") authorization: String, // "Bearer <KEY>"
        @Header("HTTP-Referer") referer: String = "https://github.com/m4diuciha/chatai",
        @Header("X-Title") title: String = "M4DiChat AI",
        @Body request: OpenRouterRequest
    ): Response<ResponseBody>
}

// --- Telegram Network Models ---

@JsonClass(generateAdapter = true)
data class TelegramUser(
    val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUserResponse(
    val ok: Boolean,
    val result: TelegramUser? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    val id: Long,
    val type: String
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TelegramMessage? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdateResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate>? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramSendMessageRequest(
    @Json(name = "chat_id") val chatId: String,
    val text: String,
    @Json(name = "parse_mode") val parseMode: String? = "Markdown"
)

@JsonClass(generateAdapter = true)
data class TelegramSendMessageResponse(
    val ok: Boolean,
    val description: String? = null
)

interface TelegramApi {
    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path("token") token: String
    ): TelegramUserResponse

    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("timeout") timeout: Int? = null
    ): TelegramUpdateResponse

    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body request: TelegramSendMessageRequest
    ): TelegramSendMessageResponse
}
