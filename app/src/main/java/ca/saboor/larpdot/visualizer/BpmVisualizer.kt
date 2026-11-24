package ca.saboor.larpdot.visualizer

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Lightweight, zero-battery BPM-synchronized visualizer.
 *
 * Characteristics:
 * - Real-Time Beat Synchronization: Driven by the song's actual tempo queried from Deezer API.
 * - Distinct Band Ceilings & Envelopes: Each bar has a unique instrument envelope (Kick drum on downbeats,
 *   Snare on backbeats, Hi-Hats on subdivisions) with staggered musical weights so bars are never identical.
 * - Per-Band Frequency Ballistics: Bass bands hold decay longer, Treble bands decay snappy and jittery.
 * - Hardware vsync driven with zero allocations inside DrawScope.
 */
@Composable
fun BpmVisualizer(
    bpm: Float?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 2.4.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 15.dp,
    minHeight: Dp = 2.8.dp,
    secondaryColor: Color = accentColor,
) = BpmVisualizer(
    bpm = bpm,
    positionMsProvider = { currentPositionMs },
    isPlaying = isPlaying,
    accentColor = accentColor,
    modifier = modifier,
    barCount = barCount,
    barWidth = barWidth,
    barSpacing = barSpacing,
    maxHeight = maxHeight,
    minHeight = minHeight,
    secondaryColor = secondaryColor,
)

/**
 * Lambda-backed variant that reads playback position inside the draw phase so callers
 * do not recompose on every position tick.
 */
@Composable
fun BpmVisualizer(
    bpm: Float?,
    positionMsProvider: () -> Long,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 2.4.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 15.dp,
    minHeight: Dp = 2.8.dp,
    secondaryColor: Color = accentColor,
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }
    val heightSpanPx = maxHeightPx - minHeightPx
    val latestPositionProvider by rememberUpdatedState(positionMsProvider)

    val hasResolvedBpm = bpm != null && bpm > 0f
    val isBpmActive = isPlaying && hasResolvedBpm

    // Staggered acoustic musical ceilings & playback springs
    val weights = remember(barCount) { acousticBandWeights(barCount) }
    val playback = rememberVisualizerPlaybackStates(isBpmActive, labelPrefix = "bpm")

    // Hardware frame clock
    var vsyncClock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isBpmActive) {
        while (isBpmActive) {
            withFrameNanos { nanos -> vsyncClock = nanos }
        }
    }

    // [0] = lastReportedPos, [1] = lastReportedTime; updated strictly inside DrawScope.
    val anchorPosAndTime = remember { longArrayOf(-1L, 0L) }

    // Ballistic smoothing buffer dynamically sized to barCount
    val safeCount = barCount.coerceAtLeast(1)
    val ballisticHeights = remember(safeCount) { FloatArray(safeCount) }
    val cornerRadiusPx = barWidthPx / 2f

    val safeBpm = (bpm ?: 0f).toDouble().coerceIn(60.0, 240.0)
    val beatDurationMs = 60_000.0 / safeBpm

    val totalWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
    WaveformCanvas(modifier, accentColor, secondaryColor, totalWidth) { gradient ->
        val _tick = vsyncClock
        val playProgress = playback.playProgress.value
        val playAlpha = playback.playAlpha.value

        val now = SystemClock.elapsedRealtime()
        val livePos = latestPositionProvider()
        if (anchorPosAndTime[0] < 0L) {
            anchorPosAndTime[0] = livePos
            anchorPosAndTime[1] = now
        } else {
            val expectedAnchorMs = anchorPosAndTime[0] + if (isPlaying) (now - anchorPosAndTime[1]).coerceAtLeast(0L) else 0L
            if (abs(livePos - expectedAnchorMs) > 600L) {
                anchorPosAndTime[0] = livePos
                anchorPosAndTime[1] = now
            }
        }
        val elapsed = if (isPlaying) (now - anchorPosAndTime[1]).coerceAtLeast(0L) else 0L
        val exactMs = (anchorPosAndTime[0] + elapsed).coerceAtLeast(0L)

        val currentBeatExact = exactMs.toDouble() / beatDurationMs
        val beatIndex = currentBeatExact.toInt()
        val beatPhase = (currentBeatExact - beatIndex).coerceIn(0.0, 1.0)
        val measureBeat = ((beatIndex % 4) + 4) % 4 // 4/4 measure beats 0, 1, 2, 3

        // Acoustic musical drum envelopes:
        // 1. Kick drum strikes on downbeat 0 and beat 2
        val kickAccent = if (measureBeat == 0 || measureBeat == 2) 1.0 else 0.20
        val kickEnv = kickAccent * exp(-6.5 * beatPhase)

        // 2. Snare / Clap strikes on backbeats 1 and 3
        val snareAccent = if (measureBeat == 1 || measureBeat == 3) 1.0 else 0.18
        val snareEnv = snareAccent * exp(-5.0 * beatPhase)

        // 3. Hi-Hats tick on eighth-notes
        val hatPhase = (beatPhase * 2.0) % 1.0
        val hatEnv = exp(-9.0 * hatPhase)

        // 4. Subtle musical melody breathing across full 4-beat bar
        val barMeasurePhase = (currentBeatExact / 4.0) * 2.0 * PI
        val melodySway = (sin(barMeasurePhase) * 0.15 + 0.15).coerceIn(0.0, 0.3)

        var currentX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normalizedCol = if (barCount > 1) i.toFloat() / (barCount - 1) else 0.5f

            // Distinct instrument assignment per bar
            val rawTarget = if (barCount <= 7) {
                when (i) {
                    0 -> (kickEnv * 0.96 + 0.04).toFloat()
                    1 -> (kickEnv * 0.40 + melodySway * 0.80 + 0.05).toFloat()
                    2 -> (snareEnv * 0.96 + 0.04).toFloat()
                    3 -> (snareEnv * 0.35 + hatEnv * 0.55 + 0.05).toFloat()
                    4 -> (hatEnv * 0.94 + 0.06).toFloat()
                    5 -> (hatEnv * 0.70 + 0.05).toFloat()
                    else -> (hatEnv * 0.50 + 0.05).toFloat()
                }
            } else {
                when {
                    normalizedCol < 0.25f -> (kickEnv * (0.95 - normalizedCol * 1.5) + melodySway * 0.25 + 0.04).toFloat()
                    normalizedCol < 0.60f -> (snareEnv * 0.85 + melodySway * 0.45 + hatEnv * 0.25 + 0.05).toFloat()
                    else -> (hatEnv * (0.92 - (normalizedCol - 0.60) * 0.50) + 0.05).toFloat()
                }
            }.coerceIn(0.0f, 1.0f)

            val w = weights.getOrElse(i) { 1.0f }
            val targetHeight = (rawTarget * w).coerceIn(0.0f, 1.0f)
            val filteredHeight = stepBandBallistics(ballisticHeights[i], targetHeight, normalizedCol)
            ballisticHeights[i] = filteredHeight

            val activeHeightPx = minHeightPx + (heightSpanPx * filteredHeight)
            val barH = (minHeightPx + (activeHeightPx - minHeightPx) * playProgress).coerceIn(minHeightPx, maxHeightPx)

            drawRoundRect(
                brush = gradient,
                alpha = playAlpha,
                topLeft = Offset(currentX, centerY - (barH / 2f)),
                size = Size(barWidthPx, barH),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
            )
            currentX += barWidthPx + barSpacingPx
        }
    }
}

@Composable
fun BpmVisualizer(
    bpm: Int?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 2.4.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 15.dp,
    minHeight: Dp = 2.8.dp,
    secondaryColor: Color = accentColor,
) = BpmVisualizer(
    bpm = bpm?.toFloat(),
    currentPositionMs = currentPositionMs,
    isPlaying = isPlaying,
    accentColor = accentColor,
    modifier = modifier,
    barCount = barCount,
    barWidth = barWidth,
    barSpacing = barSpacing,
    maxHeight = maxHeight,
    minHeight = minHeight,
    secondaryColor = secondaryColor,
)

/**
 * Remembers a real-time 0f..1f beat pulse state synchronized to the track's BPM.
 * At the onset of each musical beat, the pulse surges to 1.0 and smoothly decays.
 * Returns 0f when paused, disabled, or when BPM is unresolved (null / <= 0).
 */
@Composable
fun rememberBpmPulse(
    bpm: Float?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    enabled: Boolean = true,
): State<Float> = rememberBpmPulse(
    bpm = bpm,
    positionMsProvider = { currentPositionMs },
    isPlaying = isPlaying,
    enabled = enabled,
)

/** Lambda-backed variant that reads live position inside frame callbacks without recomposing the caller. */
@Composable
fun rememberBpmPulse(
    bpm: Float?,
    positionMsProvider: () -> Long,
    isPlaying: Boolean,
    enabled: Boolean = true,
): State<Float> {
    val pulseState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, enabled, bpm) {
        if (!isPlaying || !enabled || bpm == null || bpm <= 0f) {
            pulseState.floatValue = 0f
            return@LaunchedEffect
        }
        var lastReportedPos = positionMsProvider()
        var lastReportedTime = SystemClock.elapsedRealtime()

        val safeBpm = bpm.toDouble().coerceIn(60.0, 240.0)
        val beatDurationMs = 60_000.0 / safeBpm

        while (isActive && isPlaying && enabled) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
                val livePos = positionMsProvider()
                val expectedAnchorMs = lastReportedPos + (now - lastReportedTime).coerceAtLeast(0L)
                if (abs(livePos - expectedAnchorMs) > 600L) {
                    lastReportedPos = livePos
                    lastReportedTime = now
                }
                val elapsed = (now - lastReportedTime).coerceAtLeast(0L)
                val exactMs = (lastReportedPos + elapsed).coerceAtLeast(0L)

                val currentBeatExact = exactMs.toDouble() / beatDurationMs
                val beatIndex = currentBeatExact.toInt()
                val beatPhase = (currentBeatExact - beatIndex).coerceIn(0.0, 1.0)
                val measureBeat = ((beatIndex % 4) + 4) % 4

                // Downbeats get full punch, other beats keep a solid musical pulse
                val beatAccent = when (measureBeat) {
                    0 -> 1.0
                    2 -> 0.92
                    else -> 0.84
                }
                pulseState.floatValue = (beatAccent * exp(-5.0 * beatPhase)).toFloat().coerceIn(0f, 1f)
            }
        }
        pulseState.floatValue = 0f
    }

    return pulseState
}

/**
 * Elegant, compact BPM Pill badge for display in expanded music player cards.
 * Renders a tempo icon and the track's verified BPM.
 * Automatically hides when BPM is unresolved (null or <= 0).
 */
@Composable
fun BpmChip(
    bpm: Float?,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.16f),
    contentColor: Color = Color.White.copy(alpha = 0.95f),
) {
    if (bpm == null || bpm <= 0f) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = LarpIcons.Speed,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.88f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${bpm.toInt()} BPM",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}

