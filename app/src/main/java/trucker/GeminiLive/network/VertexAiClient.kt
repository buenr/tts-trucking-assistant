package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.genai.*
import com.google.genai.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import trucker.geminilive.tools.TruckingTools
import java.util.UUID

/**
 * Vertex AI client using the official Gen AI SDK.
 * Replaces the REST-based Gemini Interactions API client.
 */
class VertexAiClient(private val context: Context) {

    private var client: Client? = null

    private suspend fun getClient(): Client {
        if (client == null) {
            val projectId = VertexAuth.getProjectId(context)
            val accessToken = VertexAuth.getAccessToken(context)

            client = Client.builder()
                .vertexAi(true)
                .project(projectId)
                .location(VertexAuth.LOCATION)
                .credentials(com.google.auth.oauth2.GoogleCredentials.create(
                    com.google.auth.oauth2.AccessToken(accessToken, null)
                ))
                .build()
        }
        return client!!
    }

    /**
     * Send a message to Vertex AI with conversation history (non-streaming).
     */
    suspend fun sendMessage(
        textInput: String,
        history: List<Content> = emptyList()
    ): GeminiResponse {
        return try {
            val client = getClient()
            val contents = buildContents(history, textInput)

            val response = client.models.generateContent(
                VertexAuth.MODEL,
                contents,
                buildConfig()
            )

            parseResponse(response)
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error sending message", e)
            GeminiResponse.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Send a message with streaming response.
     */
    suspend fun sendMessageStream(
        textInput: String,
        history: List<Content> = emptyList(),
        onDelta: (String) -> Unit
    ): GeminiResponse {
        return try {
            val client = getClient()
            val contents = buildContents(history, textInput)

            val accumulatedText = StringBuilder()
            val functionCalls = mutableListOf<FunctionCallData>()

            client.models.generateContentStream(VertexAuth.MODEL, contents, buildConfig())
                .collect { chunk ->
                    val text = chunk.text()
                    if (!text.isNullOrBlank()) {
                        val sanitized = GeminiResponse.sanitizeTextForTts(text)
                        accumulatedText.append(sanitized)
                        onDelta(sanitized)
                    }

                    // Check for function calls in the chunk
                    chunk.functionCalls()?.forEach { funcCall ->
                        functionCalls.add(FunctionCallData(
                            id = funcCall.id ?: UUID.randomUUID().toString(),
                            name = funcCall.name ?: "",
                            args = funcCall.args?.let { argsMap ->
                                val jsonObject = buildJsonObject {
                                    argsMap.forEach { (key, value) ->
                                        when (value) {
                                            is String -> put(key, value)
                                            is Number -> put(key, value)
                                            is Boolean -> put(key, value)
                                            else -> put(key, value.toString())
                                        }
                                    }
                                }
                                Json.encodeToJsonElement(jsonObject)
                            }
                        ))
                    }
                }

            return if (functionCalls.isNotEmpty()) {
                GeminiResponse.NeedsFunctionCall(functionCalls, "")
            } else {
                GeminiResponse.Text(accumulatedText.toString(), "")
            }
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error streaming message", e)
            GeminiResponse.Error(e.message ?: "Unknown streaming error")
        }
    }

    /**
     * Send function results back to the model.
     */
    suspend fun sendFunctionResults(
        functionResults: List<FunctionResult>,
        history: List<Content> = emptyList()
    ): GeminiResponse {
        return try {
            val client = getClient()

            // Build contents with function results as tool responses
            val contents = buildList {
                addAll(history)
                add(Content.fromParts(
                    functionResults.map { result ->
                        Part.fromFunctionResponse(
                            FunctionResponse.builder()
                                .id(result.callId)
                                .name(result.name)
                                .response(result.result.toString())
                                .build()
                        )
                    }
                ))
            }

            val response = client.models.generateContent(
                VertexAuth.MODEL,
                contents,
                buildConfig()
            )

            parseResponse(response)
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error sending function results", e)
            GeminiResponse.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildContents(history: List<Content>, newText: String): List<Content> {
        return buildList {
            // System instruction as first content with role "user" and "model" ack
            // (Gen AI SDK handles system instructions differently, we'll include it in config)

            addAll(history)
            add(Content.fromText(newText))
        }
    }

    private fun buildConfig(): GenerateContentConfig {
        val tools = TruckingTools.getVertexTools()

        return GenerateContentConfig.builder()
            .systemInstruction(SYSTEM_INSTRUCTION)
            .temperature(0.2)
            .maxOutputTokens(512)
            .tools(tools)
            .build()
    }

    private fun parseResponse(response: GenerateContentResponse): GeminiResponse {
        val text = response.text()
        val functionCalls = response.functionCalls()

        return if (!functionCalls.isNullOrEmpty()) {
            val calls = functionCalls.map { funcCall ->
                FunctionCallData(
                    id = funcCall.id ?: UUID.randomUUID().toString(),
                    name = funcCall.name ?: "",
                    args = funcCall.args?.let { argsMap ->
                        val jsonObject = buildJsonObject {
                            argsMap.forEach { (key, value) ->
                                when (value) {
                                    is String -> put(key, value)
                                    is Number -> put(key, value)
                                    is Boolean -> put(key, value)
                                    else -> put(key, value.toString())
                                }
                            }
                        }
                        Json.encodeToJsonElement(jsonObject)
                    }
                )
            }
            GeminiResponse.NeedsFunctionCall(calls, "")
        } else {
            GeminiResponse.Text(GeminiResponse.sanitizeTextForTts(text ?: ""), "")
        }
    }

    companion object {
        private const val SYSTEM_INSTRUCTION = """
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
    }
}

/**
 * Sealed class representing different response types from Vertex AI.
 */
sealed class GeminiResponse {
    data class Text(val text: String, val interactionId: String) : GeminiResponse()
    data class NeedsFunctionCall(val calls: List<FunctionCallData>, val interactionId: String) : GeminiResponse()
    data class Error(val message: String) : GeminiResponse()

    companion object {
        /**
         * Removes special characters that TTS struggles to pronounce.
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
}

data class FunctionCallData(
    val id: String,
    val name: String,
    val args: JsonElement? = null,
    val result: JsonElement? = null
)

data class FunctionResult(
    val callId: String,
    val name: String,
    val result: JsonElement
)

class NetworkFailureException(message: String, cause: Throwable? = null) : Exception(message, cause)
