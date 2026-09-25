package ca.saboor.larpdot.ui.overlay

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class FlashlightSwipeRate(val intervalMs: Long, val levelsPerStep: Int)

/** Interpolates from one level every 500 ms to ten levels every 100 ms. */
internal fun flashlightSwipeRate(dragX: Float, nearDistancePx: Float, maxDistancePx: Float): FlashlightSwipeRate {
    val progress = if (maxDistancePx > nearDistancePx) {
        ((abs(dragX) - nearDistancePx) / (maxDistancePx - nearDistancePx)).coerceIn(0f, 1f)
    } else {
        0f
    }
    return FlashlightSwipeRate(
        intervalMs = (500f - 400f * progress).roundToInt().toLong(),
        levelsPerStep = (1f + 9f * progress).roundToInt(),
    )
}

/** Keeps stepping while held; moving changes direction, repeat speed, and levels per step. */
internal class FlashlightSwipeRepeater(
    private val scope: CoroutineScope,
    private val nearDistancePx: Float,
    private val maxDistancePx: Float,
    private val onStep: (Int, Int) -> Boolean,
) {
    private var dragX = 0f
    private var lastStepTimeMs = 0L
    private var atLimit = false
    private var job: Job? = null

    fun start(dragX: Float) {
        stop()
        this.dragX = dragX
        val direction = dragX.direction()
        if (direction != 0) {
            val rate = flashlightSwipeRate(dragX, nearDistancePx, maxDistancePx)
            atLimit = !onStep(direction, rate.levelsPerStep)
            lastStepTimeMs = SystemClock.uptimeMillis()
        }
        job = scope.launch {
            while (isActive) {
                delay(if (atLimit) 30L else 10L)
                val currentDirection = this@FlashlightSwipeRepeater.dragX.direction()
                if (currentDirection == 0) {
                    lastStepTimeMs = SystemClock.uptimeMillis()
                    continue
                }
                val rate = flashlightSwipeRate(this@FlashlightSwipeRepeater.dragX, nearDistancePx, maxDistancePx)
                val now = SystemClock.uptimeMillis()
                if (now - lastStepTimeMs >= rate.intervalMs) {
                    lastStepTimeMs = now
                    atLimit = !onStep(currentDirection, rate.levelsPerStep)
                }
            }
        }
    }

    fun update(dragX: Float) {
        val previousDirection = this.dragX.direction()
        this.dragX = dragX
        val direction = dragX.direction()
        if (direction != 0 && direction != previousDirection) {
            lastStepTimeMs = SystemClock.uptimeMillis()
            atLimit = !onStep(direction, flashlightSwipeRate(dragX, nearDistancePx, maxDistancePx).levelsPerStep)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        dragX = 0f
        lastStepTimeMs = 0L
        atLimit = false
    }
}

private fun Float.direction(): Int = when {
    this > 0f -> 1
    this < 0f -> -1
    else -> 0
}
