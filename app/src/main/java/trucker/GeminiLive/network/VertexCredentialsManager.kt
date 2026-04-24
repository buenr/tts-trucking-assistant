package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import trucker.geminilive.BuildConfig
import java.io.ByteArrayInputStream

/**
 * Simplified credentials manager for Vertex AI authentication.
 *
 * Loads the service account JSON directly from app assets (assets/)
 * and creates GoogleCredentials for the Gen AI SDK.
 *
 * Setup:
 * 1. Download your service account JSON from Google Cloud Console
 * 2. Rename it to "vertex_sa.json"
 * 3. Place it in: app/src/main/assets/vertex_sa.json
 *
 * Security: The JSON is protected by APK signing and the Android assets system.
 */
object VertexCredentialsManager {

    private const val TAG = "VertexCredentialsManager"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    private const val ASSET_FILENAME = "vertex-ai-testing1.json"

    private var cachedCredentials: GoogleCredentials? = null
    private var cachedProjectId: String? = null
    private val mutex = Mutex()

    /**
     * Gets GoogleCredentials for Vertex AI client.
     * Loads from assets/vertex_sa.json on first call, then caches.
     */
    suspend fun getCredentials(context: Context): GoogleCredentials = mutex.withLock {
        cachedCredentials?.let { return@withLock it }

        withContext(Dispatchers.IO) {
            try {
                // Load service account JSON from assets
                val jsonString = loadServiceAccountJson(context)

                // Parse to extract project_id
                val projectId = extractProjectId(jsonString)
                cachedProjectId = projectId

                // Create credentials
                val stream = ByteArrayInputStream(jsonString.toByteArray(Charsets.UTF_8))
                val credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(listOf(SCOPE))

                cachedCredentials = credentials
                Log.d(TAG, "Loaded credentials for project: $projectId")

                credentials
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load credentials", e)
                throw IllegalStateException(
                    "Failed to load Vertex AI credentials. " +
                    "Ensure assets/vertex_sa.json exists and is a valid service account key. " +
                    "Error: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Gets the project ID from the service account or BuildConfig.
     */
    suspend fun getProjectId(context: Context): String = mutex.withLock {
        cachedProjectId?.let { return@withLock it }

        withContext(Dispatchers.IO) {
            try {
                val jsonString = loadServiceAccountJson(context)
                val projectId = extractProjectId(jsonString)
                cachedProjectId = projectId
                projectId
            } catch (e: Exception) {
                // Fallback to build config
                BuildConfig.VERTEX_AI_PROJECT_ID.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("No project ID available")
            }
        }
    }

    /**
     * Clears cached credentials. Call this if you need to reload (e.g., after rotation).
     */
    fun clearCache() {
        cachedCredentials = null
        cachedProjectId = null
        Log.d(TAG, "Credentials cache cleared")
    }

    /**
     * Check if credentials are available (vertex_sa.json exists in assets).
     */
    fun hasCredentials(context: Context): Boolean {
        return try {
            context.assets.open(ASSET_FILENAME).use { it.available() > 0 }
        } catch (e: Exception) {
            false
        }
    }

    private fun loadServiceAccountJson(context: Context): String {
        return try {
            context.assets.open(ASSET_FILENAME).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: java.io.IOException) {
            throw IllegalStateException(
                "Service account JSON not found. " +
                "Place your service account key at: app/src/main/assets/vertex_sa.json"
            )
        }
    }

    private fun extractProjectId(jsonString: String): String {
        return try {
            val json = Json.parseToJsonElement(jsonString).jsonObject
            json["project_id"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("project_id not found in service account JSON")
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse project_id from JSON, using BuildConfig", e)
            BuildConfig.VERTEX_AI_PROJECT_ID.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("No project ID available")
        }
    }
}
