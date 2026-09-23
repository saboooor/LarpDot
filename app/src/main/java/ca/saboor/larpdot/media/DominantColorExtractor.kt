package ca.saboor.larpdot.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * High-performance dominant color extraction based on hilight-studio's MediaTracker algorithm.
 * Uses 15-bit color quantization (5 bits per RGB channel) and perceptual diversity filtering,
 * with HSV brightness and saturation clamping so colors pop brilliantly against the black Dynamic Island.
 */
object DominantColorExtractor {

    // Non-Compose fallback matching the app's default dark primary. Compose surfaces replace
    // this with MaterialTheme.colorScheme.primary so Android 12+ uses the live Material You color.
    val DEFAULT_ACCENT: Color = Color(0xFFA5C8FF)

    /**
     * Extracts the primary dominant color from a bitmap album cover.
     * If the bitmap is null, callers should substitute their current Material color-scheme primary.
     * The static accent remains a safe fallback for non-Compose callers and invalid artwork.
     */
    fun extractDominantColor(bitmap: Bitmap?, fallbackSeed: Int? = null): Color {
        if (bitmap == null) {
            return DEFAULT_ACCENT
        }
        val colors = extractColors(bitmap)
        return colors.firstOrNull() ?: fallbackColor(fallbackSeed ?: 42)
    }

    /**
     * Extracts distinct dominant colors from the album cover bitmap.
     */
    fun extractColors(bitmap: Bitmap): List<Color> {
        val size = 48
        val scaled = if (bitmap.width != size || bitmap.height != size) {
            runCatching { Bitmap.createScaledBitmap(bitmap, size, size, true) }.getOrDefault(bitmap)
        } else bitmap

        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        return extractColorsFromPixels(pixels)
    }

    private data class ColorCandidate(val color: Int, val count: Int)

    fun extractColorsFromPixels(pixels: IntArray): List<Color> {
        if (pixels.isEmpty()) return listOf(DEFAULT_ACCENT)

        // 15-bit color quantization: 5 bits per channel (32 x 32 x 32 = 32,768 bins)
        val histogram = IntArray(32768)
        val sumR = IntArray(32768)
        val sumG = IntArray(32768)
        val sumB = IntArray(32768)

        var validPixels = 0
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            histogram[bin]++
            sumR[bin] += r
            sumG[bin] += g
            sumB[bin] += b
            validPixels++
        }

        if (validPixels == 0) return listOf(DEFAULT_ACCENT)

        // Score each bin by frequency × saturation × brightness — this down-weights dark
        // backgrounds and washed-out areas so the winning color is genuinely chromatic.
        val candidates = ArrayList<ColorCandidate>()
        for (bin in 0 until 32768) {
            val count = histogram[bin]
            if (count == 0) continue
            val r = sumR[bin] / count
            val g = sumG[bin] / count
            val b = sumB[bin] / count
            val c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

            // Compute HSV saturation and value for weighting
            val hsv = FloatArray(3)
            colorToHsv(c, hsv)
            val s = hsv[1]
            val v = hsv[2]
            // Discard near-black (dark backgrounds) and near-white (overexposed)
            if (v < 0.15f || (s < 0.10f && v > 0.90f)) continue
            // Weight: prefer saturated, mid-brightness colors; penalise greys
            val weight = (count * (0.25f + s * 0.50f + v * 0.25f)).toInt().coerceAtLeast(count)
            candidates.add(ColorCandidate(c, weight))
        }

        // Order candidates by weighted score (most prominent first)
        candidates.sortByDescending { it.count }

        // Filter for perceptual color diversity — higher threshold keeps palette tighter
        val distinctColors = ArrayList<Int>()
        val minDistanceSq = 3200 // ~57 distance in weighted RGB space

        for (cand in candidates) {
            val c = cand.color
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF

            var isTooClose = false
            for (accepted in distinctColors) {
                val ar = (accepted ushr 16) and 0xFF
                val ag = (accepted ushr 8) and 0xFF
                val ab = accepted and 0xFF
                val dr = r - ar
                val dg = g - ag
                val db = b - ab
                val distSq = 2 * dr * dr + 4 * dg * dg + 3 * db * db
                if (distSq < minDistanceSq) {
                    isTooClose = true
                    break
                }
            }

            if (!isTooClose) {
                distinctColors.add(c)
                if (distinctColors.size >= 6) break
            }
        }

        if (distinctColors.isEmpty() && candidates.isNotEmpty()) {
            distinctColors.add(candidates.first().color)
        }

        return distinctColors.map { Color(ensureColorVisible(it)) }
    }


    /**
     * Ensures extracted colors are legible on the black island background without boosting them
     * into neon territory. Strategy:
     * - Near-black (V < 0.20): lift brightness to 0.55 so it's visible
     * - Achromatic (S < 0.12): return a neutral white-ish grey
     * - Low-saturation chromatic (S < 0.35): gently lift S to 0.40, keep V as-is clamped ≥0.55
     * - Fully chromatic: keep V clamped to 0.55–0.90 and S clamped to 0.45–0.72
     *   (0.72 max saturation avoids neon while still being clearly coloured)
     */
    fun ensureColorVisible(color: Int): Int {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val maxChannel = maxOf(r, g, b)

        if (maxChannel == 0) return (0xFF shl 24) or (220 shl 16) or (220 shl 8) or 220

        val hsv = FloatArray(3)
        colorToHsv(color, hsv)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        return when {
            // Very dark: lift brightness, keep hue/saturation
            v < 0.20f -> hsvToColor(h, s.coerceAtLeast(0.30f), 0.55f)
            // Achromatic (grey/white): return a soft neutral
            s < 0.12f -> hsvToColor(h, 0f, v.coerceIn(0.70f, 1.0f))
            // Low-saturation chromatic: nudge saturation up slightly, keep brightness honest
            s < 0.35f -> hsvToColor(h, 0.40f, v.coerceIn(0.55f, 0.88f))
            // Normal chromatic: clamp both channels to avoid neon blowout
            else -> hsvToColor(h, s.coerceIn(0.45f, 0.72f), v.coerceIn(0.55f, 0.90f))
        }
    }

    fun fallbackColor(seed: Int): Color {
        val baseHue = abs(seed % 360).toFloat()
        return Color(hsvToColor(baseHue, 0.9f, 1f))
    }

    private fun colorToHsv(color: Int, outHsv: FloatArray) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> (60f * ((g - b) / delta) + 360f) % 360f
            max == g -> (60f * ((b - r) / delta) + 120f) % 360f
            else -> (60f * ((r - g) / delta) + 240f) % 360f
        }
        val s = if (max == 0f) 0f else delta / max
        outHsv[0] = h
        outHsv[1] = s
        outHsv[2] = max
    }

    private fun hsvToColor(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when (((h / 60f).toInt()) % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            (((r + m) * 255f).toInt().coerceIn(0, 255) shl 16) or
            (((g + m) * 255f).toInt().coerceIn(0, 255) shl 8) or
            ((b + m) * 255f).toInt().coerceIn(0, 255)
    }

    /**
     * Creates a high quality sample artwork for Starboy / The Weeknd simulation.
     * Iconic Starboy electric neon magenta / crimson palette.
     */
    fun createSampleArtwork(title: String = "Starboy"): Bitmap {
        val bm = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (title) {
            "Die For You" -> {
                // Sunset Amber & Violet
                paint.color = 0xFF1D0E2B.toInt()
                canvas.drawRect(0f, 0f, 160f, 160f, paint)
                paint.color = 0xFFFF6D00.toInt() // Vibrant amber
                canvas.drawCircle(80f, 80f, 54f, paint)
                paint.color = 0xFFFFD600.toInt()
                canvas.drawCircle(80f, 80f, 26f, paint)
            }
            "Blinding Lights" -> {
                // Crimson Red & Electric Gold
                paint.color = 0xFF0D0204.toInt()
                canvas.drawRect(0f, 0f, 160f, 160f, paint)
                paint.color = 0xFFE50914.toInt() // Vibrant red
                canvas.drawCircle(80f, 80f, 55f, paint)
                paint.color = 0xFFFFC400.toInt()
                canvas.drawCircle(80f, 80f, 25f, paint)
            }
            else -> {
                // Starboy: Cobalt Blue & Neon Hot Pink / Magenta Cross
                paint.color = 0xFF0A1128.toInt() // Deep cobalt
                canvas.drawRect(0f, 0f, 160f, 160f, paint)
                paint.color = 0xFF00E5FF.toInt() // Electric cyan circle
                canvas.drawCircle(80f, 80f, 50f, paint)
                paint.color = 0xFFFF0055.toInt() // Iconic neon pink cross
                canvas.drawRect(70f, 35f, 90f, 125f, paint)
                canvas.drawRect(45f, 55f, 115f, 75f, paint)
            }
        }
        return bm
    }
}
