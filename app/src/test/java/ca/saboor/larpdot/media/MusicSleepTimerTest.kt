package ca.saboor.larpdot.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSleepTimerTest {

    @Test fun badgeFormatsOffEndOfTrackMinutesAndHours() {
        assertNull(formatSleepTimerBadge(SleepTimerState.Off))
        assertEquals("End", formatSleepTimerBadge(SleepTimerState.EndOfTrack("Starboy_The Weeknd")))
        assertEquals("0:01", formatSleepTimerBadge(SleepTimerState.Countdown(400L)))
        assertEquals("15:00", formatSleepTimerBadge(SleepTimerState.Countdown(15 * 60_000L)))
        assertEquals("1h 00m", formatSleepTimerBadge(SleepTimerState.Countdown(60 * 60_000L)))
        assertEquals("1h 15m", formatSleepTimerBadge(SleepTimerState.Countdown(75 * 60_000L)))
    }

    @Test fun endOfTrackTriggersOnlyWhenTrackActuallyChanges() {
        val waiting = SleepTimerState.EndOfTrack("Starboy_The Weeknd")
        assertFalse(shouldTriggerEndOfTrackSleep(SleepTimerState.Off, "Blinding Lights_The Weeknd"))
        assertFalse(shouldTriggerEndOfTrackSleep(SleepTimerState.Countdown(10_000L), "Blinding Lights_The Weeknd"))
        assertFalse(shouldTriggerEndOfTrackSleep(waiting, "Starboy_The Weeknd"))
        assertFalse(shouldTriggerEndOfTrackSleep(waiting, ""))
        assertFalse(shouldTriggerEndOfTrackSleep(SleepTimerState.EndOfTrack(""), "Starboy_The Weeknd"))
        assertTrue(shouldTriggerEndOfTrackSleep(waiting, "Blinding Lights_The Weeknd"))
    }
}
