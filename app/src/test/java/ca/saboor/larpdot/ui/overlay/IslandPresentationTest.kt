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
}
