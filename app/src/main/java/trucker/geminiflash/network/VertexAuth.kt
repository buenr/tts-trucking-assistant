package trucker.geminiflash.network

import android.content.Context
import trucker.geminiflash.BuildConfig

/**
 * Simplified Vertex AI configuration object.
 * Provides constants for location and model configuration.
 * 
 * For credential management, use VertexCredentialsManager directly.
 */
object VertexAuth {
    val LOCATION = BuildConfig.VERTEX_AI_LOCATION
    val MODEL = BuildConfig.VERTEX_AI_MODEL

    /**
     * Checks if credentials are configured.
     * Delegates to VertexCredentialsManager to check for the asset file.
     */
    fun hasCredentials(context: Context): Boolean {
        return VertexCredentialsManager.hasCredentials(context)
    }
}




