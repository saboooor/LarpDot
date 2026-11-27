package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandSettings
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.enforceSingleSwipeDownIsland
import org.junit.Assert.*
import org.junit.Test

class IslandStackTest {
    private val customIsland = CustomIsland("test", "Test island")

    @Test fun customIslandTakesThePrimarySlotWhenBuiltInIslandsAreIdle() {
        val idle = builtInIslandSlots(false, false, null, emptyList(), custom = customIsland)
        assertEquals(listOf(IslandSlot(IslandType.CUSTOM, customIslandId = customIsland.id)), idle.ordered.map { it.content })

        val busy = builtInIslandSlots(true, true, null, emptyList(), custom = customIsland)
        assertEquals(
            listOf(IslandSlot(IslandType.MEDIA), IslandSlot(IslandType.FLASHLIGHT)),
            busy.ordered.map { it.content },
        )
    }

    @Test fun alwaysShowsCustomIslandAlongsideBuiltInActivities() {
        val always = customIsland.copy(visibility = CustomIslandVisibility.ALWAYS)
        val stack = builtInIslandSlots(true, true, null, emptyList(), custom = always)
        assertEquals(
            listOf(IslandSlot(IslandType.CUSTOM, customIslandId = always.id), IslandSlot(IslandType.MEDIA), IslandSlot(IslandType.FLASHLIGHT)),
            stack.ordered.map { it.content },
        )
    }

    @Test fun customPriorityCanPlaceTheActiveIslandInASecondarySlot() {
        val always = customIsland.copy(visibility = CustomIslandVisibility.ALWAYS)
        val custom = IslandPriorityItem.Custom(always.id)
        val stack = builtInIslandSlots(
            music = true,
            flashlight = true,
            activity = null,
            shortTerms = emptyList(),
            priorityOrder = listOf(IslandPriorityItem.MEDIA, custom, IslandPriorityItem.FLASHLIGHT),
            custom = always,
        )
        assertEquals(
            listOf(IslandSlot(IslandType.MEDIA), IslandSlot(IslandType.CUSTOM, customIslandId = always.id), IslandSlot(IslandType.FLASHLIGHT)),
            stack.ordered.map { it.content },
        )
    }

    @Test fun customPriorityUsesStableIdsAndIgnoresDeletedIslands() {
        val existing = IslandPriorityItem.Custom(customIsland.id)
        val added = customIsland.copy(id = "another", name = "Another")
        val order = islandPriorityOrderWithCustom(
            listOf(IslandPriorityItem.MEDIA, IslandPriorityItem.Custom("deleted"), existing),
            listOf(customIsland.copy(name = "Renamed"), added),
        )
        assertEquals(listOf(IslandPriorityItem.Custom(added.id), IslandPriorityItem.MEDIA, existing), order.take(3))
        assertEquals(existing, IslandPriorityItem.fromName(existing.name))
        assertEquals(IslandPriorityItem.MEDIA, IslandPriorityItem.fromName("MEDIA"))
    }

    @Test fun conditionalIslandOnlyAppearsWhenItsSelectedActivityIsVisible() {
        val conditional = customIsland.copy(
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.FLASHLIGHT,
        )
        val noMatch = builtInIslandSlots(true, false, null, emptyList(), custom = conditional)
        assertEquals(listOf(IslandSlot(IslandType.MEDIA)), noMatch.ordered.map { it.content })
        val match = builtInIslandSlots(false, true, null, emptyList(), custom = conditional)
        assertEquals(
            listOf(IslandSlot(IslandType.CUSTOM, customIslandId = conditional.id), IslandSlot(IslandType.FLASHLIGHT)),
            match.ordered.map { it.content },
        )
    }

    @Test fun simultaneousStatusesUseDistinctSecondarySlotsAlongsideOtherProviders() {
        fun status(key: String) = ca.saboor.larpdot.notification.NotificationActivityInfo(
            key = key, packageName = "android", appName = "System", title = "Charging", text = null,
            kind = ca.saboor.larpdot.notification.NotificationActivityKind.BATTERY_CHARGING,
            progress = null, progressMax = null, progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null, chronometerCountsDown = false,
            postedAtMillis = 1L, contentIntent = null, actions = emptyList(),
        )
        val charging = status("charging")
        val bluetooth = status("bluetooth")
        val sound = status("sound")
        val stack = builtInIslandSlots(true, true, charging, listOf(charging, bluetooth, sound))
        assertEquals(5, stack.ordered.size)
        assertEquals(5, stack.ordered.map { it.content }.distinct().size)
        assertEquals(IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "charging"), stack.primary?.content)
        assertTrue(stack.secondary.any { it.content == IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "bluetooth") })
        assertTrue(stack.secondary.any { it.content == IslandSlot(IslandType.MEDIA) })
        val after = builtInIslandSlots(true, true, bluetooth, listOf(bluetooth, sound))
        assertFalse(after.ordered.any { it.content.activityKey == "charging" })
    }

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
        assertEquals(thickness, secondaryItemWidthDp(IslandType.MEDIA, thickness, false, "100%", "5m", musicRightInfoExtentDp = 16f), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, false, "100%", "5m"), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, false, "100%", "5m"), 0.001f)
    }

    @Test fun secondaryItemWidthExpandsToMiniPillWhenEnabledAndContentPresent() {
        val thickness = 28f
        // The music mini pill adds its trailing content width and inset
        assertEquals(thickness + 28f, secondaryItemWidthDp(IslandType.MEDIA, thickness, true, "", null, musicRightInfoExtentDp = 16f), 0.001f)
        // Media without right-side info stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.MEDIA, thickness, true, "", null), 0.001f)

        // Flashlight with status expands by 24dp
        assertEquals(thickness + 24f, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, true, "100%", null), 0.001f)
        // Flashlight without status stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.FLASHLIGHT, thickness, true, "", null), 0.001f)

        // Notification activity with short status (e.g. "5m") expands by 24dp
        assertEquals(thickness + 24f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, "", "5m"), 0.001f)
        // Notification activity with longer status (e.g. "12:30") expands by 34dp
        assertEquals(thickness + 34f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, "", "12:30"), 0.001f)
        // Notification activity with empty/null status stays a dot
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, "", null), 0.001f)
        assertEquals(thickness, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, thickness, true, "", ""), 0.001f)
    }

    @Test fun earbudMiniPillFitsBothRingsAndTheGap() {
        assertEquals(44f, secondaryItemWidthDp(
            IslandType.NOTIFICATION_ACTIVITY, 24f, true, "", null,
            smaller = true, hasEarbudBatteryLevels = true,
        ), 0.001f)
        assertEquals(24f, secondaryItemWidthDp(
            IslandType.NOTIFICATION_ACTIVITY, 24f, false, "", null,
            hasEarbudBatteryLevels = true,
        ), 0.001f)
    }

    @Test fun smallerSecondaryMiniPillsReduceExtraSpaceWhileFittingTheirContents() {
        assertEquals(48f, secondaryItemWidthDp(IslandType.MEDIA, 24f, true, "", null, smaller = true, musicRightInfoExtentDp = 12f), 0.001f)
        assertEquals(44f, secondaryItemWidthDp(IslandType.FLASHLIGHT, 24f, true, "100%", null, smaller = true), 0.001f)
        assertEquals(51f, secondaryItemWidthDp(IslandType.FLASHLIGHT, 28f, true, "100%", null, smaller = true), 0.001f)
        assertEquals(54f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, 24f, true, "", "12:30", smaller = true), 0.001f)
        assertEquals(24f, secondaryItemWidthDp(IslandType.NOTIFICATION_ACTIVITY, 24f, true, "", null, smaller = true), 0.001f)
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

    @Test fun compactMediaUsesTheWiderWingForBothSides() {
        assertEquals(CompactMediaWings(60f, 60f), compactMediaWings(120f, 19f))
        assertEquals(CompactMediaWings(78f, 78f), compactMediaWings(120f, 70f))
    }

    @Test fun customPriorityOrderControlsMainIslandWinner() {
        val musicFirst = listOf(
            IslandPriorityItem.MEDIA,
            IslandPriorityItem.SHORT_TERM_ALERTS,
            IslandPriorityItem.LIVE_ACTIVITIES,
            IslandPriorityItem.NAVIGATION,
            IslandPriorityItem.FLASHLIGHT,
        )
        val stack = builtInIslandStack(
            music = true,
            flashlight = true,
            activity = true,
            activityHasRightWing = true,
            activityKind = ca.saboor.larpdot.notification.NotificationActivityKind.BATTERY_CHARGING,
            priorityOrder = musicFirst,
        )
        assertEquals(IslandType.MEDIA, stack.primary?.content)
        assertEquals(listOf(IslandType.NOTIFICATION_ACTIVITY, IslandType.FLASHLIGHT), stack.secondary.map { it.content })

        val navOverMusic = listOf(
            IslandPriorityItem.NAVIGATION,
            IslandPriorityItem.MEDIA,
            IslandPriorityItem.SHORT_TERM_ALERTS,
            IslandPriorityItem.LIVE_ACTIVITIES,
            IslandPriorityItem.FLASHLIGHT,
        )
        val navStack = builtInIslandStack(
            music = true,
            flashlight = false,
            activity = true,
            activityHasRightWing = true,
            activityKind = ca.saboor.larpdot.notification.NotificationActivityKind.NAVIGATION,
            priorityOrder = navOverMusic,
        )
        assertEquals(IslandType.NOTIFICATION_ACTIVITY, navStack.primary?.content)
        assertEquals(listOf(IslandType.MEDIA), navStack.secondary.map { it.content })
    }

    @Test fun pinnedShortTermAlertYieldsToMusicWhileAutoDismissAlertWinsMain() {
        val pinnedBt = ca.saboor.larpdot.notification.NotificationActivityInfo(
            key = "system_bluetooth_watch", packageName = "android", appName = "System",
            title = "Saboor's Pixel Watch 5", text = "Watch • Connected",
            kind = ca.saboor.larpdot.notification.NotificationActivityKind.BLUETOOTH,
            progress = null, progressMax = null, progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null, chronometerCountsDown = false,
            postedAtMillis = 1L, contentIntent = null, actions = emptyList(),
            bluetoothBatteryPercent = 85, isPinnedShortTerm = true,
        )
        val autoDismissSound = pinnedBt.copy(
            key = "system_sound_mode", title = "Silent Mode", text = "Calls and alerts muted",
            kind = ca.saboor.larpdot.notification.NotificationActivityKind.SOUND_MODE,
            bluetoothBatteryPercent = null, isPinnedShortTerm = false, postedAtMillis = 2L,
        )

        // Default order: Music beats Pinned Bluetooth, putting Pinned Bluetooth in a side dot
        val withPinnedOnly = builtInIslandSlots(
            music = true,
            flashlight = false,
            activity = pinnedBt,
            shortTerms = listOf(pinnedBt),
        )
        assertEquals(IslandSlot(IslandType.MEDIA), withPinnedOnly.primary?.content)
        assertEquals(listOf(IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "system_bluetooth_watch")), withPinnedOnly.secondary.map { it.content })

        // Auto-dismiss alert beats both Music and Pinned Bluetooth
        val withAutoDismiss = builtInIslandSlots(
            music = true,
            flashlight = false,
            activity = autoDismissSound,
            shortTerms = listOf(autoDismissSound, pinnedBt),
        )
        assertEquals(IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "system_sound_mode"), withAutoDismiss.primary?.content)
        assertEquals(
            listOf(
                IslandSlot(IslandType.MEDIA),
                IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "system_bluetooth_watch"),
            ),
            withAutoDismiss.secondary.map { it.content },
        )
    }

    @Test fun normalizeIslandPriorityOrderDeduplicatesAndAppendsMissingEntries() {
        val normalized = normalizeIslandPriorityOrder(
            listOf(IslandPriorityItem.FLASHLIGHT, IslandPriorityItem.FLASHLIGHT, IslandPriorityItem.MEDIA),
        )
        assertEquals(
            listOf(
                IslandPriorityItem.FLASHLIGHT,
                IslandPriorityItem.MEDIA,
                IslandPriorityItem.SHORT_TERM_ALERTS,
                IslandPriorityItem.LIVE_ACTIVITIES,
                IslandPriorityItem.PINNED_SHORT_TERM,
                IslandPriorityItem.NAVIGATION,
            ),
            normalized,
        )
    }

    @Test fun bluetoothAndNavigationResolveToDistinctPrimaryAndSecondaryActivities() {
        val nav = ca.saboor.larpdot.notification.NotificationActivityInfo(
            key = "maps_nav", packageName = "com.google.android.apps.maps", appName = "Maps",
            title = "15 min", text = "Turn right on Main St",
            kind = ca.saboor.larpdot.notification.NotificationActivityKind.NAVIGATION,
            progress = null, progressMax = null, progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null, chronometerCountsDown = false,
            postedAtMillis = 1L, contentIntent = null, actions = emptyList(),
        )
        val pinnedBt = nav.copy(
            key = "system_bluetooth_buds", packageName = "android", appName = "System",
            title = "Pixel Buds Pro", text = "Earbuds • Connected",
            kind = ca.saboor.larpdot.notification.NotificationActivityKind.BLUETOOTH,
            bluetoothBatteryPercent = 80, isPinnedShortTerm = true, postedAtMillis = 2L,
        )

        val stack = builtInIslandSlots(
            music = false,
            flashlight = false,
            activity = nav,
            shortTerms = listOf(pinnedBt),
        )
        assertEquals(IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "system_bluetooth_buds"), stack.primary?.content)
        assertEquals(listOf(IslandSlot(IslandType.NOTIFICATION_ACTIVITY, "maps_nav")), stack.secondary.map { it.content })
        assertEquals(pinnedBt, resolveIslandSlotActivity(stack.primary?.content, nav, listOf(pinnedBt)))
        assertEquals(nav, resolveIslandSlotActivity(stack.secondary.first().content, nav, listOf(pinnedBt)))
    }

    @Test fun secondaryDotsAlternateRightThenLeftOutwardFromTheMainIsland() {
        assertEquals(SecondarySideSplit(emptyList<String>(), emptyList()), splitSecondarySlots(emptyList<String>()))
        assertEquals(SecondarySideSplit(emptyList(), listOf("1")), splitSecondarySlots(listOf("1")))
        assertEquals(SecondarySideSplit(listOf("2"), listOf("1")), splitSecondarySlots(listOf("1", "2")))
        assertEquals(SecondarySideSplit(listOf("2"), listOf("1", "3")), splitSecondarySlots(listOf("1", "2", "3")))
        assertEquals(SecondarySideSplit(listOf("4", "2"), listOf("1", "3")), splitSecondarySlots(listOf("1", "2", "3", "4")))
        assertEquals(
            SecondarySideSplit(listOf("4", "2"), listOf("1", "3", "5")),
            splitSecondarySlots(listOf("1", "2", "3", "4", "5")),
        )
    }

    @Test fun swipeDownCustomIslandNeverCreatesAMinimizedSlotAndIsActiveIndependently() {
        val swipeDownNoWidget = customIsland.copy(
            id = "swipe",
            visibility = CustomIslandVisibility.SWIPE_DOWN,
            left = customIsland.left.copy(icon = CustomIslandIcon.STAR),
        )
        assertFalse(swipeDownNoWidget.hasContent)

        val swipeDownWithWidget = swipeDownNoWidget.copy(
            main = swipeDownNoWidget.main.copy(widgetId = 42),
        )
        assertTrue(swipeDownWithWidget.hasContent)

        // Never appears in compact stack, even when idle or busy
        val idle = builtInIslandSlots(false, false, null, emptyList(), custom = swipeDownWithWidget)
        assertTrue(idle.ordered.isEmpty())
        val busy = builtInIslandSlots(true, false, null, emptyList(), custom = swipeDownWithWidget)
        assertEquals(listOf(IslandSlot(IslandType.MEDIA)), busy.ordered.map { it.content })

        // Excluded from compact island priority ordering
        val priority = islandPriorityOrderWithCustom(emptyList(), listOf(customIsland, swipeDownWithWidget))
        assertTrue(priority.contains(IslandPriorityItem.Custom(customIsland.id)))
        assertFalse(priority.contains(IslandPriorityItem.Custom(swipeDownWithWidget.id)))

        // Can stay active alongside multiple regular custom islands
        val regular = customIsland.copy(left = customIsland.left.copy(icon = CustomIslandIcon.HOME))
        val second = customIsland.copy(
            id = "transit",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.NOTIFICATION,
            left = customIsland.left.copy(icon = CustomIslandIcon.NAVIGATION),
        )
        val settings = CustomIslandSettings(
            islands = listOf(regular, second, swipeDownWithWidget),
            activeIds = setOf(regular.id, second.id),
            swipeDownId = swipeDownWithWidget.id,
        )
        assertEquals(listOf(regular, second), settings.activeIslands)
        assertTrue(settings.isActive(regular.id))
        assertTrue(settings.isActive(second.id))
        assertEquals(regular, settings.activeIsland)
        assertEquals(swipeDownWithWidget, settings.swipeDownIsland)
        assertTrue(settings.canUseSwipeDownVisibility(swipeDownWithWidget.id))
        assertFalse(settings.canUseSwipeDownVisibility(regular.id))

        // Multiple active custom islands stack by priority with distinct IslandSlot IDs
        val alwaysOne = regular.copy(visibility = CustomIslandVisibility.ALWAYS)
        val multiStack = builtInIslandSlots(
            music = true,
            flashlight = false,
            activity = null,
            shortTerms = emptyList(),
            priorityOrder = listOf(
                IslandPriorityItem.Custom(second.id),
                IslandPriorityItem.MEDIA,
                IslandPriorityItem.Custom(alwaysOne.id),
            ),
            customIslands = listOf(alwaysOne, second),
            matchingNotificationIds = setOf(second.id),
        )
        assertEquals(
            listOf(
                IslandSlot(IslandType.CUSTOM, customIslandId = second.id),
                IslandSlot(IslandType.MEDIA),
                IslandSlot(IslandType.CUSTOM, customIslandId = alwaysOne.id),
            ),
            multiStack.ordered.map { it.content },
        )

        val duplicateSwipeDown = customIsland.copy(id = "swipe2", visibility = CustomIslandVisibility.SWIPE_DOWN)
        val normalized = enforceSingleSwipeDownIsland(
            listOf(swipeDownWithWidget, duplicateSwipeDown),
            preferredId = duplicateSwipeDown.id,
        )
        assertEquals(CustomIslandVisibility.IDLE, normalized[0].visibility)
        assertEquals(CustomIslandVisibility.SWIPE_DOWN, normalized[1].visibility)
    }

}
