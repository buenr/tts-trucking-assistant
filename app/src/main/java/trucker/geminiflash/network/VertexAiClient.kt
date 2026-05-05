package trucker.geminiflash.network

import android.content.Context
import android.util.Log
import com.google.genai.*
import com.google.genai.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import trucker.geminiflash.BuildConfig
import trucker.geminiflash.tools.TruckingTools
import java.util.UUID

/**
 * Vertex AI client using the official Gen AI SDK.
 * Replaces the REST-based Gemini Interactions API client.
 */
class VertexAiClient(private val context: Context) {

    private var client: Client? = null

    private suspend fun getClient(): Client {
        if (client == null) {
            // Load credentials and project ID from VertexCredentialsManager
            val projectId = VertexCredentialsManager.getProjectId(context)
            val credentials = VertexCredentialsManager.getCredentials(context)

            client = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(BuildConfig.VERTEX_AI_LOCATION)
                .credentials(credentials)
                .build()

            Log.d(TAG, "Vertex AI client initialized for project: $projectId")
        }
        return client!!
    }

    /**
     * Send a message to Vertex AI with conversation history (non-streaming).
     */
    suspend fun sendMessage(
        textInput: String,
        history: List<Content> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val client = getClient()
            val contents = buildContents(history, textInput)

            val response = client.models.generateContent(
                BuildConfig.VERTEX_AI_MODEL,
                contents,
                buildConfig()
            )

            parseResponse(response)
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error sending message: ${e.javaClass.simpleName}: ${e.message}", e)
            GeminiResponse.Error("${e.javaClass.simpleName}: ${e.message ?: "no details"} | cause: ${e.cause?.message ?: "none"}")
        }
    }

    /**
     * Send a message with streaming response.
     */
    suspend fun sendMessageStream(
        textInput: String,
        history: List<Content> = emptyList(),
        onDelta: (String) -> Unit
    ): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val client = getClient()
            val contents = buildContents(history, textInput)

            val accumulatedText = StringBuilder()
            val functionCalls = mutableListOf<FunctionCallData>()

            val responseStream = client.models.generateContentStream(BuildConfig.VERTEX_AI_MODEL, contents, buildConfig())
            for (chunk in responseStream) {
                val text = chunk.text()
                if (!text.isNullOrBlank()) {
                    val sanitized = GeminiResponse.sanitizeTextForTts(text)
                    accumulatedText.append(sanitized)
                    onDelta(sanitized)
                }

                // Check for function calls in the chunk
                chunk.functionCalls()?.forEach { funcCall ->
                    functionCalls.add(FunctionCallData(
                        id = funcCall.id().orElse(UUID.randomUUID().toString()),
                        name = funcCall.name().orElse(""),
                        args = funcCall.args().orElse(null)?.let { argsMap ->
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

            if (functionCalls.isNotEmpty()) {
                GeminiResponse.NeedsFunctionCall(functionCalls, "")
            } else {
                GeminiResponse.Text(accumulatedText.toString(), "")
            }
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error streaming message: ${e.javaClass.simpleName}: ${e.message}", e)
            GeminiResponse.Error("${e.javaClass.simpleName}: ${e.message ?: "no details"} | cause: ${e.cause?.message ?: "none"}")
        }
    }

    /**
     * Send function results back to the model.
     */
    suspend fun sendFunctionResults(
        functionResults: List<FunctionResult>,
        history: List<Content> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val client = getClient()

            // Build contents with function results as tool responses
            val contents = buildList {
                addAll(history)
                val parts = functionResults.map { result ->
                    Part.builder().functionResponse(
                        FunctionResponse.builder()
                            .id(result.callId)
                            .name(result.name)
                            .response(mapOf("output" to result.result.toString()))
                            .build()
                    ).build()
                }
                add(Content.fromParts(*parts.toTypedArray()))
            }

            val response = client.models.generateContent(
                VertexAuth.MODEL,
                contents,
                buildConfig()
            )

            parseResponse(response)
        } catch (e: Exception) {
            Log.e("VertexAiClient", "Error sending function results: ${e.javaClass.simpleName}: ${e.message}", e)
            GeminiResponse.Error("${e.javaClass.simpleName}: ${e.message ?: "no details"} | cause: ${e.cause?.message ?: "none"}")
        }
    }

    private fun buildContents(history: List<Content>, newText: String): List<Content> {
        return buildList {
            // System instruction as first content with role "user" and "model" ack
            // (Gen AI SDK handles system instructions differently, we'll include it in config)

            addAll(history)
            add(Content.fromParts(Part.fromText(newText)))
        }
    }

    private fun buildConfig(): GenerateContentConfig {
        val tools = TruckingTools.getVertexTools()

        return GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
            .temperature(0.2f)
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
                    id = funcCall.id().orElse(UUID.randomUUID().toString()),
                    name = funcCall.name().orElse(""),
                    args = funcCall.args().orElse(null)?.let { argsMap ->
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
        private const val TAG = "VertexAiClient"
        private val SYSTEM_INSTRUCTION = """
            # PERSONA
            You are a Knight-Swift Transportation trucking in-cab copilot (AI Assistant) on their tablet. Your responses will be spoken aloud via text-to-speech (TTS) to the driver. Directly address the driver's question, don't add irrelevant information from the tool call result. Use concise, operational language familiar to truck drivers.

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
            - getDriverDashboard: Driver profile, HOS, safety score, MPG performance, medical card reminders, and home-time countdown
            - getTruckInfo: Truck and trailer equipment details including tractor/trailer numbers, ELD provider, DEF/fuel levels, tire tread, fault codes, and service milestones
            - getLoadInformation: Load details based on type (current/next) with BOL numbers, stops, ETAs, and route risks
            - getFinancials: Financial information by period (current/ytd/bonus) including pay, accessorials, and safety bonus details
            - getRouteConditions: Route planning with traffic/weather conditions and recommended fuel stops with amenities
            - getCommunications: Communication data (messages/contacts) including dispatch inbox and support department phone numbers
            - getCompanyResources: Company information by category (policies/mentor/ownerOperator/training) including FAQs and program details
            - getComplianceStatus: Compliance-focused data including HOS alerts, medical card status, DVIR requirements, and inspection scheduling
            - closeApp: Close the application when driver requests it

            # REASONING STEPS
            1. Analyze the driver's request to identify the primary intent (e.g., pay, load, safety, compliance).
            2. Map the intent to the most relevant tool from the list below.
            3. If the request is ambiguous, ask a very short clarifying question (e.g., "Do you mean your current load or your next load?").
            4. If a tool call is required, identify the necessary parameters (like `loadType` or `period`) from the context.
            5. After receiving tool data, extract ONLY the specific value requested by the driver.
            6. Convert that value into a natural-sounding, TTS-optimized sentence.

            ## Tool Selection Guide & Examples
            Choose the SINGLE best tool based on driver intent. Use these specific mappings:

            ### 1. getDriverDashboard (Driver Profile & Performance)
            - Use for: Personal profile, tenure, fleet, current location/corridor, safety score (with recent events), MPG performance (with peer comparison), idle time, cruise usage, fuel savings, home-time countdown (days remaining), referral bonus, monthly miles, and bonus progress.
            - NOT for: HOS details, medical card status, DVIR status - use getComplianceStatus for those.
            - Examples: "Where am I?", "How's my safety score?", "What is my MPG?", "Who am I?", "How many years have I been here?", "What fleet am I in?", "What is my home terminal?", "What's my idle time?", "Am I using cruise control enough?", "Tell me about my recent hard braking event.", "How many days until I go home?", "What's my referral bonus status?", "How many miles have I driven this month?", "What corridor am I on?".

            ### 2. getTruckInfo (Equipment)
            - Use for: Tractor number, trailer number, trailer type, ELD provider, DEF level, fuel percentage, tire tread status, active fault codes, and service milestones.
            - Examples: "What's my truck number?", "What's my trailer number?", "What's my trailer type?", "What's my ELD provider?", "What's my DEF level?", "How much fuel is in the tank?", "Do I have any fault codes?", "When is my next service?", "How's my tire tread?".

            ### 3. getLoadInformation (Load & ETA)
            - Use for: Pickup/delivery times, customer info (name/phone/reference), BOL numbers, route risks, next load details, preload availability, facility insights (parking, bathroom, detention time, entry instructions, scales), load type (live vs drop-hook), priority, and total miles.
            - Parameter `loadType`: "current" (default) for active load, "next" for upcoming/pre-dispatch.
            - Examples: "Where is my load?", "When do I deliver?", "What is my BOL number?", "What's my next load?", "Am I on time?", "Who is the customer?", "What's the reference number?", "Is my next load a drop and hook?", "Is there a preload available?", "Does the receiver allow overnight parking?", "What are the entry instructions?", "What's the average detention time at this customer?", "Is there a scale on site?", "What's the customer's phone number?", "What's the total mileage for my next load?", "What's the delivery window for my next trip?".

            ### 4. getFinancials (Pay & Bonus)
            - Use for: Recent paychecks (net pay, pay date, pay period), base pay/CPM rates, layover/detention/accessorial pay, deductions (insurance), YTD gross/net, safety bonus eligibility, quarterly bonus projections, and required safety classes.
            - Parameter `period`: "current" for last check, "ytd" for yearly, "bonus" for safety bonus details.
            - Examples: "How much did I get paid?", "What's my YTD gross?", "Did I get my safety bonus?", "What's my current CPM?", "How much layover pay did I get?", "Am I eligible for my quarterly bonus?", "What safety class do I need for my bonus?", "What was my net pay on my last check?", "What's my YTD net?", "How much was my insurance deduction?", "What was the date of my last paycheck?", "What's the current pay period?".

            ### 5. getRouteConditions (Real-Time Road & Fuel)
            - Use for: Real-time weather/traffic conditions for the immediate route (next 1 hour), recommended fuel stops (brand/location/distance), fuel discounts, stop amenities (DEF, scales, showers, restaurants), and corridor fuel restrictions.
            - NOT for: Load-specific route risks tied to a delivery - use getLoadInformation for those.
            - Examples: "What's the weather ahead?", "Any traffic?", "Where should I fuel up?", "Are there any high winds?", "Does the next fuel stop have DEF at the pump?", "Are there showers at the Pilot?", "Any fuel restrictions I should know about?", "What amenities are at the next stop?".

            ### 6. getCommunications (Dispatch & Support)
            - Use for: Dispatch messages (gate codes, instructions, subject/body), unread message filtering, and contact info for support departments (payroll, breakdown, driver leader).
            - Parameter `type`: "messages" for inbox, "contacts" for phone numbers.
            - Examples: "Any messages from dispatch?", "What's the number for payroll?", "How do I reach breakdown?", "Who is my driver leader?", "What's the gate code for the customer?", "Read my unread messages.", "Who is my fleet leader?".

            ### 7. getCompanyResources (Policies & Training)
            - Use for: Company rules (pets, riders, breakdown SOP), terminal info (parking capacity, amenities, shop status), mentor program, owner operator/lease programs, and training modules/videos (progress, links, deadlines).
            - Parameter `category`: "policies" (FAQs), "mentor", "ownerOperator" (lease), "training".
            - Examples: "What is the pet policy?", "Can I have a rider?", "Tell me about the mentor program.", "How do I become an owner operator?", "Do I have any training modules?", "Are there any safety videos I need to watch?", "Is the shop open at the terminal?", "How much parking is left at the terminal?", "What's the breakdown protocol?".

            ### 8. getComplianceStatus (HOS, Medical, & Compliance)
            - Use for: Hours of Service remaining (drive/duty/cycle), 30-min break clock, 7-day HOS recap, HOS alerts (violations/warnings), medical card status (expiry, renewal window, preferred clinics), annual inspection status, and DVIR submission status. This is the authoritative source for all regulatory compliance data.
            - Examples: "How much drive time left?", "When is my next break?", "Tell me about my HOS recap for next week.", "When does my medical card expire?", "Where can I get my DOT physical?", "Is my DVIR submitted?", "When is my tractor inspection due?", "Do I have any HOS alerts?", "When can I renew my medical card?", "Do I need a DOT physical?".

            ### 9. closeApp (Exit)
            - Use for: Closing the application.
            - Examples: "Close the app.", "Goodbye.", "I'm done.", "Quit.".

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
