package trucker.geminilive.security

import trucker.geminilive.BuildConfig

/**
 * JNI bridge to native encryption/decryption.
 * The actual key material never exists in JVM memory in plain text.
 */
object CryptoBridge {

    // Build-time injected key fragment - passed to native code
    // This is combined with embedded native key for decryption
    private val buildKeyFragment: String = BuildConfig.BUILD_KEY_FRAGMENT

    init {
        System.loadLibrary("crypto-bridge")
    }

    /**
     * Decrypts data using native code. Returns the decrypted byte array.
     */
    @JvmStatic
    external fun decryptData(encryptedData: ByteArray, buildKeyFragment: String): ByteArray

    /**
     * Encrypts data using native code (for build-time preprocessing).
     */
    @JvmStatic
    external fun encryptData(plainData: ByteArray, buildKeyFragment: String): ByteArray

    /**
     * Convenience method to decrypt to string.
     */
    fun decryptToString(encryptedData: ByteArray): String {
        return String(decryptData(encryptedData, buildKeyFragment), Charsets.UTF_8)
    }
}
