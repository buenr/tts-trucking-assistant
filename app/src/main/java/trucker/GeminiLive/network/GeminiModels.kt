package trucker.geminilive.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============================================
// Shared Models (used by TruckingTools)
// ============================================

@Serializable
data class FunctionDeclaration(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: Schema? = null
)

@Serializable
data class Schema(
    val type: String,
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
    val items: Schema? = null,
    val description: String? = null
)

// ============================================
// UI State
// ============================================

enum class GeminiState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    THINKING("Thinking..."),
    WORKING("Checking Data..."), // Used during Tool Calls
    SPEAKING("Speaking..."),
    OFFLINE("Offline")
}

// ============================================
// Interactions API Models
// ============================================

/**
 * Input content for Interactions API.
 * The API accepts: string, single Content object, or array of Content objects.
 */
@Serializable
data class InputContent(
    val type: String,
    val text: String? = null,
    @SerialName("mime_type")
    val mimeType: String? = null,
    val data: String? = null,
    val name: String? = null,
    @SerialName("call_id")
    val callId: String? = null,
    val result: JsonElement? = null
)

/**
 * Request model for creating a new Interaction.
 * Interactions API endpoint: POST /v1beta/interactions
 */
@Serializable
data class InteractionRequest(
    val model: String? = null,
    val input: JsonElement? = null,
    @SerialName("previous_interaction_id")
    val previousInteractionId: String? = null,
    @SerialName("system_instruction")
    val systemInstruction: String? = null,
    @SerialName("generation_config")
    val generationConfig: InteractionGenerationConfig? = null,
    @SerialName("response_modalities")
    val responseModalities: List<String>? = null,
    val stream: Boolean? = null,
    val tools: List<FunctionDeclaration>? = null
)

@Serializable
data class InteractionStreamEvent(
    @SerialName("event_type")
    val eventType: String,
    val delta: StreamDelta? = null,
    val interaction: InteractionResponse? = null,
    val index: Int? = null
)

@Serializable
data class StreamDelta(
    val type: String? = null,
    val text: String? = null,
    val name: String? = null,
    val id: String? = null,
    val arguments: JsonElement? = null
)

/**
 * System instruction model for Gemini.
 */
@Serializable
data class SystemInstruction(
    val role: String? = "system",
    val parts: List<TextPart>
)

/**
 * A part of a system instruction or message containing text.
 */
@Serializable
data class TextPart(
    val text: String
)

/**
 * Generation configuration for Interactions API.
 */
@Serializable
data class InteractionGenerationConfig(
    val temperature: Double? = null,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null
)

/**
 * Response model from Interactions API.
 */
@Serializable
data class InteractionResponse(
    val id: String,
    val status: String,  // "completed", "requires_action", "failed", "cancelled"
    val outputs: List<OutputContent>? = null,
    val error: InteractionError? = null,
    val usage: UsageInfo? = null
)

/**
 * Output content from an Interaction response.
 * Typed content blocks: text, function_call, thought
 */
@Serializable
data class OutputContent(
    val type: String,  // "text", "function_call", "thought"
    val text: String? = null,
    // For function_call type
    val id: String? = null,
    val name: String? = null,
    val arguments: JsonElement? = null
)

/**
 * Error details from Interactions API response.
 */
@Serializable
data class InteractionError(
    val code: Int? = null,
    val message: String? = null
)

/**
 * Token usage information from Interactions API response.
 */
@Serializable
data class UsageInfo(
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null
)

/**
 * Function result for sending tool execution results back to the model.
 */
@Serializable
data class FunctionResult(
    val callId: String,
    val name: String,
    val result: JsonElement
)

/**
 * Thrown when the network is completely unavailable and all retries are exhausted.
 * The controller should trigger an offline fallback TTS response.
 */
class NetworkFailureException(message: String, cause: Throwable? = null) : Exception(message, cause)

