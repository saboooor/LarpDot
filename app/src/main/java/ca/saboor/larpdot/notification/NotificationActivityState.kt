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
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ca.saboor.larpdot.media.MediaPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ca.saboor.larpdot.service.OverlayPreferences

enum class NotificationActivityKind {
    LIVE_UPDATE,
    PROGRESS,
    NAVIGATION,
    CALL,
    TIMER,
    BATTERY_CHARGING,
    BATTERY_LOW,
    SOUND_MODE,
    DND,
    BLUETOOTH,
    AUDIO_OUTPUT,
}

data class NotificationActivitySettings(
    val enabled: Boolean = true,
    val liveUpdates: Boolean = true,
    val progress: Boolean = true,
    val navigation: Boolean = true,
    val calls: Boolean = true,
    val timers: Boolean = true,
    val batteryCharging: Boolean = true,
    val batteryLow: Boolean = true,
    val soundMode: Boolean = true,
    val dnd: Boolean = true,
    val bluetooth: Boolean = true,
    val audioOutput: Boolean = true,
    val chargeLimitAlert: Boolean = false,
    val chargeLimitPercent: Int = 80,
    val lowBatteryThresholdPercent: Int = 15,
    val permanentCharging: Boolean = false,
    val permanentLowBattery: Boolean = false,
    val permanentSoundMode: Boolean = false,
    val permanentDnd: Boolean = false,
    val permanentBluetooth: Boolean = false,
    val permanentAudioOutput: Boolean = false,
    val autoExpandLiveUpdates: Boolean = false,
    val autoExpandLiveUpdatesOnChange: Boolean = false,
    val autoExpandProgress: Boolean = false,
    val autoExpandNavigation: Boolean = false,
    val autoExpandCalls: Boolean = false,
    val autoExpandTimers: Boolean = false,
    val autoExpandCharging: Boolean = false,
    val autoExpandLowBattery: Boolean = false,
    val autoExpandChargeLimit: Boolean = false,
    val autoExpandSoundMode: Boolean = false,
    val autoExpandDnd: Boolean = false,
    val autoExpandBluetooth: Boolean = false,
    val autoExpandAudioOutput: Boolean = false,
    val progressOutline: Boolean = true,
    val hideStatusBarLiveUpdates: Boolean = false,
    val minimizeStatusBarLiveUpdates: Boolean = false,
    val useAppColorForIcons: Boolean = false,
) {
    fun allows(kind: NotificationActivityKind): Boolean = enabled && when (kind) {
        NotificationActivityKind.LIVE_UPDATE -> liveUpdates
        NotificationActivityKind.PROGRESS -> progress
        NotificationActivityKind.NAVIGATION -> navigation
        NotificationActivityKind.CALL -> calls
        NotificationActivityKind.TIMER -> timers
        NotificationActivityKind.BATTERY_CHARGING -> batteryCharging
        NotificationActivityKind.BATTERY_LOW -> batteryLow
        NotificationActivityKind.SOUND_MODE -> soundMode
        NotificationActivityKind.DND -> dnd
        NotificationActivityKind.BLUETOOTH -> bluetooth
        NotificationActivityKind.AUDIO_OUTPUT -> audioOutput
    }

    fun shouldAutoExpandOnStart(info: NotificationActivityInfo): Boolean = when (info.kind) {
        NotificationActivityKind.LIVE_UPDATE -> autoExpandLiveUpdates
        NotificationActivityKind.PROGRESS -> autoExpandProgress
        NotificationActivityKind.NAVIGATION -> autoExpandNavigation
        NotificationActivityKind.CALL -> autoExpandCalls
        NotificationActivityKind.TIMER -> autoExpandTimers
        NotificationActivityKind.BATTERY_CHARGING ->
            if (info.key == "system_battery_charge_limit") autoExpandChargeLimit else autoExpandCharging
        NotificationActivityKind.BATTERY_LOW -> autoExpandLowBattery
        NotificationActivityKind.SOUND_MODE -> autoExpandSoundMode
        NotificationActivityKind.DND -> autoExpandDnd
        NotificationActivityKind.BLUETOOTH -> autoExpandBluetooth
        NotificationActivityKind.AUDIO_OUTPUT -> autoExpandAudioOutput
    }

    fun shouldAutoExpandOnUpdate(info: NotificationActivityInfo): Boolean = when (info.kind) {
        NotificationActivityKind.LIVE_UPDATE -> autoExpandLiveUpdatesOnChange
        NotificationActivityKind.SOUND_MODE -> autoExpandSoundMode
        NotificationActivityKind.AUDIO_OUTPUT -> autoExpandAudioOutput
        NotificationActivityKind.DND -> autoExpandDnd
        else -> false
    }

    fun allowsAutoExpand(info: NotificationActivityInfo): Boolean =
        shouldAutoExpandOnStart(info) || shouldAutoExpandOnUpdate(info)
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
    val concurrentStatuses: List<String> = emptyList(),
    val bluetoothDeviceType: String? = null,
    val bluetoothBatteryPercent: Int? = null,
    val isPinnedShortTerm: Boolean = false,
    val maneuverIcon: Bitmap? = null,
    val earbudBatteryLevels: EarbudBatteryLevels? = null,
    val notificationColor: Int? = null,
    val shortCriticalText: String? = null,
) {
    internal val isSystemActivity: Boolean
        get() = packageName == "android" && key.startsWith("system_")

    internal val pairedEarbudBatteryLevels: EarbudBatteryLevels?
        get() = earbudBatteryLevels?.takeIf { it.left != null && it.right != null }

    val progressFraction: Float?
        get() = if (progress != null && progressMax != null && progressMax > 0) {
            (progress.toFloat() / progressMax).coerceIn(0f, 1f)
        } else {
            null
        }
}

data class EarbudBatteryLevels(val left: Int?, val right: Int?)

/**
 * App-owned interpretation of ongoing system notifications. It deliberately uses only Android's
 * documented notification fields; no package-specific text dictionaries are involved.
 */
object NotificationActivityState {
    private const val MAX_CACHED_APP_ICONS = 64

    private val lock = Any()
    private val activities = linkedMapOf<String, NotificationActivityInfo>()
    private val appIconCache = LruCache<String, Bitmap>(MAX_CACHED_APP_ICONS)
    private var appContext: Context? = null

    private val _eligibleActivities = MutableStateFlow<List<NotificationActivityInfo>>(emptyList())
    internal val eligibleActivities = _eligibleActivities.asStateFlow()

    private val _current = MutableStateFlow<NotificationActivityInfo?>(null)
    val current: StateFlow<NotificationActivityInfo?> = _current.asStateFlow()

    private val _shortTermActivities = MutableStateFlow<List<NotificationActivityInfo>>(emptyList())
    val shortTermActivities = _shortTermActivities.asStateFlow()

    private var settings: NotificationActivitySettings = NotificationActivitySettings()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isBlacklistObserverStarted = false

    fun isPackageBlacklisted(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (!OverlayPreferences.appBlacklistEnabledFlow.value) return false
        return OverlayPreferences.blacklistedPackagesFlow.value.contains(packageName)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Hydrate blacklist flows once so high-frequency notification updates read strictly from memory.
        OverlayPreferences.isAppBlacklistEnabled(appContext!!)
        if (!isBlacklistObserverStarted) {
            isBlacklistObserverStarted = true
            scope.launch {
                combine(
                    OverlayPreferences.appBlacklistEnabledFlow,
                    OverlayPreferences.blacklistedPackagesFlow,
                ) { enabled, blacklisted ->
                    enabled to blacklisted
                }.collectLatest {
                    synchronized(lock) {
                        publishBestLocked()
                    }
                }
            }
        }
    }

    fun configure(newSettings: NotificationActivitySettings) {
        synchronized(lock) {
            settings = newSettings
            publishBestLocked()
        }
    }

    fun update(context: Context, sbn: StatusBarNotification) {
        appContext = context.applicationContext
        if (isPackageBlacklisted(sbn.packageName)) {
            synchronized(lock) {
                if (activities.remove(sbn.key) != null) {
                    publishBestLocked()
                }
            }
            return
        }
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

    fun remove(key: String) {
        synchronized(lock) {
            if (activities.remove(key) != null) {
                publishBestLocked()
            }
        }
    }

    internal fun clearNotificationActivities() {
        synchronized(lock) {
            activities.values.removeAll { !it.isSystemActivity }
            publishBestLocked()
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

    fun open(context: Context, activity: NotificationActivityInfo): Boolean =
        open(context, activity.packageName, activity.contentIntent)

    internal fun open(context: Context, packageName: String, contentIntent: PendingIntent?): Boolean {
        var launched = false

        if (contentIntent != null) {
            launched = runCatching {
                val bundle = if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                        )
                        .toBundle()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                        .toBundle()
                } else {
                    ActivityOptions.makeBasic().toBundle()
                }
                contentIntent.send(context, 0, null, null, null, null, bundle)
                true
            }.getOrDefault(false)
        }

        if (launched) return true
        return openApp(context, packageName)
    }

    internal fun openApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun perform(action: NotificationActivityAction) {
        send(action.pendingIntent)
    }

    private fun send(pendingIntent: PendingIntent?) {
        if (pendingIntent == null) return
        runCatching { pendingIntent.send() }
    }

    fun postSystemActivity(info: NotificationActivityInfo) {
        synchronized(lock) {
            activities[info.key] = info
            publishBestLocked()
        }
    }

    fun updateBluetoothBattery(key: String, batteryPercent: Int?) {
        synchronized(lock) {
            val current = activities[key] ?: return
            if (current.kind != NotificationActivityKind.BLUETOOTH) return
            activities[key] = current.copy(bluetoothBatteryPercent = batteryPercent)
            publishBestLocked()
        }
    }

    fun updateEarbudBatteries(key: String, levels: EarbudBatteryLevels?) {
        synchronized(lock) {
            val current = activities[key] ?: return
            if (current.kind != NotificationActivityKind.BLUETOOTH || current.earbudBatteryLevels == levels) return
            activities[key] = current.copy(earbudBatteryLevels = levels)
            publishBestLocked()
        }
    }

    fun updateBluetoothTitle(key: String, title: String) {
        if (title.isBlank()) return
        synchronized(lock) {
            val current = activities[key] ?: return
            if (current.kind != NotificationActivityKind.BLUETOOTH || current.title == title) return
            activities[key] = current.copy(title = title)
            publishBestLocked()
        }
    }

    fun removeSystemActivity(key: String) = remove(key)

    private fun publishBestLocked() {
        val eligible = activities.values.filter { settings.allows(it.kind) && !isPackageBlacklisted(it.packageName) }
        _eligibleActivities.value = eligible
        val shortTerm = eligible.filter { it.isSystemActivity }
        _shortTermActivities.value = shortTerm.sortedBy { it.key }
        _current.value = selectBestActivity(eligible)
    }

    internal fun selectBestActivity(eligible: List<NotificationActivityInfo>): NotificationActivityInfo? {
        if (eligible.isEmpty()) return null
        val nonShortTerm = eligible.filterNot { it.isSystemActivity }
        return (nonShortTerm.ifEmpty { eligible })
            .maxWithOrNull(
                compareBy<NotificationActivityInfo> { if (it.isPinnedShortTerm) 0 else priority(it.kind) }
                    .thenBy { it.postedAtMillis },
            )
    }

    private fun priority(kind: NotificationActivityKind): Int = when (kind) {
        NotificationActivityKind.CALL -> 6
        NotificationActivityKind.BATTERY_LOW -> 5
        NotificationActivityKind.BATTERY_CHARGING -> 5
        NotificationActivityKind.SOUND_MODE -> 4
        NotificationActivityKind.DND -> 4
        NotificationActivityKind.BLUETOOTH -> 4
        NotificationActivityKind.AUDIO_OUTPUT -> 4
        NotificationActivityKind.NAVIGATION -> 3
        NotificationActivityKind.LIVE_UPDATE -> 3
        NotificationActivityKind.TIMER -> 2
        NotificationActivityKind.PROGRESS -> 1
    }

    fun getOrLoadAppIcon(context: Context, packageName: String): Bitmap? {
        synchronized(appIconCache) {
            appIconCache.get(packageName)?.takeUnless { it.isRecycled }?.let { return it }
        }
        val loaded = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        }.getOrNull() ?: return null
        synchronized(appIconCache) {
            appIconCache.put(packageName, loaded)
        }
        return loaded
    }

    internal fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap {
        val maxDimension = (sizePx * 2).coerceAtLeast(sizePx)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val b = drawable.bitmap
            if (!b.isRecycled && b.width == b.height) {
                return if (b.width <= maxDimension) b
                else runCatching { b.scale(maxDimension, maxDimension) }.getOrDefault(b)
            }
        }
        val size = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtMost(maxDimension)
        } else {
            sizePx
        }
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    internal fun isMediaOrInternalNotification(notification: Notification, packageName: String): Boolean {
        if (packageName == "com.chiller3.pixellight") {
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

        val extras = notification.extras ?: return null
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasProgress = progressMax > 0 || indeterminate
        val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val shortCriticalText = extractShortCriticalText(notification)
        val isLiveUpdate = isLiveUpdateNotification(notification, shortCriticalText, isOngoing) ||
            ca.saboor.larpdot.service.ShizukuLiveUpdateSuppressor.isTrackedKey(sbn.key)

        val kind = when {
            notification.category == Notification.CATEGORY_CALL && isOngoing -> NotificationActivityKind.CALL
            notification.category == Notification.CATEGORY_NAVIGATION && isOngoing -> NotificationActivityKind.NAVIGATION
            usesChronometer && isOngoing -> NotificationActivityKind.TIMER
            isLiveUpdate -> NotificationActivityKind.LIVE_UPDATE
            hasProgress && (isOngoing || !sbn.isClearable) -> NotificationActivityKind.PROGRESS
            else -> return null
        }

        val title = firstText(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
        ) ?: applicationName(context, sbn.packageName)
        val text = deduplicateNotificationText(
            title,
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        )
        val whenElapsed = notification.`when`.takeIf { it > 0L }?.let { wallTime ->
            SystemClock.elapsedRealtime() + (wallTime - System.currentTimeMillis())
        }

        val appIcon = getOrLoadAppIcon(context, sbn.packageName)
        val maneuverIcon = if (kind == NotificationActivityKind.NAVIGATION) {
            runCatching {
                notification.getLargeIcon()?.loadDrawable(context)?.let { drawableToBitmap(it) }
            }.getOrNull()
        } else null

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
            maneuverIcon = maneuverIcon,
            notificationColor = notification.color.takeIf { it != Notification.COLOR_DEFAULT },
            shortCriticalText = shortCriticalText,
        )
    }

    private fun extractShortCriticalText(notification: Notification): String? {
        val direct = if (Build.VERSION.SDK_INT >= 36) {
            runCatching { notification.shortCriticalText }.getOrNull()
        } else null
        val fromExtras = notification.extras?.getCharSequence("android.shortCriticalText")?.toString()
        return (direct ?: fromExtras)?.trim()?.takeIf(String::isNotEmpty)?.take(24)
    }

    private fun isLiveUpdateNotification(
        notification: Notification,
        shortCriticalText: String?,
        isOngoing: Boolean,
    ): Boolean {
        val hasPromotedFlag = (notification.flags and 0x00040000) != 0
        val extras = notification.extras
        val requestedPromoted = extras?.getBoolean("android.requestPromotedOngoing", false) == true
        val isProgressStyle = extras?.getString(Notification.EXTRA_TEMPLATE)
            ?.contains("ProgressStyle", ignoreCase = true) == true
        return hasPromotedFlag || requestedPromoted || (isOngoing && isProgressStyle) || (isOngoing && shortCriticalText != null)
    }

    private fun firstText(vararg values: CharSequence?): String? = values.firstNotNullOfOrNull {
        it?.toString()?.trim()?.takeIf(String::isNotEmpty)
    }

    internal fun deduplicateNotificationText(title: String, vararg candidates: CharSequence?): String? {
        val cleanTitle = title.trim()
        return candidates.firstNotNullOfOrNull { raw ->
            val value = raw?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return@firstNotNullOfOrNull null
            if (value.equals(cleanTitle, ignoreCase = true)) null else value
        }
    }

    private fun applicationName(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
