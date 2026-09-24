package ca.saboor.larpdot.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.launch

/**
 * Standard system overlay service that acts as a fallback when Accessibility Service is not enabled.
 * Uses TYPE_APPLICATION_OVERLAY in WindowManager.
 */
class DotOverlayService : Service() {
    private var overlayController: IslandOverlayViewController? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val flashlightReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT") {
                FlashlightController.toggleFlashlight()
            }
        }
    }
    private var isReceiverRegistered = false

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

        if (!isReceiverRegistered) {
            val filter = IntentFilter("ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(flashlightReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(flashlightReceiver, filter)
            }
            isReceiverRegistered = true
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val controller = IslandOverlayViewController(this, overlayType)
        overlayController = controller

        serviceScope.launch {
            combine(
                OverlayPreferences.isEnabledFlow,
                FlashlightController.isFlashlightOn,
                OverlayPreferences.showFlashlightIslandFlow,
                ScreenStateTracker.isScreenOn,
                OverlayPreferences.hideWhenScreenOffFlow,
                ScreenStateTracker.isDeviceLocked,
                OverlayPreferences.hideOnLockScreenFlow,
            ) { values ->
                OverlayVisibilityPolicy.shouldShowOverlay(
                    isEnabled = values[0],
                    isTorchOn = values[1],
                    showTorchIsland = values[2],
                    isScreenOn = values[3],
                    hideWhenScreenOff = values[4],
                    isLocked = values[5],
                    hideOnLockScreen = values[6],
                )
            }.collectLatest { shouldShow ->
                if (shouldShow) {
                    controller.show()
                } else {
                    controller.hide()
                    if (!OverlayPreferences.isOverlayEnabled(this@DotOverlayService) &&
                        !FlashlightController.isFlashlightOn.value &&
                        ScreenStateTracker.isScreenOn.value
                    ) {
                        stopSelf()
                    }
                }
            }
        }

        val initialShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = OverlayPreferences.isOverlayEnabled(this),
            isTorchOn = FlashlightController.isFlashlightOn.value,
            showTorchIsland = OverlayPreferences.isShowFlashlightIslandEnabled(this),
            isScreenOn = ScreenStateTracker.isScreenOn.value,
            hideWhenScreenOff = OverlayPreferences.isHideWhenScreenOffEnabled(this),
            isLocked = ScreenStateTracker.isDeviceLocked.value,
            hideOnLockScreen = OverlayPreferences.isHideOnLockScreenEnabled(this),
        )
        if (initialShow) {
            controller.show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT") {
            FlashlightController.toggleFlashlight()
        }

        if (DotAccessibilityService.isServiceConnected.value) {
            stopSelf()
            return START_NOT_STICKY
        }

        val shouldShow = OverlayVisibilityPolicy.shouldShowOverlay(
            isEnabled = OverlayPreferences.isOverlayEnabled(this),
            isTorchOn = FlashlightController.isFlashlightOn.value,
            showTorchIsland = OverlayPreferences.isShowFlashlightIslandEnabled(this),
            isScreenOn = ScreenStateTracker.isScreenOn.value,
            hideWhenScreenOff = OverlayPreferences.isHideWhenScreenOffEnabled(this),
            isLocked = ScreenStateTracker.isDeviceLocked.value,
            hideOnLockScreen = OverlayPreferences.isHideOnLockScreenEnabled(this),
        )
        if (shouldShow) {
            overlayController?.show()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(flashlightReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        serviceJob.cancel()
        overlayController?.destroy()
        overlayController = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
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
