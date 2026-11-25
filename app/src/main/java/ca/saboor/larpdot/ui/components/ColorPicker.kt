package ca.saboor.larpdot.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toRgb(): Int = AndroidColor.HSVToColor(floatArrayOf(hue % 360f, saturation, value)) and 0xFFFFFF

    companion object {
        fun fromRgb(rgb: Int, fallbackHue: Float = 0f): HsvColor {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(rgb, hsv)
            return HsvColor(if (hsv[1] == 0f) fallbackHue else hsv[0], hsv[1], hsv[2])
        }
    }
}

private val hueSpectrum = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

@Composable
internal fun ColorPicker(color: HsvColor, onColorChange: (HsvColor) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(128.dp).clip(shape)
                .pickAt { x, y -> onColorChange(color.copy(saturation = x, value = 1f - y)) }
                .semantics {
                    contentDescription = "Saturation and brightness"
                    stateDescription = "${(color.saturation * 100).roundToInt()}% saturation, " +
                        "${(color.value * 100).roundToInt()}% brightness"
                    customActions = listOf(
                        CustomAccessibilityAction("Increase saturation") {
                            onColorChange(color.copy(saturation = (color.saturation + 0.05f).coerceAtMost(1f)))
                            true
                        },
                        CustomAccessibilityAction("Decrease saturation") {
                            onColorChange(color.copy(saturation = (color.saturation - 0.05f).coerceAtLeast(0f)))
                            true
                        },
                        CustomAccessibilityAction("Increase brightness") {
                            onColorChange(color.copy(value = (color.value + 0.05f).coerceAtMost(1f)))
                            true
                        },
                        CustomAccessibilityAction("Decrease brightness") {
                            onColorChange(color.copy(value = (color.value - 0.05f).coerceAtLeast(0f)))
                            true
                        },
                    )
                },
        ) {
            val hue = Color.hsv(color.hue % 360f, 1f, 1f)
            drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawPickerHandle(Offset(color.saturation * size.width, (1f - color.value) * size.height))
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .pickAt { x, _ -> onColorChange(color.copy(hue = x * 360f)) }
                .semantics {
                    contentDescription = "Hue"
                    progressBarRangeInfo = ProgressBarRangeInfo(color.hue, 0f..360f)
                    setProgress {
                        onColorChange(color.copy(hue = it.coerceIn(0f, 360f)))
                        true
                    }
                },
        ) {
            val trackHeight = 20.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(hueSpectrum),
                topLeft = Offset(0f, (size.height - trackHeight) / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawPickerHandle(Offset(color.hue / 360f * size.width, size.height / 2f))
        }
    }
}

@Composable
private fun Modifier.pickAt(onPick: (Float, Float) -> Unit): Modifier {
    val pick = rememberUpdatedState(onPick)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            fun select(position: Offset) {
                if (size.width == 0 || size.height == 0) return
                pick.value(
                    (position.x / size.width).coerceIn(0f, 1f),
                    (position.y / size.height).coerceIn(0f, 1f),
                )
            }
            down.consume()
            select(down.position)
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                select(change.position)
                change.consume()
            } while (change.pressed)
        }
    }
}

private fun DrawScope.drawPickerHandle(position: Offset) {
    val radius = 7.dp.toPx()
    val center = Offset(
        position.x.coerceIn(minOf(radius, size.width / 2f), maxOf(size.width - radius, size.width / 2f)),
        position.y.coerceIn(minOf(radius, size.height / 2f), maxOf(size.height - radius, size.height / 2f)),
    )
    drawCircle(Color.Black, radius, center)
    drawCircle(Color.White, radius - 1.dp.toPx(), center)
    drawCircle(Color.Black, radius - 3.dp.toPx(), center)
}
