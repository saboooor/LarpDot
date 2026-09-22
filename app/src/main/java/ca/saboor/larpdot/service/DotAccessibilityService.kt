package ca.saboor.larpdot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import ca.saboor.larpdot.flashlight.FlashlightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Accessibility service that hosts the Dynamic Island using TYPE_ACCESSIBILITY_OVERLAY.
 * This allows the overlay to stay visible on top of the notification pull-down shade
 * and draw on the lock screen (Keyguard), matching com.pryshedko.mtisland.
 */
class DotAccessibilityService : AccessibilityService() {
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            topPackage = pkg
            if (pkg == FlashlightController.PIXELLIGHT_PACKAGE) {
                lastPixelLightActivityTime = SystemClock.uptimeMillis()
                FlashlightController.onPixelLightActivityTriggered()
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        // Stop standard fallback overlay if it was running
        DotOverlayService.stop(this)

        if (!isReceiverRegistered) {
            val filter = IntentFilter("ca.saboor.larpdot.ACTION_TOGGLE_FLASHLIGHT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(flashlightReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(flashlightReceiver, filter)
            }
            isReceiverRegistered = true
        }

        val controller = IslandOverlayViewController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )
        overlayController = controller

        // Reactively display or hide based on user toggle or active flashlight state
        serviceScope.launch {
            combine(
                OverlayPreferences.isEnabledFlow,
                FlashlightController.isFlashlightOn,
                OverlayPreferences.showFlashlightIslandFlow,
            ) { isEnabled, isTorchOn, showTorchIsland ->
                isEnabled || (isTorchOn && showTorchIsland)
            }.collectLatest { shouldShow ->
                if (shouldShow) {
                    controller.show()
                } else {
                    controller.hide()
                }
            }
        }

        // If enabled in preferences or flashlight is on upon service start, show immediately
        val initialShow = OverlayPreferences.isOverlayEnabled(this) ||
                (FlashlightController.isFlashlightOn.value && OverlayPreferences.isShowFlashlightIslandEnabled(this))
        if (initialShow) {
            controller.show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance == this) instance = null
        _isServiceConnected.value = false
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(flashlightReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        overlayController?.destroy()
        overlayController = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        _isServiceConnected.value = false
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
        var instance: DotAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        @Volatile
        var topPackage: String? = null
            private set

        @Volatile
        var lastPixelLightActivityTime: Long = 0L
            private set

        fun isCameraAppInForeground(): Boolean {
            val pkg = topPackage ?: instance?.rootInActiveWindow?.packageName?.toString() ?: return false
            return FlashlightController.isKnownCameraPackage(pkg)
        }

        /**
         * Triggers opening the Android Notification Shade using Accessibility action,
         * or fallback via StatusBarManager reflection.
         */
        fun openNotificationShade(context: Context): Boolean {
            val service = instance
            if (service != null && service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) {
                return true
            }
            return try {
                @android.annotation.SuppressLint("WrongConstant")
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
                expandMethod.invoke(statusBarService)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun openQuickSettings(context: Context): Boolean {
            val service = instance
            if (service != null && service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
                return true
            }
            return try {
                val sbm = context.getSystemService("statusbar")
                val method = sbm.javaClass.getMethod("expandSettingsPanel")
                method.invoke(sbm)
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Checks if DotAccessibilityService is currently enabled in Android Accessibility Settings.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo ?: continue
                if (serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == DotAccessibilityService::class.java.name
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Opens Accessibility Settings directly to LarpDot's service config page.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent("com.samsung.accessibility.installed_service").apply {
                if (resolveActivity(context.packageManager) == null) {
                    action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                }
            }
            val serviceComponent = "${context.packageName}/${DotAccessibilityService::class.java.name}"
            val bundle = Bundle().apply {
                putString(":settings:fragment_args_key", serviceComponent)
            }
            intent.putExtra(":settings:fragment_args_key", serviceComponent)
            intent.putExtra(":settings:show_fragment_args", bundle)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
