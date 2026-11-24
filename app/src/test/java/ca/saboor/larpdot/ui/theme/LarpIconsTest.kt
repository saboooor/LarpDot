package ca.saboor.larpdot.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertTrue
import org.junit.Test

class LarpIconsTest {
    @Test fun allMaterialSymbolsParseCleanly() {
        val getters = LarpIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == ImageVector::class.java }
        assertTrue("Expected at least 85 icons, found ${getters.size}", getters.size >= 85)
        for (getter in getters) {
            val vector = getter.invoke(LarpIcons) as ImageVector
            assertTrue("${getter.name} should have viewport 960", vector.viewportWidth == 960f && vector.viewportHeight == 960f)
            assertTrue("${getter.name} should have non-empty root group", vector.root.size > 0)
        }
    }
}
