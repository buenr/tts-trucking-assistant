package trucker.geminilive.network

import android.content.Context
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import trucker.geminilive.BuildConfig

object VertexAuth {
    private const val ASSET_NAME = "vertex-ai-testing1.json"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    /**
     * Get API key from BuildConfig.
     * Primary authentication method for Interactions API.
     */
    fun getApiKey(context: Context): String {
        return BuildConfig.GEMINI_API_KEY
    }
    
    /**
     * Check if API key is configured.
     */
    fun hasApiKey(): Boolean {
        return BuildConfig.GEMINI_API_KEY.isNotBlank()
    }

    /**
     * Get OAuth2 access token (fallback method).
     * Used when API key is not configured.
     */
    suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val stream = context.assets.open(ASSET_NAME)
        val credentials = ServiceAccountCredentials.fromStream(stream)
            .createScoped(listOf(SCOPE))
        credentials.refreshIfExpired()
        credentials.accessToken.tokenValue
    }

    fun getProjectId(context: Context): String {
        val stream = context.assets.open(ASSET_NAME)
        val credentials = ServiceAccountCredentials.fromStream(stream)
        return credentials.projectId ?: "vertex-ai-testing1"
    }
}
