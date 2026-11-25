package ca.saboor.larpdot.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MediaHistoryEntry(
    val title: String,
    val artist: String,
    val album: String = "",
    val appName: String? = null,
    val packageName: String? = null,
    val playedAtMillis: Long,
)

internal fun mergeHistoryEntry(
    current: List<MediaHistoryEntry>,
    incoming: MediaHistoryEntry,
    maxEntries: Int = MediaPlaybackHistory.MAX_ENTRIES,
): List<MediaHistoryEntry> {
    val cleanTitle = incoming.title.trim()
    if (cleanTitle.isEmpty() || cleanTitle.equals("Playing Track", ignoreCase = true)) {
        return current
    }
    val normalized = incoming.copy(
        title = cleanTitle,
        artist = incoming.artist.trim(),
        album = incoming.album.trim(),
    )
    val head = current.firstOrNull()
    if (head != null &&
        head.title.equals(normalized.title, ignoreCase = true) &&
        head.artist.equals(normalized.artist, ignoreCase = true)
    ) {
        val mergedHead = head.copy(
            album = normalized.album.ifBlank { head.album },
            appName = normalized.appName ?: head.appName,
            packageName = normalized.packageName ?: head.packageName,
            playedAtMillis = maxOf(head.playedAtMillis, normalized.playedAtMillis),
        )
        return listOf(mergedHead) + current.drop(1)
    }
    val deduped = current.filterNot {
        it.title.equals(normalized.title, ignoreCase = true) &&
            it.artist.equals(normalized.artist, ignoreCase = true)
    }
    return (listOf(normalized) + deduped).take(maxEntries.coerceAtLeast(1))
}

internal fun encodeHistoryJson(entries: List<MediaHistoryEntry>): String {
    val array = JSONArray()
    for (entry in entries) {
        array.put(
            JSONObject().apply {
                put("title", entry.title)
                put("artist", entry.artist)
                put("album", entry.album)
                if (entry.appName != null) put("appName", entry.appName)
                if (entry.packageName != null) put("packageName", entry.packageName)
                put("playedAtMillis", entry.playedAtMillis)
            },
        )
    }
    return array.toString()
}

internal fun decodeHistoryJson(raw: String?): List<MediaHistoryEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                if (title.isEmpty()) continue
                add(
                    MediaHistoryEntry(
                        title = title,
                        artist = obj.optString("artist").trim(),
                        album = obj.optString("album").trim(),
                        appName = obj.optString("appName").takeIf { it.isNotBlank() },
                        packageName = obj.optString("packageName").takeIf { it.isNotBlank() },
                        playedAtMillis = obj.optLong("playedAtMillis", 0L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun formatRelativePlayedTime(playedAtMillis: Long, nowMillis: Long): String {
    if (playedAtMillis <= 0L) return ""
    val diffMs = (nowMillis - playedAtMillis).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

internal fun formatCopyableTrackLabel(entry: MediaHistoryEntry): String =
    if (entry.artist.isNotBlank()) "${entry.artist} — ${entry.title}" else entry.title

object MediaPlaybackHistory {
    internal const val MAX_ENTRIES = 20
    private const val PREFS_NAME = "larpdot_media_history"
    private const val KEY_ENTRIES = "recent_tracks_json"

    private var appContext: Context? = null
    private val _entries = MutableStateFlow<List<MediaHistoryEntry>>(emptyList())
    val entries: StateFlow<List<MediaHistoryEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _entries.value = decodeHistoryJson(prefs.getString(KEY_ENTRIES, null)).take(MAX_ENTRIES)
    }

    fun record(
        title: String,
        artist: String,
        album: String = "",
        appName: String? = null,
        packageName: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val incoming = MediaHistoryEntry(
            title = title,
            artist = artist,
            album = album,
            appName = appName,
            packageName = packageName,
            playedAtMillis = nowMillis,
        )
        val updated = mergeHistoryEntry(_entries.value, incoming, MAX_ENTRIES)
        if (updated == _entries.value) return
        _entries.value = updated
        persist(updated)
    }

    fun clear() {
        _entries.value = emptyList()
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit {
            remove(KEY_ENTRIES)
        }
    }

    fun copyTrack(context: Context, entry: MediaHistoryEntry) {
        val label = formatCopyableTrackLabel(entry)
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Track", label))
            Toast.makeText(context.applicationContext, "Copied: $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPlayerApp(context: Context, entry: MediaHistoryEntry) {
        val pkg = entry.packageName ?: return
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun persist(entries: List<MediaHistoryEntry>) {
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_ENTRIES, encodeHistoryJson(entries))
            }
        }
    }
}
