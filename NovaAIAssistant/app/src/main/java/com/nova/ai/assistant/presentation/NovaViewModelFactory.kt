package com.nova.ai.assistant.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nova.ai.assistant.data.device.DeviceActionExecutor
import com.nova.ai.assistant.data.local.MemoryDao
import com.nova.ai.assistant.data.repository.NovaAiRepository
import com.nova.ai.assistant.data.voice.SpeechToTextManager
import com.nova.ai.assistant.data.voice.TextToSpeechManager

class NovaViewModelFactory(
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TextToSpeechManager,
    private val aiRepository: NovaAiRepository,
    private val actionExecutor: DeviceActionExecutor,
    private val memoryDao: MemoryDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NovaViewModel::class.java)) {
            return NovaViewModel(sttManager, ttsManager, aiRepository, actionExecutor, memoryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
