package ca.saboor.larpdot.visualizer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Lightweight BPM detector for tempo-synchronized visualizer animations.
 * Fetches accurate song tempo via Deezer API with deterministic fallback.
 * Operates completely off the UI thread with zero audio decoding and zero CPU impact.
 */
object BpmDetector {

    private const val TAG = "BpmDetector"
    const val DEFAULT_BPM = 120f

    private val bpmCache = ConcurrentHashMap<String, Float>()

    private val _currentBpm = MutableStateFlow(DEFAULT_BPM)
    val currentBpm: StateFlow<Float> = _currentBpm.asStateFlow()

    private val _detectedTrack = MutableStateFlow<String?>(null)
    val detectedTrack: StateFlow<String?> = _detectedTrack.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeJob: Job? = null
    private var lastKey: String? = null

    fun syncTrack(title: String?, artist: String?) {
        if (title.isNullOrBlank()) {
            _currentBpm.value = DEFAULT_BPM
            _detectedTrack.value = null
            _isDetecting.value = false
            return
        }

        val key = sanitizeKey(title, artist ?: "")
        if (key == lastKey && (_detectedTrack.value != null || activeJob?.isActive == true)) {
            return
        }

        val cached = bpmCache[key]
        if (cached != null) {
            lastKey = key
            _currentBpm.value = cached
            _detectedTrack.value = "$title • ${artist ?: ""}"
            _isDetecting.value = false
            return
        }

        activeJob?.cancel()
        lastKey = key
        _isDetecting.value = true

        activeJob = scope.launch {
            val detected = fetchBpm(title, artist ?: "")
            if (isActive) {
                val effectiveBpm = detected ?: fallbackBpm(title, artist ?: "")
                bpmCache[key] = effectiveBpm
                _currentBpm.value = effectiveBpm
                _detectedTrack.value = "$title • ${artist ?: ""}"
                _isDetecting.value = false
            }
        }
    }

    private fun fetchBpm(title: String, artist: String): Float? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = URL("https://api.deezer.com/search?q=$query&limit=1")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val trackId = data.optJSONObject(0)?.optLong("id") ?: return null
                val trackUrl = URL("https://api.deezer.com/track/$trackId")
                val trackConn = trackUrl.openConnection() as HttpURLConnection
                trackConn.connectTimeout = 4000
                trackConn.readTimeout = 4000
                trackConn.requestMethod = "GET"

                val trackResp = trackConn.inputStream.bufferedReader().use { it.readText() }
                trackConn.disconnect()

                val trackJson = JSONObject(trackResp)
                val rawBpm = trackJson.optDouble("bpm", 0.0).toFloat()
                if (rawBpm > 30f && rawBpm < 300f) {
                    // Musical tempo normalization: tempos > 165 BPM (e.g. Starboy 186)
                    // normalize to half-time groove (e.g. 93 BPM) for optimal visual bounce
                    if (rawBpm > 165f) rawBpm / 2f else rawBpm
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching BPM from Deezer: ${e.message}")
            null
        }
    }

    private fun fallbackBpm(title: String, artist: String): Float {
        val hash = abs((title + artist).hashCode())
        val tempos = floatArrayOf(90f, 96f, 100f, 105f, 110f, 116f, 120f, 124f, 128f, 132f)
        return tempos[hash % tempos.size]
    }

    private fun sanitizeKey(title: String, artist: String): String {
        return (title.trim().lowercase() + "_" + artist.trim().lowercase())
            .replace(Regex("[^a-z0-9_]"), "_")
            .take(64)
    }
}
