package ca.saboor.larpdot.service

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val LARPDOT_CATALOG_BASE_URL = "https://larpdot.luminescent.dev"
private const val MAX_SUBMIT_URL_CODE_LENGTH = 6000

internal enum class CatalogFilterKind(val queryValue: String, val label: String) {
    ALL("all", "All"),
    SINGLE("single", "Single"),
    BUNDLE("bundle", "Bundles"),
}

internal enum class CatalogSortOption(val sortBy: String, val sortOrder: String, val label: String) {
    NEWEST("createdAt", "desc", "Newest"),
    POPULAR("copies", "desc", "Popular"),
    NAME("name", "asc", "A–Z"),
}

internal data class CatalogPresetEntry(
    val id: Int,
    val slug: String,
    val name: String,
    val author: String,
    val description: String,
    val isBundle: Boolean,
    val appPackage: String?,
    val appName: String?,
    val islandCount: Int,
    val visibility: String,
    val condition: String?,
    val shareCode: String,
    val copies: Int,
)

internal data class CatalogPage(
    val page: Int,
    val perPage: Int,
    val total: Int,
    val items: List<CatalogPresetEntry>,
) {
    val hasMore: Boolean get() = page * perPage < total
}

internal object CustomIslandCatalogClient {
    fun buildCatalogApiUrl(
        search: String = "",
        kind: CatalogFilterKind = CatalogFilterKind.ALL,
        sort: CatalogSortOption = CatalogSortOption.POPULAR,
        page: Int = 1,
        perPage: Int = 20,
        baseUrl: String = LARPDOT_CATALOG_BASE_URL,
    ): String {
        val params = buildList {
            if (search.isNotBlank()) add("search=${encodeQuery(search.trim())}")
            if (kind != CatalogFilterKind.ALL) add("kind=${kind.queryValue}")
            add("sortBy=${sort.sortBy}")
            add("sortOrder=${sort.sortOrder}")
            add("page=${page.coerceAtLeast(1)}")
            add("perPage=${perPage.coerceIn(1, 50)}")
        }
        return "$baseUrl/api/presets?${params.joinToString("&")}"
    }

    fun buildWebSubmitUrl(
        shareCode: String,
        baseUrl: String = LARPDOT_CATALOG_BASE_URL,
    ): String {
        val clean = shareCode.trim()
        if (clean.isEmpty() || clean.length > MAX_SUBMIT_URL_CODE_LENGTH) {
            return "$baseUrl/presets/submit"
        }
        return "$baseUrl/presets/submit?code=${encodeQuery(clean)}"
    }

    fun buildPresetWebUrl(slug: String, baseUrl: String = LARPDOT_CATALOG_BASE_URL): String =
        "$baseUrl/presets/${encodeQuery(slug)}"

    fun parseCatalogPage(rawJson: String): CatalogPage? {
        val root = runCatching { JsonParser.parseString(rawJson) }.getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return null
        val itemsArray = root.get("items")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return null
        val items = itemsArray.mapNotNull { elem ->
            val obj = elem.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val slug = obj.str("slug").trim()
            val name = obj.str("name").trim()
            val shareCode = obj.str("shareCode").trim()
            if (slug.isEmpty() || name.isEmpty() || shareCode.isEmpty()) return@mapNotNull null
            CatalogPresetEntry(
                id = obj.int("id", 0),
                slug = slug,
                name = name,
                author = obj.str("author").trim().ifBlank { "Community" },
                description = obj.str("description").trim(),
                isBundle = obj.bool("isBundle", false),
                appPackage = obj.str("appPackage").trim().takeIf(String::isNotBlank),
                appName = obj.str("appName").trim().takeIf(String::isNotBlank),
                islandCount = obj.int("islandCount", 1).coerceAtLeast(1),
                visibility = obj.str("visibility").ifBlank { "IDLE" },
                condition = obj.str("condition").trim().takeIf(String::isNotBlank),
                shareCode = shareCode,
                copies = obj.int("copies", 0).coerceAtLeast(0),
            )
        }
        return CatalogPage(
            page = root.int("page", 1).coerceAtLeast(1),
            perPage = root.int("perPage", 20).coerceAtLeast(1),
            total = root.int("total", items.size).coerceAtLeast(items.size),
            items = items,
        )
    }

    suspend fun fetchCatalog(
        search: String = "",
        kind: CatalogFilterKind = CatalogFilterKind.ALL,
        sort: CatalogSortOption = CatalogSortOption.POPULAR,
        page: Int = 1,
        perPage: Int = 20,
    ): Result<CatalogPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(buildCatalogApiUrl(search, kind, sort, page, perPage))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LarpDot-Android")
            }
            try {
                val status = conn.responseCode
                if (status !in 200..299) {
                    throw IllegalStateException("Catalog returned HTTP $status")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseCatalogPage(body) ?: throw IllegalStateException("Invalid catalog response")
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun recordCopy(slug: String) {
        if (slug.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$LARPDOT_CATALOG_BASE_URL/api/presets/${encodeQuery(slug)}/copy")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4_000
                    readTimeout = 4_000
                    setRequestProperty("User-Agent", "LarpDot-Android")
                    doOutput = false
                }
                try {
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.int(key: String, default: Int): Int =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull() ?: default

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull() ?: default
}
