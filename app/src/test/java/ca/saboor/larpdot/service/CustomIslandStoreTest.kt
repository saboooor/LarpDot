package ca.saboor.larpdot.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIslandStoreTest {

    private fun island(
        id: String,
        visibility: CustomIslandVisibility = CustomIslandVisibility.IDLE,
        mainWidgetId: Int? = null,
        leftIcon: CustomIslandIcon? = null,
        rightIcon: CustomIslandIcon? = null,
        spanFullIsland: Boolean = false,
    ): CustomIsland = CustomIsland(
        id = id,
        visibility = visibility,
        left = CustomIslandSlot(icon = leftIcon, width = 64, height = 24),
        right = CustomIslandSlot(icon = rightIcon, width = 64, height = 24),
        main = CustomIslandSlot(width = 300, height = 150, spanFullIsland = spanFullIsland),
        components = listOfNotNull(
            mainWidgetId?.let {
                CustomIslandComponent(
                    id = "w-$it",
                    kind = CustomComponentKind.WIDGET,
                    widgetId = it,
                    height = 150,
                )
            },
        ),
    )

    @Test
    fun reorderCustomIslandsMovesWithinBounds() {
        val list = listOf(island("a"), island("b"), island("c"))
        assertEquals(listOf("b", "a", "c"), reorderCustomIslands(list, "b", -1).map { it.id })
        assertEquals(listOf("a", "c", "b"), reorderCustomIslands(list, "b", 1).map { it.id })
        // Clamps at list edges without changing order
        assertEquals(list, reorderCustomIslands(list, "a", -1))
        assertEquals(list, reorderCustomIslands(list, "c", 1))
        // Unknown id or zero delta returns original list
        assertEquals(list, reorderCustomIslands(list, "missing", 1))
        assertEquals(list, reorderCustomIslands(list, "b", 0))
    }

    @Test
    fun enforceSingleSwipeDownIslandKeepsPreferredAndDemotesOthers() {
        val list = listOf(
            island("a", CustomIslandVisibility.SWIPE_DOWN, mainWidgetId = 1),
            island("b", CustomIslandVisibility.SWIPE_DOWN, mainWidgetId = 2),
            island("c", CustomIslandVisibility.ALWAYS, leftIcon = CustomIslandIcon.MUSIC),
        )
        val normalized = enforceSingleSwipeDownIsland(list, preferredId = "b")
        assertEquals(CustomIslandVisibility.IDLE, normalized[0].visibility)
        assertEquals(CustomIslandVisibility.SWIPE_DOWN, normalized[1].visibility)
        assertEquals(CustomIslandVisibility.ALWAYS, normalized[2].visibility)

        val settings = CustomIslandSettings(islands = normalized, swipeDownId = "b")
        assertTrue(settings.canUseSwipeDownVisibility("b"))
        assertFalse(settings.canUseSwipeDownVisibility("a"))
        assertEquals("b", settings.swipeDownIsland?.id)
    }

    @Test
    fun customIslandDisplayInfoDescribesWidgetsAndSlotsWithoutRequiringNames() {
        val widgetIsland = island(
            id = "w1",
            visibility = CustomIslandVisibility.SWIPE_DOWN,
            mainWidgetId = 42,
            spanFullIsland = true,
        )
        val info = customIslandDisplayInfo(widgetIsland) { widgetId ->
            if (widgetId == 42) CustomIslandWidgetLabels("Weather Forecast", "Weather") else null
        }
        assertEquals("Weather Forecast", info.title)
        assertTrue(info.subtitle.contains("Swipe down"))
        assertTrue(info.subtitle.contains("Weather"))
        assertTrue(info.subtitle.contains("300×150 dp"))
        assertTrue(info.subtitle.contains("Full island"))

        val iconIsland = island(
            id = "i1",
            visibility = CustomIslandVisibility.ALWAYS,
            leftIcon = CustomIslandIcon.TIMER,
            rightIcon = CustomIslandIcon.MUSIC,
        )
        val iconInfo = customIslandDisplayInfo(iconIsland) { null }
        assertEquals("Timer • Music", iconInfo.title)
        assertTrue(iconInfo.subtitle.contains("Left & Right icons"))
    }

    @Test
    fun expandedWingSlotsIntegrateWithSlotAccessorsAndExpandedContent() {
        assertTrue(CustomIslandPosition.EXPANDED_LEFT.isExpandedWing)
        assertTrue(CustomIslandPosition.EXPANDED_RIGHT.isExpandedWing)
        assertFalse(CustomIslandPosition.LEFT.isExpandedWing)
        assertFalse(CustomIslandPosition.RIGHT.isExpandedWing)
        assertFalse(CustomIslandPosition.MAIN.isExpandedWing)

        val empty = CustomIsland(id = "wings")
        assertFalse(empty.hasExpandedContent)
        assertFalse(empty.hasContent)

        val withLeftWing = empty.withSlot(
            CustomIslandPosition.EXPANDED_LEFT,
            CustomIslandSlot(icon = CustomIslandIcon.STAR, width = 48, height = 48),
        )
        assertEquals(CustomIslandIcon.STAR, withLeftWing.slot(CustomIslandPosition.EXPANDED_LEFT).icon)
        assertTrue(withLeftWing.hasExpandedContent)
        assertTrue(withLeftWing.hasContent)

        val withBothWings = withLeftWing.withSlot(
            CustomIslandPosition.EXPANDED_RIGHT,
            CustomIslandSlot(image = CustomIslandImage.ALBUM_ART, width = 48, height = 48),
        )
        assertEquals(CustomIslandImage.ALBUM_ART, withBothWings.slot(CustomIslandPosition.EXPANDED_RIGHT).image)
        assertTrue(withBothWings.hasExpandedContent)
    }

}
