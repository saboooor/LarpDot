package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion

@Composable
internal fun albumArtGlowColor(media: MediaTrackInfo): Color {
    val enabled by OverlayPreferences.albumArtGlowFlow.collectAsState()
    val tint = lerp(media.artworkColors.primary, media.artworkColors.secondary, 0.18f)
    val color by animateColorAsState(
        tint.copy(alpha = if (enabled && media.albumArt != null) 1f else 0f),
        LarpMotion.fade(),
        label = "album_art_glow",
    )
    return color
}

internal fun Modifier.albumArtGlow(color: Color, anchor: ArtworkAnchor?): Modifier {
    if (color.alpha == 0f) return this
    return drawWithCache {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfSide = maxOf(size.width, size.height) / 2f
        val radius = halfSide + 10.dp.toPx()
        val brush = haloBrush(color, center, radius, halfSide / radius)
        onDrawWithContent {
            if (anchor?.isMoving() != true) drawCircle(brush, radius, center)
            drawContent()
        }
    }
}

internal fun DrawScope.drawMovingAlbumArtGlow(color: Color, tileSize: Size, opacity: Float) {
    if (color.alpha == 0f) return
    val center = Offset(tileSize.width / 2f, tileSize.height / 2f)
    val halfSide = maxOf(tileSize.width, tileSize.height) / 2f
    val radius = halfSide + 10.dp.toPx()
    val brush = haloBrush(color.copy(alpha = color.alpha * opacity), center, radius, halfSide / radius)
    drawCircle(brush, radius, center)
}

/** Edge-to-edge artwork has space for its halo where the cover meets the reflection. */
internal fun DrawScope.drawBlendedAlbumArtGlow(color: Color, isLandscape: Boolean, anchor: ArtworkAnchor?) {
    if (color.alpha == 0f || anchor?.isMoving() == true) return
    val side = if (isLandscape) size.width else size.height
    if (side <= 0f) return
    val seam = if (isLandscape) Offset(side / 2f, side) else Offset(side, side / 2f)
    val radius = side * 0.72f
    val brush = haloBrush(color.copy(alpha = color.alpha * 0.72f), seam, radius, 0.38f)
    drawCircle(brush, radius, seam)
}

private fun haloBrush(color: Color, center: Offset, radius: Float, edge: Float) = Brush.radialGradient(
    0f to color.copy(alpha = color.alpha * 0.68f),
    edge to color.copy(alpha = color.alpha * 0.44f),
    1f to color.copy(alpha = 0f),
    center = center,
    radius = radius,
)
