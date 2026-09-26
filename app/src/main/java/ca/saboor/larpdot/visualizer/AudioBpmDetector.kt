package ca.saboor.larpdot.visualizer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

/**
 * Fallback audio-based BPM detector that accurately measures true song tempo
 * directly from 30-second audio previews (iTunes / Deezer) using spectral energy
 * onset envelope extraction and autocorrelation.
 *
 * Runs strictly offline on background threads when metadata APIs return 0 or have no match.
 */
object AudioBpmDetector {

    private const val TAG = "AudioBpmDetector"
    private const val TARGET_FPS = 100 // 100 frames per second = 10ms resolution

    private fun downloadPreview(urlString: String, destination: File): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LarpDot/1.0 (Android)")
            connection.instanceFollowRedirects = true

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            connection.disconnect()
            destination.exists() && destination.length() > 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download preview: ${e.message}")
            false
        }
    }

    fun analyzeBpmFromFile(audioFile: File): Float? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            extractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) return null
            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isCodecEOS = false

            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            var samplesPerFrame = (sampleRate / TARGET_FPS).coerceAtLeast(1)
            val envelope = ArrayList<Float>(3500)
            var frameSampleSumSq = 0.0
            var frameSampleCount = 0
            val timeoutUs = 5000L

            while (!isCodecEOS) {
                if (!isExtractorEOS) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isCodecEOS = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.asShortBuffer()

                        while (shortBuffer.hasRemaining()) {
                            val sampleL = shortBuffer.get().toInt()
                            val sampleR = if (channelCount >= 2 && shortBuffer.hasRemaining()) {
                                shortBuffer.get().toInt()
                            } else sampleL
                            val mono = (sampleL + sampleR) / 2.0 / 32768.0
                            frameSampleSumSq += mono * mono
                            frameSampleCount++

                            if (frameSampleCount >= samplesPerFrame) {
                                val rms = sqrt(frameSampleSumSq / frameSampleCount).toFloat()
                                envelope.add(rms)
                                frameSampleSumSq = 0.0
                                frameSampleCount = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    samplesPerFrame = (sampleRate / TARGET_FPS).coerceAtLeast(1)
                }
            }

            if (envelope.size < TARGET_FPS * 5) return null // Need at least 5 seconds of audio

            computeBpmFromEnvelope(envelope, TARGET_FPS.toFloat())

        } catch (e: Exception) {
            Log.w(TAG, "Decode and analyze failed: ${e.message}")
            null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Computes the tempo (BPM) from an energy envelope using half-wave rectified onset
     * difference and autocorrelation over the musical tempo range 70..215 BPM.
     */
    fun computeBpmFromEnvelope(envelope: List<Float>, fps: Float): Float? {
        val n = envelope.size
        if (n < 100) return null

        // 1. Positive onset differences (half-wave rectified)
        val diff = FloatArray(n - 1)
        var meanDiff = 0.0
        for (i in 0 until n - 1) {
            val delta = envelope[i + 1] - envelope[i]
            val onset = if (delta > 0f) delta else 0f
            diff[i] = onset
            meanDiff += onset
        }
        meanDiff /= diff.size

        // Zero-center the onsets
        for (i in diff.indices) {
            diff[i] -= meanDiff.toFloat()
        }

        // 2. Autocorrelation over lags for 70..215 BPM
        val minBpm = 70f
        val maxBpm = 215f
        val minLag = (fps * 60f / maxBpm).toInt().coerceAtLeast(1)
        val maxLag = (fps * 60f / minBpm).toInt().coerceAtMost(diff.size - 1)

        if (minLag >= maxLag) return null

        var bestLag = minLag
        var maxCorr = Float.NEGATIVE_INFINITY
        val corrTable = FloatArray(maxLag + 1)

        val diffSize = diff.size
        for (lag in minLag..maxLag) {
            var sum = 0.0f
            var count = 0
            for (i in lag until diffSize) {
                sum += diff[i] * diff[i - lag]
                count++
            }
            val normCorr = if (count > 0) sum / count else 0f
            corrTable[lag] = normCorr
            if (normCorr > maxCorr) {
                maxCorr = normCorr
                bestLag = lag
            }
        }

        if (maxCorr <= 0f) return null

        // Sub-harmonic disambiguation: check if half-lag (double tempo) is a valid musical beat
        val halfLag = bestLag / 2
        if (halfLag >= minLag && corrTable[halfLag] > maxCorr * 0.70f) {
            val halfBpm = (fps * 60f) / halfLag
            if (halfBpm in 90f..180f) {
                bestLag = halfLag
            }
        }

        val detectedBpm = (fps * 60f) / bestLag
        return if (detectedBpm in 60f..240f) detectedBpm else null
    }
}
