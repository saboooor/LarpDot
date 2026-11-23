package ca.saboor.larpdot.media

import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkColorsTest {
    @Test
    fun distinctCoverColorsBecomeGradientStops() {
        val pixels = IntArray(100) { if (it < 70) 0xFFDD2211.toInt() else 0xFF2255CC.toInt() }

        val (primary, secondary) = DominantColorExtractor.artworkColorsFromPixels(pixels)

        assertTrue(primary.red > primary.blue)
        assertTrue(secondary.blue > secondary.red)
    }

    @Test
    fun monochromeArtworkGetsASecondShade() {
        val pixels = IntArray(100) { 0xFF407F54.toInt() }

        val (primary, secondary) = DominantColorExtractor.artworkColorsFromPixels(pixels)

        assertTrue(secondary.luminance() > primary.luminance())
        assertTrue(secondary.green > secondary.red)
    }

    @Test
    fun transparentArtworkUsesTheDefaultPalette() {
        assertEquals(
            DominantColorExtractor.defaultColors,
            DominantColorExtractor.artworkColorsFromPixels(IntArray(100)),
        )
    }
}
