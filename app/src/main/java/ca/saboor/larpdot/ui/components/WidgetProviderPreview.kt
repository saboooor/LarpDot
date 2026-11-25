package ca.saboor.larpdot.ui.components

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.saboor.larpdot.service.WidgetColorKey
import ca.saboor.larpdot.ui.WidgetColorKeyRenderer
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun sampleOpaqueRgb(argb: Int): Int? =
    if ((argb ushr 24) >= 16) argb and 0xFFFFFF else null

internal class WidgetPreviewSampler {
    internal var hostView: ViewGroup? = null
    private var cachedBitmap: Bitmap? = null

    fun prepareSnapshot(): Boolean {
        releaseSnapshot()
        val frame = hostView ?: return false
        val width = frame.width
        val height = frame.height
        if (width <= 0 || height <= 0 || frame.childCount == 0) return false
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Draw children directly onto a software canvas so any RenderEffect filter on the host frame is bypassed.
        for (index in 0 until frame.childCount) {
            val child = frame.getChildAt(index)
            if (child.visibility == View.VISIBLE) {
                canvas.save()
                canvas.translate(child.left.toFloat(), child.top.toFloat())
                if (!child.matrix.isIdentity) canvas.concat(child.matrix)
                canvas.translate(-child.scrollX.toFloat(), -child.scrollY.toFloat())
                child.draw(canvas)
                canvas.restore()
            }
        }
        cachedBitmap = bitmap
        return true
    }

    fun sampleRgb(normalizedX: Float, normalizedY: Float): Int? {
        val bitmap = cachedBitmap ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val x = (normalizedX.coerceIn(0f, 1f) * (bitmap.width - 1)).roundToInt()
        val y = (normalizedY.coerceIn(0f, 1f) * (bitmap.height - 1)).roundToInt()
        return sampleOpaqueRgb(bitmap.getPixel(x, y))
    }

    fun releaseSnapshot() {
        cachedBitmap?.recycle()
        cachedBitmap = null
    }
}

private data class WidgetPreviewAssets(
    val generated: RemoteViews?,
    val layout: RemoteViews?,
    val image: Drawable?,
    val icon: Drawable?,
) {
    val isEmpty get() = generated == null && layout == null && image == null && icon == null
}

@Composable
internal fun WidgetProviderPreview(
    provider: AppWidgetProviderInfo,
    modifier: Modifier = Modifier,
    colorKey: WidgetColorKey? = null,
    sampler: WidgetPreviewSampler? = null,
) {
    val context = LocalContext.current
    DisposableEffect(sampler) {
        onDispose {
            sampler?.hostView = null
            sampler?.releaseSnapshot()
        }
    }
    val assets by produceState<WidgetPreviewAssets?>(null, provider.provider, context) {
        value = withContext(Dispatchers.IO) {
            val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
            val generated = if (Build.VERSION.SDK_INT >= 35 && provider.generatedPreviewCategories and category != 0) {
                runCatching {
                    AppWidgetManager.getInstance(context)?.getWidgetPreview(provider.provider, provider.profile, category)
                }.getOrNull()
            } else null
            val layout = if (Build.VERSION.SDK_INT >= 31 && provider.previewLayout != 0) {
                RemoteViews(provider.provider.packageName, provider.previewLayout)
            } else null
            WidgetPreviewAssets(
                generated,
                layout,
                runCatching { provider.loadPreviewImage(context, 0) }.getOrNull(),
                runCatching { provider.loadIcon(context, 0) }.getOrNull(),
            )
        }
    }
    val preview = assets
    if (preview == null || preview.isEmpty) {
        sampler?.hostView = null
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(LarpIcons.Apps, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.outline)
        }
    } else key(preview) {
        AndroidView(
            factory = { viewContext ->
                val frame = WidgetPreviewFrame(viewContext)
                val remote = sequenceOf(preview.generated, preview.layout).filterNotNull().firstNotNullOfOrNull {
                    runCatching { it.apply(viewContext, frame) }.getOrNull()
                }
                if (remote != null) {
                    frame.addView(remote, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER,
                    ))
                } else {
                    val image = ImageView(viewContext).apply {
                        scaleType = if (preview.image != null) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
                        setImageDrawable(preview.image ?: preview.icon)
                    }
                    frame.addView(image, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                }
                sampler?.hostView = frame
                frame
            },
            modifier = modifier,
            update = {
                sampler?.hostView = it
                it.colorKeyRenderer.update(colorKey)
            },
            onRelease = {
                if (sampler?.hostView === it) {
                    sampler.hostView = null
                    sampler.releaseSnapshot()
                }
            },
        )
    }
}

private class WidgetPreviewFrame(context: Context) : FrameLayout(context) {
    val colorKeyRenderer = WidgetColorKeyRenderer(this)

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
}
