package ca.saboor.larpdot.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import ca.saboor.larpdot.service.DotAccessibilityService
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun sampleBitmapPixelRgb(
    bitmap: Bitmap,
    normalizedX: Float,
    normalizedY: Float,
): Int? {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
    val x = (normalizedX.coerceIn(0f, 1f) * (bitmap.width - 1)).roundToInt()
    val y = (normalizedY.coerceIn(0f, 1f) * (bitmap.height - 1)).roundToInt()
    return bitmap.getPixel(x, y) and 0xFFFFFF
}

internal fun captureScreenBitmap(
    activity: Activity?,
    onCaptured: (Bitmap?) -> Unit,
) {
    val canUseAccessibility = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        DotAccessibilityService.instance != null
    if (canUseAccessibility) {
        DotAccessibilityService.takeScreenshotBitmap { bitmap ->
            if (bitmap != null) {
                onCaptured(bitmap)
            } else {
                captureActivityWindowBitmap(activity, onCaptured)
            }
        }
    } else {
        captureActivityWindowBitmap(activity, onCaptured)
    }
}

private fun captureActivityWindowBitmap(
    activity: Activity?,
    onCaptured: (Bitmap?) -> Unit,
) {
    val window = activity?.window
    val decor = window?.decorView
    if (window == null || decor == null || decor.width <= 0 || decor.height <= 0) {
        onCaptured(null)
        return
    }
    val bitmap = runCatching {
        createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
    if (bitmap == null) {
        onCaptured(null)
        return
    }
    try {
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    onCaptured(bitmap)
                } else {
                    drawDecorFallback(decor, bitmap)
                    onCaptured(bitmap)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (_: Throwable) {
        drawDecorFallback(decor, bitmap)
        onCaptured(bitmap)
    }
}

private fun drawDecorFallback(decor: View, target: Bitmap) {
    runCatching {
        val canvas = AndroidCanvas(target)
        decor.draw(canvas)
    }
}

internal fun canPickFromOtherApps(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && DotAccessibilityService.isServiceConnected.value

/**
 * Minimizes LarpDot and floats a small accessibility overlay pill so the user can navigate
 * to another app or the home screen and tap "Pick color" to capture that screen.
 */
internal object ExternalScreenColorSession {
    private var activeOverlay: View? = null

    fun start(
        activity: Activity?,
        onCaptured: (Bitmap?) -> Unit,
    ): Boolean {
        val service = DotAccessibilityService.instance ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        dismiss(service)

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(0xE61C1B1F.toInt())
                setStroke(dp(1), 0x44FFFFFF)
            }
            elevation = dp(8).toFloat()
        }

        val pickButton = TextView(service).apply {
            text = "Pick color"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xFF4C6EF5.toInt())
            }
        }

        val cancelButton = TextView(service).apply {
            text = "Cancel"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(6), dp(6), dp(6))
        }

        container.addView(pickButton)
        container.addView(cancelButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }

        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var dragged = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dragged = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).roundToInt()
                    val dy = (event.rawY - touchStartY).roundToInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        dragged = true
                        params.x = dragStartX + dx
                        params.y = (dragStartY - dy).coerceAtLeast(dp(24))
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    dragged
                }
                else -> false
            }
        }

        fun returnToApp() {
            val launch = service.packageManager.getLaunchIntentForPackage(service.packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            if (launch != null) runCatching { service.startActivity(launch) }
        }

        pickButton.setOnClickListener {
            dismiss(service)
            Handler(Looper.getMainLooper()).postDelayed({
                DotAccessibilityService.takeScreenshotBitmap { bitmap ->
                    returnToApp()
                    onCaptured(bitmap)
                }
            }, 140L)
        }

        cancelButton.setOnClickListener {
            dismiss(service)
            returnToApp()
            onCaptured(null)
        }

        val added = runCatching {
            wm.addView(container, params)
            activeOverlay = container
            true
        }.getOrDefault(false)
        if (!added) return false

        activity?.moveTaskToBack(true)
        return true
    }

    fun dismiss(context: Context? = DotAccessibilityService.instance) {
        val view = activeOverlay ?: return
        activeOverlay = null
        val wm = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeViewImmediate(view) }
    }
}

@Composable
internal fun ScreenColorPickerOverlay(
    bitmap: Bitmap,
    initialRgb: Int,
    onPickFromOtherApp: (() -> Unit)?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var samplePoint by remember(bitmap) { mutableStateOf<Offset?>(null) }
    var sampleNorm by remember(bitmap) { mutableStateOf<Offset?>(null) }
    var sampledRgb by remember(bitmap, initialRgb) { mutableStateOf(initialRgb and 0xFFFFFF) }

    DisposableEffect(bitmap) {
        onDispose {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Screen capture for color picking",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun updateAt(pos: Offset) {
                                if (size.width <= 0 || size.height <= 0) return
                                val clampedX = pos.x.coerceIn(0f, size.width.toFloat())
                                val clampedY = pos.y.coerceIn(0f, size.height.toFloat())
                                val nx = clampedX / size.width
                                val ny = clampedY / size.height
                                samplePoint = Offset(clampedX, clampedY)
                                sampleNorm = Offset(nx, ny)
                                sampleBitmapPixelRgb(bitmap, nx, ny)?.let { sampledRgb = it }
                            }
                            down.consume()
                            updateAt(down.position)
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break
                                updateAt(change.position)
                                change.consume()
                            } while (change.pressed)
                        }
                    },
            )

            // Magnifier loupe
            val point = samplePoint
            val norm = sampleNorm
            if (point != null && norm != null && !bitmap.isRecycled) {
                Canvas(Modifier.fillMaxSize()) {
                    val loupeRadius = 46.dp.toPx()
                    val offsetY = if (point.y > loupeRadius * 2.5f) -loupeRadius * 1.9f else loupeRadius * 1.9f
                    val center = Offset(
                        x = point.x.coerceIn(loupeRadius + 12.dp.toPx(), (size.width - loupeRadius - 12.dp.toPx()).coerceAtLeast(loupeRadius)),
                        y = (point.y + offsetY).coerceIn(loupeRadius + 24.dp.toPx(), (size.height - loupeRadius - 96.dp.toPx()).coerceAtLeast(loupeRadius)),
                    )

                    // Target reticle at finger
                    drawCircle(Color.Black, 6.dp.toPx(), point, style = Stroke(2.5.dp.toPx()))
                    drawCircle(Color.White, 6.dp.toPx(), point, style = Stroke(1.25.dp.toPx()))

                    // Zoomed 9x9 pixel grid inside circular loupe
                    val gridHalf = 4
                    val gridCount = gridHalf * 2 + 1
                    val cellSize = (loupeRadius * 2f) / gridCount
                    val bmpX = (norm.x * (bitmap.width - 1)).roundToInt()
                    val bmpY = (norm.y * (bitmap.height - 1)).roundToInt()
                    val clip = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center.x - loupeRadius,
                                center.y - loupeRadius,
                                center.x + loupeRadius,
                                center.y + loupeRadius,
                            ),
                        )
                    }
                    clipPath(clip) {
                        for (dy in -gridHalf..gridHalf) {
                            for (dx in -gridHalf..gridHalf) {
                                val px = (bmpX + dx).coerceIn(0, bitmap.width - 1)
                                val py = (bmpY + dy).coerceIn(0, bitmap.height - 1)
                                val pixelColor = Color(bitmap.getPixel(px, py) or 0xFF000000.toInt())
                                drawRect(
                                    color = pixelColor,
                                    topLeft = Offset(
                                        center.x + (dx - 0.5f) * cellSize,
                                        center.y + (dy - 0.5f) * cellSize,
                                    ),
                                    size = Size(cellSize + 0.5f, cellSize + 0.5f),
                                )
                            }
                        }
                        // Highlight center sampled pixel
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(center.x - cellSize / 2f, center.y - cellSize / 2f),
                            size = Size(cellSize, cellSize),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }

                    // Loupe rings showing sampled color
                    drawCircle(Color(sampledRgb or 0xFF000000.toInt()), loupeRadius, center, style = Stroke(6.dp.toPx()))
                    drawCircle(Color.White, loupeRadius + 3.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.6f), loupeRadius + 4.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                }
            }

            // Bottom floating control bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(sampledRgb or 0xFF000000.toInt()))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(
                        text = "#${sampledRgb.toRgbHex()}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (onPickFromOtherApp != null) {
                        OutlinedButton(onClick = onPickFromOtherApp) {
                            Icon(LarpIcons.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Other app")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = { onConfirm(sampledRgb) }) {
                        Text("Use")
                    }
                }
            }
        }
    }
}
