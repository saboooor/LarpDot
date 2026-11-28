package ca.saboor.larpdot.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIslandCatalogClientTest {
    @Test
    fun parsesCatalogPageAndSkipsIncompleteEntries() {
        val json = """
            {
              "page": 1,
              "perPage": 12,
              "total": 25,
              "items": [
                {
                  "id": 1,
                  "slug": "spotify-pill-a1b2c3",
                  "name": "Spotify Pill",
                  "author": "sab",
                  "description": "Compact album art and waveform",
                  "isBundle": false,
                  "appPackage": "com.spotify.music",
                  "appName": "Spotify",
                  "islandCount": 1,
                  "visibility": "CONDITIONAL",
                  "condition": "NOTIFICATION",
                  "shareCode": "LARPDOT1:abc123",
                  "copies": 42
                },
                {
                  "id": 2,
                  "slug": "transit-bundle-d4e5f6",
                  "name": "Transit Pack",
                  "author": "",
                  "description": "Walk & bus stop pills",
                  "isBundle": true,
                  "appPackage": "com.thetransitapp.droid",
                  "appName": "Transit",
                  "islandCount": 3,
                  "visibility": "MIXED",
                  "condition": null,
                  "shareCode": "LARPDOT1:xyz789",
                  "copies": 9
                },
                {
                  "id": 3,
                  "slug": "",
                  "name": "Broken",
                  "shareCode": ""
                }
              ]
            }
        """.trimIndent()

        val page = CustomIslandCatalogClient.parseCatalogPage(json)
        assertNotNull(page)
        assertEquals(1, page!!.page)
        assertEquals(12, page.perPage)
        assertEquals(25, page.total)
        assertTrue(page.hasMore)
        assertEquals(2, page.items.size)

        val first = page.items[0]
        assertEquals("Spotify Pill", first.name)
        assertEquals("sab", first.author)
        assertFalse(first.isBundle)
        assertEquals(42, first.copies)

        val second = page.items[1]
        assertEquals("Community", second.author)
        assertTrue(second.isBundle)
        assertEquals(3, second.islandCount)
        assertNull(second.condition)
    }

    @Test
    fun buildsApiAndSubmitUrlsSafely() {
        val apiUrl = CustomIslandCatalogClient.buildCatalogApiUrl(
            search = "google maps",
            kind = CatalogFilterKind.BUNDLE,
            sort = CatalogSortOption.NEWEST,
            page = 2,
            perPage = 24,
        )
        assertEquals(
            "https://larpdot.luminescent.dev/api/presets?search=google%20maps&kind=bundle&sortBy=createdAt&sortOrder=desc&page=2&perPage=24",
            apiUrl,
        )

        val submitUrl = CustomIslandCatalogClient.buildWebSubmitUrl("LARPDOT1:abc_123")
        assertEquals(
            "https://larpdot.luminescent.dev/presets/submit?code=LARPDOT1%3Aabc_123",
            submitUrl,
        )

        val hugeCode = "LARPDOT1:" + "a".repeat(7000)
        assertEquals(
            "https://larpdot.luminescent.dev/presets/submit",
            CustomIslandCatalogClient.buildWebSubmitUrl(hugeCode),
        )
    }
}
