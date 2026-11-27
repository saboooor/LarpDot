package ca.saboor.larpdot.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtGlowTest {

    private fun packArgb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
    private fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    private fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    private fun blue(argb: Int): Int = argb and 0xFF

    @Test
    fun blurRadiatesIntoTransparentPaddingAndFadesToZeroAtOuterBorder() {
        val pixels = IntArray(BLUR_BUFFER_SIZE * BLUR_BUFFER_SIZE)
        for (y in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
            for (x in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
                pixels[y * BLUR_BUFFER_SIZE + x] = packArgb(255, 240, 80, 40)
            }
        }

        blurArtworkPixels(
            pixels = pixels,
            width = BLUR_BUFFER_SIZE,
            height = BLUR_BUFFER_SIZE,
            radius = BLUR_RADIUS,
            passes = BLUR_PASSES,
            saturationBoost = 1f,
        )

        val midY = BLUR_BUFFER_SIZE / 2
        val outsideNear = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING - 2)]
        val outsideMid = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING - 6)]
        val outerBorder = pixels[midY * BLUR_BUFFER_SIZE + 0]

        assertTrue("Glow should bleed just outside artwork bounds", alpha(outsideNear) in 40..200)
        assertTrue("Glow should decay monotonically outward", alpha(outsideNear) > alpha(outsideMid))
        assertEquals("Outer buffer edge must fade cleanly to 0 alpha", 0, alpha(outerBorder))
        assertTrue("Premultiplied blur should preserve vibrant red without black fringing", red(outsideNear) >= 235)
        assertTrue("Premultiplied blur should preserve green channel", green(outsideNear) in 75..85)
        assertTrue("Premultiplied blur should preserve blue channel", blue(outsideNear) in 35..45)
    }

    @Test
    fun blurPreservesDistinctMultiColorEdgesInsteadOfSingleAccentColor() {
        val pixels = IntArray(BLUR_BUFFER_SIZE * BLUR_BUFFER_SIZE)
        val splitX = BLUR_PADDING + BLUR_CORE_SIZE / 2
        for (y in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
            for (x in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
                pixels[y * BLUR_BUFFER_SIZE + x] = if (x < splitX) {
                    packArgb(255, 255, 30, 30)
                } else {
                    packArgb(255, 30, 80, 255)
                }
            }
        }

        blurArtworkPixels(
            pixels = pixels,
            width = BLUR_BUFFER_SIZE,
            height = BLUR_BUFFER_SIZE,
            radius = BLUR_RADIUS,
            passes = BLUR_PASSES,
        )

        val midY = BLUR_BUFFER_SIZE / 2
        val leftGlow = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING - 3)]
        val rightGlow = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING + BLUR_CORE_SIZE + 2)]

        assertTrue("Left glow should be visible", alpha(leftGlow) > 20)
        assertTrue("Right glow should be visible", alpha(rightGlow) > 20)
        assertTrue("Left glow should radiate red from left edge of cover", red(leftGlow) > 200 && blue(leftGlow) < 60)
        assertTrue("Right glow should radiate blue from right edge of cover", blue(rightGlow) > 200 && red(rightGlow) < 60)
    }

    @Test
    fun blurPreservesNeutralMonochromeWithoutColorShift() {
        val pixels = IntArray(BLUR_BUFFER_SIZE * BLUR_BUFFER_SIZE)
        for (y in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
            for (x in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
                pixels[y * BLUR_BUFFER_SIZE + x] = packArgb(255, 200, 200, 200)
            }
        }

        blurArtworkPixels(
            pixels = pixels,
            width = BLUR_BUFFER_SIZE,
            height = BLUR_BUFFER_SIZE,
            radius = BLUR_RADIUS,
            passes = BLUR_PASSES,
        )

        val midY = BLUR_BUFFER_SIZE / 2
        val near = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING - 3)]
        assertTrue("Monochrome glow should remain visible", alpha(near) > 20)
        assertEquals(red(near), green(near))
        assertEquals(green(near), blue(near))
    }

    @Test
    fun shapedMaskRadiatesAlongShapeSilhouetteInsteadOfSquareCorners() {
        val pixels = IntArray(BLUR_BUFFER_SIZE * BLUR_BUFFER_SIZE)
        val center = BLUR_PADDING + BLUR_CORE_SIZE / 2f
        val half = BLUR_CORE_SIZE / 2f
        // Fill a diamond silhouette inside the core region
        for (y in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
            for (x in BLUR_PADDING until BLUR_PADDING + BLUR_CORE_SIZE) {
                val dx = kotlin.math.abs(x + 0.5f - center)
                val dy = kotlin.math.abs(y + 0.5f - center)
                if (dx + dy <= half) {
                    pixels[y * BLUR_BUFFER_SIZE + x] = packArgb(255, 255, 100, 50)
                }
            }
        }

        blurArtworkPixels(
            pixels = pixels,
            width = BLUR_BUFFER_SIZE,
            height = BLUR_BUFFER_SIZE,
            radius = BLUR_RADIUS,
            passes = BLUR_PASSES,
        )

        val midY = BLUR_BUFFER_SIZE / 2
        val vertexHalo = pixels[midY * BLUR_BUFFER_SIZE + (BLUR_PADDING - 2)]
        val cutCorner = pixels[BLUR_PADDING * BLUR_BUFFER_SIZE + BLUR_PADDING]
        assertTrue("Glow near diamond tip should be clearly visible", alpha(vertexHalo) > 25)
        assertTrue(
            "Empty square corner outside diamond should stay dimmer than diamond edge",
            alpha(cutCorner) < alpha(vertexHalo),
        )
    }

    @Test
    fun glowShapeKeyNormalizesRotationEquivalently() {
        assertEquals(
            AlbumArtGlowShapeKey.of(ca.saboor.larpdot.service.OverlayPreferences.NestedAlbumArtShape.HEART, 0f),
            AlbumArtGlowShapeKey.of(ca.saboor.larpdot.service.OverlayPreferences.NestedAlbumArtShape.HEART, 360f),
        )
        assertEquals(
            AlbumArtGlowShapeKey.of(ca.saboor.larpdot.service.OverlayPreferences.NestedAlbumArtShape.DIAMOND, -45f),
            AlbumArtGlowShapeKey.of(ca.saboor.larpdot.service.OverlayPreferences.NestedAlbumArtShape.DIAMOND, 315f),
        )
    }
}
