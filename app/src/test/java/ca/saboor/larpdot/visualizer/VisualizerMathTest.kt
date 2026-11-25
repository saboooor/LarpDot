package ca.saboor.larpdot.visualizer

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerMathTest {

    @Test
    fun crossoverCutoffsAreStrictlyIncreasingForEverySupportedBandCount() {
        assertEquals(0, crossoverCutoffs(1).size)
        for (bands in 2..16) {
            val cutoffs = crossoverCutoffs(bands)
            assertEquals(bands - 1, cutoffs.size)
            for (i in 1 until cutoffs.size) {
                assertTrue(
                    "Cutoffs for $bands bands must increase strictly",
                    cutoffs[i] > cutoffs[i - 1],
                )
            }
        }
    }

    @Test
    fun crossoverFilterBankSeparatesBassAndTrebleSineWaves() {
        val sampleRate = 44_100
        val sampleCount = 4_410 // 100 ms

        val bassBank = CrossoverFilterBank(numBands = 5, sampleRateHz = sampleRate)
        for (i in 0 until sampleCount) {
            val sample = sin(2.0 * PI * 80.0 * i / sampleRate)
            bassBank.processSample(sample)
        }
        val bassLow = bassBank.bandRms(0, sampleCount)
        val bassHigh = bassBank.bandRms(4, sampleCount)
        assertTrue("80 Hz sine must concentrate in lowest band (low=$bassLow, high=$bassHigh)", bassLow > bassHigh * 5f)

        val trebleBank = CrossoverFilterBank(numBands = 5, sampleRateHz = sampleRate)
        for (i in 0 until sampleCount) {
            val sample = sin(2.0 * PI * 10_000.0 * i / sampleRate)
            trebleBank.processSample(sample)
        }
        val trebleLow = trebleBank.bandRms(0, sampleCount)
        val trebleHigh = trebleBank.bandRms(4, sampleCount)
        assertTrue("10 kHz sine must concentrate in highest band (low=$trebleLow, high=$trebleHigh)", trebleHigh > trebleLow * 5f)
    }

    @Test
    fun acousticBandWeightsAndBallisticsBehavePredictably() {
        for (count in listOf(1, 3, 5, 7, 12)) {
            val weights = acousticBandWeights(count)
            assertEquals(count, weights.size)
            assertTrue(weights.all { it in 0.4f..1.0f })
        }

        // Attack moves rapidly upward; treble decays faster than bass
        val attacked = stepBandBallistics(prevHeight = 0.1f, targetHeight = 1.0f, normalizedBandPos = 0.5f)
        assertTrue(attacked > 0.8f)

        val bassDecay = stepBandBallistics(prevHeight = 1.0f, targetHeight = 0.0f, normalizedBandPos = 0.0f)
        val trebleDecay = stepBandBallistics(prevHeight = 1.0f, targetHeight = 0.0f, normalizedBandPos = 1.0f)
        assertTrue("Treble should decay faster than bass (bass=$bassDecay, treble=$trebleDecay)", trebleDecay < bassDecay)
    }
}
