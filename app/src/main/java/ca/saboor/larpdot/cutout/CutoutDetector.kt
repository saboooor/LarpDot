package ca.saboor.larpdot.cutout

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.DisplayCutout
import android.view.RoundedCorner
import android.view.Surface
import android.view.WindowManager
import ca.saboor.larpdot.service.OverlayPreferences
import kotlin.math.abs

data class CutoutInfo(
    val centerX: Float,
    val centerY: Float,
    val radiusPx: Float,
    val isAutoDetected: Boolean,
    val boundingRect: Rect? = null,
    val displayCornerRadiusPx: Float = 0f,
    val manualOffsetX: Float = 0f,
    val manualOffsetY: Float = 0f,
    val manualDiameterDp: Float = 0f,
)

object CutoutDetector {
    /**
     * Locates the camera cutout on the device, applying user manual adjustments if enabled.
     */
    fun detect(context: Context): CutoutInfo {
        val hw = detectHardwareCutout(context)
        val config = OverlayPreferences.getCutoutConfig(context)
        if (!config.isManualEnabled) {
            return hw
        }

        val density = context.resources.displayMetrics.density
        val rad = if (config.customDiameterDp > 0f) {
            (config.customDiameterDp * density) / 2f
        } else {
            hw.radiusPx
        }

        return hw.copy(
            centerX = hw.centerX + (config.offsetX * density),
            centerY = hw.centerY + (config.offsetY * density),
            radiusPx = rad,
            isAutoDetected = false,
            manualOffsetX = config.offsetX,
            manualOffsetY = config.offsetY,
            manualDiameterDp = config.customDiameterDp,
        )
    }

    /**
     * Automatically locates the physical camera hole punch cutout from device hardware.
     */
    @Suppress("DEPRECATION")
    fun detectHardwareCutout(context: Context): CutoutInfo {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display
            } catch (_: Exception) {
                displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            }
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        } ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val displayContext = if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                context.createDisplayContext(display)
            } catch (_: Exception) {
                context
            }
        } else {
            context
        }

        val realMetrics = DisplayMetrics()
        val screenWidth: Float
        val screenHeight: Float
        val density: Float

        if (display != null) {
            display.getRealMetrics(realMetrics)
            screenWidth = realMetrics.widthPixels.toFloat()
            screenHeight = realMetrics.heightPixels.toFloat()
            density = realMetrics.density
        } else {
            val dm = displayContext.resources.displayMetrics
            screenWidth = dm.widthPixels.toFloat()
            screenHeight = dm.heightPixels.toFloat()
            density = dm.density
        }

        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 || screenWidth > screenHeight

        // Default fallback position: top center of status bar in portrait, or left/right edge in landscape
        val resourceId = displayContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            displayContext.resources.getDimensionPixelSize(resourceId).toFloat()
        } else {
            24f * density
        }
        val displayCornerRadius = detectDisplayCornerRadius(displayContext)
        val defaultRadius = 16f * density

        val defaultCenterX = when (rotation) {
            Surface.ROTATION_270 -> screenWidth - (statusBarHeight / 2f).coerceAtLeast(24f * density)
            Surface.ROTATION_90 -> (statusBarHeight / 2f).coerceAtLeast(24f * density)
            else -> screenWidth / 2f
        }
        val defaultCenterY = when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> screenHeight / 2f
            Surface.ROTATION_180 -> screenHeight - (statusBarHeight / 2f).coerceAtLeast(24f * density)
            else -> statusBarHeight / 2f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout: DisplayCutout? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val displayWm = displayContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    displayWm?.currentWindowMetrics?.windowInsets?.displayCutout
                } catch (_: Exception) {
                    null
                } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    display?.cutout
                } else {
                    display?.let {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        wm?.defaultDisplay?.cutout
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                display?.cutout ?: run {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    wm?.defaultDisplay?.cutout
                }
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.defaultDisplay?.cutout
            }

            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                val rects = cutout.boundingRects
                val targetRect = rects.minByOrNull { it.width() * it.height() } ?: rects.first()

                if (!targetRect.isEmpty) {
                    var cx = targetRect.centerX().toFloat()
                    var cy = targetRect.centerY().toFloat()
                    val w = targetRect.width().toFloat()
                    val h = targetRect.height().toFloat()

                    // In landscape mode:
                    // In ROTATION_90 (standard landscape): camera hole punch is physically on the left edge.
                    // In ROTATION_270 (reverse landscape): camera hole punch is physically on the right edge.
                    if (rotation == Surface.ROTATION_270) {
                        cx = maxOf(cx, screenWidth - cx)
                        if (cy < screenHeight / 4f || cy > screenHeight * 0.75f) {
                            cy = screenHeight / 2f
                        }
                    } else if (rotation == Surface.ROTATION_90 || isLandscape) {
                        cx = minOf(cx, screenWidth - cx)
                        if (cy < screenHeight / 4f || cy > screenHeight * 0.75f) {
                            cy = screenHeight / 2f
                        }
                    }

                    // On punch-hole cutouts, one dimension often extends to the display edge (e.g. height from top=0),
                    // so the smaller dimension represents the actual circular camera diameter.
                    val dimension = if (w > 0f && h > 0f) minOf(w, h) else maxOf(w, h)
                    val computedRadius = (dimension / 2f).coerceIn(12f * density, 16f * density)
                    val rad = if (computedRadius > 0f) computedRadius else defaultRadius

                    return CutoutInfo(
                        centerX = cx,
                        centerY = cy,
                        radiusPx = rad,
                        isAutoDetected = true,
                        boundingRect = targetRect,
                        displayCornerRadiusPx = displayCornerRadius,
                    )
                }
            }
        }

        return CutoutInfo(
            centerX = defaultCenterX,
            centerY = defaultCenterY,
            radiusPx = defaultRadius,
            isAutoDetected = false,
            boundingRect = null,
            displayCornerRadiusPx = displayCornerRadius,
        )
    }

    /**
     * Detects the physical display's rounded corner radius in pixels.
     */
    fun detectDisplayCornerRadius(context: Context): Float {
        val dm = context.resources.displayMetrics
        val density = dm.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null) {
                    val insets = wm.currentWindowMetrics.windowInsets
                    val tl = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    val tr = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                    val r = tl?.radius ?: tr?.radius ?: 0
                    if (r > 0) {
                        return r.toFloat()
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback via internal system dimension "rounded_corner_radius" (standard Android framework dimen)
        try {
            val resId = context.resources.getIdentifier("rounded_corner_radius", "dimen", "android")
            if (resId > 0) {
                val r = context.resources.getDimensionPixelSize(resId)
                if (r > 0) return r.toFloat()
            }
        } catch (_: Exception) {}

        // Fallback via internal system dimension "rounded_corner_radius_top"
        try {
            val resIdTop = context.resources.getIdentifier("rounded_corner_radius_top", "dimen", "android")
            if (resIdTop > 0) {
                val r = context.resources.getDimensionPixelSize(resIdTop)
                if (r > 0) return r.toFloat()
            }
        } catch (_: Exception) {}

        // Fallback default: 34dp (standard curve for modern bezel-less screens)
        return 34f * density
    }
}
