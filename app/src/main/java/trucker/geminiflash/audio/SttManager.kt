package trucker.geminiflash.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SttManager(context: Context) {
    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var onFinalResult: ((String) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    private var isOfflineAvailable = false

    init {
        isOfflineAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        if (!isOfflineAvailable) {
            Log.w("SttManager", "On-device recognition not available on this device")
        }
        initializeRecognizer()
    }

    fun setCallbacks(
        onFinalResult: (String) -> Unit,
        onError: (Int) -> Unit
    ) {
        this.onFinalResult = onFinalResult
        this.onError = onError
    }

    fun isOfflineRecognitionAvailable(): Boolean = isOfflineAvailable

    private fun initializeRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("SttManager", "Ready for speech")
                    _isListening.value = true
                    _partialResults.value = ""
                }

                override fun onBeginningOfSpeech() {
                    Log.d("SttManager", "Speech begun")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // No-op — handled by internal VAD
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // No-op
                }

                override fun onEndOfSpeech() {
                    Log.d("SttManager", "Speech ended")
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    Log.e("SttManager", "Speech recognition error: $error")
                    _isListening.value = false
                    _partialResults.value = ""
                    onError?.invoke(error)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val finalText = matches?.firstOrNull()?.trim() ?: ""
                    Log.d("SttManager", "Final result: $finalText")
                    _isListening.value = false
                    _partialResults.value = ""
                    if (finalText.isNotBlank()) {
                        onFinalResult?.invoke(finalText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) {
                        _partialResults.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // No-op
                }
            })
        }
    }

    fun startListening() {
        if (_isListening.value) {
            Log.w("SttManager", "Already listening, skipping start")
            return
        }

        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d("SttManager", "Started listening (offline preferred)")
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to start listening", e)
            _isListening.value = false
            onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun stopListening() {
        if (!_isListening.value) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("SttManager", "Error stopping listening", e)
        }
        _isListening.value = false
        _partialResults.value = ""
    }

    fun destroy() {
        stopListening()
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
