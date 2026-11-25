package ca.saboor.larpdot.ui.screens

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandDisplayInfo
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.customIslandDisplayInfo
import ca.saboor.larpdot.ui.components.CustomIslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSubPageTopBar
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.WidgetProviderPreview
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

@Composable
internal fun CustomIslandScreen(cutoutInfo: CutoutInfo) {
    val context = LocalContext.current
    val settings by CustomIslandStore.settings.collectAsState()
    val activity = LocalActivity.current
    val slideTravel = with(LocalDensity.current) { 18.dp.roundToPx() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingIsland = settings.islands.firstOrNull { it.id == editingId }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetPosition by rememberSaveable { mutableStateOf<CustomIslandPosition?>(null) }
    var iconPosition by rememberSaveable { mutableStateOf<CustomIslandPosition?>(null) }
    var editPosition by rememberSaveable(editingIsland?.id) { mutableStateOf<CustomIslandPosition?>(null) }

    fun displayInfo(island: CustomIsland): CustomIslandDisplayInfo =
        customIslandDisplayInfo(island) { widgetId -> CustomIslandWidgets.widgetLabels(context, widgetId) }

    LaunchedEffect(editingIsland?.id, editingIsland?.isSwipeDownOnly) {
        if (editingIsland == null && editingId != null) {
            editingId = null
        } else if (editingIsland?.isSwipeDownOnly == true && editPosition != null && editPosition != CustomIslandPosition.MAIN) {
            editPosition = CustomIslandPosition.MAIN
        }
    }

    fun isIslandActive(island: CustomIsland): Boolean = if (island.isSwipeDownOnly) {
        settings.swipeDownIsland?.id == island.id
    } else {
        settings.activeIsland?.id == island.id
    }

    fun setIslandActive(island: CustomIsland, enabled: Boolean) {
        if (island.isSwipeDownOnly) {
            CustomIslandStore.setSwipeDownActive(if (enabled) island.id else null)
        } else {
            CustomIslandStore.setActive(if (enabled) island.id else null)
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
                                "No custom islands yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Tap New Island to build a custom minimized pill, expanded widget card, or swipe-down island.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(settings.islands, key = { it.id }) { island ->
                    val active = isIslandActive(island)
                    val info = remember(island) { displayInfo(island) }
                    LarpCard(
                        onClick = { editingId = island.id },
                        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = info.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { deleteId = island.id },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.DeleteOutline,
                                    contentDescription = "Delete ${info.title}",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Switch(
                                checked = active,
                                enabled = island.hasContent || active,
                                onCheckedChange = { setIslandActive(island, it) },
                            )
                        }
                    }
                }
            }
        } else {
            val active = isIslandActive(pageIsland)
            val info = remember(pageIsland) { displayInfo(pageIsland) }
            val otherSwipeDown = settings.existingSwipeDownIsland?.takeUnless { it.id == pageIsland.id }
            val otherSwipeDownTitle = remember(otherSwipeDown) { otherSwipeDown?.let(::displayInfo)?.title }
            BackHandler { editingId = null }
            Column(modifier = Modifier.fillMaxSize()) {
                LarpSubPageTopBar(
                    title = info.title,
                    subtitle = info.subtitle,
                    onBack = { editingId = null },
                    trailing = {
                        Switch(
                            checked = active,
                            enabled = pageIsland.hasContent || active,
                            onCheckedChange = { setIslandActive(pageIsland, it) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "visibility") {
                        CustomIslandVisibilitySettings(pageIsland, otherSwipeDownTitle)
                    }
                    item(key = "preview") {
                        Text(
                            if (pageIsland.isSwipeDownOnly) "Tap the expanded island to edit it."
                            else "Tap a wing or the expanded island to edit it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        CustomIslandPreview(pageIsland, cutoutInfo, editPosition) { position ->
                            editPosition = if (editPosition == position) null else position
                        }
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
                    item(key = "actions") {
                        val islandIndex = settings.islands.indexOfFirst { it.id == pageIsland.id }
                        val canMoveUp = islandIndex > 0
                        val canMoveDown = islandIndex in 0 until settings.islands.lastIndex
                        LarpCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = { CustomIslandStore.requestOverlayPreview(pageIsland.id) },
                                    enabled = pageIsland.main.widgetId != null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(LarpIcons.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Test in Island", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (settings.islands.size > 1) {
                                    IconButton(
                                        onClick = { CustomIslandStore.move(pageIsland.id, -1) },
                                        enabled = canMoveUp,
                                    ) {
                                        Icon(LarpIcons.KeyboardArrowUp, contentDescription = "Move island up")
                                    }
                                    IconButton(
                                        onClick = { CustomIslandStore.move(pageIsland.id, 1) },
                                        enabled = canMoveDown,
                                    ) {
                                        Icon(LarpIcons.KeyboardArrowDown, contentDescription = "Move island down")
                                    }
                                }
                                IconButton(onClick = { deleteId = pageIsland.id }) {
                                    Icon(LarpIcons.DeleteOutline, contentDescription = "Delete island")
                                }
                            }
                            if (!pageIsland.isSwipeDownOnly && (pageIsland.left.hasContent || pageIsland.right.hasContent)) {
                                OutlinedButton(
                                    onClick = { CustomIslandStore.swapWings(pageIsland.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(LarpIcons.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Swap Left & Right Wings")
                                }
                            }
                            if (pageIsland.main.widgetId == null) {
                                Text(
                                    "Add an expanded widget to test this island live in the overlay.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                                CustomIslandStore.configureSlot(editingIsland.id, chooseIcon, slot.copy(icon = icon, widgetId = null))
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
                "Show while the island overlay is available. Set its position in Home → Island Priority.",
                style = MaterialTheme.typography.bodySmall,
            )
            CustomIslandVisibility.IDLE -> Text(
                "Show when no built-in island is active.",
                style = MaterialTheme.typography.bodySmall,
            )
            CustomIslandVisibility.SWIPE_DOWN -> Text(
                "Opens directly as an expanded island when you swipe down from the main camera dot. No minimized island is shown.",
                style = MaterialTheme.typography.bodySmall,
            )
            CustomIslandVisibility.CONDITIONAL -> {
                Text("Show when this activity is visible", style = MaterialTheme.typography.bodySmall)
                LarpConnectedButtons(CustomIslandCondition.entries.map { condition ->
                    LarpButtonOption(condition.label, checked = island.condition == condition) {
                        CustomIslandStore.setCondition(island.id, condition)
                    }
                })
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
    LarpCard {
        val icon = when (position) {
            CustomIslandPosition.LEFT -> LarpIcons.ArrowBack
            CustomIslandPosition.RIGHT -> LarpIcons.ArrowForwardIos
            CustomIslandPosition.MAIN -> LarpIcons.OpenInFull
        }
        IslandPreviewInspectorHeader(position.label, icon, onDismiss)
        val options = buildList {
            add(LarpButtonOption("None", checked = !slot.hasContent) {
                CustomIslandStore.configureSlot(island.id, position, slot.copy(icon = null, widgetId = null))
            })
            if (position != CustomIslandPosition.MAIN) {
                add(LarpButtonOption("Icon", checked = slot.icon != null, onCheckedChange = { onChooseIcon() }))
            }
            add(LarpButtonOption("Widget", checked = widget != null, onCheckedChange = { onChooseWidget() }))
        }
        LarpConnectedButtons(options)
        if (widget != null) {
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
            if (position == CustomIslandPosition.MAIN) {
                var height by remember(widget, slot.height) { mutableFloatStateOf(slot.height.toFloat()) }
                LarpSliderRow(
                    title = "Widget Height",
                    value = height,
                    valueLabel = "${height.roundToInt()} dp",
                    valueRange = 48f..300f,
                    onValueChange = { height = it },
                    onValueChangeFinished = {
                        CustomIslandStore.configureSlot(island.id, position, slot.copy(height = height.roundToInt()))
                    },
                )
                LarpSwitchRow(
                    title = "Span Full Island",
                    subtitle = "Fill the entire expanded island, including the camera dot area",
                    checked = slot.spanFullIsland,
                    onCheckedChange = {
                        CustomIslandStore.configureSlot(island.id, position, slot.copy(spanFullIsland = it))
                    },
                )
            }
            val isDefaultSize = slot.width == position.defaultWidth &&
                (position != CustomIslandPosition.MAIN || slot.height == position.defaultHeight)
            if (!isDefaultSize) {
                TextButton(
                    onClick = {
                        CustomIslandStore.configureSlot(
                            island.id,
                            position,
                            slot.copy(
                                width = position.defaultWidth,
                                height = if (position == CustomIslandPosition.MAIN) position.defaultHeight else slot.height,
                            ),
                        )
                    },
                ) {
                    Text("Reset Widget Size")
                }
            }
        } else if (slot.icon != null) {
            Text(slot.icon.label, style = MaterialTheme.typography.titleSmall)
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
