package trucker.GeminiLive

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import trucker.geminilive.network.GeminiRestClient
import trucker.geminilive.network.GeminiResponse
import trucker.geminilive.tools.TruckingTools

/**
 * Integration test for Gemini API with trucking tools.
 * Tests the HOS (Hours of Service) functionality.
 */
class GeminiIntegrationTest {

    private val client = GeminiRestClient {}

    private val apiKey: String by lazy {
        val props = java.util.Properties()
        val file = java.io.File("../secrets.properties")
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
            props.getProperty("GEMINI_API_KEY", "")
        } else {
            System.getenv("GEMINI_API_KEY") ?: "your-api-key-here"
        }
    }

    @Test
    fun testGetHoursOfServiceClocks() = runBlocking {
        // Skip test if no API key is provided
        if (apiKey == "your-api-key-here") {
            println("Skipping test: GEMINI_API_KEY environment variable not set")
            return@runBlocking
        }

        val testInput = "What's my HOS status?"

        val response = client.createTextInteraction(
            textInput = testInput,
            apiKey = apiKey
        )

        when (response) {
            is GeminiResponse.Text -> {
                println("Response: ${response.text}")
                // The model might respond directly or call the tool
                assertTrue("Response should not be empty", response.text.isNotBlank())
            }
            is GeminiResponse.NeedsFunctionCall -> {
                println("Function calls needed: ${response.calls.size}")
                assertTrue("Should have at least one function call", response.calls.isNotEmpty())

                // Check if getHoursOfServiceClocks was called
                val hosCall = response.calls.find { it.name == "getHoursOfServiceClocks" }
                assertNotNull("getHoursOfServiceClocks should be called", hosCall!!)

                // Simulate tool execution
                val toolResult = TruckingTools.handleToolCall(hosCall.name, hosCall.args?.let { 
                    // Convert JsonElement to Map if needed
                    if (it is kotlinx.serialization.json.JsonObject) it.toMap() else emptyMap()
                })
                println("Tool result: $toolResult")

                // Send function result back
                val finalResponse = client.sendFunctionResults(
                    functionResults = listOf(
                        trucker.geminilive.network.FunctionResult(
                            callId = hosCall.id,
                            name = hosCall.name,
                            result = toolResult
                        )
                    ),
                    apiKey = apiKey,
                    previousInteractionId = response.interactionId
                )

                when (finalResponse) {
                    is GeminiResponse.Text -> {
                        println("Final response: ${finalResponse.text}")
                        assertTrue("Final response should not be empty", finalResponse.text.isNotBlank())
                        assertTrue("Should mention HOS or hours", finalResponse.text.contains("hour", ignoreCase = true) ||
                                finalResponse.text.contains("HOS", ignoreCase = true))
                    }
                    is GeminiResponse.Error -> {
                        fail("Should not get error: ${finalResponse.message}")
                    }
                    else -> {
                        fail("Unexpected response type: $finalResponse")
                    }
                }
            }
            is GeminiResponse.Error -> {
                fail("API call failed: ${response.message}")
            }
        }
    }

    @Test
    fun testToolExecutionDirectly() {
        val result = TruckingTools.handleToolCall("getHoursOfServiceClocks", null)
        println("Direct tool execution result: $result")

        assertNotNull("Tool should return a result", result)
        assertTrue("Result should contain driver_id", result.toString().contains("driver_id"))
        assertTrue("Result should contain clocks", result.toString().contains("clocks"))
        assertTrue("Result should contain drive_time_remaining", result.toString().contains("drive_time_remaining"))
    }
}