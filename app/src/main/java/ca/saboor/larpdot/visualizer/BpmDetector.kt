package ca.saboor.larpdot.visualizer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    private const val MAX_CACHE_ENTRIES = 500
    private const val FAILURE_RETRY_MS = 15L * 60L * 1000L

    private val bpmCache = ConcurrentHashMap<String, Float>()
    private val failedKeys = ConcurrentHashMap<String, Long>()
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJob: Job? = null
    private var lastKey: String? = null

    fun init(context: Context) {
        val app = context.applicationContext
        if (appContext != null) return
        appContext = app
        scope.launch {
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
            _detectedSource.value = "Cache"
            _isDetecting.value = false
            return
        }

        val failedAt = failedKeys[key]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_RETRY_MS) {
            lastKey = key
            _currentBpm.value = null
            _isResolved.value = false
            _detectedTrack.value = "$title • ${artist ?: ""}"
            _detectedSource.value = null
            _isDetecting.value = false
            return
        }
        if (failedAt != null) failedKeys.remove(key, failedAt)

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
                    failedKeys.remove(key)
                    bpmCache[key] = effectiveBpm
                    saveToDisk(key, effectiveBpm)
                    _currentBpm.value = effectiveBpm
                    _isResolved.value = true
                    _detectedTrack.value = "$title • ${artist ?: ""}"
                    _detectedSource.value = sourceName
                } else {
                    if (failedKeys.size >= 256) failedKeys.clear()
                    failedKeys[key] = System.currentTimeMillis()
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
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (bpmCache.size > MAX_CACHE_ENTRIES) {
                    val excess = bpmCache.keys.filter { it != key }.take(bpmCache.size - MAX_CACHE_ENTRIES)
                    excess.forEach { oldKey ->
                        bpmCache.remove(oldKey)
                        editor.remove(oldKey)
                    }
                }
                editor.putFloat(key, bpm).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed saving BPM to disk: ${e.message}")
            }
        }
    }

    private fun fetchUrlText(urlString: String): String =
        VisualizerHttp.fetchText(urlString, connectTimeoutMs = 4_000, readTimeoutMs = 4_000)

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
            val response = fetchUrlText("https://api.reccobeats.com/v1/track/search?searchText=$encodedTitle&size=10")

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

            val featResp = fetchUrlText("https://api.reccobeats.com/v1/track/$matchedId/audio-features")
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
            val response = fetchUrlText("https://api.deezer.com/search?q=$query&limit=5")

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

                val trackResp = fetchUrlText("https://api.deezer.com/track/$trackId")
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

    private val titleBracketNoiseRegex = Regex(
        """\s*[\(\[](feat\.|ft\.|with|version|remaster|deluxe|bonus|explicit|official|edit|radio|single).*?[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val titleDashNoiseRegex = Regex(
        """\s*-\s*(remaster|single|bonus|deluxe|official|edit|radio|mono|stereo|anniversary).*$""",
        RegexOption.IGNORE_CASE,
    )
    private val titleYearRemasterRegex = Regex(
        """\s*-\s*\d{4}\s*remaster.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val emptyBracketsRegex = Regex("""\(\s*\)|\[\s*\]""")
    private val artistSplitRegex = Regex(
        """,|;|/|\s+feat\.?\s+|\s+ft\.?\s+|\s+&\s+|\s+x\s+""",
        RegexOption.IGNORE_CASE,
    )

    fun cleanTitle(title: String): String {
        var cleaned = title
        // Strip parenthetical / bracketed features, versions, remasters, etc.
        cleaned = cleaned.replace(titleBracketNoiseRegex, "")
        // Strip "- Remastered...", "- Single...", "- Bonus Track...", "- Official..."
        cleaned = cleaned.replace(titleDashNoiseRegex, "")
        // Strip trailing "- 2011 Remaster", etc.
        cleaned = cleaned.replace(titleYearRemasterRegex, "")
        // Strip empty parenthesis or brackets left over
        cleaned = cleaned.replace(emptyBracketsRegex, "")
        cleaned = cleaned.trim()
        return if (cleaned.isNotBlank()) cleaned else title.trim()
    }

    fun cleanArtist(artist: String): String {
        val primary = artist.split(artistSplitRegex).firstOrNull()?.trim()
        return if (!primary.isNullOrBlank()) primary else artist.trim()
    }

    private fun sanitizeKey(title: String, artist: String): String =
        sanitizeTrackSlug(cleanTitle(title), cleanArtist(artist))
}

private val NON_SLUG_CHARS_REGEX = Regex("[^a-z0-9_]")

internal fun sanitizeTrackSlug(title: String, artist: String): String =
    (title.trim().lowercase() + "_" + artist.trim().lowercase())
        .replace(NON_SLUG_CHARS_REGEX, "_")
        .take(64)
