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
import ca.saboor.larpdot.notification.NotificationActivityState

/**
 * Service to listen for system notifications, active media sessions, and PixelLight state
 * when Notification Access is granted.
 */
class NotificationAccessService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null

    private fun isMediaNotification(notification: Notification): Boolean {
        val extras = notification.extras ?: return false
        return extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true ||
            notification.category == Notification.CATEGORY_TRANSPORT
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        MediaPlaybackState.updateFromControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            MediaPlaybackState.initialize(this)
            NotificationActivityState.initialize(this)
            mediaSessionManager = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, NotificationAccessService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)

            // Check if PixelLight is currently active
            checkActivePixelLight()
            activeNotifications?.forEach { NotificationActivityState.update(this, it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        NotificationActivityState.update(this, sbn)

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
            if (isMediaNotification(notif)) {
                if (MediaPlaybackState.isPackageBlacklisted(sbn.packageName)) {
                    return
                }
                val sessionToken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? android.media.session.MediaSession.Token
                }

                if (sessionToken != null) {
                    try {
                        val directController = android.media.session.MediaController(this, sessionToken)
                        MediaPlaybackState.attachDirectController(directController)
                    } catch (_: Exception) {
                        val component = ComponentName(this, NotificationAccessService::class.java)
                        val activeControllers = mediaSessionManager?.getActiveSessions(component)
                        MediaPlaybackState.updateFromControllers(activeControllers)
                    }
                } else {
                    val component = ComponentName(this, NotificationAccessService::class.java)
                    val activeControllers = mediaSessionManager?.getActiveSessions(component)
                    MediaPlaybackState.updateFromControllers(activeControllers)
                }

                val extraMediaActions = notif.actions.orEmpty().mapNotNull { action ->
                    val label = action.title?.toString()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val isExtraControl = listOf(
                        "favorite", "favourite", "like", "thumb", "shuffle", "repeat", "heart",
                    ).any { label.contains(it, ignoreCase = true) }
                    if (isExtraControl && action.actionIntent != null) {
                        label to action.actionIntent
                    } else {
                        null
                    }
                }
                MediaPlaybackState.updateNotificationActions(sbn.packageName, extraMediaActions)

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
        handleNotificationRemoved(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        handleNotificationRemoved(sbn)
    }

    private fun handleNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(NotificationActivityState::remove)
        if (sbn?.packageName == FlashlightController.PIXELLIGHT_PACKAGE) {
            android.util.Log.i("NotificationAccessService", "PixelLight notification removed")
            FlashlightController.onPixelLightNotificationRemoved()
        }

        if (sbn?.notification?.let(::isMediaNotification) != true) return
        try {
            val component = ComponentName(this, NotificationAccessService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)
        } catch (_: Exception) {}
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
        NotificationActivityState.clear()
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
