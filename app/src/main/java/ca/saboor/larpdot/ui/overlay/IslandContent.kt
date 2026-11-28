package ca.saboor.larpdot.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.CustomIsland

/** The shell chooses the provider here for both compact and morphing surfaces. */
@Composable
internal fun IslandCompactContent(
    model: IslandModel,
    media: MediaTrackInfo,
    activity: NotificationActivityInfo?,
    diameter: Dp,
    landscape: Boolean,
    showTitle: Boolean,
    announcing: Boolean,
    trailingWingFraction: Float,
    artworkAnchor: ArtworkAnchor? = null,
    visualizerAnchor: IslandElementAnchor? = null,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
    customIsland: CustomIsland? = null,
    onExpand: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
) {
    when (model.type) {
        IslandType.CUSTOM -> if (customIsland != null) CustomIslandCompactContent(
            island = customIsland,
            thickness = diameter,
            isLandscape = landscape,
            trailingWingFraction = trailingWingFraction,
            leftAnchor = iconAnchor,
            rightAnchor = progressAnchor,
        )
        IslandType.MEDIA -> CompactIslandContent(
            mediaInfo = media, cutoutDiameterDp = diameter,
            showMinimizedTitle = showTitle, isSongAnnouncement = announcing,
            trailingWingFraction = trailingWingFraction, artworkAnchor = artworkAnchor,
            visualizerAnchor = visualizerAnchor,
        )
        IslandType.FLASHLIGHT -> CompactFlashlightContent(
            cutoutDiameterDp = diameter, onExpand = onExpand,
            onFlashlightToggle = onFlashlightToggle, isLandscape = landscape,
            trailingWingFraction = trailingWingFraction, status = model.presentation.status,
            iconAnchor = iconAnchor, progressAnchor = progressAnchor,
        )
        IslandType.NOTIFICATION_ACTIVITY -> if (activity != null) CompactNotificationActivityContent(
            activity = activity, cutoutDiameterDp = diameter, isLandscape = landscape,
            trailingWingFraction = trailingWingFraction,
            iconAnchor = iconAnchor, progressAnchor = progressAnchor,
        )
    }
}
