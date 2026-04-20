package trucker.geminilive

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import trucker.geminilive.audio.AudioRecorder
import trucker.geminilive.audio.SoundManager
import trucker.geminilive.audio.TtsManager
import trucker.geminilive.network.*

class GeminiViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = mutableStateOf(GeminiUiState())
    val uiState: State<GeminiUiState> = _uiState

    private val _logs = mutableStateListOf<String>()
    val logs: List<String> = _logs

    private val audioRecorder = AudioRecorder()
    private val ttsManager = TtsManager(application)
    private val soundManager = SoundManager()
    private var geminiClient: GeminiRestClient
    private var toolTimerJob: Job? = null
    private var isRecording = false
    private val audioBuffer = java.io.ByteArrayOutputStream()
    private var processingJob: Job? = null
    
    // Server-side state management via interaction ID
    // Replaces client-side conversationHistory
    private var interactionId: String? = null

    init {
        geminiClient = GeminiRestClient(
            onToolCallStarted = { toolName ->
                updateUi { it.copy(currentTool = toolName) }
                addLog("TOOL CALL: $toolName")
            }
        )
    }

    private fun updateUi(reducer: (GeminiUiState) -> GeminiUiState) {
        viewModelScope.launch {
            _uiState.value = reducer(_uiState.value)
        }
    }

    private fun addLog(message: String) {
        viewModelScope.launch {
            val timestamped = "[${System.currentTimeMillis() % 100000}] $message"
            _logs.add(timestamped)
            if (_logs.size > 100) {
                _logs.removeAt(0)
            }
        }
    }

    private fun startRecorder() {
        if (isRecording) return
        isRecording = true
        synchronized(audioBuffer) {
            audioBuffer.reset()
        }
        updateUi { it.copy(aiState = GeminiState.LISTENING) }
        
        audioRecorder.start { audioData ->
            // Accumulate audio data
            synchronized(audioBuffer) {
                audioBuffer.write(audioData)
            }
        }
    }

    private fun stopRecorderAndProcess() {
        if (!isRecording) return
        isRecording = false
        audioRecorder.stop()
        
        // Get accumulated audio
        val audioToProcess = synchronized(audioBuffer) {
            val bytes = audioBuffer.toByteArray()
            audioBuffer.reset()
            bytes
        }
        
        if (audioToProcess.isEmpty()) {
            addLog("No audio to process")
            startRecorder()
            return
        }
        
        addLog("Processing ${audioToProcess.size} bytes of audio")
        processAudio(audioToProcess)
    }

    private fun processAudio(audioData: ByteArray) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            updateUi { it.copy(aiState = GeminiState.THINKING) }
            soundManager.startThinkingLoop()
            
            try {
                val apiKey = VertexAuth.getApiKey(getApplication())
                val partialTextBuffer = StringBuilder()
                var streamingStarted = false

                val response = geminiClient.createInteractionStream(
                    audioData = audioData,
                    apiKey = apiKey,
                    previousInteractionId = interactionId
                ) { deltaText ->
                    synchronized(partialTextBuffer) {
                        partialTextBuffer.append(deltaText)
                        val buffered = partialTextBuffer.toString()
                        val shouldStartSpeaking = !streamingStarted &&
                            (buffered.length >= 18 || buffered.count { it == ' ' } >= 3)

                        if (shouldStartSpeaking) {
                            streamingStarted = true
                            val firstChunk = buffered.trim()
                            partialTextBuffer.clear()

                            soundManager.stopLoop()
                            viewModelScope.launch {
                                addLog("Gemini streaming: ${firstChunk.take(100)}...")
                                updateUi { it.copy(aiState = GeminiState.SPEAKING) }
                                ttsManager.speak(firstChunk)
                            }
                        } else if (streamingStarted) {
                            val delta = deltaText.trim()
                            if (delta.isNotEmpty()) {
                                viewModelScope.launch {
                                    ttsManager.speak(delta)
                                }
                            }
                        }
                    }
                }

                synchronized(partialTextBuffer) {
                    if (partialTextBuffer.isNotBlank()) {
                        val leftover = partialTextBuffer.toString().trim()
                        partialTextBuffer.clear()
                        if (leftover.isNotBlank()) {
                            ttsManager.speak(leftover)
                        }
                    }
                }

                if (!streamingStarted) {
                    soundManager.stopLoop()
                }

                when (response) {
                    is GeminiResponse.Text -> {
                        interactionId = response.interactionId
                        addLog("Interaction ID: ${response.interactionId}")

                        if (!streamingStarted) {
                            soundManager.stopLoop()
                            if (response.text.isNotBlank()) {
                                addLog("Gemini: ${response.text.take(100)}...")
                                updateUi { it.copy(aiState = GeminiState.SPEAKING) }
                                ttsManager.speak(response.text)
                            }
                        }

                        delay(500)
                        startRecorder()
                    }
                    is GeminiResponse.NeedsFunctionCall -> {
                        interactionId = response.interactionId
                        addLog("Interaction ID: ${response.interactionId}")

                        if (response.calls.isEmpty()) {
                            addLog("Error: Model requested action but provided no function calls")
                            soundManager.stopLoop()
                            delay(500)
                            startRecorder()
                            return@launch
                        }

                        updateUi { it.copy(aiState = GeminiState.WORKING) }
                        soundManager.startWorkingLoop()

                        val finalResponse = geminiClient.sendFunctionResults(
                            functionResults = response.calls.map { call ->
                                FunctionResult(
                                    callId = call.id,
                                    name = call.name,
                                    result = call.result
                                )
                            },
                            apiKey = apiKey,
                            previousInteractionId = interactionId!!
                        )

                        soundManager.stopLoop()

                        when (finalResponse) {
                            is GeminiResponse.Text -> {
                                interactionId = finalResponse.interactionId
                                addLog("Interaction ID: ${finalResponse.interactionId}")

                                if (finalResponse.text.isNotBlank()) {
                                    addLog("Gemini: ${finalResponse.text.take(100)}...")
                                    updateUi { it.copy(aiState = GeminiState.SPEAKING) }
                                    ttsManager.speak(finalResponse.text)
                                }
                            }
                            is GeminiResponse.Error -> {
                                addLog("Error: ${finalResponse.message}")
                                ttsManager.speak("Sorry, I encountered an error. Please try again.")
                            }
                            else -> {
                                addLog("Unexpected response type")
                            }
                        }

                        delay(500)
                        startRecorder()
                    }
                    is GeminiResponse.Error -> {
                        soundManager.stopLoop()
                        addLog("Error: ${response.message}")
                        updateUi { it.copy(lastError = response.message, aiState = GeminiState.IDLE) }
                        ttsManager.speak("Sorry, I encountered an error. Please try again.")
                        delay(500)
                        startRecorder()
                    }
                }
            } catch (e: Exception) {
                soundManager.stopLoop()
                addLog("Exception: ${e.message}")
                updateUi { it.copy(lastError = e.message ?: "Unknown error", aiState = GeminiState.IDLE) }
                delay(500)
                startRecorder()
            }
        }
    }

    fun toggleConnection() {
        if (uiState.value.isConnected) {
            stop()
        } else {
            start()
        }
    }

    private fun start() {
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            status = "Connected",
            aiState = GeminiState.IDLE,
            currentTool = "",
            lastError = "",
            userText = "",
            geminiText = ""
        )
        _logs.clear()
        // Clear interaction ID for new session
        interactionId = null
        addLog("Session started - tap button to speak")
    }

    private fun stop() {
        processingJob?.cancel()
        toolTimerJob?.cancel()
        isRecording = false
        audioRecorder.stop()
        ttsManager.stop()
        soundManager.stopLoop()
        // Clear interaction ID when session stops
        interactionId = null
        
        updateUi { it.copy(isConnected = false, status = "Disconnected", aiState = GeminiState.IDLE) }
        addLog("Session stopped")
    }

    fun toggleRecording() {
        if (!uiState.value.isConnected) return
        
        if (isRecording) {
            stopRecorderAndProcess()
        } else {
            startRecorder()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        soundManager.release()
        ttsManager.release()
    }
}

data class GeminiUiState(
    val isConnected: Boolean = false,
    val aiState: GeminiState = GeminiState.IDLE,
    val status: String = "Disconnected",
    val userText: String = "",
    val geminiText: String = "",
    val currentTool: String = "",
    val lastError: String = ""
)
