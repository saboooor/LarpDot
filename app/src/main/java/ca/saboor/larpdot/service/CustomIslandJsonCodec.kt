package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.NotificationActivityKind
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

internal fun matchingCustomNotificationIds(
    customIslands: List<CustomIsland>,
    notifications: Collection<CustomNotification>,
    deviceLocked: Boolean,
    isExiting: Boolean = false,
): Set<String> {
    if (deviceLocked || isExiting || customIslands.isEmpty() || notifications.isEmpty()) {
        return emptySet()
    }
    return buildSet {
        for (island in customIslands) {
            if (island.matchingNotification(notifications) != null) {
                add(island.id)
            }
        }
    }
}

/**
 * Shared Gson JSON codec for CustomIsland models used by both on-disk store persistence
 * ([CustomIslandStore]) and portable preset/bundle sharing ([CustomIslandPresetCodec]).
 */
internal object CustomIslandJsonCodec {
    fun encodeSettings(settings: CustomIslandSettings): JsonObject = JsonObject().apply {
        add("activeIds", JsonArray().apply {
            settings.activeIds.forEach(::add)
        })
        settings.swipeDownId?.let { addProperty("swipeDown", it) }
        add("islands", JsonArray().apply {
            settings.islands.forEach { island ->
                add(encodeIsland(island, includeWidgets = true, includeIds = true))
            }
        })
        settings.pending?.let { pending ->
            add("pending", JsonObject().apply {
                addProperty("island", pending.islandId)
                addProperty("position", pending.position.name)
                addProperty("id", pending.widgetId)
            })
        }
    }

    fun decodeSettings(raw: String): CustomIslandSettings {
        val root = JsonParser.parseString(raw).asJsonObject
        return decodeSettings(root)
    }

    fun decodeSettings(root: JsonObject): CustomIslandSettings {
        val saved = root.get("islands")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: throw IllegalArgumentException("Missing islands array")
        val islands = saved.mapNotNull { elem ->
            val obj = elem.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            if (obj.str("id").isBlank()) return@mapNotNull null
            decodeIsland(
                json = obj,
                includeWidgets = true,
                freshIds = false,
                defaultName = "Custom island",
            )
        }
        val pending = root.obj("pending")?.let { json ->
            val id = json.int("id", -1)
            val position = CustomIslandPosition.entries.firstOrNull { it.name == json.str("position") }
            val islandId = json.str("island")
            if (id > 0 && position != null && islands.any { it.id == islandId }) {
                PendingIslandWidget(islandId, position, id)
            } else null
        }
        val swipeDownId = root.str("swipeDown").takeIf(String::isNotBlank)
        val normalizedIslands = enforceSingleSwipeDownIsland(islands, preferredId = swipeDownId)
        val validIslandIds = normalizedIslands.map { it.id }.toSet()
        val activeIdsArray = root.get("activeIds")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        val decodedActiveIds = buildSet {
            activeIdsArray?.forEach { elem ->
                val id = elem.takeIf(JsonElement::isJsonPrimitive)?.asString.orEmpty()
                if (id in validIslandIds) add(id)
            }
        }
        return CustomIslandSettings(
            islands = normalizedIslands,
            activeIds = decodedActiveIds,
            swipeDownId = swipeDownId,
            pending = pending,
        )
    }

    fun encodeIsland(
        island: CustomIsland,
        includeWidgets: Boolean,
        includeIds: Boolean,
    ): JsonObject = JsonObject().apply {
        if (includeIds) addProperty("id", island.id)
        addProperty("name", island.name)
        addProperty("visibility", island.visibility.name)
        addProperty("condition", island.condition.name)
        addProperty("tapAction", island.tapAction.name)
        if (includeIds || island.notificationActionTitle.isNotBlank()) {
            addProperty("notificationActionTitle", island.notificationActionTitle)
        }
        island.tapAppPackage?.let { addProperty("tapAppPackage", it) }
        island.activityKind?.let { addProperty("activityKind", it.name) }
        if (island.backgroundStyle != OverlayPreferences.ExpandedBackgroundStyle.NONE) {
            addProperty("backgroundStyle", island.backgroundStyle.name)
        }
        island.backgroundImage?.let { addProperty("backgroundImage", it.name) }
        add("notificationRule", encodeNotificationRule(island.notificationRule))
        val componentsToEncode = if (includeWidgets) {
            island.components
        } else {
            island.expandedComponents.filter { it.kind != CustomComponentKind.WIDGET }
        }
        add("components", JsonArray().apply {
            componentsToEncode.forEach { component ->
                add(encodeComponent(component, includeWidgets = includeWidgets, includeIds = includeIds))
            }
        })
        CustomIslandPosition.entries.forEach { position ->
            add(position.name, encodeSlot(position, island.slot(position), includeWidgets = includeWidgets))
        }
    }

    fun decodeIsland(
        json: JsonObject,
        includeWidgets: Boolean,
        freshIds: Boolean,
        defaultName: String = "",
    ): CustomIsland {
        val rawName = if (json.has("name") && !json.get("name").isJsonNull) {
            json.str("name")
        } else {
            defaultName
        }
        val componentsArray = json.get("components")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        val components = componentsArray?.mapNotNull { elem ->
            val entry = elem.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            decodeComponent(entry, includeWidgets = includeWidgets, freshIds = freshIds)
        }.orEmpty()

        return CustomIsland(
            id = if (freshIds) UUID.randomUUID().toString() else json.str("id").ifBlank { UUID.randomUUID().toString() },
            name = if (includeWidgets) rawName else rawName.trim().take(48),
            left = decodeSlot(CustomIslandPosition.LEFT, json.obj(CustomIslandPosition.LEFT.name), includeWidgets),
            right = decodeSlot(CustomIslandPosition.RIGHT, json.obj(CustomIslandPosition.RIGHT.name), includeWidgets),
            main = decodeSlot(CustomIslandPosition.MAIN, json.obj(CustomIslandPosition.MAIN.name), includeWidgets),
            expandedLeft = decodeSlot(CustomIslandPosition.EXPANDED_LEFT, json.obj(CustomIslandPosition.EXPANDED_LEFT.name), includeWidgets),
            expandedRight = decodeSlot(CustomIslandPosition.EXPANDED_RIGHT, json.obj(CustomIslandPosition.EXPANDED_RIGHT.name), includeWidgets),
            visibility = CustomIslandVisibility.entries.firstOrNull { it.name == json.str("visibility") }
                ?: CustomIslandVisibility.IDLE,
            condition = CustomIslandCondition.entries.firstOrNull { it.name == json.str("condition") }
                ?: CustomIslandCondition.MUSIC,
            activityKind = NotificationActivityKind.entries.firstOrNull { it.name == json.str("activityKind") },
            notificationRule = decodeNotificationRule(json.obj("notificationRule")),
            components = components,
            tapAction = CustomIslandTapAction.entries.firstOrNull { it.name == json.str("tapAction") }
                ?: CustomIslandTapAction.EXPAND,
            notificationActionTitle = json.str("notificationActionTitle").take(150),
            tapAppPackage = json.str("tapAppPackage").takeIf(String::isNotBlank)?.take(200),
            backgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.entries
                .firstOrNull { it.name == json.str("backgroundStyle") }
                ?: OverlayPreferences.ExpandedBackgroundStyle.NONE,
            backgroundImage = CustomIslandImage.entries.firstOrNull { it.name == json.str("backgroundImage") },
        )
    }

    private fun encodeNotificationRule(rule: CustomNotificationRule): JsonObject = JsonObject().apply {
        addProperty("package", rule.packageName)
        addProperty("channel", rule.channelId)
        addProperty("title", rule.titleContains)
        addProperty("text", rule.textContains)
        addProperty("matchChannel", rule.matchChannel)
        if (rule.minimizeNotification) {
            addProperty("minimizeNotification", true)
        }
        if (rule.conditions.isNotEmpty()) {
            add("conditions", JsonArray().apply {
                rule.conditions.forEach { condition ->
                    add(JsonObject().apply {
                        addProperty("field", condition.field.name)
                        addProperty("operator", condition.operator.name)
                        addProperty("pattern", condition.pattern.take(1000))
                        addProperty("join", condition.join.name)
                    })
                }
            })
        }
    }

    private fun decodeNotificationRule(rule: JsonObject?): CustomNotificationRule {
        val conditions = rule?.get("conditions")?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.mapNotNull { elem ->
            val entry = elem.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val field = CustomNotificationField.entries.firstOrNull { it.name == entry.str("field") }
                ?: CustomNotificationField.TITLE
            val operator = CustomNotificationOperator.entries.firstOrNull { it.name == entry.str("operator") }
                ?: CustomNotificationOperator.CONTAINS
            val join = CustomConditionJoin.entries.firstOrNull { it.name == entry.str("join") }
                ?: CustomConditionJoin.AND
            CustomNotificationCondition(
                field = field,
                operator = operator,
                pattern = entry.str("pattern").take(1000),
                join = join,
            )
        }.orEmpty()
        return CustomNotificationRule(
            packageName = rule?.str("package").orEmpty().take(200),
            channelId = rule?.str("channel").orEmpty().take(200),
            titleContains = rule?.str("title").orEmpty().take(250),
            textContains = rule?.str("text").orEmpty().take(1000),
            matchChannel = rule?.bool("matchChannel", true) ?: true,
            minimizeNotification = rule?.bool("minimizeNotification", false) ?: false,
            conditions = conditions,
        )
    }

    private fun encodeComponent(
        component: CustomIslandComponent,
        includeWidgets: Boolean,
        includeIds: Boolean,
    ): JsonObject = JsonObject().apply {
        if (includeIds) addProperty("id", component.id)
        addProperty("kind", component.kind.name)
        if (includeWidgets && component.widgetId != null) addProperty("widget", component.widgetId)
        addProperty("height", component.height)
        if (includeIds || component.text.isNotEmpty()) addProperty("text", component.text)
        component.textStyle?.takeIf { component.kind == CustomComponentKind.TEXT }?.let { style ->
            add("textStyle", encodeTextStyle(style))
        }
        if (component.kind == CustomComponentKind.BUTTONS) {
            add("buttonStyle", encodeButtonStyle(component.buttonStyle))
        }
        if (includeWidgets) {
            component.colorKey?.let { add("colorKey", encodeColorKey(it)) }
        }
    }

    private fun decodeComponent(
        entry: JsonObject,
        includeWidgets: Boolean,
        freshIds: Boolean,
    ): CustomIslandComponent? {
        val kind = CustomComponentKind.entries.firstOrNull { it.name == entry.str("kind") } ?: return null
        if (!includeWidgets && kind == CustomComponentKind.WIDGET) return null
        val widgetId = if (includeWidgets) entry.int("widget", -1).takeIf { it > 0 } else null
        if (kind == CustomComponentKind.WIDGET && widgetId == null) return null
        val textStyleObj = entry.obj("textStyle")
        val textStyle = when {
            textStyleObj != null -> decodeTextStyle(textStyleObj, defaultSize = 14, defaultAlignment = CustomTextAlignment.START)
            !includeWidgets && kind == CustomComponentKind.TEXT -> CustomTextStyle(size = 14, alignment = CustomTextAlignment.START)
            else -> null
        }
        return CustomIslandComponent(
            id = if (freshIds) UUID.randomUUID().toString() else entry.str("id").ifBlank { UUID.randomUUID().toString() },
            kind = kind,
            widgetId = widgetId,
            height = entry.int("height", 150).coerceIn(24, 300),
            text = entry.str("text").take(1500),
            colorKey = if (includeWidgets) decodeColorKey(entry.obj("colorKey")) else null,
            textStyle = textStyle,
            buttonStyle = decodeButtonStyle(entry.obj("buttonStyle")),
        )
    }

    private fun encodeSlot(
        position: CustomIslandPosition,
        slot: CustomIslandSlot,
        includeWidgets: Boolean,
    ): JsonObject = JsonObject().apply {
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
        if (includeWidgets && slot.widgetId != null) addProperty("widget", slot.widgetId)
        addProperty("width", slot.width)
        addProperty("height", slot.height)
        addProperty("paddingX", slot.paddingX)
        addProperty("paddingY", slot.paddingY)
        if (position == CustomIslandPosition.MAIN && slot.spanFullIsland) {
            addProperty("spanFullIsland", true)
        }
        if (includeWidgets) {
            slot.colorKey?.let { add("colorKey", encodeColorKey(it)) }
        }
    }

    private fun decodeSlot(
        position: CustomIslandPosition,
        entry: JsonObject?,
        includeWidgets: Boolean,
    ): CustomIslandSlot {
        if (entry == null) {
            return CustomIslandSlot(width = position.defaultWidth, height = position.defaultHeight)
        }
        val widget = if (includeWidgets && !position.isExpandedWing) {
            entry.int("widget", -1).takeIf { it > 0 }
        } else null
        val slotText = if (
            widget == null &&
            position != CustomIslandPosition.MAIN &&
            entry.has("text") &&
            !entry.get("text").isJsonNull
        ) {
            entry.str("text").take(120)
        } else null
        val iconColor = entry.obj("iconColor")?.let { colorObj ->
            CustomIslandColor(
                mode = CustomColorMode.entries.firstOrNull { it.name == colorObj.str("mode") } ?: CustomColorMode.MATERIAL_YOU,
                rgb = colorObj.int("rgb", 0xFFFFFF).coerceIn(0, 0xFFFFFF),
            )
        } ?: CustomIslandColor()
        return CustomIslandSlot(
            icon = if (widget == null && slotText == null && position != CustomIslandPosition.MAIN) {
                CustomIslandIcon.entries.firstOrNull { it.name == entry.str("icon") }
            } else null,
            widgetId = widget,
            image = if (widget == null && slotText == null && position != CustomIslandPosition.MAIN) {
                CustomIslandImage.entries.firstOrNull { it.name == entry.str("image") }
            } else null,
            text = slotText,
            textStyle = decodeTextStyle(entry.obj("textStyle"), defaultSize = 12, defaultAlignment = CustomTextAlignment.CENTER),
            iconColor = iconColor,
            width = entry.int("width", position.defaultWidth).coerceIn(position.minWidth, position.maxWidth),
            height = entry.int("height", position.defaultHeight).coerceIn(1, 300),
            colorKey = if (widget != null) decodeColorKey(entry.obj("colorKey")) else null,
            paddingX = entry.int("paddingX", 8).coerceIn(0, 32),
            paddingY = entry.int("paddingY", 8).coerceIn(0, 32),
            spanFullIsland = position == CustomIslandPosition.MAIN && entry.bool("spanFullIsland", false),
        )
    }

    fun encodeTextStyle(style: CustomTextStyle): JsonObject = JsonObject().apply {
        addProperty("size", style.size)
        addProperty("rgb", style.rgb)
        addProperty("colorMode", style.colorMode.name)
        addProperty("bold", style.bold)
        addProperty("italic", style.italic)
        addProperty("underline", style.underline)
        addProperty("alignment", style.alignment.name)
    }

    fun decodeTextStyle(
        obj: JsonObject?,
        defaultSize: Int,
        defaultAlignment: CustomTextAlignment,
    ): CustomTextStyle =
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

    private fun encodeButtonStyle(style: OverlayPreferences.PlaybackControlsStyle): JsonObject = JsonObject().apply {
        addProperty("playBackground", style.playPauseBackground)
        addProperty("playAccent", style.playPauseAccentColor)
        addProperty("skipBackground", style.skipBackground)
        addProperty("radius", style.cornerRadiusDp.toDouble())
        addProperty("connected", style.material3ButtonGroup)
    }

    private fun decodeButtonStyle(obj: JsonObject?): OverlayPreferences.PlaybackControlsStyle =
        if (obj == null) OverlayPreferences.PlaybackControlsStyle()
        else OverlayPreferences.PlaybackControlsStyle(
            playPauseBackground = obj.bool("playBackground", true),
            playPauseAccentColor = obj.bool("playAccent", false),
            skipBackground = obj.bool("skipBackground", false),
            cornerRadiusDp = obj.double("radius", 20.0).takeIf(Double::isFinite)
                ?.coerceIn(0.0, 32.0)?.toFloat() ?: 20f,
            material3ButtonGroup = obj.bool("connected", false),
        )

    private fun encodeColorKey(key: WidgetColorKey): JsonObject = JsonObject().apply {
        addProperty("rgb", key.rgb)
        addProperty("tolerance", key.tolerancePercent)
    }

    private fun decodeColorKey(obj: JsonObject?): WidgetColorKey? =
        obj?.int("rgb", -1)?.takeIf { it in 0..0xFFFFFF }?.let { rgb ->
            WidgetColorKey(rgb, obj.int("tolerance", 10).coerceIn(0, 100))
        }

    fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    fun JsonObject.int(key: String, default: Int): Int =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull() ?: default

    fun JsonObject.long(key: String, default: Long): Long =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull() ?: default

    fun JsonObject.double(key: String, default: Double): Double =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asDouble }.getOrNull() ?: default

    fun JsonObject.bool(key: String, default: Boolean): Boolean =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull() ?: default
}
