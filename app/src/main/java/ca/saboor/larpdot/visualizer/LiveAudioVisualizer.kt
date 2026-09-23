package ca.saboor.larpdot.visualizer

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import ca.saboor.larpdot.service.AudioCaptureService
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max

/**
 * Real-time hardware Audio Visualizer manager.
 * Supports:
 * 1. AudioPlaybackCaptureConfiguration via MediaProjection (Screen/Audio Capture - Android 10+ standard)
 *    which captures clean internal digital audio from Spotify, Apple Music, YouTube Music, etc.
 * 2. AudioSession 0 legacy Visualizer fallback for supported devices.
 */
object LiveAudioVisualizer {

    private const val TAG = "LiveAudioVisualizer"

    private val _liveAmplitudes = MutableStateFlow<FloatArray?>(null)
    val liveAmplitudes: StateFlow<FloatArray?> = _liveAmplitudes.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var legacyVisualizer: Visualizer? = null
    private var bandPeaks = FloatArray(32) { 12.0f }

    fun updateAmplitudes(amplitudes: FloatArray) {
        _liveAmplitudes.value = amplitudes
    }

    fun reportCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
        if (!capturing) {
            _liveAmplitudes.value = null
        }
    }

    fun reportError(error: String?) {
        _errorMessage.value = error
    }

    fun stop(context: Context) {
        // Stop foreground service
        AudioCaptureService.stop(context)

        // Stop legacy visualizer if running
        try {
            legacyVisualizer?.enabled = false
            legacyVisualizer?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing legacy visualizer: ${e.message}")
        } finally {
            legacyVisualizer = null
        }

        _isCapturing.value = false
        _liveAmplitudes.value = null
    }

    /**
     * Legacy Visualizer(0) fallback for older devices or ROMs that permit session 0 capture.
     */
    @Synchronized
    fun startLegacyFallback(context: Context, bandCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT) {
        val safeBands = bandCount.coerceIn(1, 32)
        if (_isCapturing.value) return

        try {
            val viz = Visualizer(0)
            viz.enabled = false
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            viz.captureSize = 512.coerceIn(captureSizeRange[0], captureSizeRange[1])
            val maxRate = Visualizer.getMaxCaptureRate()
            val targetRate = (maxRate / 2).coerceIn(10_000, 40_000)

            viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.isEmpty()) return
                    processLegacyFft(fft, samplingRate, safeBands)
                }
            }, targetRate, false, true)

            viz.enabled = true
            legacyVisualizer = viz
            _isCapturing.value = true
            _errorMessage.value = null
        } catch (e: Throwable) {
            Log.w(TAG, "Legacy visualizer initialization failed: ${e.message}")
            _errorMessage.value = "Device requires Screen/Audio capture method"
        }
    }

    private fun processLegacyFft(fft: ByteArray, samplingRateMilliHz: Int, numBands: Int) {
        val n = fft.size
        if (n < 4) return
        val rateHz = if (samplingRateMilliHz > 1000) samplingRateMilliHz / 1000f else 44100f
        val binWidth = rateHz / n

        if (bandPeaks.size < numBands) {
            bandPeaks = FloatArray(numBands) { 12.0f }
        }

        val cutoffs = when (numBands) {
            3 -> doubleArrayOf(280.0, 3200.0)
            4 -> doubleArrayOf(220.0, 1000.0, 4200.0)
            5 -> doubleArrayOf(180.0, 600.0, 2400.0, 7000.0)
            6 -> doubleArrayOf(160.0, 450.0, 1400.0, 3800.0, 8500.0)
            7 -> doubleArrayOf(160.0, 450.0, 1200.0, 3000.0, 6500.0, 12000.0)
            else -> {
                val count = (numBands - 1).coerceAtLeast(1)
                val minFreq = 140.0
                val maxFreq = 14000.0
                val logMin = kotlin.math.ln(minFreq)
                val logMax = kotlin.math.ln(maxFreq)
                val step = (logMax - logMin) / numBands
                DoubleArray(count) { idx ->
                    kotlin.math.exp(logMin + (idx + 1) * step)
                }
            }
        }

        val bandSums = FloatArray(numBands)
        val bandCounts = IntArray(numBands)
        val numBins = n / 2

        for (k in 1 until numBins) {
            val r = fft[2 * k].toFloat()
            val i = fft[2 * k + 1].toFloat()
            val mag = hypot(r, i)
            val freq = k * binWidth

            var targetBand = numBands - 1
            for (b in 0 until cutoffs.size) {
                if (freq < cutoffs[b]) {
                    targetBand = b
                    break
                }
            }
            bandSums[targetBand] += mag
            bandCounts[targetBand]++
        }

        val rawAmps = FloatArray(numBands)
        for (b in 0 until numBands) {
            val count = bandCounts[b].coerceAtLeast(1)
            val avgMag = bandSums[b] / count
            bandPeaks[b] = max(bandPeaks[b] * 0.992f, max(avgMag, 8.0f))
            rawAmps[b] = (avgMag / bandPeaks[b]).coerceIn(0.04f, 1.0f)
        }
        _liveAmplitudes.value = rawAmps
    }
}
