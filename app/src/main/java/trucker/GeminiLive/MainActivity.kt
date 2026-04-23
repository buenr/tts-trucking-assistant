package trucker.geminilive

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import trucker.geminilive.controller.AiState
import trucker.geminilive.startup.StartupReadinessManager
import trucker.geminilive.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: GeminiViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // If permission denied, the app can't function; UI will show a warning
            android.util.Log.w("MainActivity", "Audio recording permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request audio permission upfront
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            MyApplicationTheme {
                KeepScreenOn()
                val vm: GeminiViewModel = viewModel()
                viewModel = vm

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
        // Samsung Galaxy Tab Active 5 Active Key (XCover key) = 1001
        // Also support common programmable headset/PTT buttons
        if (keyCode == 1001 || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_BUTTON_1) {
            if (::viewModel.isInitialized) {
                viewModel.onActiveKeyPressed()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Checking offline voice models...",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReadinessScreen(
    report: StartupReadinessManager.ReadinessReport,
    onRecheck: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "OFFLINE VOICE MODELS MISSING",
                color = Color(0xFFFF5252),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect to terminal Wi-Fi to download the required Google/Samsung voice packs (~150MB).",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            report.errors.forEach { error ->
                Surface(
                    color = Color(0xFF2D2D2D),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFFFB74D),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRecheck,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-check Readiness", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CopilotApp(viewModel: GeminiViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partialText by viewModel.partialText.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    // Developer log toggle (5-tap on the top-left corner)
    var tapCount by remember { mutableIntStateOf(0) }
    var showLogs by remember { mutableStateOf(false) }
    LaunchedEffect(tapCount) {
        if (tapCount >= 5) {
            showLogs = !showLogs
            tapCount = 0
        }
    }

    // Background color subtly shifts with state for peripheral visibility
    val bgColor = when (uiState.aiState) {
        AiState.IDLE, AiState.LISTENING -> Color(0xFF0A1F0A) // very dark green tint
        AiState.THINKING -> Color(0xFF1F1F0A) // very dark yellow tint
        AiState.WORKING, AiState.SPEAKING -> Color(0xFF0A0A1F) // very dark blue tint
        AiState.OFFLINE -> Color(0xFF1F0A0A) // very dark red tint
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hidden tap area for developer logs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { tapCount++ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Large State Indicator
            StateIndicator(uiState.aiState, uiState.currentTool)

            Spacer(modifier = Modifier.height(24.dp))

            // Partial STT text — lets driver know they're being heard
            if (partialText.isNotBlank() && uiState.aiState == AiState.LISTENING) {
                Text(
                    text = partialText,
                    color = Color(0xFF81C784),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // User / Gemini text summary
            if (uiState.userText.isNotBlank()) {
                Text(
                    text = "You: ${uiState.userText}",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (uiState.geminiText.isNotBlank() && uiState.aiState == AiState.SPEAKING) {
                Text(
                    text = "Copilot: ${uiState.geminiText.take(120)}...",
                    color = Color(0xFF64B5F6),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Error display
            if (uiState.lastError.isNotBlank()) {
                Text(
                    text = uiState.lastError,
                    color = Color(0xFFFF5252),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Debug log console (hidden by default)
            if (showLogs) {
                LogConsole(logs = logs)
            }

            // Bottom status bar
            StatusBar(uiState)
        }
    }
}

@Composable
fun StateIndicator(state: AiState, currentTool: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val color = when (state) {
        AiState.IDLE -> Color(0xFF4CAF50)
        AiState.LISTENING -> Color(0xFF00E676)
        AiState.THINKING -> Color(0xFFFFC107)
        AiState.WORKING -> Color(0xFF2196F3)
        AiState.SPEAKING -> Color(0xFF42A5F5)
        AiState.OFFLINE -> Color(0xFFF44336)
    }

    val icon = when (state) {
        AiState.IDLE, AiState.LISTENING -> Icons.Default.Mic
        AiState.THINKING -> Icons.Default.Settings
        AiState.WORKING -> Icons.Default.Settings
        AiState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
        AiState.OFFLINE -> Icons.Default.Warning
    }

    val label = when (state) {
        AiState.IDLE -> "READY"
        AiState.LISTENING -> "LISTENING..."
        AiState.THINKING -> "THINKING..."
        AiState.WORKING -> if (currentTool.isNotEmpty()) currentTool.replace(Regex("([A-Z])"), " $1").uppercase()
        else "CHECKING DATA..."
        AiState.SPEAKING -> "SPEAKING..."
        AiState.OFFLINE -> "OFFLINE"
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
        targetValue = if (state == AiState.WORKING || state == AiState.THINKING) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusBar(uiState: trucker.geminilive.controller.CopilotUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = uiState.status,
            style = MaterialTheme.typography.labelLarge,
            color = if (uiState.aiState == AiState.OFFLINE) Color(0xFFFF5252) else Color(0xFF81C784)
        )
        if (uiState.currentTool.isNotEmpty()) {
            Text(
                text = uiState.currentTool,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64B5F6)
            )
        }
    }
}

@Composable
fun LogConsole(logs: List<String>) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Black
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(
                count = logs.size,
                itemContent = { index ->
                    Text(
                        text = logs[index],
                        color = Color(0xFF00FF00),
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                }
            )
        }
    }
}
