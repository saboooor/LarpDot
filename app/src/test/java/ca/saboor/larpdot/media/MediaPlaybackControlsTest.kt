package ca.saboor.larpdot.media

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.RotatedShape
import ca.saboor.larpdot.ui.overlay.formatRemainingTime
import ca.saboor.larpdot.ui.overlay.formatTime
import ca.saboor.larpdot.ui.overlay.formatTotalDuration
import ca.saboor.larpdot.ui.overlay.resolvePlaybackButtonVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun playbackPositionHandlesInvalidSessionValuesAndOverflow() {
        assertEquals(2_500L, MediaPlaybackState.computePlaybackPosition(1_000L, 1_000L, 1.5f, 10_000L))
        assertEquals(2_000L, MediaPlaybackState.computePlaybackPosition(1_000L, 1_000L, Float.NaN, 10_000L))
        assertEquals(0L, MediaPlaybackState.computePlaybackPosition(-1L, -1L, 1f, 10_000L))
        assertEquals(10_000L, MediaPlaybackState.computePlaybackPosition(9_500L, 1_000L, 2f, 10_000L))
        assertEquals(Long.MAX_VALUE, MediaPlaybackState.computePlaybackPosition(Long.MAX_VALUE - 1, 1_000L, 1f, 0L))
    }

    @Test
    fun timeFormattersHandleElapsedRemainingAndTotalDuration() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:00", formatTime(-4_000L))
        assertEquals("2:05", formatTime(125_000L))
        assertEquals("1:15:04", formatTime(4_504_000L))

        assertEquals("-1:15", formatRemainingTime(45_000L, 120_000L))
        assertEquals("-1:05:30", formatRemainingTime(300_000L, 4_230_000L))
        assertEquals("-0:00", formatRemainingTime(150_000L, 120_000L))
        assertEquals("--:--", formatRemainingTime(10_000L, 0L))

        assertEquals("3:45", formatTotalDuration(225_000L))
        assertEquals("2:00:00", formatTotalDuration(7_200_000L))
        assertEquals("--:--", formatTotalDuration(0L))
        assertEquals("--:--", formatTotalDuration(-1_000L))
    }

    @Test
    fun rotatedShapeSupportsStructuralEquality() {
        val base = RoundedCornerShape(12.dp)
        assertEquals(RotatedShape(base, 45f), RotatedShape(base, 45f))
        assertNotEquals(RotatedShape(base, 45f), RotatedShape(base, 90f))
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
