package ca.saboor.larpdot.service

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Maximum RGB channel value considered "pitch black" (catches OLED black #000000-#0E0E0E while excluding dark-mode grays). */
internal const val PITCH_BLACK_MAX_CHANNEL = 14

/** Minimum fraction of perimeter pixels that must be pitch black (tolerates status bar icons near the island). */
internal const val PITCH_BLACK_MIN_FRACTION = 0.72f

internal fun isPitchBlackPixel(argb: Int): Boolean {
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return r <= PITCH_BLACK_MAX_CHANNEL &&
        g <= PITCH_BLACK_MAX_CHANNEL &&
        b <= PITCH_BLACK_MAX_CHANNEL
}

internal fun isBackgroundPitchBlackFromSamples(pixelArgbs: List<Int>): Boolean {
    if (pixelArgbs.isEmpty()) return false
    var pitchBlackCount = 0
    for (argb in pixelArgbs) {
        if (isPitchBlackPixel(argb)) pitchBlackCount++
    }
    return pitchBlackCount.toFloat() / pixelArgbs.size >= PITCH_BLACK_MIN_FRACTION
}

internal fun resolveIslandOutlineVisible(
    mode: OverlayPreferences.IslandOutlineMode,
    isBackgroundPitchBlack: Boolean,
): Boolean = when (mode) {
    OverlayPreferences.IslandOutlineMode.OFF -> false
    OverlayPreferences.IslandOutlineMode.ON -> true
    OverlayPreferences.IslandOutlineMode.DYNAMIC -> isBackgroundPitchBlack
}

/**
 * Generates screen pixel coordinates in a thin ring just outside [islandBounds], skipping any
 * coordinates that fall inside [islandBounds] or [excludeBounds] (such as secondary bubbles).
 */
internal fun computePerimeterSamplePoints(
    islandBounds: Rect,
    excludeBounds: List<Rect>,
    screenWidth: Int,
    screenHeight: Int,
    marginPx: Int = 8,
    samplesPerEdge: Int = 14,
): List<Pair<Int, Int>> {
    if (screenWidth <= 0 || screenHeight <= 0 || islandBounds.width <= 0f || islandBounds.height <= 0f) {
        return emptyList()
    }
    val maxX = screenWidth - 1
    val maxY = screenHeight - 1
    val pad = 4f
    val paddedExcluded = excludeBounds.map { rect ->
        Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
    }

    fun isAllowed(x: Int, y: Int): Boolean {
        if (x !in 0..maxX || y !in 0..maxY) return false
        val fx = x.toFloat()
        val fy = y.toFloat()
        if (fx >= islandBounds.left && fx <= islandBounds.right &&
            fy >= islandBounds.top && fy <= islandBounds.bottom
        ) {
            return false
        }
        return paddedExcluded.none { r ->
            fx >= r.left && fx <= r.right && fy >= r.top && fy <= r.bottom
        }
    }

    val points = LinkedHashSet<Pair<Int, Int>>()
    val count = samplesPerEdge.coerceAtLeast(2)

    fun addHorizontalEdge(y: Int) {
        if (y !in 0..maxY) return
        val startX = (islandBounds.left - marginPx).coerceIn(0f, maxX.toFloat())
        val endX = (islandBounds.right + marginPx).coerceIn(0f, maxX.toFloat())
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1)
            val x = (startX + (endX - startX) * t).roundToInt()
            if (isAllowed(x, y)) points.add(x to y)
        }
    }

    fun addVerticalEdge(x: Int) {
        if (x !in 0..maxX) return
        val startY = islandBounds.top.coerceIn(0f, maxY.toFloat())
        val endY = islandBounds.bottom.coerceIn(0f, maxY.toFloat())
        val sideSamples = (count / 2).coerceAtLeast(3)
        for (i in 0 until sideSamples) {
            val t = i.toFloat() / (sideSamples - 1)
            val y = (startY + (endY - startY) * t).roundToInt()
            if (isAllowed(x, y)) points.add(x to y)
        }
    }

    // Bottom edge (just below the island)
    addHorizontalEdge((islandBounds.bottom + marginPx).roundToInt())
    // Top edge (only if the island isn't flush against the top screen edge)
    if (islandBounds.top > marginPx + 2f) {
        addHorizontalEdge((islandBounds.top - marginPx).roundToInt())
    }
    // Left and right edges
    addVerticalEdge((islandBounds.left - marginPx).roundToInt())
    addVerticalEdge((islandBounds.right + marginPx).roundToInt())

    return points.toList()
}

/**
 * Samples pixels immediately outside the Dynamic Island via AccessibilityService.takeScreenshot
 * (Android 11+) to detect pitch-black backgrounds for the Dynamic outline mode.
 */
object IslandBackdropLuminanceTracker {
    private const val MIN_SAMPLE_INTERVAL_MS = 1_100L
    private const val SETTLE_DELAY_MS = 180L

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _isBackgroundPitchBlack = MutableStateFlow(false)
    val isBackgroundPitchBlack: StateFlow<Boolean> = _isBackgroundPitchBlack.asStateFlow()

    @Volatile
    private var primaryBounds: Rect? = null

    @Volatile
    private var excludedBounds: List<Rect> = emptyList()

    private var lastSampleElapsedMs: Long = 0L
    private var sampleJob: Job? = null
    private var captureInFlight: Boolean = false

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun updateIslandRegion(primary: Rect?, exclude: List<Rect> = emptyList()) {
        val previous = primaryBounds
        primaryBounds = primary
        excludedBounds = exclude
        if (primary == null) {
            sampleJob?.cancel()
            sampleJob = null
            return
        }
        val movedSignificantly = previous == null ||
            abs(previous.width - primary.width) > 36f ||
            abs(previous.height - primary.height) > 36f ||
            abs(previous.top - primary.top) > 24f
        if (movedSignificantly) {
            requestSample()
        }
    }

    fun requestSample(delayMs: Long = SETTLE_DELAY_MS) {
        if (!isSupported()) return
        if (OverlayPreferences.islandOutlineModeFlow.value != OverlayPreferences.IslandOutlineMode.DYNAMIC) return
        if (!ScreenStateTracker.isScreenOn.value) return
        if (!DotAccessibilityService.isServiceConnected.value) return
        if (primaryBounds == null) return

        val now = SystemClock.elapsedRealtime()
        val earliestNext = if (lastSampleElapsedMs > 0L) {
            (lastSampleElapsedMs + MIN_SAMPLE_INTERVAL_MS - now).coerceAtLeast(0L)
        } else 0L
        val waitMs = maxOf(delayMs, earliestNext)

        sampleJob?.cancel()
        sampleJob = scope.launch {
            if (waitMs > 0L) delay(waitMs)
            sampleJob = null
            performSampleNow()
        }
    }

    fun reset() {
        sampleJob?.cancel()
        sampleJob = null
        primaryBounds = null
        excludedBounds = emptyList()
        captureInFlight = false
        _isBackgroundPitchBlack.value = false
    }

    private fun performSampleNow() {
        if (captureInFlight) return
        if (OverlayPreferences.islandOutlineModeFlow.value != OverlayPreferences.IslandOutlineMode.DYNAMIC) return
        if (!ScreenStateTracker.isScreenOn.value || !DotAccessibilityService.isServiceConnected.value) return
        val bounds = primaryBounds ?: return
        val excludes = excludedBounds

        captureInFlight = true
        lastSampleElapsedMs = SystemClock.elapsedRealtime()

        DotAccessibilityService.takeScreenshotBitmap { bitmap ->
            if (bitmap == null) {
                captureInFlight = false
                return@takeScreenshotBitmap
            }
            scope.launch(Dispatchers.Default) {
                val pitchBlack = try {
                    val points = computePerimeterSamplePoints(
                        islandBounds = bounds,
                        excludeBounds = excludes,
                        screenWidth = bitmap.width,
                        screenHeight = bitmap.height,
                    )
                    if (points.isEmpty()) {
                        null
                    } else {
                        val samples = points.map { (x, y) -> bitmap.getPixel(x, y) }
                        isBackgroundPitchBlackFromSamples(samples)
                    }
                } catch (_: Throwable) {
                    null
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                scope.launch(Dispatchers.Main.immediate) {
                    captureInFlight = false
                    if (pitchBlack != null &&
                        OverlayPreferences.islandOutlineModeFlow.value == OverlayPreferences.IslandOutlineMode.DYNAMIC
                    ) {
                        _isBackgroundPitchBlack.value = pitchBlack
                    }
                }
            }
        }
    }
}
