package com.nova.ai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.nova.ai.assistant.data.device.DeviceActionExecutor
import com.nova.ai.assistant.data.local.NovaDatabase
import com.nova.ai.assistant.data.repository.GeminiNovaAiRepository
import com.nova.ai.assistant.data.voice.SpeechToTextManager
import com.nova.ai.assistant.data.voice.TextToSpeechManager
import com.nova.ai.assistant.presentation.NovaSiriScreen
import com.nova.ai.assistant.presentation.NovaViewModel
import com.nova.ai.assistant.presentation.NovaViewModelFactory
import com.nova.ai.assistant.presentation.theme.NovaAITheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = NovaDatabase.getDatabase(applicationContext)
        val sttManager = SpeechToTextManager(this)
        val ttsManager = TextToSpeechManager(this)
        val actionExecutor = DeviceActionExecutor(this)
        val aiRepository = GeminiNovaAiRepository(BuildConfig.GEMINI_API_KEY)

        val factory = NovaViewModelFactory(
            sttManager = sttManager,
            ttsManager = ttsManager,
            aiRepository = aiRepository,
            actionExecutor = actionExecutor,
            memoryDao = database.memoryDao()
        )

        val viewModel = ViewModelProvider(this, factory)[NovaViewModel::class.java]

        setContent {
            NovaAITheme {
                NovaSiriScreen(viewModel = viewModel)
            }
        }
    }
}
