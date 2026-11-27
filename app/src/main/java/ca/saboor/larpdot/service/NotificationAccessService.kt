package ca.saboor.larpdot.service

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.os.BundleCompat
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.notification.NotificationActivityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service to listen for system notifications and active media sessions
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

    private fun capArtworkBitmap(bitmap: Bitmap, maxDimensionPx: Int = 512): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimensionPx || maxSide <= 0) return bitmap
        val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return runCatching { bitmap.scale(targetW, targetH) }
            .getOrDefault(bitmap)
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (_isListenerConnected.value) MediaPlaybackState.updateFromControllers(controllers)
    }

    private val listenerComponent by lazy { ComponentName(this, NotificationAccessService::class.java) }

    private fun refreshMediaSessions() {
        val manager = mediaSessionManager ?: return
        MediaPlaybackState.updateFromControllers(manager.getActiveSessions(listenerComponent))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaPlaybackState.initialize(this)
        NotificationActivityState.initialize(this)
        _isListenerConnected.value = true
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent)
            refreshMediaSessions()
        }.onFailure { Log.w(TAG, "Could not connect to media sessions", it) }

        runCatching { activeNotifications }.onSuccess { notifications ->
            notifications?.forEach(::updateNotificationActivities)
        }.onFailure { Log.w(TAG, "Could not read active notifications", it) }
    }

    private fun updateNotificationActivities(sbn: StatusBarNotification) {
        runCatching { NotificationActivityState.update(this, sbn) }
            .onFailure { NotificationActivityState.remove(sbn.key) }
        runCatching { CustomIslandNotifications.post(sbn, this) }
            .onFailure { CustomIslandNotifications.remove(sbn.key) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || !_isListenerConnected.value) return
        updateNotificationActivities(sbn)

        try {
            val notif = sbn.notification ?: return
            val extras = notif.extras ?: return
            if (isMediaNotification(notif) && !MediaPlaybackState.isPackageBlacklisted(sbn.packageName)) {
                val sessionToken = BundleCompat.getParcelable(
                    extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java,
                )
                val controller = sessionToken?.let { runCatching { MediaController(this, it) }.getOrNull() }
                if (controller == null) {
                    refreshMediaSessions()
                } else if (!MediaPlaybackState.attachDirectController(controller)) {
                    return
                }

                val extraMediaActions = notif.actions.orEmpty().mapNotNull { action ->
                    val label = action.title?.toString()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val isExtraControl = listOf(
                        "favorite", "favourite", "like", "thumb", "shuffle", "repeat", "heart",
                    ).any { label.contains(it, ignoreCase = true) }
                    if (isExtraControl && action.actionIntent != null && action.remoteInputs.isNullOrEmpty()) {
                        label to action.actionIntent
                    } else {
                        null
                    }
                }
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

                var artwork: Bitmap? = BundleCompat.getParcelable(
                    extras,
                    Notification.EXTRA_PICTURE,
                    Bitmap::class.java,
                ) ?: BundleCompat.getParcelable(
                    extras,
                    Notification.EXTRA_LARGE_ICON_BIG,
                    Bitmap::class.java,
                )

                if (artwork == null) {
                    val largeIcon = notif.getLargeIcon()
                    if (largeIcon != null) {
                        runCatching {
                            val drawable = largeIcon.loadDrawable(this)
                            if (drawable is BitmapDrawable) {
                                artwork = drawable.bitmap
                            } else if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                val bm = createBitmap(
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
                    val boundedArtwork = artwork?.let { capArtworkBitmap(it) }
                    val resolvedAppName = try {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                    } catch (_: Exception) {
                        null
                    }
                    MediaPlaybackState.updateNotificationMetadata(
                        title = title,
                        artist = artist,
                        artwork = boundedArtwork,
                        packageName = sbn.packageName,
                        appName = resolvedAppName,
                    )
                }
                MediaPlaybackState.updateNotificationActions(sbn.packageName, extraMediaActions)
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
        sbn?.key?.let {
            NotificationActivityState.remove(it)
            CustomIslandNotifications.remove(it)
        }

        if (sbn?.notification?.let(::isMediaNotification) == true) {
            runCatching { refreshMediaSessions() }
        }
    }

    override fun onListenerDisconnected() {
        stopListening()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun stopListening() {
        if (!_isListenerConnected.value) return
        _isListenerConnected.value = false
        runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        mediaSessionManager = null
        CustomIslandNotifications.clear()
        NotificationActivityState.clearNotificationActivities()
        MediaPlaybackState.updateFromControllers(emptyList())
    }

    companion object {
        private const val TAG = "NotificationAccess"
        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()
    }
}
