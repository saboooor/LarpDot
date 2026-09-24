package ca.saboor.larpdot.service

object OverlayVisibilityPolicy {
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
