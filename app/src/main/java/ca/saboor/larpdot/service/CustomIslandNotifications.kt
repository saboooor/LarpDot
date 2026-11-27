package ca.saboor.larpdot.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.os.BundleCompat
import android.service.notification.StatusBarNotification
import ca.saboor.larpdot.notification.NotificationActivityAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PATTERN_CACHE_MAX_SIZE = 64
private val notificationPatternCacheLock = Any()
private val notificationPatternCache = object : LinkedHashMap<String, Result<Regex>>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result<Regex>>?): Boolean =
        size > PATTERN_CACHE_MAX_SIZE
}

private fun isRegexPattern(pattern: String): Boolean =
    pattern.length >= 2 && pattern.startsWith("/") && pattern.endsWith("/")

private fun compileNotificationPattern(pattern: String): Result<Regex> = synchronized(notificationPatternCacheLock) {
    notificationPatternCache.getOrPut(pattern) {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        if (isRegexPattern(pattern)) {
            runCatching { Regex(pattern.substring(1, pattern.length - 1), options) }
        } else {
            val regexSource = buildString(pattern.length * 2) {
                var i = 0
                while (i < pattern.length) {
                    val c = pattern[i]
                    when {
                        c == '\\' && i + 1 < pattern.length && pattern[i + 1] in charArrayOf('*', '?', '\\') -> {
                            append(Regex.escape(pattern[i + 1].toString()))
                            i += 2
                        }
                        c == '*' -> {
                            append(".*?")
                            i++
                        }
                        c == '?' -> {
                            append('.')
                            i++
                        }
                        else -> {
                            append(Regex.escape(c.toString()))
                            i++
                        }
                    }
                }
            }
            runCatching { Regex(regexSource, options) }
        }
    }
}

internal fun isValidNotificationPattern(pattern: String): Boolean {
    if (pattern.isEmpty() || !isRegexPattern(pattern)) return true
    if (pattern.length == 2) return true
    return compileNotificationPattern(pattern).isSuccess
}

internal fun matchesNotificationPattern(value: String, pattern: String): Boolean {
    if (pattern.isEmpty()) return true
    if (!isRegexPattern(pattern) && '*' !in pattern && '?' !in pattern && '\\' !in pattern) {
        return value.contains(pattern, ignoreCase = true)
    }
    if (isRegexPattern(pattern) && pattern.length == 2) return true
    return compileNotificationPattern(pattern).getOrNull()?.containsMatchIn(value) == true
}

internal data class CustomNotificationRule(
    val packageName: String = "",
    val channelId: String = "",
    val titleContains: String = "",
    val textContains: String = "",
    val matchChannel: Boolean = true,
) {
    fun matches(item: CustomNotification) = packageName.isNotBlank() && item.packageName == packageName &&
        (!matchChannel || channelId.isBlank() || item.channelId == channelId) &&
        matchesNotificationPattern(item.title, titleContains) &&
        matchesNotificationPattern(item.text, textContains)
}

internal data class CustomNotification(
    val key: String,
    val packageName: String,
    val channelId: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val actions: List<NotificationActivityAction>,
    val contentIntent: PendingIntent? = null,
    val artwork: CustomNotificationArtwork? = null,
    val contentView: RemoteViews? = null,
    val actionLabels: List<String> = actions.map { it.title },
    val smallIcon: Icon? = null,
    val notificationColor: Int? = null,
)

internal fun loadNotificationSmallIconBitmap(context: Context, icon: Icon): Bitmap? = runCatching {
    val drawable = icon.loadDrawable(context) ?: return@runCatching null
    val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val scale = minOf(1f, 96f / maxOf(intrinsicWidth, intrinsicHeight))
    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(24)
    val height = (intrinsicHeight * scale).toInt().coerceAtLeast(24)
    drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
}.getOrNull()

internal data class CustomNotificationArtwork(
    val picture: Bitmap?,
    val pictureIcon: Icon?,
    val largeIcon: Icon?,
    val attachmentUri: Uri? = null,
) {
    fun load(context: Context): Bitmap? {
        val source = picture?.takeUnless(Bitmap::isRecycled)
            ?: pictureIcon?.let { loadIcon(context, it) }
            ?: (if (Build.VERSION.SDK_INT >= 28) attachmentUri?.let { loadAttachment(context, it) } else null)
            ?: largeIcon?.let { loadIcon(context, it) }
            ?: return null
        val scale = 512f / maxOf(source.width, source.height)
        return if (scale >= 1f) source else source.scale(
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
        )
    }

    @RequiresApi(28)
    private fun loadAttachment(context: Context, uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, 512f / maxOf(width, height))
            decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
        }
    }.getOrNull()

    private fun loadIcon(context: Context, icon: Icon): Bitmap? = runCatching {
        val drawable = icon.loadDrawable(context) ?: return@runCatching null
        if (drawable is BitmapDrawable) return@runCatching drawable.bitmap?.takeUnless(Bitmap::isRecycled)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = minOf(1f, 512f / maxOf(width, height))
        drawable.toBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
        )
    }.getOrNull()
}

internal object CustomIslandNotifications {
    private val activeItems = MutableStateFlow<Map<String, CustomNotification>>(emptyMap())
    val active = activeItems.asStateFlow()
    private val recentItems = MutableStateFlow<List<CustomNotification>>(emptyList())
    val recent = recentItems.asStateFlow()

    fun post(sbn: StatusBarNotification, context: Context? = null) {
        val notification = sbn.notification
        if (notification.visibility == Notification.VISIBILITY_SECRET || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            remove(sbn.key)
            recentItems.update { it.filterNot { item -> item.key == sbn.key } }
            return
        }
        val extras = notification.extras
        val artwork = runCatching { notificationArtwork(notification) }.getOrNull()
        val contentView = runCatching { notificationContentView(notification, context) }.getOrNull()
        val smallIcon = runCatching { notification.smallIcon }.getOrNull()
        val notificationColor = notification.color.takeIf { it != Notification.COLOR_DEFAULT }
        val item = CustomNotification(
            sbn.key, sbn.packageName, notification.channelId.orEmpty(),
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(250),
            (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString().orEmpty().take(1000),
            sbn.postTime,
            notification.actions.orEmpty().mapNotNull { action ->
                val intent = action.actionIntent ?: return@mapNotNull null
                val title = action.title?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                if (!action.remoteInputs.isNullOrEmpty()) return@mapNotNull null
                NotificationActivityAction(title, intent)
            },
            contentIntent = notification.contentIntent,
            artwork = artwork,
            contentView = contentView,
            smallIcon = smallIcon,
            notificationColor = notificationColor,
        )
        activeItems.update { it + (item.key to item) }
        recentItems.update { items ->
            (items.filterNot { it.key == item.key } + item.copy(
                actions = emptyList(),
                contentIntent = null,
                artwork = null,
                contentView = null,
                smallIcon = null,
            ))
                .sortedByDescending { it.postedAt }.take(40)
        }
    }

    @Suppress("DEPRECATION")
    private fun notificationContentView(notification: Notification, context: Context?): RemoteViews? {
        val direct = notification.bigContentView ?: notification.contentView ?: notification.headsUpContentView
        if (direct != null) return direct
        if (context == null) return null
        return runCatching {
            val builder = Notification.Builder.recoverBuilder(context, notification)
            builder.createBigContentView() ?: builder.createContentView()
        }.getOrNull()
    }

    fun remove(key: String) { activeItems.update { it - key } }

    fun clear() {
        activeItems.value = emptyMap()
        recentItems.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private fun notificationArtwork(notification: Notification): CustomNotificationArtwork? {
        val extras = notification.extras
        val attachment = if (Build.VERSION.SDK_INT >= 30) {
            Notification.MessagingStyle.Message
                .getMessagesFromBundleArray(extras?.getParcelableArray(Notification.EXTRA_MESSAGES))
                .lastOrNull { it.dataMimeType?.startsWith("image/") == true && it.dataUri?.scheme == "content" }
                ?.dataUri
        } else null
        return CustomNotificationArtwork(
            picture = extras?.let { BundleCompat.getParcelable(it, Notification.EXTRA_PICTURE, Bitmap::class.java) },
            pictureIcon = if (Build.VERSION.SDK_INT >= 31) extras?.let {
                BundleCompat.getParcelable(it, Notification.EXTRA_PICTURE_ICON, Icon::class.java)
            } else null,
            largeIcon = notification.getLargeIcon(),
            attachmentUri = attachment,
        ).takeIf { it.picture != null || it.pictureIcon != null || it.largeIcon != null || it.attachmentUri != null }
    }
}

internal fun CustomIsland.matchingNotification(items: Collection<CustomNotification>): CustomNotification? =
    items.asSequence().filter(notificationRule::matches).maxByOrNull { it.postedAt }
