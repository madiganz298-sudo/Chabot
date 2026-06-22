package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ServiceLocator
import com.example.data.model.ApiKeyConfig
import com.example.data.model.ChatMessage
import com.example.data.model.TelegramBotConfig
import com.example.data.model.TelegramLogEntry
import com.example.data.repository.ChatRepository
import com.example.data.repository.KeyRotator
import com.example.data.repository.TelegramRepository
import com.example.service.TelegramPollingService
import com.example.util.EncryptionUtils
import com.example.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    
    // Initializing dependencies
    init {
        ServiceLocator.init(context)
    }
    
    private val chatRepository: ChatRepository = ServiceLocator.chatRepository
    private val telegramRepository: TelegramRepository = ServiceLocator.telegramRepository
    private val keyRotator: KeyRotator = ServiceLocator.keyRotator

    // --- Active UI Threads & State ---
    private val _activeThreadId = MutableStateFlow("")
    val activeThreadId: StateFlow<String> = _activeThreadId.asStateFlow()

    val threads: StateFlow<List<String>> = chatRepository.allThreads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ChatMessage>> = _activeThreadId
        .flatMapLatest { threadId ->
            if (threadId.isNotBlank()) {
                chatRepository.getMessagesForThread(threadId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Loading & Sync Status ---
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isBridgeActive = MutableStateFlow(false)
    val isBridgeActive: StateFlow<Boolean> = _isBridgeActive.asStateFlow()

    // --- Notification & Toast Events ---
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // --- Settings Preferences ---
    private val _assistantName = MutableStateFlow("M4Di AI")
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()

    private val _aiModel = MutableStateFlow("openrouter/free")
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _avatarIndex = MutableStateFlow(0)
    val avatarIndex: StateFlow<Int> = _avatarIndex.asStateFlow()

    // --- Database Entity Streams (Settings Page) ---
    val apiKeys: StateFlow<List<ApiKeyConfig>> = ServiceLocator.database.apiKeyDao().getApiKeysFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telegramBots: StateFlow<List<TelegramBotConfig>> = telegramRepository.allBots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telegramLogs: StateFlow<List<TelegramLogEntry>> = telegramRepository.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load Settings Preferences
        viewModelScope.launch {
            SettingsManager.getAssistantName(context).collect { _assistantName.value = it }
        }
        viewModelScope.launch {
            SettingsManager.getAiModel(context).collect { _aiModel.value = it }
        }
        viewModelScope.launch {
            SettingsManager.getAvatarIndex(context).collect { _avatarIndex.value = it }
        }
        
        // Setup initial Thread ID if empty
        viewModelScope.launch {
            threads.collect { list ->
                if (_activeThreadId.value.isBlank()) {
                    if (list.isNotEmpty()) {
                        _activeThreadId.value = list.first()
                    } else {
                        startNewThread()
                    }
                }
            }
        }

        // Periodically refresh bridge service status
        viewModelScope.launch {
            while (true) {
                _isBridgeActive.value = TelegramPollingService.isRunning
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    // --- Thread Actions ---

    fun selectThread(threadId: String) {
        _activeThreadId.value = threadId
    }

    fun startNewThread() {
        _activeThreadId.value = "Thread-${UUID.randomUUID().toString().take(6)}"
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            chatRepository.deleteThread(threadId)
            val updatedList = threads.value.filter { it != threadId }
            if (updatedList.isNotEmpty()) {
                _activeThreadId.value = updatedList.first()
            } else {
                startNewThread()
            }
            _toastEvent.emit("Sesi obrolan berhasil dihapus")
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.clearAllMessages()
            startNewThread()
            _toastEvent.emit("Semua riwayat obrolan telah dibersihkan")
        }
    }

    // --- Local Chat Action ---

    fun sendMessage(content: String) {
        if (content.isBlank() || _isGenerating.value) return
        val currentThread = _activeThreadId.value
        val model = _aiModel.value
        val asName = _assistantName.value
        val messageHistory = messages.value

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // Ensure keyRotator has fresh DB api keys
                keyRotator.reloadKeys()
                chatRepository.sendChatMessageStream(
                    threadId = currentThread,
                    userContent = content,
                    model = model,
                    assistantName = asName,
                    history = messageHistory
                ).collect { progress ->
                    // UI can react immediately to messages update flow on db insertions
                }
            } catch (e: Exception) {
                _toastEvent.emit("Pengiriman gagal: ${e.localizedMessage}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // --- Settings Preferences Saving ---

    fun savePersonalization(name: String, model: String, avatarIndex: Int) {
        viewModelScope.launch {
            SettingsManager.saveSettings(context, name, model, avatarIndex)
            _assistantName.value = name
            _aiModel.value = model
            _avatarIndex.value = avatarIndex
            _toastEvent.emit("Personalisasi AI disimpan!")
        }
    }

    // --- OpenRouter API Key Actions ---

    fun saveApiKey(slotId: Int, rawKey: String) {
        viewModelScope.launch {
            val encrypted = EncryptionUtils.encrypt(rawKey.trim())
            val config = ApiKeyConfig(id = slotId, key = encrypted, label = "Key Slot #$slotId")
            ServiceLocator.database.apiKeyDao().insertApiKey(config)
            keyRotator.reloadKeys()
            _toastEvent.emit("API Key pada Slot #$slotId tersimpan.")
        }
    }

    fun deleteApiKey(slotId: Int) {
        viewModelScope.launch {
            ServiceLocator.database.apiKeyDao().deleteApiKey(slotId)
            keyRotator.reloadKeys()
            _toastEvent.emit("API Key pada Slot #$slotId berhasil dihapus.")
        }
    }

    // --- Telegram Bot Actions ---

    fun addTelegramBot(token: String, chatId: String) {
        viewModelScope.launch {
            if (token.isBlank() || chatId.isBlank()) {
                _toastEvent.emit("Token dan Chat ID Telegram tidak boleh kosong")
                return@launch
            }
            
            _toastEvent.emit("Menghubungkan & menguji bot...")
            val botUser = telegramRepository.testBotConnection(token.trim())
            if (botUser != null) {
                val newBot = TelegramBotConfig(
                    token = token.trim(),
                    chatId = chatId.trim(),
                    botUsername = botUser.username ?: "bot_pribadi",
                    isActive = false
                )
                telegramRepository.saveBot(newBot)
                telegramRepository.logInfo("Bot Telegram tersambung: @${newBot.botUsername}")
                _toastEvent.emit("Bot @${newBot.botUsername} berhasil ditambahkan!")
            } else {
                _toastEvent.emit("Gagal menyambung ke Bot Telegram. Periksa kembali token Anda.")
            }
        }
    }

    fun toggleBotActive(bot: TelegramBotConfig, active: Boolean) {
        viewModelScope.launch {
            telegramRepository.updateBot(bot.copy(isActive = active))
            if (active) {
                telegramRepository.logInfo("Mengaktifkan bot @${bot.botUsername} pada jembatan.")
                // Start background service if not started
                if (!TelegramPollingService.isRunning) {
                    TelegramPollingService.startService(context)
                }
            } else {
                telegramRepository.logInfo("Menonaktifkan bot @${bot.botUsername} dari jembatan.")
                // If there are no active bots, shut down service
                val activeBots = telegramRepository.getActiveBotsSync()
                val isAnyActive = activeBots.any { it.id != bot.id && it.isActive }
                if (!isAnyActive && TelegramPollingService.isRunning) {
                    TelegramPollingService.stopService(context)
                }
            }
        }
    }

    fun deleteTelegramBot(botId: Long) {
        viewModelScope.launch {
            telegramRepository.deleteBot(botId)
            _toastEvent.emit("Bot Telegram terhapus dari konfigurasi.")
            
            // Check if service needs to stop
            val activeBots = telegramRepository.getActiveBotsSync()
            if (activeBots.isEmpty() && TelegramPollingService.isRunning) {
                TelegramPollingService.stopService(context)
            }
        }
    }

    // --- Service Control Actions ---

    fun toggleBridgeService(start: Boolean) {
        viewModelScope.launch {
            if (start) {
                val activeBots = telegramRepository.getActiveBotsSync()
                if (activeBots.isEmpty()) {
                    _toastEvent.emit("Aktifkan minimal 1 bot terlebih dahulu di menu Settings")
                    return@launch
                }
                TelegramPollingService.startService(context)
                _isBridgeActive.value = true
                _toastEvent.emit("Koneksi bot Telegram aktif!")
            } else {
                TelegramPollingService.stopService(context)
                _isBridgeActive.value = false
                _toastEvent.emit("Koneksi bot Telegram dihentikan.")
            }
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            telegramRepository.clearLogs()
            _toastEvent.emit("Log aktivitas berhasil dibersihkan.")
        }
    }
}
