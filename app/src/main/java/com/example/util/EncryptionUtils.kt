package com.example.util

import android.util.Base64

object EncryptionUtils {
    private const val KEY = "M4DiChatAIEncryptSecretKey"

    fun encrypt(plainText: String): String {
        try {
            val bytes = plainText.toByteArray(Charsets.UTF_8)
            val encryptedBytes = ByteArray(bytes.size)
            for (i in bytes.indices) {
                encryptedBytes[i] = (bytes[i].toInt() xor KEY[i % KEY.length].toInt()).toByte()
            }
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            return plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        try {
            val bytes = Base64.decode(encryptedText, Base64.NO_WRAP)
            val decryptedBytes = ByteArray(bytes.size)
            for (i in bytes.indices) {
                decryptedBytes[i] = (bytes[i].toInt() xor KEY[i % KEY.length].toInt()).toByte()
            }
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return encryptedText
        }
    }
}
