package ca.saboor.larpdot.visualizer

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Shared crossover cutoff frequencies (Hz) for splitting an audio signal into [numBands] acoustic bands.
 * Returns `numBands - 1` cutoff frequencies (empty for 1 band).
 */
internal fun crossoverCutoffs(numBands: Int): DoubleArray {
    val safeBands = numBands.coerceIn(1, 32)
    val numCutoffs = safeBands - 1
    if (numCutoffs <= 0) return DoubleArray(0)
    return when (safeBands) {
        2 -> doubleArrayOf(1000.0)
        3 -> doubleArrayOf(280.0, 3200.0)
        4 -> doubleArrayOf(220.0, 1000.0, 4200.0)
        5 -> doubleArrayOf(180.0, 600.0, 2400.0, 7000.0)
        6 -> doubleArrayOf(160.0, 450.0, 1400.0, 3800.0, 8500.0)
        7 -> doubleArrayOf(160.0, 450.0, 1200.0, 3000.0, 6500.0, 12000.0)
        else -> {
            val minFreq = 140.0
            val maxFreq = 14000.0
            val logMin = ln(minFreq)
            val logMax = ln(maxFreq)
            val step = (logMax - logMin) / safeBands
            DoubleArray(numCutoffs) { idx -> exp(logMin + (idx + 1) * step) }
        }
    }
}

/**
 * 2nd-order cascaded IIR crossover filterbank shared by real-time device audio capture
 * and offline 30s preview decoding. Handles 1..32 bands safely with zero per-sample allocations.
 */
internal class CrossoverFilterBank(
    numBands: Int,
    sampleRateHz: Int,
) {
    val bandCount: Int = numBands.coerceIn(1, 32)
    private val cutoffs: DoubleArray = crossoverCutoffs(bandCount)
    private var alphas: DoubleArray = computeAlphas(sampleRateHz)
    private val lp1 = DoubleArray(cutoffs.size)
    private val lp2 = DoubleArray(cutoffs.size)
    private val sumSq = DoubleArray(bandCount)

    fun updateSampleRate(sampleRateHz: Int) {
        alphas = computeAlphas(sampleRateHz)
    }

    fun resetAccumulators() {
        sumSq.fill(0.0)
    }

    fun processSample(sample: Double) {
        val numCutoffs = cutoffs.size
        if (numCutoffs == 0) {
            sumSq[0] += sample * sample
            return
        }
        for (c in 0 until numCutoffs) {
            val a = alphas[c]
            lp1[c] += a * (sample - lp1[c])
            lp2[c] += a * (lp1[c] - lp2[c])
        }
        val b0 = lp2[0]
        sumSq[0] += b0 * b0
        for (c in 1 until numCutoffs) {
            val bandSample = lp2[c] - lp2[c - 1]
            sumSq[c] += bandSample * bandSample
        }
        val bLast = sample - lp2[numCutoffs - 1]
        sumSq[bandCount - 1] += bLast * bLast
    }

    fun bandRms(bandIndex: Int, sampleCount: Int): Float {
        if (sampleCount <= 0) return 0f
        return sqrt(sumSq[bandIndex] / sampleCount.toDouble()).toFloat()
    }

    private fun computeAlphas(sampleRateHz: Int): DoubleArray {
        val safeRate = sampleRateHz.coerceAtLeast(8000).toDouble()
        return DoubleArray(cutoffs.size) { idx ->
            1.0 - exp(-2.0 * PI * cutoffs[idx] / safeRate)
        }
    }
}
