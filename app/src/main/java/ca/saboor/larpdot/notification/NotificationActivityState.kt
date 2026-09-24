package ca.saboor.larpdot.notification

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationActivityKind {
    PROGRESS,
    NAVIGATION,
    CALL,
    TIMER,
}

data class NotificationActivitySettings(
    val enabled: Boolean = true,
    val progress: Boolean = true,
    val navigation: Boolean = true,
    val calls: Boolean = true,
    val timers: Boolean = true,
) {
    fun allows(kind: NotificationActivityKind): Boolean = enabled && when (kind) {
        NotificationActivityKind.PROGRESS -> progress
        NotificationActivityKind.NAVIGATION -> navigation
        NotificationActivityKind.CALL -> calls
        NotificationActivityKind.TIMER -> timers
    }
}

data class NotificationActivityAction(
    val title: String,
    internal val pendingIntent: PendingIntent,
)

data class NotificationActivityInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String?,
    val kind: NotificationActivityKind,
    val progress: Int?,
    val progressMax: Int?,
    val progressIndeterminate: Boolean,
    val chronometerBaseElapsedRealtime: Long?,
    val chronometerCountsDown: Boolean,
    val postedAtMillis: Long,
    val contentIntent: PendingIntent?,
    val actions: List<NotificationActivityAction>,
    val appIcon: Bitmap? = null,
) {
    val progressFraction: Float?
        get() = if (progress != null && progressMax != null && progressMax > 0) {
            (progress.toFloat() / progressMax).coerceIn(0f, 1f)
        } else {
            null
        }
}

/**
 * App-owned interpretation of ongoing system notifications. It deliberately uses only Android's
 * documented notification fields; no package-specific text dictionaries are involved.
 */
object NotificationActivityState {
    private val lock = Any()
    private val activities = linkedMapOf<String, NotificationActivityInfo>()
    private val appIconCache = ConcurrentHashMap<String, Bitmap>()
    private var appContext: Context? = null

    private val _current = MutableStateFlow<NotificationActivityInfo?>(null)
    val current: StateFlow<NotificationActivityInfo?> = _current.asStateFlow()

    private var settings: NotificationActivitySettings = NotificationActivitySettings()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun configure(newSettings: NotificationActivitySettings) {
        synchronized(lock) {
            settings = newSettings
            publishBestLocked()
        }
    }

    fun updateSettings(newSettings: NotificationActivitySettings) = configure(newSettings)

    fun update(context: Context, sbn: StatusBarNotification) {
        appContext = context.applicationContext
        val parsed = parse(context, sbn)
        synchronized(lock) {
            if (parsed == null) {
                if (activities.remove(sbn.key) != null) {
                    publishBestLocked()
                }
                return
            }
            activities[sbn.key] = parsed
            publishBestLocked()
        }
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) = update(context, sbn)

    fun remove(key: String) {
        synchronized(lock) {
            if (activities.remove(key) != null) {
                publishBestLocked()
            }
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) = remove(sbn.key)

    fun clear() {
        synchronized(lock) {
            activities.clear()
            _current.value = null
        }
    }

    fun open(activity: NotificationActivityInfo) {
        val ctx = appContext
        if (ctx != null) {
            open(ctx, activity)
        } else {
            send(activity.contentIntent)
        }
    }

    fun open(context: Context, activity: NotificationActivityInfo) {
        var launched = false

        if (activity.contentIntent != null) {
            launched = runCatching {
                val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                        .toBundle()
                } else {
                    ActivityOptions.makeBasic().toBundle()
                }
                activity.contentIntent.send(context, 0, null, null, null, null, bundle)
                true
            }.getOrDefault(false)
        }

        // If sending the pendingIntent failed or contentIntent was null,
        // fall back to launching the target package's main launcher activity.
        if (!launched && activity.packageName.isNotBlank()) {
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(activity.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(intent)
                }
            }
        }
    }

    fun perform(action: NotificationActivityAction) {
        send(action.pendingIntent)
    }

    private fun send(pendingIntent: PendingIntent?) {
        if (pendingIntent == null) return
        runCatching { pendingIntent.send() }
    }

    private fun publishBestLocked() {
        _current.value = activities.values.asSequence()
            .filter { settings.allows(it.kind) }
            .maxWithOrNull(
            compareBy<NotificationActivityInfo> { priority(it.kind) }
                .thenBy { it.postedAtMillis },
            )
    }

    private fun priority(kind: NotificationActivityKind): Int = when (kind) {
        NotificationActivityKind.CALL -> 4
        NotificationActivityKind.NAVIGATION -> 3
        NotificationActivityKind.TIMER -> 2
        NotificationActivityKind.PROGRESS -> 1
    }

    fun getOrLoadAppIcon(context: Context, packageName: String): Bitmap? {
        return appIconCache.getOrPut(packageName) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable)
            }.getOrNull() ?: return null
        }
    }

    internal fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val b = drawable.bitmap
            if (b.width == b.height) {
                return b
            }
        }
        val size = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight)
        } else {
            sizePx
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    internal fun isMediaOrInternalNotification(notification: Notification, packageName: String): Boolean {
        if (packageName == FlashlightController.PIXELLIGHT_PACKAGE) {
            return true
        }

        if (notification.category == Notification.CATEGORY_TRANSPORT) {
            return true
        }

        val extras = notification.extras
        if (extras != null) {
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.containsKey("android.mediaSession") ||
                extras.containsKey("android.compactActions")
            ) {
                return true
            }

            val template = extras.getString(Notification.EXTRA_TEMPLATE)
            if (template != null && template.contains("MediaStyle", ignoreCase = true)) {
                return true
            }
        }

        val activeMediaPackage = MediaPlaybackState.currentTrack.value.playerPackageName
        if (!activeMediaPackage.isNullOrEmpty() && packageName == activeMediaPackage) {
            return true
        }

        val mediaActionKeywords = setOf(
            "play", "pause", "resume", "stop", "next", "previous", "prev",
            "skip", "rewind", "fast forward", "like", "dislike",
            "thumbs up", "thumbs down", "favorite",
        )
        val actions = notification.actions
        if (actions != null && actions.isNotEmpty()) {
            val hasMediaAction = actions.any { action ->
                val title = action.title?.toString()?.lowercase()?.trim().orEmpty()
                mediaActionKeywords.any { keyword -> title == keyword || title.startsWith("$keyword ") }
            }
            val isLikelyMediaApp = packageName.contains("music", ignoreCase = true) ||
                packageName.contains("youtube", ignoreCase = true) ||
                packageName.contains("audio", ignoreCase = true) ||
                packageName.contains("podcast", ignoreCase = true) ||
                packageName.contains("spotify", ignoreCase = true) ||
                packageName.contains("player", ignoreCase = true)

            if (hasMediaAction && isLikelyMediaApp) {
                return true
            }
        }

        return false
    }

    private fun parse(context: Context, sbn: StatusBarNotification): NotificationActivityInfo? {
        if (sbn.packageName == context.packageName) return null
        val notification = sbn.notification ?: return null
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        if (isMediaOrInternalNotification(notification, sbn.packageName)) return null

        val extras = notification.extras
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasProgress = progressMax > 0 || indeterminate
        val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

        val kind = when {
            notification.category == Notification.CATEGORY_CALL && isOngoing -> NotificationActivityKind.CALL
            notification.category == Notification.CATEGORY_NAVIGATION && isOngoing -> NotificationActivityKind.NAVIGATION
            usesChronometer && isOngoing -> NotificationActivityKind.TIMER
            hasProgress && (isOngoing || !sbn.isClearable) -> NotificationActivityKind.PROGRESS
            else -> return null
        }

        val title = firstText(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
        ) ?: applicationName(context, sbn.packageName)
        val text = firstText(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        )
        val whenElapsed = notification.`when`.takeIf { it > 0L }?.let { wallTime ->
            SystemClock.elapsedRealtime() + (wallTime - System.currentTimeMillis())
        }

        val appIcon = getOrLoadAppIcon(context, sbn.packageName)

        return NotificationActivityInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = applicationName(context, sbn.packageName),
            title = title,
            text = text,
            kind = kind,
            progress = progress.takeIf { hasProgress && !indeterminate },
            progressMax = progressMax.takeIf { it > 0 },
            progressIndeterminate = indeterminate,
            chronometerBaseElapsedRealtime = whenElapsed.takeIf { usesChronometer || kind == NotificationActivityKind.CALL },
            chronometerCountsDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
            postedAtMillis = sbn.postTime,
            contentIntent = notification.contentIntent,
            actions = notification.actions.orEmpty().mapNotNull { action ->
                val label = action.title?.toString()?.trim().orEmpty()
                if (label.isEmpty() || action.actionIntent == null) null
                else NotificationActivityAction(label, action.actionIntent)
            }.take(3),
            appIcon = appIcon,
        )
    }

    private fun firstText(vararg values: CharSequence?): String? = values.firstNotNullOfOrNull {
        it?.toString()?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun applicationName(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
