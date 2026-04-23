package trucker.geminilive.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Interceptor
import trucker.geminilive.tools.TruckingTools
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * REST client for Gemini Interactions API.
 * Sends text-only payloads to minimize bandwidth over low-quality LTE.
 * Uses SSE streaming for low-latency token delivery.
 */
class GeminiRestClient(
    private val onToolCallStarted: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(ExponentialBackoffRetryInterceptor(maxRetries = 3))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "gemini-2.5-flash"
    }

    private val systemInstruction = """
        You are a Swift Transportation trucking in-cab copilot (AI Assistant) on their tablet. Speak in very short, direct sentences. Directly address only the driver's question, don't add much extra information from the tool call result. Use concise, operational language familiar to truck drivers. Provide accurate data-driven answers by calling tools whenever the driver's request overlaps with available functions. Never guess or fabricate information when a tool should provide the answer. Tool selection guidance: use getDriverProfile for profile/location/equipment snapshot requests; use getLoadStatus for active-load timeline, stop status, ETA, and load-specific risks; use getHoursOfServiceClocks for HOS clock and break timing; use getTrafficAndWeather for immediate (1 hour) road conditions, traffic, and weather ahead; use getDispatchInbox for dispatch messages to relay unread messages from dispatch; use getCompanyFAQs for general company policy/procedure FAQs ; use getPaycheckInfo for paycheck, settlement, CPM, gross/net, and miles-related compensation questions; use findNearestSwiftTerminal to check for nearby Swift yards and amenities; use checkSafetyScore to review driving telematics, harsh braking, and safety bonus standing; use getFuelNetworkRouting to find the next approved in-network fuel stop; use getContacts for contact details for dispatch, leaders, payroll, and support; use getNextLoadDetails for details on the next scheduled load, pickup/delivery windows, and pre-dispatch information; use getMentorFAQs for information on becoming a driver mentor, its benefits, and the application process; use getOwnerOperatorFAQs for information on the owner-operator program. If the driver asks for data or actions outside available tools/data, unmistakably state that the request is out-of-scope (DO NOT fabricate details) and route them to their Driver Leader for support.
    """.trimIndent()

    /**
     * Send a text message to the LLM (non-streaming).
     */
    suspend fun sendMessage(
        textInput: String,
        apiKey: String,
        previousInteractionId: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val request = buildTextInteractionRequest(
            text = textInput,
            previousInteractionId = previousInteractionId,
            stream = false
        )
        executeRequest(request, apiKey)
    }

    /**
     * Send a text message to the LLM with SSE streaming.
     * @param onDelta Callback invoked on incremental text deltas from Gemini
     */
    suspend fun sendMessageStream(
        textInput: String,
        apiKey: String,
        previousInteractionId: String? = null,
        onDelta: (String) -> Unit
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val request = buildTextInteractionRequest(
            text = textInput,
            previousInteractionId = previousInteractionId,
            stream = true
        )
        executeStreamingRequest(request, apiKey, onDelta)
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

    private fun buildTextInteractionRequest(
        text: String,
        previousInteractionId: String? = null,
        stream: Boolean = false
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
        } catch (e: IOException) {
            Log.e("GeminiRest", "Network failure", e)
            throw NetworkFailureException(e.message ?: "Network request failed", e)
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

                if (finalResponse == null && interactionId != null) {
                    finalResponse = buildResponseFromOutputs(interactionId!!, accumulatedOutputs)
                }

                finalResponse ?: GeminiResponse.Error("Stream ended without completion")
            }
        } catch (e: IOException) {
            Log.e("GeminiRest", "Streaming network failure", e)
            throw NetworkFailureException(e.message ?: "Streaming network request failed", e)
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
                    val eventJson = json.parseToJsonElement(eventData) as? JsonObject
                    val contentJson = eventJson?.get("content") as? JsonObject
                    val type = contentJson?.get("type")?.jsonPrimitive?.contentOrNull
                    Log.d("GeminiRest", "Content start: type=$type")
                    null
                }

                "content.delta" -> {
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
    data class Text(val text: String, val interactionId: String) : GeminiResponse()
    data class NeedsFunctionCall(val calls: List<FunctionCallData>, val interactionId: String) : GeminiResponse()
    data class Error(val message: String) : GeminiResponse()
}

data class FunctionCallData(
    val id: String,
    val name: String,
    val args: JsonElement? = null,
    val result: JsonElement
)

/**
 * OkHttp interceptor that retries requests with exponential backoff
 * on transient network failures (timeouts, 5xx, connection errors).
 */
class ExponentialBackoffRetryInterceptor(private val maxRetries: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response? = null
        var exception: IOException? = null

        while (attempt <= maxRetries) {
            try {
                response = chain.proceed(chain.request())
                if (response.isSuccessful || !isRetryable(response)) {
                    return response
                }
                // Retryable HTTP error (5xx, etc.)
                response.close()
            } catch (e: IOException) {
                exception = e
                response?.close()
            }

            attempt++
            if (attempt <= maxRetries) {
                val delayMs = (1000L * (1 shl (attempt - 1))).coerceAtMost(8000L)
                Log.d("GeminiRest", "Retry $attempt/$maxRetries after ${delayMs}ms")
                Thread.sleep(delayMs)
            }
        }

        throw exception ?: IOException("Request failed after $maxRetries retries")
    }

    private fun isRetryable(response: Response): Boolean {
        val code = response.code
        return code in 500..599 || code == 408 || code == 429
    }
}
