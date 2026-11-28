package ca.saboor.larpdot.ui.screens

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.CustomComponentKind
import ca.saboor.larpdot.service.CustomConditionJoin
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandComponent
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandNotifications
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.CustomNotificationCondition
import ca.saboor.larpdot.service.CustomNotificationField
import ca.saboor.larpdot.service.CustomNotificationOperator
import ca.saboor.larpdot.service.CustomNotificationRule
import ca.saboor.larpdot.service.CustomTextStyle
import ca.saboor.larpdot.service.CustomTextAlignment
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.NotificationAccessService
import ca.saboor.larpdot.service.ShizukuEarbudBattery
import ca.saboor.larpdot.service.ShizukuLiveUpdateStatus
import ca.saboor.larpdot.service.ShizukuLiveUpdateSuppressor
import ca.saboor.larpdot.service.isValidNotificationPattern
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpColorPickerButton
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.overlay.renderCustomIslandMarkdown
import ca.saboor.larpdot.ui.overlay.rememberCustomIslandDominantColor
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.overlay.customIslandPlaceholders
import ca.saboor.larpdot.ui.overlay.placeholderIcon
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.roundToInt

@Composable
internal fun CustomIslandDimensionSettings(island: CustomIsland) {
    val slot = island.main
    fun updateSlot(value: ca.saboor.larpdot.service.CustomIslandSlot) {
        CustomIslandStore.configureSlot(island.id, CustomIslandPosition.MAIN, value)
    }
    LarpCard {
        CustomDimensionSlider("Content width", slot.width, 80..440) { updateSlot(slot.copy(width = it)) }
        CustomDimensionSlider("X padding", slot.paddingX, 0..32) { updateSlot(slot.copy(paddingX = it)) }
        CustomDimensionSlider("Y padding", slot.paddingY, 0..32) { updateSlot(slot.copy(paddingY = it)) }
        LarpSwitchRow(
            title = "Full-screen widget",
            checked = slot.spanFullIsland,
            onCheckedChange = { updateSlot(slot.copy(spanFullIsland = it)) },
            subtitle = "Remove the extra top space reserved for the camera",
        )
    }
}

@Composable
internal fun CustomIslandBackgroundSettings(island: CustomIsland) {
    LarpCard {
        CustomIslandBackgroundStylePicker(
            selected = island.backgroundStyle,
            onSelect = { style -> CustomIslandStore.setBackgroundStyle(island.id, style) },
        )
        if (island.backgroundStyle != OverlayPreferences.ExpandedBackgroundStyle.NONE) {
            LarpDivider()
            Text("Background Image", style = MaterialTheme.typography.titleMedium)
            val activeImage = island.effectiveBackgroundImage
            val imageRow1 = listOf(CustomIslandImage.ALBUM_ART, CustomIslandImage.NOTIFICATION_IMAGE)
            val imageRow2 = listOf(CustomIslandImage.APP_ICON, CustomIslandImage.NOTIFICATION_ICON)
            LarpConnectedButtons(
                options = imageRow1.map { image ->
                    LarpButtonOption(
                        label = image.label,
                        icon = image.placeholderIcon,
                        checked = activeImage == image,
                        onCheckedChange = { CustomIslandStore.setBackgroundImage(island.id, image) },
                    )
                },
            )
            LarpConnectedButtons(
                options = imageRow2.map { image ->
                    LarpButtonOption(
                        label = image.label,
                        icon = image.placeholderIcon,
                        checked = activeImage == image,
                        onCheckedChange = { CustomIslandStore.setBackgroundImage(island.id, image) },
                    )
                },
            )
        }
    }
}

@Composable
private fun CustomIslandBackgroundStylePicker(
    selected: OverlayPreferences.ExpandedBackgroundStyle,
    onSelect: (OverlayPreferences.ExpandedBackgroundStyle) -> Unit,
) {
    val styleRow1 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.NONE,
        OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED,
        OverlayPreferences.ExpandedBackgroundStyle.BLENDED,
    )
    val styleRow2 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND,
        OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    )
    Text("Background Style", style = MaterialTheme.typography.titleMedium)
    LarpConnectedButtons(
        options = styleRow1.map { style ->
            LarpButtonOption(
                label = style.label,
                checked = selected == style,
                onCheckedChange = { onSelect(style) },
            )
        },
    )
    LarpConnectedButtons(
        options = styleRow2.map { style ->
            LarpButtonOption(
                label = style.label,
                checked = selected == style,
                onCheckedChange = { onSelect(style) },
            )
        },
    )
}

@Composable
internal fun CustomIslandComponentInspector(
    island: CustomIsland,
    component: CustomIslandComponent,
    onDismiss: () -> Unit,
) {
    val components = island.expandedComponents
    val index = components.indexOfFirst { it.id == component.id }
    LarpCard {
        val icon = when (component.kind) {
            CustomComponentKind.WIDGET -> LarpIcons.Apps
            CustomComponentKind.TEXT -> LarpIcons.FormatSize
            CustomComponentKind.BUTTONS -> LarpIcons.Tune
            CustomComponentKind.ALBUM_ART,
            CustomComponentKind.NOTIFICATION_IMAGE,
            CustomComponentKind.APP_ICON,
            CustomComponentKind.NOTIFICATION_ICON -> requireNotNull(component.kind.image).placeholderIcon
            CustomComponentKind.NOTIFICATION_CONTENT -> LarpIcons.Notifications
        }
        IslandPreviewInspectorHeader(component.kind.label, icon, onDismiss)
        CustomComponentSettings(island, component)
        LarpDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(enabled = index > 0, onClick = { moveComponent(island.id, component.id, -1) }) {
                Icon(LarpIcons.KeyboardArrowUp, "Move component up")
            }
            IconButton(enabled = index in 0 until components.lastIndex, onClick = { moveComponent(island.id, component.id, 1) }) {
                Icon(LarpIcons.KeyboardArrowDown, "Move component down")
            }
            IconButton(onClick = {
                CustomIslandStore.editComponents(island.id) { it.filterNot { item -> item.id == component.id } }
                onDismiss()
            }) {
                Icon(LarpIcons.DeleteOutline, "Remove component")
            }
        }
    }
}

private fun moveComponent(islandId: String, componentId: String, offset: Int) {
    CustomIslandStore.editComponents(islandId) { components ->
        val index = components.indexOfFirst { it.id == componentId }
        val target = index + offset
        if (index < 0 || target !in components.indices) components else {
            components.toMutableList().apply { add(target, removeAt(index)) }
        }
    }
}

@Composable
private fun CustomComponentSettings(island: CustomIsland, component: CustomIslandComponent) {
    fun update(edit: (CustomIslandComponent) -> CustomIslandComponent) {
        CustomIslandStore.editComponents(island.id) { items -> items.map { if (it.id == component.id) edit(it) else it } }
    }
    when (component.kind) {
        CustomComponentKind.TEXT -> {
            val text = remember(component.text, component.textStyle) { editableComponentText(component) }
            OutlinedTextField(
                text,
                { value -> update { it.copy(text = value.take(1500), textStyle = it.textStyle ?: CustomTextStyle()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text") },
                minLines = 2,
            )
            FlowRow {
                customIslandPlaceholders(island).forEach { name ->
                    TextButton(onClick = {
                        update {
                            val existing = editableComponentText(it)
                            val separator = if (existing.isBlank()) "" else " "
                            it.copy(
                                text = (existing + separator + "{$name}").take(1500),
                                textStyle = it.textStyle ?: CustomTextStyle(),
                            )
                        }
                    }) { Text("{${name}}") }
                }
            }
            val (dominantColor, dominantLabel) = rememberCustomIslandDominantColor(island)
            CustomTextFormatSettings(
                style = component.textStyle ?: CustomTextStyle(),
                dominantColor = dominantColor,
                dominantLabel = dominantLabel,
            ) { style ->
                update { it.copy(text = editableComponentText(it), textStyle = style) }
            }
        }
        CustomComponentKind.WIDGET -> {
            val id = component.widgetId ?: return
            val context = LocalContext.current
            val activity = LocalActivity.current
            val info = remember(id) { CustomIslandWidgets.info(id) }
            var showFilter by rememberSaveable(id) { mutableStateOf(false) }
            Text(remember(id) { CustomIslandWidgets.widgetLabels(context, id)?.label } ?: "Widget unavailable")
            CustomDimensionSlider("Widget height", component.height, 24..300) { height -> update { it.copy(height = height) } }
            FlowRow {
                if (info?.configure != null && activity != null) {
                    TextButton(onClick = { CustomIslandWidgets.reconfigure(activity, id) }) { Text("Configure widget") }
                }
                if (info != null && Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = { showFilter = true }) { Text("Color to Transparency") }
                }
            }
            if (showFilter && Build.VERSION.SDK_INT >= 33) {
                WidgetColorKeyDialog(id, component.colorKey, onDismiss = { showFilter = false }) { filter ->
                    update { it.copy(colorKey = filter) }
                    showFilter = false
                }
            }
        }
        CustomComponentKind.ALBUM_ART,
        CustomComponentKind.NOTIFICATION_IMAGE,
        CustomComponentKind.APP_ICON,
        CustomComponentKind.NOTIFICATION_ICON -> {
            val imageKind = requireNotNull(component.kind.image)
            val isBackground = island.backgroundStyle != OverlayPreferences.ExpandedBackgroundStyle.NONE &&
                island.effectiveBackgroundImage == imageKind
            CustomDimensionSlider("Image Height", component.height, 24..300) { height -> update { it.copy(height = height) } }
            LarpDivider()
            LarpSwitchRow(
                title = "Use as Island Background",
                subtitle = "Show ${imageKind.label.lowercase()} behind the expanded island",
                checked = isBackground,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val targetStyle = if (island.backgroundStyle == OverlayPreferences.ExpandedBackgroundStyle.NONE) {
                            OverlayPreferences.ExpandedBackgroundStyle.BLENDED
                        } else island.backgroundStyle
                        CustomIslandStore.setBackground(island.id, targetStyle, imageKind)
                    } else if (isBackground) {
                        CustomIslandStore.setBackgroundStyle(island.id, OverlayPreferences.ExpandedBackgroundStyle.NONE)
                    }
                },
            )
            if (isBackground) {
                CustomIslandBackgroundStylePicker(
                    selected = island.backgroundStyle,
                    onSelect = { style -> CustomIslandStore.setBackground(island.id, style, imageKind) },
                )
            }
        }
        CustomComponentKind.NOTIFICATION_CONTENT -> {
            Text(
                "Shows the matched notification's custom view (for example, live transit trackers or custom progress layouts) directly inside the expanded island.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CustomComponentKind.BUTTONS -> {
            val forPlayback = island.visibility == CustomIslandVisibility.CONDITIONAL &&
                island.condition == CustomIslandCondition.MUSIC
            PlaybackButtonStyleSettings(component.buttonStyle, forPlayback = forPlayback) { style ->
                update { it.copy(buttonStyle = style) }
            }
        }
    }
}

private fun editableComponentText(component: CustomIslandComponent): String =
    if (component.textStyle == null) renderCustomIslandMarkdown(component.text, emptyMap()).text else component.text

@Composable
internal fun CustomTextFormatSettings(
    style: CustomTextStyle,
    dominantColor: Color? = null,
    dominantLabel: String = "App Color",
    onChange: (CustomTextStyle) -> Unit,
) {
    var size by remember(style.size) { mutableFloatStateOf(style.size.toFloat()) }
    LarpSliderRow(
        title = "Text Size",
        valueLabel = "${size.roundToInt()} sp",
        value = size,
        valueRange = 8f..64f,
        onValueChange = { size = it },
        onValueChangeFinished = { onChange(style.copy(size = size.roundToInt())) },
    )
    LarpConnectedButtons(
        options = listOf(
            LarpButtonOption("Bold", style.bold) { onChange(style.copy(bold = it)) },
            LarpButtonOption("Italic", style.italic) { onChange(style.copy(italic = it)) },
            LarpButtonOption("Underline", style.underline) { onChange(style.copy(underline = it)) },
        ),
        singleSelection = false,
    )
    LarpConnectedButtons(CustomTextAlignment.entries.map { alignment ->
        val label = alignment.name.lowercase().replaceFirstChar { it.titlecase() }
        LarpButtonOption(label, style.alignment == alignment) { onChange(style.copy(alignment = alignment)) }
    })
    LarpColorPickerButton(
        label = "Text Color",
        selection = style.colorChoice,
        dominantColor = dominantColor,
        dominantLabel = dominantLabel,
        onSelectionChange = { choice ->
            onChange(style.copy(rgb = choice.rgb, colorMode = choice.mode))
        },
    )
}

@Composable
private fun CustomDimensionSlider(title: String, value: Int, range: IntRange, onCommit: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    LarpSliderRow(title, "${draft.roundToInt()} dp", draft, range.first.toFloat()..range.last.toFloat(),
        onValueChange = { draft = it }, onValueChangeFinished = { onCommit(draft.roundToInt()) })
}

@Composable
internal fun CustomLiveActivitySelector(island: CustomIsland) {
    var open by remember { mutableStateOf(false) }
    fun label(kind: NotificationActivityKind?) = kind?.name?.lowercase()?.replace('_', ' ')
        ?.replaceFirstChar { it.titlecase() } ?: "Any live activity"
    Column {
        OutlinedButton(onClick = { open = true }) { Text(label(island.activityKind)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf(null) + NotificationActivityKind.entries).forEach { kind ->
                DropdownMenuItem(text = { Text(label(kind)) }, onClick = {
                    CustomIslandStore.setActivityKind(island.id, kind)
                    open = false
                })
            }
        }
    }
}

@Composable
internal fun CustomNotificationSettings(island: CustomIsland) {
    val context = LocalContext.current
    val activeNotifications by CustomIslandNotifications.active.collectAsState()
    val recent by CustomIslandNotifications.recent.collectAsState()
    val shizukuStatus by ShizukuLiveUpdateSuppressor.status.collectAsState()
    val shizukuLastError by ShizukuLiveUpdateSuppressor.lastError.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    val rule = island.notificationRule
    fun save(value: CustomNotificationRule) = CustomIslandStore.setNotificationRule(island.id, value)
    val conditions = rule.effectiveConditions
    fun saveConditions(updated: List<CustomNotificationCondition>) {
        val normalized = updated.mapIndexed { idx, condition ->
            if (idx == 0) condition.copy(join = CustomConditionJoin.AND) else condition
        }
        save(rule.copy(titleContains = "", textContains = "", conditions = normalized))
    }

    LaunchedEffect(Unit) {
        ShizukuLiveUpdateSuppressor.initialize(context)
        ShizukuLiveUpdateSuppressor.refreshStatus()
    }

    OutlinedButton(onClick = { picking = true }) { Text("Choose recent notification") }
    if (rule.packageName.isNotBlank()) {
        Text(rule.packageName, style = MaterialTheme.typography.bodySmall)
        if (rule.channelId.isNotBlank()) {
            LarpSwitchRow("Match notification channel", rule.matchChannel, { save(rule.copy(matchChannel = it)) })
        }
        val needsShizuku = rule.minimizeNotification && shizukuStatus != ShizukuLiveUpdateStatus.READY
        LarpSwitchRow(
            title = "Minimize notification",
            subtitle = when {
                !rule.minimizeNotification ->
                    "Temporarily minimize the notification channel via Shizuku while active"
                shizukuStatus == ShizukuLiveUpdateStatus.READY && !shizukuLastError.isNullOrBlank() ->
                    "Shizuku error: $shizukuLastError"
                shizukuStatus == ShizukuLiveUpdateStatus.READY ->
                    "Minimizes the notification channel and restores it when dismissed"
                shizukuStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION ->
                    "Allow LarpDot in Shizuku to minimize notifications"
                shizukuStatus == ShizukuLiveUpdateStatus.PERMISSION_DENIED ->
                    "Shizuku permission denied — enable LarpDot in Shizuku"
                else -> "Start Shizuku to minimize notifications"
            },
            icon = LarpIcons.Remove,
            checked = rule.minimizeNotification,
            onCheckedChange = { minimize ->
                save(rule.copy(minimizeNotification = minimize))
                if (minimize && shizukuStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION) {
                    ShizukuLiveUpdateSuppressor.requestPermission()
                } else if (shizukuStatus == ShizukuLiveUpdateStatus.READY) {
                    NotificationAccessService.reconcileLiveUpdates()
                }
            },
        )
        if (needsShizuku) {
            val requestPermission = shizukuStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION
            val launchIntent = if (!requestPermission) {
                ShizukuEarbudBattery.managerLaunchIntent(context)
            } else null
            if (requestPermission || launchIntent != null) {
                FilledTonalButton(
                    onClick = {
                        if (requestPermission) {
                            ShizukuLiveUpdateSuppressor.requestPermission()
                        } else {
                            launchIntent?.let(context::startActivity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (requestPermission) "Allow Shizuku" else "Open Shizuku")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (conditions.isEmpty()) {
                Text(
                    "Matches any notification from this app. Add conditions to filter by title, description, or channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                conditions.forEachIndexed { index, condition ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LarpConnectedButtons(
                                options = CustomConditionJoin.entries.map { join ->
                                    LarpButtonOption(
                                        label = join.label,
                                        checked = condition.join == join,
                                        onCheckedChange = {
                                            saveConditions(
                                                conditions.mapIndexed { i, item ->
                                                    if (i == index) item.copy(join = join) else item
                                                },
                                            )
                                        },
                                    )
                                },
                                modifier = Modifier.width(164.dp),
                            )
                        }
                    }

                    val patternValid = isValidNotificationPattern(condition.pattern)
                    val maxLen = if (condition.field == CustomNotificationField.TEXT) 1000 else 250
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LarpConnectedButtons(
                                    options = CustomNotificationField.entries.map { field ->
                                        LarpButtonOption(
                                            label = field.label,
                                            checked = condition.field == field,
                                            onCheckedChange = {
                                                saveConditions(
                                                    conditions.mapIndexed { i, item ->
                                                        if (i == index) item.copy(field = field) else item
                                                    },
                                                )
                                            },
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        saveConditions(conditions.filterIndexed { i, _ -> i != index })
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = LarpIcons.DeleteOutline,
                                        contentDescription = "Remove condition",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            LarpConnectedButtons(
                                options = CustomNotificationOperator.entries.map { operator ->
                                    LarpButtonOption(
                                        label = operator.label.replaceFirstChar { it.titlecase() },
                                        checked = condition.operator == operator,
                                        onCheckedChange = {
                                            saveConditions(
                                                conditions.mapIndexed { i, item ->
                                                    if (i == index) item.copy(operator = operator) else item
                                                },
                                            )
                                        },
                                    )
                                },
                            )

                            OutlinedTextField(
                                value = condition.pattern,
                                onValueChange = { value ->
                                    saveConditions(
                                        conditions.mapIndexed { i, item ->
                                            if (i == index) item.copy(pattern = value.take(maxLen)) else item
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = condition.field != CustomNotificationField.TEXT,
                                isError = !patternValid,
                                label = { Text("${condition.field.label} ${condition.operator.label}") },
                                supportingText = if (!patternValid) {
                                    { Text("Invalid regex") }
                                } else null,
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = {
                    val defaultJoin = conditions.lastOrNull()?.join ?: CustomConditionJoin.AND
                    saveConditions(
                        conditions + CustomNotificationCondition(
                            field = CustomNotificationField.TITLE,
                            operator = CustomNotificationOperator.CONTAINS,
                            pattern = "",
                            join = if (conditions.isEmpty()) CustomConditionJoin.AND else defaultJoin,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(LarpIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add condition")
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = {
                    saveConditions(
                        conditions + CustomNotificationCondition(
                            field = CustomNotificationField.TITLE,
                            operator = CustomNotificationOperator.CONTAINS,
                            pattern = "(*)",
                            join = CustomConditionJoin.AND,
                        ),
                    )
                }) { Text("+ Capture (*)") }
                TextButton(onClick = {
                    saveConditions(
                        conditions + CustomNotificationCondition(
                            field = CustomNotificationField.TITLE,
                            operator = CustomNotificationOperator.CONTAINS,
                            pattern = "(?<value>*)",
                            join = CustomConditionJoin.AND,
                        ),
                    )
                }) { Text("+ Named (?<value>*)") }
                TextButton(onClick = {
                    saveConditions(
                        conditions + CustomNotificationCondition(
                            field = CustomNotificationField.TEXT,
                            operator = CustomNotificationOperator.NOT_CONTAINS,
                            pattern = "",
                            join = CustomConditionJoin.AND,
                        ),
                    )
                }) { Text("+ Exclude text") }
            }

            val capturePlaceholders = remember(rule) { rule.declaredCapturePlaceholders() }
            if (capturePlaceholders.isNotEmpty()) {
                Text(
                    "Captured placeholders: ${capturePlaceholders.joinToString(", ") { "{$it}" }} (available in wing and expanded text).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LiveNotificationRuleTestCard(
                rule = rule,
                activeNotifications = activeNotifications.values.toList(),
                recentNotifications = recent,
            )
            Text(
                "Supports * and ? wildcards, (...) capture groups (e.g. Get off in (*) stops), or /regex/ with (?<name>...). AND binds before OR.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Recent notifications") },
            text = {
                if (recent.isEmpty()) Text("No recent notifications. Notification access is required.")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(recent, key = { it.key }) { item ->
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            val initialConditions = if (item.title.isNotBlank()) {
                                listOf(
                                    CustomNotificationCondition(
                                        field = CustomNotificationField.TITLE,
                                        operator = CustomNotificationOperator.CONTAINS,
                                        pattern = item.title,
                                    ),
                                )
                            } else {
                                emptyList()
                            }
                            save(
                                CustomNotificationRule(
                                    packageName = item.packageName,
                                    channelId = item.channelId,
                                    minimizeNotification = rule.minimizeNotification,
                                    conditions = initialConditions,
                                ),
                            )
                            picking = false
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.title.ifBlank { item.packageName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.packageName, style = MaterialTheme.typography.labelSmall)
                                if (item.text.isNotBlank()) Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LiveNotificationRuleTestCard(
    rule: CustomNotificationRule,
    activeNotifications: List<ca.saboor.larpdot.service.CustomNotification>,
    recentNotifications: List<ca.saboor.larpdot.service.CustomNotification>,
) {
    val activeMatches = remember(rule, activeNotifications) {
        activeNotifications.filter(rule::matches)
    }
    val recentFromApp = remember(rule.packageName, recentNotifications) {
        recentNotifications.filter { it.packageName == rule.packageName }
    }
    val recentMatches = remember(rule, recentFromApp) {
        recentFromApp.filter(rule::matches)
    }
    val sampleMatch = activeMatches.firstOrNull() ?: recentMatches.firstOrNull()
    val sampleCaptures = remember(rule, sampleMatch) {
        sampleMatch?.let(rule::extractCaptures).orEmpty()
            .filterKeys { !it.startsWith("group") }
    }
    val statusText = when {
        activeMatches.isNotEmpty() ->
            "Matches ${activeMatches.size} active notification${if (activeMatches.size == 1) "" else "s"}"
        recentMatches.isNotEmpty() ->
            "Matches ${recentMatches.size} recent notification${if (recentMatches.size == 1) "" else "s"}"
        recentFromApp.isNotEmpty() ->
            "No match against ${recentFromApp.size} recent notification${if (recentFromApp.size == 1) "" else "s"} from this app"
        else -> "No recent notifications seen from this app yet"
    }
    val statusColor = when {
        activeMatches.isNotEmpty() || recentMatches.isNotEmpty() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Live Rule Test",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            if (sampleMatch != null) {
                if (sampleMatch.title.isNotBlank()) {
                    Text(
                        text = sampleMatch.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sampleMatch.text.isNotBlank()) {
                    Text(
                        text = sampleMatch.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sampleCaptures.isNotEmpty()) {
                    val formatted = sampleCaptures.entries.joinToString(" • ") { (k, v) -> "{$k} = \"$v\"" }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
