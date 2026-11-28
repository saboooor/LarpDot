package ca.saboor.larpdot.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import ca.saboor.larpdot.service.CustomIslandJsonCodec.int
import ca.saboor.larpdot.service.CustomIslandJsonCodec.long
import ca.saboor.larpdot.service.CustomIslandJsonCodec.obj
import ca.saboor.larpdot.service.CustomIslandJsonCodec.str
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CustomIslandPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val island: CustomIsland,
    val createdAt: Long = System.currentTimeMillis(),
)

internal data class CustomIslandPresetImportResult(
    val preset: CustomIslandPreset,
    val omittedWidgets: Int = 0,
)

internal data class CustomIslandPresetBundle(
    val name: String,
    val description: String = "",
    val appPackage: String? = null,
    val appName: String? = null,
    val presets: List<CustomIslandPreset>,
)

internal data class CustomIslandImportResult(
    val bundleName: String,
    val description: String = "",
    val appPackage: String? = null,
    val appName: String? = null,
    val presets: List<CustomIslandPreset>,
    val omittedWidgets: Int = 0,
    val isBundle: Boolean = presets.size > 1,
)

internal object CustomIslandPresetCodec {
    private const val PRESET_TYPE = "larpdot-custom-island-preset"
    private const val BUNDLE_TYPE = "larpdot-custom-island-bundle"
    private const val PRESET_VERSION = 1
    private const val SHARE_CODE_PREFIX = "LARPDOT1:"
    private const val MAX_IMPORT_BYTES = 256 * 1024

    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val compactGson = GsonBuilder().create()

    fun stripDeviceLocalWidgets(island: CustomIsland): Pair<CustomIsland, Int> {
        var omitted = 0
        fun stripSlot(slot: CustomIslandSlot): CustomIslandSlot {
            if (slot.widgetId != null) {
                omitted++
                return slot.copy(widgetId = null, colorKey = null)
            }
            return slot
        }
        val filteredComponents = island.expandedComponents.filter { component ->
            if (component.kind == CustomComponentKind.WIDGET) {
                omitted++
                false
            } else {
                true
            }
        }
        val portable = island.copy(
            left = stripSlot(island.left),
            right = stripSlot(island.right),
            main = island.main.copy(widgetId = null, colorKey = null),
            expandedLeft = stripSlot(island.expandedLeft),
            expandedRight = stripSlot(island.expandedRight),
            components = filteredComponents,
        )
        return portable to omitted
    }

    fun toShareJson(preset: CustomIslandPreset): String {
        val (portable, omitted) = stripDeviceLocalWidgets(preset.island)
        val root = encodePresetEnvelope(preset.copy(island = portable), omitted)
        return prettyGson.toJson(root)
    }

    fun toShareCode(preset: CustomIslandPreset): String {
        val (portable, omitted) = stripDeviceLocalWidgets(preset.island)
        val compactJson = compactGson.toJson(encodePresetEnvelope(preset.copy(island = portable), omitted))
        return compressToShareCode(compactJson)
    }

    fun toBundleShareJson(bundle: CustomIslandPresetBundle): String {
        val root = encodeBundleEnvelope(bundle)
        return prettyGson.toJson(root)
    }

    fun toBundleShareCode(bundle: CustomIslandPresetBundle): String {
        val compactJson = compactGson.toJson(encodeBundleEnvelope(bundle))
        return compressToShareCode(compactJson)
    }

    private fun compressToShareCode(compactJson: String): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(compactJson.toByteArray(Charsets.UTF_8))
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return SHARE_CODE_PREFIX + encoded
    }

    fun fromShareInput(raw: String): CustomIslandPresetImportResult? {
        val result = fromAnyShareInput(raw) ?: return null
        if (result.isBundle || result.presets.size != 1) return null
        return CustomIslandPresetImportResult(
            preset = result.presets.first(),
            omittedWidgets = result.omittedWidgets,
        )
    }

    fun fromShareJson(raw: String): CustomIslandPresetImportResult? {
        val result = fromAnyShareJson(raw) ?: return null
        if (result.isBundle || result.presets.size != 1) return null
        return CustomIslandPresetImportResult(
            preset = result.presets.first(),
            omittedWidgets = result.omittedWidgets,
        )
    }

    fun fromAnyShareInput(raw: String): CustomIslandImportResult? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val shareToken = extractShareCode(trimmed)
        if (shareToken != null) {
            val json = decompressShareCode(shareToken) ?: return null
            return fromAnyShareJson(json)
        }
        return fromAnyShareJson(trimmed)
    }

    fun fromAnyShareJson(raw: String): CustomIslandImportResult? {
        if (raw.length > MAX_IMPORT_BYTES) return null
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        val version = root.int("version", -1)
        if (version !in 1..PRESET_VERSION) return null
        return when (root.str("type")) {
            PRESET_TYPE -> {
                val islandJson = root.obj("island") ?: return null
                val decodedIsland = decodePortableIsland(islandJson, freshIds = true)
                val name = root.str("name").trim().ifBlank { decodedIsland.name.ifBlank { "Imported Preset" } }.take(48)
                val description = root.str("description").trim().take(160)
                val omitted = root.int("omittedWidgets", 0).coerceAtLeast(0)
                val preset = CustomIslandPreset(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                    island = decodedIsland.copy(name = name),
                    createdAt = System.currentTimeMillis(),
                )
                CustomIslandImportResult(
                    bundleName = name,
                    description = description,
                    appPackage = preset.island.associatedAppPackage(),
                    presets = listOf(preset),
                    omittedWidgets = omitted,
                    isBundle = false,
                )
            }
            BUNDLE_TYPE -> {
                val presetsArray = root.get("presets")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
                val now = System.currentTimeMillis()
                val decodedPresets = presetsArray.mapNotNull { elem ->
                    val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val islandObj = obj.obj("island") ?: return@mapNotNull null
                    val decodedIsland = decodePortableIsland(islandObj, freshIds = true)
                    val name = obj.str("name").trim().ifBlank { decodedIsland.name.ifBlank { "Custom Island" } }.take(48)
                    val description = obj.str("description").trim().take(160)
                    CustomIslandPreset(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        description = description,
                        island = decodedIsland.copy(name = name),
                        createdAt = now,
                    )
                }
                if (decodedPresets.isEmpty()) return null
                val appPackage = root.str("appPackage").trim().takeIf(String::isNotBlank)?.take(200)
                    ?: decodedPresets.firstNotNullOfOrNull { it.island.associatedAppPackage() }
                val appName = root.str("appName").trim().takeIf(String::isNotBlank)?.take(64)
                val bundleName = root.str("name").trim().ifBlank { appName ?: "App Bundle" }.take(48)
                val description = root.str("description").trim().take(160)
                val omitted = root.int("omittedWidgets", 0).coerceAtLeast(0)
                CustomIslandImportResult(
                    bundleName = bundleName,
                    description = description,
                    appPackage = appPackage,
                    appName = appName,
                    presets = decodedPresets,
                    omittedWidgets = omitted,
                    isBundle = true,
                )
            }
            else -> null
        }
    }

    internal fun encodePresetList(presets: List<CustomIslandPreset>): String {
        val root = JsonObject().apply {
            addProperty("version", PRESET_VERSION)
            add("presets", JsonArray().apply {
                presets.forEach { preset ->
                    val (portable, _) = stripDeviceLocalWidgets(preset.island)
                    add(JsonObject().apply {
                        addProperty("id", preset.id)
                        addProperty("name", preset.name)
                        addProperty("description", preset.description)
                        addProperty("createdAt", preset.createdAt)
                        add("island", encodeIsland(portable))
                    })
                }
            })
        }
        return compactGson.toJson(root)
    }

    internal fun decodePresetList(raw: String): List<CustomIslandPreset> {
        val root = JsonParser.parseString(raw).asJsonObject
        val arr = root.getAsJsonArray("presets") ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val islandObj = obj.obj("island") ?: return@mapNotNull null
            val island = decodePortableIsland(islandObj, freshIds = false)
            val id = obj.str("id").ifBlank { UUID.randomUUID().toString() }
            val name = obj.str("name").trim().ifBlank { island.name.ifBlank { "Custom Preset" } }.take(48)
            val description = obj.str("description").trim().take(160)
            val createdAt = obj.long("createdAt", System.currentTimeMillis())
            CustomIslandPreset(id = id, name = name, description = description, island = island, createdAt = createdAt)
        }
    }

    private fun extractShareCode(text: String): String? {
        val idx = text.indexOf(SHARE_CODE_PREFIX)
        if (idx < 0) return null
        val start = idx + SHARE_CODE_PREFIX.length
        val end = text.indexOfAny(charArrayOf(' ', '\n', '\r', '\t'), startIndex = start).let {
            if (it < 0) text.length else it
        }
        return text.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun decompressShareCode(token: String): String? = runCatching {
        val compressed = Base64.getUrlDecoder().decode(token)
        if (compressed.size > MAX_IMPORT_BYTES) return null
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(2048)
            var total = 0
            while (true) {
                val read = gzip.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) return null
                buffer.write(chunk, 0, read)
            }
            buffer.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    private fun encodePresetEnvelope(preset: CustomIslandPreset, omittedWidgets: Int): JsonObject = JsonObject().apply {
        addProperty("type", PRESET_TYPE)
        addProperty("version", PRESET_VERSION)
        addProperty("name", preset.name.trim().take(48))
        if (preset.description.isNotBlank()) addProperty("description", preset.description.trim().take(160))
        if (omittedWidgets > 0) addProperty("omittedWidgets", omittedWidgets)
        add("island", encodeIsland(preset.island))
    }

    private fun encodeBundleEnvelope(bundle: CustomIslandPresetBundle): JsonObject = JsonObject().apply {
        var totalOmitted = 0
        val encodedPresets = JsonArray()
        bundle.presets.forEach { preset ->
            val (portable, omitted) = stripDeviceLocalWidgets(preset.island)
            totalOmitted += omitted
            val cleanName = preset.name.trim().ifBlank { portable.name.ifBlank { "Custom Island" } }.take(48)
            encodedPresets.add(JsonObject().apply {
                addProperty("name", cleanName)
                if (preset.description.isNotBlank()) {
                    addProperty("description", preset.description.trim().take(160))
                }
                add("island", encodeIsland(portable.copy(name = cleanName)))
            })
        }
        addProperty("type", BUNDLE_TYPE)
        addProperty("version", PRESET_VERSION)
        addProperty("name", bundle.name.trim().ifBlank { bundle.appName ?: "App Bundle" }.take(48))
        if (bundle.description.isNotBlank()) addProperty("description", bundle.description.trim().take(160))
        bundle.appPackage?.takeIf(String::isNotBlank)?.let { addProperty("appPackage", it.take(200)) }
        bundle.appName?.takeIf(String::isNotBlank)?.let { addProperty("appName", it.take(64)) }
        if (totalOmitted > 0) addProperty("omittedWidgets", totalOmitted)
        add("presets", encodedPresets)
    }

    private fun encodeIsland(island: CustomIsland): JsonObject =
        CustomIslandJsonCodec.encodeIsland(island, includeWidgets = false, includeIds = false)

    private fun decodePortableIsland(json: JsonObject, freshIds: Boolean): CustomIsland =
        CustomIslandJsonCodec.decodeIsland(json, includeWidgets = false, freshIds = freshIds)
}

internal fun CustomIslandPreset.instantiateForStore(existingIslands: List<CustomIsland>): CustomIsland {
    val (portable, _) = CustomIslandPresetCodec.stripDeviceLocalWidgets(island)
    val canBeSwipeDown = existingIslands.none { it.isSwipeDownOnly }
    val resolvedVisibility = if (portable.isSwipeDownOnly && !canBeSwipeDown) {
        CustomIslandVisibility.IDLE
    } else {
        portable.visibility
    }
    return portable.copy(
        id = UUID.randomUUID().toString(),
        name = name.trim().take(48),
        visibility = resolvedVisibility,
        components = portable.expandedComponents.map { it.copy(id = UUID.randomUUID().toString()) },
    )
}

internal object CustomIslandPresetStore {
    private lateinit var file: AtomicFile
    private val mutablePresets = MutableStateFlow<List<CustomIslandPreset>>(emptyList())
    val presets = mutablePresets.asStateFlow()

    fun initialize(context: Context): Boolean {
        val savedFile = File(context.noBackupFilesDir, "custom-island-presets.json")
        file = AtomicFile(savedFile)
        return runCatching {
            mutablePresets.value = CustomIslandPresetCodec.decodePresetList(file.readFully().toString(Charsets.UTF_8))
        }.recoverCatching { error ->
            if (error !is FileNotFoundException || savedFile.exists() || File("${savedFile.path}.bak").exists()) {
                throw error
            }
            mutablePresets.value = emptyList()
        }.onFailure { Log.w("CustomIslandPresets", "Could not read saved presets", it) }.isSuccess
    }

    fun saveFromIsland(name: String, description: String, island: CustomIsland): CustomIslandPreset? {
        val (portable, _) = CustomIslandPresetCodec.stripDeviceLocalWidgets(island)
        val cleanName = name.trim().ifBlank { "Custom Preset" }.take(48)
        val preset = CustomIslandPreset(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            description = description.trim().take(160),
            island = portable.copy(name = cleanName),
            createdAt = System.currentTimeMillis(),
        )
        return preset.takeIf { save(listOf(preset) + presets.value) }
    }

    fun saveAllFromIslands(
        items: List<Pair<String, CustomIsland>>,
        description: String = "",
    ): List<CustomIslandPreset> {
        if (items.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val cleanDescription = description.trim().take(160)
        val created = items.map { (rawName, island) ->
            val (portable, _) = CustomIslandPresetCodec.stripDeviceLocalWidgets(island)
            val cleanName = rawName.trim().ifBlank { "Custom Preset" }.take(48)
            CustomIslandPreset(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                description = cleanDescription,
                island = portable.copy(name = cleanName),
                createdAt = now,
            )
        }
        return if (save(created + presets.value)) created else emptyList()
    }

    fun addImported(preset: CustomIslandPreset): CustomIslandPreset? {
        val copy = preset.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
        )
        return copy.takeIf { save(listOf(copy) + presets.value) }
    }

    fun addImportedAll(imported: List<CustomIslandPreset>): List<CustomIslandPreset> {
        if (imported.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val copies = imported.map { preset ->
            preset.copy(
                id = UUID.randomUUID().toString(),
                createdAt = now,
            )
        }
        return if (save(copies + presets.value)) copies else emptyList()
    }

    fun remove(id: String) {
        val updated = presets.value.filterNot { it.id == id }
        if (updated.size != presets.value.size) save(updated)
    }

    private fun save(list: List<CustomIslandPreset>): Boolean = runCatching {
        val bytes = CustomIslandPresetCodec.encodePresetList(list).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (exception: Exception) {
            file.failWrite(stream)
            throw exception
        }
        mutablePresets.value = list
    }.onFailure { Log.w("CustomIslandPresets", "Could not save presets", it) }.isSuccess
}
