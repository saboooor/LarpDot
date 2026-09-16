package ca.saboor.larpdot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.WindowManager

/**
 * Standard system overlay service that acts as a fallback when Accessibility Service is not enabled.
 * Uses TYPE_APPLICATION_OVERLAY in WindowManager.
 */
class DotOverlayService : Service() {
    private var overlayController: IslandOverlayViewController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // If accessibility service is already handling the overlay, do not create duplicate
        if (DotAccessibilityService.isServiceConnected.value || DotAccessibilityService.isAccessibilityEnabled(this)) {
            stopSelf()
            return
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayController = IslandOverlayViewController(this, overlayType).apply {
            show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DotAccessibilityService.isServiceConnected.value || DotAccessibilityService.isAccessibilityEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayController == null) {
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            overlayController = IslandOverlayViewController(this, overlayType).apply {
                show()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
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
