package ca.saboor.larpdot.ui.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashlightSwipeTest {
    @Test
    fun nearMiddleAndFarDistancesScaleBothIntervalAndStepSize() {
        assertEquals(FlashlightSwipeRate(500L, 1), flashlightSwipeRate(0f, 24f, 120f))
        assertEquals(FlashlightSwipeRate(500L, 1), flashlightSwipeRate(24f, 24f, 120f))
        assertEquals(FlashlightSwipeRate(300L, 6), flashlightSwipeRate(72f, 24f, 120f))
        assertEquals(FlashlightSwipeRate(300L, 6), flashlightSwipeRate(-72f, 24f, 120f))
        assertEquals(FlashlightSwipeRate(100L, 10), flashlightSwipeRate(120f, 24f, 120f))
        assertEquals(FlashlightSwipeRate(100L, 10), flashlightSwipeRate(-1_000f, 24f, 120f))
    }
}
