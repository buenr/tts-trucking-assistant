package trucker.geminilive.controller

import android.content.Context
import android.util.Log
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.FunctionCall
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import trucker.geminilive.audio.SttManager
import trucker.geminilive.audio.TtsManager
import trucker.geminilive.network.*
import trucker.geminilive.tools.TruckingTools
import kotlinx.serialization.json.*

/**
 * Co-Pilot Logic Controller.
 * Orchestrates the state machine between STT, LLM Gateway, and TTS.
 * Designed for hands-free / eyes-free in-cab operation on low-bandwidth LTE.
 */
class CoPilotController(
    context: Context,
    private val sttManager: SttManager,
    private val ttsManager: TtsManager,
    private val onCloseAppRequested: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val vertexAiClient = VertexAiClient(appContext)

    private val _uiState = MutableStateFlow(CopilotUiState())
    val uiState: StateFlow<CopilotUiState> = _uiState

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    // Conversation history for multi-turn context
    private val conversationHistory = mutableListOf<Content>()
    private var processingJob: Job? = null
    private var isActive = false
    private var shouldCloseAppAfterTts = false
    private var speakSessionId: String? = null

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
                // Only retry if we're still supposed to be listening (not processing)
                if (isActive && _uiState.value.aiState == AiState.LISTENING) {
                    // Small error recovery delay, then resume listening
                    scope.launch {
                        delay(500)
                        // Double-check state after delay - don't interrupt CHECKING_DATA/SPEAKING
                        if (_uiState.value.aiState == AiState.LISTENING) {
                            startListening()
                        }
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

        // TTS completion auto-resumes listening (or closes app if requested)
        ttsManager.setOnSpeakComplete {
            addLog("TTS complete")
            if (shouldCloseAppAfterTts) {
                addLog("Closing app as requested")
                onCloseAppRequested()
                return@setOnSpeakComplete
            }
            // Only transition if we're still in SPEAKING state and no new session started
            if (isActive && _uiState.value.aiState == AiState.SPEAKING) {
                scope.launch {
                    delay(300)
                    // Double-check state after delay to prevent race conditions
                    if (_uiState.value.aiState == AiState.SPEAKING) {
                        startListening()
                    }
                }
            }
        }
    }

    fun start() {
        if (isActive) return
        isActive = true
        conversationHistory.clear()
        shouldCloseAppAfterTts = false
        _logs.value = emptyList()
        _partialText.value = ""
        updateUi {
            it.copy(
                isConnected = true,
                status = "Connected",
                aiState = AiState.LISTENING,
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
        conversationHistory.clear()
        updateUi {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                aiState = AiState.LISTENING
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
            AiState.LISTENING -> {
                // If already listening, this keypress can be used to force-stop and process
                // For now, just restart listening
                sttManager.stopListening()
                startListening()
            }
            AiState.CHECKING_DATA, AiState.SPEAKING -> {
                // Cancel current operation and return to listening
                processingJob?.cancel()
                ttsManager.stop()
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!isActive) return

        // Only enter listening from listening/speaking states, not from checking data
        val currentState = _uiState.value.aiState
        if (currentState == AiState.CHECKING_DATA) {
            addLog("Skipping startListening: currently $currentState")
            return
        }

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
            transitionTo(AiState.CHECKING_DATA)

            try {
                var streamingStarted = false
                val partialTextBuffer = StringBuilder()
                val currentHistory = conversationHistory.toList()

                val response = vertexAiClient.sendMessageStream(
                    textInput = text,
                    history = currentHistory
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
                            speakSessionId = "session_${System.currentTimeMillis()}"

                            scope.launch {
                                addLog("Vertex AI streaming: ${firstChunk.take(80)}...")
                                transitionTo(AiState.SPEAKING)
                                ttsManager.streamText(firstChunk, speakSessionId)
                            }
                        } else if (streamingStarted) {
                            val delta = deltaText.trim()
                            if (delta.isNotEmpty()) {
                                scope.launch {
                                    ttsManager.streamText(delta, speakSessionId)
                                }
                            }
                        }
                    }
                }

                // Flush any remaining buffered text in TtsManager
                if (!streamingStarted) {
                    speakSessionId = "session_${System.currentTimeMillis()}"
                    transitionTo(AiState.SPEAKING)
                }
                ttsManager.flushStreamBuffer(speakSessionId)

                // Add user message to history
                conversationHistory.add(Content.fromParts(Part.fromText(text)))

                if (!streamingStarted) {
                    // No streaming text received at all
                    handleResponse(response)
                } else {
                    // Streaming handled text; parse final response
                    when (response) {
                        is GeminiResponse.Text -> {
                            // Add assistant response to history
                            conversationHistory.add(Content.fromParts(Part.fromText(response.text)))
                            addLog("Response completed")
                            // TTS completion callback will resume listening
                        }
                        is GeminiResponse.NeedsFunctionCall -> {
                            handleToolCalls(response)
                        }
                        is GeminiResponse.Error -> {
                            addLog("Error: ${response.message}")
                            speakSessionId = "error_${System.currentTimeMillis()}"
                            transitionTo(AiState.SPEAKING)
                            ttsManager.speak("Sorry, I encountered an error. Please try again.", speakSessionId)
                        }
                    }
                }
            } catch (e: NetworkFailureException) {
                addLog("Network failure: ${e.message}")
                speakSessionId = "network_error_${System.currentTimeMillis()}"
                transitionTo(AiState.SPEAKING)
                ttsManager.speak("I've lost connection to dispatch. Try again when we have a signal.", speakSessionId)
            } catch (e: Exception) {
                addLog("Exception: ${e.message}")
                speakSessionId = "exception_${System.currentTimeMillis()}"
                transitionTo(AiState.SPEAKING)
                ttsManager.speak("Sorry, I encountered an error. Please try again.", speakSessionId)
            }
        }
    }

    private fun handleResponse(response: GeminiResponse) {
        when (response) {
            is GeminiResponse.Text -> {
                if (response.text.isNotBlank()) {
                    addLog("Vertex AI: ${response.text.take(100)}...")
                    updateUi { it.copy(geminiText = response.text) }
                    speakSessionId = "session_${System.currentTimeMillis()}"
                    transitionTo(AiState.SPEAKING)
                    ttsManager.speak(response.text, speakSessionId)
                } else {
                    startListening()
                }
            }
            is GeminiResponse.NeedsFunctionCall -> {
                handleToolCalls(response)
            }
            is GeminiResponse.Error -> {
                addLog("Error: ${response.message}")
                updateUi { it.copy(lastError = response.message) }
                speakSessionId = "error_${System.currentTimeMillis()}"
                transitionTo(AiState.SPEAKING)
                ttsManager.speak("Sorry, I encountered an error. Please try again.", speakSessionId)
            }
        }
    }

    private fun handleToolCalls(response: GeminiResponse.NeedsFunctionCall) {
        if (response.calls.isEmpty()) {
            addLog("Error: Model requested action but provided no function calls")
            startListening()
            return
        }

        transitionTo(AiState.CHECKING_DATA)
        val call = response.calls.first()
        updateUi { it.copy(currentTool = call.name) }
        addLog("TOOL CALL: ${call.name}")

        // Check if this is a closeApp request
        if (call.name == "closeApp") {
            shouldCloseAppAfterTts = true
        }

        scope.launch {
            try {
                // Execute tool calls in the controller
                val functionResults = response.calls.map { call ->
                    val args = call.args?.jsonObject?.mapValues { it.value }
                    val result = TruckingTools.handleToolCall(call.name, args)
                    FunctionResult(
                        callId = call.id,
                        name = call.name,
                        result = result
                    )
                }

                // Add function call to history
                val parts = response.calls.map { call ->
                    val argsMap = call.args?.jsonObject?.mapValues { entry ->
                        val value = entry.value
                        if (value is JsonPrimitive) {
                            if (value.isString) value.content
                            else value.toString()
                        } else value.toString()
                    } ?: emptyMap<String, Any>()

                    Part.builder().functionCall(
                        FunctionCall.builder()
                            .id(call.id)
                            .name(call.name)
                            .args(argsMap)
                            .build()
                    ).build()
                }
                conversationHistory.add(Content.fromParts(*parts.toTypedArray()))

                val currentHistory = conversationHistory.toList()
                val finalResponse = vertexAiClient.sendFunctionResults(
                    functionResults = functionResults,
                    history = currentHistory
                )

                when (finalResponse) {
                    is GeminiResponse.Text -> {
                        if (finalResponse.text.isNotBlank()) {
                            addLog("Vertex AI: ${finalResponse.text.take(100)}...")
                            updateUi { it.copy(geminiText = finalResponse.text, currentTool = "") }
                            // Add assistant response to history
                            conversationHistory.add(Content.fromParts(Part.fromText(finalResponse.text)))
                            speakSessionId = "session_${System.currentTimeMillis()}"
                            transitionTo(AiState.SPEAKING)
                            ttsManager.speak(finalResponse.text, speakSessionId)
                        } else {
                            updateUi { it.copy(currentTool = "") }
                            startListening()
                        }
                    }
                    is GeminiResponse.Error -> {
                        addLog("Error: ${finalResponse.message}")
                        updateUi { it.copy(lastError = finalResponse.message, currentTool = "") }
                        speakSessionId = "error_${System.currentTimeMillis()}"
                        transitionTo(AiState.SPEAKING)
                        ttsManager.speak("Sorry, I encountered an error. Please try again.", speakSessionId)
                    }
                    else -> {
                        addLog("Unexpected response type")
                        updateUi { it.copy(currentTool = "") }
                        transitionTo(AiState.SPEAKING)
                        speakSessionId = "unexpected_${System.currentTimeMillis()}"
                        ttsManager.speak("Sorry, I didn't understand that response. Please try again.", speakSessionId)
                    }
                }
            } catch (e: NetworkFailureException) {
                addLog("Network failure during tool call: ${e.message}")
                speakSessionId = "network_error_${System.currentTimeMillis()}"
                transitionTo(AiState.SPEAKING)
                ttsManager.speak("I've lost connection to dispatch. Try again when we have a signal.", speakSessionId)
            } catch (e: Exception) {
                addLog("Exception during tool call: ${e.message}")
                speakSessionId = "exception_${System.currentTimeMillis()}"
                transitionTo(AiState.SPEAKING)
                ttsManager.speak("Sorry, I encountered an error. Please try again.", speakSessionId)
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
    val aiState: AiState = AiState.LISTENING,
    val status: String = "Disconnected",
    val userText: String = "",
    val geminiText: String = "",
    val currentTool: String = "",
    val lastError: String = ""
)

enum class AiState(val label: String) {
    LISTENING("Listening..."),
    CHECKING_DATA("Checking Data..."),
    SPEAKING("Speaking...")
}
