package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import kotlin.math.ceil
import kotlin.math.floor

/** Physical screen pixels, rounded outward so no part of an island loses touch input. */
data class IslandTouchRegion(val left: Int, val top: Int, val width: Int, val height: Int)

/** Laid out compact bounds and the content split used to draw that same frame. */
internal data class CompactIslandFrame(
    val bounds: Rect,
    val trailingFraction: Float = 0.5f,
    val hasBubbleBefore: Boolean = false,
    val hasBubbleAfter: Boolean = false,
    val announcing: Boolean = false,
    val isMiniPill: Boolean = false,
) {
    fun toward(target: CompactIslandFrame, fraction: Float): CompactIslandFrame {
        if (fraction <= 0f) return this
        if (fraction >= 1f) return target
        return copy(
            bounds = Rect(
                lerp(bounds.left, target.bounds.left, fraction),
                lerp(bounds.top, target.bounds.top, fraction),
                lerp(bounds.right, target.bounds.right, fraction),
                lerp(bounds.bottom, target.bounds.bottom, fraction),
            ),
            trailingFraction = lerp(trailingFraction, target.trailingFraction, fraction),
        )
    }
}

fun islandTouchRegion(bounds: List<Rect>, paddingPx: Float): IslandTouchRegion? {
    val visible = bounds.filter { it.width > 0f && it.height > 0f }
    if (visible.isEmpty()) return null
    val left = floor(visible.minOf { it.left } - paddingPx).toInt()
    val top = floor(visible.minOf { it.top } - paddingPx).toInt().coerceAtLeast(0)
    val right = ceil(visible.maxOf { it.right } + paddingPx).toInt()
    val bottom = ceil(visible.maxOf { it.bottom } + paddingPx).toInt()
    return IslandTouchRegion(left, top, right - left, bottom - top)
}

/**
 * Computes the touch-forwarding bounds for both compact and expanded islands using one path.
 * Expanded mode fits strictly to the expanded card (outside touches pass through and dismiss
 * via FLAG_WATCH_OUTSIDE_TOUCH); collapsing or idle returns only compact bounds or empty.
 */
internal fun activeIslandTouchBounds(
    isExpanded: Boolean,
    expandedBounds: Rect?,
    compactBounds: Rect?,
    secondaryBounds: List<Rect>,
    swipeDownBounds: Rect? = null,
): List<Rect> = if (isExpanded) {
    listOfNotNull(expandedBounds ?: compactBounds ?: swipeDownBounds)
} else {
    buildList {
        compactBounds?.let(::add)
        addAll(secondaryBounds)
        swipeDownBounds?.let(::add)
    }
}

/**
 * Generous touch zone anchored to the screen edge around the camera dot for the swipe-down
 * custom island, so swipes starting on or just above the camera intercept before the status bar.
 */
internal fun cameraDotSwipeBounds(
    cameraCenterX: Float,
    cameraCenterY: Float,
    cameraRadiusPx: Float,
    screenWidthPx: Float,
    density: Float,
    isLandscape: Boolean,
): Rect {
    val safeRadius = cameraRadiusPx.coerceAtLeast(12f * density)
    val alongEdgeHalf = maxOf(safeRadius + 40f * density, 56f * density)
    val inwardReach = safeRadius + 16f * density
    return if (isLandscape) {
        val onLeft = cameraCenterX < screenWidthPx / 2f
        val left = if (onLeft) 0f else (cameraCenterX - inwardReach).coerceAtLeast(0f)
        val right = if (onLeft) (cameraCenterX + inwardReach).coerceAtMost(screenWidthPx) else screenWidthPx
        val top = (cameraCenterY - alongEdgeHalf).coerceAtLeast(0f)
        val bottom = cameraCenterY + alongEdgeHalf
        Rect(left, top, right, bottom)
    } else {
        val left = (cameraCenterX - alongEdgeHalf).coerceAtLeast(0f)
        val right = (cameraCenterX + alongEdgeHalf).coerceAtMost(screenWidthPx)
        val bottom = cameraCenterY + inwardReach
        Rect(left, 0f, right, bottom)
    }
}

/** Content-independent camera geometry, in dp. */
data class IslandCameraGeometry(val diameter: Float, val thickness: Float, val cornerRadius: Float)

fun islandCameraGeometry(radiusPx: Float, density: Float): IslandCameraGeometry {
    val diameter = (if (radiusPx > 0f) radiusPx * 2f / density else 24f).coerceIn(16f, 36f)
    return IslandCameraGeometry(diameter, diameter, diameter / 2f)
}

/** Expanded panels share the same camera-side placement regardless of their content size. */
data class IslandPanelPlacement(val offsetX: Float, val top: Float)

fun islandSideMargin(cameraX: Float, screenWidth: Float, cameraRadius: Float): Float =
    (minOf(cameraX, screenWidth - cameraX) + cameraRadius + 8f).coerceAtLeast(14f)

fun islandPanelPlacement(
    screenWidth: Float,
    screenHeight: Float,
    cameraX: Float,
    cameraY: Float,
    cameraRadius: Float,
    panelWidth: Float,
    panelHeight: Float,
    landscape: Boolean,
): IslandPanelPlacement {
    if (!landscape) return IslandPanelPlacement(0f, 14f)
    val offset = (screenWidth - panelWidth) / 2f - islandSideMargin(cameraX, screenWidth, cameraRadius)
    val top = (cameraY - panelHeight / 2f).coerceIn(14f, (screenHeight - panelHeight - 14f).coerceAtLeast(14f))
    return IslandPanelPlacement(if (cameraX < screenWidth / 2f) -offset else offset, top)
}

/** Only expansion reveals transparency; spring overshoot must never change the alpha range. */
internal fun islandBackgroundAlpha(expandedOpacity: Float, expansion: Float): Float =
    1f + (expandedOpacity.coerceIn(0f, 1f) - 1f) * expansion.coerceIn(0f, 1f)

/** Limit each wing independently to its available space around the physical camera. */
internal fun fitCompactMediaWings(
    requested: CompactMediaWings,
    diameter: Float,
    screenWidth: Float,
    cameraX: Float,
    maxWidthPercent: Float,
    beforeExtent: Float = 0f,
    afterExtent: Float = 0f,
    symmetric: Boolean = false,
): CompactMediaWings {
    val leadingRoom = (cameraX - diameter / 2f - beforeExtent).coerceAtLeast(0f)
    val trailingRoom = (screenWidth - cameraX - diameter / 2f - afterExtent).coerceAtLeast(0f)
    val fraction = maxWidthPercent.coerceIn(40f, 100f) / 100f
    if (symmetric) {
        val wing = minOf(maxOf(requested.leadingDp, requested.trailingDp), minOf(leadingRoom, trailingRoom) * fraction)
        return CompactMediaWings(wing, wing)
    }
    return CompactMediaWings(minOf(requested.leadingDp, leadingRoom * fraction), minOf(requested.trailingDp, trailingRoom * fraction))
}

/** Shared downward swipe threshold from the camera dot (responsive for quick and angled thumb swipes). */
internal fun isCameraDotSwipeDown(dx: Float, dy: Float, density: Float): Boolean =
    dy > 6f * density && dy > kotlin.math.abs(dx) * 0.7f

/**
 * Maps vertical finger drag distance to interactive expansion progress (0..1 linear tracking,
 * with gentle asymptotic rubber-band overscroll past 1f up to 1.08f).
 */
internal fun interactivePullProgress(dragYPx: Float, pullRangePx: Float): Float {
    if (dragYPx <= 0f || pullRangePx <= 0f) return 0f
    val linear = dragYPx / pullRangePx
    if (linear <= 1f) return linear
    val excess = linear - 1f
    return 1f + (excess / (1f + excess * 2f)) * 0.08f
}

/** Commits an interactive pull-down on release when dragged far enough or flicked downward. */
internal fun shouldCommitSwipeDownPull(
    progress: Float,
    velocityYPxPerSec: Float,
    density: Float,
    cancelled: Boolean = false,
): Boolean {
    if (cancelled) {
        // SystemUI can pilfer top-edge touches mid-swipe; commit if the user was actively pulling down.
        return progress >= 0.04f && velocityYPxPerSec >= 0f
    }
    if (velocityYPxPerSec > 180f * density) return true
    if (velocityYPxPerSec < -300f * density) return false
    return progress >= 0.22f
}

/** Natural content widths, before applying the user's upper limit. */
internal fun compactTextMediaWings(
    baseExtraDp: Float,
    titleWidthDp: Float,
    artistWidthDp: Float,
    announcing: Boolean,
    announcementArtWidthDp: Float = 0f,
    announcementVisualizerWidthDp: Float = 0f,
    announcementEndInsetDp: Float = 8f,
): CompactMediaWings = if (announcing) {
    CompactMediaWings(
        (titleWidthDp + if (announcementArtWidthDp > 0f) announcementArtWidthDp else 6f).coerceAtLeast(12f),
        (artistWidthDp + announcementVisualizerWidthDp + announcementEndInsetDp).coerceAtLeast(12f),
    )
} else {
    CompactMediaWings(baseExtraDp / 2f, maxOf(baseExtraDp / 2f, titleWidthDp + 8f))
}
