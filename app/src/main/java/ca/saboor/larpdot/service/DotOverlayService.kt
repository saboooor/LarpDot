package ca.saboor.larpdot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import ca.saboor.larpdot.flashlight.FlashlightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Standard system overlay service that acts as a fallback when Accessibility Service is not enabled.
 * Uses TYPE_APPLICATION_OVERLAY in WindowManager.
 */
class DotOverlayService : Service() {
    private var overlayController: IslandOverlayViewController? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val flashlightBroadcast = FlashlightBroadcastRegistration()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // If accessibility service is already actively handling the overlay, do not create duplicate
        if (DotAccessibilityService.isServiceConnected.value) {
            stopSelf()
            return
        }

        ScreenStateTracker.init(this)
        ForegroundAppTracker.init(this)
        FullscreenStateTracker.updateOrientation(resources.configuration)

        flashlightBroadcast.register(this)

        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val controller = IslandOverlayViewController(this, overlayType)
        overlayController = controller

        serviceScope.launch {
            OverlayVisibilityPolicy.visibilityFlow()
                .combine(OverlayPreferences.isEnabledFlow) { visible, enabled -> (visible && enabled) to enabled }
                .distinctUntilChanged()
                .collectLatest { (shouldShow, enabled) ->
                    if (shouldShow) {
                        controller.show()
                    } else {
                        controller.hide()
                        if (!enabled && !OverlayPreferences.isEnabledFlow.value) stopSelf()
                    }
                }
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT") {
            FlashlightController.toggleFlashlight()
        }

        if (DotAccessibilityService.isServiceConnected.value) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!OverlayPreferences.isOverlayEnabled(this)) return START_NOT_STICKY

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        FullscreenStateTracker.updateOrientation(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        flashlightBroadcast.unregister(this)
        serviceJob.cancel()
        overlayController?.destroy()
        overlayController = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (!OverlayPreferences.isOverlayEnabled(context)) return
            try {
                val intent = Intent(context, DotOverlayService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DotOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
