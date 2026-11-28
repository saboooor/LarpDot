package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.graphics.Color
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.OverlayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandModelTest {
    @Test
    fun everyProviderUsesTheSameTapAndHoldPreference() {
        for (type in IslandType.entries) {
            val actions = mutableListOf<String>()
            val model = IslandModel(type, { actions.add("activate") }, { actions.add("hold") })
            model.tap(true) { actions.add("expand") }
            model.hold(true) { actions.add("expand") }
            model.tap(false) { actions.add("expand") }
            model.hold(false) { actions.add("expand") }
            assertEquals(listOf("expand", "hold", "activate", "expand"), actions)
        }
    }

    @Test
    fun activateOrExpandFallsBackToExpandWhenNoSingleTapActionExists() {
        var expanded = 0
        activateOrExpand(action = { true }, expand = { expanded++ })
        assertEquals(0, expanded)

        activateOrExpand(action = { false }, expand = { expanded++ })
        assertEquals(1, expanded)
    }

    @Test
    fun secondaryMiniPillOpensOnSwipeRightAndHidesWhenAlreadyShown() {
        assertEquals(true, nextSecondaryMiniPillState(isMiniPill = false, canShowMiniPill = true, swipeRight = true))
        assertNull(nextSecondaryMiniPillState(isMiniPill = false, canShowMiniPill = true, swipeRight = false))
        assertNull(nextSecondaryMiniPillState(isMiniPill = false, canShowMiniPill = false, swipeRight = true))
        assertEquals(false, nextSecondaryMiniPillState(isMiniPill = true, canShowMiniPill = true, swipeRight = false))
        assertEquals(false, nextSecondaryMiniPillState(isMiniPill = true, canShowMiniPill = true, swipeRight = true))
    }

    @Test
    fun customSecondaryMiniPillExpandsWhenBothWingsHaveContent() {
        val withBothWings = CustomIsland(
            id = "custom-both",
            left = CustomIslandSlot(
                icon = CustomIslandIcon.MUSIC,
                width = CustomIslandPosition.LEFT.defaultWidth,
                height = CustomIslandPosition.LEFT.defaultHeight,
            ),
            right = CustomIslandSlot(
                text = "3:24",
                width = 48,
                height = CustomIslandPosition.RIGHT.defaultHeight,
            ),
        )
        val rightExtent = secondaryCustomRightInfoExtentDp(withBothWings, thicknessDp = 32f, rightTextWidthDp = 28f)
        assertEquals(28f, rightExtent, 0.01f)
        val miniPillWidth = secondaryItemWidthDp(
            type = IslandType.CUSTOM,
            thicknessDp = 32f,
            isMiniPill = true,
            flashlightStatus = "",
            activityStatus = null,
            customRightInfoExtentDp = rightExtent,
        )
        assertTrue(miniPillWidth > 32f + 0.5f)

        val leftOnly = withBothWings.copy(
            right = CustomIslandSlot(
                width = CustomIslandPosition.RIGHT.defaultWidth,
                height = CustomIslandPosition.RIGHT.defaultHeight,
            ),
        )
        val leftOnlyExtent = secondaryCustomRightInfoExtentDp(leftOnly, thicknessDp = 32f)
        assertEquals(0f, leftOnlyExtent, 0.01f)
        assertEquals(
            32f,
            secondaryItemWidthDp(
                type = IslandType.CUSTOM,
                thicknessDp = 32f,
                isMiniPill = true,
                flashlightStatus = "",
                activityStatus = null,
                customRightInfoExtentDp = leftOnlyExtent,
            ),
            0.01f,
        )
    }

    @Test
    fun smallerBubblesModeHandlesOffAlwaysAndMoreThanThreeActivities() {
        assertFalse(OverlayPreferences.SmallerBubblesMode.OFF.shouldShrink(0))
        assertFalse(OverlayPreferences.SmallerBubblesMode.OFF.shouldShrink(5))

        assertTrue(OverlayPreferences.SmallerBubblesMode.ALWAYS.shouldShrink(1))
        assertTrue(OverlayPreferences.SmallerBubblesMode.ALWAYS.shouldShrink(4))

        assertFalse(OverlayPreferences.SmallerBubblesMode.MORE_THAN_THREE.shouldShrink(2))
        assertFalse(OverlayPreferences.SmallerBubblesMode.MORE_THAN_THREE.shouldShrink(3))
        assertTrue(OverlayPreferences.SmallerBubblesMode.MORE_THAN_THREE.shouldShrink(4))
        assertTrue(OverlayPreferences.SmallerBubblesMode.MORE_THAN_THREE.shouldShrink(6))
    }

    @Test
    fun activityIconTintUsesAppColorWhenEnabledAndFallsBackToMaterialYou() {
        val materialYou = Color(0xFFD0BCFF)
        val wazeBlue = Color(0xFF33CCFF)
        val mapsGreenInt = 0xFF34A853.toInt()

        val mapsNav = NotificationActivityInfo(
            key = "nav-maps",
            packageName = "com.google.android.apps.maps",
            appName = "Maps",
            title = "Turn right",
            text = "5 min",
            kind = NotificationActivityKind.NAVIGATION,
            progress = null,
            progressMax = null,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = 1L,
            contentIntent = null,
            actions = emptyList(),
            notificationColor = mapsGreenInt,
        )
        // Toggle off -> Material You primary color
        assertEquals(
            materialYou,
            resolveActivityIconTint(mapsNav, useAppColor = false, appDominantColor = wazeBlue, primaryColor = materialYou),
        )
        // Toggle on -> prefers notification color when available
        assertEquals(
            Color(ca.saboor.larpdot.media.DominantColorExtractor.ensureColorVisible(mapsGreenInt)),
            resolveActivityIconTint(mapsNav, useAppColor = true, appDominantColor = wazeBlue, primaryColor = materialYou),
        )
        // Toggle on without notification color -> falls back to app icon dominant color (e.g. Waze blue)
        assertEquals(
            wazeBlue,
            resolveActivityIconTint(mapsNav.copy(notificationColor = null), useAppColor = true, appDominantColor = wazeBlue, primaryColor = materialYou),
        )
        // Toggle on with neither notification color nor dominant color -> falls back to Material You
        assertEquals(
            materialYou,
            resolveActivityIconTint(mapsNav.copy(notificationColor = null), useAppColor = true, appDominantColor = null, primaryColor = materialYou),
        )
        // System activities keep standard tint even when toggle is enabled
        val systemBluetooth = mapsNav.copy(
            packageName = "system",
            kind = NotificationActivityKind.BLUETOOTH,
            notificationColor = mapsGreenInt,
        )
        assertEquals(
            materialYou,
            resolveActivityIconTint(systemBluetooth, useAppColor = true, appDominantColor = wazeBlue, primaryColor = materialYou),
        )
    }

    @Test
    fun liveUpdateActivityShowsShortCriticalTextAndRespectsToggle() {
        val liveUpdate = NotificationActivityInfo(
            key = "live-uber",
            packageName = "com.ubercab",
            appName = "Uber",
            title = "Driver arriving",
            text = "White Camry • ABC 123",
            kind = NotificationActivityKind.LIVE_UPDATE,
            progress = 75,
            progressMax = 100,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = 1L,
            contentIntent = null,
            actions = emptyList(),
            shortCriticalText = "3 min",
        )
        assertEquals("3 min", compactStatusFallback(liveUpdate))
        assertTrue(hasCompactStatusContent(liveUpdate))
        assertEquals("75%", compactStatusFallback(liveUpdate.copy(shortCriticalText = null)))

        val enabledSettings = ca.saboor.larpdot.notification.NotificationActivitySettings(liveUpdates = true)
        val disabledSettings = ca.saboor.larpdot.notification.NotificationActivitySettings(liveUpdates = false)
        assertTrue(enabledSettings.allows(NotificationActivityKind.LIVE_UPDATE))
        assertFalse(disabledSettings.allows(NotificationActivityKind.LIVE_UPDATE))
    }
}
