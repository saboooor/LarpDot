package ca.saboor.larpdot.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.OverlayPreferences

/** Base compact pill extra width for media art styles (shared by live overlay and settings preview). */
internal fun compactMediaBaseExtraDp(
    artStyle: OverlayPreferences.AlbumArtStyle,
    thickness: Dp,
    showOutline: Boolean,
    radialGaugeExtra: Dp = compactRadialGaugeExtraDp(thickness),
): Dp = when (artStyle) {
    OverlayPreferences.AlbumArtStyle.BLENDED -> thickness * 3
    OverlayPreferences.AlbumArtStyle.NESTED -> maxOf(
        thickness + compactNestedArtworkSize(thickness),
        if (showOutline) radialGaugeExtra else 0.dp,
    )
    else -> if (showOutline) maxOf(60.dp, thickness * 2) else 60.dp
}

/** Total horizontal width of the compact visualizer bars (shared by live overlay and settings preview). */
internal fun compactVisualizerWidthDp(
    showVisualizer: Boolean,
    bandCount: Int,
    barWidth: Float,
    barSpacing: Float,
): Float = if (showVisualizer) {
    bandCount * barWidth + (bandCount - 1).coerceAtLeast(0) * barSpacing
} else 0f

/** Shared width and viewport limits for the live player and its settings preview. */
internal fun expandedMediaCardWidth(
    playerLayout: OverlayPreferences.ExpandedPlayerLayout,
    screenWidthDp: Dp,
    horizontalMarginDp: Dp,
    drawWindowWidthDp: Dp = screenWidthDp,
    isLandscape: Boolean = false,
): Dp = when {
    playerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE ->
        minOf(screenWidthDp - 88.dp, 248.dp)
    isLandscape -> minOf(drawWindowWidthDp - horizontalMarginDp - 14.dp, 420.dp)
    else -> screenWidthDp - (horizontalMarginDp * 2)
}

internal fun expandedMediaCardMaxHeight(screenHeight: Dp, isLandscape: Boolean = false): Dp =
    minOf(446.dp, screenHeight - if (isLandscape) 36.dp else 14.dp).coerceAtLeast(1.dp)

/** Values supplied by content providers to the common island shell. */
data class IslandPresentation(
    val status: String = "",
    val accent: Color = Color.White,
    val progress: Float = 0f,
    val compactOutline: Boolean = false,
    val expandedOutline: Boolean = false,
    val compactExtra: Dp = 0.dp,
    val landscapeExtra: Dp = compactExtra,
) {
    fun width(diameter: Dp, thickness: Dp, landscape: Boolean, fittedWingWidth: Dp? = null): Dp =
        if (landscape) thickness else diameter + (fittedWingWidth ?: compactExtra)

    fun height(diameter: Dp, thickness: Dp, landscape: Boolean): Dp =
        if (landscape) diameter + landscapeExtra else thickness
}

/** Provider adaptation happens once; rendering and geometry consume the same values. */
@Composable
internal fun rememberIslandPresentations(
    media: MediaTrackInfo,
    activity: NotificationActivityInfo?,
    activityStatus: String?,
    flashlightStatus: String,
    flashlightProgress: Float,
    thickness: Dp,
    cutoutDiameter: Dp,
    announcing: Boolean,
    customIsland: CustomIsland? = null,
): Map<IslandType, IslandPresentation> {
    val artStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val compactMediaOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val expandedMediaOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val compactFlashOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val expandedFlashOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val radialGaugeExtra = compactRadialGaugeExtraDp(thickness)
    val activityExtra = rememberCompactActivityExtraDp(activity, activityStatus, cutoutDiameter, activitySettings.progressOutline)
    val flashlightExtra = maxOf(
        if (compactFlashOutline) radialGaugeExtra else 0.dp,
        rememberCompactStatusExtraDp(flashlightStatus).coerceAtLeast(56.dp),
    )
    val activityProgress = activity?.progressFraction
    val activityAccent = activity?.let { activityIconTint(it.kind, MaterialTheme.colorScheme.primary) }
        ?: MaterialTheme.colorScheme.primary
    val showActivityOutline = activitySettings.progressOutline && activityProgress != null
    return mapOf(
        IslandType.CUSTOM to IslandPresentation(compactExtra = customIslandCompactExtra(customIsland, cutoutDiameter)),
        IslandType.MEDIA to run {
            val base = compactMediaBaseExtraDp(artStyle, thickness, compactMediaOutline, radialGaugeExtra)
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
        IslandType.FLASHLIGHT to IslandPresentation(flashlightStatus, FlashlightWhite, flashlightProgress,
            compactFlashOutline, expandedFlashOutline, flashlightExtra),
        IslandType.NOTIFICATION_ACTIVITY to IslandPresentation(
            status = activityStatus.orEmpty(),
            accent = activityAccent,
            progress = activityProgress ?: 0f,
            compactOutline = showActivityOutline,
            expandedOutline = showActivityOutline,
            compactExtra = activityExtra,
        ),
    )
}
