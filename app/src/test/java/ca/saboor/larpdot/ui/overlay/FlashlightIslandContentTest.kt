package ca.saboor.larpdot.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashlightIslandContentTest {

    @Test
    fun getFlashlightPercentTextReturns100PercentForBinaryTorch() {
        // Binary torch: maxStrength <= 1
        assertEquals("100%", getFlashlightPercentText(torchStrength = 1, maxStrength = 1))
        assertEquals("100%", getFlashlightPercentText(torchStrength = 0, maxStrength = 1))
        assertEquals("100%", getFlashlightPercentText(torchStrength = 1, maxStrength = 0))
    }

    @Test
    fun getFlashlightPercentTextCalculatesCorrectPercentageForVariableTorch() {
        // Android 13+ variable torch with maxStrength > 1
        assertEquals("100%", getFlashlightPercentText(torchStrength = 10, maxStrength = 10))
        assertEquals("50%", getFlashlightPercentText(torchStrength = 5, maxStrength = 10))
        assertEquals("10%", getFlashlightPercentText(torchStrength = 1, maxStrength = 10))
        assertEquals("25%", getFlashlightPercentText(torchStrength = 1, maxStrength = 4))
        assertEquals("75%", getFlashlightPercentText(torchStrength = 3, maxStrength = 4))
    }

    @Test
    fun getFlashlightPercentTextClampsBetween1And100() {
        assertEquals("1%", getFlashlightPercentText(torchStrength = 0, maxStrength = 100))
        assertEquals("100%", getFlashlightPercentText(torchStrength = 150, maxStrength = 100))
    }
}
