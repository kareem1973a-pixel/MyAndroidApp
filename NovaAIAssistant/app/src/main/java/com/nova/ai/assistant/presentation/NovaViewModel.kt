package com.nova.ai.assistant.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.ai.assistant.data.device.ActionResult
import com.nova.ai.assistant.data.device.DeviceActionExecutor
import com.nova.ai.assistant.data.local.MemoryDao
import com.nova.ai.assistant.data.local.MemoryEntity
import com.nova.ai.assistant.data.repository.NovaAiRepository
import com.nova.ai.assistant.data.voice.SpeechResult
import com.nova.ai.assistant.data.voice.SpeechToTextManager
import com.nova.ai.assistant.data.voice.TextToSpeechManager
import com.nova.ai.assistant.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NovaSiriUiState(
    val voiceState: VoiceState = VoiceState.Idle,
    val spokenUserText: String = "",
    val assistantReplyText: String = "أنا جاهز للاستماع لأوامرك.",
    val currentAction: NovaAction? = null,
    val isPendingCallConfirmation: Boolean = false,
    val pendingPhoneNumber: String = "",
    val pendingContactName: String = "",
    val languageCode: String = "ar-EG"
)

class NovaViewModel(
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TextToSpeechManager,
    private val aiRepository: NovaAiRepository,
    private val actionExecutor: DeviceActionExecutor,
    private val memoryDao: MemoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovaSiriUiState())
    val uiState: StateFlow<NovaSiriUiState> = _uiState.asStateFlow()

    fun onOrbClicked() {
        if (_uiState.value.voiceState is VoiceState.Speaking) {
            // مقاطعة الرد الصوتي فوراً
            stopSpeaking()
            startListeningFlow()
        } else if (_uiState.value.voiceState is VoiceState.Listening) {
            sttManager.stopListening()
            _uiState.update { it.copy(voiceState = VoiceState.Idle) }
        } else {
            ttsManager.stop()
            startListeningFlow()
        }
    }

    private fun startListeningFlow() {
        viewModelScope.launch {
            sttManager.startListening(_uiState.value.languageCode).collect { result ->
                when (result) {
                    is SpeechResult.RmsChanged -> {
                        _uiState.update { it.copy(voiceState = VoiceState.Listening(rmsDb = result.rmsDb)) }
                    }
                    is SpeechResult.Partial -> {
                        _uiState.update { it.copy(spokenUserText = result.text) }
                    }
                    is SpeechResult.Success -> {
                        _uiState.update { it.copy(spokenUserText = result.text) }
                        processVoiceQuery(result.text)
                    }
                    is SpeechResult.Error -> {
                        _uiState.update { it.copy(voiceState = VoiceState.Error(result.errorMsg)) }
                    }
                    is SpeechResult.EndOfSpeech -> {
                        _uiState.update { it.copy(voiceState = VoiceState.Processing) }
                    }
                }
            }
        }
    }

    fun processVoiceQuery(query: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    spokenUserText = query,
                    voiceState = VoiceState.Processing,
                    currentAction = null
                )
            }

            val memories = memoryDao.getAllMemories().firstOrNull()?.associate {
                it.memoryKey to it.memoryValue
            } ?: emptyMap()

            val result = aiRepository.processUserQuery(query, memories)

            result.onSuccess { aiResponse ->
                val action = aiResponse.executedAction

                if (action is NovaAction.SaveMemory) {
                    memoryDao.insertMemory(
                        MemoryEntity(
                            memoryKey = action.key,
                            memoryValue = action.value
                        )
                    )
                }

                if (action != null && action !is NovaAction.SaveMemory) {
                    handleDeviceAction(aiResponse.replyText, action)
                } else {
                    _uiState.update {
                        it.copy(
                            assistantReplyText = aiResponse.replyText,
                            currentAction = action
                        )
                    }
                    speakResponse(aiResponse.replyText)
                }
            }.onFailure { error ->
                val fallback = "عذراً: \${error.localizedMessage}"
                _uiState.update { it.copy(assistantReplyText = fallback, voiceState = VoiceState.Error(fallback)) }
                speakResponse(fallback)
            }
        }
    }

    private fun handleDeviceAction(replyText: String, action: NovaAction) {
        val result = actionExecutor.execute(action)
        when (result) {
            is ActionResult.Success -> {
                _uiState.update {
                    it.copy(
                        assistantReplyText = replyText,
                        currentAction = action,
                        voiceState = VoiceState.ExecutingAction(result.message)
                    )
                }
                speakResponse(replyText)
            }
            is ActionResult.RequiresConfirmation -> {
                if (action is NovaAction.PrepareCall) {
                    _uiState.update {
                        it.copy(
                            assistantReplyText = result.prompt,
                            currentAction = action,
                            isPendingCallConfirmation = true,
                            pendingPhoneNumber = action.phoneNumber,
                            pendingContactName = action.contactName,
                            voiceState = VoiceState.Idle
                        )
                    }
                    speakResponse(result.prompt)
                }
            }
            is ActionResult.Failure -> {
                _uiState.update {
                    it.copy(
                        assistantReplyText = "\$replyText\n(تنبيه: \${result.reason})",
                        currentAction = action
                    )
                }
                speakResponse(replyText)
            }
        }
    }

    fun confirmCall() {
        val phone = _uiState.value.pendingPhoneNumber
        val result = actionExecutor.makeCall(phone)
        val msg = if (result is ActionResult.Success) result.message else (result as ActionResult.Failure).reason
        _uiState.update {
            it.copy(
                isPendingCallConfirmation = false,
                assistantReplyText = msg
            )
        }
        speakResponse(msg)
    }

    fun dismissCall() {
        _uiState.update { it.copy(isPendingCallConfirmation = false, currentAction = null) }
    }

    private fun speakResponse(text: String) {
        _uiState.update { it.copy(voiceState = VoiceState.Speaking()) }
        ttsManager.speak(text) {
            _uiState.update { it.copy(voiceState = VoiceState.Idle) }
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _uiState.update { it.copy(voiceState = VoiceState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
