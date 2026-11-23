package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.BpmVisualizer
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import ca.saboor.larpdot.visualizer.TrackVisualizer
import ca.saboor.larpdot.visualizer.WaveformCanvas

/**
 * Animated 5-bar equalizer visualizer component modeled after com.pryshedko.mtisland.
 * Each bar oscillates smoothly with organic sinusoidal harmonics when audio is playing,
 * and gently settles to resting dots when paused.
 */
@Composable
internal fun EqualizerWaveform(
    mediaInfo: MediaTrackInfo,
    maxHeightDp: Float,
    modifier: Modifier = Modifier,
    barCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT,
    barWidth: Dp = 2.2.dp,
    barSpacing: Dp = 2.dp,
    minHeight: Dp = 2.8.dp,
    barCornerRadius: Dp = barWidth / 2f,
) {
    if (barCount <= 0) return

    val scheme = MaterialTheme.colorScheme
    val accentColor = if (mediaInfo.albumArt == null) scheme.primary else mediaInfo.artworkColors.primary
    val secondaryColor = if (mediaInfo.albumArt == null) scheme.tertiary else mediaInfo.artworkColors.secondary
    val gradientStart by animateColorAsState(accentColor, LarpMotion.fade(260), label = "waveform_start")
    val gradientEnd by animateColorAsState(secondaryColor, LarpMotion.fade(260), label = "waveform_end")
    val totalWidth = barWidth * barCount + barSpacing * (barCount - 1)
    val maxHeight = maxHeightDp.dp
    val waveformModifier = modifier.width(totalWidth).height(maxHeight)

    val visualizerMode by OverlayPreferences.visualizerModeFlow.collectAsState()
    val realAmplitudes by AudioPreviewExtractor.currentAmplitudes.collectAsState()
    val liveAmplitudes by LiveAudioVisualizer.liveAmplitudes.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isPreviewPlaying by PreviewAudioPlayer.isPlaying.collectAsState()
    val previewPositionMs by PreviewAudioPlayer.currentPositionMs.collectAsState()

    val effectiveIsPlaying = isPreviewPlaying || mediaInfo.isPlaying
    val effectivePositionMs = if (isPreviewPlaying) previewPositionMs else mediaInfo.positionMs

    if (visualizerMode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO) {
        LiveTrackVisualizer(
            amplitudes = liveAmplitudes,
            isPlaying = effectiveIsPlaying,
            accentColor = gradientStart,
            secondaryColor = gradientEnd,
            modifier = waveformModifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeight,
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
            accentColor = gradientStart,
            secondaryColor = gradientEnd,
            modifier = waveformModifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeight,
            minHeight = minHeight,
        )
        return
    }

    if (visualizerMode == OverlayPreferences.VisualizerMode.BPM) {
        BpmVisualizer(
            bpm = currentBpm,
            currentPositionMs = effectivePositionMs,
            isPlaying = effectiveIsPlaying,
            accentColor = gradientStart,
            secondaryColor = gradientEnd,
            modifier = waveformModifier,
            barCount = barCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            maxHeight = maxHeight,
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
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = FastOutSlowInEasing),
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
                    animation = LarpMotion.timing(
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
                LarpMotion.quickSpring(
                    dampingRatio = 0.58f + (normalizedIndex * 0.12f),
                    stiffness = Spring.StiffnessMediumLow,
                )
            } else {
                LarpMotion.quickSpring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            },
            label = "play_$index",
        )
        restingFraction + (animatedFraction - restingFraction) * playProgress.coerceAtLeast(0f)
    }

    val widthPx = with(LocalDensity.current) { totalWidth.toPx() }
    WaveformCanvas(waveformModifier, gradientStart, gradientEnd, widthPx) { gradient ->
        val barWidthPx = barWidth.toPx()
        val stepPx = barWidthPx + barSpacing.toPx()
        val left = (size.width - widthPx) / 2f
        heights.forEachIndexed { index, fraction ->
            val heightPx = (maxHeightDp * fraction).dp.coerceAtLeast(minHeight).toPx()
            drawRoundRect(
                brush = gradient,
                alpha = playAlpha,
                topLeft = Offset(left + index * stepPx, (size.height - heightPx) / 2f),
                size = Size(barWidthPx, heightPx),
                cornerRadius = CornerRadius(barCornerRadius.toPx()),
            )
        }
    }
}
