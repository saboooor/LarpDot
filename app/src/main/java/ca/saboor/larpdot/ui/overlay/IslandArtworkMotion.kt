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
import kotlin.math.roundToInt

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
    private var firstSquare = false
    private var style = ArtworkStyle()
    var opacity: () -> Float = { 1f }
        private set
    var placement by mutableStateOf<ArtworkPlacement?>(null)
        private set
    private val position = IslandElementPosition { bounds ->
        val side = minOf(bounds.width, bounds.height)
        val artwork = if (firstSquare) Rect(bounds.topLeft, Size(side, side)) else bounds
        placement = ArtworkPlacement(artwork, style)
    }

    fun placeContainer(coordinates: LayoutCoordinates) = position.placeContainer(coordinates)

    fun placeTile(
        coordinates: LayoutCoordinates,
        style: ArtworkStyle,
        firstSquare: Boolean,
        opacity: () -> Float,
    ) {
        this.style = style
        this.firstSquare = firstSquare
        this.opacity = opacity
        position.placeTile(coordinates)
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
    compactGlow: AlbumArtGlowState,
    expandedGlow: AlbumArtGlowState,
    path: IslandElementPath,
    modifier: Modifier,
) {
    val layerPaint = remember { Paint() }
    val reusablePath = remember { Path() }
    Canvas(modifier) {
        val progress = motion.progress
        if (progress <= 0f || progress >= 1f) return@Canvas
        val start = motion.compact.placement ?: return@Canvas
        val end = motion.expanded.placement ?: return@Canvas
        val frame = path.frameInShell(start.bounds, end.bounds, center, progress)
        val tile = frame.bounds
        val tileSize = tile.size
        val styleMix = frame.morphFraction
        val opacity = lerp(motion.compact.opacity(), motion.expanded.opacity(), styleMix)
        translate(tile.left, tile.top) {
            if (compactGlow.blurredArtwork == expandedGlow.blurredArtwork) {
                drawMovingAlbumArtGlow(compactGlow, tileSize, opacity)
            } else {
                drawMovingAlbumArtGlow(compactGlow, tileSize, opacity * (1f - styleMix))
                drawMovingAlbumArtGlow(expandedGlow, tileSize, opacity * styleMix)
            }
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
