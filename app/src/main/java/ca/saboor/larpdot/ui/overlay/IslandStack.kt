package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.notification.NotificationActivityKind

/** User-orderable island priority buckets; higher entries take the main island first. */
enum class IslandPriorityItem(
    val label: String,
    val subtitle: String,
) {
    SHORT_TERM_ALERTS("Auto-Dismiss Alerts", "Temporary charging, Bluetooth, sound mode, screenshots"),
    LIVE_ACTIVITIES("Live Activities", "Calls, timers, downloads & progress"),
    MEDIA("Music", "Now playing & audio controls"),
    PINNED_SHORT_TERM("Pinned Alerts", "Pinned Bluetooth battery, charging, sound mode"),
    NAVIGATION("Navigation", "Turn-by-turn maps & trips"),
    FLASHLIGHT("Flashlight", "Torch status & brightness"),
}

val DEFAULT_ISLAND_PRIORITY_ORDER: List<IslandPriorityItem> = listOf(
    IslandPriorityItem.SHORT_TERM_ALERTS,
    IslandPriorityItem.LIVE_ACTIVITIES,
    IslandPriorityItem.MEDIA,
    IslandPriorityItem.PINNED_SHORT_TERM,
    IslandPriorityItem.NAVIGATION,
    IslandPriorityItem.FLASHLIGHT,
)

fun normalizeIslandPriorityOrder(order: List<IslandPriorityItem>): List<IslandPriorityItem> {
    val result = LinkedHashSet<IslandPriorityItem>(IslandPriorityItem.entries.size)
    order.forEach { result.add(it) }
    DEFAULT_ISLAND_PRIORITY_ORDER.forEach { result.add(it) }
    return result.toList()
}

internal fun islandPriorityScore(
    item: IslandPriorityItem,
    priorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
): Int {
    val normalized = normalizeIslandPriorityOrder(priorityOrder)
    val index = normalized.indexOf(item)
    return if (index >= 0) (normalized.size - index) * 100 else 100
}

/** A stable identity and priority are all a provider needs to participate in stacking. */
data class IslandCandidate<T>(val id: String, val priority: Int, val content: T)

data class IslandStack<T>(val ordered: List<IslandCandidate<T>>) {
    val primary get() = ordered.firstOrNull()
    val secondary get() = ordered.drop(1)
}

/** Higher priorities win; stable IDs break ties so content updates never shuffle slots. */
fun <T> resolveIslandStack(candidates: List<IslandCandidate<T>>): IslandStack<T> {
    require(candidates.map { it.id }.distinct().size == candidates.size) {
        "Island provider IDs must be unique"
    }
    return IslandStack(candidates.sortedWith(
        compareByDescending<IslandCandidate<T>> { it.priority }.thenBy { it.id },
    ))
}

/** Central adapter for the built-in providers. Keep priority decisions out of renderers. */
fun builtInIslandStack(
    music: Boolean,
    flashlight: Boolean,
    activity: Boolean,
    activityHasRightWing: Boolean = true,
    activityKind: NotificationActivityKind? = null,
    activityIsPinnedShortTerm: Boolean = false,
    priorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
): IslandStack<IslandType> {
    val normalized = normalizeIslandPriorityOrder(priorityOrder)
    val shortTermScore = islandPriorityScore(IslandPriorityItem.SHORT_TERM_ALERTS, normalized)
    val pinnedShortTermScore = islandPriorityScore(IslandPriorityItem.PINNED_SHORT_TERM, normalized)
    val liveActivityScore = islandPriorityScore(IslandPriorityItem.LIVE_ACTIVITIES, normalized)
    val mediaScore = islandPriorityScore(IslandPriorityItem.MEDIA, normalized)
    val navigationScore = islandPriorityScore(IslandPriorityItem.NAVIGATION, normalized)
    val flashlightScore = islandPriorityScore(IslandPriorityItem.FLASHLIGHT, normalized)

    return resolveIslandStack(buildList {
        if (activity) {
            val isShortTermKind = activityKind == NotificationActivityKind.BATTERY_CHARGING ||
                activityKind == NotificationActivityKind.BATTERY_LOW ||
                activityKind == NotificationActivityKind.SOUND_MODE ||
                activityKind == NotificationActivityKind.BLUETOOTH ||
                activityKind == NotificationActivityKind.AUDIO_OUTPUT ||
                activityKind == NotificationActivityKind.SCREENSHOT
            val priority = when {
                isShortTermKind && activityIsPinnedShortTerm -> pinnedShortTermScore
                isShortTermKind -> shortTermScore
                activityKind == NotificationActivityKind.NAVIGATION -> navigationScore
                activityHasRightWing -> liveActivityScore
                else -> minOf(liveActivityScore, mediaScore - 10)
            }
            add(IslandCandidate("live-activity", priority, IslandType.NOTIFICATION_ACTIVITY))
        }
        if (music) add(IslandCandidate("media", mediaScore, IslandType.MEDIA))
        if (flashlight) add(IslandCandidate("flashlight", flashlightScore, IslandType.FLASHLIGHT))
    })
}

/** Reserve room for dots while keeping the primary centered on the camera.
 * When there are 2 dots, they flank the primary on left and right, so each side only needs space for 1 dot.
 */
fun stackedPillWidth(requested: Float, thickness: Float, dotCount: Int, availableWidth: Float): Float {
    val maxSideDots = if (dotCount == 2) 1 else dotCount
    val dotExtent = (thickness + 8f) * maxSideDots
    return requested.coerceAtMost((availableWidth - 2f * dotExtent).coerceAtLeast(thickness))
}

fun stackedPillWidthWithExtents(
    requested: Float,
    thickness: Float,
    leftExtent: Float,
    rightExtent: Float,
    availableWidth: Float,
): Float {
    val maxExtent = maxOf(leftExtent, rightExtent)
    return requested.coerceAtMost((availableWidth - 2f * maxExtent).coerceAtLeast(thickness))
}

/** Identity stays stable when an individual notification's content changes. */
data class IslandSlot(val type: IslandType, val activityKey: String? = null) {
    val activity get() = resolveIslandSlotActivity(
        slot = this,
        primaryActivity = ca.saboor.larpdot.notification.NotificationActivityState.current.value,
        shortTerms = ca.saboor.larpdot.notification.NotificationActivityState.shortTermActivities.value,
    )
}

internal fun resolveIslandSlotActivity(
    slot: IslandSlot?,
    primaryActivity: ca.saboor.larpdot.notification.NotificationActivityInfo?,
    shortTerms: List<ca.saboor.larpdot.notification.NotificationActivityInfo>,
): ca.saboor.larpdot.notification.NotificationActivityInfo? {
    val key = slot?.activityKey ?: return null
    return shortTerms.firstOrNull { it.key == key }
        ?: primaryActivity?.takeIf { it.key == key }
}

fun builtInIslandSlots(
    music: Boolean, flashlight: Boolean,
    activity: ca.saboor.larpdot.notification.NotificationActivityInfo?,
    shortTerms: List<ca.saboor.larpdot.notification.NotificationActivityInfo>,
    priorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
): IslandStack<IslandSlot> {
    val normalized = normalizeIslandPriorityOrder(priorityOrder)
    val base = builtInIslandStack(
        music = music,
        flashlight = flashlight,
        activity = activity != null,
        activityHasRightWing = hasCompactStatusContent(activity),
        activityKind = activity?.kind,
        activityIsPinnedShortTerm = activity?.isPinnedShortTerm == true,
        priorityOrder = normalized,
    )
    val autoDismissSecondaryPriority = islandPriorityScore(IslandPriorityItem.SHORT_TERM_ALERTS, normalized) - 5
    val pinnedSecondaryPriority = islandPriorityScore(IslandPriorityItem.PINNED_SHORT_TERM, normalized) - 5
    return resolveIslandStack(base.ordered.map {
        IslandCandidate(it.id, it.priority, IslandSlot(it.content,
            if (it.content == IslandType.NOTIFICATION_ACTIVITY) activity?.key else null))
    } + shortTerms.filter { it.key != activity?.key }.map { item ->
        val score = if (item.isPinnedShortTerm) pinnedSecondaryPriority else autoDismissSecondaryPriority
        IslandCandidate(item.key, score, IslandSlot(IslandType.NOTIFICATION_ACTIVITY, item.key))
    })
}
