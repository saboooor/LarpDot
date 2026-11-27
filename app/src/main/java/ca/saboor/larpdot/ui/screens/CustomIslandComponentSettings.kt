package ca.saboor.larpdot.ui.screens

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.CustomComponentKind
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandComponent
import ca.saboor.larpdot.service.CustomIslandNotifications
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.CustomNotificationRule
import ca.saboor.larpdot.service.CustomTextStyle
import ca.saboor.larpdot.service.CustomTextAlignment
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandVisibility
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
            CustomComponentKind.ALBUM_ART, CustomComponentKind.NOTIFICATION_IMAGE -> requireNotNull(component.kind.image).placeholderIcon
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
        CustomComponentKind.ALBUM_ART, CustomComponentKind.NOTIFICATION_IMAGE -> {
            CustomDimensionSlider("Image Height", component.height, 24..300) { height -> update { it.copy(height = height) } }
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
    val recent by CustomIslandNotifications.recent.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    val rule = island.notificationRule
    fun save(value: CustomNotificationRule) = CustomIslandStore.setNotificationRule(island.id, value)
    OutlinedButton(onClick = { picking = true }) { Text("Choose recent notification") }
    if (rule.packageName.isNotBlank()) {
        Text(rule.packageName, style = MaterialTheme.typography.bodySmall)
        if (rule.channelId.isNotBlank()) {
            LarpSwitchRow("Match notification channel", rule.matchChannel, { save(rule.copy(matchChannel = it)) })
        }
        val titleValid = isValidNotificationPattern(rule.titleContains)
        val textValid = isValidNotificationPattern(rule.textContains)
        OutlinedTextField(
            value = rule.titleContains,
            onValueChange = { save(rule.copy(titleContains = it.take(250))) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = !titleValid,
            label = { Text("Title contains") },
            supportingText = if (!titleValid) {
                { Text("Invalid regex") }
            } else null,
        )
        OutlinedTextField(
            value = rule.textContains,
            onValueChange = { save(rule.copy(textContains = it.take(1000))) },
            modifier = Modifier.fillMaxWidth(),
            isError = !textValid,
            label = { Text("Description contains") },
            supportingText = if (!textValid) {
                { Text("Invalid regex") }
            } else null,
        )
        Text(
            "Supports * and ? wildcards (e.g. Get off in * stops), or /regex/.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                            save(CustomNotificationRule(item.packageName, item.channelId, item.title))
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
