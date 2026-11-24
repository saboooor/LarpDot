package ca.saboor.larpdot.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

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

    @Test
    fun crossoverFilterBankHandlesSingleBandWithoutOutOfBounds() {
        val bank = CrossoverFilterBank(numBands = 1, sampleRateHz = 44100)
        assertEquals(1, bank.bandCount)
        assertEquals(0, crossoverCutoffs(1).size)
        val samples = 1024
        for (i in 0 until samples) {
            bank.processSample(sin(2.0 * PI * 440.0 * i / 44100.0))
        }
        val rms = bank.bandRms(0, samples)
        assertEquals(0.707f, rms, 0.03f)
    }

    @Test
    fun crossoverFilterBankSeparatesBassAndTrebleTones() {
        val sampleRate = 44100
        val bassBank = CrossoverFilterBank(numBands = 5, sampleRateHz = sampleRate)
        val trebleBank = CrossoverFilterBank(numBands = 5, sampleRateHz = sampleRate)
        val samples = 2048
        for (i in 0 until samples) {
            bassBank.processSample(sin(2.0 * PI * 80.0 * i / sampleRate))
            trebleBank.processSample(sin(2.0 * PI * 10000.0 * i / sampleRate))
        }
        assertTrue("80Hz tone should dominate low band", bassBank.bandRms(0, samples) > bassBank.bandRms(4, samples) * 3f)
        assertTrue("10kHz tone should dominate high band", trebleBank.bandRms(4, samples) > trebleBank.bandRms(0, samples) * 3f)

        val dynamicCutoffs = crossoverCutoffs(12)
        assertEquals(11, dynamicCutoffs.size)
        for (i in 1 until dynamicCutoffs.size) {
            assertTrue("Cutoffs should be strictly increasing", dynamicCutoffs[i] > dynamicCutoffs[i - 1])
        }
    }
}
