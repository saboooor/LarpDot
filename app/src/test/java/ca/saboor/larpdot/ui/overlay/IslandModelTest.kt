package ca.saboor.larpdot.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandModelTest {
    @Test
    fun everyProviderUsesTheSameTapAndHoldPreference() {
        for (type in IslandType.entries) {
            val actions = mutableListOf<String>()
            val model = IslandModel(type, { actions.add("activate") }, { actions.add("hold") })
            model.tap(true) { actions.add("expand") }
            model.hold(true) { actions.add("expand") }
            model.tap(false) { actions.add("expand") }
            model.hold(false) { actions.add("expand") }
            assertEquals(listOf("expand", "hold", "activate", "expand"), actions)
        }
    }
}
