package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.NotificationActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIslandPresetStoreTest {

    private fun richTransitIsland(): CustomIsland = CustomIsland(
        id = "orig-island-id",
        name = "Transit Live",
        left = CustomIslandSlot(
            image = CustomIslandImage.NOTIFICATION_ICON,
            iconColor = CustomIslandColor(mode = CustomColorMode.DOMINANT, rgb = 0x22AA55),
            width = 64,
            height = 24,
        ),
        right = CustomIslandSlot(
            text = "{title}",
            textStyle = CustomTextStyle(
                size = 13,
                rgb = 0x4CAF50,
                bold = true,
                alignment = CustomTextAlignment.END,
                colorMode = CustomColorMode.MATERIAL_YOU,
            ),
            width = 72,
            height = 24,
        ),
        main = CustomIslandSlot(width = 320, height = 160, paddingX = 12, paddingY = 10, spanFullIsland = true),
        expandedLeft = CustomIslandSlot(
            image = CustomIslandImage.NOTIFICATION_ICON,
            iconColor = CustomIslandColor(mode = CustomColorMode.CUSTOM, rgb = 0xFF7043),
            width = 44,
            height = 44,
        ),
        expandedRight = CustomIslandSlot(
            icon = CustomIslandIcon.NAVIGATION,
            iconColor = CustomIslandColor(mode = CustomColorMode.MATERIAL_YOU),
            width = 44,
            height = 44,
        ),
        visibility = CustomIslandVisibility.CONDITIONAL,
        condition = CustomIslandCondition.NOTIFICATION,
        activityKind = NotificationActivityKind.NAVIGATION,
        notificationRule = CustomNotificationRule(
            packageName = "com.thetransitapp.droid",
            channelId = "go_channel",
            titleContains = "Get off in * stops",
            textContains = "/\\d+ min/",
            matchChannel = true,
            minimizeNotification = true,
        ),
        components = listOf(
            CustomIslandComponent(
                id = "c1",
                kind = CustomComponentKind.TEXT,
                text = "{title}",
                textStyle = CustomTextStyle(size = 16, rgb = 0xFFFFFF, bold = true),
            ),
            CustomIslandComponent(
                id = "c2",
                kind = CustomComponentKind.NOTIFICATION_CONTENT,
                height = 120,
            ),
            CustomIslandComponent(
                id = "c3",
                kind = CustomComponentKind.BUTTONS,
                buttonStyle = OverlayPreferences.PlaybackControlsStyle(
                    playPauseBackground = false,
                    skipBackground = true,
                    cornerRadiusDp = 16f,
                    material3ButtonGroup = true,
                ),
            ),
        ),
        tapAction = CustomIslandTapAction.OPEN_NOTIFICATION,
        notificationActionTitle = "Stop GO",
        tapAppPackage = "com.thetransitapp.droid",
    )

    @Test
    fun shareJsonAndShareCodeRoundTripFullCustomIslandAndGenerateFreshIds() {
        val original = CustomIslandPreset(
            id = "preset-1",
            name = "Transit Tracker",
            description = "Shows bus stops and live notification card",
            island = richTransitIsland(),
        )

        val json = CustomIslandPresetCodec.toShareJson(original)
        val fromJson = CustomIslandPresetCodec.fromShareInput(json)
        assertNotNull(fromJson)
        assertEquals(0, fromJson!!.omittedWidgets)
        assertEquals("Transit Tracker", fromJson.preset.name)
        assertEquals("Shows bus stops and live notification card", fromJson.preset.description)
        assertNotEquals(original.id, fromJson.preset.id)
        assertNotEquals(original.island.id, fromJson.preset.island.id)
        assertEquals(original.island.notificationRule, fromJson.preset.island.notificationRule)
        assertEquals(original.island.left, fromJson.preset.island.left)
        assertEquals(original.island.right, fromJson.preset.island.right)
        assertEquals(original.island.expandedLeft, fromJson.preset.island.expandedLeft)
        assertEquals(original.island.expandedRight, fromJson.preset.island.expandedRight)
        assertEquals(original.island.main, fromJson.preset.island.main)
        assertEquals(original.island.tapAction, fromJson.preset.island.tapAction)
        assertEquals(3, fromJson.preset.island.components.size)
        assertEquals(CustomComponentKind.NOTIFICATION_CONTENT, fromJson.preset.island.components[1].kind)
        assertNotEquals("c1", fromJson.preset.island.components[0].id)

        // Share code (even wrapped inside a chat message) decodes identically
        val shareCode = CustomIslandPresetCodec.toShareCode(original)
        assertTrue(shareCode.startsWith("LARPDOT1:"))
        val wrapped = "Check out my Transit preset!\n$shareCode\nLet me know what you think"
        val fromCode = CustomIslandPresetCodec.fromShareInput(wrapped)
        assertNotNull(fromCode)
        assertEquals("Transit Tracker", fromCode!!.preset.name)
        assertEquals(original.island.notificationRule, fromCode.preset.island.notificationRule)
        assertEquals(original.island.right.text, fromCode.preset.island.right.text)
        assertEquals(original.island.right.textStyle, fromCode.preset.island.right.textStyle)
    }

    @Test
    fun notificationConditionListRoundTripsThroughPresetShareCode() {
        val multiConditionIsland = richTransitIsland().copy(
            notificationRule = CustomNotificationRule(
                packageName = "com.thetransitapp.droid",
                channelId = "go_channel",
                matchChannel = true,
                conditions = listOf(
                    CustomNotificationCondition(CustomNotificationField.TITLE, CustomNotificationOperator.CONTAINS, "Get off in * stops", CustomConditionJoin.AND),
                    CustomNotificationCondition(CustomNotificationField.TITLE, CustomNotificationOperator.CONTAINS, "Walk * min", CustomConditionJoin.OR),
                    CustomNotificationCondition(CustomNotificationField.TEXT, CustomNotificationOperator.NOT_CONTAINS, "Cancelled", CustomConditionJoin.AND),
                ),
            ),
        )
        val preset = CustomIslandPreset(name = "Transit Multi", island = multiConditionIsland)
        val decoded = CustomIslandPresetCodec.fromShareInput(CustomIslandPresetCodec.toShareCode(preset))
        assertNotNull(decoded)
        assertEquals(multiConditionIsland.notificationRule, decoded!!.preset.island.notificationRule)
    }

    @Test
    fun deviceLocalWidgetsAreStrippedAndReportedInOmittedCount() {
        val withWidgets = richTransitIsland().copy(
            left = CustomIslandSlot(widgetId = 42, width = 80, height = 24, colorKey = WidgetColorKey(0x112233, 15)),
            components = listOf(
                CustomIslandComponent(id = "w1", kind = CustomComponentKind.WIDGET, widgetId = 99, height = 140),
                CustomIslandComponent(id = "t1", kind = CustomComponentKind.TEXT, text = "Hello"),
            ),
        )
        val preset = CustomIslandPreset(name = "Mixed", island = withWidgets)
        val imported = CustomIslandPresetCodec.fromShareInput(CustomIslandPresetCodec.toShareCode(preset))!!
        assertEquals(2, imported.omittedWidgets)
        assertNull(imported.preset.island.left.widgetId)
        assertNull(imported.preset.island.left.colorKey)
        assertEquals(1, imported.preset.island.components.size)
        assertEquals(CustomComponentKind.TEXT, imported.preset.island.components[0].kind)
    }

    @Test
    fun invalidInputsReturnNullAndSwipeDownNormalizesWhenOneAlreadyExists() {
        assertNull(CustomIslandPresetCodec.fromShareInput(""))
        assertNull(CustomIslandPresetCodec.fromShareInput("not a preset"))
        assertNull(CustomIslandPresetCodec.fromShareInput("LARPDOT1:!!!invalid!!!"))
        assertNull(CustomIslandPresetCodec.fromShareInput("""{"type":"other","version":1,"island":{}}"""))

        val swipePreset = CustomIslandPreset(
            name = "Quick Panel",
            island = richTransitIsland().copy(visibility = CustomIslandVisibility.SWIPE_DOWN),
        )
        val existingSwipeDown = CustomIsland(id = "existing", visibility = CustomIslandVisibility.SWIPE_DOWN)
        val instantiatedWhenOccupied = swipePreset.instantiateForStore(listOf(existingSwipeDown))
        assertEquals(CustomIslandVisibility.IDLE, instantiatedWhenOccupied.visibility)

        val instantiatedWhenFree = swipePreset.instantiateForStore(emptyList())
        assertEquals(CustomIslandVisibility.SWIPE_DOWN, instantiatedWhenFree.visibility)
    }

    @Test
    fun presetListSerializationRoundTripsCleanly() {
        val list = listOf(
            CustomIslandPreset(id = "p1", name = "Transit", description = "Bus tracker", island = richTransitIsland(), createdAt = 100L),
            CustomIslandPreset(id = "p2", name = "Music", description = "", island = CustomIsland(id = "m", visibility = CustomIslandVisibility.ALWAYS), createdAt = 200L),
        )
        val encoded = CustomIslandPresetCodec.encodePresetList(list)
        val decoded = CustomIslandPresetCodec.decodePresetList(encoded)
        assertEquals(2, decoded.size)
        assertEquals("p1", decoded[0].id)
        assertEquals("Transit", decoded[0].name)
        assertEquals("Bus tracker", decoded[0].description)
        assertEquals("p2", decoded[1].id)
    }

    @Test
    fun islandsGroupByAppAlphabeticallyWithGeneralLastAndSwapWithinGroup() {
        val transit1 = richTransitIsland().copy(id = "t1", name = "Transit 1")
        val general = CustomIsland(id = "g1", name = "General Idle", visibility = CustomIslandVisibility.IDLE)
        val discord = CustomIsland(
            id = "d1",
            name = "Discord Call",
            tapAppPackage = "com.discord",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.LIVE_ACTIVITY,
        )
        val widgetIsland = CustomIsland(
            id = "w1",
            name = "Weather",
            left = CustomIslandSlot(widgetId = 7, width = 64, height = 24),
        )
        val transit2 = richTransitIsland().copy(id = "t2", name = "Transit 2")

        assertEquals("com.thetransitapp.droid", transit1.associatedAppPackage())
        assertEquals("com.discord", discord.associatedAppPackage())
        assertEquals("com.google.weather", widgetIsland.associatedAppPackage { id -> if (id == 7) "com.google.weather" else null })
        assertNull(general.associatedAppPackage())

        val names = mapOf(
            "com.thetransitapp.droid" to "Transit",
            "com.discord" to "Discord",
            "com.google.weather" to "Pixel Weather",
        )
        val all = listOf(transit1, general, discord, widgetIsland, transit2)
        val groups = groupByApp(
            items = all,
            packageOf = { it.associatedAppPackage { id -> if (id == 7) "com.google.weather" else null } },
            resolveAppName = { names[it] ?: it },
        )

        assertEquals(listOf("Discord", "Pixel Weather", "Transit", "General"), groups.map { it.appName })
        assertEquals(listOf("t1", "t2"), groups[2].items.map { it.id })
        assertEquals(listOf("g1"), groups[3].items.map { it.id })

        val swapped = swapCustomIslands(all, "t1", "t2")
        assertEquals(listOf("t2", "g1", "d1", "w1", "t1"), swapped.map { it.id })
    }

    @Test
    fun appBundleShareCodeAndJsonRoundTripMultipleIslands() {
        val first = CustomIslandPreset(
            id = "p-1",
            name = "Stops Remaining",
            description = "Bus countdown",
            island = richTransitIsland(),
        )
        val second = CustomIslandPreset(
            id = "p-2",
            name = "Walk To Stop",
            island = richTransitIsland().copy(
                id = "orig-2",
                left = CustomIslandSlot(widgetId = 55, width = 64, height = 24),
                notificationRule = CustomNotificationRule(
                    packageName = "com.thetransitapp.droid",
                    titleContains = "Walk * min",
                ),
            ),
        )
        val bundle = CustomIslandPresetBundle(
            name = "Transit Pack",
            description = "All Transit live islands",
            appPackage = "com.thetransitapp.droid",
            appName = "Transit",
            presets = listOf(first, second),
        )

        val code = CustomIslandPresetCodec.toBundleShareCode(bundle)
        assertTrue(code.startsWith("LARPDOT1:"))
        val importedFromCode = CustomIslandPresetCodec.fromAnyShareInput("Grab this bundle:\n$code")
        assertNotNull(importedFromCode)
        assertTrue(importedFromCode!!.isBundle)
        assertEquals("Transit Pack", importedFromCode.bundleName)
        assertEquals("All Transit live islands", importedFromCode.description)
        assertEquals("com.thetransitapp.droid", importedFromCode.appPackage)
        assertEquals("Transit", importedFromCode.appName)
        assertEquals(1, importedFromCode.omittedWidgets)
        assertEquals(2, importedFromCode.presets.size)
        assertEquals("Stops Remaining", importedFromCode.presets[0].name)
        assertEquals("Walk To Stop", importedFromCode.presets[1].name)
        assertNull(importedFromCode.presets[1].island.left.widgetId)
        assertNotEquals(first.id, importedFromCode.presets[0].id)

        val json = CustomIslandPresetCodec.toBundleShareJson(bundle)
        val importedFromJson = CustomIslandPresetCodec.fromAnyShareInput(json)
        assertNotNull(importedFromJson)
        assertTrue(importedFromJson!!.isBundle)
        assertEquals(2, importedFromJson.presets.size)

        // Single preset still decodes with isBundle = false through unified parser
        val singleResult = CustomIslandPresetCodec.fromAnyShareInput(CustomIslandPresetCodec.toShareCode(first))
        assertNotNull(singleResult)
        assertEquals(false, singleResult!!.isBundle)
        assertEquals(1, singleResult.presets.size)
    }

    @Test
    fun duplicateIslandCreatesFreshIdsStripsWidgetsAndConvertsSwipeDownToIdle() {
        val original = richTransitIsland().copy(
            id = "src-1",
            name = "Transit Alerts",
            visibility = CustomIslandVisibility.SWIPE_DOWN,
            left = CustomIslandSlot(widgetId = 99, width = 64, height = 24),
        )
        val duplicate = cloneCustomIslandForDuplicate(original, listOf(original))

        assertNotEquals(original.id, duplicate.id)
        assertEquals("Transit Alerts (Copy)", duplicate.name)
        assertEquals(CustomIslandVisibility.IDLE, duplicate.visibility)
        assertNull(duplicate.left.widgetId)
        assertEquals(original.components.size, duplicate.components.size)
        assertTrue(duplicate.components.none { copyComp -> original.components.any { it.id == copyComp.id } })
        assertEquals(original.notificationRule, duplicate.notificationRule)
    }

    @Test
    fun customIslandSettingsJsonCodecRoundTripsWidgetsAndConditions() {
        val withWidget = richTransitIsland().copy(
            id = "isl-widget",
            left = CustomIslandSlot(
                widgetId = 42,
                width = 72,
                height = 24,
                colorKey = WidgetColorKey(rgb = 0x112233, tolerancePercent = 18),
            ),
            components = richTransitIsland().components + CustomIslandComponent(
                id = "comp-widget",
                kind = CustomComponentKind.WIDGET,
                widgetId = 77,
                height = 140,
                colorKey = WidgetColorKey(rgb = 0x000000, tolerancePercent = 12),
            ),
        )
        val swipeDown = CustomIsland(
            id = "isl-swipe",
            name = "Quick Swipe",
            visibility = CustomIslandVisibility.SWIPE_DOWN,
            components = listOf(CustomIslandComponent(id = "c-text", kind = CustomComponentKind.TEXT, text = "Hello")),
        )
        val settings = CustomIslandSettings(
            islands = listOf(withWidget, swipeDown),
            activeIds = setOf("isl-widget"),
            swipeDownId = "isl-swipe",
            pending = PendingIslandWidget("isl-widget", CustomIslandPosition.RIGHT, 88),
        )

        val raw = CustomIslandJsonCodec.encodeSettings(settings).toString()
        val decoded = CustomIslandJsonCodec.decodeSettings(raw)

        assertEquals(2, decoded.islands.size)
        assertEquals(setOf("isl-widget"), decoded.activeIds)
        assertEquals("isl-swipe", decoded.swipeDownId)
        assertEquals(PendingIslandWidget("isl-widget", CustomIslandPosition.RIGHT, 88), decoded.pending)
        assertEquals(42, decoded.islands[0].left.widgetId)
        assertEquals(WidgetColorKey(0x112233, 18), decoded.islands[0].left.colorKey)
        assertEquals(77, decoded.islands[0].components.last().widgetId)
        assertEquals(withWidget.notificationRule, decoded.islands[0].notificationRule)
    }

    @Test
    fun customIslandBackgroundAndAllImageComponentsRoundTripThroughJsonAndShareCode() {
        for (imageKind in CustomIslandImage.entries) {
            val island = CustomIsland(
                id = "bg-${imageKind.name}",
                name = "Bg ${imageKind.label}",
                visibility = CustomIslandVisibility.CONDITIONAL,
                condition = CustomIslandCondition.NOTIFICATION,
                backgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
                backgroundImage = imageKind,
                components = listOf(
                    CustomIslandComponent(id = "c1", kind = CustomComponentKind.ALBUM_ART, height = 80),
                    CustomIslandComponent(id = "c2", kind = CustomComponentKind.NOTIFICATION_IMAGE, height = 100),
                    CustomIslandComponent(id = "c3", kind = CustomComponentKind.APP_ICON, height = 64),
                    CustomIslandComponent(id = "c4", kind = CustomComponentKind.NOTIFICATION_ICON, height = 48),
                ),
            )
            val settings = CustomIslandSettings(islands = listOf(island), activeIds = setOf(island.id))
            val decodedSettings = CustomIslandJsonCodec.decodeSettings(CustomIslandJsonCodec.encodeSettings(settings).toString())
            val restored = decodedSettings.islands.single()
            assertEquals(OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND, restored.backgroundStyle)
            assertEquals(imageKind, restored.backgroundImage)
            assertEquals(imageKind, restored.effectiveBackgroundImage)
            assertEquals(
                listOf(
                    CustomComponentKind.ALBUM_ART,
                    CustomComponentKind.NOTIFICATION_IMAGE,
                    CustomComponentKind.APP_ICON,
                    CustomComponentKind.NOTIFICATION_ICON,
                ),
                restored.components.map { it.kind },
            )

            val preset = CustomIslandPreset(name = island.name, island = island)
            val shareCode = CustomIslandPresetCodec.toShareCode(preset)
            val imported = CustomIslandPresetCodec.fromShareInput(shareCode)
            assertNotNull(imported)
            assertEquals(OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND, imported!!.preset.island.backgroundStyle)
            assertEquals(imageKind, imported.preset.island.backgroundImage)
            assertEquals(4, imported.preset.island.components.size)
        }

        val notifDefault = CustomIsland(
            id = "d1",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.NOTIFICATION,
        )
        assertEquals(CustomIslandImage.NOTIFICATION_IMAGE, notifDefault.effectiveBackgroundImage)

        val liveDefault = CustomIsland(
            id = "d2",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.LIVE_ACTIVITY,
        )
        assertEquals(CustomIslandImage.APP_ICON, liveDefault.effectiveBackgroundImage)

        val musicDefault = CustomIsland(
            id = "d3",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.MUSIC,
        )
        assertEquals(CustomIslandImage.ALBUM_ART, musicDefault.effectiveBackgroundImage)
    }
}
