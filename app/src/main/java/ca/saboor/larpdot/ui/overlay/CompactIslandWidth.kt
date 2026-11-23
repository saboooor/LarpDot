package ca.saboor.larpdot.ui.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences

@Composable
internal fun configuredCompactMediaWings(
    mediaInfo: MediaTrackInfo,
    extraWidthDp: Float,
    hasTrailingBubble: Boolean,
    showTitle: Boolean,
    announcing: Boolean,
    visualizerWidthDp: Float,
    cameraDiameterDp: Float,
    cameraCenterXPx: Float,
    bubbleThicknessDp: Float,
    hasLeadingBubble: Boolean,
    landscape: Boolean,
): CompactMediaWings {
    val albumArtStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val percent by OverlayPreferences.minimizedMaxWidthPercent.collectAsState()
    val miniPill by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val style by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val density = LocalDensity.current.density
    val screenWidth = LocalView.current.width.takeIf { it > 0 }?.let { it / density }
        ?: LocalConfiguration.current.screenWidthDp.toFloat()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    fun textWidth(text: String, title: Boolean): Float {
        val textStyle = if (announcing) labelStyle.copy(
            fontSize = 11.5.sp,
            fontWeight = labelStyle.fontWeight,
        ) else labelStyle
        return textMeasurer.measure(
            AnnotatedString(text), style = textStyle, softWrap = false, maxLines = 1,
        ).size.width / density
    }
    val requested = if (!landscape && (showTitle || announcing)) {
        val baseExtra = when (albumArtStyle) {
            OverlayPreferences.AlbumArtStyle.BLENDED -> cameraDiameterDp * 3f
            OverlayPreferences.AlbumArtStyle.NESTED -> cameraDiameterDp + (cameraDiameterDp - 12f).coerceIn(16f, 24f)
            else -> 60f
        }
        compactTextMediaWings(
            baseExtraDp = baseExtra,
            titleWidthDp = textWidth(if (announcing) mediaInfo.title.ifBlank { "Playing Track" } else mediaInfo.title, true),
            artistWidthDp = if (announcing) textWidth(mediaInfo.artist.ifBlank { "Media Player" }, false) else 0f,
            announcing = announcing,
            announcementArtWidthDp = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                (cameraDiameterDp - 8f).coerceIn(18f, 26f) + 3f
            } else 0f,
        )
    } else compactMediaWings(extraWidthDp, hasTrailingBubble, showTitle, announcing, visualizerWidthDp)
    if (landscape) return requested
    // Reserve the largest mini-pill status width so changing providers cannot overlap the pill.
    val bubbleExtent = bubbleThicknessDp + (if (miniPill) 34f else 0f) +
        (if (style == OverlayPreferences.BubbleStyle.MATERIAL_3) 4f else 8f)
    return fitCompactMediaWings(
        requested, cameraDiameterDp, screenWidth, cameraCenterXPx / density, percent,
        if (hasLeadingBubble) bubbleExtent else 0f,
        if (hasTrailingBubble) bubbleExtent else 0f,
    )
}
