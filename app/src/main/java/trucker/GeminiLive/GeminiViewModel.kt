package trucker.geminilive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import trucker.geminilive.audio.SttManager
import trucker.geminilive.audio.TtsManager
import trucker.geminilive.controller.AiState
import trucker.geminilive.controller.CoPilotController
import trucker.geminilive.controller.CopilotUiState
import trucker.geminilive.network.GeminiRestClient
import trucker.geminilive.startup.StartupReadinessManager

class GeminiViewModel(application: Application) : AndroidViewModel(application) {

    // Managers
    private val sttManager = SttManager(application)
    private val ttsManager = TtsManager(application)
    private val geminiClient = GeminiRestClient(
        onToolCallStarted = { toolName ->
            addLog("TOOL CALL: $toolName")
        }
    )

    // Controller
    private val controller = CoPilotController(
        context = application,
        sttManager = sttManager,
        ttsManager = ttsManager,
        geminiClient = geminiClient
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
                // Auto-start the copilot session once everything is verified
                controller.start()
            }
        }
    }

    fun startSession() {
        controller.start()
    }

    fun stopSession() {
        controller.stop()
    }

    fun onActiveKeyPressed() {
        controller.onActiveKeyPressed()
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
