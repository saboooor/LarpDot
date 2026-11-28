package ca.saboor.larpdot.service

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class CustomIslandPosition(val label: String) {
    LEFT("Left wing"),
    RIGHT("Right wing"),
    MAIN("Expanded island"),
    EXPANDED_LEFT("Expanded left wing"),
    EXPANDED_RIGHT("Expanded right wing");

    val isExpandedWing get() = this == EXPANDED_LEFT || this == EXPANDED_RIGHT
    val minWidth get() = when {
        this == MAIN -> 80
        isExpandedWing -> 36
        else -> 24
    }
    val maxWidth get() = when {
        this == MAIN -> 440
        isExpandedWing -> 140
        else -> 160
    }
    val defaultWidth get() = when {
        this == MAIN -> 300
        isExpandedWing -> 44
        else -> 64
    }
    val defaultHeight get() = when {
        this == MAIN -> 150
        isExpandedWing -> 44
        else -> 24
    }
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
    MUSIC("Music"), FLASHLIGHT("Flashlight"), LIVE_ACTIVITY("Live activity"), NOTIFICATION("Notification"),
}

internal enum class CustomIslandImage(val label: String) {
    APP_ICON("App Icon"),
    NOTIFICATION_ICON("Notification Icon"),
    ALBUM_ART("Album Art"),
    NOTIFICATION_IMAGE("Notification Image"),
}

internal enum class CustomColorMode(val label: String) {
    MATERIAL_YOU("Material You"),
    DOMINANT("App Color"),
    CUSTOM("Custom"),
}

internal data class CustomIslandColor(
    val mode: CustomColorMode = CustomColorMode.MATERIAL_YOU,
    val rgb: Int = 0xFFFFFF,
)

internal enum class CustomIslandTapAction(val label: String) {
    EXPAND("Expand"), OPEN_APP("Open App"), OPEN_NOTIFICATION("Open Notification"),
    RUN_NOTIFICATION_ACTION("Run Notification Action"), NONE("None"),
}

internal data class CustomIslandSlot(
    val icon: CustomIslandIcon? = null,
    val widgetId: Int? = null,
    val width: Int,
    val height: Int,
    val colorKey: WidgetColorKey? = null,
    val spanFullIsland: Boolean = false,
    val paddingX: Int = 8,
    val paddingY: Int = 8,
    val image: CustomIslandImage? = null,
    val text: String? = null,
    val textStyle: CustomTextStyle = CustomTextStyle(size = 12, alignment = CustomTextAlignment.CENTER),
    val iconColor: CustomIslandColor = CustomIslandColor(),
) {
    val hasContent get() = icon != null || widgetId != null || image != null || !text.isNullOrBlank()
    val supportsIconColor get() = icon != null || image == CustomIslandImage.NOTIFICATION_ICON
}

internal data class WidgetColorKey(val rgb: Int = 0, val tolerancePercent: Int = 10)

internal enum class CustomComponentKind(val label: String, val image: CustomIslandImage? = null) {
    WIDGET("Widget"), TEXT("Text"), BUTTONS("Buttons"),
    ALBUM_ART("Album Art", CustomIslandImage.ALBUM_ART),
    NOTIFICATION_IMAGE("Notification Image", CustomIslandImage.NOTIFICATION_IMAGE),
    APP_ICON("App Icon", CustomIslandImage.APP_ICON),
    NOTIFICATION_ICON("Notification Icon", CustomIslandImage.NOTIFICATION_ICON),
    NOTIFICATION_CONTENT("Notification Content"),
}

internal enum class CustomTextAlignment { START, CENTER, END }

internal data class CustomTextStyle(
    val size: Int = 14,
    val rgb: Int = 0xFFFFFF,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val alignment: CustomTextAlignment = CustomTextAlignment.START,
    val colorMode: CustomColorMode = CustomColorMode.CUSTOM,
) {
    val colorChoice get() = CustomIslandColor(mode = colorMode, rgb = rgb)
}

internal data class CustomIslandComponent(
    val id: String = UUID.randomUUID().toString(),
    val kind: CustomComponentKind,
    val widgetId: Int? = null,
    val height: Int = 150,
    val text: String = "",
    val colorKey: WidgetColorKey? = null,
    val textStyle: CustomTextStyle? = CustomTextStyle(),
    val buttonStyle: OverlayPreferences.PlaybackControlsStyle = OverlayPreferences.PlaybackControlsStyle(),
)

internal data class CustomIsland(
    val id: String,
    val name: String = "",
    val left: CustomIslandSlot = CustomIslandPosition.LEFT.emptySlot(),
    val right: CustomIslandSlot = CustomIslandPosition.RIGHT.emptySlot(),
    val main: CustomIslandSlot = CustomIslandPosition.MAIN.emptySlot(),
    val expandedLeft: CustomIslandSlot = CustomIslandPosition.EXPANDED_LEFT.emptySlot(),
    val expandedRight: CustomIslandSlot = CustomIslandPosition.EXPANDED_RIGHT.emptySlot(),
    val visibility: CustomIslandVisibility = CustomIslandVisibility.IDLE,
    val condition: CustomIslandCondition = CustomIslandCondition.MUSIC,
    val activityKind: ca.saboor.larpdot.notification.NotificationActivityKind? = null,
    val notificationRule: CustomNotificationRule = CustomNotificationRule(),
    val components: List<CustomIslandComponent> = emptyList(),
    val tapAction: CustomIslandTapAction = CustomIslandTapAction.EXPAND,
    val notificationActionTitle: String = "",
    val tapAppPackage: String? = null,
    val backgroundStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
    val backgroundImage: CustomIslandImage? = null,
) {
    val expandedComponents get() = components
    val hasExpandedContent get() = expandedComponents.isNotEmpty() || expandedLeft.hasContent || expandedRight.hasContent
    val widgetIds get() = listOfNotNull(left.widgetId, right.widgetId) + expandedComponents.mapNotNull { it.widgetId }

    val isSwipeDownOnly get() = visibility == CustomIslandVisibility.SWIPE_DOWN
    val hasContent get() = if (isSwipeDownOnly) hasExpandedContent else left.hasContent || right.hasContent || hasExpandedContent
    val effectiveBackgroundImage: CustomIslandImage get() = backgroundImage ?: defaultBackgroundImage()

    fun defaultBackgroundImage(): CustomIslandImage = when {
        visibility == CustomIslandVisibility.CONDITIONAL && condition == CustomIslandCondition.NOTIFICATION ->
            CustomIslandImage.NOTIFICATION_IMAGE
        visibility == CustomIslandVisibility.CONDITIONAL && condition == CustomIslandCondition.LIVE_ACTIVITY ->
            CustomIslandImage.APP_ICON
        visibility == CustomIslandVisibility.CONDITIONAL && condition == CustomIslandCondition.MUSIC ->
            CustomIslandImage.ALBUM_ART
        associatedAppPackage() != null -> CustomIslandImage.APP_ICON
        else -> CustomIslandImage.ALBUM_ART
    }
    fun associatedAppPackage(resolveWidgetPackage: (Int) -> String? = { null }): String? =
        notificationRule.packageName.takeIf(String::isNotBlank)
            ?: tapAppPackage?.takeIf(String::isNotBlank)
            ?: widgetIds.firstNotNullOfOrNull { id -> resolveWidgetPackage(id)?.takeIf(String::isNotBlank) }

    fun slot(position: CustomIslandPosition) = when (position) {
        CustomIslandPosition.LEFT -> left
        CustomIslandPosition.RIGHT -> right
        CustomIslandPosition.MAIN -> main
        CustomIslandPosition.EXPANDED_LEFT -> expandedLeft
        CustomIslandPosition.EXPANDED_RIGHT -> expandedRight
    }

    fun withSlot(position: CustomIslandPosition, value: CustomIslandSlot) = when (position) {
        CustomIslandPosition.LEFT -> copy(left = value)
        CustomIslandPosition.RIGHT -> copy(right = value)
        CustomIslandPosition.MAIN -> copy(main = value)
        CustomIslandPosition.EXPANDED_LEFT -> copy(expandedLeft = value)
        CustomIslandPosition.EXPANDED_RIGHT -> copy(expandedRight = value)
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
    val mainWidgetId = island.expandedComponents.firstNotNullOfOrNull { it.widgetId }
    val mainWidget = mainWidgetId?.let(resolveWidget)
    val leftWidget = if (!island.isSwipeDownOnly) island.left.widgetId?.let(resolveWidget) else null
    val rightWidget = if (!island.isSwipeDownOnly) island.right.widgetId?.let(resolveWidget) else null
    val leftLabel = if (island.isSwipeDownOnly) null else {
        island.left.widgetId?.let { leftWidget?.label ?: "Widget" }
            ?: island.left.icon?.label
            ?: island.left.image?.label
            ?: island.left.text?.trim()?.takeIf(String::isNotEmpty)?.take(24)
    }
    val rightLabel = if (island.isSwipeDownOnly) null else {
        island.right.widgetId?.let { rightWidget?.label ?: "Widget" }
            ?: island.right.icon?.label
            ?: island.right.image?.label
            ?: island.right.text?.trim()?.takeIf(String::isNotEmpty)?.take(24)
    }

    val title = when {
        mainWidgetId != null -> mainWidget?.label ?: "Expanded widget"
        island.hasExpandedContent -> "Expanded island"
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
            island.left.image != null || island.right.image != null -> Unit
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
    val activeIds: Set<String> = emptySet(),
    val swipeDownId: String? = null,
    val pending: PendingIslandWidget? = null,
) {
    val activeIslands get() = islands.filter { it.id in activeIds && !it.isSwipeDownOnly && it.hasContent }
    val activeIsland get() = activeIslands.firstOrNull()
    val swipeDownIsland get() = islands.firstOrNull { it.id == swipeDownId && it.isSwipeDownOnly && it.hasExpandedContent }
    val existingSwipeDownIsland get() = islands.firstOrNull { it.isSwipeDownOnly }
    fun isActive(islandId: String): Boolean = activeIslands.any { it.id == islandId }
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

internal data class CustomIslandAppGroup<T>(
    val packageName: String?,
    val appName: String,
    val items: List<T>,
)

internal fun <T> groupByApp(
    items: List<T>,
    packageOf: (T) -> String?,
    resolveAppName: (String) -> String,
    generalLabel: String = "General",
): List<CustomIslandAppGroup<T>> {
    if (items.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String?, MutableList<T>>()
    for (item in items) {
        val pkg = packageOf(item)?.trim()?.takeIf(String::isNotBlank)
        grouped.getOrPut(pkg) { mutableListOf() }.add(item)
    }
    val appGroups = grouped.entries
        .filter { it.key != null }
        .map { (pkg, groupItems) ->
            val name = resolveAppName(pkg!!).ifBlank { pkg }
            CustomIslandAppGroup(packageName = pkg, appName = name, items = groupItems)
        }
        .sortedBy { it.appName.lowercase() }
    val generalGroup = grouped[null]?.let {
        listOf(CustomIslandAppGroup(packageName = null, appName = generalLabel, items = it))
    }.orEmpty()
    return appGroups + generalGroup
}

internal fun swapCustomIslands(
    islands: List<CustomIsland>,
    firstId: String,
    secondId: String,
): List<CustomIsland> {
    if (firstId == secondId || islands.size < 2) return islands
    val firstIndex = islands.indexOfFirst { it.id == firstId }
    val secondIndex = islands.indexOfFirst { it.id == secondId }
    if (firstIndex < 0 || secondIndex < 0) return islands
    return islands.toMutableList().apply {
        val tmp = this[firstIndex]
        this[firstIndex] = this[secondIndex]
        this[secondIndex] = tmp
    }
}

internal fun reorderCustomIslands(
    islands: List<CustomIsland>,
    id: String,
    delta: Int,
): List<CustomIsland> {
    if (delta == 0 || islands.size < 2) return islands
    val index = islands.indexOfFirst { it.id == id }
    if (index < 0) return islands
    val targetIndex = (index + delta).coerceIn(0, islands.lastIndex)
    if (targetIndex == index) return islands
    return islands.toMutableList().apply {
        val item = removeAt(index)
        add(targetIndex, item)
    }
}

internal fun cloneCustomIslandForDuplicate(
    source: CustomIsland,
    existingIslands: List<CustomIsland>,
): CustomIsland {
    val (portable, _) = CustomIslandPresetCodec.stripDeviceLocalWidgets(source)
    val baseName = portable.name.trim()
    val copiedName = if (baseName.isNotEmpty()) {
        val suffix = " (Copy)"
        val maxBase = (48 - suffix.length).coerceAtLeast(1)
        "${baseName.take(maxBase)}$suffix"
    } else ""
    val targetVisibility = if (
        portable.isSwipeDownOnly &&
        existingIslands.any { it.isSwipeDownOnly }
    ) {
        CustomIslandVisibility.IDLE
    } else {
        portable.visibility
    }
    return portable.copy(
        id = UUID.randomUUID().toString(),
        name = copiedName,
        visibility = targetVisibility,
        components = portable.expandedComponents.map { component ->
            component.copy(id = UUID.randomUUID().toString())
        },
    )
}

internal object CustomIslandStore {
    private lateinit var file: AtomicFile
    private val mutableSettings = MutableStateFlow(CustomIslandSettings())
    val settings = mutableSettings.asStateFlow()
    fun initialize(context: Context): Boolean {
        val savedFile = File(context.noBackupFilesDir, "custom-islands.json")
        file = AtomicFile(savedFile)
        return runCatching {
            mutableSettings.value = CustomIslandJsonCodec.decodeSettings(file.readFully().toString(Charsets.UTF_8))
        }.recoverCatching { error ->
            if (error !is FileNotFoundException || savedFile.exists() || File("${savedFile.path}.bak").exists()) {
                throw error
            }
            mutableSettings.value = CustomIslandSettings()
        }.onFailure { Log.w("CustomIslands", "Could not read saved islands", it) }.isSuccess
    }

    fun create(name: String = ""): String? {
        val island = CustomIsland(UUID.randomUUID().toString(), name.trim().take(48))
        return island.id.takeIf { save(settings.value.copy(islands = settings.value.islands + island)) }
    }

    fun duplicate(id: String): String? {
        val previous = settings.value
        val sourceIndex = previous.islands.indexOfFirst { it.id == id }
        if (sourceIndex < 0) return null
        val duplicated = cloneCustomIslandForDuplicate(previous.islands[sourceIndex], previous.islands)
        val updated = previous.islands.toMutableList().apply {
            add(sourceIndex + 1, duplicated)
        }
        return duplicated.id.takeIf { save(previous.copy(islands = updated)) }
    }

    fun addFromPreset(preset: CustomIslandPreset): String? {
        val previous = settings.value
        val island = preset.instantiateForStore(previous.islands)
        return island.id.takeIf { save(previous.copy(islands = previous.islands + island)) }
    }

    fun addAllFromPresets(presets: List<CustomIslandPreset>): List<String> {
        if (presets.isEmpty()) return emptyList()
        val previous = settings.value
        val combined = previous.islands.toMutableList()
        val addedIds = mutableListOf<String>()
        for (preset in presets) {
            val island = preset.instantiateForStore(combined)
            combined.add(island)
            addedIds.add(island.id)
        }
        return if (save(previous.copy(islands = combined))) addedIds else emptyList()
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
        val wasActive = id in previous.activeIds
        val newActiveIds = when {
            updated.isSwipeDownOnly -> previous.activeIds - id
            !updated.isSwipeDownOnly && previous.swipeDownId == id && updated.hasContent -> previous.activeIds + id
            wasActive && !updated.hasContent -> previous.activeIds - id
            else -> previous.activeIds
        }
        val newSwipeDownId = when {
            updated.isSwipeDownOnly && wasActive && previous.swipeDownId == null && updated.hasExpandedContent -> id
            !updated.isSwipeDownOnly && previous.swipeDownId == id -> null
            previous.swipeDownId == id && !updated.hasExpandedContent -> null
            else -> previous.swipeDownId
        }
        save(previous.copy(
            islands = previous.islands.map { if (it.id == id) updated else it },
            activeIds = newActiveIds,
            swipeDownId = newSwipeDownId,
        ))
    }

    fun setCondition(id: String, condition: CustomIslandCondition) {
        updateIsland(id) { it.copy(condition = condition) }
    }

    fun setIslandActive(id: String, enabled: Boolean) {
        val previous = settings.value
        if (enabled) {
            if (previous.islands.any { it.id == id && !it.isSwipeDownOnly && it.hasContent }) {
                save(previous.copy(activeIds = previous.activeIds + id))
            }
        } else if (id in previous.activeIds) {
            save(previous.copy(activeIds = previous.activeIds - id))
        }
    }

    fun setActive(id: String?) {
        if (id == null) {
            save(settings.value.copy(activeIds = emptySet()))
        } else {
            setIslandActive(id, true)
        }
    }

    fun setSwipeDownActive(id: String?) {
        if (id == null || settings.value.islands.any { it.id == id && it.isSwipeDownOnly && it.hasExpandedContent }) {
            save(settings.value.copy(swipeDownId = id))
        }
    }

    fun move(id: String, delta: Int) {
        val previous = settings.value
        val reordered = reorderCustomIslands(previous.islands, id, delta)
        if (reordered != previous.islands) {
            save(previous.copy(islands = reordered))
        }
    }

    fun swap(firstId: String, secondId: String) {
        val previous = settings.value
        val swapped = swapCustomIslands(previous.islands, firstId, secondId)
        if (swapped != previous.islands) {
            save(previous.copy(islands = swapped))
        }
    }

    fun remove(id: String) {
        val previous = settings.value
        val island = previous.islands.firstOrNull { it.id == id } ?: return
        val pending = previous.pending?.takeIf { it.islandId == id }
        if (save(previous.copy(
                islands = previous.islands.filterNot { it.id == id },
                activeIds = previous.activeIds - id,
                swipeDownId = previous.swipeDownId.takeUnless { it == id },
                pending = previous.pending.takeUnless { it == pending },
            ))
        ) {
            island.widgetIds.forEach(CustomIslandWidgets::delete)
            CustomIslandWidgets.delete(pending?.widgetId)
        }
    }

    fun setTapAction(id: String, action: CustomIslandTapAction) {
        updateIsland(id) { it.copy(tapAction = action) }
    }

    fun setNotificationActionTitle(id: String, title: String) {
        updateIsland(id) { it.copy(notificationActionTitle = title.take(150)) }
    }

    fun setTapAppPackage(id: String, packageName: String?) {
        updateIsland(id) { it.copy(tapAppPackage = packageName) }
    }

    fun setActivityKind(id: String, kind: ca.saboor.larpdot.notification.NotificationActivityKind?) {
        updateIsland(id) { it.copy(activityKind = kind) }
    }

    fun setNotificationRule(id: String, rule: CustomNotificationRule) {
        updateIsland(id) { it.copy(notificationRule = rule) }
    }

    fun setBackgroundStyle(id: String, style: OverlayPreferences.ExpandedBackgroundStyle) {
        updateIsland(id) { island ->
            val resolvedImage = if (style != OverlayPreferences.ExpandedBackgroundStyle.NONE && island.backgroundImage == null) {
                island.defaultBackgroundImage()
            } else {
                island.backgroundImage
            }
            island.copy(backgroundStyle = style, backgroundImage = resolvedImage)
        }
    }

    fun setBackgroundImage(id: String, image: CustomIslandImage) {
        updateIsland(id) { it.copy(backgroundImage = image) }
    }

    fun setBackground(
        id: String,
        style: OverlayPreferences.ExpandedBackgroundStyle,
        image: CustomIslandImage?,
    ) {
        updateIsland(id) { it.copy(backgroundStyle = style, backgroundImage = image ?: it.backgroundImage) }
    }

    fun editComponents(id: String, edit: (List<CustomIslandComponent>) -> List<CustomIslandComponent>) {
        val island = settings.value.islands.firstOrNull { it.id == id } ?: return
        val components = edit(island.expandedComponents)
        if (components == island.expandedComponents) return
        if (updateIsland(id) { it.copy(main = it.main.copy(widgetId = null, colorKey = null), components = components) }) {
            val kept = components.mapNotNull { it.widgetId }.toSet()
            island.expandedComponents.mapNotNull { it.widgetId }.filterNot { it in kept }.forEach(CustomIslandWidgets::delete)
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
        if (pending.position == CustomIslandPosition.MAIN) {
            val component = CustomIslandComponent(kind = CustomComponentKind.WIDGET, widgetId = pending.widgetId, height = island.main.height)
            val updated = island.copy(main = island.main.copy(widgetId = null, colorKey = null),
                components = island.expandedComponents + component)
            return save(previous.copy(islands = previous.islands.map { if (it.id == island.id) updated else it }, pending = null))
        }
        val old = island.slot(pending.position)
        val replacement = island.withSlot(pending.position, old.copy(icon = null, widgetId = pending.widgetId, colorKey = null, image = null, text = null))
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
            activeIds = if (updated.isSwipeDownOnly || !updated.hasContent) previous.activeIds - id else previous.activeIds,
            swipeDownId = previous.swipeDownId.takeUnless { it == id && (!updated.isSwipeDownOnly || !updated.hasExpandedContent) },
        ))
    }

    private fun save(settings: CustomIslandSettings): Boolean = runCatching {
        val bytes = CustomIslandJsonCodec.encodeSettings(settings).toString().toByteArray(Charsets.UTF_8)
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
}
