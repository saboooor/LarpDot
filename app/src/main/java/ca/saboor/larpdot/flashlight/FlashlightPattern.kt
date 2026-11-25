package ca.saboor.larpdot.flashlight

import java.util.Locale

enum class FlashlightMode(val label: String) {
    STEADY("Steady"),
    STROBE("Strobe"),
    SOS("SOS"),
}

enum class StrobeSpeed(val label: String, val stepMs: Long) {
    SLOW("Slow", 240L),
    NORMAL("Normal", 110L),
    FAST("Fast", 60L),
}

internal fun nextFlashlightMode(current: FlashlightMode): FlashlightMode = when (current) {
    FlashlightMode.STEADY -> FlashlightMode.STROBE
    FlashlightMode.STROBE -> FlashlightMode.SOS
    FlashlightMode.SOS -> FlashlightMode.STEADY
}

internal data class FlashlightPatternStep(
    val torchOn: Boolean,
    val durationMs: Long,
)

/** Returns the repeating on/off timing sequence for [mode], or empty for steady illumination. */
internal fun flashlightPatternSteps(
    mode: FlashlightMode,
    strobeSpeed: StrobeSpeed = StrobeSpeed.NORMAL,
): List<FlashlightPatternStep> = when (mode) {
    FlashlightMode.STEADY -> emptyList()
    FlashlightMode.STROBE -> listOf(
        FlashlightPatternStep(torchOn = true, durationMs = strobeSpeed.stepMs),
        FlashlightPatternStep(torchOn = false, durationMs = strobeSpeed.stepMs),
    )
    FlashlightMode.SOS -> {
        val unit = 180L
        val dot = FlashlightPatternStep(true, unit)
        val dash = FlashlightPatternStep(true, unit * 3)
        val intraGap = FlashlightPatternStep(false, unit)
        val letterGap = FlashlightPatternStep(false, unit * 3)
        val wordGap = FlashlightPatternStep(false, unit * 7)
        listOf(
            // S: ...
            dot, intraGap, dot, intraGap, dot, letterGap,
            // O: ---
            dash, intraGap, dash, intraGap, dash, letterGap,
            // S: ...
            dot, intraGap, dot, intraGap, dot, wordGap,
        )
    }
}

internal fun formatAutoOffRemaining(remainingMs: Long): String {
    val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
