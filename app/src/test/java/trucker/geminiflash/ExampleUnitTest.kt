package trucker.geminiflash

import org.junit.Test

import org.junit.Assert.*
import trucker.geminiflash.network.VertexAiClient

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun vertexModelPolicy_allowsGeminiModels() {
        assertTrue(VertexAiClient.isAllowedVertexModel("gemini-2.5-flash"))
        assertTrue(VertexAiClient.isAllowedVertexModel("Gemini-2.0-flash"))
    }

    @Test
    fun vertexModelPolicy_rejectsNonGeminiModels() {
        assertFalse(VertexAiClient.isAllowedVertexModel("gpt-4o"))
        assertFalse(VertexAiClient.isAllowedVertexModel("custom-model"))
    }

    @Test
    fun vertexNetworkPolicy_rejectsInvalidLocation() {
        assertThrows(IllegalArgumentException::class.java) {
            VertexAiClient.validateVertexOnlyNetworkPolicy(
                projectId = "my-project",
                location = "invalid-region",
                model = "gemini-2.5-flash"
            )
        }
    }

    @Test
    fun vertexNetworkPolicy_rejectsBlankProject() {
        assertThrows(IllegalArgumentException::class.java) {
            VertexAiClient.validateVertexOnlyNetworkPolicy(
                projectId = " ",
                location = "global",
                model = "gemini-2.5-flash"
            )
        }
    }
}
