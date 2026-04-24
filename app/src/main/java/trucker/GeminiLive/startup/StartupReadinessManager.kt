package trucker.geminilive.startup

import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import trucker.geminilive.network.VertexAuth
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Validates that the tablet has the required offline voice models before
 * entering Driving Mode. If models are missing, the driver must connect to
 * terminal Wi-Fi to download the required Google/Samsung voice packs (~150MB).
 */
class StartupReadinessManager(private val context: Context) {

    data class ReadinessReport(
        val isReady: Boolean,
        val sttAvailable: Boolean,
        val ttsOfflineVoiceAvailable: Boolean,
        val vertexAiConfigured: Boolean,
        val errors: List<String>
    )

    suspend fun checkReadiness(): ReadinessReport = withContext(Dispatchers.Main) {
        val errors = mutableListOf<String>()

        // 1. Check offline STT model availability
        val sttAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!sttAvailable) {
            errors.add("Offline Speech-to-Text language pack is not installed. Please connect to terminal Wi-Fi to download it.")
            Log.w("StartupReadiness", "On-device STT not available")
        } else {
            Log.d("StartupReadiness", "On-device STT available")
        }

        // 2. Check local TTS voices
        val ttsOfflineVoiceAvailable = checkTtsOfflineVoice()
        if (!ttsOfflineVoiceAvailable) {
            errors.add("Offline Text-to-Speech voice pack is not installed. Please connect to terminal Wi-Fi to download it.")
            Log.w("StartupReadiness", "Offline TTS voice not available")
        } else {
            Log.d("StartupReadiness", "Offline TTS voice available")
        }

        // 3. Verify Google TTS engine is present
        val pm = context.packageManager
        val googleTtsInstalled = try {
            pm.getPackageInfo("com.google.android.tts", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!googleTtsInstalled) {
            errors.add("Google Text-to-Speech engine is not installed.")
            Log.w("StartupReadiness", "Google TTS engine not installed")
        }

        // 4. Check Vertex AI credentials are configured (ADC or service account in assets)
        val vertexAiConfigured = VertexAuth.hasCredentials(context)
        if (!vertexAiConfigured) {
            errors.add("Vertex AI credentials not configured. Set GOOGLE_APPLICATION_CREDENTIALS environment variable or add vertex-ai-testing1.json to assets.")
            Log.w("StartupReadiness", "Vertex AI credentials not found (ADC or service account)")
        } else {
            Log.d("StartupReadiness", "Vertex AI credentials configured")
        }

        val isReady = sttAvailable && ttsOfflineVoiceAvailable && googleTtsInstalled && vertexAiConfigured
        ReadinessReport(
            isReady = isReady,
            sttAvailable = sttAvailable,
            ttsOfflineVoiceAvailable = ttsOfflineVoiceAvailable,
            vertexAiConfigured = vertexAiConfigured,
            errors = errors
        )
    }

    private suspend fun checkTtsOfflineVoice(): Boolean = suspendCancellableCoroutine { continuation ->
        var resumed = false
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (resumed) return@TextToSpeech
            resumed = true
            if (status == TextToSpeech.SUCCESS) {
                val voices = tts.voices
                val hasOfflineUsVoice = voices?.any { voice ->
                    voice.locale == Locale.US && !voice.isNetworkConnectionRequired
                } ?: false
                val hasOfflineEnVoice = voices?.any { voice ->
                    voice.locale.language == "en" && !voice.isNetworkConnectionRequired
                } ?: false
                val result = hasOfflineUsVoice || hasOfflineEnVoice
                tts.shutdown()
                continuation.resume(result)
            } else {
                tts.shutdown()
                continuation.resume(false)
            }
        }
        continuation.invokeOnCancellation {
            if (!resumed) {
                resumed = true
                tts.shutdown()
                continuation.resume(false)
            }
        }
    }
}
