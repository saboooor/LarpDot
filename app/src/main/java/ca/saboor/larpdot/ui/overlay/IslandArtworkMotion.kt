package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal class IslandArtworkMotion(private val fraction: () -> Float) {
    val compact: ArtworkAnchor = ArtworkAnchor { isMoving }
    val expanded: ArtworkAnchor = ArtworkAnchor { isMoving }

    val progress get() = fraction().coerceIn(0f, 1f)
    private val isMoving: Boolean get() = progress > 0f && progress < 1f &&
        compact.placement != null && expanded.placement != null
}

internal data class ArtworkStyle(
    val shape: Shape? = null,
    val fadeVertically: Boolean? = null,
)

internal data class ArtworkPlacement(val bounds: Rect, val style: ArtworkStyle)

internal class ArtworkAnchor(val isMoving: () -> Boolean) {
    private var container: LayoutCoordinates? = null
    private var tile: LayoutCoordinates? = null
    private var firstSquare = false
    private var style = ArtworkStyle()
    var opacity: () -> Float = { 1f }
        private set
    var placement by mutableStateOf<ArtworkPlacement?>(null)
        private set

    fun placeContainer(coordinates: LayoutCoordinates) {
        container = coordinates
        updatePlacement()
    }

    fun placeTile(
        coordinates: LayoutCoordinates,
        style: ArtworkStyle,
        firstSquare: Boolean,
        opacity: () -> Float,
    ) {
        tile = coordinates
        this.style = style
        this.firstSquare = firstSquare
        this.opacity = opacity
        updatePlacement()
    }

    private fun updatePlacement() {
        val container = container?.takeIf { it.isAttached } ?: return
        val tile = tile?.takeIf { it.isAttached } ?: return
        val bounds = container.localBoundingBoxOf(tile, clipBounds = false)
        if (bounds.width <= 0f || bounds.height <= 0f) return
        val side = minOf(bounds.width, bounds.height)
        val artwork = if (firstSquare) Rect(bounds.topLeft, Size(side, side)) else bounds
        // Center-relative coordinates cancel the outer layers' scale, translation and clipping.
        val center = Offset(container.size.width / 2f, container.size.height / 2f)
        placement = ArtworkPlacement(artwork.translate(-center), style)
    }
}

internal fun Modifier.artworkContainer(anchor: ArtworkAnchor?): Modifier =
    if (anchor == null) this else onGloballyPositioned(anchor::placeContainer)

internal fun Modifier.artworkTile(
    anchor: ArtworkAnchor?,
    style: ArtworkStyle = ArtworkStyle(),
    opacity: () -> Float = { 1f },
    recordPosition: Boolean = true,
    firstSquare: Boolean = false,
): Modifier {
    if (anchor == null) return this
    val tracked = if (recordPosition) onGloballyPositioned {
        anchor.placeTile(it, style, firstSquare, opacity)
    } else this
    // Blended art draws the square and its reflection separately in its own Canvas.
    return if (firstSquare) tracked else tracked.drawWithContent {
        if (!anchor.isMoving()) drawContent()
    }
}

private val FADE_MASK_COLORS = listOf(Color.White, Color.Transparent)

@Composable
internal fun TravelingArtwork(
    bitmap: ImageBitmap,
    motion: IslandArtworkMotion,
    glow: AlbumArtGlowState,
    isLandscape: Boolean = false,
    modifier: Modifier,
) {
    val layerPaint = remember { Paint() }
    val reusablePath = remember { Path() }
    Canvas(modifier) {
        val progress = motion.progress
        if (progress <= 0f || progress >= 1f) return@Canvas
        val start = motion.compact.placement ?: return@Canvas
        val end = motion.expanded.placement ?: return@Canvas
        // Curve around the camera cutout: swing outward toward the wing side while keeping the
        // tile compact near the cutout, then slide across once below (or clear of) the camera.
        val wingAxisProgress = progress.pow(2.6f)
        val clearAxisProgress = 1f - (1f - progress).pow(1.9f)
        val sizeProgress = progress.pow(1.4f)
        val tileSize = Size(
            lerp(start.bounds.width, end.bounds.width, sizeProgress),
            lerp(start.bounds.height, end.bounds.height, sizeProgress),
        )
        val arc = sin(progress * PI).toFloat()
        val xProgress = if (isLandscape) clearAxisProgress else wingAxisProgress
        val yProgress = if (isLandscape) wingAxisProgress else clearAxisProgress
        val baseX = center.x + lerp(start.bounds.center.x, end.bounds.center.x, xProgress)
        val baseY = center.y + lerp(start.bounds.center.y, end.bounds.center.y, yProgress)
        val swingAmount = arc * minOf(size.width, size.height) * 0.28f
        val tileCenter = if (isLandscape) {
            val swingDir = if (start.bounds.center.y <= 0f) -1f else 1f
            val minY = (tileSize.height / 2f).coerceAtMost(center.y)
            val maxY = (size.height - tileSize.height / 2f).coerceAtLeast(center.y)
            Offset(baseX, (baseY + swingDir * swingAmount).coerceIn(minOf(baseY, minY), maxOf(baseY, maxY)))
        } else {
            val swingDir = if (start.bounds.center.x <= 0f) -1f else 1f
            val minX = (tileSize.width / 2f).coerceAtMost(center.x)
            val maxX = (size.width - tileSize.width / 2f).coerceAtLeast(center.x)
            Offset((baseX + swingDir * swingAmount).coerceIn(minOf(baseX, minX), maxOf(baseX, maxX)), baseY)
        }
        val opacity = lerp(motion.compact.opacity(), motion.expanded.opacity(), progress)
        val styleMix = ((progress - 0.15f) / 0.7f).coerceIn(0f, 1f)
        translate(tileCenter.x - tileSize.width / 2f, tileCenter.y - tileSize.height / 2f) {
            drawMovingAlbumArtGlow(glow, tileSize, opacity)
            if (start.style == end.style) {
                drawArtwork(bitmap, tileSize, start.style, opacity, layerPaint, reusablePath)
            } else {
                // Mix the masks over a transparent layer to keep their overlap at full opacity.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, tileSize), layerPaint)
                    drawArtwork(bitmap, tileSize, start.style, opacity * (1f - styleMix), layerPaint, reusablePath)
                    drawArtwork(bitmap, tileSize, end.style, opacity * styleMix, layerPaint, reusablePath, BlendMode.Plus)
                    canvas.restore()
                }
            }
        }
    }
}

private fun DrawScope.drawArtwork(
    bitmap: ImageBitmap,
    tileSize: Size,
    style: ArtworkStyle,
    alpha: Float,
    layerPaint: Paint,
    reusablePath: Path,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (alpha <= 0f) return
    val ratio = tileSize.width / tileSize.height
    val sourceWidth = (bitmap.height * ratio).roundToInt().coerceIn(1, bitmap.width)
    val sourceHeight = (bitmap.width / ratio).roundToInt().coerceIn(1, bitmap.height)
    val outline = style.shape?.createOutline(tileSize, layoutDirection, this)
    val path = when (outline) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> reusablePath.apply {
            rewind()
            addRoundRect(outline.roundRect)
        }
        is Outline.Rectangle -> reusablePath.apply {
            rewind()
            addRect(outline.rect)
        }
        null -> null
    }

    fun drawTile() {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset((bitmap.width - sourceWidth) / 2, (bitmap.height - sourceHeight) / 2),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstSize = IntSize(tileSize.width.roundToInt(), tileSize.height.roundToInt()),
            alpha = alpha,
            blendMode = blendMode,
        )
    }

    if (path != null) {
        clipPath(path) { drawTile() }
    } else if (style.fadeVertically != null) {
        drawIntoCanvas { canvas ->
            val mask = if (style.fadeVertically) Brush.verticalGradient(FADE_MASK_COLORS, endY = tileSize.height)
                else Brush.horizontalGradient(FADE_MASK_COLORS, endX = tileSize.width)
            canvas.saveLayer(Rect(Offset.Zero, tileSize), layerPaint)
            drawTile()
            drawRect(mask, size = tileSize, blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    } else {
        drawTile()
    }
}
