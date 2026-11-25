package ca.saboor.larpdot.flashlight

import java.util.Locale

enum class FlashlightMode(val label: String) {
    STEADY("Steady"),
    STROBE("Strobe"),
    SOS("SOS"),
}

internal data class FlashlightPatternStep(
    val torchOn: Boolean,
    val durationMs: Long,
)

/** Returns the repeating on/off timing sequence for [mode], or empty for steady illumination. */
internal fun flashlightPatternSteps(mode: FlashlightMode): List<FlashlightPatternStep> = when (mode) {
    FlashlightMode.STEADY -> emptyList()
    FlashlightMode.STROBE -> listOf(
        FlashlightPatternStep(torchOn = true, durationMs = 110L),
        FlashlightPatternStep(torchOn = false, durationMs = 110L),
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
