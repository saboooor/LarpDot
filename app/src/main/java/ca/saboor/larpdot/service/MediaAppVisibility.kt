package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityState

/** Match the actual session owner, rather than any app categorized as a music player. */
fun isActiveMediaAppOpen(foregroundPackage: String?, playerPackage: String?): Boolean =
    !playerPackage.isNullOrBlank() && foregroundPackage == playerPackage

fun shouldHideForForegroundApp(
    enabled: Boolean,
    foregroundPackage: String?,
    relatedPackage: String?,
): Boolean {
    if (!enabled || foregroundPackage.isNullOrBlank() || relatedPackage.isNullOrBlank()) return false
    if (relatedPackage == "android") return false
    return foregroundPackage == relatedPackage
}

fun shouldHideMediaApp(enabled: Boolean, foregroundPackage: String?, playerPackage: String?): Boolean =
    shouldHideForForegroundApp(enabled, foregroundPackage, playerPackage)

fun filterActivityForForeground(
    activity: NotificationActivityInfo?,
    hideInRelatedApp: Boolean,
    foregroundPackage: String?,
): NotificationActivityInfo? =
    activity?.takeUnless { shouldHideForForegroundApp(hideInRelatedApp, foregroundPackage, it.packageName) }

fun filterActivitiesForForeground(
    activities: List<NotificationActivityInfo>,
    hideInRelatedApp: Boolean,
    foregroundPackage: String?,
): List<NotificationActivityInfo> {
    if (!hideInRelatedApp || foregroundPackage.isNullOrBlank() || activities.isEmpty()) return activities
    return activities.filterNot { shouldHideForForegroundApp(true, foregroundPackage, it.packageName) }
}

fun selectBestVisibleActivity(
    primaryActivity: NotificationActivityInfo?,
    eligibleActivities: List<NotificationActivityInfo>,
    hideInRelatedApp: Boolean,
    foregroundPackage: String?,
): NotificationActivityInfo? {
    val visiblePrimary = filterActivityForForeground(primaryActivity, hideInRelatedApp, foregroundPackage)
    if (visiblePrimary != null || primaryActivity == null) return visiblePrimary
    val filteredEligible = filterActivitiesForForeground(eligibleActivities, hideInRelatedApp, foregroundPackage)
    return NotificationActivityState.selectBestActivity(filteredEligible)
}

internal fun CustomIsland.relatedForegroundPackage(
    activeMediaPackage: String? = null,
    eligibleActivities: List<NotificationActivityInfo> = emptyList(),
    activeNotifications: Collection<CustomNotification> = emptyList(),
    resolveWidgetPackage: (Int) -> String? = { null },
): String? {
    val runtimePackage = if (visibility == CustomIslandVisibility.CONDITIONAL) {
        when (condition) {
            CustomIslandCondition.MUSIC -> activeMediaPackage?.takeIf(String::isNotBlank)
            CustomIslandCondition.NOTIFICATION -> matchingNotification(activeNotifications)
                ?.packageName
                ?.takeIf(String::isNotBlank)
            CustomIslandCondition.LIVE_ACTIVITY -> {
                val matching = if (activityKind != null) {
                    eligibleActivities.firstOrNull { it.kind == activityKind && it.packageName.isNotBlank() && it.packageName != "android" }
                } else {
                    eligibleActivities.firstOrNull { it.packageName.isNotBlank() && it.packageName != "android" }
                }
                matching?.packageName
            }
            CustomIslandCondition.FLASHLIGHT -> null
        }
    } else null
    return runtimePackage ?: associatedAppPackage(resolveWidgetPackage)
}

internal fun filterCustomIslandsForForeground(
    customIslands: List<CustomIsland>,
    hideInRelatedApp: Boolean,
    foregroundPackage: String?,
    activeMediaPackage: String? = null,
    eligibleActivities: List<NotificationActivityInfo> = emptyList(),
    activeNotifications: Collection<CustomNotification> = emptyList(),
    resolveWidgetPackage: (Int) -> String? = { null },
): List<CustomIsland> {
    if (!hideInRelatedApp || foregroundPackage.isNullOrBlank() || customIslands.isEmpty()) return customIslands
    return customIslands.filterNot { island ->
        val related = island.relatedForegroundPackage(
            activeMediaPackage = activeMediaPackage,
            eligibleActivities = eligibleActivities,
            activeNotifications = activeNotifications,
            resolveWidgetPackage = resolveWidgetPackage,
        )
        shouldHideForForegroundApp(true, foregroundPackage, related)
    }
}
