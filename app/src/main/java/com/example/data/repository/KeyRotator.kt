package com.example.data.repository

import android.util.Log
import com.example.data.local.ApiKeyDao
import com.example.data.local.LogDao
import com.example.data.model.TelegramLogEntry
import com.example.util.EncryptionUtils
import retrofit2.HttpException

class KeyRotator(
    private val apiKeyDao: ApiKeyDao,
    private val logDao: LogDao
) {
    private var cachedKeys = listOf<String>()
    private var currentIndex = 0

    suspend fun reloadKeys() {
        val dbKeys = apiKeyDao.getApiKeysSync()
        cachedKeys = dbKeys
            .map { EncryptionUtils.decrypt(it.key) }
            .filter { it.isNotBlank() }
        currentIndex = 0
        Log.d("KeyRotator", "Loaded ${cachedKeys.size} keys from database.")
    }

    fun getCurrentKey(): String? {
        if (cachedKeys.isEmpty()) return null
        val safeIndex = currentIndex % cachedKeys.size
        return cachedKeys[safeIndex]
    }

    suspend fun rotateKey() {
        if (cachedKeys.isEmpty()) return
        val previousIndex = currentIndex % cachedKeys.size
        currentIndex++
        val newIndex = currentIndex % cachedKeys.size
        
        val logMsg = "API Key limit or error. Rotating key from slot ${previousIndex + 1} to slot ${newIndex + 1}"
        Log.w("KeyRotator", logMsg)
        
        logDao.insertLog(
            TelegramLogEntry(
                direction = "INFO",
                message = logMsg
            )
        )
    }

    fun hasKeys(): Boolean {
        return cachedKeys.isNotEmpty()
    }

    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        block: suspend (String) -> T
    ): T {
        // Ensure keys are loaded
        if (cachedKeys.isEmpty()) {
            reloadKeys()
        }

        if (cachedKeys.isEmpty()) {
            throw IllegalStateException("No OpenRouter API keys configured. Please add keys in Settings.")
        }

        var attempts = 0
        while (attempts < maxRetries) {
            val currentKey = getCurrentKey() ?: throw IllegalStateException("No keys available")
            try {
                return block(currentKey)
            } catch (e: HttpException) {
                val code = e.code()
                if (code == 429 || code == 401) {
                    // Rotate and retry
                    rotateKey()
                    attempts++
                } else {
                    // Other HTTP error, propagate
                    throw e
                }
            } catch (e: Exception) {
                // If it's a network exception or block-specific failure, let's propagate
                throw e
            }
        }
        throw IllegalStateException("All configured API keys failed due to rate limits or invalid status.")
    }
}
