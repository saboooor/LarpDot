package ca.saboor.larpdot.ui.overlay

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences.CameraCoverStyle
import kotlin.math.sin

/** Shared by the expanded player and its settings preview; coordinates are local to the card. */
@Composable
internal fun CameraCover(style: CameraCoverStyle, centerX: Dp, centerY: Dp, diameter: Dp) {
    if (style == CameraCoverStyle.NONE) return
    val shader = if (Build.VERSION.SDK_INT >= 33) {
        remember(style) {
            android.graphics.RuntimeShader(
                if (style == CameraCoverStyle.SWOOP) ORGANIC_SCOOP_FADE_SHADER else SOFT_CAMERA_COVER_SHADER,
            )
        }
    } else null
    val shaderBrush = remember(shader) { shader?.let(::ShaderBrush) }

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(centerX.toPx(), centerY.toPx())
        val radius = diameter.toPx() / 2f + 3.dp.toPx()
        when (style) {
            CameraCoverStyle.NONE -> Unit
            CameraCoverStyle.GRADIENT, CameraCoverStyle.CANOPY -> {
                // The opaque region spans the entire top edge and extends below the camera.
                // This hides its silhouette before the fade starts, instead of outlining it.
                val solidBottom = (center.y + radius + 8.dp.toPx()).coerceAtLeast(0f)
                val curveDepth = if (style == CameraCoverStyle.CANOPY) 18.dp.toPx() else 0f
                // Gradient scales with the whole card, reaching transparency only at its bottom.
                val fade = if (style == CameraCoverStyle.GRADIENT) {
                    (size.height - solidBottom).coerceAtLeast(1f)
                } else 112.dp.toPx()
                if (shader != null && shaderBrush != null && Build.VERSION.SDK_INT >= 33) {
                    shader.setFloatUniform("uWidth", size.width)
                    shader.setFloatUniform("uCenterX", center.x)
                    shader.setFloatUniform("uSolidBottom", solidBottom)
                    shader.setFloatUniform("uCurveDepth", curveDepth)
                    shader.setFloatUniform("uFade", fade)
                    drawRect(shaderBrush)
                } else {
                    // Match the shader's smoothstep fade with nested bands on older Android.
                    var previousAlpha = 0f
                    for (band in 95 downTo 0) {
                        val t = band / 96f
                        val targetAlpha = 1f - t * t * (3f - 2f * t)
                        val layerAlpha = ((targetAlpha - previousAlpha) / (1f - previousAlpha))
                            .coerceIn(0f, 1f)
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            for (step in 64 downTo 0) {
                                val x = size.width * step / 64f
                                val distance = ((x - center.x) / size.width.coerceAtLeast(1f))
                                    .coerceIn(-1f, 1f)
                                val curve = 1f - distance * distance
                                lineTo(x, solidBottom + curveDepth * curve * curve + fade * t)
                            }
                            close()
                        }
                        drawPath(path, Color.Black.copy(alpha = layerAlpha))
                        previousAlpha = targetAlpha
                    }
                }
            }
            CameraCoverStyle.SWOOP -> {
                val depth = center.y + diameter.toPx() / 2f - 2.dp.toPx()
                val fade = 72.dp.toPx()
                val halfWidth = (minOf(center.x, size.width - center.x) * 0.75f).coerceAtLeast(1f)
                if (shader != null && shaderBrush != null && Build.VERSION.SDK_INT >= 33) {
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uDotCenter", center.x, center.y)
                    shader.setFloatUniform("uDotRadius", radius)
                    shader.setFloatUniform("uScoopDepth", depth)
                    shader.setFloatUniform("uFadeWidth", fade)
                    shader.setFloatUniform("uHalfWidth", halfWidth)
                    drawRect(shaderBrush)
                } else {
                    // Layer curved translucent bands on devices without RuntimeShader.
                    for (band in 32 downTo 0) {
                        val extension = fade * band / 32f
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            for (step in 64 downTo 0) {
                                val x = size.width * step / 64f
                                val t = (1f - kotlin.math.abs(x - center.x) / halfWidth).coerceIn(0f, 1f)
                                val curve = sin(t * Math.PI.toFloat() / 2f)
                                lineTo(x, depth * curve * kotlin.math.sqrt(curve) + extension)
                            }
                            close()
                        }
                        drawPath(path, Color.Black.copy(alpha = if (band == 0) 1f else 0.075f))
                    }
                }
            }
        }
        // Preserve a solid core even when a soft style is selected.
        drawCircle(Color.Black, radius, center)
    }
}

// Both alternatives merge the camera into a solid top region with zero slope at
// either end of the fade, avoiding a visible ring or a hard capsule boundary.
private const val SOFT_CAMERA_COVER_SHADER = """
    uniform float uWidth;
    uniform float uCenterX;
    uniform float uSolidBottom;
    uniform float uCurveDepth;
    uniform float uFade;

    half4 main(float2 coord) {
        float distance = clamp((coord.x - uCenterX) / max(uWidth, 1.0), -1.0, 1.0);
        float curve = 1.0 - distance * distance;
        float boundary = uSolidBottom + uCurveDepth * curve * curve;
        float alpha = 1.0 - smoothstep(boundary, boundary + uFade, coord.y);
        return half4(0.0, 0.0, 0.0, alpha);
    }
"""
