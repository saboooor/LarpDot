package ca.saboor.larpdot.flashlight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ca.saboor.larpdot.MainActivity

/** Keeps our Camera2 session eligible to run after the flashlight screen leaves the foreground. */
class FlashlightBoostService : Service() {
    private var session: CameraBoostSession? = null
    private var requestId = -1L
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FlashlightController.turnOff()
            stopSelf()
            return START_NOT_STICKY
        }
        val requestedId = intent?.getLongExtra(EXTRA_REQUEST_ID, -1L) ?: -1L
        if (!FlashlightController.isBoostRequested) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A queued update from an older request must not stop a newer session.
        if (requestedId != FlashlightController.boostRequestId) return START_NOT_STICKY
        try {
            if (!foregroundStarted) {
                val notifications = getSystemService(NotificationManager::class.java)
                notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Flashlight boost", NotificationManager.IMPORTANCE_LOW))
                val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("Flashlight boost")
                    .setContentText("LarpDot is controlling the camera flashlight.")
                    .setContentIntent(open).setOngoing(true)
                    .addAction(0, "Turn off", stop).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(4102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                } else startForeground(4102, notification)
                foregroundStarted = true
            }
            if (requestId != requestedId) {
                session?.stop()
                session = null
                requestId = requestedId
            }
            if (session == null) {
                val config = FlashlightController.boostConfiguration ?: error("Boost is not supported on this camera.")
                session = CameraBoostSession(this, config.cameraId, config.maximum, config.outputSize,
                    onLevel = { level -> FlashlightController.onBoostLevel(requestedId, level) },
                    onError = { message -> FlashlightController.onBoostError(message, requestedId); stopSelf() },
                ).also { it.start(FlashlightController.torchStrength.value) }
            } else session?.strength(FlashlightController.torchStrength.value)
        } catch (error: Exception) {
            FlashlightController.onBoostError("Unable to run camera boost: ${error.message}", requestedId)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session?.stop()
        session = null
        FlashlightController.onBoostStopped(requestId)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "flashlight_boost"
        const val EXTRA_REQUEST_ID = "boost_request_id"
        const val ACTION_STOP = "ca.saboor.larpdot.BOOST_STOP"
    }
}
