package ca.saboor.larpdot.cutout

import android.content.Context
import android.graphics.Rect
import android.os.Build
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
    fun detectHardwareCutout(context: Context): CutoutInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels.toFloat()
        val screenHeight = dm.heightPixels.toFloat()
        val density = dm.density

        // Default fallback position: top center of status bar
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId).toFloat()
        } else {
            24f * density
        }
        val displayCornerRadius = detectDisplayCornerRadius(context)
        val defaultCenterX = screenWidth / 2f
        val defaultCenterY = statusBarHeight / 2f
        val defaultRadius = 16f * density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout: DisplayCutout? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    wm.currentWindowMetrics.windowInsets.displayCutout
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay?.cutout
                }
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.cutout
            }

            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                @Suppress("DEPRECATION")
                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.display.rotation
                    } catch (_: Exception) {
                        Surface.ROTATION_0
                    }
                } else {
                    wm.defaultDisplay.rotation
                }

                // Match the targeted camera cutout according to screen orientation
                val targetRect = when (rotation) {
                    Surface.ROTATION_90 -> {
                        cutout.boundingRects.filter { it.left < screenWidth / 4 }
                            .minByOrNull { abs(it.centerY() - screenHeight / 2f) }
                            ?: cutout.boundingRects.firstOrNull()
                    }
                    Surface.ROTATION_270 -> {
                        cutout.boundingRects.filter { it.right > screenWidth * 0.75f }
                            .minByOrNull { abs(it.centerY() - screenHeight / 2f) }
                            ?: cutout.boundingRects.firstOrNull()
                    }
                    Surface.ROTATION_180 -> {
                        cutout.boundingRects.filter { it.bottom > screenHeight * 0.75f }
                            .minByOrNull { abs(it.centerX() - screenWidth / 2f) }
                            ?: cutout.boundingRects.firstOrNull()
                    }
                    else -> {
                        cutout.boundingRects.filter { it.top < screenHeight / 4 }
                            .minByOrNull { abs(it.centerX() - screenWidth / 2f) }
                            ?: cutout.boundingRects.firstOrNull()
                    }
                }

                if (targetRect != null && !targetRect.isEmpty) {
                    val cx = targetRect.centerX().toFloat()
                    val cy = targetRect.centerY().toFloat()
                    val w = targetRect.width().toFloat()
                    val h = targetRect.height().toFloat()
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
