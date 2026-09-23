package ca.saboor.larpdot.visualizer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.PowerManager
import android.util.Log
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Extraction status reporting for UI feedback and preview audio playback.
 */
sealed class ExtractionState {
    object Idle : ExtractionState()

    data class Processing(
        val trackTitle: String,
        val artist: String,
        val bandCount: Int,
        val sourceName: String = "iTunes",
    ) : ExtractionState()

    data class Success(
        val trackTitle: String,
        val artist: String,
        val previewUrl: String?,
        val audioFilePath: String?,
        val frameCount: Int,
        val fps: Int,
        val bandCount: Int,
        val sourceName: String = "iTunes",
    ) : ExtractionState()

    data class Failed(
        val trackTitle: String,
        val reason: String,
    ) : ExtractionState()
}

/**
 * Background Audio Feature Extractor for dynamic multi-band audio-reactive visuals.
 *
 * Capabilities:
 * - Dynamically processes however many frequency bands are enabled (3, 4, 5, 6, or 7 bands).
 * - Queries iTunes Search API for 30-second AAC/MP3 song previews.
 * - Extracts and decodes PCM audio buffers using MediaExtractor and MediaCodec offline.
 * - Blends interleaved stereo channels into mono to eliminate channel phase jitter.
 * - Dynamically computes 2nd-order cascaded IIR crossover filters for N-1 cutoffs to cleanly
 *   separate audio into exactly N frequency bands.
 * - Applies per-band peak normalization (AGC) so every enabled band dances vibrantly.
 * - Caches computed multi-band FloatArrays tagged by track key and band count.
 * - Enforces thermal and battery guardrails (PowerManager.isPowerSaveMode).
 * - Strictly executes off the main thread with zero leaks on track switching.
 */
object AudioPreviewExtractor {

    private const val TAG = "AudioPreviewExtractor"

    // Binary cache format header for MultiBand v3 ('MBA3')
    private const val CACHE_MAGIC = 0x4D424133
    const val DEFAULT_BAND_COUNT = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT

    private val memoryCache = ConcurrentHashMap<String, FloatArray>()
    private val failedCache = ConcurrentHashMap<String, String>()

    private val _currentAmplitudes = MutableStateFlow<FloatArray?>(null)
    val currentAmplitudes: StateFlow<FloatArray?> = _currentAmplitudes.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    private val extractorScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeJob: Job? = null
    private var lastKey: String? = null
    private var lastBandCount: Int = -1
    private var lastSource: OverlayPreferences.PreviewAudioSource? = null

    val targetFps: Int
        get() = if (Runtime.getRuntime().availableProcessors() <= 4) 30 else 60

    fun syncTrack(
        context: Context,
        title: String?,
        artist: String?,
        isHqEnabled: Boolean,
        bandCount: Int = OverlayPreferences.getWaveformBandCount(context),
        previewSource: OverlayPreferences.PreviewAudioSource = OverlayPreferences.getPreviewAudioSource(context),
    ) {
        val safeBands = bandCount.coerceIn(1, 32)

        if (!isHqEnabled || title.isNullOrBlank()) {
            activeJob?.cancel()
            activeJob = null
            lastKey = null
            lastBandCount = -1
            lastSource = null
            _currentAmplitudes.value = null
            _extractionState.value = ExtractionState.Idle
            return
        }

        // Thermal & Power Guardrail: Disable extraction when battery saver is active
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            Log.d(TAG, "Battery saver active: disabling RMS extraction (fallback to simulated waveform)")
            activeJob?.cancel()
            activeJob = null
            _currentAmplitudes.value = null
            _extractionState.value = ExtractionState.Failed(title, "Battery Saver is active")
            return
        }

        val trackKey = sanitizeKey(title, artist ?: "")
        val cacheKey = "${trackKey}_${previewSource.key}_${safeBands}b"

        // Guard: If this exact track, band count, and source is already active, loaded, or previously failed, do NOT re-check!
        if (trackKey == lastKey && safeBands == lastBandCount && previewSource == lastSource) {
            if (_currentAmplitudes.value != null || activeJob?.isActive == true) {
                return
            }
            val failedReason = failedCache[cacheKey]
            if (failedReason != null) {
                if (_extractionState.value !is ExtractionState.Failed) {
                    _extractionState.value = ExtractionState.Failed(title, failedReason)
                }
                return
            }
        }

        val cacheFolder = File(context.cacheDir, "visualizer_cache")
        val audioFile = File(cacheFolder, "audio_${trackKey}_${previewSource.key}.m4a")
        val audioPath = if (audioFile.exists() && audioFile.length() > 0) audioFile.absolutePath else null

        // Check in-memory cache first for instant 0ms restoration
        val inMemory = memoryCache[cacheKey]
        if (inMemory != null) {
            lastKey = trackKey
            lastBandCount = safeBands
            lastSource = previewSource
            _currentAmplitudes.value = inMemory
            _extractionState.value = ExtractionState.Success(
                trackTitle = title,
                artist = artist ?: "",
                previewUrl = null,
                audioFilePath = audioPath,
                frameCount = inMemory.size / safeBands,
                fps = targetFps,
                bandCount = safeBands,
                sourceName = previewSource.title,
            )
            return
        }

        // Check failure cache to avoid spamming network for non-existent previews
        val failedReason = failedCache[cacheKey]
        if (failedReason != null) {
            lastKey = trackKey
            lastBandCount = safeBands
            lastSource = previewSource
            _currentAmplitudes.value = null
            _extractionState.value = ExtractionState.Failed(title, failedReason)
            return
        }

        activeJob?.cancel()
        lastKey = trackKey
        lastBandCount = safeBands
        lastSource = previewSource
        _extractionState.value = ExtractionState.Processing(title, artist ?: "", safeBands, previewSource.title)

        activeJob = extractorScope.launch {
            try {
                val amplitudes = loadOrExtract(
                    context.applicationContext,
                    trackKey,
                    cacheKey,
                    title,
                    artist ?: "",
                    safeBands,
                    previewSource,
                )
                if (isActive) {
                    _currentAmplitudes.value = amplitudes
                    if (amplitudes != null) {
                        failedCache.remove(cacheKey)
                    }
                }
            } catch (_: CancellationException) {
                // Rapid track skip or setting change
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to extract audio preview: ${e.message}")
                if (isActive) {
                    val reason = e.message ?: "Processing error"
                    failedCache[cacheKey] = reason
                    _currentAmplitudes.value = null
                    _extractionState.value = ExtractionState.Failed(title, reason)
                }
            }
        }
    }

    private suspend fun loadOrExtract(
        context: Context,
        trackKey: String,
        cacheKey: String,
        title: String,
        artist: String,
        bandCount: Int,
        previewSource: OverlayPreferences.PreviewAudioSource,
    ): FloatArray? {
        val cacheFolder = File(context.cacheDir, "visualizer_cache")
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
        }

        val cacheFile = File(cacheFolder, "mb3_${bandCount}b_${previewSource.key}_${trackKey}.bin")
        val audioFile = File(cacheFolder, "audio_${trackKey}_${previewSource.key}.m4a")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            val cachedData = readDiskCache(cacheFile, bandCount)
            if (cachedData != null && cachedData.isNotEmpty()) {
                memoryCache[cacheKey] = cachedData
                val audioPath = if (audioFile.exists() && audioFile.length() > 0) audioFile.absolutePath else null
                _extractionState.value = ExtractionState.Success(
                    trackTitle = title,
                    artist = artist,
                    previewUrl = null,
                    audioFilePath = audioPath,
                    frameCount = cachedData.size / bandCount,
                    fps = targetFps,
                    bandCount = bandCount,
                    sourceName = previewSource.title,
                )
                return cachedData
            }
        }

        // Fetch preview url from requested source
        var resolvedSourceName = previewSource.title
        val previewUrl = if (audioFile.exists() && audioFile.length() > 0) {
            null
        } else {
            val result = fetchPreview(title, artist, previewSource)
            if (result != null) {
                resolvedSourceName = result.sourceName
                result.url
            } else {
                null
            }
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            if (previewUrl == null) {
                val reason = "No 30-second preview found on ${previewSource.title}"
                failedCache[cacheKey] = reason
                _extractionState.value = ExtractionState.Failed(title, reason)
                return null
            }

            try {
                downloadToFile(previewUrl, audioFile)
            } catch (e: Exception) {
                val reason = e.message ?: "Download failed"
                failedCache[cacheKey] = reason
                _extractionState.value = ExtractionState.Failed(title, reason)
                return null
            }
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            val reason = "Audio preview unavailable"
            failedCache[cacheKey] = reason
            _extractionState.value = ExtractionState.Failed(title, reason)
            return null
        }

        // Decode raw audio and perform dynamic N-band frequency separation on Dispatchers.Default
        val extracted = withContext(Dispatchers.Default) {
            decodeAudioToDynamicBandsRms(audioFile, targetFps, bandCount)
        }

        if (extracted != null && extracted.isNotEmpty()) {
            writeDiskCache(cacheFile, extracted, bandCount)
            memoryCache[cacheKey] = extracted
            failedCache.remove(cacheKey)
            _extractionState.value = ExtractionState.Success(
                trackTitle = title,
                artist = artist,
                previewUrl = previewUrl,
                audioFilePath = audioFile.absolutePath,
                frameCount = extracted.size / bandCount,
                fps = targetFps,
                bandCount = bandCount,
                sourceName = resolvedSourceName,
            )
            return extracted
        } else {
            val reason = "Failed to decode audio track"
            failedCache[cacheKey] = reason
            _extractionState.value = ExtractionState.Failed(title, reason)
        }

        return null
    }

    data class PreviewFetchResult(
        val url: String,
        val sourceName: String,
    )

    fun fetchDeezerPreviewUrl(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val endpoint = URL("https://api.deezer.com/search?q=$query&limit=1")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LarpDot/1.0")
            connection.instanceFollowRedirects = true

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseText)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                data.optJSONObject(0)?.optString("preview")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Deezer search API error: ${e.message}")
            null
        }
    }

    fun fetchItunesPreviewUrl(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val endpoint = URL("https://itunes.apple.com/search?term=$query&entity=song&limit=1")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseText)
            val results = json.optJSONArray("results")
            if (results != null && results.length() > 0) {
                results.optJSONObject(0)?.optString("previewUrl")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "iTunes search API error: ${e.message}")
            null
        }
    }

    fun fetchPreview(
        title: String,
        artist: String,
        source: OverlayPreferences.PreviewAudioSource = OverlayPreferences.PreviewAudioSource.AUTO,
    ): PreviewFetchResult? {
        return when (source) {
            OverlayPreferences.PreviewAudioSource.AUTO -> {
                fetchDeezerPreviewUrl(title, artist)?.let { PreviewFetchResult(it, "Deezer") }
                    ?: fetchItunesPreviewUrl(title, artist)?.let { PreviewFetchResult(it, "iTunes") }
            }
            OverlayPreferences.PreviewAudioSource.DEEZER -> {
                fetchDeezerPreviewUrl(title, artist)?.let { PreviewFetchResult(it, "Deezer") }
            }
            OverlayPreferences.PreviewAudioSource.ITUNES -> {
                fetchItunesPreviewUrl(title, artist)?.let { PreviewFetchResult(it, "iTunes") }
            }
            OverlayPreferences.PreviewAudioSource.DEVICE -> null
        }
    }

    private fun downloadToFile(urlString: String, destination: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true

        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
    }

    /**
     * Decodes 16-bit PCM audio and separates exactly [numBands] frequency bands
     * using a 2nd-order cascaded IIR crossover filterbank.
     */
    private fun decodeAudioToDynamicBandsRms(audioFile: File, fps: Int, numBands: Int): FloatArray? {
        val safeBands = numBands.coerceIn(1, 32)
        val numCutoffs = (safeBands - 1).coerceAtLeast(1)

        val cutoffs = when (safeBands) {
            1 -> doubleArrayOf(14000.0)
            2 -> doubleArrayOf(1000.0)
            3 -> doubleArrayOf(250.0, 3500.0)
            4 -> doubleArrayOf(200.0, 1000.0, 4500.0)
            5 -> doubleArrayOf(180.0, 600.0, 2400.0, 7000.0)
            6 -> doubleArrayOf(160.0, 450.0, 1400.0, 3800.0, 8500.0)
            7 -> doubleArrayOf(160.0, 450.0, 1200.0, 3000.0, 6500.0, 12000.0)
            else -> {
                val minFreq = 140.0
                val maxFreq = 14000.0
                val logMin = kotlin.math.ln(minFreq)
                val logMax = kotlin.math.ln(maxFreq)
                val step = (logMax - logMin) / safeBands
                DoubleArray(numCutoffs) { idx ->
                    kotlin.math.exp(logMin + (idx + 1) * step)
                }
            }
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val result = ArrayList<Float>(1800 * safeBands)

        try {
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

            if (audioTrackIndex < 0 || format == null) {
                return null
            }

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

            var alphas = DoubleArray(numCutoffs) { idx ->
                1.0 - exp(-2.0 * Math.PI * cutoffs[idx] / sampleRate.toDouble())
            }

            var samplesPerFrame = sampleRate / fps
            if (samplesPerFrame <= 0) samplesPerFrame = 44100 / fps

            val lp1 = DoubleArray(numCutoffs)
            val lp2 = DoubleArray(numCutoffs)

            val sumSq = DoubleArray(safeBands)
            var currentCount = 0
            val timeoutUs = 8000L

            while (!isCodecEOS) {
                if (!isExtractorEOS) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isExtractorEOS = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
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
                            } else {
                                sampleL
                            }
                            // Average stereo channels into clean mono signal
                            val monoSample = (sampleL + sampleR) / 2
                            val s = monoSample.toDouble() / Short.MAX_VALUE.toDouble()

                            for (c in 0 until numCutoffs) {
                                val a = alphas[c]
                                lp1[c] += a * (s - lp1[c])
                                lp2[c] += a * (lp1[c] - lp2[c])
                            }

                            // Dynamic Crossover decomposition across exactly safeBands
                            // Band 0: Low-pass below cutoff 0
                            val b0 = lp2[0]
                            sumSq[0] += b0 * b0

                            // Intermediate bands: Band-pass between cutoff c-1 and cutoff c
                            for (c in 1 until numCutoffs) {
                                val bandSample = lp2[c] - lp2[c - 1]
                                sumSq[c] += bandSample * bandSample
                            }

                            // Last band: High-pass above last cutoff
                            val bLast = s - lp2[numCutoffs - 1]
                            sumSq[numCutoffs] += bLast * bLast

                            currentCount++

                            if (currentCount >= samplesPerFrame) {
                                val countD = currentCount.toDouble()
                                for (k in 0 until safeBands) {
                                    val rms = sqrt(sumSq[k] / countD).toFloat()
                                    result.add(rms)
                                    sumSq[k] = 0.0
                                }
                                currentCount = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        alphas = DoubleArray(numCutoffs) { idx ->
                            1.0 - exp(-2.0 * Math.PI * cutoffs[idx] / sampleRate.toDouble())
                        }
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    samplesPerFrame = sampleRate / fps
                    if (samplesPerFrame <= 0) samplesPerFrame = 44100 / fps
                }
            }

            if (currentCount > 0) {
                val countD = currentCount.toDouble()
                for (k in 0 until safeBands) {
                    val rms = sqrt(sumSq[k] / countD).toFloat()
                    result.add(rms)
                }
            }

            if (result.isEmpty()) return null

            // Per-band Dynamic Range Normalization (AGC) across the safeBands
            val maxPerBand = FloatArray(safeBands) { 0.001f }
            for (i in result.indices) {
                val b = i % safeBands
                if (result[i] > maxPerBand[b]) {
                    maxPerBand[b] = result[i]
                }
            }

            val finalAmplitudes = FloatArray(result.size)
            for (i in result.indices) {
                val b = i % safeBands
                val effectivePeak = (maxPerBand[b] * 0.85f).coerceAtLeast(0.001f)
                val normalized = ((result[i] / effectivePeak) * 0.95f).coerceIn(0.0f, 1.0f)
                finalAmplitudes[i] = normalized
            }

            return finalAmplitudes

        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during audio decode: ${oom.message}")
            return null
        } catch (ce: MediaCodec.CodecException) {
            Log.e(TAG, "MediaCodec.CodecException during audio decode: ${ce.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error decoding audio preview: ${e.message}")
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {}
            try {
                codec?.release()
            } catch (_: Throwable) {}
            try {
                extractor.release()
            } catch (_: Throwable) {}
        }
    }

    private fun readDiskCache(file: File, expectedBands: Int): FloatArray? {
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                val magic = dis.readInt()
                if (magic != CACHE_MAGIC) return null
                val bands = dis.readInt()
                if (bands != expectedBands) return null
                val size = dis.readInt()
                if (size <= 0 || size > 200000) return null
                val array = FloatArray(size)
                for (i in 0 until size) {
                    array[i] = dis.readFloat()
                }
                array
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading cache file: ${e.message}")
            null
        }
    }

    private fun writeDiskCache(file: File, amplitudes: FloatArray, bandCount: Int) {
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
                dos.writeInt(CACHE_MAGIC)
                dos.writeInt(bandCount)
                dos.writeInt(amplitudes.size)
                for (value in amplitudes) {
                    dos.writeFloat(value)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing cache file: ${e.message}")
        }
    }

    private fun sanitizeKey(title: String, artist: String): String {
        return (title.trim().lowercase() + "_" + artist.trim().lowercase())
            .replace(Regex("[^a-z0-9_]"), "_")
            .take(64)
    }
}
