package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.computePerimeterSamplePoints
import ca.saboor.larpdot.service.isBackgroundPitchBlackFromSamples
import ca.saboor.larpdot.service.isPitchBlackPixel
import ca.saboor.larpdot.service.resolveIslandOutlineVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun islandOutlineFadesWithVisibility() {
        assertEquals(0f, islandOutlineColor(0f).alpha, 0f)
        assertEquals(19f / 255f, islandOutlineColor(0.4f).alpha, 0.0001f)
        assertEquals(48f / 255f, islandOutlineColor(1f).alpha, 0.0001f)
        assertEquals(0f, islandOutlineColor(1f, enabled = false).alpha, 0f)
    }

    @Test fun dynamicIslandOutlineDetectsPitchBlackPerimeterExcludingBubbles() {
        // Pure OLED black and near-black count as pitch black; dark-mode grays and white do not
        assertTrue(isPitchBlackPixel(0xFF000000.toInt()))
        assertTrue(isPitchBlackPixel(0xFF0C0C0C.toInt()))
        assertFalse(isPitchBlackPixel(0xFF141218.toInt()))
        assertFalse(isPitchBlackPixel(0xFF1E1E1E.toInt()))
        assertFalse(isPitchBlackPixel(0xFFFFFFFF.toInt()))

        // Status bar icons on a pitch-black background still pass the threshold
        val blackWithIcons = List(16) { 0xFF000000.toInt() } + List(4) { 0xFFFFFFFF.toInt() }
        assertTrue(isBackgroundPitchBlackFromSamples(blackWithIcons))

        // Material dark-gray surface fails the threshold
        assertFalse(isBackgroundPitchBlackFromSamples(List(20) { 0xFF141218.toInt() }))
        assertFalse(isBackgroundPitchBlackFromSamples(emptyList()))

        // Sample points stay outside both the main pill and any secondary bubbles
        val pill = Rect(400f, 18f, 680f, 90f)
        val secondary = Rect(696f, 18f, 768f, 90f)
        val points = computePerimeterSamplePoints(pill, listOf(secondary), screenWidth = 1080, screenHeight = 2400)
        assertTrue(points.isNotEmpty())
        assertTrue(points.none { (x, y) -> x.toFloat() in pill.left..pill.right && y.toFloat() in pill.top..pill.bottom })
        assertTrue(points.none { (x, y) -> x.toFloat() in secondary.left..secondary.right && y.toFloat() in secondary.top..secondary.bottom })

        // Mode resolution
        assertFalse(resolveIslandOutlineVisible(OverlayPreferences.IslandOutlineMode.OFF, isBackgroundPitchBlack = true))
        assertTrue(resolveIslandOutlineVisible(OverlayPreferences.IslandOutlineMode.ON, isBackgroundPitchBlack = false))
        assertTrue(resolveIslandOutlineVisible(OverlayPreferences.IslandOutlineMode.DYNAMIC, isBackgroundPitchBlack = true))
        assertFalse(resolveIslandOutlineVisible(OverlayPreferences.IslandOutlineMode.DYNAMIC, isBackgroundPitchBlack = false))
    }
}
