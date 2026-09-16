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
import ca.saboor.larpdot.media.MediaPlaybackState

/**
 * Service to listen for system notifications and active media sessions when Notification Access is granted.
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val component = ComponentName(this, NotificationAccessService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)

            // Extract artwork from media notification extras if available (hilight-studio pattern)
            val notif = sbn.notification ?: return
            val extras = notif.extras ?: return
            val isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true ||
                notif.category == Notification.CATEGORY_TRANSPORT

            if (isMedia) {
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
                    MediaPlaybackState.updateNotificationMetadata(
                        title = title,
                        artist = artist,
                        artwork = artwork,
                        packageName = sbn.packageName,
                    )
                }
            }
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            val component = ComponentName(this, NotificationAccessService::class.java)
            val activeControllers = mediaSessionManager?.getActiveSessions(component)
            MediaPlaybackState.updateFromControllers(activeControllers)
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
