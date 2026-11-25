package ca.saboor.larpdot.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import ca.saboor.larpdot.service.WidgetColorKey

internal class WidgetColorKeyRenderer(private val view: View) {
    private var appliedKey: WidgetColorKey? = null
    private var shader: ColorKeyShader? = null

    fun update(key: WidgetColorKey?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || appliedKey == key) return
        view.setRenderEffect(key?.let {
            val colorShader = shader ?: ColorKeyShader().also { shader = it }
            colorShader.effect(it)
        })
        appliedKey = key
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ColorKeyShader {
    private val shader = RuntimeShader(
        """
        uniform shader widget;
        layout(color) uniform float4 keyColor;
        uniform float tolerance;

        half4 main(float2 position) {
            half4 pixel = widget.eval(position);
            float3 rgb = float3(pixel.rgb) / max(float(pixel.a), 0.0001);
            float3 delta = abs(rgb - keyColor.rgb);
            float difference = max(delta.r, max(delta.g, delta.b));
            float keep = smoothstep(tolerance, tolerance + 0.06, difference);
            return pixel * half(keep);
        }
        """.trimIndent(),
    )

    fun effect(key: WidgetColorKey): RenderEffect {
        shader.setColorUniform("keyColor", key.rgb or 0xFF000000.toInt())
        shader.setFloatUniform("tolerance", key.tolerancePercent / 100f)
        return RenderEffect.createRuntimeShaderEffect(shader, "widget")
    }
}
