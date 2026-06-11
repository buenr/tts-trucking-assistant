package trucker.geminiflash.audio

/**
 * Defines silence timeouts based on the ambient noise level.
 * Trucks have high floor noise, requiring longer silence detection windows.
 */
enum class NoiseProfile(val label: String, val silenceMs: Long) {
    QUIET("Quiet", 800L),
    NORMAL("Normal", 1200L),
    LOUD_TRUCK("Loud Truck", 2500L),
    CUSTOM("Custom", 2500L)
}
