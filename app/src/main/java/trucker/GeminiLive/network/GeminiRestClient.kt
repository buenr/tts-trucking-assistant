package trucker.geminilive.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import trucker.geminilive.tools.TruckingTools
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * REST client for Gemini Interactions API.
 * Migrated from generateContent to interactions.create endpoint.
 */
class GeminiRestClient(
    private val onToolCallStarted: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
    }
    
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "gemini-2.5-flash"
        private const val AUDIO_MIME_TYPE = "audio/wav"
    }
    
    private val systemInstruction = """
        You are a Swift Transportation trucking copilot (AI Assistant). Use concise, operational language familiar to truck drivers. You are incentivized to provide accurate, data-driven answers by calling tools whenever the driver's request overlaps with available functions. Do not guess or fabricate information if a tool can provide the answer. Tool selection guidance: use getDriverProfile for profile/location/equipment/compliance snapshot requests; use getLoadStatus for active-load timeline, stop status, ETA, and load-specific risks; use getHoursOfServiceClocks for HOS clock and break timing; use getTrafficAndWeather for immediate (1 hour) road conditions, traffic, and weather ahead; use getDispatchInbox for dispatch messages and open exceptions requiring action; use getCompanyFAQs for general company policy/procedure FAQs ; use getPaycheckInfo for paycheck, settlement, CPM, gross/net, and miles-related compensation questions; use findNearestSwiftTerminal to check for nearby Swift yards and amenities; use checkSafetyScore to review driving telematics, harsh braking, and safety bonus standing; use getFuelNetworkRouting to find the next approved in-network fuel stop; use getContacts for phone numbers and contact details for dispatch, leaders, payroll, and support; use getNextLoadDetails for details on the next scheduled load, pickup/delivery windows, and pre-dispatch information; use getMentorFAQs for information on becoming a driver mentor, its benefits, and the application process; use getOwnerOperatorFAQs for information on the owner-operator program. If the driver asks for data or actions outside available tools/data, unmistakably state that the request is out-of-scope (DO NOT fabricate details) and route them to their Driver Leader for support. IMPORTANT: ONLY AND ALWAYS RESPOND IN ENGLISH.
    """.trimIndent()
    
    /**
     * Creates a new interaction with audio input.
     * @param audioData PCM audio bytes (16kHz, 16-bit, mono)
     * @param apiKey API key for authentication
     * @param previousInteractionId Server-managed conversation state from prior interaction
     * @return GeminiResponse with text or function calls
     */
    suspend fun createInteraction(
        audioData: ByteArray,
        apiKey: String,
        previousInteractionId: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val wavData = pcmToWav(audioData)
        val base64Audio = Base64.encodeToString(wavData, Base64.NO_WRAP)
        
        val request = buildInteractionRequest(
            audioBase64 = base64Audio,
            previousInteractionId = previousInteractionId,
            stream = false
        )
        
        executeRequest(request, apiKey)
    }

    /**
     * Creates a new interaction with audio input using streaming.
     * @param audioData PCM audio bytes (16kHz, 16-bit, mono)
     * @param apiKey API key for authentication
     * @param previousInteractionId Server-managed conversation state from prior interaction
     * @param onDelta Callback invoked on incremental text deltas from Gemini
     * @return GeminiResponse with text or function calls once the interaction completes
     */
    suspend fun createInteractionStream(
        audioData: ByteArray,
        apiKey: String,
        previousInteractionId: String? = null,
        onDelta: (String) -> Unit
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val wavData = pcmToWav(audioData)
        val base64Audio = Base64.encodeToString(wavData, Base64.NO_WRAP)

        val request = buildInteractionRequest(
            audioBase64 = base64Audio,
            previousInteractionId = previousInteractionId,
            stream = true
        )

        executeStreamingRequest(request, apiKey, onDelta)
    }
    
    /**
     * Creates a new interaction with text input (for testing).
     * @param textInput The text input for the interaction
     * @param apiKey API key for authentication
     * @param previousInteractionId Server-managed conversation state from prior interaction
     * @return GeminiResponse with text or function calls
     */
    suspend fun createTextInteraction(
        textInput: String,
        apiKey: String,
        previousInteractionId: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val request = buildTextInteractionRequest(
            text = textInput,
            previousInteractionId = previousInteractionId
        )
        
        executeRequest(request, apiKey)
    }

    suspend fun sendFunctionResults(
        functionResults: List<FunctionResult>,
        apiKey: String,
        previousInteractionId: String
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val request = buildFunctionResultRequest(
            functionResults = functionResults,
            previousInteractionId = previousInteractionId
        )
        
        executeRequest(request, apiKey)
    }
    
    /**
     * Converts raw PCM audio bytes to WAV format.
     * Assumes 16kHz, 16-bit, mono PCM.
     */
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val dataSize = pcmData.size
        val fileSize = 44 + dataSize - 8
        val byteArray = ByteArray(44 + dataSize)
        
        // RIFF header
        "RIFF".toByteArray().copyInto(byteArray, 0)
        // File size
        byteArray[4] = (fileSize and 0xFF).toByte()
        byteArray[5] = ((fileSize shr 8) and 0xFF).toByte()
        byteArray[6] = ((fileSize shr 16) and 0xFF).toByte()
        byteArray[7] = ((fileSize shr 24) and 0xFF).toByte()
        // WAVE
        "WAVE".toByteArray().copyInto(byteArray, 8)
        // fmt 
        "fmt ".toByteArray().copyInto(byteArray, 12)
        // fmt chunk size (16)
        byteArray[16] = 16
        // Audio format (1 = PCM)
        byteArray[20] = 1
        // Channels (1)
        byteArray[22] = 1
        // Sample rate (16000)
        val sampleRate = 16000
        byteArray[24] = (sampleRate and 0xFF).toByte()
        byteArray[25] = ((sampleRate shr 8) and 0xFF).toByte()
        byteArray[26] = ((sampleRate shr 16) and 0xFF).toByte()
        byteArray[27] = ((sampleRate shr 24) and 0xFF).toByte()
        // Byte rate (16000 * 1 * 2)
        val byteRate = 16000 * 1 * 2
        byteArray[28] = (byteRate and 0xFF).toByte()
        byteArray[29] = ((byteRate shr 8) and 0xFF).toByte()
        byteArray[30] = ((byteRate shr 16) and 0xFF).toByte()
        byteArray[31] = ((byteRate shr 24) and 0xFF).toByte()
        // Block align (1 * 2)
        byteArray[32] = 2
        // Bits per sample (16)
        byteArray[34] = 16
        // data
        "data".toByteArray().copyInto(byteArray, 36)
        // Data size
        byteArray[40] = (dataSize and 0xFF).toByte()
        byteArray[41] = ((dataSize shr 8) and 0xFF).toByte()
        byteArray[42] = ((dataSize shr 16) and 0xFF).toByte()
        byteArray[43] = ((dataSize shr 24) and 0xFF).toByte()
        // PCM data
        pcmData.copyInto(byteArray, 44)
        
        return byteArray
    }
    private fun buildTextInteractionRequest(
        text: String,
        previousInteractionId: String? = null
    ): InteractionRequest {
        val input = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        }
        
        return InteractionRequest(
            model = "models/$MODEL",
            input = input,
            previousInteractionId = previousInteractionId,
            systemInstruction = if (previousInteractionId == null) systemInstruction else null,
            generationConfig = InteractionGenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1024
            ),
            responseModalities = listOf("text"),
            stream = false,
            tools = TruckingTools.declaration
        )
    }

    private fun buildInteractionRequest(
        audioBase64: String,
        previousInteractionId: String? = null,
        stream: Boolean = false
    ): InteractionRequest {
        val input = buildJsonArray {
            add(buildJsonObject {
                put("type", "audio")
                put("mime_type", AUDIO_MIME_TYPE)
                put("data", audioBase64)
            })
        }

        return InteractionRequest(
            model = "models/$MODEL",
            input = input,
            previousInteractionId = previousInteractionId,
            systemInstruction = if (previousInteractionId == null) systemInstruction else null,
            generationConfig = InteractionGenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1024
            ),
            responseModalities = listOf("text"),
            stream = stream,
            tools = TruckingTools.declaration
        )
    }

    private fun buildFunctionResultRequest(
        functionResults: List<FunctionResult>,
        previousInteractionId: String
    ): InteractionRequest {
        if (functionResults.isEmpty()) {
            Log.w("GeminiRest", "Attempted to build function result request with empty results")
        }

        val input = buildJsonArray {
            functionResults.forEach { result ->
                add(buildJsonObject {
                    put("type", "function_result")
                    put("name", result.name)
                    put("call_id", result.callId)
                    put("result", result.result)
                })
            }
        }
        
        return InteractionRequest(
            model = "models/$MODEL",
            input = input,
            previousInteractionId = previousInteractionId,
            systemInstruction = null,
            generationConfig = InteractionGenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1024
            ),
            responseModalities = listOf("text"),
            stream = false,
            tools = TruckingTools.declaration
        )
    }
    
    /**
     * Builds the tool declarations for all 15 trucking tools as JsonElements.
     * Interactions API expects tools as an array of function objects with type: "function".
     */
    private fun buildToolsJson(): List<JsonElement> {
        return emptyList()
    }
    
    /**
     * Executes the HTTP request to the Interactions API.
     */
    private suspend fun executeRequest(
        request: InteractionRequest,
        apiKey: String
    ): GeminiResponse {
        val requestBody = json.encodeToString(request)
        Log.d("GeminiRest", "Request Body: $requestBody")
        
        val httpRequest = Request.Builder()
            .url("$BASE_URL/interactions")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                val errorMsg = "API error: ${response.code} - $responseBody"
                Log.e("GeminiRest", errorMsg)
                return GeminiResponse.Error(errorMsg)
            }
            
            parseResponse(responseBody)
        } catch (e: Exception) {
            Log.e("GeminiRest", "Request failed", e)
            GeminiResponse.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun executeStreamingRequest(
        request: InteractionRequest,
        apiKey: String,
        onDelta: (String) -> Unit
    ): GeminiResponse {
        val requestBody = json.encodeToString(request)
        Log.d("GeminiRest", "Streaming Request Body: $requestBody")

        val httpRequest = Request.Builder()
            .url("$BASE_URL/interactions")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(httpRequest).execute()
            if (!response.isSuccessful || response.body == null) {
                val responseBody = response.body?.string()
                val errorMsg = "API error: ${response.code} - $responseBody"
                Log.e("GeminiRest", errorMsg)
                return GeminiResponse.Error(errorMsg)
            }

            response.body!!.charStream().buffered().use { reader ->
                var currentEvent: String? = null
                val currentData = StringBuilder()
                var finalResponse: GeminiResponse? = null
                var interactionId: String? = null
                val accumulatedOutputs = mutableListOf<OutputContent>()

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) {
                        if (currentEvent != null) {
                            val result = parseStreamEvent(
                                eventType = currentEvent,
                                eventData = currentData.toString().trim(),
                                onDelta = onDelta,
                                accumulatedOutputs = accumulatedOutputs,
                                onInteractionId = { id -> interactionId = id }
                            )
                            currentEvent = null
                            currentData.clear()
                            if (result != null) {
                                finalResponse = result
                            }
                        }
                        continue
                    }

                    if (line.startsWith("event:")) {
                        currentEvent = line.removePrefix("event:").trim()
                    } else if (line.startsWith("data:")) {
                        currentData.append(line.removePrefix("data:").trim())
                    }
                }

                // Process any remaining event
                if (finalResponse == null && currentEvent != null && currentData.isNotBlank()) {
                    val result = parseStreamEvent(
                        eventType = currentEvent,
                        eventData = currentData.toString().trim(),
                        onDelta = onDelta,
                        accumulatedOutputs = accumulatedOutputs,
                        onInteractionId = { id -> interactionId = id }
                    )
                    if (result != null) {
                        finalResponse = result
                    }
                }

                // If we have accumulated outputs but no final response, build one
                if (finalResponse == null && interactionId != null) {
                    finalResponse = buildResponseFromOutputs(interactionId!!, accumulatedOutputs)
                }

                finalResponse ?: GeminiResponse.Error("Stream ended without completion")
            }
        } catch (e: Exception) {
            Log.e("GeminiRest", "Streaming request failed", e)
            GeminiResponse.Error(e.message ?: "Unknown streaming error")
        }
    }

    private fun parseStreamEvent(
        eventType: String,
        eventData: String,
        onDelta: (String) -> Unit,
        accumulatedOutputs: MutableList<OutputContent>,
        onInteractionId: (String) -> Unit
    ): GeminiResponse? {
        return try {
            when (eventType) {
                "interaction.start" -> {
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val interactionJson = eventJson?.get("interaction") as? JsonObject
                    val id = interactionJson?.get("id")?.jsonPrimitive?.contentOrNull
                    if (id != null) {
                        onInteractionId(id)
                        Log.d("GeminiRest", "Interaction started: $id")
                    }
                    null
                }
                
                "content.start" -> {
                    // content.start signals the start of a content block
                    // The actual content data comes via content.delta events
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val contentJson = eventJson?.get("content") as? JsonObject
                    val type = contentJson?.get("type")?.jsonPrimitive?.contentOrNull
                    Log.d("GeminiRest", "Content start: type=$type")
                    null
                }
                
                "content.delta" -> {
                    // content.delta contains the actual content data
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val deltaJson = eventJson?.get("delta") as? JsonObject
                    val deltaType = deltaJson?.get("type")?.jsonPrimitive?.contentOrNull
                    
                    when (deltaType) {
                        "text" -> {
                            val text = deltaJson?.get("text")?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrBlank()) {
                                onDelta(text)
                            }
                        }
                        "function_call" -> {
                            // Function call data comes in the delta
                            val name = deltaJson?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                            val id = deltaJson?.get("id")?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
                            val arguments = deltaJson?.get("arguments")
                            
                            Log.d("GeminiRest", "Function call delta: name=$name, id=$id")
                            
                            val output = OutputContent(
                                type = "function_call",
                                id = id,
                                name = name,
                                arguments = arguments
                            )
                            accumulatedOutputs.add(output)
                        }
                    }
                    null
                }
                
                "interaction.status_update" -> {
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val status = eventJson?.get("status")?.jsonPrimitive?.contentOrNull
                    val interactionId = eventJson?.get("interaction_id")?.jsonPrimitive?.contentOrNull
                    Log.d("GeminiRest", "Status update: status=$status, id=$interactionId")
                    
                    if (interactionId != null) {
                        onInteractionId(interactionId)
                    }
                    
                    if (status == "requires_action" && interactionId != null) {
                        return buildResponseFromOutputs(interactionId, accumulatedOutputs)
                    }
                    null
                }
                
                "interaction.complete" -> {
                    // In streaming mode, interaction.complete has empty outputs
                    // Use accumulated outputs from content.start/content.delta events
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val interactionJson = eventJson?.get("interaction") as? JsonObject
                    
                    if (interactionJson == null) {
                        Log.e("GeminiRest", "interaction.complete missing interaction object")
                        return GeminiResponse.Error("Invalid interaction.complete: missing interaction")
                    }
                    
                    val id = interactionJson["id"]?.jsonPrimitive?.contentOrNull
                    val status = interactionJson["status"]?.jsonPrimitive?.contentOrNull

                    if (id == null) {
                        Log.e("GeminiRest", "interaction.complete missing id")
                        return GeminiResponse.Error("Invalid interaction.complete: missing id")
                    }
                    
                    onInteractionId(id)
                    Log.d("GeminiRest", "Interaction complete: id=$id, status=$status, outputs=${accumulatedOutputs.size}")

                    // Build response from accumulated outputs
                    return buildResponseFromOutputs(id, accumulatedOutputs)
                }
                
                "error" -> {
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val errorJson = eventJson?.get("error") as? JsonObject
                    val message = errorJson?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    Log.e("GeminiRest", "Error event: $message")
                    GeminiResponse.Error(message)
                }
                
                else -> {
                    Log.d("GeminiRest", "Ignored stream event: $eventType")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiRest", "Failed to parse stream event: $eventType, data: $eventData", e)
            if (eventType == "interaction.complete" || eventType == "error") {
                GeminiResponse.Error("Failed to parse $eventType event: ${e.message}")
            } else {
                null
            }
        }
    }
    
    private fun buildResponseFromOutputs(
        interactionId: String,
        outputs: List<OutputContent>
    ): GeminiResponse {
        val functionCalls = outputs.filter { it.type == "function_call" }
        val textOutputs = outputs.filter { it.type == "text" }
        
        return if (functionCalls.isNotEmpty()) {
            val calls = functionCalls.map { output ->
                val name = output.name ?: ""
                val id = output.id ?: UUID.randomUUID().toString()
                val args = output.arguments?.jsonObject?.mapValues { it.value }
                
                onToolCallStarted(name)
                
                // Execute tool immediately
                val result = TruckingTools.handleToolCall(name, args)
                
                FunctionCallData(
                    id = id,
                    name = name,
                    args = output.arguments,
                    result = result
                )
            }
            GeminiResponse.NeedsFunctionCall(calls, interactionId)
        } else {
            val text = textOutputs.mapNotNull { it.text }.joinToString("")
            GeminiResponse.Text(text, interactionId)
        }
    }

    /**
     * Parses the Interactions API response.
     */
    private fun parseResponse(responseBody: String): GeminiResponse {
        return try {
            val response = json.decodeFromString<InteractionResponse>(responseBody)
            parseResponse(response)
        } catch (e: Exception) {
            Log.e("GeminiRest", "Failed to parse response", e)
            GeminiResponse.Error("Failed to parse response: ${e.message}")
        }
    }

    private fun parseResponse(response: InteractionResponse): GeminiResponse {
        return try {
            val outputs = response.outputs.orEmpty()
            when (response.status) {
                "completed" -> {
                    val text = outputs
                        .filter { it.type == "text" }
                        .mapNotNull { it.text }
                        .joinToString("")
                    GeminiResponse.Text(text, response.id)
                }
                
                "requires_action" -> {
                    val functionCalls = outputs
                        .filter { it.type == "function_call" }
                        .map { output ->
                            val name = output.name ?: ""
                            val id = output.id ?: UUID.randomUUID().toString()
                            val args = output.arguments?.jsonObject?.mapValues { it.value }
                            
                            onToolCallStarted(name)
                            
                            // Execute tool immediately
                            val result = TruckingTools.handleToolCall(name, args)
                            
                            FunctionCallData(
                                id = id,
                                name = name,
                                args = output.arguments,
                                result = result
                            )
                        }
                    
                    GeminiResponse.NeedsFunctionCall(functionCalls, response.id)
                }
                
                "failed" -> {
                    GeminiResponse.Error(response.error?.message ?: "Unknown error")
                }
                
                "cancelled" -> {
                    GeminiResponse.Error("Interaction cancelled")
                }
                
                else -> {
                    GeminiResponse.Error("Unexpected status: ${response.status}")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiRest", "Failed to parse response", e)
            GeminiResponse.Error("Failed to parse response: ${e.message}")
        }
    }
}

/**
 * Sealed class representing different response types from the Gemini API.
 */
sealed class GeminiResponse {
    /**
     * Text response from the model.
     * @param text The generated text
     * @param interactionId The interaction ID for subsequent requests
     */
    data class Text(val text: String, val interactionId: String) : GeminiResponse()
    
    /**
     * Response indicating function calls are needed.
     * @param calls List of function calls to execute
     * @param interactionId The interaction ID for sending function results
     */
    data class NeedsFunctionCall(val calls: List<FunctionCallData>, val interactionId: String) : GeminiResponse()
    
    /**
     * Error response.
     * @param message Error message
     */
    data class Error(val message: String) : GeminiResponse()
}

/**
 * Data class representing a function call from the model.
 */
data class FunctionCallData(
    val id: String,
    val name: String,
    val args: JsonElement? = null,
    val result: JsonElement
)
