package ca.saboor.larpdot.service

import ca.saboor.larpdot.flashlight.FlashlightController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

object OverlayVisibilityPolicy {
    fun visibilityFlow(): Flow<Boolean> = combine(
        OverlayPreferences.isEnabledFlow,
        FlashlightController.isFlashlightOn,
        OverlayPreferences.showFlashlightIslandFlow,
        ScreenStateTracker.isScreenOn,
        OverlayPreferences.hideWhenScreenOffFlow,
        ScreenStateTracker.isDeviceLocked,
        OverlayPreferences.hideOnLockScreenFlow,
    ) { values ->
        shouldShowOverlay(
            isEnabled = values[0],
            isTorchOn = values[1],
            showTorchIsland = values[2],
            isScreenOn = values[3],
            hideWhenScreenOff = values[4],
            isLocked = values[5],
            hideOnLockScreen = values[6],
        )
    }

    /**
     * Determines whether the island overlay window should be visible on screen based on
     * user master toggle, screen state, lock screen state, and active flashlight.
     */
    fun shouldShowOverlay(
        isEnabled: Boolean,
        isTorchOn: Boolean,
        showTorchIsland: Boolean,
        isScreenOn: Boolean,
        hideWhenScreenOff: Boolean,
        isLocked: Boolean,
        hideOnLockScreen: Boolean,
    ): Boolean {
        val baseCondition = isEnabled || (isTorchOn && showTorchIsland)
        val screenAllowed = if (hideWhenScreenOff) isScreenOn else true
        val lockAllowed = if (hideOnLockScreen) !isLocked else true
        return baseCondition && screenAllowed && lockAllowed
    }
}
