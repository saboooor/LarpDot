package ca.saboor.larpdot.visualizer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.PI
import kotlin.math.sin

internal data class VisualizerPlaybackStates(
    val playProgress: State<Float>,
    val playAlpha: State<Float>,
)

/** Staggered acoustic musical ceilings shared by BPM, preview, and live visualizers. */
internal fun acousticBandWeights(barCount: Int): FloatArray = when (barCount) {
    3 -> floatArrayOf(1.00f, 0.80f, 0.58f)
    4 -> floatArrayOf(1.00f, 0.74f, 0.92f, 0.58f)
    5 -> floatArrayOf(1.00f, 0.72f, 0.96f, 0.68f, 0.52f)
    6 -> floatArrayOf(1.00f, 0.70f, 0.92f, 0.76f, 0.62f, 0.48f)
    7 -> floatArrayOf(1.00f, 0.68f, 0.88f, 0.96f, 0.74f, 0.60f, 0.46f)
    else -> {
        val count = barCount.coerceAtLeast(1)
        FloatArray(count) { idx ->
            val norm = if (count > 1) idx.toFloat() / (count - 1) else 0.5f
            (1.0f - norm * 0.46f + sin(norm * PI.toFloat()) * 0.16f).coerceIn(0.44f, 1.0f)
        }
    }
}

/** Per-band ballistic smoothing: bass bands hold decay longer, treble bands snap and decay faster. */
internal fun stepBandBallistics(
    prevHeight: Float,
    targetHeight: Float,
    normalizedBandPos: Float,
): Float {
    val attackRate = 0.80f + (0.12f * normalizedBandPos)
    val decayRate = 0.13f + (0.18f * normalizedBandPos)
    val rate = if (targetHeight > prevHeight) attackRate else decayRate
    return prevHeight + (targetHeight - prevHeight) * rate
}

@Composable
internal fun rememberVisualizerPlaybackStates(
    isActive: Boolean,
    labelPrefix: String,
): VisualizerPlaybackStates {
    val playProgress = animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (isActive) {
            LarpMotion.quickSpring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
        } else {
            LarpMotion.quickSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "${labelPrefix}_play_spring",
    )
    val playAlpha = animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.45f,
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "${labelPrefix}_alpha",
    )
    return VisualizerPlaybackStates(playProgress = playProgress, playAlpha = playAlpha)
}
