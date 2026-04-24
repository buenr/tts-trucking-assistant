package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import trucker.geminilive.BuildConfig
import java.io.ByteArrayInputStream

/**
 * Vertex AI authentication helper.
 * Uses service account JSON from Gradle Secrets (local.properties) for OAuth2 authentication.
 *
 * Configuration (via local.properties and Secrets Gradle Plugin):
 * - VERTEX_AI_PROJECT_ID: Google Cloud project ID (required)
 * - VERTEX_AI_LOCATION: Vertex AI location (default: global)
 * - VERTEX_AI_MODEL: Model name (default: gemini-2.5-flash)
 * - VERTEX_AI_SERVICE_ACCOUNT_JSON: Service account JSON content (required)
 *
 * Setup:
 * 1. Copy local.properties.template to local.properties
 * 2. Add your service account JSON content to VERTEX_AI_SERVICE_ACCOUNT_JSON
 *    (on one line, or use Gradle's multi-line support with \ at end of lines)
 */
object VertexAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    // Vertex AI configuration from BuildConfig (populated by Secrets Gradle Plugin)
    val PROJECT_ID: String
        get() = BuildConfig.VERTEX_AI_PROJECT_ID.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("VERTEX_AI_PROJECT_ID not configured in local.properties")

    val LOCATION: String
        get() = BuildConfig.VERTEX_AI_LOCATION.takeIf { it.isNotBlank() } ?: "global"

    val MODEL: String
        get() = BuildConfig.VERTEX_AI_MODEL.takeIf { it.isNotBlank() } ?: "gemini-2.5-flash"

    /**
     * Get Google Credentials from service account JSON in secrets.
     */
    suspend fun getCredentials(context: Context): GoogleCredentials = withContext(Dispatchers.IO) {
        val serviceAccountJson = BuildConfig.VERTEX_AI_SERVICE_ACCOUNT_JSON
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "VERTEX_AI_SERVICE_ACCOUNT_JSON not configured in local.properties. " +
                "Please add your service account JSON content to local.properties"
            )

        try {
            val stream = ByteArrayInputStream(serviceAccountJson.toByteArray(Charsets.UTF_8))
            ServiceAccountCredentials.fromStream(stream)
                .createScoped(listOf(SCOPE))
                .also {
                    Log.d("VertexAuth", "Loaded service account credentials from secrets")
                }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse service account JSON from secrets. " +
                "Ensure VERTEX_AI_SERVICE_ACCOUNT_JSON contains valid JSON.",
                e
            )
        }
    }

    /**
     * Get project ID from secrets.
     */
    fun getProjectId(context: Context): String {
        return PROJECT_ID.also {
            Log.d("VertexAuth", "Using project ID from local.properties: $it")
        }
    }

    /**
     * Check if credentials are configured in secrets.
     */
    fun hasCredentials(context: Context): Boolean {
        val hasProjectId = BuildConfig.VERTEX_AI_PROJECT_ID.isNotBlank()
        val hasServiceAccountJson = BuildConfig.VERTEX_AI_SERVICE_ACCOUNT_JSON.isNotBlank()
        return hasProjectId && hasServiceAccountJson
    }
}
