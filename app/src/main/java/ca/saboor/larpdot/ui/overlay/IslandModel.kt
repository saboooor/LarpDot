package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Common interaction contract for every island, regardless of content or stack slot. */
data class IslandModel(
    val type: IslandType,
    val onActivate: () -> Unit,
    val onHold: () -> Unit = onActivate,
    val presentation: IslandPresentation = IslandPresentation(),
) {
    fun tap(tapToExpand: Boolean, expand: () -> Unit) {
        if (tapToExpand) expand() else onActivate()
    }

    fun hold(tapToExpand: Boolean, expand: () -> Unit) {
        if (tapToExpand) onHold() else expand()
    }
}

/** Observe without consuming events, so buttons, sliders and swipes keep their own gestures. */
@Composable
internal fun rememberIslandPressFeedback(
    identity: Any,
    enabled: Boolean = true,
    pressedScale: Float = 1.035f,
    pressDurationMs: Int? = null,
): IslandPressFeedback {
    var pressed by remember(identity, enabled) { mutableStateOf(false) }
    val scaleAnim = remember(identity) { Animatable(1f) }
    LaunchedEffect(pressed, pressDurationMs, pressedScale) {
        if (pressed) {
            if (pressDurationMs != null) {
                // Compensate for system animator scale so press growth matches the real hold timer 1:1.
                val scaleFactor = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                val adjustedMs = if (scaleFactor > 0f) (pressDurationMs / scaleFactor).roundToInt() else 0
                scaleAnim.animateTo(
                    targetValue = pressedScale,
                    animationSpec = tween(durationMillis = adjustedMs.coerceAtLeast(1), easing = LinearEasing),
                )
            } else {
                scaleAnim.animateTo(targetValue = pressedScale, animationSpec = LarpMotion.press)
            }
        } else {
            scaleAnim.animateTo(targetValue = 1f, animationSpec = LarpMotion.release)
        }
    }
    val modifier = Modifier.pointerInput(identity, enabled) {
        if (enabled) awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                pressed = false
            }
        }
    }
    return IslandPressFeedback(scaleAnim.value, modifier)
}

internal data class IslandPressFeedback(val scale: Float, val modifier: Modifier)

/** One tap/hold implementation for every slot. A completed hold never also fires a tap. */
@Composable
internal fun Modifier.islandTapGestures(
    identity: Any,
    enabled: Boolean,
    tapToExpand: Boolean,
    holdDurationMs: Int = OverlayPreferences.DEFAULT_HOLD_TO_EXPAND_DURATION_MS,
    model: IslandModel,
    onExpand: () -> Unit,
    onFeedback: () -> Unit,
    onPress: () -> Unit = {},
): Modifier {
    val currentModel by rememberUpdatedState(model)
    val currentExpand by rememberUpdatedState(onExpand)
    val currentFeedback by rememberUpdatedState(onFeedback)
    val currentPress by rememberUpdatedState(onPress)
    val currentHoldDurationMs by rememberUpdatedState(holdDurationMs)
    return pointerInput(identity, enabled, tapToExpand) {
        if (enabled) {
            var held = false
            detectTapGestures(
                onPress = {
                    held = false
                    currentPress()
                    kotlinx.coroutines.coroutineScope {
                        val holdJob = launch {
                            kotlinx.coroutines.delay(currentHoldDurationMs.toLong())
                            held = true
                            currentFeedback()
                            currentModel.hold(tapToExpand, currentExpand)
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            holdJob.cancel()
                        }
                    }
                },
                onTap = {
                    if (!held) {
                        currentFeedback()
                        currentModel.tap(tapToExpand, currentExpand)
                    }
                },
            )
        }
    }
}
