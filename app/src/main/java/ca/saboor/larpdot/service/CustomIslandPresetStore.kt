package ca.saboor.larpdot.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import ca.saboor.larpdot.notification.NotificationActivityKind
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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

internal object CustomIslandPresetCodec {
    private const val PRESET_TYPE = "larpdot-custom-island-preset"
    private const val PRESET_VERSION = 1
    private const val SHARE_CODE_PREFIX = "LARPDOT1:"
    private const val MAX_IMPORT_BYTES = 128 * 1024

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
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(compactJson.toByteArray(Charsets.UTF_8))
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return SHARE_CODE_PREFIX + encoded
    }

    fun fromShareInput(raw: String): CustomIslandPresetImportResult? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val shareToken = extractShareCode(trimmed)
        if (shareToken != null) {
            val json = decompressShareCode(shareToken) ?: return null
            return fromShareJson(json)
        }
        return fromShareJson(trimmed)
    }

    fun fromShareJson(raw: String): CustomIslandPresetImportResult? {
        if (raw.length > MAX_IMPORT_BYTES) return null
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return null
        if (root.str("type") != PRESET_TYPE) return null
        val version = root.int("version", -1)
        if (version !in 1..PRESET_VERSION) return null
        val islandJson = root.obj("island") ?: return null
        val decodedIsland = decodePortableIsland(islandJson, freshIds = true) ?: return null
        val name = root.str("name").trim().ifBlank { decodedIsland.name.ifBlank { "Imported Preset" } }.take(48)
        val description = root.str("description").trim().take(160)
        val omitted = root.int("omittedWidgets", 0).coerceAtLeast(0)
        return CustomIslandPresetImportResult(
            preset = CustomIslandPreset(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                island = decodedIsland.copy(name = name),
                createdAt = System.currentTimeMillis(),
            ),
            omittedWidgets = omitted,
        )
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
            val island = decodePortableIsland(islandObj, freshIds = false) ?: return@mapNotNull null
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

    private fun encodeIsland(island: CustomIsland): JsonObject = JsonObject().apply {
        addProperty("name", island.name)
        addProperty("visibility", island.visibility.name)
        addProperty("condition", island.condition.name)
        addProperty("tapAction", island.tapAction.name)
        if (island.notificationActionTitle.isNotBlank()) {
            addProperty("notificationActionTitle", island.notificationActionTitle)
        }
        island.tapAppPackage?.let { addProperty("tapAppPackage", it) }
        island.activityKind?.let { addProperty("activityKind", it.name) }
        add("notificationRule", with(island.notificationRule) {
            JsonObject().apply {
                addProperty("package", packageName)
                addProperty("channel", channelId)
                addProperty("title", titleContains)
                addProperty("text", textContains)
                addProperty("matchChannel", matchChannel)
            }
        })
        add("components", JsonArray().apply {
            island.expandedComponents.filter { it.kind != CustomComponentKind.WIDGET }.forEach { component ->
                add(JsonObject().apply {
                    addProperty("kind", component.kind.name)
                    addProperty("height", component.height)
                    if (component.text.isNotEmpty()) addProperty("text", component.text)
                    component.textStyle?.takeIf { component.kind == CustomComponentKind.TEXT }?.let { style ->
                        add("textStyle", encodeTextStyle(style))
                    }
                    if (component.kind == CustomComponentKind.BUTTONS) {
                        add("buttonStyle", JsonObject().apply {
                            addProperty("playBackground", component.buttonStyle.playPauseBackground)
                            addProperty("playAccent", component.buttonStyle.playPauseAccentColor)
                            addProperty("skipBackground", component.buttonStyle.skipBackground)
                            addProperty("radius", component.buttonStyle.cornerRadiusDp.toDouble())
                            addProperty("connected", component.buttonStyle.material3ButtonGroup)
                        })
                    }
                })
            }
        })
        CustomIslandPosition.entries.forEach { position ->
            val slot = island.slot(position)
            add(position.name, JsonObject().apply {
                slot.icon?.let { addProperty("icon", it.name) }
                slot.image?.let { addProperty("image", it.name) }
                if (slot.supportsIconColor) {
                    add("iconColor", JsonObject().apply {
                        addProperty("mode", slot.iconColor.mode.name)
                        addProperty("rgb", slot.iconColor.rgb)
                    })
                }
                if (slot.text != null) {
                    addProperty("text", slot.text)
                    add("textStyle", encodeTextStyle(slot.textStyle))
                }
                addProperty("width", slot.width)
                addProperty("height", slot.height)
                addProperty("paddingX", slot.paddingX)
                addProperty("paddingY", slot.paddingY)
                if (position == CustomIslandPosition.MAIN && slot.spanFullIsland) {
                    addProperty("spanFullIsland", true)
                }
            })
        }
    }

    private fun encodeTextStyle(style: CustomTextStyle): JsonObject = JsonObject().apply {
        addProperty("size", style.size)
        addProperty("rgb", style.rgb)
        addProperty("colorMode", style.colorMode.name)
        addProperty("bold", style.bold)
        addProperty("italic", style.italic)
        addProperty("underline", style.underline)
        addProperty("alignment", style.alignment.name)
    }

    private fun decodeTextStyle(obj: JsonObject?, defaultSize: Int, defaultAlignment: CustomTextAlignment): CustomTextStyle =
        if (obj == null) CustomTextStyle(size = defaultSize, alignment = defaultAlignment)
        else CustomTextStyle(
            size = obj.int("size", defaultSize).coerceIn(8, 64),
            rgb = obj.int("rgb", 0xFFFFFF).coerceIn(0, 0xFFFFFF),
            bold = obj.bool("bold", false),
            italic = obj.bool("italic", false),
            underline = obj.bool("underline", false),
            alignment = CustomTextAlignment.entries.firstOrNull { it.name == obj.str("alignment") } ?: defaultAlignment,
            colorMode = CustomColorMode.entries.firstOrNull { it.name == obj.str("colorMode") } ?: CustomColorMode.CUSTOM,
        )

    private fun decodePortableIsland(json: JsonObject, freshIds: Boolean): CustomIsland? {
        fun slot(position: CustomIslandPosition): CustomIslandSlot {
            val entry = json.obj(position.name) ?: return CustomIslandSlot(
                width = position.defaultWidth,
                height = position.defaultHeight,
            )
            val slotText = if (position != CustomIslandPosition.MAIN && entry.has("text") && !entry.get("text").isJsonNull) {
                entry.str("text").take(120)
            } else null
            val iconColor = entry.obj("iconColor")?.let { colorObj ->
                CustomIslandColor(
                    mode = CustomColorMode.entries.firstOrNull { it.name == colorObj.str("mode") } ?: CustomColorMode.MATERIAL_YOU,
                    rgb = colorObj.int("rgb", 0xFFFFFF).coerceIn(0, 0xFFFFFF),
                )
            } ?: CustomIslandColor()
            return CustomIslandSlot(
                icon = if (slotText == null && position != CustomIslandPosition.MAIN) {
                    CustomIslandIcon.entries.firstOrNull { it.name == entry.str("icon") }
                } else null,
                widgetId = null,
                image = if (slotText == null && position != CustomIslandPosition.MAIN) {
                    CustomIslandImage.entries.firstOrNull { it.name == entry.str("image") }
                } else null,
                text = slotText,
                textStyle = decodeTextStyle(entry.obj("textStyle"), defaultSize = 12, defaultAlignment = CustomTextAlignment.CENTER),
                iconColor = iconColor,
                width = entry.int("width", position.defaultWidth).coerceIn(position.minWidth, position.maxWidth),
                height = entry.int("height", position.defaultHeight).coerceIn(1, 300),
                paddingX = entry.int("paddingX", 8).coerceIn(0, 32),
                paddingY = entry.int("paddingY", 8).coerceIn(0, 32),
                spanFullIsland = position == CustomIslandPosition.MAIN && entry.bool("spanFullIsland", false),
            )
        }
        val rule = json.obj("notificationRule")
        val componentsArray = json.get("components")?.takeIf { it.isJsonArray }?.asJsonArray
        val components = componentsArray?.mapNotNull { elem ->
            val entry = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val kind = CustomComponentKind.entries.firstOrNull { it.name == entry.str("kind") }
                ?.takeUnless { it == CustomComponentKind.WIDGET }
                ?: return@mapNotNull null
            val buttonStyle = entry.obj("buttonStyle")?.let {
                OverlayPreferences.PlaybackControlsStyle(
                    playPauseBackground = it.bool("playBackground", true),
                    playPauseAccentColor = it.bool("playAccent", false),
                    skipBackground = it.bool("skipBackground", false),
                    cornerRadiusDp = it.double("radius", 20.0).takeIf(Double::isFinite)
                        ?.coerceIn(0.0, 32.0)?.toFloat() ?: 20f,
                    material3ButtonGroup = it.bool("connected", false),
                )
            } ?: OverlayPreferences.PlaybackControlsStyle()
            CustomIslandComponent(
                id = if (freshIds) UUID.randomUUID().toString() else entry.str("id").ifBlank { UUID.randomUUID().toString() },
                kind = kind,
                widgetId = null,
                height = entry.int("height", 150).coerceIn(24, 300),
                text = entry.str("text").take(1500),
                colorKey = null,
                textStyle = if (kind == CustomComponentKind.TEXT) {
                    decodeTextStyle(entry.obj("textStyle"), defaultSize = 14, defaultAlignment = CustomTextAlignment.START)
                } else null,
                buttonStyle = buttonStyle,
            )
        }.orEmpty()
        return CustomIsland(
            id = if (freshIds) UUID.randomUUID().toString() else json.str("id").ifBlank { UUID.randomUUID().toString() },
            name = json.str("name").trim().take(48),
            left = slot(CustomIslandPosition.LEFT),
            right = slot(CustomIslandPosition.RIGHT),
            main = slot(CustomIslandPosition.MAIN),
            expandedLeft = slot(CustomIslandPosition.EXPANDED_LEFT),
            expandedRight = slot(CustomIslandPosition.EXPANDED_RIGHT),
            visibility = CustomIslandVisibility.entries.firstOrNull { it.name == json.str("visibility") }
                ?: CustomIslandVisibility.IDLE,
            condition = CustomIslandCondition.entries.firstOrNull { it.name == json.str("condition") }
                ?: CustomIslandCondition.MUSIC,
            activityKind = NotificationActivityKind.entries.firstOrNull { it.name == json.str("activityKind") },
            notificationRule = CustomNotificationRule(
                packageName = rule?.str("package").orEmpty().take(200),
                channelId = rule?.str("channel").orEmpty().take(200),
                titleContains = rule?.str("title").orEmpty().take(250),
                textContains = rule?.str("text").orEmpty().take(1000),
                matchChannel = rule?.bool("matchChannel", true) ?: true,
            ),
            components = components,
            tapAction = CustomIslandTapAction.entries.firstOrNull { it.name == json.str("tapAction") }
                ?: CustomIslandTapAction.EXPAND,
            notificationActionTitle = json.str("notificationActionTitle").take(150),
            tapAppPackage = json.str("tapAppPackage").takeIf(String::isNotBlank)?.take(200),
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.int(key: String, default: Int): Int =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull() ?: default

    private fun JsonObject.long(key: String, default: Long): Long =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull() ?: default

    private fun JsonObject.double(key: String, default: Double): Double =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asDouble }.getOrNull() ?: default

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull() ?: default
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

    fun addImported(preset: CustomIslandPreset): CustomIslandPreset? {
        val copy = preset.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
        )
        return copy.takeIf { save(listOf(copy) + presets.value) }
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
