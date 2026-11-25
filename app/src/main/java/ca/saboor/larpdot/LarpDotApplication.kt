package ca.saboor.larpdot

import android.app.Application
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenStateTracker

class LarpDotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CustomIslandStore.initialize(this)
        CustomIslandWidgets.initialize(this)
        ScreenStateTracker.init(this)
        ForegroundAppTracker.init(this)
        MediaPlaybackState.initialize(this)
        NotificationActivityState.initialize(this)
        OverlayPreferences.isOverlayEnabled(this)
        OverlayPreferences.getBubbleStyle(this)
        OverlayPreferences.areSmallerBubblesEnabled(this)
        OverlayPreferences.getProgressTimestampPlacement(this)
        OverlayPreferences.isShowFlashlightIslandEnabled(this)
        OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(this)
        OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(this)
        OverlayPreferences.isHideWhenScreenOffEnabled(this)
        OverlayPreferences.isHideOnLockScreenEnabled(this)
        OverlayPreferences.isHideInFullscreenEnabled(this)
        OverlayPreferences.isHideInLandscapeEnabled(this)
        OverlayPreferences.isHideMusicWhenAppOpenEnabled(this)
        OverlayPreferences.isAppBlacklistEnabled(this)
        OverlayPreferences.getBlacklistedPackages(this)
        OverlayPreferences.getNotificationActivitySettings(this)
        FlashlightController.init(this)
        ca.saboor.larpdot.service.FullscreenStateTracker.updateOrientation(resources.configuration)
        ca.saboor.larpdot.service.AudioOutputTracker.init(this)
        ca.saboor.larpdot.service.SystemEventManager.init(this)
        ca.saboor.larpdot.service.ScreenshotPocketManager.init(this)
    }
}
