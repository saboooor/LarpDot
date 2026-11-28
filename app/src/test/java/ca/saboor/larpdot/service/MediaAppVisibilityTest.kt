package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import org.junit.Assert.*
import org.junit.Test

class MediaAppVisibilityTest {
    private fun activity(
        key: String,
        pkg: String,
        kind: NotificationActivityKind = NotificationActivityKind.NAVIGATION,
        postedAtMillis: Long = 1_000L,
    ) = NotificationActivityInfo(
        key = key,
        packageName = pkg,
        appName = pkg,
        title = "Title",
        text = "Text",
        kind = kind,
        progress = null,
        progressMax = null,
        progressIndeterminate = false,
        chronometerBaseElapsedRealtime = null,
        chronometerCountsDown = false,
        postedAtMillis = postedAtMillis,
        contentIntent = null,
        actions = emptyList(),
    )

    @Test fun onlyTheCurrentSessionOwnerHidesMedia() {
        assertTrue(shouldHideMediaApp(true, "com.spotify.music", "com.spotify.music"))
        assertFalse(shouldHideMediaApp(true, "com.google.android.apps.youtube.music", "com.spotify.music"))
        assertFalse(shouldHideMediaApp(true, "com.android.launcher", "com.spotify.music"))
    }

    @Test fun leavingPlayerOrChangingSessionMakesMediaVisibleAgain() {
        assertTrue(shouldHideMediaApp(true, "player.a", "player.a"))
        assertFalse(shouldHideMediaApp(true, "browser", "player.a"))
        assertFalse(shouldHideMediaApp(true, "player.a", "player.b"))
    }

    @Test fun disabledSettingAndUnknownPackagesNeverHideMedia() {
        assertFalse(shouldHideMediaApp(false, "player", "player"))
        assertFalse(shouldHideMediaApp(true, null, "player"))
        assertFalse(shouldHideMediaApp(true, "player", null))
        assertFalse(shouldHideMediaApp(true, "", ""))
    }

    @Test fun systemAndroidActivitiesAreNeverHiddenByRelatedAppSetting() {
        assertFalse(shouldHideForForegroundApp(true, "android", "android"))
        val charging = activity(key = "system_battery_charging", pkg = "android", kind = NotificationActivityKind.BATTERY_CHARGING)
        assertEquals(charging, filterActivityForForeground(charging, true, "android"))
    }

    @Test fun liveActivityHidesInItsOwnAppAndFallsBackToOtherAppsActivity() {
        val maps = activity(key = "maps", pkg = "com.google.android.apps.maps", kind = NotificationActivityKind.NAVIGATION, postedAtMillis = 2_000L)
        val uber = activity(key = "uber", pkg = "com.ubercab", kind = NotificationActivityKind.PROGRESS, postedAtMillis = 1_000L)
        val eligible = listOf(maps, uber)

        assertNull(filterActivityForForeground(maps, true, "com.google.android.apps.maps"))
        assertEquals(listOf(uber), filterActivitiesForForeground(eligible, true, "com.google.android.apps.maps"))
        assertEquals(uber, selectBestVisibleActivity(maps, eligible, true, "com.google.android.apps.maps"))
        assertEquals(maps, selectBestVisibleActivity(maps, eligible, true, "com.android.launcher"))
        assertEquals(maps, selectBestVisibleActivity(maps, eligible, false, "com.google.android.apps.maps"))
    }

    @Test fun customIslandsHideWhenTheirAssociatedOrRuntimeAppIsOpen() {
        val widgetIsland = CustomIsland(
            id = "widget",
            left = CustomIslandSlot(widgetId = 42, width = 64, height = 24),
            visibility = CustomIslandVisibility.ALWAYS,
        )
        val tapAppIsland = CustomIsland(
            id = "tap",
            left = CustomIslandSlot(icon = CustomIslandIcon.APPS, width = 64, height = 24),
            visibility = CustomIslandVisibility.ALWAYS,
            tapAction = CustomIslandTapAction.OPEN_APP,
            tapAppPackage = "com.example.notes",
        )
        val notifRuleIsland = CustomIsland(
            id = "notif_rule",
            left = CustomIslandSlot(icon = CustomIslandIcon.NOTIFICATIONS, width = 64, height = 24),
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.NOTIFICATION,
            notificationRule = CustomNotificationRule(packageName = "com.ubereats", titleContains = "Order"),
        )
        val musicIsland = CustomIsland(
            id = "music_custom",
            left = CustomIslandSlot(icon = CustomIslandIcon.MUSIC, width = 64, height = 24),
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.MUSIC,
        )
        val navIsland = CustomIsland(
            id = "nav_custom",
            left = CustomIslandSlot(icon = CustomIslandIcon.NAVIGATION, width = 64, height = 24),
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.LIVE_ACTIVITY,
            activityKind = NotificationActivityKind.NAVIGATION,
        )
        val unassociatedIsland = CustomIsland(
            id = "plain",
            left = CustomIslandSlot(icon = CustomIslandIcon.STAR, width = 64, height = 24),
            visibility = CustomIslandVisibility.ALWAYS,
        )
        val all = listOf(widgetIsland, tapAppIsland, notifRuleIsland, musicIsland, navIsland, unassociatedIsland)
        val liveNotifs = listOf(
            CustomNotification(
                key = "k1",
                packageName = "com.ubereats",
                channelId = "orders",
                title = "Order arriving",
                text = "2 min",
                postedAt = 1_000L,
                actions = emptyList(),
            ),
        )
        val mapsActivity = activity(key = "maps", pkg = "com.google.android.apps.maps", kind = NotificationActivityKind.NAVIGATION)
        val resolveWidget: (Int) -> String? = { id -> if (id == 42) "com.google.android.calendar" else null }

        // Opening widget provider app hides widget island only
        val inCalendar = filterCustomIslandsForForeground(all, true, "com.google.android.calendar", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertFalse(inCalendar.any { it.id == "widget" })
        assertTrue(inCalendar.any { it.id == "tap" })
        assertTrue(inCalendar.any { it.id == "plain" })

        // Opening tap-action app hides tap-app island
        val inNotes = filterCustomIslandsForForeground(all, true, "com.example.notes", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertFalse(inNotes.any { it.id == "tap" })
        assertTrue(inNotes.any { it.id == "widget" })

        // Opening notification rule's app hides conditional notification island
        val inUberEats = filterCustomIslandsForForeground(all, true, "com.ubereats", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertFalse(inUberEats.any { it.id == "notif_rule" })
        assertTrue(inUberEats.any { it.id == "music_custom" })

        // Opening active media player hides conditional music custom island
        val inSpotify = filterCustomIslandsForForeground(all, true, "com.spotify.music", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertFalse(inSpotify.any { it.id == "music_custom" })

        // Opening active navigation app hides conditional live-activity custom island
        val inMaps = filterCustomIslandsForForeground(all, true, "com.google.android.apps.maps", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertFalse(inMaps.any { it.id == "nav_custom" })

        // Disabling global toggle keeps all islands visible
        val disabled = filterCustomIslandsForForeground(all, false, "com.google.android.calendar", "com.spotify.music", listOf(mapsActivity), liveNotifs, resolveWidget)
        assertEquals(all.size, disabled.size)
    }
}
