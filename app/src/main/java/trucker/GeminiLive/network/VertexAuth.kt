package trucker.geminilive.network

import android.content.Context
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vertex AI authentication helper.
 * Uses service account JSON key for OAuth2 authentication with Vertex AI.
 */
object VertexAuth {
    private const val ASSET_NAME = "vertex-ai-testing1.json"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    // Vertex AI configuration
    const val LOCATION = "global"
    const val MODEL = "gemini-3-flash-preview"

    /**
     * Get OAuth2 access token from service account.
     */
    suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val stream = context.assets.open(ASSET_NAME)
        val credentials = ServiceAccountCredentials.fromStream(stream)
            .createScoped(listOf(SCOPE))
        credentials.refreshIfExpired()
        credentials.accessToken.tokenValue
    }

    /**
     * Get project ID from service account JSON.
     */
    fun getProjectId(context: Context): String {
        val stream = context.assets.open(ASSET_NAME)
        val credentials = ServiceAccountCredentials.fromStream(stream)
        return credentials.projectId ?: throw IllegalStateException("Project ID not found in service account JSON")
    }

    /**
     * Check if service account JSON exists in assets.
     */
    fun hasServiceAccount(context: Context): Boolean {
        return try {
            context.assets.list("")?.contains(ASSET_NAME) ?: false
        } catch (e: Exception) {
            false
        }
    }
}
