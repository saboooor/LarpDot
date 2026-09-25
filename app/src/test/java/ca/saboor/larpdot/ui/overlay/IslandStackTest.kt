package ca.saboor.larpdot.ui.overlay

import org.junit.Assert.*
import org.junit.Test

class IslandStackTest {
    @Test fun everyBuiltInCombinationKeepsEachActiveProviderExactlyOnce() {
        for (mask in 0..7) {
            val music = mask and 1 != 0
            val flashlight = mask and 2 != 0
            val activity = mask and 4 != 0
            val expected = buildList {
                if (activity) add(IslandType.NOTIFICATION_ACTIVITY)
                if (music) add(IslandType.MEDIA)
                if (flashlight) add(IslandType.FLASHLIGHT)
            }
            val stack = builtInIslandStack(music, flashlight, activity)
            assertEquals(expected, stack.ordered.map { it.content })
            assertEquals(expected.firstOrNull(), stack.primary?.content)
            assertEquals(expected.drop(1), stack.secondary.map { it.content })
        }
    }

    @Test fun endingPrimaryPromotesMusicAndKeepsFlashlightInADot() {
        val before = builtInIslandStack(true, true, true)
        val after = builtInIslandStack(true, true, false)
        assertEquals(before.secondary, after.ordered)
    }

    @Test fun navigationIsLowerPriorityThanMusicEvenWithRightWingEta() {
        val stack = builtInIslandStack(
            music = true,
            flashlight = false,
            activity = true,
            activityHasRightWing = true,
            activityKind = ca.saboor.larpdot.notification.NotificationActivityKind.NAVIGATION,
        )
        // Music (priority 200) wins over Navigation (priority 150)
        assertEquals(IslandType.MEDIA, stack.primary?.content)
        assertEquals(listOf(IslandType.NOTIFICATION_ACTIVITY), stack.secondary.map { it.content })
    }

    @Test fun activityWithoutRightWingIsDemotedToDotSoMusicTakesOver() {
        // When activity has no right wing content (no progress percentage, ETA, or chronometer),
        // it is demoted to a dot (priority 150) so two-sided music (priority 200) takes over the primary island.
        val stack = builtInIslandStack(
            music = true,
            flashlight = true,
            activity = true,
            activityHasRightWing = false,
        )
        assertEquals(IslandType.MEDIA, stack.primary?.content)
        assertEquals(
            listOf(IslandType.NOTIFICATION_ACTIVITY, IslandType.FLASHLIGHT),
            stack.secondary.map { it.content },
        )
    }

    @Test fun activityWithRightWingWinsPrimaryOverMusic() {
        // When activity has right-wing status (e.g. progress percentage 75%, timer countdown, or ETA),
        // it keeps top priority (300) over music (200).
        val stack = builtInIslandStack(
            music = true,
            flashlight = true,
            activity = true,
            activityHasRightWing = true,
        )
        assertEquals(IslandType.NOTIFICATION_ACTIVITY, stack.primary?.content)
        assertEquals(
            listOf(IslandType.MEDIA, IslandType.FLASHLIGHT),
            stack.secondary.map { it.content },
        )
    }

    @Test fun activityWithoutRightWingWinsOverFlashlightWhenMusicInactive() {
        // If music is not active, single-sided activity (150) still beats flashlight (100)
        val stack = builtInIslandStack(
            music = false,
            flashlight = true,
            activity = true,
            activityHasRightWing = false,
        )
        assertEquals(IslandType.NOTIFICATION_ACTIVITY, stack.primary?.content)
        assertEquals(listOf(IslandType.FLASHLIGHT), stack.secondary.map { it.content })
    }

    @Test fun futureProvidersParticipateWithoutChangingTheResolver() {
        val entries = listOf(
            IslandCandidate("download:1", 100, "Download"),
            IslandCandidate("call:1", 400, "Call"),
            IslandCandidate("navigation", 300, "Navigation"),
            IslandCandidate("download:2", 100, "Another download"),
        )
        val stack = resolveIslandStack(entries)
        assertEquals("Call", stack.primary?.content)
        assertEquals(listOf("navigation", "download:1", "download:2"), stack.secondary.map { it.id })
        assertEquals(stack, resolveIslandStack(entries.reversed()))
    }

    @Test fun twoDotsFitOnANarrowDisplayWithoutMovingTheCameraAnchor() {
        val width = stackedPillWidth(234f, 28f, 2, 360f)
        assertEquals(234f, width, 0.001f)
        val dotExtent = 28f + 8f
        // Left dot fits within screen bounds
        assertTrue(180f - width / 2f - dotExtent >= 0f)
        // Right dot fits within screen bounds
        assertTrue(180f + width / 2f + dotExtent <= 360f)

        // When requested width exceeds available symmetrically constrained space (360 - 2*36 = 288):
        val constrainedWidth = stackedPillWidth(300f, 28f, 2, 360f)
        assertEquals(288f, constrainedWidth, 0.001f)
        assertTrue(180f - constrainedWidth / 2f - dotExtent >= 0f)
        assertTrue(180f + constrainedWidth / 2f + dotExtent <= 360f)

        assertEquals(234f, stackedPillWidth(234f, 28f, 0, 360f), 0.001f)
    }

    @Test fun twoSecondaryCandidatesAreIdentifiedForLeftAndRightDots() {
        val stack = builtInIslandStack(music = true, flashlight = true, activity = true, activityHasRightWing = true)
        assertEquals(2, stack.secondary.size)
        // In this 3-item configuration:
        // Primary: NOTIFICATION_ACTIVITY (main pill)
        // Secondary[0]: MEDIA (left dot)
        // Secondary[1]: FLASHLIGHT (right dot)
        assertEquals(IslandType.NOTIFICATION_ACTIVITY, stack.primary?.content)
        assertEquals(IslandType.MEDIA, stack.secondary[0].content)
        assertEquals(IslandType.FLASHLIGHT, stack.secondary[1].content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdentitiesAreRejected() {
        resolveIslandStack(listOf(IslandCandidate("same", 1, 1), IslandCandidate("same", 2, 2)))
    }

    @Test fun secondaryItemWidthReturnsThicknessWhenMiniPillDisabled() {
        val thickness = 28f
        assertEquals(thickness, secondaryItemWidthDp(IslandType.MEDIA, thickness, false, true, "100%", "5m"), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, false, true, "100%", "5m"), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, false, true, "100%", "5m"), 0.001f)
    }

    @Test fun secondaryItemWidthExpandsToMiniPillWhenEnabledAndContentPresent() {
        val thickness = 28f
        // Media with music active expands by 28dp
        assertEquals(thickness + 28f, secondaryItemWidthDp(IslandType.MEDIA, thickness, true, true, "", null), 0.001f)
        // Media without active music stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.MEDIA, thickness, true, false, "", null), 0.001f)

        // Flashlight with status expands by 24dp
        assertEquals(thickness + 24f, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, true, false, "100%", null), 0.001f)
        // Flashlight without status stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, true, false, "", null), 0.001f)

        // Notification activity with short status (e.g. "5m") expands by 24dp
        assertEquals(thickness + 24f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, false, "", "5m"), 0.001f)
        // Notification activity with longer status (e.g. "12:30") expands by 34dp
        assertEquals(thickness + 34f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, false, "", "12:30"), 0.001f)
        // Notification activity with empty/null status stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, false, "", null), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, false, "", ""), 0.001f)
    }

    @Test fun stackedPillWidthWithExtentsConstrainsCorrectlyForAsymmetricExtents() {
        val thickness = 28f
        val leftExtent = 56f + 8f // Mini pill on left
        val rightExtent = 28f + 8f // Dot on right
        val availableWidth = 360f

        val pillWidth = stackedPillWidthWithExtents(
            requested = 250f,
            thickness = thickness,
            leftExtent = leftExtent,
            rightExtent = rightExtent,
            availableWidth = availableWidth,
        )
        // maxExtent is 64f. Symmetrical space is 360 - 2 * 64 = 232f.
        assertEquals(232f, pillWidth, 0.001f)
        // Anchored at screen center (180):
        val halfPill = pillWidth / 2f
        assertTrue(180f - halfPill - leftExtent >= 0f)
        assertTrue(180f + halfPill + rightExtent <= availableWidth)
    }

    @Test fun compactMediaReclaimsOnlyUnusedTrailingWing() {
        val wings = compactMediaWings(
            extraWidthDp = 120f,
            hasTrailingBubble = true,
            showTitle = false,
            announcing = false,
            visualizerWidthDp = 19f,
        )
        assertEquals(60f, wings.leadingDp, 0.001f)
        assertEquals(33f, wings.trailingDp, 0.001f)
        assertEquals(93f, wings.widthDp, 0.001f)
        assertEquals(33f / 93f, wings.trailingFraction, 0.001f)
    }

    @Test fun compactMediaKeepsSymmetryForTitleAnnouncementAndWideVisualizer() {
        val withoutBubble = compactMediaWings(120f, false, false, false, 19f)
        val withTitle = compactMediaWings(120f, true, true, false, 19f)
        val announcement = compactMediaWings(120f, true, false, true, 19f)
        val wideVisualizer = compactMediaWings(120f, true, false, false, 70f)
        listOf(withoutBubble, withTitle, announcement, wideVisualizer).forEach { wings ->
            assertEquals(60f, wings.leadingDp, 0.001f)
            assertEquals(60f, wings.trailingDp, 0.001f)
        }
    }
}
