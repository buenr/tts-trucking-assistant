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

/**
 * Noise profile presets for different driving conditions.
 * Adjusts silence timeout to prevent cutting off speech mid-sentence.
 */
enum class NoiseProfile(
    val label: String,
    val silenceTimeoutMs: Long,
    val possiblyCompleteTimeoutMs: Long,
    val description: String
) {
    QUIET(
        label = "Quiet",
        silenceTimeoutMs = 800L,
        possiblyCompleteTimeoutMs = 800L,
        description = "Office/home environment"
    ),
    NORMAL(
        label = "Normal",
        silenceTimeoutMs = 1000L,
        possiblyCompleteTimeoutMs = 1000L,
        description = "Regular city/suburban driving"
    ),
    LOUD_TRUCK(
        label = "Loud Truck",
        silenceTimeoutMs = 1500L,
        possiblyCompleteTimeoutMs = 1500L,
        description = "Highway/high noise truck environment"
    ),
    CUSTOM(
        label = "Custom",
        silenceTimeoutMs = 1200L,
        possiblyCompleteTimeoutMs = 1200L,
        description = "User-defined settings"
    );

    companion object {
        fun fromLabel(label: String): NoiseProfile {
            return values().firstOrNull { it.label == label } ?: LOUD_TRUCK
        }
    }
}

class SttManager(context: Context) {
    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _currentNoiseProfile = MutableStateFlow(NoiseProfile.LOUD_TRUCK)
    val currentNoiseProfile: StateFlow<NoiseProfile> = _currentNoiseProfile

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

    /**
     * Start listening with the current noise profile.
     * Use [setNoiseProfile] to change the profile before calling this.
     */
    fun startListening() {
        startListeningWithProfile(_currentNoiseProfile.value)
    }

    /**
     * Start listening with a specific noise profile.
     * @param profile The noise profile to use for this listening session
     */
    fun startListeningWithProfile(profile: NoiseProfile) {
        if (_isListening.value) {
            Log.w("SttManager", "Already listening, skipping start")
            return
        }

        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        _currentNoiseProfile.value = profile

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, profile.silenceTimeoutMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, profile.possiblyCompleteTimeoutMs)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d("SttManager", "Started listening with profile: ${profile.label} (silence timeout: ${profile.silenceTimeoutMs}ms)")
        } catch (e: Exception) {
            Log.e("SttManager", "Failed to start listening", e)
            _isListening.value = false
            onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    /**
     * Set the noise profile for future listening sessions.
     * @param profile The noise profile to use
     */
    fun setNoiseProfile(profile: NoiseProfile) {
        _currentNoiseProfile.value = profile
        Log.d("SttManager", "Noise profile changed to: ${profile.label}")
    }

    /**
     * Get the current noise profile.
     */
    fun getNoiseProfile(): NoiseProfile = _currentNoiseProfile.value

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
