package ca.saboor.larpdot.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickTimerManagerTest {

    @Test
    fun formatQuickTimerDurationFormatsMinutesAndHours() {
        assertEquals("0:00", formatQuickTimerDuration(0L))
        assertEquals("0:00", formatQuickTimerDuration(-1_000L))
        assertEquals("0:05", formatQuickTimerDuration(5_000L))
        assertEquals("3:45", formatQuickTimerDuration(225_000L))
        assertEquals("1:05:09", formatQuickTimerDuration(3_909_000L))
    }

    @Test
    fun computeQuickTimerRemainingProgressClampsProperly() {
        assertNull(computeQuickTimerRemainingProgress(10_000L, 0L))
        assertEquals(100, computeQuickTimerRemainingProgress(60_000L, 60_000L))
        assertEquals(50, computeQuickTimerRemainingProgress(30_000L, 60_000L))
        assertEquals(0, computeQuickTimerRemainingProgress(0L, 60_000L))
        assertEquals(0, computeQuickTimerRemainingProgress(-5_000L, 60_000L))
        assertEquals(100, computeQuickTimerRemainingProgress(90_000L, 60_000L))
    }

    @Test
    fun formatCountdownTitleHandlesSecondsMinutesAndCustomLabels() {
        assertEquals("45s Timer", formatCountdownTitle(45))
        assertEquals("5m Timer", formatCountdownTitle(300))
        assertEquals("2m 30s Timer", formatCountdownTitle(150))
        assertEquals("Pomodoro", formatCountdownTitle(1500, "  Pomodoro  "))
        assertEquals("30s Timer", formatCountdownTitle(30, "   "))
    }
}
