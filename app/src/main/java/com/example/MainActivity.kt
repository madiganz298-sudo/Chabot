package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.example.ui.AppNavigationContainer
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var textToSpeech: TextToSpeech
    
    // State to hold speech recognition transcription output
    private val voiceInputResult = mutableStateOf("")

    // Handler for Speech-to-Text dynamic intent
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = results?.getOrNull(0) ?: ""
            voiceInputResult.value = text
        }
    }

    // Dynamic Permission Requests
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordGranted) {
            Toast.makeText(this, "Izin Mikrofon ditolak. VOICE input tidak akan bekerja.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-Edge full immersion
        enableEdgeToEdge()

        // Initialize dependencies provider
        ServiceLocator.init(applicationContext)

        // Initialize TextToSpeech engine
        textToSpeech = TextToSpeech(this, this)

        // Request runtime permissions dynamically
        requestRequiredPermissions()

        setContent {
            MyApplicationTheme {
                AppNavigationContainer(
                    viewModel = viewModel,
                    onSpeakText = { text -> speakText(text) },
                    onStartVoiceInput = { startVoiceInput() },
                    voiceInputResult = voiceInputResult.value
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startVoiceInput() {
        voiceInputResult.value = "" // Reset state before launching
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Silakan bicara...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition tidak didukung di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakText(text: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "M4DiSpeakID")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English if Indonesian voice pack not installed
                textToSpeech.language = Locale.US
            }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
