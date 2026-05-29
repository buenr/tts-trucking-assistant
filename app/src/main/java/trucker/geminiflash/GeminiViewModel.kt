package trucker.geminiflash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import trucker.geminiflash.audio.SttManager
import trucker.geminiflash.audio.TtsManager
import trucker.geminiflash.audio.NoiseProfile
import trucker.geminiflash.controller.AiState
import trucker.geminiflash.controller.CoPilotController
import trucker.geminiflash.controller.CopilotUiState
import trucker.geminiflash.startup.StartupReadinessManager

class GeminiViewModel(application: Application) : AndroidViewModel(application) {

    // Managers
    private val sttManager = SttManager(application)
    private val ttsManager = TtsManager(application)
    private var closeAppCallback: (() -> Unit)? = null

    // Controller (VertexAiClient is created internally)
    private val controller = CoPilotController(
        context = application,
        sttManager = sttManager,
        ttsManager = ttsManager,
        onCloseAppRequested = {
            closeAppCallback?.invoke()
        }
    )

    // Startup
    private val startupManager = StartupReadinessManager(application)

    // Exposed UI state from controller
    val uiState: StateFlow<CopilotUiState> = controller.uiState
    val partialText: StateFlow<String> = controller.partialText
    val logs: StateFlow<List<String>> = controller.logs

    // Startup readiness
    private val _readinessReport = MutableStateFlow<StartupReadinessManager.ReadinessReport?>(null)
    val readinessReport: StateFlow<StartupReadinessManager.ReadinessReport?> = _readinessReport

    private val _isCheckingReadiness = MutableStateFlow(true)
    val isCheckingReadiness: StateFlow<Boolean> = _isCheckingReadiness

    private var lastAnnouncedErrorSignature: String? = null

    init {
        checkReadiness()
    }

    fun checkReadiness() {
        _isCheckingReadiness.value = true
        viewModelScope.launch {
            val report = startupManager.checkReadiness()
            _readinessReport.value = report
            _isCheckingReadiness.value = false

            if (report.isReady) {
                lastAnnouncedErrorSignature = null
                controller.start()
            } else {
                controller.stop()
                announceReadinessFailure(report)
            }
        }
    }

    private fun announceReadinessFailure(report: StartupReadinessManager.ReadinessReport) {
        val signature = report.errors.joinToString("|")
        if (signature == lastAnnouncedErrorSignature) return
        lastAnnouncedErrorSignature = signature

        val hint = report.errors.firstOrNull()
            ?: "Setup incomplete. Speech or cloud configuration missing."
        ttsManager.speak(hint)
    }

    fun startSession() {
        if (!isSpeechReady()) return
        controller.start()
    }

    fun stopSession() {
        controller.stop()
    }

    fun onActiveKeyPressed() {
        if (!isSpeechReady()) return
        controller.onActiveKeyPressed()
    }

    private fun isSpeechReady(): Boolean {
        val report = _readinessReport.value
        return report?.isReady == true && !_isCheckingReadiness.value
    }

    fun setCloseAppCallback(callback: () -> Unit) {
        closeAppCallback = callback
    }

    fun setAnswerMode(mode: trucker.geminiflash.controller.AnswerMode) {
        controller.setAnswerMode(mode)
    }

    fun setNoiseProfile(profile: trucker.geminiflash.audio.NoiseProfile) {
        controller.setNoiseProfile(profile)
    }

    fun getNoiseProfile(): trucker.geminiflash.audio.NoiseProfile {
        return controller.getNoiseProfile()
    }

    
    fun addLog(message: String) {
        // Controller owns the logs; this is a convenience bridge for external log injection
        // Not needed for normal flow, but kept for compatibility if any external component needs it.
    }

    override fun onCleared() {
        super.onCleared()
        controller.destroy()
        sttManager.destroy()
        ttsManager.release()
    }
}
