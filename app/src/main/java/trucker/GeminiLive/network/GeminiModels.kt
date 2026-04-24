package trucker.geminilive.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ============================================
// Shared Models (used by TruckingTools)
// ============================================

@Serializable
data class FunctionDeclaration(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: Schema? = null
)

@Serializable
data class Schema(
    val type: String,
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
    val items: Schema? = null,
    val description: String? = null
)

// ============================================
// UI State
// ============================================

enum class GeminiState(val label: String) {
    LISTENING("Listening..."),
    CHECKING_DATA("Checking Data..."),
    SPEAKING("Speaking...")
}

