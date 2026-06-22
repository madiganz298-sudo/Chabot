package com.example.data.repository

import com.example.data.api.TelegramApi
import com.example.data.api.TelegramSendMessageRequest
import com.example.data.api.TelegramUpdate
import com.example.data.api.TelegramUser
import com.example.data.local.BotDao
import com.example.data.local.LogDao
import com.example.data.model.TelegramBotConfig
import com.example.data.model.TelegramLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TelegramRepository(
    private val botDao: BotDao,
    private val logDao: LogDao,
    private val telegramApi: TelegramApi
) {
    val allBots: Flow<List<TelegramBotConfig>> = botDao.getAllBots()
    val logs: Flow<List<TelegramLogEntry>> = logDao.getLogsFlow()

    suspend fun saveBot(bot: TelegramBotConfig) = withContext(Dispatchers.IO) {
        botDao.insertBot(bot)
    }

    suspend fun updateBot(bot: TelegramBotConfig) = withContext(Dispatchers.IO) {
        botDao.updateBot(bot)
    }

    suspend fun deleteBot(id: Long) = withContext(Dispatchers.IO) {
        botDao.deleteBot(id)
    }

    suspend fun getActiveBotsSync(): List<TelegramBotConfig> = withContext(Dispatchers.IO) {
        botDao.getActiveBotsSync()
    }

    suspend fun getBotSync(id: Long): TelegramBotConfig? = withContext(Dispatchers.IO) {
        botDao.getBotSync(id)
    }

    // --- Logger Helpers ---

    suspend fun logInfo(message: String) = withContext(Dispatchers.IO) {
        logDao.insertLog(TelegramLogEntry(direction = "INFO", message = message))
    }

    suspend fun logIn(message: String) = withContext(Dispatchers.IO) {
        logDao.insertLog(TelegramLogEntry(direction = "IN", message = message))
    }

    suspend fun logOut(message: String) = withContext(Dispatchers.IO) {
        logDao.insertLog(TelegramLogEntry(direction = "OUT", message = message))
    }

    suspend fun logError(message: String) = withContext(Dispatchers.IO) {
        logDao.insertLog(TelegramLogEntry(direction = "ERROR", message = message))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearLogs()
    }

    // --- Bot Network Actions ---

    suspend fun testBotConnection(token: String): TelegramUser? = withContext(Dispatchers.IO) {
        try {
            val response = telegramApi.getMe(token)
            if (response.ok) {
                return@withContext response.result
            } else {
                return@withContext null
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun getUpdates(token: String, offset: Long?, timeout: Int = 5): List<TelegramUpdate>? = withContext(Dispatchers.IO) {
        try {
            val response = telegramApi.getUpdates(token = token, offset = offset, limit = 10, timeout = timeout)
            if (response.ok) {
                return@withContext response.result
            }
        } catch (e: Exception) {
            // Log networking exception or suppression
        }
        return@withContext null
    }

    suspend fun sendMessage(token: String, chatId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = telegramApi.sendMessage(
                token = token,
                request = TelegramSendMessageRequest(chatId = chatId, text = text)
            )
            return@withContext response.ok
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
