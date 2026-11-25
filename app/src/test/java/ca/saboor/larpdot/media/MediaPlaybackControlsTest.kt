package ca.saboor.larpdot.media

import androidx.compose.ui.graphics.Color
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.formatRemainingTime
import ca.saboor.larpdot.ui.overlay.formatTime
import ca.saboor.larpdot.ui.overlay.formatTotalDuration
import ca.saboor.larpdot.ui.overlay.resolvePlaybackButtonVisuals
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlaybackControlsTest {

    @Test
    fun computeSeekTargetClampsWithinTrackBounds() {
        assertEquals(25_000L, MediaPlaybackState.computeSeekTarget(15_000L, 10_000L, 180_000L))
        assertEquals(0L, MediaPlaybackState.computeSeekTarget(4_000L, -10_000L, 180_000L))
        assertEquals(180_000L, MediaPlaybackState.computeSeekTarget(175_000L, 10_000L, 180_000L))
        assertEquals(0L, MediaPlaybackState.computeSeekTarget(-5_000L, -10_000L, 180_000L))
        // Live / unknown duration tracks still prevent negative seeks
        assertEquals(30_000L, MediaPlaybackState.computeSeekTarget(20_000L, 10_000L, 0L))
        assertEquals(0L, MediaPlaybackState.computeSeekTarget(3_000L, -10_000L, 0L))
    }

    @Test
    fun timeFormattersHandleElapsedRemainingAndTotalDuration() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:00", formatTime(-4_000L))
        assertEquals("2:05", formatTime(125_000L))

        assertEquals("-1:15", formatRemainingTime(45_000L, 120_000L))
        assertEquals("-0:00", formatRemainingTime(150_000L, 120_000L))
        assertEquals("--:--", formatRemainingTime(10_000L, 0L))

        assertEquals("3:45", formatTotalDuration(225_000L))
        assertEquals("--:--", formatTotalDuration(0L))
        assertEquals("--:--", formatTotalDuration(-1_000L))
    }

    @Test
    fun resolvePlaybackButtonVisualsAdaptsColorsToStyle() {
        val accent = Color(0xFF2196F3)
        val defaultStyle = OverlayPreferences.PlaybackControlsStyle(
            playPauseBackground = true,
            skipBackground = false,
            cornerRadiusDp = 22f,
            material3ButtonGroup = false,
            playPauseAccentColor = true,
        )
        val visuals = resolvePlaybackButtonVisuals(defaultStyle, accent)
        assertEquals(Color.Transparent, visuals.skipBgColor)
        assertEquals(accent, visuals.playBgColor)
        assertEquals(Color.White, visuals.playTint)

        val groupStyle = defaultStyle.copy(skipBackground = true, material3ButtonGroup = true)
        val groupVisuals = resolvePlaybackButtonVisuals(groupStyle, accent)
        assertEquals(Color.White.copy(alpha = 0.14f), groupVisuals.skipBgColor)
    }
}
