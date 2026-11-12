package ca.saboor.larpdot.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences

/** Values supplied by content providers to the common island shell. */
data class IslandPresentation(
    val status: String = "",
    val accent: Color = Color.White,
    val progress: Float = 0f,
    val compactOutline: Boolean = false,
    val expandedOutline: Boolean = false,
    val compactExtra: Dp = 0.dp,
    val landscapeExtra: Dp = compactExtra,
    val hideTrackWhenOutlineDisabled: Boolean = false,
) {
    fun width(diameter: Dp, thickness: Dp, landscape: Boolean, mediaWingWidth: Dp? = null): Dp =
        if (landscape) thickness else diameter + (mediaWingWidth ?: compactExtra)

    fun height(diameter: Dp, thickness: Dp, landscape: Boolean): Dp =
        if (landscape) diameter + landscapeExtra else thickness
}

/** Provider adaptation happens once; rendering and geometry consume the same values. */
@Composable
internal fun rememberIslandPresentations(
    media: MediaTrackInfo,
    activityStatus: String?,
    flashlightStatus: String,
    flashlightProgress: Float,
    thickness: Dp,
    announcing: Boolean,
): Map<IslandType, IslandPresentation> {
    val artStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val compactMediaOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val expandedMediaOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val compactFlashOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val expandedFlashOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val activityExtra = rememberCompactStatusExtraDp(activityStatus)
    val flashlightExtra = rememberCompactStatusExtraDp(flashlightStatus)
    return mapOf(
        IslandType.MEDIA to run {
            val base = when (artStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> thickness * 3
                OverlayPreferences.AlbumArtStyle.NESTED -> thickness + (thickness - 12.dp).coerceIn(16.dp, 24.dp)
                else -> 60.dp
            }
            val extra = maxOf(base, if (showTitle) 180.dp else 0.dp, if (announcing) 210.dp else 0.dp)
            IslandPresentation(
                status = media.artist,
                accent = if (media.albumArt == null) MaterialTheme.colorScheme.primary else media.dominantColor,
                progress = if (media.durationMs > 0) (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f,
                compactOutline = compactMediaOutline && media.hasMedia,
                expandedOutline = expandedMediaOutline && media.hasMedia,
                compactExtra = extra,
                landscapeExtra = maxOf(base, if (announcing) 140.dp else 0.dp),
            )
        },
        IslandType.FLASHLIGHT to IslandPresentation(flashlightStatus, Color.White, flashlightProgress,
            compactFlashOutline, expandedFlashOutline, flashlightExtra, hideTrackWhenOutlineDisabled = true),
        IslandType.NOTIFICATION_ACTIVITY to IslandPresentation(status = activityStatus.orEmpty(), compactExtra = activityExtra),
    )
}
