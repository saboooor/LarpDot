package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind

/** A brief burst of horizontal motion for each newly posted vibrate-mode event. */
@Composable
internal fun rememberStatusArrivalOffset(activity: NotificationActivityInfo?, enabled: Boolean = true): Float {
    val offset = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { 1.dp.toPx() }
    LaunchedEffect(activity?.key, activity?.postedAtMillis) {
        offset.snapTo(0f)
        if (activity?.kind == NotificationActivityKind.SOUND_MODE &&
            activity.title.contains("Vibrate", ignoreCase = true)) {
            offset.animateTo(distance, tween(30))
            repeat(8) { offset.animateTo(if (it % 2 == 0) -distance else distance, tween(45)) }
            offset.animateTo(0f, tween(30))
        }
    }
    return if (enabled) offset.value else 0f
}
