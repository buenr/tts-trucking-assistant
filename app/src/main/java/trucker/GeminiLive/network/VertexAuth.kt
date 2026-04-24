package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import trucker.geminilive.BuildConfig

/**
 * Vertex AI authentication helper.
 * Uses Application Default Credentials (ADC) for OAuth2 authentication.
 * Falls back to service account JSON from assets if ADC is not configured.
 *
 * Configuration (via local.properties and Secrets Gradle Plugin):
 * - VERTEX_AI_PROJECT_ID: Google Cloud project ID
 * - VERTEX_AI_LOCATION: Vertex AI location (default: global)
 * - VERTEX_AI_MODEL: Model name (default: gemini-2.5-flash)
 *
 * ADC Configuration:
 * - Set GOOGLE_APPLICATION_CREDENTIALS environment variable to point to service account JSON
 * - Or place vertex-ai-testing1.json in app/src/main/assets/
 */
object VertexAuth {
    private const val ASSET_NAME = "vertex-ai-testing1.json"
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
     * Get Google Credentials using Application Default Credentials (ADC).
     * Falls back to service account JSON from assets if ADC is not configured.
     */
    suspend fun getCredentials(context: Context): GoogleCredentials = withContext(Dispatchers.IO) {
        try {
            // Try Application Default Credentials first (environment variable)
            val adcCredentials = GoogleCredentials.getApplicationDefault()
                .createScoped(listOf(SCOPE))
            Log.d("VertexAuth", "Using Application Default Credentials (ADC)")
            adcCredentials
        } catch (e: Exception) {
            Log.w("VertexAuth", "ADC not configured, falling back to service account from assets: ${e.message}")
            // Fallback: Load from assets
            val stream = context.assets.open(ASSET_NAME)
            ServiceAccountCredentials.fromStream(stream)
                .createScoped(listOf(SCOPE))
        }
    }

    /**
     * Get project ID from BuildConfig or credentials.
     * Priority: BuildConfig > ADC > Service Account JSON
     */
    fun getProjectId(context: Context): String {
        // First check if project ID is configured in local.properties
        val configProjectId = BuildConfig.VERTEX_AI_PROJECT_ID.takeIf { it.isNotBlank() }
        if (configProjectId != null) {
            Log.d("VertexAuth", "Using project ID from local.properties: $configProjectId")
            return configProjectId
        }

        // Fallback to ADC
        return try {
            val adcCredentials = GoogleCredentials.getApplicationDefault()
            adcCredentials.projectId ?: throw IllegalStateException("Project ID not found in ADC credentials")
        } catch (e: Exception) {
            Log.w("VertexAuth", "ADC project ID not available, trying service account: ${e.message}")
            // Fallback to service account JSON
            try {
                val stream = context.assets.open(ASSET_NAME)
                val credentials = ServiceAccountCredentials.fromStream(stream)
                credentials.projectId ?: throw IllegalStateException("Project ID not found in service account JSON")
            } catch (fallbackException: Exception) {
                throw IllegalStateException(
                    "Failed to get project ID. " +
                    "Please set VERTEX_AI_PROJECT_ID in local.properties, " +
                    "or ensure GOOGLE_APPLICATION_CREDENTIALS is set, " +
                    "or place $ASSET_NAME in assets.",
                    fallbackException
                )
            }
        }
    }

    /**
     * Check if credentials are available (either ADC or service account in assets).
     */
    fun hasCredentials(context: Context): Boolean {
        // Check if ADC is configured
        val adcAvailable = try {
            GoogleCredentials.getApplicationDefault()
            true
        } catch (e: Exception) {
            false
        }

        // Check if service account JSON exists in assets
        val assetAvailable = try {
            context.assets.list("")?.contains(ASSET_NAME) ?: false
        } catch (e: Exception) {
            false
        }

        return adcAvailable || assetAvailable
    }

    /**
     * @deprecated Use hasCredentials() instead
     */
    @Deprecated("Use hasCredentials() for ADC support")
    fun hasServiceAccount(context: Context): Boolean {
        return hasCredentials(context)
    }
}
