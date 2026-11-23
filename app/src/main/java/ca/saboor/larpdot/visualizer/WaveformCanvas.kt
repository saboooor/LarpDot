package ca.saboor.larpdot.visualizer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp

@Composable
internal fun WaveformCanvas(
    modifier: Modifier,
    startColor: Color,
    endColor: Color,
    widthPx: Float,
    onDraw: DrawScope.(Brush) -> Unit,
) {
    Spacer(
        modifier.drawWithCache {
            val fit = (size.width / widthPx.coerceAtLeast(1f)).coerceAtMost(1f)
            // Compensate for squeezed bars so the visible gradient stays at 45 degrees.
            val span = (widthPx * fit + size.height).coerceAtLeast(1f) / (2f * (1f + fit * fit))
            val diagonal = Offset(fit * span, -span)
            val center = Offset(size.width / 2f, size.height / 2f)
            val gradient = Brush.linearGradient(
                colors = listOf(lerp(startColor, endColor, 0.29f), lerp(endColor, startColor, 0.29f)),
                start = center - diagonal,
                end = center + diagonal,
            )
            onDrawBehind {
                scale(scaleX = fit, scaleY = 1f) { onDraw(gradient) }
            }
        },
    )
}
