package ca.saboor.larpdot.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal enum class CustomIslandPosition(val label: String) {
    LEFT("Left wing"), RIGHT("Right wing"), MAIN("Expanded island");

    val minWidth get() = if (this == MAIN) 80 else 24
    val maxWidth get() = if (this == MAIN) 440 else 160
    val defaultWidth get() = if (this == MAIN) 300 else 64
    val defaultHeight get() = if (this == MAIN) 150 else 24
}

internal enum class CustomIslandIcon(val label: String) {
    APPS("Apps"), MUSIC("Music"), FLASHLIGHT("Flashlight"), BATTERY("Battery"),
    HEADPHONES("Headphones"), TIMER("Timer"), CALL("Phone"), STAR("Star"),
    NAVIGATION("Navigation"), NOTIFICATIONS("Notifications"), HOME("Home"), PHOTO("Photos"),
    ANDROID("Android"), AUDIO_TRACK("Audio track"), BATTERY_ALERT("Low battery"), BATTERY_SAVER("Battery saver"),
    BLUETOOTH("Bluetooth"), BRIGHTNESS("Brightness"), BUBBLES("Bubbles"), BUG("Bug"), CAR("Car"),
    HOURGLASS("Hourglass"), DOWNLOAD("Download"), EARBUDS("Earbuds"), GAME("Game"), HEARING("Hearing"),
    KEYBOARD("Keyboard"), LAPTOP("Laptop"), LAYERS("Layers"), LOCK("Lock"), MIC("Microphone"),
    MOUSE("Mouse"), PALETTE("Palette"), PAUSE("Pause"), PHONE("Mobile phone"), PIN("Pin"), PLAY("Play"),
    POWER("Power"), SCREENSHOT("Screenshot"), SEARCH("Search"), SECURITY("Security"), SETTINGS("Settings"),
    SHARE("Share"), SHUFFLE("Shuffle"), SPEAKER("Speaker"), SPEED("Speed"), SUN("Sun"), TV("TV"),
    UPLOAD("Upload"), USB("USB"), VIBRATION("Vibration"), VOLUME("Volume"), VOLUME_OFF("Muted"), WATCH("Watch"),
    WAVEFORM("Waveform"),
}

internal enum class CustomIslandVisibility(val label: String) {
    ALWAYS("Always"), CONDITIONAL("Condition"), IDLE("Idle"), SWIPE_DOWN("Swipe down"),
}

internal enum class CustomIslandCondition(val label: String) {
    MUSIC("Music"), FLASHLIGHT("Flashlight"), LIVE_ACTIVITY("Live activity"),
}

internal data class CustomIslandSlot(
    val icon: CustomIslandIcon? = null,
    val widgetId: Int? = null,
    val width: Int,
    val height: Int,
    val colorKey: WidgetColorKey? = null,
    val spanFullIsland: Boolean = false,
) {
    val hasContent get() = icon != null || widgetId != null
}

internal data class WidgetColorKey(val rgb: Int = 0, val tolerancePercent: Int = 10)

internal data class CustomIsland(
    val id: String,
    val name: String = "",
    val left: CustomIslandSlot = CustomIslandPosition.LEFT.emptySlot(),
    val right: CustomIslandSlot = CustomIslandPosition.RIGHT.emptySlot(),
    val main: CustomIslandSlot = CustomIslandPosition.MAIN.emptySlot(),
    val visibility: CustomIslandVisibility = CustomIslandVisibility.IDLE,
    val condition: CustomIslandCondition = CustomIslandCondition.MUSIC,
) {
    val isSwipeDownOnly get() = visibility == CustomIslandVisibility.SWIPE_DOWN
    val hasContent get() = if (isSwipeDownOnly) main.hasContent else left.hasContent || right.hasContent || main.hasContent
    fun slot(position: CustomIslandPosition) = when (position) {
        CustomIslandPosition.LEFT -> left
        CustomIslandPosition.RIGHT -> right
        CustomIslandPosition.MAIN -> main
    }

    fun withSlot(position: CustomIslandPosition, value: CustomIslandSlot) = when (position) {
        CustomIslandPosition.LEFT -> copy(left = value)
        CustomIslandPosition.RIGHT -> copy(right = value)
        CustomIslandPosition.MAIN -> copy(main = value)
    }
}

internal data class CustomIslandWidgetLabels(
    val label: String,
    val appName: String? = null,
)

internal data class CustomIslandDisplayInfo(
    val title: String,
    val subtitle: String,
)

internal fun CustomIsland.modeLabel(): String = when (visibility) {
    CustomIslandVisibility.ALWAYS -> "Always"
    CustomIslandVisibility.IDLE -> "Idle"
    CustomIslandVisibility.SWIPE_DOWN -> "Swipe down"
    CustomIslandVisibility.CONDITIONAL -> "Condition • ${condition.label}"
}

internal fun customIslandDisplayInfo(
    island: CustomIsland,
    resolveWidget: (Int) -> CustomIslandWidgetLabels?,
): CustomIslandDisplayInfo {
    val mode = island.modeLabel()
    val mainWidgetId = island.main.widgetId
    val mainWidget = mainWidgetId?.let(resolveWidget)
    val leftWidget = if (!island.isSwipeDownOnly) island.left.widgetId?.let(resolveWidget) else null
    val rightWidget = if (!island.isSwipeDownOnly) island.right.widgetId?.let(resolveWidget) else null
    val leftLabel = if (island.isSwipeDownOnly) null else {
        island.left.widgetId?.let { leftWidget?.label ?: "Widget" } ?: island.left.icon?.label
    }
    val rightLabel = if (island.isSwipeDownOnly) null else {
        island.right.widgetId?.let { rightWidget?.label ?: "Widget" } ?: island.right.icon?.label
    }

    val title = when {
        mainWidgetId != null -> mainWidget?.label ?: "Expanded widget"
        leftLabel != null && rightLabel != null -> "$leftLabel • $rightLabel"
        leftLabel != null -> leftLabel
        rightLabel != null -> rightLabel
        island.isSwipeDownOnly -> "Empty swipe-down island"
        else -> "Empty island"
    }

    val details = buildList {
        add(mode)
        when {
            mainWidgetId != null -> {
                mainWidget?.appName?.takeIf { it.isNotBlank() && it != mainWidget.label }?.let(::add)
                add("${island.main.width}×${island.main.height} dp")
                if (island.main.spanFullIsland) add("Full island")
                if (leftLabel != null && rightLabel != null) {
                    add("L: $leftLabel, R: $rightLabel")
                } else if (leftLabel != null) {
                    add("Left: $leftLabel")
                } else if (rightLabel != null) {
                    add("Right: $rightLabel")
                }
            }
            leftLabel != null || rightLabel != null -> {
                when {
                    island.left.widgetId != null && island.right.widgetId != null -> add("Left & Right widgets")
                    island.left.widgetId != null -> {
                        leftWidget?.appName?.takeIf { it.isNotBlank() && it != leftLabel }?.let(::add)
                        add("Left • ${island.left.width} dp")
                    }
                    island.right.widgetId != null -> {
                        rightWidget?.appName?.takeIf { it.isNotBlank() && it != rightLabel }?.let(::add)
                        add("Right • ${island.right.width} dp")
                    }
                    leftLabel != null && rightLabel != null -> add("Left & Right icons")
                    leftLabel != null -> add("Left icon")
                    else -> add("Right icon")
                }
            }
            else -> add("Tap to add widget")
        }
    }

    return CustomIslandDisplayInfo(
        title = title,
        subtitle = details.joinToString(" • "),
    )
}

private fun CustomIslandPosition.emptySlot() = CustomIslandSlot(width = defaultWidth, height = defaultHeight)

internal data class PendingIslandWidget(
    val islandId: String,
    val position: CustomIslandPosition,
    val widgetId: Int,
)

internal data class CustomIslandSettings(
    val islands: List<CustomIsland> = emptyList(),
    val activeId: String? = null,
    val swipeDownId: String? = null,
    val pending: PendingIslandWidget? = null,
) {
    val activeIsland get() = islands.firstOrNull { it.id == activeId && !it.isSwipeDownOnly && it.hasContent }
    val swipeDownIsland get() = islands.firstOrNull { it.id == swipeDownId && it.isSwipeDownOnly && it.main.widgetId != null }
    val existingSwipeDownIsland get() = islands.firstOrNull { it.isSwipeDownOnly }
    fun canUseSwipeDownVisibility(islandId: String): Boolean =
        islands.none { it.id != islandId && it.isSwipeDownOnly }
}

internal fun enforceSingleSwipeDownIsland(
    islands: List<CustomIsland>,
    preferredId: String? = null,
): List<CustomIsland> {
    val keeperId = islands.firstOrNull { it.id == preferredId && it.isSwipeDownOnly }?.id
        ?: islands.firstOrNull { it.isSwipeDownOnly }?.id
        ?: return islands
    return islands.map { island ->
        if (island.isSwipeDownOnly && island.id != keeperId) {
            island.copy(visibility = CustomIslandVisibility.IDLE)
        } else island
    }
}

internal object CustomIslandStore {
    private lateinit var file: AtomicFile
    private val mutableSettings = MutableStateFlow(CustomIslandSettings())
    val settings = mutableSettings.asStateFlow()

    fun initialize(context: Context) {
        file = AtomicFile(File(context.noBackupFilesDir, "custom-islands.json"))
        if (file.baseFile.exists()) {
            runCatching { decode(JSONObject(file.readFully().toString(Charsets.UTF_8))) }
                .onSuccess { mutableSettings.value = it }
                .onFailure { Log.w("CustomIslands", "Could not read saved islands", it) }
        }
    }

    fun create(name: String = ""): String? {
        val island = CustomIsland(UUID.randomUUID().toString(), name.trim().take(48))
        return island.id.takeIf { save(settings.value.copy(islands = settings.value.islands + island)) }
    }

    fun rename(id: String, name: String) {
        val newName = name.trim().take(48)
        if (newName.isNotBlank()) updateIsland(id) { it.copy(name = newName) }
    }

    fun setVisibility(id: String, visibility: CustomIslandVisibility) {
        val previous = settings.value
        val island = previous.islands.firstOrNull { it.id == id } ?: return
        if (visibility == CustomIslandVisibility.SWIPE_DOWN && !previous.canUseSwipeDownVisibility(id)) return
        val updated = island.copy(visibility = visibility)
        val newActiveId = when {
            updated.isSwipeDownOnly && previous.activeId == id -> null
            !updated.isSwipeDownOnly && previous.swipeDownId == id && previous.activeId == null && updated.hasContent -> id
            previous.activeId == id && !updated.hasContent -> null
            else -> previous.activeId
        }
        val newSwipeDownId = when {
            updated.isSwipeDownOnly && previous.activeId == id && previous.swipeDownId == null && updated.main.widgetId != null -> id
            !updated.isSwipeDownOnly && previous.swipeDownId == id -> null
            previous.swipeDownId == id && updated.main.widgetId == null -> null
            else -> previous.swipeDownId
        }
        save(previous.copy(
            islands = previous.islands.map { if (it.id == id) updated else it },
            activeId = newActiveId,
            swipeDownId = newSwipeDownId,
        ))
    }

    fun setCondition(id: String, condition: CustomIslandCondition) {
        updateIsland(id) { it.copy(condition = condition) }
    }

    fun setActive(id: String?) {
        if (id == null || settings.value.islands.any { it.id == id && !it.isSwipeDownOnly && it.hasContent }) {
            save(settings.value.copy(activeId = id))
        }
    }

    fun setSwipeDownActive(id: String?) {
        if (id == null || settings.value.islands.any { it.id == id && it.isSwipeDownOnly && it.main.widgetId != null }) {
            save(settings.value.copy(swipeDownId = id))
        }
    }

    fun remove(id: String) {
        val previous = settings.value
        val island = previous.islands.firstOrNull { it.id == id } ?: return
        val pending = previous.pending?.takeIf { it.islandId == id }
        if (save(previous.copy(
                islands = previous.islands.filterNot { it.id == id },
                activeId = previous.activeId.takeUnless { it == id },
                swipeDownId = previous.swipeDownId.takeUnless { it == id },
                pending = previous.pending.takeUnless { it == pending },
            ))
        ) {
            CustomIslandPosition.entries.forEach { CustomIslandWidgets.delete(island.slot(it).widgetId) }
            CustomIslandWidgets.delete(pending?.widgetId)
        }
    }

    fun configureSlot(id: String, position: CustomIslandPosition, value: CustomIslandSlot) {
        val old = settings.value.islands.firstOrNull { it.id == id }?.slot(position) ?: return
        val widgetChanged = old.widgetId != value.widgetId
        val replacement = if (widgetChanged) value.copy(colorKey = null) else value
        if (updateIsland(id) { it.withSlot(position, replacement) } && widgetChanged) {
            CustomIslandWidgets.delete(old.widgetId)
        }
    }

    fun setWidgetColorKey(id: String, position: CustomIslandPosition, widgetId: Int, colorKey: WidgetColorKey?) {
        val slot = settings.value.islands.firstOrNull { it.id == id }?.slot(position) ?: return
        if (slot.widgetId == widgetId && slot.colorKey != colorKey) {
            configureSlot(id, position, slot.copy(colorKey = colorKey))
        }
    }

    fun setPending(pending: PendingIslandWidget?): Boolean = save(settings.value.copy(pending = pending))

    fun finishPending(pending: PendingIslandWidget): Boolean {
        val previous = settings.value
        if (previous.pending != pending) return false
        val island = previous.islands.firstOrNull { it.id == pending.islandId } ?: return false
        val old = island.slot(pending.position)
        val replacement = island.withSlot(pending.position, old.copy(icon = null, widgetId = pending.widgetId, colorKey = null))
        if (!save(previous.copy(
                islands = previous.islands.map { if (it.id == island.id) replacement else it },
                pending = null,
            ))
        ) return false
        CustomIslandWidgets.delete(old.widgetId)
        return true
    }

    private fun updateIsland(id: String, update: (CustomIsland) -> CustomIsland): Boolean {
        val previous = settings.value
        val island = previous.islands.firstOrNull { it.id == id } ?: return false
        val updated = update(island)
        return save(previous.copy(
            islands = previous.islands.map { if (it.id == id) updated else it },
            activeId = previous.activeId.takeUnless { it == id && (updated.isSwipeDownOnly || !updated.hasContent) },
            swipeDownId = previous.swipeDownId.takeUnless { it == id && (!updated.isSwipeDownOnly || updated.main.widgetId == null) },
        ))
    }

    private fun save(settings: CustomIslandSettings): Boolean = runCatching {
        val bytes = encode(settings).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (exception: Exception) {
            file.failWrite(stream)
            throw exception
        }
        mutableSettings.value = settings
    }.onFailure { Log.w("CustomIslands", "Could not save islands", it) }.isSuccess

    private fun encode(settings: CustomIslandSettings) = JSONObject().apply {
        put("active", settings.activeId)
        put("swipeDown", settings.swipeDownId)
        put("islands", JSONArray().apply {
            settings.islands.forEach { island ->
                put(JSONObject().apply {
                    put("id", island.id)
                    put("name", island.name)
                    put("visibility", island.visibility.name)
                    put("condition", island.condition.name)
                    CustomIslandPosition.entries.forEach { position ->
                        val slot = island.slot(position)
                        put(position.name, JSONObject().apply {
                            put("icon", slot.icon?.name)
                            put("widget", slot.widgetId)
                            put("width", slot.width)
                            put("height", slot.height)
                            if (position == CustomIslandPosition.MAIN && slot.spanFullIsland) {
                                put("spanFullIsland", true)
                            }
                            slot.colorKey?.let { key ->
                                put("colorKey", JSONObject().put("rgb", key.rgb).put("tolerance", key.tolerancePercent))
                            }
                        })
                    }
                })
            }
        })
        settings.pending?.let {
            put("pending", JSONObject().put("island", it.islandId).put("position", it.position.name).put("id", it.widgetId))
        }
    }

    private fun decode(root: JSONObject): CustomIslandSettings {
        val saved = root.optJSONArray("islands") ?: JSONArray()
        val islands = (0 until saved.length()).mapNotNull { index ->
            val json = saved.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            fun slot(position: CustomIslandPosition): CustomIslandSlot {
                val entry = json.optJSONObject(position.name) ?: return position.emptySlot()
                val widget = entry.optInt("widget", -1).takeIf { it > 0 }
                val colorKey = if (widget != null) entry.optJSONObject("colorKey") else null
                return CustomIslandSlot(
                    icon = if (widget == null && position != CustomIslandPosition.MAIN) {
                        CustomIslandIcon.entries.firstOrNull { it.name == entry.optString("icon") }
                    } else null,
                    widgetId = widget,
                    width = entry.optInt("width", position.defaultWidth).coerceIn(position.minWidth, position.maxWidth),
                    height = entry.optInt("height", position.defaultHeight).coerceIn(1, 300),
                    colorKey = colorKey?.optInt("rgb", -1)?.takeIf { it in 0..0xFFFFFF }?.let { rgb ->
                        WidgetColorKey(rgb, colorKey.optInt("tolerance", 10).coerceIn(0, 100))
                    },
                    spanFullIsland = position == CustomIslandPosition.MAIN && entry.optBoolean("spanFullIsland", false),
                )
            }
            CustomIsland(
                id = id,
                name = json.optString("name", "Custom island"),
                left = slot(CustomIslandPosition.LEFT),
                right = slot(CustomIslandPosition.RIGHT),
                main = slot(CustomIslandPosition.MAIN),
                visibility = CustomIslandVisibility.entries.firstOrNull { it.name == json.optString("visibility") }
                    ?: CustomIslandVisibility.IDLE,
                condition = CustomIslandCondition.entries.firstOrNull { it.name == json.optString("condition") }
                    ?: CustomIslandCondition.MUSIC,
            )
        }
        val pending = root.optJSONObject("pending")?.let { json ->
            val id = json.optInt("id", -1)
            val position = CustomIslandPosition.entries.firstOrNull { it.name == json.optString("position") }
            val islandId = json.optString("island")
            if (id > 0 && position != null) PendingIslandWidget(islandId, position, id) else null
        }
        val swipeDownId = root.optString("swipeDown").takeIf(String::isNotBlank)
        val normalizedIslands = enforceSingleSwipeDownIsland(islands, preferredId = swipeDownId)
        return CustomIslandSettings(
            islands = normalizedIslands,
            activeId = root.optString("active").takeIf(String::isNotBlank),
            swipeDownId = swipeDownId,
            pending = pending,
        )
    }
}
