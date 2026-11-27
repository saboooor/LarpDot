package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.CustomIslandSources
import ca.saboor.larpdot.service.CustomIslandTapAction
import ca.saboor.larpdot.ui.overlay.customIslandSources
import ca.saboor.larpdot.ui.overlay.CustomIslandImageContent
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CameraCover
import ca.saboor.larpdot.ui.overlay.CustomExpandedWingIcon
import ca.saboor.larpdot.ui.overlay.CustomIslandWidget
import ca.saboor.larpdot.ui.overlay.CustomIslandWings
import ca.saboor.larpdot.ui.overlay.CustomExpandedComponents
import ca.saboor.larpdot.ui.overlay.CustomWingText
import ca.saboor.larpdot.ui.overlay.customIslandCompactExtra
import ca.saboor.larpdot.ui.overlay.customWidgetWingInset
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.rememberCustomIslandDominantColor
import ca.saboor.larpdot.ui.overlay.rememberCustomIslandPlaceholderValues
import ca.saboor.larpdot.ui.overlay.secondaryBubbleIconSize
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
internal fun CustomIslandPreview(
    island: CustomIsland,
    cutoutInfo: CutoutInfo,
    selected: CustomIslandPosition?,
    selectedComponentId: String?,
    onSelectComponent: (String) -> Unit,
    onAddComponent: () -> Unit,
    onSelect: (CustomIslandPosition) -> Unit,
) {
    val sources = customIslandSources(island)
    val dominantColor = rememberCustomIslandDominantColor(island, sources).first
    val placeholderValues = if (island.left.text != null || island.right.text != null ||
        island.expandedLeft.text != null || island.expandedRight.text != null
    ) {
        rememberCustomIslandPlaceholderValues(island, sources, preview = true)
    } else emptyMap()
    val appOverride = island.tapAppPackage.takeIf { island.tapAction == CustomIslandTapAction.OPEN_APP }
    val density = LocalDensity.current
    val diameter = islandCameraGeometry(cutoutInfo.radiusPx, density.density).diameter.dp
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircle by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blur by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val opacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val compactWidth = (diameter + customIslandCompactExtra(island, diameter)).coerceAtMost(maxWidth)
        val spanFull = island.main.spanFullIsland
        val cardWidth = (island.main.width.dp + island.main.paddingX.dp * 2).coerceAtMost(maxWidth)
        val cameraTop = (with(density) { cutoutInfo.centerY.toDp() } - diameter / 2 - 14.dp).coerceAtLeast(0.dp)
        val compactShape = RoundedCornerShape(diameter / 2)
        val cardShape = previewCardShape(cornerRadius.dp, squircle, blur)
        val outline = Color(0x30FFFFFF)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!island.isSwipeDownOnly) {
                Surface(
                    modifier = Modifier.width(compactWidth).height(diameter).border(0.75.dp, outline, compactShape),
                    shape = compactShape,
                    color = Color.Black,
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CustomIslandWings(
                            diameter,
                            island,
                            left = {
                                CustomWingPreview(island.left, CustomIslandPosition.LEFT, diameter, selected, onSelect, sources, placeholderValues, appOverride, dominantColor)
                            },
                            right = {
                                CustomWingPreview(island.right, CustomIslandPosition.RIGHT, diameter, selected, onSelect, sources, placeholderValues, appOverride, dominantColor)
                            },
                        )
                        PreviewCameraCutoutDot(diameter)
                    }
                }
            }

            Surface(
                modifier = Modifier.width(cardWidth).heightIn(max = 360.dp).border(0.75.dp, outline, cardShape),
                shape = cardShape,
                color = Color.Black.copy(alpha = opacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    CameraCover(
                        style = cameraCoverStyle,
                        centerX = cardWidth / 2f,
                        centerY = cameraTop + diameter / 2f,
                        diameter = diameter,
                        modifier = Modifier.matchParentSize(),
                    )
                    val wingTopPadding = if (spanFull) 0.dp else island.main.paddingY.dp
                    val centerTopPadding = if (spanFull) 0.dp else maxOf(island.main.paddingY.dp, cameraTop + diameter)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(
                                start = island.main.paddingX.dp,
                                end = island.main.paddingX.dp,
                                top = if (spanFull) island.main.paddingY.dp else 0.dp,
                                bottom = island.main.paddingY.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CustomExpandedWingPreview(
                            slot = island.expandedLeft,
                            position = CustomIslandPosition.EXPANDED_LEFT,
                            selected = selected,
                            onSelect = onSelect,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            modifier = Modifier.padding(top = wingTopPadding),
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(top = centerTopPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (island.expandedComponents.isNotEmpty()) {
                                CustomExpandedComponents(
                                    island,
                                    Modifier.weight(1f, fill = false).fillMaxWidth(),
                                    interactive = false,
                                    preview = true,
                                    selectedComponentId = selectedComponentId,
                                    onSelectComponent = onSelectComponent,
                                )
                            }
                            FilledTonalIconButton(onClick = onAddComponent) {
                                Icon(LarpIcons.Add, contentDescription = "Add component")
                            }
                        }
                        CustomExpandedWingPreview(
                            slot = island.expandedRight,
                            position = CustomIslandPosition.EXPANDED_RIGHT,
                            selected = selected,
                            onSelect = onSelect,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            modifier = Modifier.padding(top = wingTopPadding),
                        )
                    }
                    PreviewCameraCutoutDot(diameter, Modifier.align(Alignment.TopCenter).offset(y = cameraTop))
                }
            }
        }
    }
}

@Composable
private fun CustomExpandedWingPreview(
    slot: CustomIslandSlot,
    position: CustomIslandPosition,
    selected: CustomIslandPosition?,
    onSelect: (CustomIslandPosition) -> Unit,
    sources: CustomIslandSources?,
    placeholderValues: Map<String, String>,
    appOverride: String?,
    dominantColor: Color?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val highlight = selected == position
    val borderColor = if (highlight) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF)
    val wingWidth = if (slot.text != null) slot.width.coerceAtLeast(48).dp else 48.dp
    Box(
        modifier = modifier
            .width(wingWidth)
            .height(48.dp)
            .border(if (highlight) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClickLabel = "Edit ${position.label.lowercase()}", role = Role.Button) { onSelect(position) },
        contentAlignment = Alignment.Center,
    ) {
        CustomExpandedWingIcon(
            slot = slot,
            sources = sources,
            appOverride = appOverride,
            preview = true,
            placeholderValues = placeholderValues,
            dominantColor = dominantColor,
        )
    }
}

@Composable
private fun CustomWingPreview(
    slot: CustomIslandSlot,
    position: CustomIslandPosition,
    diameter: Dp,
    selected: CustomIslandPosition?,
    onSelect: (CustomIslandPosition) -> Unit,
    sources: CustomIslandSources?,
    placeholderValues: Map<String, String>,
    appOverride: String?,
    dominantColor: Color?,
) {
    val shape = RoundedCornerShape(diameter / 2)
    val highlight = selected == position
    Box(
        modifier = Modifier.fillMaxSize()
            .border(1.5.dp, if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClickLabel = "Edit ${position.label.lowercase()}", role = Role.Button) { onSelect(position) },
        contentAlignment = if (position == CustomIslandPosition.LEFT) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        CustomSlotPreview(
            slot = slot,
            modifier = Modifier.width(slot.width.dp).fillMaxHeight().padding(vertical = customWidgetWingInset),
            iconSize = secondaryBubbleIconSize(diameter),
            sources = sources,
            placeholderValues = placeholderValues,
            appOverride = appOverride,
            dominantColor = dominantColor,
        )
    }
}

@Composable
private fun CustomSlotPreview(
    slot: CustomIslandSlot,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    sources: CustomIslandSources?,
    placeholderValues: Map<String, String>,
    appOverride: String?,
    dominantColor: Color?,
) {
    val widgetId = slot.widgetId
    val provider = remember(widgetId) { widgetId?.let(CustomIslandWidgets::info) }
    val iconModifier = Modifier.size(iconSize)
    val iconTint = resolveCustomIslandColor(slot.iconColor, MaterialTheme.colorScheme.primary, dominantColor)
    when {
        widgetId != null && provider != null -> CustomIslandWidget(
            widgetId = widgetId,
            interactive = false,
            modifier = modifier,
            colorKey = slot.colorKey,
        )
        slot.text != null -> CustomWingText(
            slot = slot,
            placeholderValues = placeholderValues,
            preview = true,
            maxLines = 1,
            dominantColor = dominantColor,
            modifier = Modifier.widthIn(max = slot.width.dp),
        )
        slot.image != null && sources != null -> CustomIslandImageContent(
            slot.image,
            sources,
            iconModifier,
            compact = true,
            preview = true,
            appOverride = appOverride,
            tint = if (slot.image == CustomIslandImage.NOTIFICATION_ICON) iconTint else null,
        )
        slot.icon != null -> Icon(slot.icon.image, slot.icon.label, iconModifier, iconTint)
        widgetId != null -> Icon(LarpIcons.Apps, "Widget unavailable", iconModifier, MaterialTheme.colorScheme.outline)
        else -> Icon(LarpIcons.Add, "Empty", iconModifier, MaterialTheme.colorScheme.outline)
    }
}
