package trucker.geminilive.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeakComplete: (() -> Unit)? = null
    private val pendingTexts = mutableListOf<String>()
    
    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onStart(utteranceId: String?) {
                        Log.d("TtsManager", "TTS started speaking")
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onDone(utteranceId: String?) {
                        Log.d("TtsManager", "TTS completed")
                        onSpeakComplete?.invoke()
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e("TtsManager", "TTS error")
                        onSpeakComplete?.invoke()
                    }
                })
                // Process any pending text
                pendingTexts.forEach { speak(it) }
                pendingTexts.clear()
            } else {
                Log.e("TtsManager", "TTS initialization failed with status: $status")
            }
        }
    }
    
    fun speak(text: String) {
        if (!isInitialized) {
            pendingTexts.add(text)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_${System.currentTimeMillis()}")
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun flush() {
        tts?.stop()
    }
    
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
