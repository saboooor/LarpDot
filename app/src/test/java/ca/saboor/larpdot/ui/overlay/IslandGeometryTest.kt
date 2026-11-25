package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.CustomIslandWidgetLabels
import ca.saboor.larpdot.service.customIslandDisplayInfo
import ca.saboor.larpdot.ui.components.sampleOpaqueRgb
import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    @Test fun customCompactWidthUsesTheLongestWingAndItsOuterInset() {
        val island = CustomIsland("test", "Test island")
        val icons = island.copy(left = island.left.copy(icon = CustomIslandIcon.STAR))
        assertEquals(52.dp, customIslandCompactExtra(icons, 32.dp))

        val widget = icons.copy(right = icons.right.copy(widgetId = 1, width = 72))
        assertEquals(152.dp, customIslandCompactExtra(widget, 32.dp))
    }

    @Test fun expandedCustomPaddingLeavesSpaceBelowTheCamera() {
        val beneathCamera = customIslandExpandedPadding(28.dp)
        assertEquals(32.dp, beneathCamera.calculateTopPadding())
        assertEquals(8.dp, beneathCamera.calculateBottomPadding())
        assertEquals(8.dp, customIslandExpandedPadding(0.dp).calculateTopPadding())

        val fullSpan = customIslandExpandedPadding(28.dp, spanFullIsland = true)
        assertEquals(0.dp, fullSpan.calculateTopPadding())
        assertEquals(0.dp, fullSpan.calculateBottomPadding())
    }

    @Test fun portraitElementMovesDownAtCompactSizeBeforeGrowing() {
        val start = Rect(44f, 18f, 64f, 38f)
        val end = Rect(62f, 52f, 126f, 116f)
        val camera = Offset(80f, 27f)
        val clearing = cameraClearingElementBounds(start, end, camera, 16f, isLandscape = false, progress = 0.2f)
        assertEquals(start.left, clearing.left, 0f)
        assertEquals(start.size, clearing.size)
        assertTrue(clearing.top > start.top)

        val growing = cameraClearingElementBounds(start, end, camera, 16f, isLandscape = false, progress = 0.6f)
        assertTrue(growing.top >= 43f)
        assertTrue(growing.width > start.width)
        assertEquals(end, cameraClearingElementBounds(start, end, camera, 16f, isLandscape = false, progress = 1f))
    }

    @Test fun landscapeElementClearsTheCameraTowardThePlayer() {
        val start = Rect(88f, 24f, 108f, 44f)
        val end = Rect(12f, 18f, 68f, 74f)
        val camera = Offset(100f, 62f)
        val cleared = cameraClearingElementBounds(start, end, camera, 14f, isLandscape = true, progress = 0.32f)
        assertEquals(start.top, cleared.top, 0f)
        assertEquals(start.size, cleared.size)
        assertEquals(86f, cleared.right, 0f)
    }

    @Test fun elementCrossesBelowTheCameraBeforeRisingToItsExpandedSlot() {
        val start = Rect(225f, 18f, 245f, 38f)
        val end = Rect(110f, 22f, 154f, 66f)
        val camera = Offset(200f, 27f)
        val clearance = 16f
        val clearanceBottom = camera.y + clearance
        for (step in 32..100) {
            val bounds = cameraClearingElementBounds(start, end, camera, clearance, isLandscape = false, progress = step / 100f)
            val besideCamera = bounds.right <= camera.x - clearance || bounds.left >= camera.x + clearance
            assertTrue("Element crosses the camera at step $step", besideCamera || bounds.top >= clearanceBottom - 0.01f)
        }
        assertEquals(end, cameraClearingElementBounds(start, end, camera, clearance, isLandscape = false, progress = 1f))
    }

    @Test fun cameraClearanceStaysFixedWhileTheShellMoves() {
        val compactCenter = Offset(234f, 28f)
        val expandedCenter = Offset(180f, 80f)
        val camera = Offset(200f, 28f)
        val path = IslandElementPath(compactCenter, expandedCenter, camera, 14f, 16f, isLandscape = false)
        val start = Rect(-10f, -10f, 10f, 10f)
        val end = Rect(-54f, 0f, 10f, 32f)
        val canvasCenter = Offset(55f, 32f)
        val progress = 0.32f
        val shellCenter = compactCenter + (expandedCenter - compactCenter) * progress
        val frame = path.frameInShell(start, end, canvasCenter, progress)
        val bounds = frame.bounds.translate(shellCenter - canvasCenter)
        assertEquals(start.translate(compactCenter).left, bounds.left, 0.001f)
        assertEquals(camera.y + 16f, bounds.top, 0.001f)
        assertEquals(start.width, bounds.width, 0.001f)
        assertEquals(0f, frame.morphFraction, 0f)
    }

    @Test fun sameSideElementsMorphImmediatelyFromTheMainIslandAndSecondaryBubbles() {
        val camera = Offset(200f, 28f)
        val icon = Rect(-10f, -10f, 10f, 10f)
        for (direction in listOf(-1f, 1f)) {
            val target = Rect(40f * direction - 18f, -18f, 40f * direction + 18f, 18f)
            for (compactCenter in listOf(camera, Offset(camera.x + 68f * direction, camera.y))) {
                val source = if (compactCenter == camera) icon.translate(Offset(24f * direction, 0f)) else icon
                val path = IslandElementPath(compactCenter, camera, camera, 12f, 14f, isLandscape = false)
                val frame = path.frameInShell(source, target, compactCenter, progress = 0.2f)
                assertTrue(frame.morphFraction > 0f)
                assertTrue(frame.bounds.width > source.width)
            }
        }
    }

    @Test fun diagonalPathThatMissesTheRoundCameraCanMorphImmediately() {
        val camera = Offset(200f, 28f)
        val path = IslandElementPath(Offset.Zero, Offset.Zero, camera, 12f, 14f, isLandscape = false)
        val start = Rect(178f, 34f, 184f, 40f)
        val end = Rect(216f, 60f, 222f, 66f)
        val frame = path.frameInShell(start, end, Offset.Zero, progress = 0.2f)
        assertTrue(frame.morphFraction > 0f)
        assertTrue(frame.bounds.left > start.left)
    }

    @Test fun secondaryElementClearsTheCameraWithoutStoppingAtTheHandoff() {
        val camera = Offset(200f, 27f)
        val path = IslandElementPath(
            Offset.Zero, Offset.Zero, camera, 14f, 16f,
            isLandscape = false, fromSecondaryBubble = true,
        )
        for (direction in listOf(-1f, 1f)) {
            val sourceX = camera.x + 80f * direction
            val targetX = camera.x - 70f * direction
            val start = Rect(sourceX - 10f, 18f, sourceX + 10f, 38f)
            val end = Rect(targetX - 20f, 22f, targetX + 20f, 62f)
            val cleared = path.frameInShell(start, end, Offset.Zero, progress = 0.32f)
            val crossing = path.frameInShell(start, end, Offset.Zero, progress = 0.55f)

            assertEquals(camera.x + 26f * direction, cleared.bounds.center.x, 0.001f)
            assertEquals(camera.y + 16f, cleared.bounds.top, 0.001f)
            assertEquals(start.size, cleared.bounds.size)
            for (progress in listOf(0.08f, 0.16f, 0.24f)) {
                val approaching = path.frameInShell(start, end, Offset.Zero, progress)
                val sideways = (approaching.bounds.center.x - sourceX) / (cleared.bounds.center.x - sourceX)
                val downward = (approaching.bounds.top - start.top) / (cleared.bounds.top - start.top)
                assertTrue(sideways > 0f && sideways < 1f)
                assertTrue(downward > 0f && downward < 1f)
                assertEquals(0f, approaching.morphFraction, 0f)
            }
            val beforeHandoff = path.frameInShell(start, end, Offset.Zero, progress = 0.319f).bounds.center.x
            val afterHandoff = path.frameInShell(start, end, Offset.Zero, progress = 0.321f).bounds.center.x
            val incomingVelocity = (cleared.bounds.center.x - beforeHandoff) / 0.001f
            val outgoingVelocity = (afterHandoff - cleared.bounds.center.x) / 0.001f
            assertTrue(incomingVelocity * direction < -100f)
            assertEquals(incomingVelocity, outgoingVelocity, 3f)
            assertTrue(crossing.morphFraction > 0f)
            assertTrue(crossing.bounds.top >= cleared.bounds.top)
        }
    }

    @Test fun compactCircleInsetMatchesTheClearanceAcrossThePill() {
        val thickness = 24.dp
        assertEquals(4.dp, compactEndInset(thickness, compactNestedArtworkSize(thickness)))
        assertEquals(3.dp, compactEndInset(thickness, compactBatteryRingDiameter(thickness)))

        val narrowThickness = 16.dp
        val narrowRing = compactBatteryRingDiameter(narrowThickness)
        assertEquals(14.dp, narrowRing)
        assertEquals(1.dp, compactEndInset(narrowThickness, narrowRing))
    }

    @Test fun shortTitleDoesNotExpandToTheMaximum() {
        val natural = compactTextMediaWings(60f, 24f, 0f, false)
        val atHalf = fitCompactMediaWings(natural, 24f, 400f, 200f, 50f, symmetric = true)
        val atFull = fitCompactMediaWings(natural, 24f, 400f, 200f, 100f, symmetric = true)
        assertEquals(32f, atFull.leadingDp, 0.001f)
        assertEquals(32f, atFull.trailingDp, 0.001f)
        assertEquals(atHalf, atFull)
    }

    @Test fun longTitleStopsAtTheConfiguredLimit() {
        val natural = compactTextMediaWings(60f, 600f, 0f, false)
        assertEquals(CompactMediaWings(94f, 94f), fitCompactMediaWings(natural, 24f, 400f, 200f, 50f, symmetric = true))
        assertEquals(CompactMediaWings(188f, 188f), fitCompactMediaWings(natural, 24f, 400f, 200f, 100f, symmetric = true))
    }

    @Test fun announcementFitsEachTextWingAndNestedArtwork() {
        val natural = compactTextMediaWings(60f, 30f, 50f, true, 21f)
        assertEquals(CompactMediaWings(51f, 58f), natural)
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
        val symmetric = fitCompactMediaWings(CompactMediaWings(188f, 188f), 24f, 400f, 200f, 100f, 40f, 60f, symmetric = true)
        assertEquals(CompactMediaWings(128f, 128f), symmetric)
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

    @Test fun widgetEyedropperIgnoresTransparentPixelsAndExtractsRgb() {
        assertNull(sampleOpaqueRgb(0x00123456))
        assertNull(sampleOpaqueRgb(0x0FABCDEF))
        assertEquals(0x123456, sampleOpaqueRgb(0xFF123456.toInt()))
        assertEquals(0xABCDEF, sampleOpaqueRgb(0x80ABCDEF.toInt()))
    }

    @Test fun cameraDotSwipeDownMatchesVerticalDragThreshold() {
        assertTrue(isCameraDotSwipeDown(dx = 0f, dy = 20f, density = 3f))
        assertTrue(isCameraDotSwipeDown(dx = 25f, dy = 20f, density = 3f))
        assertFalse(isCameraDotSwipeDown(dx = 0f, dy = 15f, density = 3f))
        assertFalse(isCameraDotSwipeDown(dx = 40f, dy = 20f, density = 3f))
        assertFalse(isCameraDotSwipeDown(dx = 0f, dy = -20f, density = 3f))
    }

    @Test fun cameraDotSwipeBoundsAnchorFlushToEdgeWithGenerousWidth() {
        val portrait = cameraDotSwipeBounds(
            cameraCenterX = 540f,
            cameraCenterY = 66f,
            cameraRadiusPx = 36f,
            screenWidthPx = 1080f,
            density = 3f,
            isLandscape = false,
        )
        assertEquals(0f, portrait.top, 0f)
        assertEquals(372f, portrait.left, 0f)
        assertEquals(708f, portrait.right, 0f)
        assertEquals(150f, portrait.bottom, 0f)

        val landscapeLeft = cameraDotSwipeBounds(
            cameraCenterX = 66f,
            cameraCenterY = 540f,
            cameraRadiusPx = 36f,
            screenWidthPx = 2400f,
            density = 3f,
            isLandscape = true,
        )
        assertEquals(0f, landscapeLeft.left, 0f)
        assertEquals(150f, landscapeLeft.right, 0f)
        assertEquals(372f, landscapeLeft.top, 0f)
        assertEquals(708f, landscapeLeft.bottom, 0f)
    }

    @Test fun interactivePullTracksFingerLinearlyWithSoftOverscrollAndVelocityCommit() {
        assertEquals(0f, interactivePullProgress(-20f, 300f), 0f)
        assertEquals(0.5f, interactivePullProgress(150f, 300f), 0.0001f)
        assertEquals(1f, interactivePullProgress(300f, 300f), 0.0001f)
        val overscroll = interactivePullProgress(600f, 300f)
        assertTrue(overscroll > 1f && overscroll < 1.08f)

        // Commit on sufficient pull or fast downward flick; cancel on upward flick
        assertTrue(shouldCommitSwipeDownPull(progress = 0.25f, velocityYPxPerSec = 0f, density = 3f))
        assertFalse(shouldCommitSwipeDownPull(progress = 0.15f, velocityYPxPerSec = 0f, density = 3f))
        assertTrue(shouldCommitSwipeDownPull(progress = 0.08f, velocityYPxPerSec = 700f, density = 3f))
        assertFalse(shouldCommitSwipeDownPull(progress = 0.8f, velocityYPxPerSec = -1200f, density = 3f))
        // If SystemUI pilfers the gesture mid-pull, commit active downward pulls but not upward/idle cancels
        assertTrue(shouldCommitSwipeDownPull(progress = 0.12f, velocityYPxPerSec = 400f, density = 3f, cancelled = true))
        assertFalse(shouldCommitSwipeDownPull(progress = 0.12f, velocityYPxPerSec = -100f, density = 3f, cancelled = true))
        assertFalse(shouldCommitSwipeDownPull(progress = 0.01f, velocityYPxPerSec = 0f, density = 3f, cancelled = true))
    }

    @Test fun customIslandDisplayInfoDescribesWidgetsAndSlotsWithoutRequiringName() {
        val empty = CustomIsland("a")
        val emptyInfo = customIslandDisplayInfo(empty) { null }
        assertEquals("Empty island", emptyInfo.title)
        assertEquals("Idle • Tap to add widget", emptyInfo.subtitle)

        val swipeDown = CustomIsland(
            id = "b",
            main = empty.main.copy(widgetId = 10, width = 320, height = 160, spanFullIsland = true),
            visibility = CustomIslandVisibility.SWIPE_DOWN,
        )
        val swipeInfo = customIslandDisplayInfo(swipeDown) {
            CustomIslandWidgetLabels(label = "Weather", appName = "Pixel Weather")
        }
        assertEquals("Weather", swipeInfo.title)
        assertEquals("Swipe down • Pixel Weather • 320×160 dp • Full island", swipeInfo.subtitle)

        val winged = CustomIsland(
            id = "c",
            left = empty.left.copy(icon = CustomIslandIcon.STAR),
            main = empty.main.copy(widgetId = 20, width = 280, height = 140),
            visibility = CustomIslandVisibility.ALWAYS,
        )
        val wingedInfo = customIslandDisplayInfo(winged) {
            CustomIslandWidgetLabels(label = "Clock", appName = "Clock")
        }
        assertEquals("Clock", wingedInfo.title)
        assertEquals("Always • 280×140 dp • Left: Star", wingedInfo.subtitle)
    }
}
