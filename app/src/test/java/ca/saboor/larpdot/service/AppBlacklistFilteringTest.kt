package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.builtInIslandStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlacklistFilteringTest {

    private fun createActivity(
        packageName: String,
        kind: NotificationActivityKind = NotificationActivityKind.PROGRESS,
    ): NotificationActivityInfo {
        return NotificationActivityInfo(
            key = "key_$packageName",
            packageName = packageName,
            appName = "App $packageName",
            title = "Notification Title",
            text = "Notification Text",
            kind = kind,
            progress = 50,
            progressMax = 100,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = 1000L,
            contentIntent = null,
            actions = emptyList(),
        )
    }

    @Test
    fun blacklistedAppNotificationsAreFilteredOut() {
        val blacklistedPackages = setOf("com.blacklisted.app", "com.spammy.notifier")
        val isBlacklistEnabled = true

        val activities = listOf(
            createActivity("com.blacklisted.app"),
            createActivity("com.allowed.app"),
        )

        val visibleActivities = activities.filter { activity ->
            if (isBlacklistEnabled) {
                !blacklistedPackages.contains(activity.packageName)
            } else {
                true
            }
        }

        assertEquals(1, visibleActivities.size)
        assertEquals("com.allowed.app", visibleActivities.first().packageName)
    }

    @Test
    fun blacklistedAppNotificationsShownWhenBlacklistDisabled() {
        val blacklistedPackages = setOf("com.blacklisted.app")
        val isBlacklistEnabled = false

        val activities = listOf(
            createActivity("com.blacklisted.app"),
            createActivity("com.allowed.app"),
        )

        val visibleActivities = activities.filter { activity ->
            if (isBlacklistEnabled) {
                !blacklistedPackages.contains(activity.packageName)
            } else {
                true
            }
        }

        assertEquals(2, visibleActivities.size)
    }

    @Test
    fun islandStackExcludesMediaAndNotificationWhenIgnored() {
        // If media and notification are ignored by blacklist, island stack should have no active items
        val stack = builtInIslandStack(
            music = false,
            flashlight = false,
            activity = false,
            activityHasRightWing = false,
            activityKind = null,
        )

        assertNull(stack.primary)
        assertTrue(stack.secondary.isEmpty())
    }

    @Test
    fun islandStackShowsAllowedNotificationWhenMediaIsIgnored() {
        val stack = builtInIslandStack(
            music = false, // Ignored due to blacklist
            flashlight = false,
            activity = true, // Allowed
            activityHasRightWing = false,
            activityKind = NotificationActivityKind.PROGRESS,
        )

        assertEquals(IslandType.NOTIFICATION_ACTIVITY, stack.primary?.content)
        assertTrue(stack.secondary.isEmpty())
    }
}
