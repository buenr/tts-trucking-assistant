package trucker.geminilive.network

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import trucker.geminilive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import trucker.geminilive.security.EncryptedCredentialStore
import java.io.ByteArrayInputStream
import java.util.Date

object VertexAuth {
    val LOCATION = BuildConfig.VERTEX_AI_LOCATION
    val MODEL = BuildConfig.VERTEX_AI_MODEL

    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    private const val TAG = "VertexAuth"

    // Token prefetch threshold - refresh if expires in less than 5 minutes
    private const val TOKEN_PREFETCH_THRESHOLD_MS = 5 * 60 * 1000L

    private var cachedProjectId: String? = null
    private var cachedToken: AccessToken? = null
    private var cachedCredentials: ServiceAccountCredentials? = null
    private val tokenMutex = Mutex()

    /**
     * Gets a valid access token, fetching a new one if needed or using cached token.
     */
    suspend fun getAccessToken(context: Context): String = tokenMutex.withLock {
        val token = cachedToken
        val now = Date()

        // Return cached token if still valid with buffer time
        if (token != null && token.expirationTime.after(Date(now.time + TOKEN_PREFETCH_THRESHOLD_MS))) {
            Log.d(TAG, "Using cached token (expires: ${token.expirationTime})")
            return@withLock token.tokenValue
        }

        // Need to fetch new token
        return@withLock fetchNewToken(context)
    }

    /**
     * Proactively prefetches a fresh token before the current one expires.
     * Call this when degradation is detected to ensure token is ready for failover.
     */
    suspend fun prefetchToken(context: Context): String? = tokenMutex.withLock {
        try {
            Log.d(TAG, "Proactively prefetching fresh token...")
            fetchNewToken(context)
        } catch (e: Exception) {
            Log.e(TAG, "Token prefetch failed", e)
            cachedToken?.tokenValue // Return existing if available
        }
    }

    /**
     * Invalidates the cached token, forcing a fresh fetch on next request.
     */
    fun invalidateToken() {
        cachedToken = null
        Log.d(TAG, "Token cache invalidated")
    }

    private suspend fun fetchNewToken(context: Context): String = withContext(Dispatchers.IO) {
        val credentialJson = EncryptedCredentialStore.getDecryptedCredentials(context)
        ByteArrayInputStream(credentialJson.toByteArray(Charsets.UTF_8)).use { stream ->
            // Reuse cached credentials if available
            val credentials = cachedCredentials ?: (ServiceAccountCredentials.fromStream(stream)
                .createScoped(listOf(SCOPE)) as ServiceAccountCredentials)
                .also { cachedCredentials = it }

            credentials.refreshIfExpired()
            val token = credentials.accessToken
            cachedToken = token
            Log.d(TAG, "Fetched new token (expires: ${token.expirationTime})")
            token.tokenValue
        }
    }

    /**
     * Returns time until token expiry in milliseconds, or -1 if no cached token.
     */
    fun getTimeUntilExpiryMs(): Long {
        val expiry = cachedToken?.expirationTime?.time ?: return -1
        return expiry - System.currentTimeMillis()
    }

    suspend fun getProjectId(context: Context): String {
        cachedProjectId?.let { return it }
        return try {
            val credentialJson = EncryptedCredentialStore.getDecryptedCredentials(context)
            ByteArrayInputStream(credentialJson.toByteArray(Charsets.UTF_8)).use { stream ->
                val credentials = ServiceAccountCredentials.fromStream(stream)
                val pid = credentials.projectId ?: BuildConfig.VERTEX_AI_PROJECT_ID
                cachedProjectId = pid
                pid
            }
        } catch (e: Exception) {
            BuildConfig.VERTEX_AI_PROJECT_ID
        }
    }

    /**
     * Checks if credentials are configured.
     * Uses VertexCredentialsManager to check for the asset file directly.
     */
    fun hasCredentials(context: Context): Boolean {
        return VertexCredentialsManager.hasCredentials(context)
    }

    /**
     * Gets GoogleCredentials for Vertex AI client.
     */
    suspend fun getCredentials(context: Context): GoogleCredentials = withContext(Dispatchers.IO) {
        val credentialJson = EncryptedCredentialStore.getDecryptedCredentials(context)
        ByteArrayInputStream(credentialJson.toByteArray(Charsets.UTF_8)).use { stream ->
            GoogleCredentials.fromStream(stream)
        }
    }
}




