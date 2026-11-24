package ca.saboor.larpdot.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerMathTest {

    @Test
    fun acousticBandWeightsReturnsExpectedPresetsAndBoundedDynamicSizes() {
        assertEquals(5, acousticBandWeights(5).size)
        assertEquals(1.0f, acousticBandWeights(5)[0], 0.0001f)
        assertEquals(7, acousticBandWeights(7).size)

        val dynamic = acousticBandWeights(16)
        assertEquals(16, dynamic.size)
        assertTrue(dynamic.all { it in 0.44f..1.0f })

        val single = acousticBandWeights(0)
        assertEquals(1, single.size)
        assertTrue(single[0] in 0.44f..1.0f)
    }

    @Test
    fun stepBandBallisticsAttacksFasterThanDecayAndTrebleFasterThanBass() {
        val bassAttack = stepBandBallistics(prevHeight = 0.2f, targetHeight = 1.0f, normalizedBandPos = 0f)
        val bassDecay = stepBandBallistics(prevHeight = 1.0f, targetHeight = 0.2f, normalizedBandPos = 0f)
        val trebleDecay = stepBandBallistics(prevHeight = 1.0f, targetHeight = 0.2f, normalizedBandPos = 1f)

        val attackDelta = bassAttack - 0.2f
        val bassDecayDelta = 1.0f - bassDecay
        val trebleDecayDelta = 1.0f - trebleDecay

        assertTrue("Attack should move faster than decay", attackDelta > bassDecayDelta)
        assertTrue("Treble should decay faster than bass", trebleDecayDelta > bassDecayDelta)
        assertEquals(0.2f + 0.8f * 0.80f, bassAttack, 0.0001f)
    }
}
