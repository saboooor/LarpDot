package ca.saboor.larpdot.ui.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences

@Composable
internal fun configuredCompactMediaWings(
    mediaInfo: MediaTrackInfo,
    extraWidthDp: Float,
    showTitle: Boolean,
    announcing: Boolean,
    visualizerWidthDp: Float,
    cameraDiameterDp: Float,
    cameraCenterXPx: Float,
    landscape: Boolean,
    leadingBubbleExtentDp: Float = 0f,
    trailingBubbleExtentDp: Float = 0f,
): CompactMediaWings {
    val albumArtStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showProgressRing by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    fun textWidth(text: String): Float {
        val textStyle = if (announcing) labelStyle.copy(
            fontSize = 11.5.sp,
            fontWeight = labelStyle.fontWeight,
        ) else labelStyle
        return textMeasurer.measure(
            AnnotatedString(text), style = textStyle, softWrap = false, maxLines = 1,
        ).size.width / density
    }
    val requested = if (!landscape && (showTitle || announcing)) {
        val thickness = cameraDiameterDp.dp
        val nestedArtworkSize = compactNestedArtworkSize(thickness)
        val renderedArtworkSize = if (showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L) {
            maxOf(nestedArtworkSize, compactBatteryRingDiameter(thickness))
        } else {
            nestedArtworkSize
        }
        val baseExtra = when (albumArtStyle) {
            OverlayPreferences.AlbumArtStyle.NESTED -> cameraDiameterDp + renderedArtworkSize.value
            else -> compactMediaBaseExtraDp(albumArtStyle, thickness, showProgressRing).value
        }
        compactTextMediaWings(
            baseExtraDp = baseExtra,
            titleWidthDp = textWidth(if (announcing) mediaInfo.title.ifBlank { "Playing Track" } else mediaInfo.title),
            artistWidthDp = if (announcing) textWidth(mediaInfo.artist.ifBlank { "Media Player" }) else 0f,
            announcing = announcing,
            announcementArtWidthDp = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED && mediaInfo.albumArt != null) {
                val startInset = compactEndInset(thickness, renderedArtworkSize)
                (renderedArtworkSize + startInset + 4.dp).value
            } else 0f,
            announcementVisualizerWidthDp = if (visualizerWidthDp > 0f) {
                visualizerWidthDp + 4f
            } else 0f,
            announcementEndInsetDp = (cameraDiameterDp / 3f).coerceAtLeast(8f),
        )
    } else compactMediaWings(extraWidthDp, visualizerWidthDp)
    if (landscape) return requested
    return rememberFittedCompactWings(
        requested, cameraDiameterDp, cameraCenterXPx,
        leadingBubbleExtentDp, trailingBubbleExtentDp,
        symmetric = !announcing,
    )
}

@Composable
internal fun rememberFittedCompactWings(
    requested: CompactMediaWings,
    cameraDiameterDp: Float,
    cameraCenterXPx: Float,
    leadingBubbleExtentDp: Float = 0f,
    trailingBubbleExtentDp: Float = 0f,
    symmetric: Boolean = true,
): CompactMediaWings {
    val percent by OverlayPreferences.minimizedMaxWidthPercent.collectAsState()
    val density = LocalDensity.current.density
    val screenWidth = LocalView.current.width.takeIf { it > 0 }?.let { it / density }
        ?: LocalConfiguration.current.screenWidthDp.toFloat()
    return fitCompactMediaWings(
        requested, cameraDiameterDp, screenWidth, cameraCenterXPx / density, percent,
        leadingBubbleExtentDp, trailingBubbleExtentDp, symmetric,
    )
}
