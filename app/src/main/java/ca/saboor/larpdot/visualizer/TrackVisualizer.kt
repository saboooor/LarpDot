package ca.saboor.larpdot.visualizer

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance hardware-vsync multi-band audio visualizer.
 *
 * Characteristics:
 * - Dynamic Band Mapping: Each bar maps 1:1 to an acoustic frequency band.
 * - Spectral Contrast Enhancement: Emphasizes differences between bands so each bar
 *   stands out with distinct, dynamic heights rather than moving uniformly.
 * - Staggered Acoustic Ceiling: Shapes bands according to real acoustic equalizer profiles
 *   (e.g. Bass and Center Mids punch tall, Low Mids and Trebles flutter in lower registers).
 * - Per-Band Frequency Ballistics: Bass bands sustain longer, Treble bands decay rapidly.
 * - Smooth Play/Pause spring transitions down to compact resting pill dots.
 * - Zero allocations inside DrawScope.
 */
@Composable
fun TrackVisualizer(
    amplitudes: FloatArray,
    currentPositionMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    fps: Int = 60,
    barCount: Int = 5,
    barWidth: Dp = 2.4.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 15.dp,
    minHeight: Dp = 2.8.dp,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }
    val heightSpanPx = maxHeightPx - minHeightPx

    // Staggered musical equalizer ceilings for any number of bands
    val weights = remember(barCount) {
        when (barCount) {
            3 -> floatArrayOf(1.00f, 0.80f, 0.58f)
            4 -> floatArrayOf(1.00f, 0.74f, 0.92f, 0.58f)
            5 -> floatArrayOf(1.00f, 0.72f, 0.96f, 0.68f, 0.52f)
            6 -> floatArrayOf(1.00f, 0.70f, 0.92f, 0.76f, 0.62f, 0.48f)
            7 -> floatArrayOf(1.00f, 0.68f, 0.88f, 0.96f, 0.74f, 0.60f, 0.46f)
            else -> {
                val count = barCount.coerceAtLeast(1)
                FloatArray(count) { idx ->
                    val norm = if (count > 1) idx.toFloat() / (count - 1) else 0.5f
                    (1.0f - norm * 0.46f + kotlin.math.sin(norm * Math.PI.toFloat()) * 0.16f).coerceIn(0.44f, 1.0f)
                }
            }
        }
    }

    // Playback state spring transition
    val playProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "tv_play_spring",
    )

    val playAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.45f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "tv_alpha",
    )

    // Hardware frame clock
    var vsyncClock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            withFrameNanos { nanos -> vsyncClock = nanos }
        }
    }

    var lastReportedPos by remember { mutableLongStateOf(currentPositionMs) }
    var lastReportedTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    if (currentPositionMs != lastReportedPos) {
        lastReportedPos = currentPositionMs
        lastReportedTime = SystemClock.elapsedRealtime()
    }

    // Dynamic ballistic smoothing buffer sized to barCount
    val safeCount = barCount.coerceAtLeast(1)
    val ballisticHeights = remember(safeCount) { FloatArray(safeCount) }
    val frameAmps = remember(safeCount) { FloatArray(safeCount) }
    val cornerRadiusPx = barWidthPx / 2f

    Canvas(modifier = modifier) {
        // Subscribe to display refresh loop
        val _tick = vsyncClock

        val bandsInArray = if (barCount > 0 && amplitudes.isNotEmpty() && amplitudes.size % barCount == 0) {
            barCount
        } else {
            (3..32).firstOrNull { amplitudes.size % it == 0 } ?: 5
        }
        val totalFrames = amplitudes.size / bandsInArray

        if (totalFrames <= 0) {
            // Resting state: Draw resting pill dots
            val totalWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
            var currentX = (size.width - totalWidth) / 2f
            val centerY = size.height / 2f
            val dotColor = accentColor.copy(alpha = playAlpha)

            for (i in 0 until barCount) {
                drawRoundRect(
                    color = dotColor,
                    topLeft = Offset(currentX, centerY - (minHeightPx / 2f)),
                    size = Size(barWidthPx, minHeightPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
                currentX += barWidthPx + barSpacingPx
            }
            return@Canvas
        }

        // Exact playback time interpolated from hardware clock
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (isPlaying) (now - lastReportedTime).coerceIn(0L, 1000L) else 0L
        val exactMs = (lastReportedPos + elapsed).coerceAtLeast(0L)

        // Deterministic frame calculation
        val exactFrame = (exactMs.toFloat() / 1000f) * fps.toFloat()
        val baseIndex = exactFrame.toInt()
        val frameFraction = (exactFrame - baseIndex).coerceIn(0f, 1f)

        val f0 = ((baseIndex % totalFrames) + totalFrames) % totalFrames
        val f1 = (f0 + 1) % totalFrames

        // Step 1: Extract all frequency bands for this frame and compute the frame mean
        var frameSum = 0f
        for (i in 0 until barCount) {
            val targetBand = if (bandsInArray == barCount) {
                i
            } else {
                ((i.toFloat() / barCount.coerceAtLeast(1)) * bandsInArray).toInt().coerceIn(0, bandsInArray - 1)
            }
            val raw0 = amplitudes.getOrElse((f0 * bandsInArray) + targetBand) { 0.05f }
            val raw1 = amplitudes.getOrElse((f1 * bandsInArray) + targetBand) { 0.05f }
            val amp = (raw0 + (raw1 - raw0) * frameFraction).coerceIn(0.0f, 1.0f)
            frameAmps[i] = amp
            frameSum += amp
        }
        val frameMean = frameSum / barCount.coerceAtLeast(1)

        val totalWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
        var currentX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        val drawColor = accentColor.copy(alpha = playAlpha)

        // Step 2: Render each bar with spectral contrast and per-band ballistics
        for (i in 0 until barCount) {
            val amp = frameAmps[i]
            // Spectral contrast enhancement: exaggerates differences between frequencies
            val diff = amp - frameMean
            val contrasted = (amp + (diff * 1.10f)).coerceIn(0.04f, 1.0f)

            // Dynamic acoustic ceiling weighting: ensures bars sit at distinctly different heights
            val w = weights.getOrElse(i) { 1.0f }
            val targetHeight = (contrasted * w).coerceIn(0.0f, 1.0f)

            val normalizedPos = if (barCount > 1) i.toFloat() / (barCount - 1) else 0.5f

            // Distinct per-band ballistics:
            // Bass bands (left) sustain longer, Treble bands (right) decay rapidly with transient jitter
            val prevHeight = ballisticHeights[i]
            val attackRate = 0.80f + (0.12f * normalizedPos) // 0.80 -> 0.92
            val decayRate = 0.13f + (0.18f * normalizedPos)  // 0.13 (bass) -> 0.31 (treble)

            val filteredHeight = if (targetHeight > prevHeight) {
                prevHeight + (targetHeight - prevHeight) * attackRate
            } else {
                prevHeight + (targetHeight - prevHeight) * decayRate
            }
            ballisticHeights[i] = filteredHeight

            val activeHeightPx = minHeightPx + (heightSpanPx * filteredHeight)
            val barH = (minHeightPx + (activeHeightPx - minHeightPx) * playProgress).coerceIn(minHeightPx, maxHeightPx)

            drawRoundRect(
                color = drawColor,
                topLeft = Offset(currentX, centerY - (barH / 2f)),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
            currentX += barWidthPx + barSpacingPx
        }
    }
}

/**
 * Real-time dynamic visualizer driven directly by live hardware audio capture (Session 0).
 */
@Composable
fun LiveTrackVisualizer(
    amplitudes: FloatArray?,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 2.4.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 15.dp,
    minHeight: Dp = 2.8.dp,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }
    val heightSpanPx = maxHeightPx - minHeightPx

    val weights = remember(barCount) {
        when (barCount) {
            3 -> floatArrayOf(1.00f, 0.80f, 0.58f)
            4 -> floatArrayOf(1.00f, 0.74f, 0.92f, 0.58f)
            5 -> floatArrayOf(1.00f, 0.72f, 0.96f, 0.68f, 0.52f)
            6 -> floatArrayOf(1.00f, 0.70f, 0.92f, 0.76f, 0.62f, 0.48f)
            7 -> floatArrayOf(1.00f, 0.68f, 0.88f, 0.96f, 0.74f, 0.60f, 0.46f)
            else -> {
                val count = barCount.coerceAtLeast(1)
                FloatArray(count) { idx ->
                    val norm = if (count > 1) idx.toFloat() / (count - 1) else 0.5f
                    (1.0f - norm * 0.46f + kotlin.math.sin(norm * Math.PI.toFloat()) * 0.16f).coerceIn(0.44f, 1.0f)
                }
            }
        }
    }

    val playProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "live_play_spring",
    )

    val playAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.45f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "live_alpha",
    )

    var vsyncClock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            withFrameNanos { nanos -> vsyncClock = nanos }
        }
    }

    val safeCount = barCount.coerceAtLeast(1)
    val ballisticHeights = remember(safeCount) { FloatArray(safeCount) }
    val cornerRadiusPx = barWidthPx / 2f

    Canvas(modifier = modifier) {
        val _tick = vsyncClock

        val totalWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
        var currentX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        val drawColor = accentColor.copy(alpha = playAlpha)

        val live = amplitudes
        if (live == null || live.isEmpty() || !isPlaying) {
            for (i in 0 until barCount) {
                drawRoundRect(
                    color = drawColor,
                    topLeft = Offset(currentX, centerY - (minHeightPx / 2f)),
                    size = Size(barWidthPx, minHeightPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
                currentX += barWidthPx + barSpacingPx
            }
            return@Canvas
        }

        var frameSum = 0f
        for (i in 0 until barCount) {
            frameSum += live.getOrElse(i) { 0.05f }
        }
        val frameMean = frameSum / barCount.coerceAtLeast(1)

        for (i in 0 until barCount) {
            val rawAmp = live.getOrElse(i) { 0.05f }
            val diff = rawAmp - frameMean
            val contrasted = (rawAmp + (diff * 1.10f)).coerceIn(0.04f, 1.0f)
            val w = weights.getOrElse(i) { 1.0f }
            val targetHeight = (contrasted * w).coerceIn(0.0f, 1.0f)

            val prevHeight = ballisticHeights[i]
            val normalizedPos = if (barCount > 1) i.toFloat() / (barCount - 1) else 0.5f
            val attackRate = 0.80f + (0.12f * normalizedPos)
            val decayRate = 0.13f + (0.18f * normalizedPos)

            val filteredHeight = if (targetHeight > prevHeight) {
                prevHeight + (targetHeight - prevHeight) * attackRate
            } else {
                prevHeight + (targetHeight - prevHeight) * decayRate
            }
            ballisticHeights[i] = filteredHeight

            val activeHeightPx = minHeightPx + (heightSpanPx * filteredHeight)
            val barH = (minHeightPx + (activeHeightPx - minHeightPx) * playProgress).coerceIn(minHeightPx, maxHeightPx)

            drawRoundRect(
                color = drawColor,
                topLeft = Offset(currentX, centerY - (barH / 2f)),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
            currentX += barWidthPx + barSpacingPx
        }
    }
}

