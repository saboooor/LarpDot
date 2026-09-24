package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.service.OverlayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementExtraWidthTest {

    @Test
    fun shortTextsResultInUltraCompactIslandToAvoidEmptyArea() {
        val extraForShortTexts = calculateAnnouncementExtraDp(
            title = "Hi",
            artist = "Low",
            cutoutDiameterDp = 28f,
            albumArtStyle = OverlayPreferences.AlbumArtStyle.BASIC_FADED,
            density = 3f,
        )

        // Must be ultra-compact (< 65dp extra space) to avoid empty area
        assertTrue("Expected ultra-compact island for short texts, got: $extraForShortTexts", extraForShortTexts < 65f)
        assertTrue("Expected at least minimum width, got: $extraForShortTexts", extraForShortTexts >= 24f)
    }

    @Test
    fun longTextsCapAt210Dp() {
        val extraForLongTexts = calculateAnnouncementExtraDp(
            title = "This Is A Very Long Song Title That Will Definitely Overflow",
            artist = "An Extremely Long Artist Name For Testing Purposes",
            cutoutDiameterDp = 28f,
            albumArtStyle = OverlayPreferences.AlbumArtStyle.BASIC_FADED,
            density = 3f,
        )

        assertEquals(210f, extraForLongTexts, 0.01f)
    }

    @Test
    fun nestedArtRequiresExtraWidthComparedToNoArt() {
        val extraWithNoArt = calculateAnnouncementExtraDp(
            title = "Intro",
            artist = "XX",
            cutoutDiameterDp = 28f,
            albumArtStyle = OverlayPreferences.AlbumArtStyle.BASIC_FADED,
            density = 3f,
        )

        val extraWithNestedArt = calculateAnnouncementExtraDp(
            title = "Intro",
            artist = "XX",
            cutoutDiameterDp = 28f,
            albumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
            density = 3f,
        )

        assertTrue("Nested art should expand wing for art icon", extraWithNestedArt > extraWithNoArt)
    }
}
