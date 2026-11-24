package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandPresentationTest {
    @Test fun allProvidersUseTheSameOrientationRules() {
        for (type in IslandType.entries) {
            val model = IslandModel(type, {}, presentation = IslandPresentation(compactExtra = 80.dp, landscapeExtra = 60.dp))
            assertEquals(104.dp, model.presentation.width(24.dp, 28.dp, false))
            assertEquals(28.dp, model.presentation.height(24.dp, 28.dp, false))
            assertEquals(28.dp, model.presentation.width(24.dp, 28.dp, true))
            assertEquals(84.dp, model.presentation.height(24.dp, 28.dp, true))
        }
    }

    @Test fun asymmetricMediaWingsOverrideCompactExtraOnlyInPortrait() {
        val presentation = IslandPresentation(compactExtra = 80.dp)
        assertEquals(144.dp, presentation.width(24.dp, 28.dp, false, 120.dp))
        assertEquals(28.dp, presentation.width(24.dp, 28.dp, true, 120.dp))
    }

    @Test fun compactOutlineHidesCompletelyWhenIslandIsHidden() {
        val hidden = compactOutlineStyle(showOutline = true, progress = 0.65f, contentAlpha = 0f)
        assertEquals(0f, hidden.progressFraction, 0f)
        assertEquals(0f, hidden.trackAlpha, 0f)
        assertEquals(0f, hidden.progressAlpha, 0f)

        val nearHidden = compactOutlineStyle(showOutline = true, progress = 1f, contentAlpha = 0.01f)
        assertEquals(0f, nearHidden.progressFraction, 0f)
        assertEquals(0f, nearHidden.trackAlpha, 0f)
        assertEquals(0f, nearHidden.progressAlpha, 0f)
    }

    @Test fun compactOutlineFadesWithIslandContentAlpha() {
        val fading = compactOutlineStyle(showOutline = true, progress = 0.5f, contentAlpha = 0.4f, accentAlpha = 0.8f)
        assertEquals(0.5f, fading.progressFraction, 0.0001f)
        assertEquals((48f / 255f) * 0.4f, fading.trackAlpha, 0.0001f)
        assertEquals(0.32f, fading.progressAlpha, 0.0001f)

        val noProgressOutline = compactOutlineStyle(showOutline = false, progress = 0.5f, contentAlpha = 1f)
        assertEquals(0f, noProgressOutline.progressFraction, 0f)
        assertEquals(48f / 255f, noProgressOutline.trackAlpha, 0.0001f)
        assertEquals(0f, noProgressOutline.progressAlpha, 0f)
    }
}
