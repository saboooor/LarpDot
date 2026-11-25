package ca.saboor.larpdot.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackHistoryTest {

    @Test
    fun mergeHistoryEntryDeduplicatesAndCapsRingBuffer() {
        val t1 = MediaHistoryEntry("Starboy", "The Weeknd", playedAtMillis = 1_000L)
        val t2 = MediaHistoryEntry("Blinding Lights", "The Weeknd", playedAtMillis = 2_000L)
        val t3 = MediaHistoryEntry("After Hours", "The Weeknd", playedAtMillis = 3_000L)

        var list = emptyList<MediaHistoryEntry>()
        list = mergeHistoryEntry(list, t1, maxEntries = 2)
        list = mergeHistoryEntry(list, t2, maxEntries = 2)
        assertEquals(listOf("Blinding Lights", "Starboy"), list.map { it.title })

        // Re-recording the current head updates metadata without duplicating
        val enrichedHead = t2.copy(album = "After Hours", appName = "Spotify", playedAtMillis = 2_500L)
        list = mergeHistoryEntry(list, enrichedHead, maxEntries = 2)
        assertEquals(2, list.size)
        assertEquals("Spotify", list.first().appName)
        assertEquals(2_500L, list.first().playedAtMillis)

        // Adding a 3rd distinct track evicts the oldest entry beyond maxEntries
        list = mergeHistoryEntry(list, t3, maxEntries = 2)
        assertEquals(listOf("After Hours", "Blinding Lights"), list.map { it.title })

        // Replaying an earlier track promotes it to the top without duplicating
        list = mergeHistoryEntry(list, t2.copy(playedAtMillis = 4_000L), maxEntries = 2)
        assertEquals(listOf("Blinding Lights", "After Hours"), list.map { it.title })

        // Placeholder / blank titles are ignored
        assertEquals(list, mergeHistoryEntry(list, MediaHistoryEntry("   ", "Artist", playedAtMillis = 5_000L), maxEntries = 2))
        assertEquals(list, mergeHistoryEntry(list, MediaHistoryEntry("Playing Track", "Artist", playedAtMillis = 5_000L), maxEntries = 2))
    }

    @Test
    fun relativeTimeAndCopyLabelFormatCleanly() {
        val now = 1_000_000_000_000L
        assertEquals("Just now", formatRelativePlayedTime(now - 20_000L, now))
        assertEquals("5m ago", formatRelativePlayedTime(now - 5 * 60_000L, now))
        assertEquals("3h ago", formatRelativePlayedTime(now - 3 * 3_600_000L, now))
        assertEquals("2d ago", formatRelativePlayedTime(now - 48 * 3_600_000L, now))
        assertEquals("", formatRelativePlayedTime(0L, now))

        assertEquals(
            "The Weeknd — Starboy",
            formatCopyableTrackLabel(MediaHistoryEntry("Starboy", "The Weeknd", playedAtMillis = now)),
        )
        assertEquals(
            "Instrumental",
            formatCopyableTrackLabel(MediaHistoryEntry("Instrumental", "", playedAtMillis = now)),
        )
    }

    @Test
    fun decodeHistoryHandlesInvalidJsonGracefully() {
        assertTrue(decodeHistoryJson(null).isEmpty())
        assertTrue(decodeHistoryJson("").isEmpty())
        assertTrue(decodeHistoryJson("not-json").isEmpty())
    }
}
