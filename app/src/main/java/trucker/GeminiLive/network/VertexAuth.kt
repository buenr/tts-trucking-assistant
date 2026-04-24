package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vertex AI authentication helper.
 * Uses Application Default Credentials (ADC) for OAuth2 authentication.
 * Falls back to service account JSON from assets if ADC is not configured.
 *
 * ADC Configuration:
 * - Set GOOGLE_APPLICATION_CREDENTIALS environment variable to point to service account JSON
 * - Or place vertex-ai-testing1.json in app/src/main/assets/
 */
object VertexAuth {
    private const val ASSET_NAME = "vertex-ai-testing1.json"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    // Vertex AI configuration
    const val LOCATION = "global"
    const val MODEL = "gemini-2.5-flash"

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
     * Get project ID from credentials.
     * Tries ADC first, then falls back to service account JSON.
     */
    fun getProjectId(context: Context): String {
        return try {
            // Try ADC first
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
                    "Failed to get project ID from ADC or service account. " +
                    "Please ensure GOOGLE_APPLICATION_CREDENTIALS is set or $ASSET_NAME is in assets.",
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
