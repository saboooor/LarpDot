package ca.saboor.larpdot.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.lang.reflect.Method
import java.util.function.Consumer
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.roundToInt

internal enum class BackdropBlurStatus(val description: String) {
    AVAILABLE("Blur API assumed available. Battery Saver or device settings may temporarily disable blur."),
    UNSUPPORTED_OS("Backdrop blur requires Android 12 or newer."),
    SYSTEM_DISABLED("Window blur is disabled by the system. Check Battery Saver and Disable window blurs in Developer options."),
}

/** Resolve only the required blur methods, without process-wide hidden API exemptions.
 * Ordinary reflection can hide these methods even when the system supports backdrop blur.
 */
private object BackdropBlurApi {
    private fun method(owner: Class<*>, name: String, vararg parameters: Class<*>): Method =
        runCatching { owner.getMethod(name, *parameters) }.getOrElse {
            HiddenApiBypass.getDeclaredMethod(owner, name, *parameters)
        }

    val methods: Array<Method>? by lazy {
        if (Build.VERSION.SDK_INT < 31) return@lazy null
        runCatching {
            val root = Class.forName("android.view.ViewRootImpl")
            val drawable = Class.forName("com.android.internal.graphics.drawable.BackgroundBlurDrawable")
            arrayOf(
                method(View::class.java, "getViewRootImpl"),
                method(root, "createBackgroundBlurDrawable"),
                method(drawable, "setBlurRadius", Int::class.javaPrimitiveType!!),
                method(drawable, "setCornerRadius", Float::class.javaPrimitiveType!!),
            )
        }.onFailure {
            Log.w("IslandBackdropBlur", "Backdrop blur method lookup failed; system support does not imply app API access", it)
        }.getOrNull()
    }
}

@Composable
internal fun rememberBackdropBlurStatus(): BackdropBlurStatus {
    val context = LocalContext.current
    val wm = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember(wm) {
        mutableStateOf(Build.VERSION.SDK_INT >= 31 && runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false))
    }
    DisposableEffect(wm) {
        val listener = Consumer<Boolean> { enabled = it }
        val registered = Build.VERSION.SDK_INT >= 31 && runCatching {
            wm.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
            true
        }.getOrDefault(false)
        onDispose {
            if (registered && Build.VERSION.SDK_INT >= 31) wm.removeCrossWindowBlurEnabledListener(listener)
        }
    }
    return when {
        Build.VERSION.SDK_INT < 31 -> BackdropBlurStatus.UNSUPPORTED_OS
        !enabled -> BackdropBlurStatus.SYSTEM_DISABLED
        else -> BackdropBlurStatus.AVAILABLE
    }
}

/** One native blur region. Android supports rounded corners here, not custom paths. */
private class IslandBlurView(context: Context) : View(context) {
    private var drawable: Drawable? = null
    private var failed = false
    private var radius = 0
    private var corner = 0f
    private var appliedRadius = -1
    private var appliedCorner = -1f

    init { setWillNotDraw(false) }

    fun update(radiusPx: Float, cornerRadiusPx: Float) {
        val roundedRadius = radiusPx.roundToInt()
        if (radius == roundedRadius && corner == cornerRadiusPx) return
        radius = roundedRadius
        corner = cornerRadiusPx
        applyBlur()
    }

    private fun applyBlur() {
        val api = BackdropBlurApi.methods ?: return
        if (!isAttachedToWindow || failed) return
        try {
            val d = drawable ?: run {
                val root = api[0].invoke(this) ?: return
                (api[1].invoke(root) as Drawable).also { drawable = it; it.callback = this }
            }
            if (radius != appliedRadius) {
                api[2].invoke(d, radius)
                appliedRadius = radius
            }
            if (corner != appliedCorner) {
                api[3].invoke(d, corner)
                appliedCorner = corner
            }
            d.setBounds(0, 0, width, height)
            invalidate()
        } catch (error: ReflectiveOperationException) {
            Log.w("IslandBackdropBlur", "Backdrop blur invocation failed", error)
            clearBlur()
            failed = true
        } catch (error: RuntimeException) {
            Log.w("IslandBackdropBlur", "Backdrop blur configuration failed", error)
            clearBlur()
            failed = true
        }
    }

    fun clearBlur() {
        drawable?.let { d ->
            runCatching { BackdropBlurApi.methods?.get(2)?.invoke(d, 0) }
            d.callback = null
        }
        drawable = null
        appliedRadius = -1
        appliedCorner = -1f
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); failed = false; applyBlur() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { applyBlur() }
    override fun onDetachedFromWindow() { clearBlur(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) { drawable?.draw(canvas) }
    override fun verifyDrawable(who: Drawable): Boolean = who === drawable || super.verifyDrawable(who)
}

@Composable
internal fun IslandBackdropBlur(radiusPx: Float, cornerRadiusPx: Float, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { IslandBlurView(it) },
        modifier = modifier,
        update = { it.update(radiusPx, cornerRadiusPx) },
        onRelease = { it.clearBlur() },
    )
}
