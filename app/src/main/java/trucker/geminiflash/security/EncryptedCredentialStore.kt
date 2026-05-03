package trucker.geminiflash.security

import android.content.Context
import android.util.Log
import trucker.geminiflash.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Secure credential store that decrypts the Vertex service account key at runtime.
 * The key is stored encrypted in the APK and decrypted via JNI.
 */
object EncryptedCredentialStore {

    private const val TAG = "EncryptedCredentialStore"

    /**
     * The encrypted service account JSON.
     * This is injected at build time by encrypting the original JSON key file.
     * Format: Base64-encoded, XOR-encrypted byte array.
     *
     * To generate:
     * 1. Run the encrypt_key.py script with your JSON key and build fragment
     * 2. Copy the output here as ENCRYPTED_KEY constant
     */
    private const val ENCRYPTED_KEY: String = BuildConfig.ENCRYPTED_VERTEX_KEY

    /**
     * Returns the decrypted service account JSON as a String.
     * Decryption happens in native code to avoid JVM memory exposure.
     */
    suspend fun getDecryptedCredentials(context: Context): String = withContext(Dispatchers.IO) {
        if (ENCRYPTED_KEY.isEmpty() || ENCRYPTED_KEY == "PLACEHOLDER") {
            throw IllegalStateException(
                "Encrypted key not set. " +
                "Run: python scripts/encrypt_key.py --input vertex-ai-testing1.json --fragment <BUILD_KEY>"
            )
        }

        try {
            // Decode base64
            val encryptedBytes = Base64.getDecoder().decode(ENCRYPTED_KEY)

            // Decrypt via JNI
            val decrypted = CryptoBridge.decryptData(encryptedBytes, BuildConfig.BUILD_KEY_FRAGMENT)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt credentials", e)
            throw IllegalStateException("Credential decryption failed", e)
        }
    }

    /**
     * Check if credentials are available (encrypted key is set).
     */
    fun hasCredentials(): Boolean {
        return ENCRYPTED_KEY.isNotEmpty() && ENCRYPTED_KEY != "PLACEHOLDER"
    }
}
