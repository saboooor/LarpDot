package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    @Test fun touchRegionTracksAsymmetricAnimatedContent() {
        val region = islandTouchRegion(listOf(Rect(100.2f, 10f, 150.8f, 40f), Rect(160f, 15f, 210.5f, 35f)), 6f)
        assertEquals(IslandTouchRegion(94, 4, 123, 42), region)
    }

    @Test fun landscapeRightSideUsesMeasuredCoordinates() {
        assertEquals(IslandTouchRegion(950, 90, 60, 220),
            islandTouchRegion(listOf(Rect(960f, 100f, 1000f, 300f)), 10f))
    }

    @Test fun noContentDoesNotCreateATouchWindow() {
        assertNull(islandTouchRegion(emptyList(), 6f))
        assertNull(islandTouchRegion(listOf(Rect(0f, 0f, 0f, 0f)), 6f))
    }

    @Test fun paddingClampsAtScreenTopAndRoundsOutward() {
        assertEquals(IslandTouchRegion(4, 0, 32, 27),
            islandTouchRegion(listOf(Rect(10.5f, 2f, 30f, 20.5f)), 6f))
    }

    @Test fun cameraSizesUseDensityAndClampHardwareOutliers() {
        assertEquals(IslandCameraGeometry(24f, 28f, 14f), islandCameraGeometry(36f, 3f))
        assertEquals(36f, islandCameraGeometry(100f, 1f).diameter)
        assertEquals(16f, islandCameraGeometry(1f, 1f).diameter)
        assertEquals(24f, islandCameraGeometry(0f, 3f).diameter)
    }
    @Test fun expandedPanelMirrorsWithCameraSide() {
        val left = islandPanelPlacement(1000f, 400f, 20f, 200f, 12f, 300f, 200f, true)
        val right = islandPanelPlacement(1000f, 400f, 980f, 200f, 12f, 300f, 200f, true)
        assertEquals(IslandPanelPlacement(-310f, 100f), left)
        assertEquals(IslandPanelPlacement(310f, 100f), right)
    }

    @Test fun expandedPanelStaysInsideVerticalEdges() {
        assertEquals(14f, islandPanelPlacement(1000f, 400f, 20f, 10f, 12f, 300f, 200f, true).top)
        assertEquals(186f, islandPanelPlacement(1000f, 400f, 20f, 390f, 12f, 300f, 200f, true).top)
        assertEquals(IslandPanelPlacement(0f, 14f),
            islandPanelPlacement(400f, 1000f, 200f, 20f, 12f, 300f, 200f, false))
    }
}
