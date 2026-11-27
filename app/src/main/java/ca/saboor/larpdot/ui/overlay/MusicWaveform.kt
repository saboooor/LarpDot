package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaPlaybackState
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

@Composable
internal fun FittedEqualizerWaveform(
    mediaInfo: MediaTrackInfo,
    maxHeightDp: Float,
    barCount: Int,
    barWidth: Float,
    barSpacing: Float,
    visualizerAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 2.8.dp,
    barCornerRadius: Dp = Dp.Infinity,
    contentAlignment: Alignment = Alignment.Center,
) {
    val naturalWidth = compactVisualizerWidthDp(true, barCount, barWidth, barSpacing)
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        if (naturalWidth <= 0f || maxWidth <= 0.dp) return@BoxWithConstraints
        val fit = (maxWidth.value / naturalWidth).coerceAtMost(1f)
        val renderedBarWidth = (barWidth * fit).dp
        EqualizerWaveform(
            mediaInfo = mediaInfo,
            maxHeightDp = maxHeightDp,
            barCount = barCount,
            barWidth = renderedBarWidth,
            barSpacing = (barSpacing * fit).dp,
            minHeight = minHeight,
            barCornerRadius = minOf(barCornerRadius, renderedBarWidth / 2f),
            visualizerAnchor = visualizerAnchor,
        )
    }
}

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
    visualizerAnchor: IslandElementAnchor? = null,
) {
    if (barCount <= 0) return

    val scheme = MaterialTheme.colorScheme
    val accentColor = if (mediaInfo.albumArt == null) scheme.primary else mediaInfo.artworkColors.primary
    val secondaryColor = if (mediaInfo.albumArt == null) scheme.tertiary else mediaInfo.artworkColors.secondary
    val gradientStart by animateColorAsState(accentColor, LarpMotion.fade(260), label = "waveform_start")
    val gradientEnd by animateColorAsState(secondaryColor, LarpMotion.fade(260), label = "waveform_end")
    val totalWidth = barWidth * barCount + barSpacing * (barCount - 1)
    val maxHeight = maxHeightDp.dp
    val waveformModifier = modifier.width(totalWidth).height(maxHeight).morphingElement(visualizerAnchor)

    val visualizerMode by OverlayPreferences.visualizerModeFlow.collectAsState()
    val realAmplitudes by AudioPreviewExtractor.currentAmplitudes.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isPreviewPlaying by PreviewAudioPlayer.isPlaying.collectAsState()

    val effectiveIsPlaying = isPreviewPlaying || mediaInfo.isPlaying

    if (visualizerMode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO) {
        LiveTrackVisualizer(
            amplitudesProvider = { LiveAudioVisualizer.liveAmplitudes.value },
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
            positionMsProvider = {
                if (isPreviewPlaying) PreviewAudioPlayer.currentPositionMs.value
                else if (mediaInfo.hasMedia) MediaPlaybackState.positionMs.value
                else mediaInfo.positionMs
            },
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
            positionMsProvider = {
                if (isPreviewPlaying) PreviewAudioPlayer.currentPositionMs.value
                else if (mediaInfo.hasMedia) MediaPlaybackState.positionMs.value
                else mediaInfo.positionMs
            },
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

    // Soft opacity transition: vibrant full color during playback, gently subdued when paused.
    // Kept as State<Float> and read strictly inside WaveformCanvas draw phase to avoid per-frame recompositions.
    val playAlphaState = animateFloatAsState(
        targetValue = if (effectiveIsPlaying) 1.0f else 0.50f,
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "eq_alpha",
    )

    val normalizedBarCount = barCount.coerceAtLeast(0)
    val restingFraction = (minHeight.value / maxHeightDp).coerceIn(0.10f, 0.35f)
    val animatedFractionStates = (0 until normalizedBarCount).map { index ->
        val initialFraction = (0.18f + (index * 0.037f % 0.18f)).coerceIn(0.18f, 0.35f)
        val targetFraction = (0.56f + ((index * 0.173f) % 0.44f)).coerceIn(0.56f, 1f)
        infiniteTransition?.animateFloat(
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
        )
    }
    val playProgressStates = (0 until normalizedBarCount).map { index ->
        val normalizedIndex = index.toFloat() / normalizedBarCount.coerceAtLeast(2).toFloat()
        animateFloatAsState(
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
    }

    val widthPx = with(LocalDensity.current) { totalWidth.toPx() }
    WaveformCanvas(waveformModifier, gradientStart, gradientEnd, widthPx) { gradient ->
        val barWidthPx = barWidth.toPx()
        val stepPx = barWidthPx + barSpacing.toPx()
        val left = (size.width - widthPx) / 2f
        val playAlpha = playAlphaState.value
        val cornerRadiusPx = barCornerRadius.toPx()
        for (index in 0 until normalizedBarCount) {
            val animatedFraction = animatedFractionStates[index]?.value ?: restingFraction
            val playProgress = playProgressStates[index].value.coerceAtLeast(0f)
            val fraction = restingFraction + (animatedFraction - restingFraction) * playProgress
            val heightPx = (maxHeightDp * fraction).dp.coerceAtLeast(minHeight).toPx()
            drawRoundRect(
                brush = gradient,
                alpha = playAlpha,
                topLeft = Offset(left + index * stepPx, (size.height - heightPx) / 2f),
                size = Size(barWidthPx, heightPx),
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }
    }
}
