package ca.saboor.larpdot.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayVisibilityPolicyTest {

    @Test
    fun overlayShowsWhenEnabledAndScreenUnlocked() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = true,
            isTorchOn = false,
            showTorchIsland = true,
            isScreenOn = true,
            hideWhenScreenOff = true,
            isLocked = false,
            hideOnLockScreen = true,
        )
        assertTrue(shouldShow)
    }

    @Test
    fun flashlightIslandShowsWhenMasterIsDisabledAndTorchIsOn() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = false,
            isTorchOn = true,
            showTorchIsland = true,
            isScreenOn = true,
            hideWhenScreenOff = true,
            isLocked = false,
            hideOnLockScreen = true,
        )
        assertTrue(shouldShow)
    }

    @Test
    fun flashlightIslandHidesWhenTorchIslandDisabledAndMasterDisabled() {
        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = false,
            isTorchOn = true,
            showTorchIsland = false,
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
            isTorchOn = false,
            showTorchIsland = true,
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
            isTorchOn = false,
            showTorchIsland = true,
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
            isTorchOn = false,
            showTorchIsland = true,
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
            isTorchOn = false,
            showTorchIsland = true,
            isScreenOn = true,
            hideWhenScreenOff = false,
            isLocked = true,
            hideOnLockScreen = false,
        )
        assertTrue(shouldShow)
    }
}
