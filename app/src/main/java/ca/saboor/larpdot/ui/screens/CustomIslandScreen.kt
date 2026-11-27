package ca.saboor.larpdot.ui.screens

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.CustomComponentKind
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandComponent
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandDisplayInfo
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandPresetStore
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.customIslandDisplayInfo
import ca.saboor.larpdot.ui.components.CustomIslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpColorPickerButton
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpListItem
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.overlay.customIslandPlaceholders
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.overlay.rememberCustomIslandDominantColor
import ca.saboor.larpdot.ui.components.LarpSubPageTopBar
import ca.saboor.larpdot.ui.components.WidgetProviderPreview
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

private enum class CustomIslandSettingsSection(val label: String) {
    LAYOUT("Layout"), VISIBILITY("Visibility"), TAP("Tap Action"),
}

@Composable
internal fun CustomIslandScreen(cutoutInfo: CutoutInfo) {
    val context = LocalContext.current
    val settings by CustomIslandStore.settings.collectAsState()
    val presets by CustomIslandPresetStore.presets.collectAsState()
    val activity = LocalActivity.current
    val slideTravel = with(LocalDensity.current) { 18.dp.roundToPx() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingIsland = settings.islands.firstOrNull { it.id == editingId }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var shareIslandId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showImportPreset by rememberSaveable { mutableStateOf(false) }
    var widgetPosition by rememberSaveable { mutableStateOf<CustomIslandPosition?>(null) }
    var iconPosition by rememberSaveable { mutableStateOf<CustomIslandPosition?>(null) }
    var editPosition by rememberSaveable(editingIsland?.id) { mutableStateOf<CustomIslandPosition?>(null) }

    var editComponentId by rememberSaveable(editingIsland?.id) { mutableStateOf<String?>(null) }
    var settingsSection by rememberSaveable(editingIsland?.id) { mutableStateOf<CustomIslandSettingsSection?>(CustomIslandSettingsSection.LAYOUT) }
    var addingComponent by rememberSaveable(editingIsland?.id) { mutableStateOf(false) }

    fun displayInfo(island: CustomIsland): CustomIslandDisplayInfo =
        customIslandDisplayInfo(island) { widgetId -> CustomIslandWidgets.widgetLabels(context, widgetId) }

    LaunchedEffect(editingIsland?.id, editingIsland?.isSwipeDownOnly) {
        if (editingIsland == null && editingId != null) {
            editingId = null
        } else if (editingIsland?.isSwipeDownOnly == true &&
            (editPosition == CustomIslandPosition.LEFT || editPosition == CustomIslandPosition.RIGHT)
        ) {
            editPosition = null
        }
    }

    fun isIslandActive(island: CustomIsland): Boolean = if (island.isSwipeDownOnly) {
        settings.swipeDownIsland?.id == island.id
    } else {
        settings.isActive(island.id)
    }

    fun setIslandActive(island: CustomIsland, enabled: Boolean) {
        if (island.isSwipeDownOnly) {
            CustomIslandStore.setSwipeDownActive(if (enabled) island.id else null)
        } else {
            CustomIslandStore.setIslandActive(island.id, enabled)
        }
    }

    AnimatedContent(
        targetState = editingIsland?.id,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        label = "custom_island_page",
        transitionSpec = {
            val direction = if (targetState != null) 1 else -1
            LarpMotion.horizontalSlideTransition(direction, slideTravel)
        },
    ) { targetIslandId ->
        val pageIsland = targetIslandId?.let { id -> settings.islands.firstOrNull { it.id == id } }
        if (pageIsland == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Custom Islands", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            CustomIslandStore.create()?.let { editingId = it }
                        }) {
                            Icon(LarpIcons.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("New Island")
                        }
                    }
                }

                if (settings.islands.isEmpty()) {
                    item(key = "empty") {
                        LarpCard {
                            Text(
                                "No custom islands",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item(key = "custom_islands_list") {
                        LarpCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                settings.islands.forEachIndexed { index, island ->
                                    val active = isIslandActive(island)
                                    val info = remember(island) { displayInfo(island) }
                                    val itemIcon = island.left.icon?.image
                                        ?: island.right.icon?.image
                                        ?: island.expandedLeft.icon?.image
                                        ?: island.expandedRight.icon?.image
                                        ?: if (island.isSwipeDownOnly) LarpIcons.KeyboardArrowDown else LarpIcons.Apps
                                    LarpListItem(
                                        title = info.title,
                                        subtitle = info.subtitle,
                                        highlighted = active,
                                        rank = index + 1,
                                        icon = itemIcon,
                                        subtitleMaxLines = 2,
                                        onClick = { editingId = island.id },
                                        onMoveUp = if (settings.islands.size > 1) {
                                            { CustomIslandStore.move(island.id, -1) }
                                        } else null,
                                        canMoveUp = index > 0,
                                        onMoveDown = if (settings.islands.size > 1) {
                                            { CustomIslandStore.move(island.id, 1) }
                                        } else null,
                                        canMoveDown = index < settings.islands.lastIndex,
                                        trailing = {
                                            IconButton(
                                                onClick = { shareIslandId = island.id },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    imageVector = LarpIcons.Share,
                                                    contentDescription = "Save or share ${info.title}",
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { deleteId = island.id },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    imageVector = LarpIcons.DeleteOutline,
                                                    contentDescription = "Delete ${info.title}",
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            Switch(
                                                checked = active,
                                                enabled = island.hasContent || active,
                                                onCheckedChange = { setIslandActive(island, it) },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "custom_island_presets") {
                    CustomIslandPresetsCard(
                        presets = presets,
                        onImportClick = { showImportPreset = true },
                        onApplyPreset = { preset ->
                            CustomIslandStore.addFromPreset(preset)?.let { editingId = it }
                        },
                        onSharePreset = { preset -> sharePresetId = preset.id },
                        onDeletePreset = { preset -> deletePresetId = preset.id },
                    )
                }
            }
        } else {
            val active = isIslandActive(pageIsland)
            val info = remember(pageIsland) { displayInfo(pageIsland) }
            val otherSwipeDown = settings.existingSwipeDownIsland?.takeUnless { it.id == pageIsland.id }
            val otherSwipeDownTitle = remember(otherSwipeDown) { otherSwipeDown?.let(::displayInfo)?.title }
            val editorScroll = rememberLazyListState()
            val selectedComponent = pageIsland.expandedComponents.firstOrNull { it.id == editComponentId }
            val inspectorHeaderHeight = with(LocalDensity.current) { 56.dp.roundToPx() }
            LaunchedEffect(selectedComponent?.id) {
                if (selectedComponent == null) return@LaunchedEffect
                withFrameNanos { }
                val layout = editorScroll.layoutInfo
                val inspector = layout.visibleItemsInfo.firstOrNull { it.key == "component:${selectedComponent.id}" }
                if (inspector != null && layout.viewportEndOffset - inspector.offset < inspectorHeaderHeight) {
                    editorScroll.animateScrollToItem(inspector.index)
                } else if (inspector == null) {
                    val preview = layout.visibleItemsInfo.firstOrNull { it.key == "preview" }
                    if (preview != null) editorScroll.animateScrollToItem(preview.index + 1)
                }
            }
            BackHandler { editingId = null }
            Column(modifier = Modifier.fillMaxSize()) {
                LarpSubPageTopBar(
                    title = info.title,
                    subtitle = info.subtitle,
                    onBack = { editingId = null },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { shareIslandId = pageIsland.id }) {
                                Icon(LarpIcons.Share, contentDescription = "Save or share preset")
                            }
                            Switch(
                                checked = active,
                                enabled = pageIsland.hasContent || active,
                                onCheckedChange = { setIslandActive(pageIsland, it) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    },
                )
                LazyColumn(
                    state = editorScroll,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "settings_sections") {
                        LarpConnectedButtons(
                            options = CustomIslandSettingsSection.entries.map { section ->
                                LarpButtonOption(section.label, checked = settingsSection == section) { checked ->
                                    settingsSection = section.takeIf { checked }
                                }
                            },
                            singleSelection = false,
                        )
                    }
                    settingsSection?.let { section ->
                        item(key = "active_settings") {
                            when (section) {
                                CustomIslandSettingsSection.LAYOUT -> CustomIslandDimensionSettings(pageIsland)
                                CustomIslandSettingsSection.VISIBILITY -> CustomIslandVisibilitySettings(pageIsland, otherSwipeDownTitle)
                                CustomIslandSettingsSection.TAP -> CustomIslandTapSettings(pageIsland)
                            }
                        }
                    }
                    item(key = "preview") {
                        CustomIslandPreview(
                            island = pageIsland,
                            cutoutInfo = cutoutInfo,
                            selected = editPosition,
                            selectedComponentId = editComponentId,
                            onSelectComponent = { id ->
                                editPosition = null
                                editComponentId = if (editComponentId == id) null else id
                            },
                            onAddComponent = { addingComponent = true },
                            onSelect = { position ->
                                editComponentId = null
                                editPosition = if (editPosition == position) null else position
                            },
                        )
                    }
                    editPosition?.let { position ->
                        item(key = "${pageIsland.id}/${position.name}") {
                            CustomIslandSlotSettings(
                                pageIsland, position,
                                onDismiss = { editPosition = null },
                                onChooseWidget = { widgetPosition = position },
                                onChooseIcon = { iconPosition = position },
                            )
                        }
                    }
                    selectedComponent?.let { component ->
                        item(key = "component:${component.id}") {
                            CustomIslandComponentInspector(pageIsland, component, onDismiss = { editComponentId = null })
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        val island = settings.islands.firstOrNull { it.id == id }
        if (island != null) {
            val info = remember(island) { displayInfo(island) }
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text(if (island.hasContent) "Delete ${info.title}?" else "Delete island?") },
                text = { Text("This removes the island and its configured widgets.") },
                confirmButton = {
                    TextButton(onClick = {
                        if (editingId == id) editingId = null
                        CustomIslandStore.remove(id)
                        deleteId = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
            )
        }
    }

    shareIslandId?.let { id ->
        val island = settings.islands.firstOrNull { it.id == id }
        if (island != null) {
            val info = remember(island) { displayInfo(island) }
            ShareOrSavePresetDialog(
                sourceIsland = island,
                initialName = island.name.ifBlank { info.title },
                allowSave = true,
                onDismiss = { shareIslandId = null },
            )
        } else {
            shareIslandId = null
        }
    }

    sharePresetId?.let { id ->
        val preset = presets.firstOrNull { it.id == id }
        if (preset != null) {
            ShareOrSavePresetDialog(
                sourceIsland = preset.island,
                initialName = preset.name,
                initialDescription = preset.description,
                allowSave = false,
                onDismiss = { sharePresetId = null },
            )
        } else {
            sharePresetId = null
        }
    }

    deletePresetId?.let { id ->
        val preset = presets.firstOrNull { it.id == id }
        if (preset != null) {
            AlertDialog(
                onDismissRequest = { deletePresetId = null },
                title = { Text("Delete ${preset.name}?") },
                text = { Text("This removes the saved preset. Existing custom islands created from it stay unchanged.") },
                confirmButton = {
                    TextButton(onClick = {
                        CustomIslandPresetStore.remove(id)
                        deletePresetId = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deletePresetId = null }) { Text("Cancel") } },
            )
        } else {
            deletePresetId = null
        }
    }

    if (showImportPreset) {
        ImportPresetDialog(
            onDismiss = { showImportPreset = false },
            onSaveOnly = { preset ->
                CustomIslandPresetStore.addImported(preset)
            },
            onImportAndAddIsland = { preset ->
                CustomIslandPresetStore.addImported(preset)
                CustomIslandStore.addFromPreset(preset)?.let { editingId = it }
            },
        )
    }

    if (editingIsland != null && addingComponent) {
        AlertDialog(
            onDismissRequest = { addingComponent = false },
            title = { Text("Add component") },
            text = {
                Column {
                    CustomComponentKind.entries.forEach { kind ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                addingComponent = false
                                if (kind == CustomComponentKind.WIDGET) {
                                    widgetPosition = CustomIslandPosition.MAIN
                                } else {
                                    val component = CustomIslandComponent(kind = kind)
                                    CustomIslandStore.editComponents(editingIsland.id) { it + component }
                                    editPosition = null
                                    editComponentId = component.id
                                }
                            },
                        ) { Text(kind.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addingComponent = false }) { Text("Cancel") } },
        )
    }

    val chooseWidget = widgetPosition
    if (editingIsland != null && chooseWidget != null) {
        CustomWidgetPicker(
            onDismiss = { widgetPosition = null },
            onPick = { provider ->
                widgetPosition = null
                activity?.let { CustomIslandWidgets.bind(it, editingIsland.id, chooseWidget, provider) }
            },
        )
    }

    val chooseIcon = iconPosition
    if (editingIsland != null && chooseIcon != null) {
        val slot = editingIsland.slot(chooseIcon)
        AlertDialog(
            onDismissRequest = { iconPosition = null },
            title = { Text("Choose an Icon") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(CustomIslandIcon.entries.sortedBy { it.label }, key = { it.name }) { icon ->
                        val checked = slot.icon == icon
                        Surface(
                            selected = checked,
                            onClick = {
                                CustomIslandStore.configureSlot(
                                    editingIsland.id,
                                    chooseIcon,
                                    slot.copy(icon = icon, widgetId = null, image = null, text = null),
                                )
                                iconPosition = null
                            },
                            shape = CircleShape,
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon.image, contentDescription = icon.label, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { iconPosition = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CustomIslandVisibilitySettings(island: CustomIsland, otherSwipeDownTitle: String?) {
    LarpCard {
        Text("Show Island", style = MaterialTheme.typography.titleMedium)
        LarpConnectedButtons(
            options = CustomIslandVisibility.entries.map { visibility ->
                val enabled = visibility != CustomIslandVisibility.SWIPE_DOWN || otherSwipeDownTitle == null
                LarpButtonOption(
                    label = visibility.label,
                    checked = island.visibility == visibility,
                    enabled = enabled,
                ) {
                    if (enabled) CustomIslandStore.setVisibility(island.id, visibility)
                }
            },
            maxButtonsPerRow = 2,
        )
        if (otherSwipeDownTitle != null && !island.isSwipeDownOnly) {
            Text(
                "Only one swipe-down island is allowed ($otherSwipeDownTitle).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (island.visibility) {
            CustomIslandVisibility.ALWAYS -> Text(
                "Always shown when the overlay is enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CustomIslandVisibility.IDLE -> Text(
                "Shown when no other island is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CustomIslandVisibility.SWIPE_DOWN -> Text(
                "Opens expanded when swiping down from the camera cutout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CustomIslandVisibility.CONDITIONAL -> {
                Text(
                    "Show when active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LarpConnectedButtons(CustomIslandCondition.entries.map { condition ->
                    LarpButtonOption(condition.label, checked = island.condition == condition) {
                        CustomIslandStore.setCondition(island.id, condition)
                    }
                })
                when (island.condition) {
                    CustomIslandCondition.LIVE_ACTIVITY -> CustomLiveActivitySelector(island)
                    CustomIslandCondition.NOTIFICATION -> CustomNotificationSettings(island)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CustomIslandSlotSettings(
    island: CustomIsland,
    position: CustomIslandPosition,
    onDismiss: () -> Unit,
    onChooseWidget: () -> Unit,
    onChooseIcon: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val slot = island.slot(position)
    val widget = slot.widgetId
    val provider = remember(widget) { widget?.let(CustomIslandWidgets::info) }
    var showColorKey by rememberSaveable(widget) { mutableStateOf(false) }
    val (dominantColor, dominantLabel) = rememberCustomIslandDominantColor(island)
    LarpCard {
        val icon = when (position) {
            CustomIslandPosition.LEFT, CustomIslandPosition.EXPANDED_LEFT -> LarpIcons.ArrowBack
            CustomIslandPosition.RIGHT, CustomIslandPosition.EXPANDED_RIGHT -> LarpIcons.ArrowForwardIos
            CustomIslandPosition.MAIN -> LarpIcons.OpenInFull
        }
        IslandPreviewInspectorHeader(position.label, icon, onDismiss)
        val options = buildList {
            add(LarpButtonOption("None", checked = !slot.hasContent && slot.text == null) {
                CustomIslandStore.configureSlot(island.id, position, slot.copy(icon = null, widgetId = null, image = null, text = null))
            })
            if (position != CustomIslandPosition.MAIN) {
                add(LarpButtonOption("Icon", checked = slot.icon != null, onCheckedChange = { onChooseIcon() }))
                add(LarpButtonOption("Text", checked = slot.text != null) {
                    CustomIslandStore.configureSlot(
                        island.id,
                        position,
                        slot.copy(icon = null, widgetId = null, image = null, text = slot.text ?: ""),
                    )
                })
            }
            if (!position.isExpandedWing) {
                add(LarpButtonOption("Widget", checked = widget != null, onCheckedChange = { onChooseWidget() }))
            }
            CustomIslandImage.entries.forEach { image ->
                add(LarpButtonOption(image.label, checked = slot.image == image) {
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(icon = null, widgetId = null, image = image, text = null))
                })
            }
        }
        LarpConnectedButtons(options, scrollable = true)
        if (slot.text != null) {
            OutlinedTextField(
                value = slot.text,
                onValueChange = { value ->
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(text = value.take(120)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text") },
                singleLine = !position.isExpandedWing,
            )
            val placeholders = customIslandPlaceholders(island)
            if (placeholders.isNotEmpty()) {
                FlowRow {
                    placeholders.forEach { name ->
                        TextButton(onClick = {
                            val separator = if (slot.text.isBlank()) "" else " "
                            CustomIslandStore.configureSlot(
                                island.id,
                                position,
                                slot.copy(text = (slot.text + separator + "{$name}").take(120)),
                            )
                        }) { Text("{$name}") }
                    }
                }
            }
            var textWidth by remember(position, slot.width) { mutableFloatStateOf(slot.width.toFloat()) }
            LarpSliderRow(
                title = "Wing Width",
                value = textWidth,
                valueLabel = "${textWidth.roundToInt()} dp",
                valueRange = position.minWidth.toFloat()..position.maxWidth.toFloat(),
                onValueChange = { textWidth = it },
                onValueChangeFinished = {
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(width = textWidth.roundToInt()))
                },
            )
            CustomTextFormatSettings(
                style = slot.textStyle,
                dominantColor = dominantColor,
                dominantLabel = dominantLabel,
            ) { style ->
                CustomIslandStore.configureSlot(island.id, position, slot.copy(textStyle = style))
            }
        } else if (widget != null) {
            Text(
                provider?.loadLabel(context.packageManager) ?: "Widget unavailable",
                style = MaterialTheme.typography.titleSmall,
            )
            if (provider?.configure != null && activity != null) {
                TextButton(onClick = { CustomIslandWidgets.reconfigure(activity, widget) }) {
                    Text("Configure Widget")
                }
            }
            if (provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TextButton(onClick = { showColorKey = true }) {
                    Text(if (slot.colorKey == null) "Color to Transparency" else "Edit Transparency Filter")
                }
            }
            var width by remember(widget, slot.width) { mutableFloatStateOf(slot.width.toFloat()) }
            LarpSliderRow(
                title = "Widget Width",
                value = width,
                valueLabel = "${width.roundToInt()} dp",
                valueRange = position.minWidth.toFloat()..position.maxWidth.toFloat(),
                onValueChange = { width = it },
                onValueChangeFinished = {
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(width = width.roundToInt()))
                },
            )
            val isDefaultSize = slot.width == position.defaultWidth
            if (!isDefaultSize) {
                TextButton(
                    onClick = {
                        CustomIslandStore.configureSlot(
                            island.id,
                            position,
                            slot.copy(
                                width = position.defaultWidth,
                            ),
                        )
                    },
                ) {
                    Text("Reset Widget Size")
                }
            }
        } else if (slot.image == CustomIslandImage.NOTIFICATION_ICON) {
            LarpColorPickerButton(
                label = "Icon Color",
                selection = slot.iconColor,
                dominantColor = dominantColor,
                dominantLabel = dominantLabel,
                onSelectionChange = { color ->
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(iconColor = color))
                },
            )
        } else if (slot.icon != null) {
            Text(slot.icon.label, style = MaterialTheme.typography.titleSmall)
            LarpColorPickerButton(
                label = "Icon Color",
                selection = slot.iconColor,
                dominantColor = dominantColor,
                dominantLabel = dominantLabel,
                onSelectionChange = { color ->
                    CustomIslandStore.configureSlot(island.id, position, slot.copy(iconColor = color))
                },
            )
        }
    }
    if (showColorKey && widget != null && provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        WidgetColorKeyDialog(widget, slot.colorKey, onDismiss = { showColorKey = false }) { filter ->
            CustomIslandStore.setWidgetColorKey(island.id, position, widget, filter)
            showColorKey = false
        }
    }
}

@Composable
private fun CustomWidgetPicker(onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val widgets = remember {
        val packages = context.packageManager
        CustomIslandWidgets.providers().map { provider ->
            val packageName = provider.provider.packageName
            val app = runCatching { packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString() }
                .getOrDefault(packageName)
            val label = provider.loadLabel(packages).orEmpty().ifBlank { app }
            Triple(provider, label, app)
        }.sortedWith(compareBy({ it.third.lowercase() }, { it.second.lowercase() }))
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = widgets.filter { (_, label, app) ->
        query.isBlank() || label.contains(query.trim(), ignoreCase = true) || app.contains(query.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a Widget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (widgets.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search widgets") },
                        singleLine = true,
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(LarpIcons.Clear, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filtered.isEmpty()) {
                    Text(if (widgets.isEmpty()) "No installed apps provide widgets." else "No matching widgets.")
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.first.provider.flattenToString() }) { (provider, label, app) ->
                            Surface(
                                onClick = { onPick(provider) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    WidgetProviderPreview(provider, Modifier.fillMaxWidth().height(104.dp))
                                    Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (app != label) Text(app, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
