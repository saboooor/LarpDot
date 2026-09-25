package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.BpmVisualizer
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import ca.saboor.larpdot.visualizer.TrackVisualizer

/**
 * Animated 5-bar equalizer visualizer component modeled after com.pryshedko.mtisland.
 * Each bar oscillates smoothly with organic sinusoidal harmonics when audio is playing,
 * and gently settles to resting dots when paused.
 */
@Composable
internal fun EqualizerWaveform(
    isPlaying: Boolean,
    maxHeightDp: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT,
    barWidth: Dp = 2.2.dp,
    barSpacing: Dp = 2.dp,
    minHeight: Dp = 2.8.dp,
    barCornerRadius: Dp = barWidth / 2f,
    currentPositionMs: Long = 0L,
) {
    if (barCount <= 0) return

    val visualizerMode by OverlayPreferences.visualizerModeFlow.collectAsState()
    val realAmplitudes by AudioPreviewExtractor.currentAmplitudes.collectAsState()
    val liveAmplitudes by LiveAudioVisualizer.liveAmplitudes.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isPreviewPlaying by PreviewAudioPlayer.isPlaying.collectAsState()
    val previewPositionMs by PreviewAudioPlayer.currentPositionMs.collectAsState()

    val effectiveIsPlaying = if (isPreviewPlaying) true else isPlaying
    val effectivePositionMs = if (isPreviewPlaying) previewPositionMs else currentPositionMs

    if (visualizerMode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO) {
        LiveTrackVisualizer(
            amplitudes = liveAmplitudes,
            isPlaying = effectiveIsPlaying,
            accentColor = accentColor,
            modifier = modifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeightDp.dp,
            minHeight = minHeight,
        )
        return
    }

    if ((isPreviewPlaying || visualizerMode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW) &&
        realAmplitudes != null && realAmplitudes!!.isNotEmpty()
    ) {
        TrackVisualizer(
            amplitudes = realAmplitudes!!,
            currentPositionMs = effectivePositionMs,
            isPlaying = effectiveIsPlaying,
            accentColor = accentColor,
            modifier = modifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeightDp.dp,
            minHeight = minHeight,
        )
        return
    }

    if (visualizerMode == OverlayPreferences.VisualizerMode.BPM) {
        BpmVisualizer(
            bpm = currentBpm,
            currentPositionMs = effectivePositionMs,
            isPlaying = effectiveIsPlaying,
            accentColor = accentColor,
            modifier = modifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeightDp.dp,
            minHeight = minHeight,
        )
        return
    }
    val infiniteTransition = if (effectiveIsPlaying) {
        rememberInfiniteTransition(label = "eq_transition")
    } else null

    // Soft opacity transition: vibrant full color during playback, gently subdued when paused
    val playAlpha by animateFloatAsState(
        targetValue = if (effectiveIsPlaying) 1.0f else 0.50f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "eq_alpha",
    )

    val normalizedBarCount = barCount.coerceAtLeast(0)
    val restingFraction = (minHeight.value / maxHeightDp).coerceIn(0.10f, 0.35f)
    val heights = (0 until normalizedBarCount).map { index ->
        val normalizedIndex = index.toFloat() / normalizedBarCount.coerceAtLeast(2).toFloat()
        val initialFraction = (0.18f + (index * 0.037f % 0.18f)).coerceIn(0.18f, 0.35f)
        val targetFraction = (0.56f + ((index * 0.173f) % 0.44f)).coerceIn(0.56f, 1f)
        val animatedFraction = if (infiniteTransition != null) {
            infiniteTransition.animateFloat(
                initialValue = initialFraction,
                targetValue = targetFraction,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 540 + ((index * 137) % 460),
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eq_$index",
            ).value
        } else restingFraction
        val playProgress by animateFloatAsState(
            targetValue = if (effectiveIsPlaying) 1f else 0f,
            animationSpec = if (effectiveIsPlaying) {
                spring(
                    dampingRatio = 0.58f + (normalizedIndex * 0.12f),
                    stiffness = Spring.StiffnessMediumLow,
                )
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            },
            label = "play_$index",
        )
        restingFraction + (animatedFraction - restingFraction) * playProgress.coerceAtLeast(0f)
    }

    val effectiveBarWidth = barWidth
    val effectiveBarSpacing = barSpacing
    val effectiveCornerRadius = if (barCornerRadius == barWidth / 2f) effectiveBarWidth / 2f else barCornerRadius

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(effectiveBarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(effectiveBarWidth)
                    .height((maxHeightDp * fraction).dp.coerceAtLeast(minHeight))
                    .clip(RoundedCornerShape(effectiveCornerRadius))
                    .background(accentColor.copy(alpha = playAlpha))
            )
        }
    }
}
