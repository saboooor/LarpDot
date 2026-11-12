package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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
internal fun rememberIslandPressFeedback(identity: Any, enabled: Boolean = true): IslandPressFeedback {
    var pressed by remember(identity, enabled) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.72f, stiffness = 450f)
            else spring(dampingRatio = 0.78f, stiffness = 360f),
        label = "island_press",
    )
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
    return IslandPressFeedback(scale, modifier)
}

internal data class IslandPressFeedback(val scale: Float, val modifier: Modifier)

/** One tap/hold implementation for every slot. A completed hold never also fires a tap. */
@Composable
internal fun Modifier.islandTapGestures(
    identity: Any,
    enabled: Boolean,
    tapToExpand: Boolean,
    model: IslandModel,
    onExpand: () -> Unit,
    onFeedback: () -> Unit,
    onPress: () -> Unit = {},
): Modifier {
    val currentModel by rememberUpdatedState(model)
    val currentExpand by rememberUpdatedState(onExpand)
    val currentFeedback by rememberUpdatedState(onFeedback)
    val currentPress by rememberUpdatedState(onPress)
    return pointerInput(identity, enabled, tapToExpand) {
        if (enabled) {
            var held = false
            detectTapGestures(
                onPress = {
                    held = false
                    currentPress()
                    kotlinx.coroutines.coroutineScope {
                        val holdJob = launch {
                            kotlinx.coroutines.delay(300L)
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
