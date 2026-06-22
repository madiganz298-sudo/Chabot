package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ServiceLocator
import com.example.data.api.OpenRouterMessage
import com.example.data.model.ChatMessage
import com.example.data.repository.ChatRepository
import com.example.data.repository.TelegramRepository
import com.example.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelegramPollingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var pollingJob: Job? = null

    private lateinit var telegramRepository: TelegramRepository
    private lateinit var chatRepository: ChatRepository

    companion object {
        private const val NOTIFICATION_ID = 4912
        private const val CHANNEL_ID = "telegram_polling_channel"
        private const val TAG = "TelegramPollingService"
        var isRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, TelegramPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelegramPollingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        ServiceLocator.init(applicationContext)
        telegramRepository = ServiceLocator.telegramRepository
        chatRepository = ServiceLocator.chatRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started")
        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isRunning = true
        startPolling()

        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob != null && pollingJob!!.isActive) return

        pollingJob = scope.launch {
            telegramRepository.logInfo("Bot Bridge Active: Mulai memantau pesan Telegram...")
            while (isActive) {
                try {
                    val activeBots = telegramRepository.getActiveBotsSync()
                    if (activeBots.isEmpty()) {
                        delay(5000)
                        continue
                    }

                    // Read current settings dynamically for Telegram replies
                    val assistantName = SettingsManager.getAssistantName(applicationContext).first()
                    val aiModel = SettingsManager.getAiModel(applicationContext).first()

                    for (bot in activeBots) {
                        val offset = if (bot.lastUpdateId > 0) bot.lastUpdateId + 1 else null
                        val updates = telegramRepository.getUpdates(bot.token, offset, timeout = 3)

                        if (!updates.isNullOrEmpty()) {
                            var maxUpdateId = bot.lastUpdateId
                            for (update in updates) {
                                if (update.updateId > maxUpdateId) {
                                    maxUpdateId = update.updateId
                                }

                                val message = update.message ?: continue
                                val fromUser = message.from
                                val chatIdString = message.chat.id.toString()
                                val messageText = message.text ?: continue

                                val senderName = fromUser?.firstName ?: "User Telegram"
                                telegramRepository.logIn("Bot @${bot.botUsername} - Masuk dari $senderName: $messageText")

                                // 1. Start processing prompt
                                val replyText = if (messageText == "/start") {
                                    "Halo $senderName! Saya adalah '$assistantName'. Jembatan AI M4DiChat tersambung aktif untuk melayani percakapan Telegram Anda."
                                } else {
                                    // Assemble simple context or query API
                                    val requestMessages = listOf(
                                        OpenRouterMessage("user", messageText)
                                    )
                                    chatRepository.getSingleCompletion(requestMessages, aiModel, assistantName)
                                }

                                // 2. Send Response back
                                val successResult = telegramRepository.sendMessage(bot.token, chatIdString, replyText)
                                if (successResult) {
                                    telegramRepository.logOut("Tergirim ke Telegram (ID: $chatIdString): $replyText")

                                    // Store conversation in local DB Room as a special thread
                                    val localThreadId = "telegram_${chatIdString}"
                                    chatRepository.insertMessage(
                                        ChatMessage(
                                            threadId = localThreadId,
                                            sender = "user",
                                            content = messageText,
                                            isTelegram = true,
                                            assistantName = assistantName
                                        )
                                    )
                                    chatRepository.insertMessage(
                                        ChatMessage(
                                            threadId = localThreadId,
                                            sender = "assistant",
                                            content = replyText,
                                            isTelegram = true,
                                            assistantName = assistantName
                                        )
                                    )
                                } else {
                                    telegramRepository.logError("Gagal membalas Telegram ke ChatID $chatIdString. Cek Token atau izin bot.")
                                }
                            }

                            if (maxUpdateId > bot.lastUpdateId) {
                                telegramRepository.updateBot(bot.copy(lastUpdateId = maxUpdateId))
                            }
                        }
                    }
                } catch (e: Exception) {
                    telegramRepository.logError("Kesalahan loop Polling: ${e.localizedMessage}")
                }
                delay(2000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "M4DiChat Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for background polling Telegram Bot Bridge"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TelegramPollingService::class.java).apply {
            action = "STOP"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("M4DiChat AI Bridge Berjalan")
            .setContentText("Menjembatani chat Telegram ke OpenRouter AI...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        isRunning = false
        pollingJob?.cancel()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
