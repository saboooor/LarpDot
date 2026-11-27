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
}
