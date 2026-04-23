package trucker.geminilive.controller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import trucker.geminilive.audio.SttManager
import trucker.geminilive.audio.TtsManager
import trucker.geminilive.network.*
import trucker.geminilive.tools.TruckingTools

/**
 * Co-Pilot Logic Controller.
 * Orchestrates the state machine between STT, LLM Gateway, and TTS.
 * Designed for hands-free / eyes-free in-cab operation on low-bandwidth LTE.
 */
class CoPilotController(
    context: Context,
    private val sttManager: SttManager,
    private val ttsManager: TtsManager,
    private val geminiClient: GeminiRestClient
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(CopilotUiState())
    val uiState: StateFlow<CopilotUiState> = _uiState

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private var interactionId: String? = null
    private var processingJob: Job? = null
    private var isActive = false

    init {
        sttManager.setCallbacks(
            onFinalResult = { text ->
                addLog("STT Final: $text")
                _partialText.value = ""
                processUserText(text)
            },
            onError = { errorCode ->
                addLog("STT Error: $errorCode")
                _partialText.value = ""
                if (isActive && _uiState.value.aiState == AiState.LISTENING) {
                    // Small error recovery delay, then resume listening
                    scope.launch {
                        delay(500)
                        startListening()
                    }
                }
            }
        )

        // Wire STT partial results to UI
        scope.launch {
            sttManager.partialResults.collect { partial ->
                _partialText.value = partial
            }
        }

        // TTS completion auto-resumes listening
        ttsManager.setOnSpeakComplete {
            addLog("TTS complete")
            if (isActive) {
                scope.launch {
                    delay(300)
                    transitionTo(AiState.IDLE)
                    startListening()
                }
            }
        }
    }

    fun start() {
        if (isActive) return
        isActive = true
        interactionId = null
        _logs.value = emptyList()
        _partialText.value = ""
        updateUi {
            it.copy(
                isConnected = true,
                status = "Connected",
                aiState = AiState.IDLE,
                currentTool = "",
                lastError = "",
                userText = "",
                geminiText = ""
            )
        }
        addLog("Session started")
        startListening()
    }

    fun stop() {
        isActive = false
        processingJob?.cancel()
        sttManager.stopListening()
        ttsManager.stop()
        interactionId = null
        updateUi {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                aiState = AiState.IDLE
            )
        }
        addLog("Session stopped")
    }

    fun onActiveKeyPressed() {
        addLog("Active key pressed")
        if (!isActive) {
            start()
            return
        }
        when (_uiState.value.aiState) {
            AiState.IDLE, AiState.LISTENING -> {
                // If already listening, this keypress can be used to force-stop and process
                // For now, just restart listening
                sttManager.stopListening()
                startListening()
            }
            AiState.THINKING, AiState.WORKING, AiState.SPEAKING -> {
                // Cancel current operation and return to listening
                processingJob?.cancel()
                ttsManager.stop()
                startListening()
            }
            AiState.OFFLINE -> {
                // Try to come back online
                transitionTo(AiState.IDLE)
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!isActive) return
        if (_uiState.value.aiState == AiState.SPEAKING) return

        transitionTo(AiState.LISTENING)
        addLog("Listening...")
        sttManager.startListening()
    }

    private fun processUserText(text: String) {
        if (text.isBlank()) {
            startListening()
            return
        }

        processingJob?.cancel()
        processingJob = scope.launch {
            updateUi { it.copy(userText = text) }
            transitionTo(AiState.THINKING)

            try {
                val apiKey = VertexAuth.getApiKey(appContext)
                var streamingStarted = false
                val partialTextBuffer = StringBuilder()

                val response = geminiClient.sendMessageStream(
                    textInput = text,
                    apiKey = apiKey,
                    previousInteractionId = interactionId
                ) { deltaText ->
                    synchronized(partialTextBuffer) {
                        partialTextBuffer.append(deltaText)
                        val buffered = partialTextBuffer.toString()
                        val shouldStartSpeaking = !streamingStarted &&
                                (buffered.length >= 40 || buffered.count { it in sentenceEndChars } >= 1)

                        if (shouldStartSpeaking) {
                            streamingStarted = true
                            val firstChunk = buffered.trim()
                            partialTextBuffer.clear()

                            scope.launch {
                                addLog("Gemini streaming: ${firstChunk.take(80)}...")
                                transitionTo(AiState.SPEAKING)
                                ttsManager.streamText(firstChunk)
                            }
                        } else if (streamingStarted) {
                            val delta = deltaText.trim()
                            if (delta.isNotEmpty()) {
                                scope.launch {
                                    ttsManager.streamText(delta)
                                }
                            }
                        }
                    }
                }

                // Flush any remaining buffered text
                synchronized(partialTextBuffer) {
                    if (partialTextBuffer.isNotBlank()) {
                        val leftover = partialTextBuffer.toString().trim()
                        partialTextBuffer.clear()
                        if (leftover.isNotBlank()) {
                            if (!streamingStarted) {
                                transitionTo(AiState.SPEAKING)
                            }
                            ttsManager.streamText(leftover)
                        }
                    }
                }
                ttsManager.flushStreamBuffer()

                if (!streamingStarted) {
                    // No streaming text received at all
                    handleResponse(response)
                } else {
                    // Streaming handled text; parse final response for interaction ID and tool calls
                    when (response) {
                        is GeminiResponse.Text -> {
                            interactionId = response.interactionId
                            addLog("Interaction completed: ${response.interactionId}")
                            // TTS completion callback will resume listening
                        }
                        is GeminiResponse.NeedsFunctionCall -> {
                            interactionId = response.interactionId
                            handleToolCalls(response)
                        }
                        is GeminiResponse.Error -> {
                            addLog("Error: ${response.message}")
                            transitionTo(AiState.IDLE)
                            ttsManager.speak("Sorry, I encountered an error. Please try again.")
                        }
                    }
                }
            } catch (e: NetworkFailureException) {
                addLog("Network failure: ${e.message}")
                transitionTo(AiState.OFFLINE)
                ttsManager.speak("I've lost connection to dispatch. Try again when we have a signal.")
            } catch (e: Exception) {
                addLog("Exception: ${e.message}")
                transitionTo(AiState.IDLE)
                ttsManager.speak("Sorry, I encountered an error. Please try again.")
            }
        }
    }

    private fun handleResponse(response: GeminiResponse) {
        when (response) {
            is GeminiResponse.Text -> {
                interactionId = response.interactionId
                if (response.text.isNotBlank()) {
                    addLog("Gemini: ${response.text.take(100)}...")
                    updateUi { it.copy(geminiText = response.text) }
                    transitionTo(AiState.SPEAKING)
                    ttsManager.speak(response.text)
                } else {
                    startListening()
                }
            }
            is GeminiResponse.NeedsFunctionCall -> {
                interactionId = response.interactionId
                handleToolCalls(response)
            }
            is GeminiResponse.Error -> {
                addLog("Error: ${response.message}")
                updateUi { it.copy(lastError = response.message) }
                transitionTo(AiState.IDLE)
                ttsManager.speak("Sorry, I encountered an error. Please try again.")
            }
        }
    }

    private fun handleToolCalls(response: GeminiResponse.NeedsFunctionCall) {
        if (response.calls.isEmpty()) {
            addLog("Error: Model requested action but provided no function calls")
            transitionTo(AiState.IDLE)
            startListening()
            return
        }

        transitionTo(AiState.WORKING)
        val call = response.calls.first()
        updateUi { it.copy(currentTool = call.name) }
        addLog("TOOL CALL: ${call.name}")

        scope.launch {
            try {
                val apiKey = VertexAuth.getApiKey(appContext)
                val finalResponse = geminiClient.sendFunctionResults(
                    functionResults = response.calls.map {
                        FunctionResult(
                            callId = it.id,
                            name = it.name,
                            result = it.result
                        )
                    },
                    apiKey = apiKey,
                    previousInteractionId = response.interactionId
                )

                when (finalResponse) {
                    is GeminiResponse.Text -> {
                        interactionId = finalResponse.interactionId
                        if (finalResponse.text.isNotBlank()) {
                            addLog("Gemini: ${finalResponse.text.take(100)}...")
                            updateUi { it.copy(geminiText = finalResponse.text, currentTool = "") }
                            transitionTo(AiState.SPEAKING)
                            ttsManager.speak(finalResponse.text)
                        } else {
                            updateUi { it.copy(currentTool = "") }
                            startListening()
                        }
                    }
                    is GeminiResponse.Error -> {
                        addLog("Error: ${finalResponse.message}")
                        updateUi { it.copy(lastError = finalResponse.message, currentTool = "") }
                        transitionTo(AiState.IDLE)
                        ttsManager.speak("Sorry, I encountered an error. Please try again.")
                    }
                    else -> {
                        addLog("Unexpected response type")
                        updateUi { it.copy(currentTool = "") }
                        startListening()
                    }
                }
            } catch (e: NetworkFailureException) {
                addLog("Network failure during tool call: ${e.message}")
                transitionTo(AiState.OFFLINE)
                ttsManager.speak("I've lost connection to dispatch. Try again when we have a signal.")
            } catch (e: Exception) {
                addLog("Exception during tool call: ${e.message}")
                transitionTo(AiState.IDLE)
                ttsManager.speak("Sorry, I encountered an error. Please try again.")
            }
        }
    }

    private fun transitionTo(newState: AiState) {
        val current = _uiState.value.aiState
        if (current == newState) return
        Log.d("CoPilotController", "State transition: $current -> $newState")
        updateUi { it.copy(aiState = newState) }
    }

    private fun updateUi(reducer: (CopilotUiState) -> CopilotUiState) {
        _uiState.value = reducer(_uiState.value)
    }

    private fun addLog(message: String) {
        val timestamped = "[${System.currentTimeMillis() % 100000}] $message"
        _logs.value = _logs.value.takeLast(99) + timestamped
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    companion object {
        private val sentenceEndChars = charArrayOf('.', '?', '!', '\n')
    }
}

data class CopilotUiState(
    val isConnected: Boolean = false,
    val aiState: AiState = AiState.IDLE,
    val status: String = "Disconnected",
    val userText: String = "",
    val geminiText: String = "",
    val currentTool: String = "",
    val lastError: String = ""
)

enum class AiState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    THINKING("Thinking..."),
    WORKING("Checking Data..."),
    SPEAKING("Speaking..."),
    OFFLINE("Offline")
}
