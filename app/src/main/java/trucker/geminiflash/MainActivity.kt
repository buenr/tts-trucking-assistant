package trucker.geminiflash

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import trucker.geminiflash.controller.AiState
import trucker.geminiflash.controller.AnswerMode
import trucker.geminiflash.controller.CopilotUiState
import trucker.geminiflash.startup.StartupReadinessManager
import trucker.geminiflash.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: GeminiViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.util.Log.w("MainActivity", "Audio recording permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            MyApplicationTheme {
                KeepScreenOn()
                val vm: GeminiViewModel = viewModel()
                viewModel = vm

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.setCloseAppCallback {
                        closeApp()
                    }
                }

                val readiness by vm.readinessReport.collectAsStateWithLifecycle()
                val isChecking by vm.isCheckingReadiness.collectAsStateWithLifecycle()

                when {
                    isChecking -> LoadingScreen()
                    readiness?.isReady == false -> ReadinessScreen(
                        report = readiness!!,
                        onRecheck = { vm.checkReadiness() }
                    )
                    else -> CopilotApp(vm)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 1001 || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_BUTTON_1) {
            if (::viewModel.isInitialized) {
                viewModel.onActiveKeyPressed()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun closeApp() {
        finishAffinity()
    }
}

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(64.dp))
    }
}

@Composable
fun ReadinessScreen(
    report: StartupReadinessManager.ReadinessReport,
    onRecheck: () -> Unit
) {
    val ttsTint = when {
        report.ttsOfflineVoiceAvailable && report.googleTtsInstalled -> Color(0xFF00E676)
        report.ttsOfflineVoiceAvailable -> Color(0xFFFFC107)
        else -> Color(0xFFFF5252)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(96.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReadinessCheckIcon(
                    icon = Icons.Default.Mic,
                    tint = if (report.sttAvailable) Color(0xFF00E676) else Color(0xFFFF5252)
                )
                ReadinessCheckIcon(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    tint = ttsTint
                )
                ReadinessCheckIcon(
                    icon = Icons.Default.Cloud,
                    tint = if (report.vertexAiConfigured) Color(0xFF00E676) else Color(0xFFFF5252)
                )
            }

            IconButton(onClick = onRecheck) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadinessCheckIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .border(3.dp, tint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun CopilotApp(viewModel: GeminiViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val bgColor = when (uiState.aiState) {
        AiState.LISTENING -> Color(0xFF0A1F0A)
        AiState.CHECKING_DATA -> Color(0xFF1F1F0A)
        AiState.SPEAKING -> Color(0xFF0A0A1F)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Version number in bottom-left corner
        androidx.compose.material3.Text(
            text = "v1.0",
            color = Color(0xFF666666),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnswerModeChip(
                    mode = uiState.answerMode,
                    onClick = {
                        val next = if (uiState.answerMode == AnswerMode.SHORT) {
                            AnswerMode.LONG
                        } else {
                            AnswerMode.SHORT
                        }
                        viewModel.setAnswerMode(next)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            StateIndicator(
                state = uiState.aiState,
                hasError = uiState.lastError.isNotBlank()
            )

            Spacer(modifier = Modifier.weight(1f))

            ConnectionStatusDots(uiState = uiState)
        }
    }
}

@Composable
private fun AnswerModeChip(mode: AnswerMode, onClick: () -> Unit) {
    val isLong = mode == AnswerMode.LONG
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF2D2D2D),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isLong) 2.dp else 0.dp,
            color = Color(0xFF00E676)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(20.dp)
            )
            androidx.compose.material3.Text(
                text = "${mode.label} Answer",
                color = Color(0xFF00E676),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun StateIndicator(state: AiState, hasError: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val color = when (state) {
        AiState.LISTENING -> Color(0xFF00E676)
        AiState.CHECKING_DATA -> Color(0xFFFFC107)
        AiState.SPEAKING -> Color(0xFF42A5F5)
    }

    val icon = when (state) {
        AiState.LISTENING -> Icons.Default.Mic
        AiState.CHECKING_DATA -> Icons.Default.Settings
        AiState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == AiState.LISTENING || state == AiState.SPEAKING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (state == AiState.CHECKING_DATA) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val orbSize = if (hasError) 288.dp else 280.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(orbSize)
    ) {
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(4.dp, Color(0xFFFF5252), CircleShape)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(12.dp, color, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .rotate(rotation),
                tint = color
            )
        }
    }
}

@Composable
fun ConnectionStatusDots(uiState: CopilotUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val connected = uiState.status == "Connected"
        StatusDot(
            color = if (connected) Color(0xFF00E676) else Color(0xFF555555),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (uiState.aiState == AiState.CHECKING_DATA) {
            StatusDot(
                color = Color(0xFFFFC107),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        if (uiState.lastError.isNotBlank()) {
            StatusDot(
                color = Color(0xFFFF5252),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}


