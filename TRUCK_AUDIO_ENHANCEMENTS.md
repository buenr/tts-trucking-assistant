# Truck Cab Audio Enhancement Ideas

## Current State
- **STT**: Uses Android's `SpeechRecognizer` with offline preference (`EXTRA_PREFER_OFFLINE`)
- **TTS**: Uses Android's `TextToSpeech` with offline voice selection
- **Noise handling**: Minimal — relies on Android's built-in VAD (Voice Activity Detection)
- **Audio ducking**: TTS requests audio focus to lower background music/GPS volume
- **Offline requirement**: 100% offline, no cloud fallback

## Truck Cab Challenges
1. **High ambient noise**: Engine (70-85 dB), road/wind (60-75 dB), tire noise
2. **Low SNR (Signal-to-Noise Ratio)**: Driver voice often masked by background noise
3. **Acoustic reflections**: Metal cab creates echoes and reverb
4. **Microphone limitations**: Built-in tablet mics not optimized for noisy environments
5. **Competing audio**: Engine sounds, radio, navigation, other alerts

---

## Enhancement Ideas (Prioritized)

### Tier 1: High Impact, Moderate Effort

#### 1. **Adaptive Noise Gate with RMS-based Detection**
**Problem**: Background noise triggers false positives in speech detection.

**Solution**: Implement a dynamic noise gate that:
- Measures RMS (Root Mean Square) energy during silence periods
- Establishes a noise floor baseline on app startup
- Only triggers speech recognition when signal exceeds noise floor + threshold (e.g., +6dB)
- Adapts baseline periodically (every 30-60 seconds) to account for changing engine RPM

**Implementation**:
```kotlin
// In SttManager.kt
private var noiseFloorRms = 0f
private var noiseFloorUpdateTime = 0L

fun updateNoiseFloor(rmsdB: Float) {
    val now = System.currentTimeMillis()
    if (now - noiseFloorUpdateTime > 30000) { // Update every 30s
        noiseFloorRms = rmsdB
        noiseFloorUpdateTime = now
    }
}

fun shouldTriggerSpeech(rmsdB: Float): Boolean {
    return rmsdB > (noiseFloorRms + 6f) // 6dB above noise floor
}
```

**Benefit**: Reduces false triggers from engine noise; improves reliability in high-noise environments.

---

#### 2. **Spectral Subtraction for Noise Reduction**
**Problem**: STT engine struggles with high background noise.

**Solution**: Apply spectral subtraction preprocessing to audio before sending to recognizer:
- Capture a ~500ms sample of silence (noise profile) at startup
- For each audio frame, subtract the noise spectrum from the signal spectrum
- Clamp negative values to prevent artifacts
- Reconstruct time-domain audio via inverse FFT

**Implementation**: Use Android's `AudioRecord` to capture raw PCM, apply FFT-based spectral subtraction, then feed cleaned audio to `SpeechRecognizer`.

**Libraries**: 
- Built-in: `android.media.AudioRecord` + `java.util.Arrays` for FFT
- Optional: Add lightweight FFT library (e.g., `commons-math` or custom Cooley-Tukey FFT)

**Benefit**: Significant SNR improvement; can reduce noise by 10-20dB in stationary noise scenarios.

---

#### 3. **Microphone Array Simulation (Beamforming)**
**Problem**: Single microphone picks up noise from all directions equally.

**Solution**: If tablet has multiple microphones:
- Use directional audio capture to focus on driver's voice direction
- Implement simple delay-and-sum beamforming (if hardware supports it)
- Fall back to single-mic if unavailable

**Implementation**: Query `AudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND)` and similar to detect multi-mic support.

**Benefit**: Reduces off-axis noise; improves SNR by 3-6dB if multi-mic available.

---

#### 4. **Silence Timeout Tuning for Truck Environment**
**Problem**: Current silence thresholds (800ms) may be too aggressive in noisy cabs.

**Solution**: Make silence detection configurable:
- Add settings UI to adjust `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`
- Provide presets: "Quiet", "Normal", "Loud Truck"
- Default to "Loud Truck" (1200-1500ms) for better robustness

**Implementation**:
```kotlin
// In SttManager.kt
enum class NoiseProfile {
    QUIET(800),
    NORMAL(1000),
    LOUD_TRUCK(1500)
}

fun startListening(profile: NoiseProfile = NoiseProfile.LOUD_TRUCK) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 
                 profile.silenceMs.toLong())
        // ... other extras
    }
    speechRecognizer?.startListening(intent)
}
```

**Benefit**: Reduces premature speech cutoff; improves recognition accuracy in noisy conditions.

---

### Tier 2: Medium Impact, Higher Effort

#### 5. **On-Device Noise Suppression with TensorFlow Lite**
**Problem**: Spectral subtraction is basic; more advanced algorithms needed for complex noise.

**Solution**: Integrate a lightweight TensorFlow Lite model for noise suppression:
- Use pre-trained models like `RNNoise` (converted to TFLite) or Google's `Noise Suppression`
- Run inference on raw audio frames before STT
- ~10-50ms latency per frame (acceptable for speech)

**Implementation**:
- Add TFLite dependency: `org.tensorflow:tensorflow-lite:2.13.0`
- Load model from assets
- Process audio in chunks (e.g., 512 samples @ 16kHz = 32ms)

**Benefit**: State-of-the-art noise reduction; handles non-stationary noise (engine RPM changes, traffic).

---

#### 6. **Echo Cancellation (AEC)**
**Problem**: TTS output can be picked up by microphone, confusing STT.

**Solution**: Implement Acoustic Echo Cancellation:
- Use Android's `AcousticEchoCanceler` (if available on device)
- Or implement simple delay-based echo cancellation
- Synchronize TTS playback with STT input to identify and cancel echoes

**Implementation**:
```kotlin
// In SttManager.kt
private var aec: AcousticEchoCanceler? = null

fun enableEchoCancellation(audioSessionId: Int) {
    aec = AcousticEchoCanceler.create(audioSessionId)
    aec?.enabled = true
}
```

**Benefit**: Prevents feedback loops; improves STT accuracy when TTS is playing.

---

#### 7. **Automatic Gain Control (AGC) Tuning**
**Problem**: Microphone input levels vary; quiet speech gets lost in noise.

**Solution**: Implement or tune AGC:
- Use Android's `AutomaticGainControl` if available
- Or implement manual gain adjustment based on signal level
- Target a consistent RMS level (e.g., -20dBFS) for STT input

**Implementation**:
```kotlin
// In SttManager.kt
private var agc: AutomaticGainControl? = null

fun enableAutoGain(audioSessionId: Int) {
    agc = AutomaticGainControl.create(audioSessionId)
    agc?.enabled = true
}
```

**Benefit**: Normalizes input levels; improves STT robustness across different microphone distances.

---

#### 8. **Multi-Pass Recognition with Confidence Scoring**
**Problem**: Single STT pass may fail in very noisy conditions.

**Solution**: Implement retry logic with confidence thresholds:
- If STT confidence < 60%, automatically retry listening
- Optionally apply different noise profiles on retry
- Log confidence scores for debugging

**Implementation**:
```kotlin
// In SttManager.kt
private var lastConfidence = 1.0f

override fun onResults(results: Bundle?) {
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
    
    lastConfidence = confidences?.firstOrNull() ?: 0f
    
    if (lastConfidence < 0.6f && retryCount < 2) {
        retryCount++
        startListening() // Retry
    } else {
        onFinalResult?.invoke(matches?.firstOrNull() ?: "")
    }
}
```

**Benefit**: Improves recognition success rate in extreme noise; reduces user frustration.

---

### Tier 3: Nice-to-Have, Lower Priority

#### 9. **Frequency-Domain Filtering**
**Problem**: Engine noise is concentrated in low frequencies (50-500 Hz).

**Solution**: Apply high-pass filter to remove low-frequency rumble:
- Design a simple IIR high-pass filter (cutoff ~300 Hz)
- Apply to audio before STT
- Preserve speech frequencies (300 Hz - 3.5 kHz)

**Benefit**: Reduces engine rumble; improves STT accuracy.

---

#### 10. **Adaptive TTS Volume**
**Problem**: TTS volume may be too quiet or too loud relative to engine noise.

**Solution**: Adjust TTS volume based on ambient noise level:
- Measure noise floor during listening phase
- Scale TTS volume proportionally (e.g., +3dB per 10dB increase in noise)
- Provide manual override in settings

**Implementation**:
```kotlin
// In TtsManager.kt
fun setSpeechVolume(noiseFloorRms: Float) {
    val volumeScale = 1.0f + (noiseFloorRms / 100f) // Scale up in noise
    tts?.setSpeechRate(0.8f * volumeScale)
}
```

**Benefit**: Ensures driver can hear responses in noisy conditions.

---

#### 11. **Haptic Feedback for Audio Events**
**Problem**: Driver may not hear STT/TTS state changes in loud cab.

**Solution**: Add vibration feedback:
- Vibrate when listening starts
- Vibrate pattern when speech recognized
- Vibrate when TTS completes

**Implementation**:
```kotlin
// In MainActivity.kt
private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

fun vibrateListeningStart() {
    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
}
```

**Benefit**: Provides non-audio feedback; improves UX in loud environments.

---

#### 12. **Noise Profile Calibration UI**
**Problem**: Users can't easily optimize for their specific truck.

**Solution**: Add a calibration screen:
- Record 5-10 seconds of ambient noise
- Analyze frequency spectrum
- Suggest optimal settings (noise gate threshold, filter cutoff, etc.)
- Save profile per truck/driver

**Benefit**: Personalized optimization; improves accuracy across diverse truck types.

---

## Implementation Roadmap

### Phase 1 (Immediate)
1. Adaptive noise gate (Tier 1.1)
2. Silence timeout tuning UI (Tier 1.4)
3. Haptic feedback (Tier 3.11)

### Phase 2 (Short-term)
4. Spectral subtraction (Tier 1.2)
5. Echo cancellation (Tier 2.6)
6. Multi-pass recognition (Tier 2.8)

### Phase 3 (Medium-term)
7. TensorFlow Lite noise suppression (Tier 2.5)
8. Adaptive TTS volume (Tier 3.10)
9. Noise profile calibration (Tier 3.12)

### Phase 4 (Long-term)
10. Microphone array beamforming (Tier 1.3)
11. Frequency-domain filtering (Tier 3.9)

---

## Testing Strategy

1. **Synthetic noise**: Generate engine/road noise at various dB levels
2. **Real-world testing**: Test in actual trucks at different RPMs
3. **Metrics**:
   - Word Error Rate (WER) vs. noise level
   - Recognition latency
   - False positive rate (noise triggering STT)
   - Battery impact

---

## Dependencies to Consider

- **TensorFlow Lite**: For advanced noise suppression
- **Apache Commons Math**: For FFT (if not using TFLite)
- **Kotlin Coroutines**: Already in use; good for async audio processing

---

## Notes

- **Offline constraint**: All solutions must work 100% offline on the tablet
- **Latency**: Keep total audio processing latency < 100ms to avoid user frustration
- **Battery**: Audio processing is CPU-intensive; monitor battery impact
- **Tablet hardware**: Verify microphone quality and multi-mic support on target device (Samsung Galaxy Tab Active 5)
