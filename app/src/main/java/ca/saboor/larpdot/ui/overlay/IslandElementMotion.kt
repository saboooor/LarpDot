package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import ca.saboor.larpdot.ui.theme.motionFraction
import kotlin.math.abs

private const val CAMERA_CLEARANCE_PHASE = 0.32f

private fun cameraClearanceMorphFraction(progress: Float): Float =
    motionFraction(progress, CAMERA_CLEARANCE_PHASE, 1f)

internal fun cameraClearingElementBounds(
    start: Rect,
    end: Rect,
    cameraCenter: Offset,
    cameraClearance: Float,
    isLandscape: Boolean,
    progress: Float,
    fromSecondaryBubble: Boolean = false,
): Rect {
    val from = start.center
    val to = end.center
    val approach = when {
        !fromSecondaryBubble -> from
        isLandscape -> Offset(from.x, approachCameraEdge(from.y, to.y, cameraCenter.y, cameraClearance + start.height / 2f))
        else -> Offset(approachCameraEdge(from.x, to.x, cameraCenter.x, cameraClearance + start.width / 2f), from.y)
    }
    val clear = if (isLandscape) {
        val clearance = cameraClearance + start.width / 2f
        val x = if (to.x >= cameraCenter.x) {
            maxOf(from.x, cameraCenter.x + clearance)
        } else {
            minOf(from.x, cameraCenter.x - clearance)
        }
        Offset(x, approach.y)
    } else {
        Offset(approach.x, maxOf(from.y, cameraCenter.y + cameraClearance + start.height / 2f))
    }
    // The approach and descent finish together. Motion parallel to the cutout edge can carry
    // velocity into the next phase while motion toward the cutout still settles before crossing.
    val clearanceProgress = motionFraction(progress, 0f, CAMERA_CLEARANCE_PHASE)
    val morphProgress = cameraClearanceMorphFraction(progress)
    val glide = when {
        !fromSecondaryBubble -> Offset.Zero
        isLandscape -> Offset(0f, clearanceGlide(from.y, clear.y, to.y, progress))
        else -> Offset(clearanceGlide(from.x, clear.x, to.x, progress), 0f)
    }
    val center = from + (clear - from) * clearanceProgress + (to - clear) * morphProgress + glide
    val size = Size(
        lerp(start.width, end.width, morphProgress),
        lerp(start.height, end.height, morphProgress),
    )
    val safeTop = cameraCenter.y + cameraClearance
    var top = center.y - size.height / 2f
    if (!isLandscape && morphProgress > 0f && end.top < safeTop) {
        // A destination beside the camera may sit higher than the clearance path.
        // Stay below the dot until the entire moving element has passed its far edge.
        val direction = if (to.x >= cameraCenter.x) 1f else -1f
        val clearEdge = (clear.x - cameraCenter.x) * direction - start.width / 2f
        val endEdge = (to.x - cameraCenter.x) * direction - end.width / 2f
        if (clearEdge < cameraClearance && endEdge > cameraClearance) {
            val exit = (cameraClearance - clearEdge) / (endEdge - clearEdge)
            top = lerp(clear.y - start.height / 2f, end.top, motionFraction(morphProgress, exit, 1f))
        }
    }
    return Rect(Offset(center.x - size.width / 2f, top), size)
}

private fun clearanceGlide(from: Float, clear: Float, to: Float, progress: Float): Float {
    val before = CAMERA_CLEARANCE_PHASE
    val after = 1f - before
    val approachVelocity = (clear - from) / before
    val departureVelocity = (to - clear) / after
    if (approachVelocity * departureVelocity <= 0f) return 0f

    val direction = if (approachVelocity > 0f) 1f else -1f
    val joinVelocity = direction * minOf(abs(approachVelocity), abs(departureVelocity)) * 1.5f
    // Add a matching cubic Hermite tangent to both smoothstep phases. Their positions at
    // the endpoints stay fixed, and the shared tangent carries the element through the join.
    return if (progress < before) {
        val t = progress / before
        joinVelocity * before * t * t * (t - 1f)
    } else {
        val t = (progress - before) / after
        joinVelocity * after * t * (1f - t) * (1f - t)
    }
}

private fun approachCameraEdge(from: Float, to: Float, camera: Float, clearance: Float): Float {
    if (abs(from - camera) <= clearance) return from
    val edge = if (from < camera) camera - clearance else camera + clearance
    return edge.coerceIn(minOf(from, to), maxOf(from, to))
}

internal data class IslandElementPath(
    val compactShellCenter: Offset,
    val expandedShellCenter: Offset,
    val cameraCenter: Offset,
    val cameraRadius: Float,
    val cameraClearance: Float,
    val isLandscape: Boolean,
    val fromSecondaryBubble: Boolean = false,
) {
    fun frameInShell(start: Rect, end: Rect, canvasCenter: Offset, progress: Float): IslandElementFrame {
        // Use display coordinates while clearing the fixed camera, then draw in the moving shell.
        val startOnDisplay = start.translate(compactShellCenter)
        val endOnDisplay = end.translate(expandedShellCenter)
        val needsClearance = elementPathIntersectsCamera(startOnDisplay, endOnDisplay, cameraCenter, cameraRadius)
        val morphFraction = if (needsClearance) cameraClearanceMorphFraction(progress)
            else motionFraction(progress, 0f, 1f)
        val bounds = if (needsClearance) {
            cameraClearingElementBounds(
                startOnDisplay, endOnDisplay, cameraCenter, cameraClearance, isLandscape, progress, fromSecondaryBubble,
            )
        } else {
            lerpBounds(startOnDisplay, endOnDisplay, morphFraction)
        }
        val shellCenter = compactShellCenter + (expandedShellCenter - compactShellCenter) * progress
        return IslandElementFrame(bounds.translate(canvasCenter - shellCenter), morphFraction)
    }
}

internal data class IslandElementFrame(val bounds: Rect, val morphFraction: Float)

private fun elementPathIntersectsCamera(start: Rect, end: Rect, center: Offset, radius: Float): Boolean {
    if (maxOf(start.right, end.right) <= center.x - radius ||
        minOf(start.left, end.left) >= center.x + radius ||
        maxOf(start.bottom, end.bottom) <= center.y - radius ||
        minOf(start.top, end.top) >= center.y + radius
    ) return false

    fun distanceSquared(fraction: Float): Float {
        val dx = maxOf(0f, lerp(start.left, end.left, fraction) - center.x, center.x - lerp(start.right, end.right, fraction))
        val dy = maxOf(0f, lerp(start.top, end.top, fraction) - center.y, center.y - lerp(start.bottom, end.bottom, fraction))
        return dx * dx + dy * dy
    }

    // Distance to the interpolating rectangle is convex along a straight morph.
    // Find the closest point so diagonal paths that miss the round cutout can stay direct.
    var from = 0f
    var to = 1f
    val radiusSquared = radius * radius
    if (distanceSquared(from) < radiusSquared || distanceSquared(to) < radiusSquared) return true
    repeat(16) {
        val first = lerp(from, to, 1f / 3f)
        val second = lerp(from, to, 2f / 3f)
        val firstDistance = distanceSquared(first)
        val secondDistance = distanceSquared(second)
        if (minOf(firstDistance, secondDistance) < radiusSquared) return true
        if (firstDistance <= secondDistance) to = second else from = first
    }
    return distanceSquared((from + to) / 2f) < radiusSquared
}

internal class IslandElementPosition(private val onPlaced: (Rect) -> Unit) {
    private var container: LayoutCoordinates? = null
    private var tile: LayoutCoordinates? = null

    fun placeContainer(coordinates: LayoutCoordinates) {
        container = coordinates
        updatePlacement()
    }

    fun placeTile(coordinates: LayoutCoordinates) {
        tile = coordinates
        updatePlacement()
    }

    fun clearTile() {
        tile = null
    }

    private fun updatePlacement() {
        val container = container?.takeIf { it.isAttached } ?: return
        val tile = tile?.takeIf { it.isAttached } ?: return
        val bounds = container.localBoundingBoxOf(tile, clipBounds = false)
        if (bounds.width <= 0f || bounds.height <= 0f) return
        // Center-relative coordinates cancel the outer layers' scale, translation and clipping.
        val center = Offset(container.size.width / 2f, container.size.height / 2f)
        onPlaced(bounds.translate(-center))
    }
}

internal class IslandElementAnchor(
    val layer: GraphicsLayer,
    val isMoving: () -> Boolean,
) {
    var bounds by mutableStateOf<Rect?>(null)
        private set
    val position = IslandElementPosition { bounds = it }

    fun clear() {
        position.clearTile()
        bounds = null
    }
}

internal class IslandElementMotion(
    compactLayer: GraphicsLayer,
    expandedLayer: GraphicsLayer,
    private val fraction: () -> Float,
) {
    val compact = IslandElementAnchor(compactLayer) { isMoving }
    val expanded = IslandElementAnchor(expandedLayer) { isMoving }
    val progress get() = fraction().coerceIn(0f, 1f)
    val isMoving: Boolean get() = progress > 0f && progress < 1f &&
        compact.bounds != null && expanded.bounds != null
}

@Composable
internal fun rememberIslandElementMotion(fraction: () -> Float): IslandElementMotion {
    val compactLayer = rememberGraphicsLayer()
    val expandedLayer = rememberGraphicsLayer()
    return remember(compactLayer, expandedLayer) {
        IslandElementMotion(compactLayer, expandedLayer, fraction)
    }
}

internal fun Modifier.elementMotionContainer(anchors: List<IslandElementAnchor>): Modifier =
    if (anchors.isEmpty()) this else onGloballyPositioned { coordinates ->
        anchors.forEach { it.position.placeContainer(coordinates) }
    }

@Composable
internal fun Modifier.morphingElement(anchor: IslandElementAnchor?): Modifier {
    if (anchor == null) return this
    DisposableEffect(anchor) {
        onDispose { anchor.clear() }
    }
    return onGloballyPositioned(anchor.position::placeTile).drawWithContent {
        if (anchor.isMoving()) {
            // Record the live element so the traveling layer can draw it in its interpolated bounds.
            anchor.layer.record { this@drawWithContent.drawContent() }
        } else {
            drawContent()
        }
    }
}

@Composable
internal fun TravelingIslandElement(motion: IslandElementMotion, path: IslandElementPath, modifier: Modifier) {
    Canvas(modifier) {
        val progress = motion.progress
        if (progress <= 0f || progress >= 1f) return@Canvas
        val compactBounds = motion.compact.bounds ?: return@Canvas
        val expandedBounds = motion.expanded.bounds ?: return@Canvas
        val frame = path.frameInShell(compactBounds, expandedBounds, center, progress)
        val compact = motion.compact.layer
        val expanded = motion.expanded.layer
        val expandedAlpha = when {
            expanded.size.width == 0 -> 0f
            compact.size.width == 0 -> 1f
            else -> frame.morphFraction
        }
        drawTravelingElement(compact, frame.bounds, 1f - expandedAlpha)
        drawTravelingElement(expanded, frame.bounds, expandedAlpha)
    }
}

private fun DrawScope.drawTravelingElement(layer: GraphicsLayer, bounds: Rect, alpha: Float) {
    if (alpha <= 0f || layer.size.width == 0 || layer.size.height == 0) return
    layer.alpha = alpha
    withTransform({
        translate(bounds.left, bounds.top)
        scale(bounds.width / layer.size.width, bounds.height / layer.size.height, pivot = Offset.Zero)
    }) {
        drawLayer(layer)
    }
}
