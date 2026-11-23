package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor

/** Physical screen pixels, rounded outward so no part of an island loses touch input. */
data class IslandTouchRegion(val left: Int, val top: Int, val width: Int, val height: Int)

fun islandTouchRegion(bounds: List<Rect>, paddingPx: Float): IslandTouchRegion? {
    val visible = bounds.filter { it.width > 0f && it.height > 0f }
    if (visible.isEmpty()) return null
    val left = floor(visible.minOf { it.left } - paddingPx).toInt()
    val top = floor(visible.minOf { it.top } - paddingPx).toInt().coerceAtLeast(0)
    val right = ceil(visible.maxOf { it.right } + paddingPx).toInt()
    val bottom = ceil(visible.maxOf { it.bottom } + paddingPx).toInt()
    return IslandTouchRegion(left, top, right - left, bottom - top)
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
): CompactMediaWings {
    val leadingRoom = (cameraX - diameter / 2f - beforeExtent).coerceAtLeast(0f)
    val trailingRoom = (screenWidth - cameraX - diameter / 2f - afterExtent).coerceAtLeast(0f)
    val fraction = maxWidthPercent.coerceIn(40f, 100f) / 100f
    return CompactMediaWings(
        minOf(requested.leadingDp, leadingRoom * fraction),
        minOf(requested.trailingDp, trailingRoom * fraction),
    )
}

/** Natural content widths, before applying the user's upper limit. */
internal fun compactTextMediaWings(
    baseExtraDp: Float,
    titleWidthDp: Float,
    artistWidthDp: Float,
    announcing: Boolean,
    announcementArtWidthDp: Float = 0f,
): CompactMediaWings = if (announcing) {
    CompactMediaWings(
        (titleWidthDp + announcementArtWidthDp + 8f).coerceAtLeast(12f),
        (artistWidthDp + 8f).coerceAtLeast(12f),
    )
} else {
    CompactMediaWings(baseExtraDp / 2f, maxOf(baseExtraDp / 2f, titleWidthDp + 16f))
}
