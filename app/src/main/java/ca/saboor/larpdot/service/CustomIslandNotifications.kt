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

internal data class PatternCaptureSpec(
    val groupCount: Int = 0,
    val namedGroups: Map<String, Int> = emptyMap(),
)

private fun isRegexPattern(pattern: String): Boolean =
    pattern.length >= 2 && pattern.startsWith("/") && pattern.endsWith("/")

private fun findWildcardCaptureParenIndices(pattern: String): Set<Int> {
    val stack = ArrayDeque<Int>()
    val pairs = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\\' && i + 1 < pattern.length) {
            i += 2
            continue
        }
        when (c) {
            '(' -> stack.addLast(i)
            ')' -> {
                if (stack.isEmpty()) return emptySet()
                pairs.add(stack.removeLast() to i)
            }
        }
        i++
    }
    if (stack.isNotEmpty()) return emptySet()

    // In wildcard mode, only treat (...) as a capture group if it uses a named header (?<name>...)
    // or contains an unescaped wildcard (* or ?). Plain literal parens like "(great)" stay literal.
    val captureIndices = HashSet<Int>()
    for ((openIdx, closeIdx) in pairs) {
        val isNamed = parseNamedGroupHeaderEnd(pattern, openIdx) != null
        var hasWildcard = false
        var j = openIdx + 1
        while (j < closeIdx && !hasWildcard) {
            val ch = pattern[j]
            if (ch == '\\' && j + 1 < closeIdx) {
                j += 2
                continue
            }
            if (ch == '*' || ch == '?') hasWildcard = true
            j++
        }
        if (isNamed || hasWildcard) {
            captureIndices.add(openIdx)
            captureIndices.add(closeIdx)
        }
    }
    return captureIndices
}

private fun wildcardToRegexSource(pattern: String): String {
    val captureParenIndices = findWildcardCaptureParenIndices(pattern)
    return buildString(pattern.length * 2) {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' && i + 1 < pattern.length && pattern[i + 1] in charArrayOf('*', '?', '\\', '(', ')') -> {
                    append(Regex.escape(pattern[i + 1].toString()))
                    i += 2
                }
                c == '(' && i in captureParenIndices -> {
                    val namedEnd = parseNamedGroupHeaderEnd(pattern, i)
                    if (namedEnd != null) {
                        append(pattern, i, namedEnd)
                        i = namedEnd
                    } else {
                        append('(')
                        i++
                    }
                }
                c == ')' && i in captureParenIndices -> {
                    append(')')
                    i++
                }
                c == '*' -> {
                    val onlyClosingParensRemain = (i + 1 until pattern.length).all {
                        it in captureParenIndices && pattern[it] == ')'
                    }
                    append(if (onlyClosingParensRemain) ".*" else ".*?")
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
}

private fun parseNamedGroupHeaderEnd(source: String, openParenIndex: Int): Int? {
    if (openParenIndex + 4 >= source.length) return null
    if (source[openParenIndex + 1] != '?' || source[openParenIndex + 2] != '<') return null
    val nameStart = openParenIndex + 3
    val first = source[nameStart]
    if (!first.isLetter()) return null
    var idx = nameStart + 1
    while (idx < source.length && (source[idx].isLetterOrDigit() || source[idx] == '_')) {
        idx++
    }
    return if (idx < source.length && source[idx] == '>') idx + 1 else null
}

private fun patternToRegexSource(pattern: String): String? {
    if (pattern.isEmpty()) return null
    if (isRegexPattern(pattern)) {
        if (pattern.length == 2) return null
        return pattern.substring(1, pattern.length - 1)
    }
    return wildcardToRegexSource(pattern)
}

internal fun inspectPatternCaptures(pattern: String): PatternCaptureSpec {
    if (!isValidNotificationPattern(pattern)) return PatternCaptureSpec()
    val source = patternToRegexSource(pattern) ?: return PatternCaptureSpec()
    var groupIndex = 0
    val named = LinkedHashMap<String, Int>()
    var inCharClass = false
    var i = 0
    while (i < source.length) {
        val c = source[i]
        if (c == '\\' && i + 1 < source.length) {
            i += 2
            continue
        }
        if (inCharClass) {
            if (c == ']') inCharClass = false
            i++
            continue
        }
        when (c) {
            '[' -> {
                inCharClass = true
                i++
            }
            '(' -> {
                val namedEnd = parseNamedGroupHeaderEnd(source, i)
                if (namedEnd != null) {
                    groupIndex++
                    val name = source.substring(i + 3, namedEnd - 1)
                    if (name !in named) named[name] = groupIndex
                    i = namedEnd
                } else if (i + 1 < source.length && source[i + 1] == '?') {
                    // Non-capturing group, lookaround, or inline flag.
                    i += 2
                } else {
                    groupIndex++
                    i++
                }
            }
            else -> i++
        }
    }
    return PatternCaptureSpec(groupCount = groupIndex, namedGroups = named)
}

private fun compileNotificationPattern(pattern: String): Result<Regex> = synchronized(notificationPatternCacheLock) {
    notificationPatternCache.getOrPut(pattern) {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val source = patternToRegexSource(pattern).orEmpty()
        runCatching { Regex(source, options) }
    }
}

internal fun isValidNotificationPattern(pattern: String): Boolean {
    if (pattern.isEmpty() || !isRegexPattern(pattern)) return true
    if (pattern.length == 2) return true
    return compileNotificationPattern(pattern).isSuccess
}

internal fun matchNotificationPattern(value: String, pattern: String): MatchResult? {
    if (pattern.isEmpty() || (isRegexPattern(pattern) && pattern.length == 2)) return null
    return compileNotificationPattern(pattern).getOrNull()?.find(value)
}

internal fun matchesNotificationPattern(value: String, pattern: String): Boolean {
    if (pattern.isEmpty()) return true
    if (!isRegexPattern(pattern) && '*' !in pattern && '?' !in pattern && '\\' !in pattern && '(' !in pattern && ')' !in pattern) {
        return value.contains(pattern, ignoreCase = true)
    }
    if (isRegexPattern(pattern) && pattern.length == 2) return true
    return compileNotificationPattern(pattern).getOrNull()?.containsMatchIn(value) == true
}

internal enum class CustomNotificationField(val label: String) {
    TITLE("Title"),
    TEXT("Description"),
    CHANNEL("Channel"),
}

internal enum class CustomNotificationOperator(val label: String) {
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
}

internal enum class CustomConditionJoin(val label: String) {
    AND("AND"),
    OR("OR"),
}

internal data class CustomNotificationCondition(
    val field: CustomNotificationField = CustomNotificationField.TITLE,
    val operator: CustomNotificationOperator = CustomNotificationOperator.CONTAINS,
    val pattern: String = "",
    val join: CustomConditionJoin = CustomConditionJoin.AND,
) {
    fun valueFor(item: CustomNotification): String = when (field) {
        CustomNotificationField.TITLE -> item.title
        CustomNotificationField.TEXT -> item.text
        CustomNotificationField.CHANNEL -> item.channelId
    }

    fun matches(item: CustomNotification): Boolean {
        if (pattern.isEmpty()) return true
        val matched = matchesNotificationPattern(valueFor(item), pattern)
        return when (operator) {
            CustomNotificationOperator.CONTAINS -> matched
            CustomNotificationOperator.NOT_CONTAINS -> !matched
        }
    }
}

internal data class CustomNotificationRule(
    val packageName: String = "",
    val channelId: String = "",
    val titleContains: String = "",
    val textContains: String = "",
    val matchChannel: Boolean = true,
    val minimizeNotification: Boolean = false,
    val conditions: List<CustomNotificationCondition> = emptyList(),
) {
    val effectiveConditions: List<CustomNotificationCondition>
        get() = if (conditions.isNotEmpty()) {
            conditions
        } else {
            buildList {
                if (titleContains.isNotEmpty()) {
                    add(
                        CustomNotificationCondition(
                            field = CustomNotificationField.TITLE,
                            operator = CustomNotificationOperator.CONTAINS,
                            pattern = titleContains,
                            join = CustomConditionJoin.AND,
                        ),
                    )
                }
                if (textContains.isNotEmpty()) {
                    add(
                        CustomNotificationCondition(
                            field = CustomNotificationField.TEXT,
                            operator = CustomNotificationOperator.CONTAINS,
                            pattern = textContains,
                            join = CustomConditionJoin.AND,
                        ),
                    )
                }
            }
        }

    private fun activeAndGroups(): List<List<CustomNotificationCondition>> {
        val activeConditions = effectiveConditions.filter { it.pattern.isNotEmpty() }
        if (activeConditions.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<CustomNotificationCondition>>()
        activeConditions.forEachIndexed { index, condition ->
            if (index == 0 || condition.join == CustomConditionJoin.OR) {
                groups.add(mutableListOf(condition))
            } else {
                groups.last().add(condition)
            }
        }
        return groups
    }

    fun matches(item: CustomNotification): Boolean {
        if (packageName.isBlank() || item.packageName != packageName) return false
        if (matchChannel && channelId.isNotBlank() && item.channelId != channelId) return false
        val groups = activeAndGroups()
        if (groups.isEmpty()) return true
        // Standard boolean precedence: AND binds tighter than OR (OR of AND-groups).
        return groups.any { group -> group.all { it.matches(item) } }
    }

    /**
     * Lists placeholder names declared by capture groups across conditions: named groups first,
     * followed by numbered groups ("1", "2", ...). Each OR branch restarts capture numbering at 1
     * while conditions joined by AND accumulate sequentially.
     */
    fun declaredCapturePlaceholders(): List<String> {
        val groups = activeAndGroups()
        if (groups.isEmpty()) return emptyList()
        val named = LinkedHashSet<String>()
        var maxNumbered = 0
        groups.forEach { andGroup ->
            var groupCount = 0
            andGroup.forEach { condition ->
                if (condition.operator != CustomNotificationOperator.CONTAINS) return@forEach
                val spec = inspectPatternCaptures(condition.pattern)
                groupCount += spec.groupCount
                named.addAll(spec.namedGroups.keys)
            }
            if (groupCount > maxNumbered) maxNumbered = groupCount
        }
        return buildList {
            addAll(named)
            for (index in 1..maxNumbered) {
                add(index.toString())
            }
        }
    }

    /**
     * Extracts captured group values from the first matching AND-group for [item].
     * Populates numbered keys ("1", "2", ...), "group1"/"group2" aliases, and any named groups.
     */
    fun extractCaptures(item: CustomNotification): Map<String, String> {
        if (packageName.isBlank() || item.packageName != packageName) return emptyMap()
        if (matchChannel && channelId.isNotBlank() && item.channelId != channelId) return emptyMap()
        val winningGroup = activeAndGroups().firstOrNull { group -> group.all { it.matches(item) } }
            ?: return emptyMap()
        val captured = LinkedHashMap<String, String>()
        var nextIndex = 1
        winningGroup.forEach { condition ->
            if (condition.operator != CustomNotificationOperator.CONTAINS) return@forEach
            val spec = inspectPatternCaptures(condition.pattern)
            if (spec.groupCount <= 0) return@forEach
            val match = matchNotificationPattern(condition.valueFor(item), condition.pattern)
            if (match != null) {
                for (localIdx in 1..spec.groupCount) {
                    val value = match.groupValues.getOrNull(localIdx).orEmpty()
                    captured[nextIndex.toString()] = value
                    captured["group$nextIndex"] = value
                    nextIndex++
                }
                spec.namedGroups.forEach { (name, localIdx) ->
                    val value = match.groupValues.getOrNull(localIdx).orEmpty()
                    captured[name] = value
                }
            } else {
                nextIndex += spec.groupCount
            }
        }
        return captured
    }
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

internal fun matchesMinimizedCustomIsland(
    notification: CustomNotification,
    customIslands: List<CustomIsland>,
): Boolean = customIslands.any { island ->
    island.visibility == CustomIslandVisibility.CONDITIONAL &&
        island.condition == CustomIslandCondition.NOTIFICATION &&
        island.notificationRule.minimizeNotification &&
        island.notificationRule.matches(notification)
}
