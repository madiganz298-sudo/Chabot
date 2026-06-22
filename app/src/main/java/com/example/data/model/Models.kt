package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val sender: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isTelegram: Boolean = false,
    val assistantName: String = "AI Assistant"
)

@Entity(tableName = "telegram_bots")
data class TelegramBotConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String,
    val chatId: String,
    val botUsername: String = "",
    val isActive: Boolean = false,
    val lastUpdateId: Long = 0
)

@Entity(tableName = "api_keys")
data class ApiKeyConfig(
    @PrimaryKey val id: Int, // 1, 2, 3, or 4
    val key: String,
    val isActive: Boolean = true,
    val label: String = "API Key #$id"
)

@Entity(tableName = "telegram_logs")
data class TelegramLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "IN", "OUT", "INFO", "ERROR"
    val message: String
)
