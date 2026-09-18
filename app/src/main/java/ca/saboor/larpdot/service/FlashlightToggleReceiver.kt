package ca.saboor.larpdot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.saboor.larpdot.flashlight.FlashlightController

class FlashlightToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT") {
            if (context != null) {
                FlashlightController.init(context)
                FlashlightController.toggleFlashlight()
            }
        }
    }
}
