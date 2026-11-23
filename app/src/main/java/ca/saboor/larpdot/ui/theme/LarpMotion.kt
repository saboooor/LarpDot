package ca.saboor.larpdot.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal object LarpMotion {
    private const val durationScale = 0.72f
    val standard = CubicBezierEasing(0.35f, 0f, 0.2f, 1f)
    val settle = CubicBezierEasing(0.18f, 0.75f, 0.25f, 1f)

    fun <T> timing(durationMillis: Int, delayMillis: Int = 0, easing: Easing = standard) =
        tween<T>((durationMillis * durationScale).roundToInt(), (delayMillis * durationScale).roundToInt(), easing)

    fun <T> fade(durationMillis: Int = 240) = timing<T>(durationMillis)

    fun <T> quickSpring(dampingRatio: Float, stiffness: Float, visibilityThreshold: T? = null) =
        spring(dampingRatio, stiffness / (durationScale * durationScale), visibilityThreshold)

    fun <T> resize() = quickSpring<T>(dampingRatio = 1f, stiffness = 520f)

    val release = quickSpring<Float>(dampingRatio = 0.8f, stiffness = 420f)
    val press = quickSpring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)

    val reveal = fadeIn(timing(220, delayMillis = 30, easing = settle)) +
        expandVertically(resize(), expandFrom = Alignment.Top)
    val conceal = fadeOut(fade(150)) +
        shrinkVertically(resize(), shrinkTowards = Alignment.Top)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object QuickExpressiveMotion : MotionScheme {
    private val expressive = MotionScheme.expressive()

    override fun <T> defaultSpatialSpec() = faster(expressive.defaultSpatialSpec<T>())
    override fun <T> fastSpatialSpec() = faster(expressive.fastSpatialSpec<T>())
    override fun <T> slowSpatialSpec() = faster(expressive.slowSpatialSpec<T>())
    override fun <T> defaultEffectsSpec() = faster(expressive.defaultEffectsSpec<T>())
    override fun <T> fastEffectsSpec() = faster(expressive.fastEffectsSpec<T>())
    override fun <T> slowEffectsSpec() = faster(expressive.slowEffectsSpec<T>())

    private fun <T> faster(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (spec is SpringSpec) LarpMotion.quickSpring(spec.dampingRatio, spec.stiffness, spec.visibilityThreshold)
        else spec
}

internal fun LazyListScope.motionItem(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.animateItem(
            fadeInSpec = LarpMotion.fade(),
            placementSpec = LarpMotion.resize(),
            fadeOutSpec = LarpMotion.fade(140),
        )) {
            content()
        }
    }
}

internal fun motionFraction(value: Float, start: Float, end: Float): Float {
    val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

/** Small, reusable blur steps for transient content layers. The resting layer has no effect. */
@Composable
internal fun rememberMotionBlur(): List<RenderEffect?> {
    val stepPx = with(LocalDensity.current) { 0.5.dp.toPx() }
    return remember(stepPx) {
        if (Build.VERSION.SDK_INT < 31) emptyList()
        else List(9) { step ->
            val radius = step * stepPx
            if (step == 0) null else BlurEffect(radius, radius)
        }
    }
}

internal fun List<RenderEffect?>.motionBlur(amount: Float): RenderEffect? =
    getOrNull((amount.coerceIn(0f, 1f) * lastIndex).roundToInt())
