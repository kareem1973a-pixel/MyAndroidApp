package com.nova.ai.assistant.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

sealed interface SpeechResult {
    data class Partial(val text: String) : SpeechResult
    data class Success(val text: String) : SpeechResult
    data class RmsChanged(val rmsDb: Float) : SpeechResult
    data class Error(val errorMsg: String) : SpeechResult
    data object EndOfSpeech : SpeechResult
}

class SpeechToTextManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    suspend fun startListening(languageCode: String = "ar-EG"): Flow<SpeechResult> = withContext(Dispatchers.Main.immediate) {
        callbackFlow {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                trySend(SpeechResult.Error("ميزة التعرف على الصوت غير مدعومة على هذا الجهاز"))
                close()
                return@callbackFlow
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SpeechResult.RmsChanged(rmsdB))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    trySend(SpeechResult.EndOfSpeech)
                }
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمعك بوضوح"
                        SpeechRecognizer.ERROR_NETWORK -> "فشل الاتصال بالإنترنت"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهت مهلة الاستماع"
                        else -> "خطأ في استقبال الصوت ($error)"
                    }
                    trySend(SpeechResult.Error(message))
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        trySend(SpeechResult.Success(text))
                    } else {
                        trySend(SpeechResult.Error("لم يتم التقاط صوت واضح"))
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { trySend(SpeechResult.Partial(it)) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)

            awaitClose {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }
}
