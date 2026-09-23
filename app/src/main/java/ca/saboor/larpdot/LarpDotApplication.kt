package ca.saboor.larpdot

import android.app.Application
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenStateTracker

class LarpDotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenStateTracker.init(this)
        ForegroundAppTracker.init(this)
        OverlayPreferences.isOverlayEnabled(this)
        OverlayPreferences.isShowFlashlightIslandEnabled(this)
        OverlayPreferences.isHideWhenScreenOffEnabled(this)
        OverlayPreferences.isHideOnLockScreenEnabled(this)
        OverlayPreferences.isHideMusicWhenAppOpenEnabled(this)
        FlashlightController.init(this)
    }
}
