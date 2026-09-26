package ca.saboor.larpdot.visualizer

import android.content.Context
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

/**
 * High-accuracy BPM detector for tempo-synchronized visualizer animations and pulse effects.
 * Fetches verified song tempos from music metadata APIs (ReccoBeats audio features and Deezer)
 * with persistent on-device caching.
 * Operates strictly off the UI thread with zero audio decoding and zero CPU impact.
 */
object BpmDetector {

    private const val TAG = "BpmDetector"
    const val DEFAULT_BPM = 120f
    private const val PREFS_NAME = "bpm_cache_v2"

    private val bpmCache = ConcurrentHashMap<String, Float>()
    @Volatile
    private var appContext: Context? = null

    private val _currentBpm = MutableStateFlow<Float?>(null)
    val currentBpm: StateFlow<Float?> = _currentBpm.asStateFlow()

    private val _isResolved = MutableStateFlow(false)
    val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

    private val _detectedTrack = MutableStateFlow<String?>(null)
    val detectedTrack: StateFlow<String?> = _detectedTrack.asStateFlow()

    private val _detectedSource = MutableStateFlow<String?>(null)
    val detectedSource: StateFlow<String?> = _detectedSource.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeJob: Job? = null
    private var lastKey: String? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        try {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            for ((key, value) in prefs.all) {
                if (value is Float) {
                    bpmCache[key] = value
                } else if (value is Number) {
                    bpmCache[key] = value.toFloat()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading bpm preferences: ${e.message}")
        }
    }

    fun syncTrack(title: String?, artist: String?, context: Context? = null) {
        if (appContext == null && context != null) {
            init(context)
        }

        if (title.isNullOrBlank()) {
            _currentBpm.value = null
            _isResolved.value = false
            _detectedTrack.value = null
            _detectedSource.value = null
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
            _isResolved.value = true
            _detectedTrack.value = "$title • ${artist ?: ""}"
            _isDetecting.value = false
            return
        }

        activeJob?.cancel()
        lastKey = key
        _isDetecting.value = true
        _currentBpm.value = null
        _isResolved.value = false

        activeJob = scope.launch {
            val detected = fetchBpm(title, artist ?: "")
            if (isActive) {
                if (detected != null) {
                    val (effectiveBpm, sourceName) = detected
                    bpmCache[key] = effectiveBpm
                    saveToDisk(key, effectiveBpm)
                    _currentBpm.value = effectiveBpm
                    _isResolved.value = true
                    _detectedTrack.value = "$title • ${artist ?: ""}"
                    _detectedSource.value = sourceName
                } else {
                    _currentBpm.value = null
                    _isResolved.value = false
                    _detectedTrack.value = "$title • ${artist ?: ""}"
                    _detectedSource.value = null
                }
                _isDetecting.value = false
            }
        }
    }

    private fun saveToDisk(key: String, bpm: Float) {
        appContext?.let { ctx ->
            try {
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putFloat(key, bpm)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving BPM to disk: ${e.message}")
            }
        }
    }

    private suspend fun fetchBpm(title: String, artist: String): Pair<Float, String>? {
        val cleanT = cleanTitle(title)
        val cleanA = cleanArtist(artist)

        // 1. Primary: ReccoBeats audio-features API (Spotify-aligned high-precision tempos, strict artist match)
        val reccobeatsResult = fetchReccoBeatsBpm(cleanT, cleanA)
        if (reccobeatsResult != null) {
            return reccobeatsResult to "ReccoBeats"
        }

        // 2. Secondary: Deezer search & track API (strict artist match)
        val deezerResult = fetchDeezerBpm(cleanT, cleanA)
        if (deezerResult != null) {
            return deezerResult to "Deezer"
        }

        return null
    }

    private fun fetchReccoBeatsBpm(cleanTitle: String, cleanArtist: String): Float? {
        return try {
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = URL("https://api.reccobeats.com/v1/track/search?searchText=$encodedTitle&size=10")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "LarpDot/1.0 (Android)")
            conn.instanceFollowRedirects = true

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val items = json.optJSONArray("content") ?: return null
            if (items.length() == 0) return null

            var matchedId: String? = null
            val lowerArtist = cleanArtist.lowercase()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val artists = item.optJSONArray("artists")
                var matchesArtist = false
                if (artists != null) {
                    for (a in 0 until artists.length()) {
                        val artistName = artists.optJSONObject(a)?.optString("name", "")?.lowercase() ?: ""
                        if (artistName.isNotBlank() && (lowerArtist.contains(artistName) || artistName.contains(lowerArtist))) {
                            matchesArtist = true
                            break
                        }
                    }
                }
                if (matchesArtist) {
                    matchedId = item.optString("id")
                    break
                }
            }

            // CRITICAL: Do NOT fall back to items[0] if artist does not match!
            if (matchedId.isNullOrBlank()) return null

            val featuresUrl = URL("https://api.reccobeats.com/v1/track/$matchedId/audio-features")
            val featConn = featuresUrl.openConnection() as HttpURLConnection
            featConn.connectTimeout = 4000
            featConn.readTimeout = 4000
            featConn.requestMethod = "GET"
            featConn.setRequestProperty("User-Agent", "LarpDot/1.0 (Android)")

            val featResp = featConn.inputStream.bufferedReader().use { it.readText() }
            featConn.disconnect()

            val featJson = JSONObject(featResp)
            val tempo = featJson.optDouble("tempo", 0.0).toFloat()
            if (tempo in 30.0f..300.0f) tempo else null
        } catch (e: Exception) {
            Log.w(TAG, "ReccoBeats BPM fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchDeezerBpm(cleanTitle: String, cleanArtist: String): Float? {
        return try {
            val query = URLEncoder.encode("$cleanTitle $cleanArtist".trim(), "UTF-8")
            val searchUrl = URL("https://api.deezer.com/search?q=$query&limit=5")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "LarpDot/1.0 (Android)")
            conn.instanceFollowRedirects = true

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return null
            if (data.length() == 0) return null

            val lowerArtist = cleanArtist.lowercase()
            for (i in 0 until minOf(data.length(), 5)) {
                val item = data.optJSONObject(i) ?: continue
                val artistObj = item.optJSONObject("artist")
                val artistName = artistObj?.optString("name", "")?.lowercase() ?: ""
                val matchesArtist = artistName.isNotBlank() && (lowerArtist.contains(artistName) || artistName.contains(lowerArtist))
                if (!matchesArtist) continue

                val trackId = item.optLong("id")
                if (trackId <= 0L) continue

                val trackUrl = URL("https://api.deezer.com/track/$trackId")
                val trackConn = trackUrl.openConnection() as HttpURLConnection
                trackConn.connectTimeout = 4000
                trackConn.readTimeout = 4000
                trackConn.requestMethod = "GET"
                trackConn.setRequestProperty("User-Agent", "LarpDot/1.0 (Android)")

                val trackResp = trackConn.inputStream.bufferedReader().use { it.readText() }
                trackConn.disconnect()

                val trackJson = JSONObject(trackResp)
                val rawBpm = trackJson.optDouble("bpm", 0.0).toFloat()
                if (rawBpm in 30.0f..300.0f) {
                    return rawBpm
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Deezer BPM fetch failed: ${e.message}")
            null
        }
    }

    fun cleanTitle(title: String): String {
        var cleaned = title
        // Strip parenthetical / bracketed features, versions, remasters, etc.
        cleaned = cleaned.replace(Regex("""\s*[\(\[](feat\.|ft\.|with|version|remaster|deluxe|bonus|explicit|official|edit|radio|single).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        // Strip "- Remastered...", "- Single...", "- Bonus Track...", "- Official..."
        cleaned = cleaned.replace(Regex("""\s*-\s*(remaster|single|bonus|deluxe|official|edit|radio|mono|stereo|anniversary).*$""", RegexOption.IGNORE_CASE), "")
        // Strip trailing "- 2011 Remaster", etc.
        cleaned = cleaned.replace(Regex("""\s*-\s*\d{4}\s*remaster.*$""", RegexOption.IGNORE_CASE), "")
        // Strip empty parenthesis or brackets left over
        cleaned = cleaned.replace(Regex("""\(\s*\)|\[\s*\]"""), "")
        cleaned = cleaned.trim()
        return if (cleaned.isNotBlank()) cleaned else title.trim()
    }

    fun cleanArtist(artist: String): String {
        val primary = artist.split(Regex(""",|;|/|\s+feat\.?\s+|\s+ft\.?\s+|\s+&\s+|\s+x\s+""", RegexOption.IGNORE_CASE)).firstOrNull()?.trim()
        return if (!primary.isNullOrBlank()) primary else artist.trim()
    }

    private fun sanitizeKey(title: String, artist: String): String {
        return (cleanTitle(title).lowercase() + "_" + cleanArtist(artist).lowercase())
            .replace(Regex("[^a-z0-9_]"), "_")
            .take(64)
    }
}
