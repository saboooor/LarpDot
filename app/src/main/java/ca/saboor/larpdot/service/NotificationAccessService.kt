package ca.saboor.larpdot.service

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState

/**
 * Service to listen for system notifications, active media sessions, and PixelLight state
 * when Notification Access is granted.
 */
class NotificationAccessService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        MediaPlaybackState.updateFromControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, NotificationAccessService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)

            // Check if PixelLight is currently active
            checkActivePixelLight()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Intercept PixelLight torch notification
        if (sbn.packageName == FlashlightController.PIXELLIGHT_PACKAGE) {
            val extras = sbn.notification?.extras
            val progress = extras?.getInt("android.progress", -1) ?: -1
            val max = extras?.getInt("android.progressMax", -1) ?: -1
            val turnOffIntent = sbn.notification?.actions?.firstOrNull {
                it.title?.toString()?.contains("Turn off", ignoreCase = true) == true
            }?.actionIntent
            FlashlightController.setPixelLightTurnOffPendingIntent(turnOffIntent)
            android.util.Log.i("NotificationAccessService", "PixelLight notification posted: progress=$progress, max=$max, hasTurnOffIntent=${turnOffIntent != null}")
            FlashlightController.onPixelLightNotificationPosted(progress, max)
        }

        try {
            // Extract artwork from media notification extras if available (hilight-studio pattern)
            val notif = sbn.notification ?: return
            val extras = notif.extras ?: return
            val isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true ||
                notif.category == Notification.CATEGORY_TRANSPORT

            if (isMedia) {
                val component = ComponentName(this, NotificationAccessService::class.java)
                val activeControllers = mediaSessionManager?.getActiveSessions(component)
                MediaPlaybackState.updateFromControllers(activeControllers)
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

                var artwork: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                        ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                        ?: (extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG) as? Bitmap)
                }

                if (artwork == null) {
                    val largeIcon = notif.getLargeIcon()
                    if (largeIcon != null) {
                        runCatching {
                            val drawable = largeIcon.loadDrawable(this)
                            if (drawable is BitmapDrawable) {
                                artwork = drawable.bitmap
                            } else if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                val bm = Bitmap.createBitmap(
                                    drawable.intrinsicWidth.coerceAtMost(256),
                                    drawable.intrinsicHeight.coerceAtMost(256),
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bm)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                artwork = bm
                            }
                        }
                    }
                }

                if (artwork != null || title != null) {
                    val resolvedAppName = try {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                    } catch (_: Exception) {
                        null
                    }
                    MediaPlaybackState.updateNotificationMetadata(
                        title = title,
                        artist = artist,
                        artwork = artwork,
                        packageName = sbn.packageName,
                        appName = resolvedAppName,
                    )
                }
            }
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == FlashlightController.PIXELLIGHT_PACKAGE) {
            android.util.Log.i("NotificationAccessService", "PixelLight notification removed")
            FlashlightController.onPixelLightNotificationRemoved()
        }

        try {
            val component = ComponentName(this, NotificationAccessService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn?.packageName == FlashlightController.PIXELLIGHT_PACKAGE) {
            android.util.Log.i("NotificationAccessService", "PixelLight notification removed (reason=$reason)")
            FlashlightController.onPixelLightNotificationRemoved()
        }
    }

    private fun checkActivePixelLight() {
        try {
            val active = activeNotifications ?: return
            val pixellightNotif = active.firstOrNull { it.packageName == FlashlightController.PIXELLIGHT_PACKAGE }
            if (pixellightNotif != null) {
                val extras = pixellightNotif.notification?.extras
                val progress = extras?.getInt("android.progress", -1) ?: -1
                val max = extras?.getInt("android.progressMax", -1) ?: -1
                val turnOffIntent = pixellightNotif.notification?.actions?.firstOrNull {
                    it.title?.toString()?.contains("Turn off", ignoreCase = true) == true
                }?.actionIntent
                FlashlightController.setPixelLightTurnOffPendingIntent(turnOffIntent)
                FlashlightController.onPixelLightNotificationPosted(progress, max)
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
