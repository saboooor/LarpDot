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
    return IslandCameraGeometry(diameter, diameter + 4f, (diameter + 4f) / 2f)
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
