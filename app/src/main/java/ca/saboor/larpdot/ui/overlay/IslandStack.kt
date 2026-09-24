package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.notification.NotificationActivityKind

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
): IslandStack<IslandType> =
    resolveIslandStack(buildList {
        if (activity) {
            // Live activities with right-wing status (progress %, timer countdown, call duration) have top priority (300).
            // Navigation is specifically lower priority than music (150 vs 200) because music has both left and right info.
            // Activities without right-wing content are demoted to a dot (150) so two-sided media (200) can take over.
            val priority = when {
                activityKind == NotificationActivityKind.NAVIGATION -> 150
                activityHasRightWing -> 300
                else -> 150
            }
            add(IslandCandidate("live-activity", priority, IslandType.NOTIFICATION_ACTIVITY))
        }
        if (music) add(IslandCandidate("media", 200, IslandType.MEDIA))
        if (flashlight) add(IslandCandidate("flashlight", 100, IslandType.FLASHLIGHT))
    })

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
