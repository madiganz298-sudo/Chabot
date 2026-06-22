package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessage
import com.example.data.model.TelegramBotConfig
import com.example.data.model.ApiKeyConfig
import com.example.data.model.TelegramLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: String): Flow<List<ChatMessage>>

    @Query("SELECT DISTINCT threadId FROM chat_messages ORDER BY timestamp DESC")
    fun getAllThreads(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface BotDao {
    @Query("SELECT * FROM telegram_bots")
    fun getAllBots(): Flow<List<TelegramBotConfig>>

    @Query("SELECT * FROM telegram_bots WHERE isActive = 1")
    fun getActiveBotsFlow(): Flow<List<TelegramBotConfig>>

    @Query("SELECT * FROM telegram_bots WHERE isActive = 1")
    suspend fun getActiveBotsSync(): List<TelegramBotConfig>

    @Query("SELECT * FROM telegram_bots WHERE id = :id LIMIT 1")
    suspend fun getBotSync(id: Long): TelegramBotConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBot(bot: TelegramBotConfig)

    @Query("DELETE FROM telegram_bots WHERE id = :id")
    suspend fun deleteBot(id: Long)

    @Update
    suspend fun updateBot(bot: TelegramBotConfig)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY id ASC")
    fun getApiKeysFlow(): Flow<List<ApiKeyConfig>>

    @Query("SELECT * FROM api_keys ORDER BY id ASC")
    suspend fun getApiKeysSync(): List<ApiKeyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(key: ApiKeyConfig)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKey(id: Int)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM telegram_logs ORDER BY timestamp DESC LIMIT 300")
    fun getLogsFlow(): Flow<List<TelegramLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TelegramLogEntry)

    @Query("DELETE FROM telegram_logs")
    suspend fun clearLogs()
}
