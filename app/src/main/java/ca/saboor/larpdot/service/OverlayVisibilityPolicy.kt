package ca.saboor.larpdot.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

object OverlayVisibilityPolicy {
    fun visibilityFlow(): Flow<Boolean> = combine(
        OverlayPreferences.isEnabledFlow,
        ScreenStateTracker.isScreenOn,
        OverlayPreferences.hideWhenScreenOffFlow,
        ScreenStateTracker.isDeviceLocked,
        OverlayPreferences.hideOnLockScreenFlow,
        FullscreenStateTracker.isFullscreenOrGame,
        OverlayPreferences.hideInFullscreenFlow,
        FullscreenStateTracker.isLandscape,
        OverlayPreferences.hideInLandscapeFlow,
    ) { values ->
        shouldShowOverlay(
            isEnabled = values[0],
            isScreenOn = values[1],
            hideWhenScreenOff = values[2],
            isLocked = values[3],
            hideOnLockScreen = values[4],
            isFullscreen = values[5],
            hideInFullscreen = values[6],
            isLandscape = values[7],
            hideInLandscape = values[8],
        )
    }

    /**
     * Determines whether the island overlay window should be visible on screen based on
     * user master toggle, screen state, lock screen state, and fullscreen/landscape rules.
     */
    fun shouldShowOverlay(
        isEnabled: Boolean,
        isScreenOn: Boolean,
        hideWhenScreenOff: Boolean,
        isLocked: Boolean,
        hideOnLockScreen: Boolean,
        isFullscreen: Boolean = false,
        hideInFullscreen: Boolean = false,
        isLandscape: Boolean = false,
        hideInLandscape: Boolean = false,
    ): Boolean {
        val screenAllowed = if (hideWhenScreenOff) isScreenOn else true
        val lockAllowed = if (hideOnLockScreen) !isLocked else true
        val fullscreenAllowed = if (hideInFullscreen) !isFullscreen else true
        val landscapeAllowed = if (hideInLandscape) !isLandscape else true
        return isEnabled && screenAllowed && lockAllowed && fullscreenAllowed && landscapeAllowed
    }
}
