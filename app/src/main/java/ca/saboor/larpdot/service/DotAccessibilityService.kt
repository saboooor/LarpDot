package ca.saboor.larpdot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Accessibility service that hosts the Dynamic Island using TYPE_ACCESSIBILITY_OVERLAY.
 * This allows the overlay to stay visible on top of the notification pull-down shade
 * and draw on the lock screen (Keyguard), matching com.pryshedko.mtisland.
 */
class DotAccessibilityService : AccessibilityService() {
    private var overlayController: IslandOverlayViewController? = null
    private var serviceJob = Job()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val flashlightBroadcast = FlashlightBroadcastRegistration()
    private var fullscreenCheckJob: Job? = null
    private var pendingFullscreenPackage: String? = null
    private var lastFullscreenCheckMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        ForegroundAppTracker.updateFromAccessibility(event)
        if (OverlayPreferences.hideInFullscreenFlow.value) {
            scheduleFullscreenCheck(event.packageName?.toString())
        }
    }

    private fun scheduleFullscreenCheck(foregroundPackage: String?) {
        pendingFullscreenPackage = foregroundPackage
        val nowMs = SystemClock.elapsedRealtime()
        val delayMs = fullscreenCheckDelayMs(nowMs = nowMs, lastRunMs = lastFullscreenCheckMs)
        if (delayMs == 0L) {
            fullscreenCheckJob?.cancel()
            fullscreenCheckJob = null
            runFullscreenCheck(nowMs)
            return
        }
        if (fullscreenCheckJob?.isActive == true) return
        fullscreenCheckJob = serviceScope.launch {
            delay(delayMs)
            fullscreenCheckJob = null
            if (OverlayPreferences.hideInFullscreenFlow.value) {
                runFullscreenCheck(SystemClock.elapsedRealtime())
            }
        }
    }

    private fun runFullscreenCheck(nowMs: Long) {
        lastFullscreenCheckMs = nowMs
        FullscreenStateTracker.updateFromAccessibilityWindows(
            context = this,
            windows = runCatching { windows }.getOrNull(),
            foregroundPackage = pendingFullscreenPackage,
        )
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceJob.cancel()
        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
        instance = this
        _isServiceConnected.value = true

        ScreenStateTracker.init(this)
        ForegroundAppTracker.init(this)
        FullscreenStateTracker.updateOrientation(resources.configuration)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info

        // Stop standard fallback overlay if it was running
        DotOverlayService.stop(this)

        flashlightBroadcast.register(this)

        val controller = IslandOverlayViewController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )
        overlayController = controller

        // Reactively display or hide based on user toggle, screen on/off, lock screen, and active flashlight state
        serviceScope.launch {
            OverlayVisibilityPolicy.visibilityFlow().distinctUntilChanged().collectLatest { shouldShow ->
                if (shouldShow) {
                    controller.show()
                } else {
                    controller.hide()
                }
            }
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        FullscreenStateTracker.updateOrientation(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        fullscreenCheckJob?.cancel()
        fullscreenCheckJob = null
        serviceJob.cancel()
        if (instance == this) instance = null
        _isServiceConnected.value = false
        FullscreenStateTracker.reset()
        flashlightBroadcast.unregister(this)
        overlayController?.destroy()
        overlayController = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        fullscreenCheckJob?.cancel()
        fullscreenCheckJob = null
        if (instance == this) instance = null
        _isServiceConnected.value = false
        flashlightBroadcast.unregister(this)
        serviceJob.cancel()
        overlayController?.destroy()
        overlayController = null
        super.onDestroy()
    }

    companion object {
        internal const val FULLSCREEN_CHECK_MIN_INTERVAL_MS = 120L

        internal fun fullscreenCheckDelayMs(
            nowMs: Long,
            lastRunMs: Long,
            minIntervalMs: Long = FULLSCREEN_CHECK_MIN_INTERVAL_MS,
        ): Long {
            if (lastRunMs <= 0L) return 0L
            val elapsed = (nowMs - lastRunMs).coerceAtLeast(0L)
            return (minIntervalMs - elapsed).coerceAtLeast(0L)
        }

        var instance: DotAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

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

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun openAppInAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val showArgs = "${context.packageName}/${DotAccessibilityService::class.java.canonicalName}"
                    putExtra(":settings:fragment_args_key", showArgs)
                    val bundle = Bundle()
                    bundle.putString(":settings:fragment_args_key", showArgs)
                    putExtra(":settings:show_fragment_args", bundle)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAccessibilitySettings(context)
            }
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = enabledServices.split(":")
            val myService = "${context.packageName}/${DotAccessibilityService::class.java.canonicalName}"
            val mySimpleService = "${context.packageName}/${DotAccessibilityService::class.java.name}"
            return colonSplitter.any { it.equals(myService, ignoreCase = true) || it.equals(mySimpleService, ignoreCase = true) }
        }
    }
}
