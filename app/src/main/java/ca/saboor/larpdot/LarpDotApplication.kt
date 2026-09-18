package ca.saboor.larpdot

import android.app.Application
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.OverlayPreferences

class LarpDotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OverlayPreferences.isOverlayEnabled(this)
        OverlayPreferences.isShowFlashlightIslandEnabled(this)
        FlashlightController.init(this)
    }
}
