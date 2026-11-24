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
    val compactTrailingFraction: Float = 0.5f,
) {
    fun width(diameter: Dp, thickness: Dp, landscape: Boolean, mediaWingWidth: Dp? = null): Dp =
        if (landscape) thickness else diameter + (mediaWingWidth ?: compactExtra)

    fun height(diameter: Dp, thickness: Dp, landscape: Boolean): Dp =
        if (landscape) diameter + landscapeExtra else thickness
}

internal data class CompactOutlineStyle(
    val progressFraction: Float,
    val trackAlpha: Float,
    val progressAlpha: Float,
)

/** Ties both the ambient track and active progress outline to the compact pill's visibility. */
internal fun compactOutlineStyle(
    showOutline: Boolean,
    progress: Float,
    contentAlpha: Float,
    accentAlpha: Float = 1f,
): CompactOutlineStyle {
    val visibility = contentAlpha.coerceIn(0f, 1f)
    val visible = visibility > 0.01f
    val showProgress = showOutline && visible
    return CompactOutlineStyle(
        progressFraction = if (showProgress) progress.coerceIn(0f, 1f) else 0f,
        trackAlpha = if (visible) (48f / 255f) * visibility else 0f,
        progressAlpha = if (showProgress) accentAlpha.coerceIn(0f, 1f) * visibility else 0f,
    )
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
    announcing: Boolean,
): Map<IslandType, IslandPresentation> {
    val artStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val compactMediaOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val expandedMediaOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val compactFlashOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val expandedFlashOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val maxActivityExtra = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - thickness - 32.dp).coerceAtLeast(56.dp)
    val isNavigation = activity?.kind == ca.saboor.larpdot.notification.NotificationActivityKind.NAVIGATION
    val radialGaugeExtra = compactRadialGaugeExtraDp(thickness)
    val activityHasRadial = activityRadialProgress(activity, activitySettings.progressOutline, MaterialTheme.colorScheme.primary) != null
    val navMeasuredWidthDp = if (isNavigation && !activityStatus.isNullOrEmpty()) {
        rememberCompactStatusTextWidthDp(activityStatus, compactFontSize = true)
    } else 0.dp
    val navWings = if (isNavigation) {
        compactNavigationWings(
            statusTextWidthDp = navMeasuredWidthDp.value,
            maxTrailingDp = (maxActivityExtra.value - 28f).coerceIn(28f, 68f),
        )
    } else null
    val activityExtra = if (navWings != null) {
        navWings.widthDp.dp.coerceAtMost(maxActivityExtra)
    } else {
        maxOf(
            if (activityHasRadial) radialGaugeExtra else 0.dp,
            if (activityStatus.isNullOrEmpty()) 56.dp else rememberCompactStatusExtraDp(activityStatus),
        ).coerceAtMost(maxActivityExtra)
    }
    val activityLandscapeExtra = if (isNavigation) {
        calculateActivityCompactExtraDp(navMeasuredWidthDp.value).dp.coerceAtMost(maxActivityExtra)
    } else {
        activityExtra
    }
    val activityTrailingFraction = navWings?.trailingFraction ?: 0.5f
    val flashlightExtra = maxOf(
        if (compactFlashOutline) radialGaugeExtra else 0.dp,
        rememberCompactStatusExtraDp(flashlightStatus).coerceAtLeast(56.dp),
    )
    val activityProgress = activity?.progressFraction
    val activityAccent = activity?.let { activityIconTint(it.kind, MaterialTheme.colorScheme.primary) }
        ?: MaterialTheme.colorScheme.primary
    val showActivityOutline = activitySettings.progressOutline && activityProgress != null
    return mapOf(
        IslandType.MEDIA to run {
            val base = when (artStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> thickness * 3
                OverlayPreferences.AlbumArtStyle.NESTED -> maxOf(
                    thickness + (thickness - 12.dp).coerceIn(16.dp, 24.dp),
                    if (compactMediaOutline) radialGaugeExtra else 0.dp,
                )
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
        IslandType.FLASHLIGHT to IslandPresentation(flashlightStatus, flashlightAccentColor(), flashlightProgress,
            compactFlashOutline, expandedFlashOutline, flashlightExtra),
        IslandType.NOTIFICATION_ACTIVITY to IslandPresentation(
            status = activityStatus.orEmpty(),
            accent = activityAccent,
            progress = activityProgress ?: 0f,
            compactOutline = showActivityOutline,
            expandedOutline = showActivityOutline,
            compactExtra = activityExtra,
            landscapeExtra = activityLandscapeExtra,
            compactTrailingFraction = activityTrailingFraction,
        ),
    )
}
