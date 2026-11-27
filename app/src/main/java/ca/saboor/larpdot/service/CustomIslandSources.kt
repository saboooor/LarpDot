package ca.saboor.larpdot.service

import android.content.Context
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityAction
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityState

internal data class CustomIslandSources(
    val media: MediaTrackInfo,
    val activity: NotificationActivityInfo?,
    val notification: CustomNotification?,
    val activityNotification: CustomNotification?,
    val appPackage: String?,
) {
    val notificationKey get() = notification?.key ?: activity?.key
    val image get() = (notification ?: activityNotification)?.artwork
    val notificationIcon get() = (notification ?: activityNotification)?.smallIcon
    val notificationColor get() = (notification ?: activityNotification)?.notificationColor
    val notificationContentView get() = (notification ?: activityNotification)?.contentView
    val notificationActions get() = (notification ?: activityNotification)?.actions ?: activity?.actions.orEmpty()
}

internal fun CustomIsland.resolveSources(
    media: MediaTrackInfo,
    currentActivity: NotificationActivityInfo?,
    activities: List<NotificationActivityInfo>,
    notifications: Map<String, CustomNotification>,
    locked: Boolean,
): CustomIslandSources {
    val source = condition.takeIf { visibility == CustomIslandVisibility.CONDITIONAL }
    val activity = if (source == CustomIslandCondition.LIVE_ACTIVITY) {
        val kind = activityKind
        if (kind == null) currentActivity
        else activities.asSequence().filter { it.kind == kind }.maxByOrNull { it.postedAtMillis }
    } else null
    val notification = if (source == CustomIslandCondition.NOTIFICATION && !locked) matchingNotification(notifications.values) else null
    val activityNotification = if (!locked && activity != null) notifications[activity.key] else null
    val app = when (source) {
        CustomIslandCondition.NOTIFICATION -> notification?.packageName
        CustomIslandCondition.LIVE_ACTIVITY -> activity?.packageName
        else -> media.playerPackageName
    }
    return CustomIslandSources(media, activity, notification, activityNotification, app)
}

private fun CustomIsland.currentSources(locked: Boolean): CustomIslandSources = resolveSources(
    MediaPlaybackState.currentTrack.value,
    NotificationActivityState.current.value,
    NotificationActivityState.eligibleActivities.value,
    CustomIslandNotifications.active.value,
    locked,
)

internal fun CustomIsland.performCurrentNotificationAction(key: String?, action: NotificationActivityAction) {
    if (key == null || ScreenStateTracker.isDeviceLocked.value) return
    val sources = currentSources(locked = false)
    if (sources.notificationKey == key && action in sources.notificationActions) {
        NotificationActivityState.perform(action)
    }
}

internal fun CustomIsland.runTapAction(context: Context, expand: () -> Unit) {
    when (tapAction) {
        CustomIslandTapAction.EXPAND -> expand()
        CustomIslandTapAction.NONE -> Unit
        CustomIslandTapAction.OPEN_APP -> {
            val app = tapAppPackage ?: currentSources(ScreenStateTracker.isDeviceLocked.value).appPackage
            app?.let { NotificationActivityState.openApp(context, it) }
        }
        CustomIslandTapAction.OPEN_NOTIFICATION -> {
            if (ScreenStateTracker.isDeviceLocked.value) return
            val sources = currentSources(locked = false)
            val notification = sources.notification ?: sources.activityNotification
            if (notification != null) {
                NotificationActivityState.open(context, notification.packageName, notification.contentIntent)
            } else {
                sources.activity?.let { NotificationActivityState.open(context, it) }
            }
        }
        CustomIslandTapAction.RUN_NOTIFICATION_ACTION -> {
            if (ScreenStateTracker.isDeviceLocked.value) return
            val title = notificationActionTitle.trim()
            currentSources(locked = false).notificationActions
                .firstOrNull { it.title.equals(title, ignoreCase = true) }
                ?.let(NotificationActivityState::perform)
        }
    }
}
