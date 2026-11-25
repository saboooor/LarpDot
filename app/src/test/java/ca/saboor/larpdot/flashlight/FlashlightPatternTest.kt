package ca.saboor.larpdot.flashlight

import ca.saboor.larpdot.service.formatFlashlightTileSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashlightPatternTest {

    @Test fun steadyHasNoPatternSteps() {
        assertTrue(flashlightPatternSteps(FlashlightMode.STEADY).isEmpty())
    }

    @Test fun strobeAlternatesOnAndOff() {
        val steps = flashlightPatternSteps(FlashlightMode.STROBE)
        assertEquals(2, steps.size)
        assertTrue(steps[0].torchOn)
        assertFalse(steps[1].torchOn)
        assertTrue(steps.all { it.durationMs > 0L })

        val slow = flashlightPatternSteps(FlashlightMode.STROBE, StrobeSpeed.SLOW)
        val fast = flashlightPatternSteps(FlashlightMode.STROBE, StrobeSpeed.FAST)
        assertTrue(slow[0].durationMs > steps[0].durationMs)
        assertTrue(fast[0].durationMs < steps[0].durationMs)

        assertEquals(FlashlightMode.STROBE, nextFlashlightMode(FlashlightMode.STEADY))
        assertEquals(FlashlightMode.SOS, nextFlashlightMode(FlashlightMode.STROBE))
        assertEquals(FlashlightMode.STEADY, nextFlashlightMode(FlashlightMode.SOS))
    }

    @Test fun sosFollowsInternationalMorseCode() {
        val steps = flashlightPatternSteps(FlashlightMode.SOS)
        // 3 dots + 3 dashes + 3 dots = 9 on-pulses, 9 gaps = 18 steps
        assertEquals(18, steps.size)
        val onDurations = steps.filter { it.torchOn }.map { it.durationMs }
        assertEquals(9, onDurations.size)
        val dot = onDurations[0]
        val dash = dot * 3L
        assertEquals(listOf(dot, dot, dot, dash, dash, dash, dot, dot, dot), onDurations)
        // Must end with a word gap longer than a dash
        assertFalse(steps.last().torchOn)
        assertTrue(steps.last().durationMs > dash)
    }

    @Test fun autoOffFormatterRoundsUpSecondsCleanly() {
        assertEquals("0:00", formatAutoOffRemaining(0L))
        assertEquals("0:01", formatAutoOffRemaining(250L))
        assertEquals("1:00", formatAutoOffRemaining(60_000L))
        assertEquals("4:59", formatAutoOffRemaining(298_500L))
        assertEquals("0:00", formatAutoOffRemaining(-5_000L))
    }

    @Test fun flashlightTileSubtitleReflectsAvailabilityModeBoostAndStrength() {
        assertEquals(
            "Unavailable",
            formatFlashlightTileSubtitle(false, false, FlashlightMode.STEADY, false, true, 5, 10),
        )
        assertEquals(
            "Off",
            formatFlashlightTileSubtitle(true, false, FlashlightMode.STEADY, false, true, 5, 10),
        )
        assertEquals(
            "SOS",
            formatFlashlightTileSubtitle(true, true, FlashlightMode.SOS, false, true, 5, 10),
        )
        assertEquals(
            "50%",
            formatFlashlightTileSubtitle(true, true, FlashlightMode.STEADY, false, true, 5, 10),
        )
        assertEquals(
            "Boost • 100%",
            formatFlashlightTileSubtitle(true, true, FlashlightMode.STEADY, true, true, 10, 10),
        )
        assertEquals(
            "On",
            formatFlashlightTileSubtitle(true, true, FlashlightMode.STEADY, false, false, 1, 1),
        )
    }
}
