package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    @Test fun shortTitleDoesNotExpandToTheMaximum() {
        val natural = compactTextMediaWings(60f, 20f, 0f, false)
        val atHalf = fitCompactMediaWings(natural, 24f, 400f, 200f, 50f)
        val atFull = fitCompactMediaWings(natural, 24f, 400f, 200f, 100f)
        assertEquals(30f, atFull.leadingDp, 0.001f)
        assertEquals(36f, atFull.trailingDp, 0.001f)
        assertEquals(atHalf, atFull)
    }

    @Test fun longTitleStopsAtTheConfiguredLimit() {
        val natural = compactTextMediaWings(60f, 600f, 0f, false)
        assertEquals(CompactMediaWings(30f, 94f), fitCompactMediaWings(natural, 24f, 400f, 200f, 50f))
        assertEquals(CompactMediaWings(30f, 188f), fitCompactMediaWings(natural, 24f, 400f, 200f, 100f))
    }

    @Test fun announcementFitsEachTextWingAndNestedArtwork() {
        val natural = compactTextMediaWings(60f, 30f, 50f, true, 21f)
        assertEquals(CompactMediaWings(59f, 58f), natural)
        val fitted = fitCompactMediaWings(natural, 24f, 400f, 200f, 100f)
        assertEquals(natural.leadingDp, fitted.leadingDp, 0.001f)
        assertEquals(natural.trailingDp, fitted.trailingDp, 0.001f)
    }

    @Test fun compactWidthCanFillTheStatusBar() {
        val wings = fitCompactMediaWings(CompactMediaWings(188f, 188f), 24f, 400f, 200f, 100f)
        assertEquals(400f, wings.widthDp + 24f, 0.001f)
        assertEquals(188f, wings.leadingDp, 0.001f)
    }

    @Test fun compactWidthRespectsTheConfiguredMaximum() {
        val wings = fitCompactMediaWings(CompactMediaWings(188f, 188f), 24f, 400f, 200f, 50f)
        assertEquals(212f, wings.widthDp + 24f, 0.001f)
    }

    @Test fun fullWidthKeepsAnOffCenterCameraAnchored() {
        val wings = fitCompactMediaWings(CompactMediaWings(188f, 188f), 24f, 400f, 40f, 100f)
        assertEquals(28f, wings.leadingDp, 0.001f)
        assertEquals(188f, wings.trailingDp, 0.001f)
        assertEquals(0f, 40f - 12f - wings.leadingDp, 0.001f)
    }

    @Test fun compactWidthLeavesRoomForSecondaryBubbles() {
        val wings = fitCompactMediaWings(CompactMediaWings(188f, 188f), 24f, 400f, 200f, 100f, 40f, 60f)
        assertEquals(148f, wings.leadingDp, 0.001f)
        assertEquals(128f, wings.trailingDp, 0.001f)
        assertEquals(300f, wings.widthDp + 24f, 0.001f)
    }

    @Test fun compactBackgroundStaysOpaqueWithTransparentExpandedSetting() {
        assertEquals(1f, islandBackgroundAlpha(0f, 0f), 0f)
        assertEquals(1f, islandBackgroundAlpha(0.4f, -0.1f), 0f)
    }

    @Test fun expandedBackgroundInterpolatesAndClampsSpringOvershoot() {
        assertEquals(0.4f, islandBackgroundAlpha(0.4f, 1f), 0.0001f)
        assertEquals(0.7f, islandBackgroundAlpha(0.4f, 0.5f), 0.0001f)
        assertEquals(0f, islandBackgroundAlpha(0f, 1.2f), 0f)
        assertEquals(1f, islandBackgroundAlpha(2f, 1f), 0f)
    }

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
        assertEquals(IslandCameraGeometry(24f, 24f, 12f), islandCameraGeometry(36f, 3f))
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

    @Test fun activeTouchBoundsFitOnlyExpandedCardWhenExpandedAndClearWhenEmpty() {
        val compact = Rect(120f, 14f, 280f, 46f)
        val secondary = Rect(290f, 14f, 322f, 46f)
        val expanded = Rect(40f, 14f, 360f, 120f)

        // Expanded mode covers only the expanded card itself (not the full 460dp draw window)
        assertEquals(
            listOf(expanded),
            activeIslandTouchBounds(isExpanded = true, expandedBounds = expanded, compactBounds = compact, secondaryBounds = listOf(secondary)),
        )
        // Before expanded bounds measure on frame 0, falls back to compact bounds
        assertEquals(
            listOf(compact),
            activeIslandTouchBounds(isExpanded = true, expandedBounds = null, compactBounds = compact, secondaryBounds = listOf(secondary)),
        )
        // Collapsed / dismissing uses compact + secondary bounds only
        assertEquals(
            listOf(compact, secondary),
            activeIslandTouchBounds(isExpanded = false, expandedBounds = expanded, compactBounds = compact, secondaryBounds = listOf(secondary)),
        )
        // No compact bounds after collapse returns empty so the touch window is removed
        assertTrue(
            activeIslandTouchBounds(isExpanded = false, expandedBounds = expanded, compactBounds = null, secondaryBounds = emptyList()).isEmpty(),
        )
    }
}
