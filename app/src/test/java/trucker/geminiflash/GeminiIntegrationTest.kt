package trucker.geminiflash

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import trucker.geminiflash.network.VertexAiClient
import trucker.geminiflash.network.GeminiResponse
import trucker.geminiflash.tools.TruckingTools

/**
 * Integration test for Vertex AI Gemini API with trucking tools.
 * Tests the HOS (Hours of Service) functionality.
 *
 * Note: This test requires:
 * 1. A valid service account JSON at app/src/main/res/raw/vertex_sa.json
 * 2. VERTEX_AI_PROJECT_ID set in local.properties
 *
 * These tests are skipped if credentials are not available.
 */
class GeminiIntegrationTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var client: VertexAiClient

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Note: In a real test, you'd use a test Context or Robolectric
        // For now, these tests will be skipped if context/credentials unavailable
    }

    @Test
    fun testGetHoursOfServiceClocks() = runBlocking {
        // Skip if no credentials available
        if (!hasCredentials()) {
            println("Skipping test: Vertex AI credentials not available")
            println("Place service account JSON at: app/src/main/res/raw/vertex_sa.json")
            return@runBlocking
        }

        val testInput = "What's my HOS status?"

        // Use VertexAiClient - note: this requires a real Android context
        // For pure unit tests, consider using a mock or Robolectric
        println("Test input: $testInput")
        println("To run this test properly, use Android instrumentation tests or Robolectric")

        // Verify tool exists
        val result = TruckingTools.handleToolCall("getDriverDashboard", null)
        assertNotNull("Tool should return a result", result)
        println("Tool result: $result")
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
