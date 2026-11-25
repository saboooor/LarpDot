package ca.saboor.larpdot.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayVisibilityPolicyTest {

    @Test
    fun overlayShowsWhenEnabledAndScreenUnlocked() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isScreenOn = true,
            hideWhenScreenOff = true,
            isLocked = false,
            hideOnLockScreen = true,
        )
        assertTrue(shouldShow)
    }

    @Test
    fun overlayStaysHiddenWhenMasterIsDisabledEvenIfDeviceAllowsIt() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = false,
            isScreenOn = true,
            hideWhenScreenOff = true,
            isLocked = false,
            hideOnLockScreen = true,
        )
        assertFalse(shouldShow)
    }

    @Test
    fun overlayHidesWhenScreenOffIfConfigured() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isScreenOn = false,
            hideWhenScreenOff = true,
            isLocked = false,
            hideOnLockScreen = false,
        )
        assertFalse(shouldShow)
    }

    @Test
    fun overlayShowsWhenScreenOffIfNotConfiguredToHide() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isScreenOn = false,
            hideWhenScreenOff = false,
            isLocked = false,
            hideOnLockScreen = false,
        )
        assertTrue(shouldShow)
    }

    @Test
    fun overlayHidesOnLockScreenIfConfigured() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isScreenOn = true,
            hideWhenScreenOff = false,
            isLocked = true,
            hideOnLockScreen = true,
        )
        assertFalse(shouldShow)
    }

    @Test
    fun overlayShowsOnLockScreenIfNotConfiguredToHide() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isScreenOn = true,
            hideWhenScreenOff = false,
            isLocked = true,
            hideOnLockScreen = false,
        )
        assertTrue(shouldShow)
    }

    @Test
    fun overlayHidesInFullscreenWhenConfigured() {
        assertFalse(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isFullscreen = true,
                hideInFullscreen = true,
            )
        )
        assertTrue(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isFullscreen = true,
                hideInFullscreen = false,
            )
        )
    }

    @Test
    fun overlayHidesInLandscapeWhenConfigured() {
        assertFalse(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isLandscape = true,
                hideInLandscape = true,
            )
        )
        assertTrue(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isLandscape = false,
                hideInLandscape = true,
            )
        )
    }

    @Test
    fun overlayHidesDuringDoNotDisturbWhenConfigured() {
        assertFalse(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isDndActive = true,
                hideInDnd = true,
            )
        )
        assertTrue(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isDndActive = true,
                hideInDnd = false,
            )
        )
        assertTrue(
            OverlayVisibilityPolicy.shouldShowOverlay(
                isEnabled = true,
                isScreenOn = true,
                hideWhenScreenOff = true,
                isLocked = false,
                hideOnLockScreen = false,
                isDndActive = false,
                hideInDnd = true,
            )
        )
    }
}
