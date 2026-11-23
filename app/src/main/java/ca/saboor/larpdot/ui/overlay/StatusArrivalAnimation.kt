package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import kotlin.math.PI
import kotlin.math.sin
import ca.saboor.larpdot.ui.theme.LarpMotion

/** A brief burst of horizontal motion for each newly posted vibrate-mode event. */
@Composable
internal fun rememberStatusArrivalOffset(activity: NotificationActivityInfo?, enabled: Boolean = true): Float {
    val arrival = remember { Animatable(1f) }
    val distance = with(LocalDensity.current) { 1.dp.toPx() }
    LaunchedEffect(activity?.key, activity?.postedAtMillis) {
        if (activity?.kind == NotificationActivityKind.SOUND_MODE &&
            activity.title.contains("Vibrate", ignoreCase = true)) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, LarpMotion.timing(360, easing = LinearEasing))
        } else arrival.snapTo(1f)
    }
    if (!enabled) return 0f
    val phase = arrival.value
    val envelope = sin(PI * phase).toFloat() * (1f - phase)
    return distance * 1.7f * envelope * sin(8 * PI * phase).toFloat()
}
