package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    fadeStart: Boolean = true,
    fadeEnd: Boolean = true,
    fadeWidth: Dp = 12.dp,
    initialDelayMillis: Int = 1000,
    repeatDelayMillis: Int = 1200,
    velocity: Dp = 30.dp,
    spacing: Dp = 24.dp,
) {
    val measurer = rememberTextMeasurer()
    val mergedStyle = style.let { if (color != Color.Unspecified) it.copy(color = color) else it }

    BoxWithConstraints(modifier) {
        val textWidth = remember(text, mergedStyle) {
            measurer.measure(text, mergedStyle, maxLines = 1, softWrap = false).size.width
        }
        val overflows = constraints.hasBoundedWidth && textWidth > constraints.maxWidth

        if (!overflows) {
            Text(
                text = text,
                style = mergedStyle.copy(textAlign = textAlign),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = text,
                style = mergedStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val f = (fadeWidth.toPx() / size.width).coerceIn(0f, 0.35f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to if (fadeStart) Color.Transparent else Color.Black,
                                f to Color.Black,
                                (1f - f).coerceAtLeast(f) to Color.Black,
                                1f to if (fadeEnd) Color.Transparent else Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = initialDelayMillis,
                        repeatDelayMillis = repeatDelayMillis,
                        velocity = velocity,
                        spacing = MarqueeSpacing(spacing),
                    ),
            )
        }
    }
}
