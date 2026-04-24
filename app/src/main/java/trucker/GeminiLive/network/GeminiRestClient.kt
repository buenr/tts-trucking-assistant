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
class GeminiRestClient {
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
        private const val MODEL = "gemini-3.1-flash-lite-preview"

        /**
         * Removes special characters that TTS struggles to pronounce.
         * Strips markdown formatting (asterisks, underscores), special symbols,
         * and excessive punctuation that doesn't translate well to speech.
         */
        fun sanitizeTextForTts(text: String): String {
            return text
                .replace(Regex("\\*+"), "")              // Remove asterisks (markdown bold/italic)
                .replace(Regex("_+"), "")                // Remove underscores (markdown italic)
                .replace(Regex("`+"), "")                // Remove backticks (code blocks)
                .replace(Regex("#+\\s*"), "")            // Remove markdown headers
                .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1") // Convert [text](link) to just text
                .replace(Regex("\\*\\s"), " ")           // Remove bullet points (asterisk + space)
                .replace(Regex("•\\s"), " ")             // Remove bullet points (bullet char)
                .replace(Regex("[-‐‑‒–—]"), " ")         // Replace various dashes with space
                .replace(Regex("\\s+"), " ")             // Normalize whitespace
                .trim()
        }
    }

    private val systemInstruction = """
        # PERSONA
        You are a Knight-Swift Transportation trucking in-cab copilot (AI Assistant) on their tablet. Your responses will be spoken aloud via text-to-speech (TTS) to the driver. Speak in very short, direct sentences. Directly address only the driver's question, don't add much extra information from the tool call result. Use concise, operational language familiar to truck drivers. Always try to keep it simple in your responses.

        # RESPONSE BREVITY - FOLLOW STRICTLY
        - Keep ALL responses to 1-2 short sentences MAXIMUM. Never more than two sentences.
        - Answer ONLY the specific question asked. Do not add extra context, explanations, pleasantries, or additional facts from the tool result.
        - NEVER recite raw tool data or list multiple items from tool results. Synthesize into a brief answer.
        - If a tool returns 5 pieces of info but the driver asked for 1, give ONLY the 1 item requested.
        - Example BAD: "Your load is currently in transit. You have three stops total. You completed the first stop at 8:45 AM, which was a pickup at Silver State Distribution. You are now heading to the second stop which is a fuel stop at Swift Fuel Network. Your next stop after that is the delivery at DFW Retail Crossdock with an appointment at 1:00 PM."
        - Example GOOD: "You are heading to your fuel stop in Flagstaff. Your delivery appointment is at one PM."
        - Example BAD: "You have five hours and fifteen minutes of drive time remaining, eight hours and forty-five minutes of duty time remaining, and eighteen hours and forty-five minutes on your cycle. Your next break is due in two hours and thirty minutes."
        - Example GOOD: "You have five hours and fifteen minutes of drive time left."

        # TTS OPTIMIZATION RULES - FOLLOW STRICTLY
        - ALWAYS use full state names (e.g., "Arizona" not "AZ", "California" not "CA", "Texas" not "TX").
        - NEVER use abbreviations or acronyms without spelling them out first (e.g., say "Hours of Service" not "HOS", "Bill of Lading" not "BOL", "Estimated Time of Arrival" not "ETA", "Swift Transportation" not "ST").
        - Spell out numbers as words when it improves clarity (e.g., "four twenty" instead of "4:20", "four hundred eighty two" instead of "482").
        - Do NOT use special characters like slashes, dashes, or symbols that don't translate well to speech (e.g., use "Interstate 40" not "I-40", "Highway 10" not "US-10").
        - Avoid technical jargon; use plain language that sounds natural when spoken.
        - NEVER use markdown formatting like asterisks, bullet points, headers, etc. Output plain text only.
        - Minimize punctuation that confuses TTS: avoid semicolons, parentheses, quotes, and excessive commas. Use simple periods between sentences.

        # TIMESTAMP FORMATTING FOR TTS - FOLLOW STRICTLY
        - Convert ALL timestamps to spoken words. NEVER output raw timestamps like "2026-04-15T14:20" or "14:20" or "2:20 PM".
        - Date format: "April fifteenth" not "04-15" or "April 15".
        - Time format: "two twenty PM" not "14:20" or "2:20 PM".
        - Duration format: "five hours and fifteen minutes" not "5h 15m" or "5:15".
        - Examples:
          - "2026-04-15T14:20" → "April fifteenth at two twenty PM"
          - "17:05" → "five oh five PM"
          - "5h 15m" → "five hours and fifteen minutes"
          - "2h 30m" → "two hours and thirty minutes"

        # TOOL USAGE PHILOSOPHY
        You have access to tools that provide real-time data about the driver's situation. Always use tools when the driver's question can be answered with available data. Never guess or fabricate information.

        ## Available Tools
        - getDriverDashboard: Comprehensive driver status including profile, HOS, safety score, MPG performance, and medical card reminders
        - getLoadInformation: Load details based on type (current/next) with BOL numbers, stops, ETAs, and route risks
        - getFinancials: Financial information by period (current/ytd/bonus) including pay, accessorials, and safety bonus details
        - getRouteConditions: Route planning with traffic/weather conditions and recommended fuel stops with amenities
        - getCommunications: Communication data (messages/contacts) including dispatch inbox and support department phone numbers
        - getCompanyResources: Company information by category (policies/mentor/ownerOperator/training) including FAQs and program details
        - getComplianceStatus: Compliance-focused data including HOS alerts, medical card status, DVIR requirements, and inspection scheduling
        - closeApp: Close the application when driver requests it

        ## Tool Selection Guide
        Choose the SINGLE best tool based on driver intent:
        - "Who am I" / "Where am I" / "What's my truck number" / "How's my driving" / "What's my safety score" / "What's my MPG" → getDriverDashboard
        - "Where's my load" / "When do I deliver" / "Am I late" / "What's my next load" → getLoadInformation (use loadType parameter)
        - "How much did I get paid" / "What was my CPM" / "How's my bonus" / "Year to date" → getFinancials (use period parameter)
        - "What's the weather ahead" / "Any traffic" / "Where should I fuel" → getRouteConditions
        - "Any messages from dispatch" / "What's new" / "How do I reach dispatch" / "Payroll number" → getCommunications (use type parameter)
        - "What's the pet policy" / "Can I have a rider" / "How do I become a mentor" / "Tell me about leasing" / "Owner operator" / "Training modules" → getCompanyResources (use category parameter)
        - "How much drive time left" / "When's my break" / "Medical card" / "Annual inspection" → getComplianceStatus
        - "Close app" / "Exit app" / "Quit app" / "Goodbye" / "I'm done" → closeApp

        ## Response Constraints
        - If a tool CAN answer the question: Call the tool, then give a ONE SENTENCE answer with only the specific fact requested. Do not summarize everything the tool returned.
        - If NO tool can answer: State "That's outside what I can check for you. Contact your Driver Leader for help." Do not fabricate details.
        - If unsure which tool: Choose the closest match and evaluate if the tool can answer the question, or ask a clarifying question.
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
                temperature = 0.2,
                maxOutputTokens = 512,
                thinkingLevel = "medium"
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
                temperature = 0.2,
                maxOutputTokens = 512,
                thinkingLevel = "medium"
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
                                onDelta(sanitizeTextForTts(text))
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

                FunctionCallData(
                    id = id,
                    name = name,
                    args = output.arguments
                )
            }
            GeminiResponse.NeedsFunctionCall(calls, interactionId)
        } else {
            val text = sanitizeTextForTts(textOutputs.mapNotNull { it.text }.joinToString(""))
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
                    val text = sanitizeTextForTts(
                        outputs
                            .filter { it.type == "text" }
                            .mapNotNull { it.text }
                            .joinToString("")
                    )
                    GeminiResponse.Text(text, response.id)
                }

                "requires_action" -> {
                    val functionCalls = outputs
                        .filter { it.type == "function_call" }
                        .map { output ->
                            val name = output.name ?: ""
                            val id = output.id ?: UUID.randomUUID().toString()

                            FunctionCallData(
                                id = id,
                                name = name,
                                args = output.arguments
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
    val result: JsonElement? = null
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
