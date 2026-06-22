package com.example.data.repository

import com.example.data.api.OpenRouterApi
import com.example.data.api.OpenRouterMessage
import com.example.data.api.OpenRouterRequest
import com.example.data.api.OpenRouterStreamResponse
import com.example.data.local.ChatDao
import com.example.data.model.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.BufferedReader

class ChatRepository(
    private val chatDao: ChatDao,
    private val openRouterApi: OpenRouterApi,
    private val keyRotator: KeyRotator
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val streamAdapter = moshi.adapter(OpenRouterStreamResponse::class.java)

    val allThreads: Flow<List<String>> = chatDao.getAllThreads()

    fun getMessagesForThread(threadId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForThread(threadId)

    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun deleteThread(threadId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteThread(threadId)
    }

    suspend fun clearAllMessages() = withContext(Dispatchers.IO) {
        chatDao.clearAllMessages()
    }

    /**
     * Sends user message, saves it, and yields real-time streaming tokens of AI response.
     */
    fun sendChatMessageStream(
        threadId: String,
        userContent: String,
        model: String,
        assistantName: String,
        history: List<ChatMessage>
    ): Flow<String> = flow {
        // 1. Insert User Message
        val userMsg = ChatMessage(
            threadId = threadId,
            sender = "user",
            content = userContent,
            assistantName = assistantName
        )
        chatDao.insertMessage(userMsg)

        // 2. Build OpenRouter Request
        val requestMessages = mutableListOf<OpenRouterMessage>()
        // Let's add system prompt personalization
        requestMessages.add(OpenRouterMessage("system", "You are an AI Assistant designated as '$assistantName'. Always help the user in Indonesian. Keep replies structured and premium."))
        
        // Add existing thread history (only keep latest 20 to save tokens and prevent huge loads)
        val historyLimit = history.takeLast(20)
        historyLimit.forEach { msg ->
            requestMessages.add(OpenRouterMessage(msg.sender, msg.content))
        }
        // Include the newly added user message
        requestMessages.add(OpenRouterMessage("user", userContent))

        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = true
        )

        var accumulatedResponse = ""

        try {
            // 3. Execute with Automatic Key Rotation
            keyRotator.executeWithRetry { apiKey ->
                val response = openRouterApi.getChatCompletionStream(
                    authorization = "Bearer $apiKey",
                    request = request
                )

                if (!response.isSuccessful) {
                    throw HttpException(response)
                }

                val responseBody = response.body() ?: throw IllegalStateException("Empty response body from AI server")
                val reader = BufferedReader(responseBody.charStream())
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val json = currentLine.substring(6).trim()
                        if (json == "[DONE]" || json.isBlank()) continue
                        try {
                            val chunk = streamAdapter.fromJson(json)
                            val deltaText = chunk?.choices?.firstOrNull()?.delta?.content
                            if (!deltaText.isNullOrEmpty()) {
                                accumulatedResponse += deltaText
                                emit(accumulatedResponse)
                            }
                        } catch (e: Exception) {
                            // Suppress parse failures of keep-alive or malformed streaming frames
                        }
                    }
                }
            }

            // 4. Save entire accumulated AI response to Room
            if (accumulatedResponse.isNotBlank()) {
                val aiMsg = ChatMessage(
                    threadId = threadId,
                    sender = "assistant",
                    content = accumulatedResponse,
                    assistantName = assistantName
                )
                chatDao.insertMessage(aiMsg)
            } else {
                emit("Error: Asisten tidak memberikan balasan.")
            }

        } catch (e: Exception) {
            val errMsg = "Error occurred: ${e.localizedMessage ?: "Unknown network failure."}"
            emit(errMsg)
            // Save the error string so history shows it or doesn't lock
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Standard Non-streaming fallback (used for background operations like Telegram bot answers).
     */
    suspend fun getSingleCompletion(
        messages: List<OpenRouterMessage>,
        model: String,
        assistantName: String
    ): String = withContext(Dispatchers.IO) {
        val requestMessages = mutableListOf<OpenRouterMessage>()
        requestMessages.add(OpenRouterMessage("system", "Kamu adalah asisten AI bernama '$assistantName'. Tolong bantu pengguna sebaik mungkin dalah bahasa Indonesia."))
        requestMessages.addAll(messages)

        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = false
        )

        try {
            keyRotator.executeWithRetry { apiKey ->
                val response = openRouterApi.getChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                val text = response.choices?.firstOrNull()?.message?.content
                if (!text.isNullOrBlank()) {
                    return@executeWithRetry text
                } else if (response.error != null) {
                    throw IllegalStateException("API Error: ${response.error.message}")
                } else {
                    throw IllegalStateException("No content returned.")
                }
            }
        } catch (e: Exception) {
            "Gagal memproses AI: ${e.localizedMessage ?: "Mohon periksa konfigurasi API Key."}"
        }
    }
}
