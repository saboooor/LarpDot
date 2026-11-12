package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import ca.saboor.larpdot.flashlight.FlashlightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Providers supply actions; the common recognizer owns thresholds, cancellation and stretch. */
internal data class IslandDragActions(
    val onStart: () -> Unit = {},
    val onHorizontal: ((Float) -> Unit)? = null,
    val onSwipe: ((Boolean) -> Unit)? = null,
    val onDown: (() -> Unit)? = null,
    val onFinish: () -> Unit = {},
)

internal fun flashlightDragActions(
    scope: CoroutineScope,
    density: Float,
    feedback: () -> Unit,
): IslandDragActions {
    if (FlashlightController.maxStrength.value <= 1) return IslandDragActions()
    var applied = 1
    var adjusting = false
    val repeater = FlashlightSwipeRepeater(scope, 24f * density, 120f * density) { direction, levels ->
        val next = (applied + direction * levels).coerceIn(1, FlashlightController.maxStrength.value)
        if (next == applied) false else {
            applied = next
            FlashlightController.setStrength(next)
            feedback()
            true
        }
    }
    return IslandDragActions(
        onStart = { applied = FlashlightController.torchStrength.value },
        onHorizontal = { distance ->
            if (!adjusting) {
                adjusting = true
                repeater.start(distance)
            } else repeater.update(distance)
        },
        onFinish = {
            if (adjusting) {
                repeater.stop()
                FlashlightController.flushStrength(applied)
                adjusting = false
            }
        },
    )
}

@Composable
internal fun Modifier.islandDragGestures(
    identity: Any,
    enabled: Boolean,
    stretch: Animatable<Float, AnimationVector1D>,
    maxStretchPx: Float,
    density: Float,
    actions: IslandDragActions,
): Modifier {
    val currentActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()
    return pointerInput(identity, enabled) {
        if (!enabled) return@pointerInput
        var session = currentActions
        var x = 0f
        var y = 0f
        var triggered = false
        fun finish() {
            session.onFinish()
            scope.launch { stretch.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 450f)) }
        }
        try {
            detectDragGestures(
                onDragStart = {
                    session = currentActions
                    x = 0f
                    y = 0f
                    triggered = false
                    session.onStart()
                },
                onDragEnd = { finish() },
                onDragCancel = { finish() },
                onDrag = { change, amount ->
                    x += amount.x
                    y += amount.y
                    if (abs(x) > abs(y)) {
                        val damped = (x * 0.10f).coerceIn(-maxStretchPx, maxStretchPx)
                        scope.launch { stretch.snapTo(damped) }
                    }
                    if (abs(x) > abs(y) * 1.3f) {
                        change.consume()
                        if (!triggered) {
                            session.onHorizontal?.invoke(x)
                            if (session.onSwipe != null && abs(x) > 28f * density) {
                                triggered = true
                                session.onSwipe?.invoke(x > 0f)
                            }
                        }
                    } else if (!triggered && y > 20f * density && y > abs(x) * 1.3f && session.onDown != null) {
                        triggered = true
                        change.consume()
                        session.onDown?.invoke()
                    }
                },
            )
        } finally {
            finish()
        }
    }
}
