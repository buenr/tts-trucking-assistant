package trucker.geminilive.audio

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class TtsManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeakComplete: (() -> Unit)? = null
    private val pendingSentences = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Offline voice enforcement
    private var offlineVoice: Voice? = null
    private var hasOfflineVoice = false

    // Sentence boundary buffer for streaming TTS
    private val streamBuffer = StringBuilder()
    private val sentenceDelimiters = charArrayOf('.', '?', '!', '\n')

    fun setOnSpeakComplete(callback: () -> Unit) {
        onSpeakComplete = callback
    }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts?.defaultEngine
                Log.d("TtsManager", "TTS engine: $engine")

                // Check for offline English voice
                val voices = tts?.voices
                offlineVoice = voices?.firstOrNull { voice ->
                    voice.locale == Locale.US &&
                            voice.name.contains("en-us", ignoreCase = true) &&
                            !voice.isNetworkConnectionRequired
                } ?: voices?.firstOrNull { voice ->
                    voice.locale.language == "en" && !voice.isNetworkConnectionRequired
                }

                hasOfflineVoice = offlineVoice != null
                val currentVoice = offlineVoice
                if (hasOfflineVoice && currentVoice != null) {
                    tts?.voice = currentVoice
                    Log.d("TtsManager", "Using offline voice: ${currentVoice.name}")
                } else {
                    Log.w("TtsManager", "No offline US English voice found; falling back to default")
                }

                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onStart(utteranceId: String?) {
                        Log.d("TtsManager", "TTS started: $utteranceId")
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onDone(utteranceId: String?) {
                        Log.d("TtsManager", "TTS completed: $utteranceId")
                        processNextSentence()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e("TtsManager", "TTS error: $utteranceId")
                        processNextSentence()
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        Log.d("TtsManager", "TTS stopped: $utteranceId (interrupted=$interrupted)")
                        processNextSentence()
                    }
                })

                isInitialized = true
                // Process any pending buffered sentences
                flushStreamBuffer()
            } else {
                Log.e("TtsManager", "TTS initialization failed: $status")
                isInitialized = false
            }
        }
    }

    fun hasOfflineVoice(): Boolean = hasOfflineVoice

    /**
     * Queue a complete utterance (non-streaming). Used for fallback / error messages.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        pendingSentences.add(text.trim())
        if (!isSpeaking) {
            processNextSentence()
        }
    }

    /**
     * Feed streaming text deltas. Buffers text and flushes complete sentences.
     * This allows the tablet to speak before the LLM finishes generating.
     */
    fun streamText(delta: String) {
        if (delta.isBlank()) return

        synchronized(streamBuffer) {
            streamBuffer.append(delta)

            // Extract complete sentences
            var flushIndex = -1
            for (i in streamBuffer.indices) {
                if (streamBuffer[i] in sentenceDelimiters) {
                    flushIndex = i
                }
            }

            if (flushIndex >= 0) {
                val chunk = streamBuffer.substring(0, flushIndex + 1).trim()
                streamBuffer.delete(0, flushIndex + 1)
                if (chunk.isNotBlank()) {
                    pendingSentences.add(chunk)
                }
            }
        }

        if (!isSpeaking) {
            processNextSentence()
        }
    }

    /**
     * Flush any remaining buffered text as a final sentence.
     */
    fun flushStreamBuffer() {
        synchronized(streamBuffer) {
            if (streamBuffer.isNotBlank()) {
                val chunk = streamBuffer.toString().trim()
                streamBuffer.clear()
                if (chunk.isNotBlank()) {
                    pendingSentences.add(chunk)
                }
            }
        }
        if (!isSpeaking) {
            processNextSentence()
        }
    }

    private fun processNextSentence() {
        if (!isInitialized) {
            Log.w("TtsManager", "TTS not initialized yet")
            return
        }

        val sentence = pendingSentences.poll()
        if (sentence == null) {
            isSpeaking = false
            onSpeakComplete?.invoke()
            return
        }

        isSpeaking = true

        // Request audio focus with ducking so music/GPS quiets while speaking
        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d("TtsManager", "Audio focus granted (ducking)")
        }

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        pendingSentences.clear()
        synchronized(streamBuffer) {
            streamBuffer.clear()
        }
        audioManager.abandonAudioFocus(null)
    }

    fun release() {
        stop()
        scope.cancel()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
