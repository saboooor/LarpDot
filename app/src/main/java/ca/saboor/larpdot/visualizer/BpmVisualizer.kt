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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
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
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }
    val heightSpanPx = maxHeightPx - minHeightPx

    val hasResolvedBpm = bpm != null && bpm > 0f
    val isBpmActive = isPlaying && hasResolvedBpm

    // Staggered acoustic musical ceilings
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
        targetValue = if (isBpmActive) 1f else 0f,
        animationSpec = if (isBpmActive) {
            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "bpm_play_spring",
    )

    val playAlpha by animateFloatAsState(
        targetValue = if (isBpmActive) 1.0f else 0.45f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "bpm_alpha",
    )

    // Hardware frame clock
    var vsyncClock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isBpmActive) {
        while (isBpmActive) {
            withFrameNanos { nanos -> vsyncClock = nanos }
        }
    }

    var lastReportedPos by remember { mutableLongStateOf(currentPositionMs) }
    var lastReportedTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val nowAnchor = SystemClock.elapsedRealtime()
    val expectedAnchorMs = lastReportedPos + if (isPlaying) (nowAnchor - lastReportedTime) else 0L
    if (abs(currentPositionMs - expectedAnchorMs) > 600L) {
        lastReportedPos = currentPositionMs
        lastReportedTime = nowAnchor
    }

    // Ballistic smoothing buffer dynamically sized to barCount
    val safeCount = barCount.coerceAtLeast(1)
    val ballisticHeights = remember(safeCount) { FloatArray(safeCount) }
    val cornerRadiusPx = barWidthPx / 2f

    val safeBpm = (bpm ?: 0f).toDouble().coerceIn(60.0, 240.0)
    val beatDurationMs = 60_000.0 / safeBpm

    Canvas(modifier = modifier) {
        val _tick = vsyncClock

        val now = SystemClock.elapsedRealtime()
        val elapsed = if (isPlaying) (now - lastReportedTime).coerceAtLeast(0L) else 0L
        val exactMs = (lastReportedPos + elapsed).coerceAtLeast(0L)

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

        val totalWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
        var currentX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        val drawColor = accentColor.copy(alpha = playAlpha)

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

            val prevHeight = ballisticHeights[i]
            val attackRate = 0.80f + (0.12f * normalizedCol) // 0.80 -> 0.92
            val decayRate = 0.13f + (0.18f * normalizedCol)  // 0.13 (bass) -> 0.31 (treble)

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
): State<Float> {
    val pulseState = remember { mutableFloatStateOf(0f) }

    var lastReportedPos by remember { mutableLongStateOf(currentPositionMs) }
    var lastReportedTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val nowAnchor = SystemClock.elapsedRealtime()
    val expectedAnchorMs = lastReportedPos + if (isPlaying) (nowAnchor - lastReportedTime) else 0L
    if (abs(currentPositionMs - expectedAnchorMs) > 600L) {
        lastReportedPos = currentPositionMs
        lastReportedTime = nowAnchor
    }

    LaunchedEffect(isPlaying, enabled, bpm) {
        if (!isPlaying || !enabled || bpm == null || bpm <= 0f) {
            pulseState.floatValue = 0f
            return@LaunchedEffect
        }
        lastReportedPos = currentPositionMs
        lastReportedTime = SystemClock.elapsedRealtime()

        val safeBpm = bpm.toDouble().coerceIn(60.0, 240.0)
        val beatDurationMs = 60_000.0 / safeBpm

        while (isActive && isPlaying && enabled) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
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
                imageVector = Icons.Default.Speed,
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

