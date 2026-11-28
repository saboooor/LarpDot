package ca.saboor.larpdot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val flashlightBroadcast = FlashlightBroadcastRegistration()
    private var fullscreenCheckJob: Job? = null
    private var fullscreenConfirmationJob: Job? = null
    private var pendingFullscreenPackage: String? = null
    private var lastFullscreenCheckMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        ForegroundAppTracker.updateFromAccessibility(event)
        if (OverlayPreferences.hideInFullscreenFlow.value) {
            scheduleFullscreenCheck(event.packageName?.toString())
        }
        if (OverlayPreferences.islandOutlineModeFlow.value == OverlayPreferences.IslandOutlineMode.DYNAMIC) {
            IslandBackdropLuminanceTracker.requestSample()
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
        val isFullscreen = detectFullscreen() ?: return
        if (!isFullscreen) {
            fullscreenConfirmationJob?.cancel()
            fullscreenConfirmationJob = null
            FullscreenStateTracker.setFullscreen(false)
            return
        }
        if (FullscreenStateTracker.isFullscreenOrGame.value || fullscreenConfirmationJob?.isActive == true) return

        // The status bar window can disappear briefly as the notification shade closes.
        fullscreenConfirmationJob = serviceScope.launch {
            delay(FULLSCREEN_CONFIRMATION_MS)
            fullscreenConfirmationJob = null
            if (OverlayPreferences.hideInFullscreenFlow.value && detectFullscreen() == true) {
                FullscreenStateTracker.setFullscreen(true)
            }
        }
    }

    private fun detectFullscreen(): Boolean? =
        FullscreenStateTracker.detectFullscreen(
            context = this,
            windows = runCatching { windows }.getOrNull(),
            foregroundPackage = pendingFullscreenPackage,
        )

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceJob.cancel()
        serviceJob = SupervisorJob()
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

        // Apply the master toggle and device visibility settings.
        serviceScope.launch {
            OverlayVisibilityPolicy.visibilityFlow().distinctUntilChanged().collectLatest { shouldShow ->
                if (shouldShow) {
                    controller.show()
                } else {
                    controller.hide()
                }
            }
        }

        serviceScope.launch {
            OverlayPreferences.islandOutlineModeFlow.collectLatest { mode ->
                if (mode == OverlayPreferences.IslandOutlineMode.DYNAMIC) {
                    IslandBackdropLuminanceTracker.requestSample(delayMs = 0L)
                } else {
                    IslandBackdropLuminanceTracker.reset()
                }
            }
        }

        serviceScope.launch {
            ScreenStateTracker.isScreenOn.collectLatest { isScreenOn ->
                if (isScreenOn && OverlayPreferences.islandOutlineModeFlow.value == OverlayPreferences.IslandOutlineMode.DYNAMIC) {
                    IslandBackdropLuminanceTracker.requestSample()
                }
            }
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        FullscreenStateTracker.updateOrientation(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
        if (OverlayPreferences.islandOutlineModeFlow.value == OverlayPreferences.IslandOutlineMode.DYNAMIC) {
            IslandBackdropLuminanceTracker.requestSample()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        fullscreenCheckJob?.cancel()
        fullscreenCheckJob = null
        fullscreenConfirmationJob?.cancel()
        fullscreenConfirmationJob = null
        serviceJob.cancel()
        if (instance == this) instance = null
        _isServiceConnected.value = false
        FullscreenStateTracker.reset()
        IslandBackdropLuminanceTracker.reset()
        flashlightBroadcast.unregister(this)
        overlayController?.destroy()
        overlayController = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        fullscreenCheckJob?.cancel()
        fullscreenCheckJob = null
        fullscreenConfirmationJob?.cancel()
        fullscreenConfirmationJob = null
        if (instance == this) instance = null
        _isServiceConnected.value = false
        IslandBackdropLuminanceTracker.reset()
        flashlightBroadcast.unregister(this)
        serviceJob.cancel()
        overlayController?.destroy()
        overlayController = null
        super.onDestroy()
    }

    companion object {
        internal const val FULLSCREEN_CHECK_MIN_INTERVAL_MS = 120L
        private const val FULLSCREEN_CONFIRMATION_MS = 300L

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

        fun dismissNotificationShade(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else false
        }

        fun takeScreenshotBitmap(onResult: (Bitmap?) -> Unit) {
            val service = instance
            if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                onResult(null)
                return
            }
            runCatching {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val buffer = screenshot.hardwareBuffer
                            val bitmap = try {
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                wrapped?.copy(Bitmap.Config.ARGB_8888, false).also {
                                    wrapped?.recycle()
                                }
                            } catch (_: Throwable) {
                                null
                            } finally {
                                buffer.close()
                            }
                            onResult(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            onResult(null)
                        }
                    },
                )
            }.onFailure {
                onResult(null)
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
