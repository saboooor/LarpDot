package ca.saboor.larpdot.cutout

import android.view.Surface
import ca.saboor.larpdot.service.OverlayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutDetectorTest {

    @Test
    fun applyManualCutoutConfigPreservesHardwareWhenDisabledAndOffsetsWhenEnabled() {
        val hw = CutoutInfo(
            centerX = 540f,
            centerY = 66f,
            radiusPx = 36f,
            isAutoDetected = true,
        )
        val disabled = OverlayPreferences.CutoutConfig(
            isManualEnabled = false,
            offsetX = 10f,
            offsetY = -5f,
            customDiameterDp = 30f,
        )
        assertEquals(hw, CutoutDetector.applyManualCutoutConfig(hw, disabled, density = 3f))

        val enabled = disabled.copy(isManualEnabled = true)
        val adjusted = CutoutDetector.applyManualCutoutConfig(hw, enabled, density = 3f)
        assertFalse(adjusted.isAutoDetected)
        assertEquals(570f, adjusted.centerX, 0.001f)
        assertEquals(51f, adjusted.centerY, 0.001f)
        assertEquals(45f, adjusted.radiusPx, 0.001f)

        // When customDiameterDp is 0, hardware radius is kept
        val keepRadius = OverlayPreferences.CutoutConfig(
            isManualEnabled = true,
            offsetX = -2f,
            offsetY = 4f,
            customDiameterDp = 0f,
        )
        val kept = CutoutDetector.applyManualCutoutConfig(hw, keepRadius, density = 3f)
        assertEquals(36f, kept.radiusPx, 0.001f)
        assertEquals(534f, kept.centerX, 0.001f)
        assertEquals(78f, kept.centerY, 0.001f)
    }

    @Test
    fun fallbackCutoutCenterAdaptsToAllFourRotations() {
        val density = 3f
        val statusBar = 90f

        val portrait = CutoutDetector.computeFallbackCutoutCenter(1080f, 2400f, statusBar, Surface.ROTATION_0, density)
        assertEquals(540f, portrait.first, 0.001f)
        assertEquals(45f, portrait.second, 0.001f)

        val landscape90 = CutoutDetector.computeFallbackCutoutCenter(2400f, 1080f, statusBar, Surface.ROTATION_90, density)
        assertEquals(72f, landscape90.first, 0.001f) // max(45, 24*3)
        assertEquals(540f, landscape90.second, 0.001f)

        val landscape270 = CutoutDetector.computeFallbackCutoutCenter(2400f, 1080f, statusBar, Surface.ROTATION_270, density)
        assertEquals(2328f, landscape270.first, 0.001f)
        assertEquals(540f, landscape270.second, 0.001f)

        val upsideDown = CutoutDetector.computeFallbackCutoutCenter(1080f, 2400f, statusBar, Surface.ROTATION_180, density)
        assertEquals(540f, upsideDown.first, 0.001f)
        assertEquals(2328f, upsideDown.second, 0.001f)
    }

    @Test
    fun detectedCutoutGeometryMirrorsLandscapeEdgeAndClampsRadius() {
        val density = 3f
        // Portrait top punch-hole whose rect extends from top=0 (height 120, width 84 -> smaller dim 84 -> radius 42)
        val (px, py, pr) = CutoutDetector.computeDetectedCutoutGeometry(
            rawCenterX = 540f,
            rawCenterY = 60f,
            rawWidth = 84f,
            rawHeight = 120f,
            screenWidth = 1080f,
            screenHeight = 2400f,
            rotation = Surface.ROTATION_0,
            isLandscape = false,
            density = density,
            defaultRadius = 48f,
        )
        assertEquals(540f, px, 0.001f)
        assertEquals(60f, py, 0.001f)
        assertEquals(42f, pr, 0.001f)

        // Reverse landscape (ROTATION_270) forces camera to right edge & centers off-axis Y outliers
        val (rx, ry, rr) = CutoutDetector.computeDetectedCutoutGeometry(
            rawCenterX = 66f,
            rawCenterY = 50f,
            rawWidth = 30f,
            rawHeight = 30f,
            screenWidth = 2400f,
            screenHeight = 1080f,
            rotation = Surface.ROTATION_270,
            isLandscape = true,
            density = density,
            defaultRadius = 48f,
        )
        assertEquals(2334f, rx, 0.001f)
        assertEquals(540f, ry, 0.001f)
        assertTrue(rr >= 12f * density && rr <= 16f * density)
    }
}
