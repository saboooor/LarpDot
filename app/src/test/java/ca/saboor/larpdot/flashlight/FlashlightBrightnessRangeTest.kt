package ca.saboor.larpdot.flashlight

import ca.saboor.larpdot.ui.overlay.getFlashlightPercentText
import org.junit.Assert.*
import org.junit.Test

class FlashlightBrightnessRangeTest {
    @Test fun normalBrightnessAndExactly100PercentNeverRequireCamera() {
        val range = FlashlightBrightnessRange(45, 127)
        (1..45).forEach { assertFalse(range.needsCamera(it)) }
        assertEquals("100%", getFlashlightPercentText(45, 127, 45))
    }

    @Test fun crossingTheBoundaryInEitherDirectionSelectsCorrectBackend() {
        val range = FlashlightBrightnessRange(45, 127)
        assertFalse(range.needsCamera(45))
        assertTrue(range.needsCamera(46))
        assertTrue(range.needsCamera(127))
        assertFalse(range.needsCamera(45))
        assertFalse(range.needsCamera(20))
    }

    @Test fun enablingExtendedRangeDoesNotRedefine100Percent() {
        assertEquals("100%", getFlashlightPercentText(45, 45, 45))
        assertEquals("100%", getFlashlightPercentText(45, 127, 45))
        assertEquals("200%", getFlashlightPercentText(90, 127, 45))
        assertEquals("282%", getFlashlightPercentText(127, 127, 45))
    }
}
