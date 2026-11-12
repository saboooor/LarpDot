package ca.saboor.larpdot.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo

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
    onExpand: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
) {
    when (model.type) {
        IslandType.MEDIA -> CompactIslandContent(
            mediaInfo = media, cutoutDiameterDp = diameter, onExpand = onExpand,
            showMinimizedTitle = showTitle, isSongAnnouncement = announcing,
            trailingWingFraction = trailingWingFraction,
        )
        IslandType.FLASHLIGHT -> CompactFlashlightContent(
            cutoutDiameterDp = diameter, onExpand = onExpand,
            onFlashlightToggle = onFlashlightToggle, isLandscape = landscape, status = model.presentation.status,
        )
        IslandType.NOTIFICATION_ACTIVITY -> if (activity != null) CompactNotificationActivityContent(
            activity = activity, cutoutDiameterDp = diameter, isLandscape = landscape, status = model.presentation.status,
        )
    }
}
