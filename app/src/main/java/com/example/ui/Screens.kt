package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ApiKeyConfig
import com.example.data.model.ChatMessage
import com.example.data.model.TelegramBotConfig
import com.example.data.model.TelegramLogEntry
import com.example.ui.theme.*
import com.example.util.EncryptionUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppNavigationContainer(
    viewModel: MainViewModel,
    onSpeakText: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    voiceInputResult: String
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Trigger toast events from ViewModel flow
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat", tint = if (selectedTab == 0) GoldPremium else WhiteText) },
                    label = { Text("Chat AI", color = if (selectedTab == 0) GoldPremium else GrayMuted) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = BubbleUser
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = if (selectedTab == 1) GoldPremium else WhiteText) },
                    label = { Text("Log Bot", color = if (selectedTab == 1) GoldPremium else GrayMuted) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = BubbleUser
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (selectedTab == 2) GoldPremium else WhiteText) },
                    label = { Text("Setelan", color = if (selectedTab == 2) GoldPremium else GrayMuted) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = BubbleUser
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "About", tint = if (selectedTab == 3) GoldPremium else WhiteText) },
                    label = { Text("M4Di", color = if (selectedTab == 3) GoldPremium else GrayMuted) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = BubbleUser
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatScreen(
                    viewModel = viewModel,
                    onSpeakText = onSpeakText,
                    onStartVoiceInput = onStartVoiceInput,
                    voiceInputResult = voiceInputResult
                )
                1 -> TelegramLogScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
                3 -> AboutScreen()
            }
        }
    }
}

// ==========================================
// 1. CHAT SCREEN (AI Core Conversation Model)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onSpeakText: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    voiceInputResult: String
) {
    val context = LocalContext.current
    val activeThread by viewModel.activeThreadId.collectAsStateWithLifecycle()
    val threadsList by viewModel.threads.collectAsStateWithLifecycle()
    val rawMsgList by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isBridgeOn by viewModel.isBridgeActive.collectAsStateWithLifecycle()
    val assistantName by viewModel.assistantName.collectAsStateWithLifecycle()
    val activeModel by viewModel.aiModel.collectAsStateWithLifecycle()

    var chatInputText by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Synchronize voice recording transcription
    LaunchedEffect(voiceInputResult) {
        if (voiceInputResult.isNotBlank()) {
            chatInputText = voiceInputResult
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- CUSTOM PREMIUM BRANDED HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branded Custom Logo
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "M4Di",
                        color = GoldPremium,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Chat AI",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
                Text(
                    text = "Asisten: $assistantName (${activeModel.substringAfterLast("/")})",
                    fontSize = 11.sp,
                    color = GrayMuted,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }

            // Connection Badge Status of Telegram Polling Bridge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isBridgeOn) Color(0xFF1B3B2B) else Color(0xFF2B2B2B))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Surface(
                    color = if (isBridgeOn) Color.Green else Color.Gray,
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isBridgeOn) "Bot On-Air" else "Bot Off",
                    color = if (isBridgeOn) Color.Green else GrayMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Thread Switch Dropdown Selector
            Box {
                IconButton(onClick = { expandedDropdown = true }) {
                    Icon(Icons.Default.History, "Sessions List", tint = GoldPremium)
                }
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("➕ Sesi Baru", color = GoldPremium, fontWeight = FontWeight.Bold) },
                        onClick = {
                            viewModel.startNewThread()
                            expandedDropdown = false
                        }
                    )
                    Divider(color = Color.DarkGray)
                    threadsList.forEach { thId ->
                        DropdownMenuItem(
                            text = { Text(thId, color = if (thId == activeThread) GoldPremium else WhiteText) },
                            onClick = {
                                viewModel.selectThread(thId)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // Delete active session button
            IconButton(onClick = { viewModel.deleteThread(activeThread) }) {
                Icon(Icons.Default.DeleteOutline, "Hapus Sesi", tint = Color.Red)
            }

            // Export Chat Action Button
            IconButton(onClick = { exportChatHistory(context, activeThread, rawMsgList) }) {
                Icon(Icons.Default.Share, "Ekspor Chat", tint = Color.White)
            }
        }

        // --- MESSAGE VIEWPORT ---
        val scrollState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(rawMsgList.size) {
            if (rawMsgList.isNotEmpty()) {
                coroutineScope.launch {
                    scrollState.animateScrollToItem(rawMsgList.size - 1)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (rawMsgList.isEmpty()) {
                // Empty Illustration View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "No Chat Illustration",
                        tint = GoldPremium.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Mulai obrolan baru dengan $assistantName!",
                        color = WhiteText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Konfigurasikan API key OpenRouter Anda di menu Setelan untuk bertukar pikiran sekarang.",
                        color = GrayMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rawMsgList) { msg ->
                        ChatBubbleRow(msg = msg, onSpeakText = onSpeakText)
                    }

                    if (isGenerating) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = BubbleAI),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = GoldPremium,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Mengetik...",
                                            fontSize = 13.sp,
                                            color = GoldPremium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CHAT INPUT BAR ---
        Surface(
            color = DarkSurface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone / Voice input button
                IconButton(
                    onClick = { onStartVoiceInput() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BubbleAI)
                        .size(42.dp)
                ) {
                    Icon(Icons.Default.Mic, "Input Suara", tint = GoldPremium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Input Text Field
                OutlinedTextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = { Text("Tulis pesan ke AI...", color = GrayMuted, fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 100.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldPremium,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (chatInputText.isNotBlank() && !isGenerating) {
                            viewModel.sendMessage(chatInputText)
                            chatInputText = ""
                        }
                    },
                    enabled = chatInputText.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (chatInputText.isNotBlank()) GoldPremium else BubbleAI)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (chatInputText.isNotBlank()) DarkBackground else GrayMuted
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(msg: ChatMessage, onSpeakText: (String) -> Unit) {
    val isUser = msg.sender == "user"
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val cardBg = if (isUser) BubbleUser else BubbleAI
    val borderBrush = if (isUser) BorderStroke(1.dp, GoldPremium.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color.Transparent)
    val cornerShape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        if (!isUser) {
            // AI Mini Avatar Badge
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(GoldPremium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg.assistantName.take(1).uppercase(),
                    color = DarkBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = cornerShape,
                border = borderBrush,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Sender context
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isUser) "Anda" else msg.assistantName,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) GoldPremium else GoldAlt,
                            fontSize = 12.sp
                        )
                        if (msg.isTelegram) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "From Telegram Bot",
                                tint = Color(0xFF2CA5E0),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Text Content
                    Text(
                        text = msg.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start
                    )
                }
            }

            // Options (Audio Text-to-Speech Button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Output time formatted
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = sdf.format(Date(msg.timestamp)),
                    fontSize = 10.sp,
                    color = GrayMuted,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                )

                if (!isUser) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Text to Speech Output",
                        tint = GoldPremium.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSpeakText(msg.content) }
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(6.dp))
            // User initials Avatar
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BubbleUser)
                    .border(1.dp, GoldPremium, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Avatar",
                    tint = GoldPremium,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ==========================================
// 2. TELEGRAM LOG MONITORING SCREEN
// ==========================================
@Composable
fun TelegramLogScreen(viewModel: MainViewModel) {
    val isBridgeRunning by viewModel.isBridgeActive.collectAsStateWithLifecycle()
    val logList by viewModel.telegramLogs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(logList.size) {
        if (logList.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0) // Stay anchored at top (newest logs)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- BRIDGE CONTROL BAR ---
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Juru Jembatan Telegram",
                        fontWeight = FontWeight.Bold,
                        color = GoldPremium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (isBridgeRunning) "Status: Jembatan Aktif (Polling)" else "Status: Jembatan Berhenti",
                        color = if (isBridgeRunning) Color.Green else GrayMuted,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = isBridgeRunning,
                    onCheckedChange = { viewModel.toggleBridgeService(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPremium,
                        checkedTrackColor = BubbleUser,
                        uncheckedThumbColor = GrayMuted,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- TRAFFIC ACTIVITY HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, "Router Console", tint = GoldPremium)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Konsol Aktivitas Router",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            IconButton(onClick = { viewModel.clearLogHistory() }) {
                Icon(Icons.Default.Delete, "Clear Logs", tint = Color.Red.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- LOG DISPLAY VIEW ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0D0D))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (logList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada trafik log.\nAktifkan bot Telegram dan kirim pesan ke bot Anda.",
                        color = GrayMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logList) { log ->
                        LogLineItem(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogLineItem(log: TelegramLogEntry) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timestamp = sdf.format(Date(log.timestamp))

    // Color logic according to log message direction
    val (labelColor, contentColor, tag) = when (log.direction) {
        "IN" -> Triple(Color.Green, Color.White, "[TELEGRAM IN]")
        "OUT" -> Triple(GoldAlt, Color.White, "[AI OUT]")
        "ERROR" -> Triple(Color.Red, Color.Red, "[CON-ERROR]")
        else -> Triple(Color.Cyan, GrayMuted, "[INFO]")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$timestamp ",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "$tag ",
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = log.message,
            color = contentColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==========================================
// 3. SETTINGS & PROFILE CONFIGURATION SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val apiKeysList by viewModel.apiKeys.collectAsStateWithLifecycle()
    val botsList by viewModel.telegramBots.collectAsStateWithLifecycle()
    
    val currentAsName by viewModel.assistantName.collectAsStateWithLifecycle()
    val currentModel by viewModel.aiModel.collectAsStateWithLifecycle()
    val currentAvatarIndex by viewModel.avatarIndex.collectAsStateWithLifecycle()

    var editingAsName by remember { mutableStateOf("") }
    var editingModel by remember { mutableStateOf("") }
    var editingAvatarIndex by remember { mutableStateOf(0) }

    // Synchronize current saved values to editable state fields
    LaunchedEffect(currentAsName, currentModel, currentAvatarIndex) {
        editingAsName = currentAsName
        editingModel = currentModel
        editingAvatarIndex = currentAvatarIndex
    }

    // Individual Text Field States for OpenRouter Slots
    var key1 by remember { mutableStateOf("") }
    var key2 by remember { mutableStateOf("") }
    var key3 by remember { mutableStateOf("") }
    var key4 by remember { mutableStateOf("") }

    // Initialize API keys from DB configs
    LaunchedEffect(apiKeysList) {
        apiKeysList.forEach { apiK ->
            try {
                val decrypted = EncryptionUtils.decrypt(apiK.key)
                when (apiK.id) {
                    1 -> key1 = decrypted
                    2 -> key2 = decrypted
                    3 -> key3 = decrypted
                    4 -> key4 = decrypted
                }
            } catch (e: Exception) {}
        }
    }

    // Bot Configuration Inputs
    var inputBotToken by remember { mutableStateOf("") }
    var inputBotChatId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- SECTION 1: AI CHARACTER DESIGN ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Face, "AI Settings", tint = GoldPremium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Profil Karakter AI",
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Nama Asisten", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = editingAsName,
                        onValueChange = { editingAsName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPremium, unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Model AI (OpenRouter ID)", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = editingModel,
                        onValueChange = { editingModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPremium, unfocusedBorderColor = Color.DarkGray
                        )
                    )
                    Text("Model default: openrouter/free (ganti manual bila diinginkan).", color = GrayMuted, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Avatar selectors
                    Text("Avatar Asisten", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val avatars = listOf("🤖", "🌌", "👑", "🔮")
                        avatars.forEachIndexed { idx, avChar ->
                            val isSelected = editingAvatarIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) GoldPremium else BubbleAI)
                                    .border(1.dp, if (isSelected) GoldAlt else Color.Transparent, CircleShape)
                                    .clickable { editingAvatarIndex = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(avChar, fontSize = 20.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.savePersonalization(editingAsName, editingModel, editingAvatarIndex) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Konfigurasi AI", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- SECTION 2: OPENROUTER API SLOTS (ROTATION DESIGN) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, "Key Manager", tint = GoldPremium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manajemen Key OpenRouter",
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "Input hingga 4 Slot API Key. Sistem akan otomatis melakukan rotasi bila limit 429 terjadi.",
                        color = GrayMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Slot 1
                    ApiKeyRowItem(slotId = 1, keyValue = key1, onSave = { viewModel.saveApiKey(1, it) }, onDelete = { viewModel.deleteApiKey(1) })
                    Spacer(modifier = Modifier.height(10.dp))
                    // Slot 2
                    ApiKeyRowItem(slotId = 2, keyValue = key2, onSave = { viewModel.saveApiKey(2, it) }, onDelete = { viewModel.deleteApiKey(2) })
                    Spacer(modifier = Modifier.height(10.dp))
                    // Slot 3
                    ApiKeyRowItem(slotId = 3, keyValue = key3, onSave = { viewModel.saveApiKey(3, it) }, onDelete = { viewModel.deleteApiKey(3) })
                    Spacer(modifier = Modifier.height(10.dp))
                    // Slot 4
                    ApiKeyRowItem(slotId = 4, keyValue = key4, onSave = { viewModel.saveApiKey(4, it) }, onDelete = { viewModel.deleteApiKey(4) })
                }
            }
        }

        // --- SECTION 3: ADD TELEGRAM BOT ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, "Add Telegram Bot", tint = GoldPremium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sambung Bot Telegram Baru",
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "Konfigurasikan bot pribadi, dapatkan Token dari @BotFather di Telegram.",
                        color = GrayMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Text("Bot Token", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = inputBotToken,
                        onValueChange = { inputBotToken = it },
                        placeholder = { Text("6123456789:AAH-...", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPremium, unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Penerima Chat ID Telegram", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = inputBotChatId,
                        onValueChange = { inputBotChatId = it },
                        placeholder = { Text("1234567890", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPremium, unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.addTelegramBot(inputBotToken, inputBotChatId)
                            inputBotToken = ""
                            inputBotChatId = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hubungkan & Tes Bot", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- SECTION 4: ACTIVE TELEGRAM BOTS LIST ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, "Registered Bots", tint = GoldPremium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daftar Sambungan Bot Telegram",
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            fontSize = 16.sp
                        )
                    }

                    if (botsList.isEmpty()) {
                        Text(
                            text = "Belum ada bot terhubung.",
                            color = GrayMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        botsList.forEach { bot ->
                            TelegramBotRowItem(
                                bot = bot,
                                onToggleActive = { viewModel.toggleBotActive(bot, it) },
                                onDelete = { viewModel.deleteTelegramBot(bot.id) }
                            )
                            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeyRowItem(
    slotId: Int,
    keyValue: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    LaunchedEffect(keyValue) {
        rawText = keyValue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Slot #$slotId",
            fontWeight = FontWeight.Bold,
            color = GoldAlt,
            fontSize = 13.sp,
            modifier = Modifier.width(60.dp)
        )

        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            placeholder = { Text("sk-or-...", color = Color.Gray, fontSize = 11.sp) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = GoldPremium, unfocusedBorderColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(onClick = { onSave(rawText) }) {
            Icon(Icons.Default.Save, "Save Slot", tint = Color.Green)
        }

        IconButton(onClick = {
            onDelete()
            rawText = ""
        }) {
            Icon(Icons.Default.Clear, "Clear Slot", tint = Color.Red.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun TelegramBotRowItem(
    bot: TelegramBotConfig,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bot: @${bot.botUsername}",
                fontWeight = FontWeight.Bold,
                color = WhiteText,
                fontSize = 14.sp
            )
            Text(
                text = "Chat ID: ${bot.chatId}",
                color = GrayMuted,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = bot.isActive,
            onToggleActive,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldPremium,
                checkedTrackColor = BubbleUser,
                uncheckedThumbColor = GrayMuted,
                uncheckedTrackColor = Color.DarkGray
            )
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete Bot Link", tint = Color.Red.copy(alpha = 0.8f))
        }
    }
}

// ==========================================
// 4. ABOUT / BRAND INFORMATION SCREEN
// ==========================================
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Luxury Golden Badge Logo
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(GoldPremium, BubbleUser)))
                .border(2.dp, GoldPremium, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M4Di",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "M4DiChat AI",
            fontWeight = FontWeight.Bold,
            color = GoldPremium,
            fontSize = 24.sp
        )
        Text(
            text = "Yurisdiksi Keasistenan AI Premium",
            color = WhiteText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Premium Telegram integration chatbot engine version 1.0",
            color = GrayMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.fillMaxWidth(0.8f))

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Bagaimana Cara Kerja Jembatan Telegram?",
            color = GoldAlt,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "1. Dapatkan token dari @BotFather di aplikasi Telegram Anda.\n" +
                   "2. Daftarkan Token dan Chat ID Anda di menu Setelan.\n" +
                   "3. Nyalakan toggle Bot di Setelan dan aktifkan jembatan di menu Log Bot.\n" +
                   "4. Kirim pesan apa saja ke bot Telegram tersebut, maka AI akan membalas di Telegram dan riwayatnya akan tersambung ke aplikasi ini secara mulus!",
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Dikembangkan Oleh:",
            color = GrayMuted,
            fontSize = 11.sp
        )
        Text(
            text = "M4DI~UciH4",
            color = GoldPremium,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            fontFamily = FontFamily.Serif
        )
    }
}

// ==========================================
// UTILITY CHAT EXPORTER (TXT SHARE INTENT)
// ==========================================
private fun exportChatHistory(context: Context, threadId: String, messages: List<ChatMessage>) {
    if (messages.isEmpty()) {
        android.widget.Toast.makeText(context, "Sesi obrolan kosong, tidak ada yang bisa diekspor.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val stringBuilder = java.lang.StringBuilder()
    stringBuilder.append("====== M4DiChat AI - EKSPOR OBROLAN ======\n")
    stringBuilder.append("Sesi Thread: $threadId\n")
    stringBuilder.append("Waktu Ekspor: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
    stringBuilder.append("Platform Pengembang: M4DI~UciH4\n")
    stringBuilder.append("=========================================\n\n")

    messages.forEach { msg ->
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formattedTime = sdf.format(Date(msg.timestamp))
        val senderLabel = if (msg.sender == "user") "ANDA" else msg.assistantName.uppercase()
        stringBuilder.append("[$formattedTime] $senderLabel:\n")
        stringBuilder.append(msg.content)
        stringBuilder.append("\n\n")
    }

    stringBuilder.append("====== AKHIR DOKUMEN ======")

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, stringBuilder.toString())
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, "Ekspor Obrolan M4DiChat AI")
    context.startActivity(shareIntent)
}
