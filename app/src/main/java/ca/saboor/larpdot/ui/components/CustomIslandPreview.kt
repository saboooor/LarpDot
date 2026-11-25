package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import ca.saboor.larpdot.service.CustomIslandPosition
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CameraCover
import ca.saboor.larpdot.ui.overlay.CustomIslandWidget
import ca.saboor.larpdot.ui.overlay.CustomIslandWings
import ca.saboor.larpdot.ui.overlay.customIslandCardPadding
import ca.saboor.larpdot.ui.overlay.customIslandCompactExtra
import ca.saboor.larpdot.ui.overlay.customIslandExpandedPadding
import ca.saboor.larpdot.ui.overlay.customWidgetWingInset
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.secondaryBubbleIconSize
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
internal fun CustomIslandPreview(
    island: CustomIsland,
    cutoutInfo: CutoutInfo,
    selected: CustomIslandPosition?,
    onSelect: (CustomIslandPosition) -> Unit,
) {
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
        val cardWidth = (island.main.width.dp + if (spanFull) 0.dp else customIslandCardPadding * 2).coerceAtMost(maxWidth)
        val cameraTop = (with(density) { cutoutInfo.centerY.toDp() } - diameter / 2 - 14.dp).coerceAtLeast(0.dp)
        val cardPadding = customIslandExpandedPadding(cameraTop + diameter, spanFull)
        val cardHeight = island.main.height.dp + cardPadding.calculateTopPadding() + cardPadding.calculateBottomPadding()
        val compactShape = RoundedCornerShape(diameter / 2)
        val cardShape = previewCardShape(cornerRadius.dp.coerceAtMost(minOf(cardWidth, cardHeight) / 2), squircle, blur)
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
                                CustomWingPreview(island.left, CustomIslandPosition.LEFT, diameter, selected, onSelect)
                            },
                            right = {
                                CustomWingPreview(island.right, CustomIslandPosition.RIGHT, diameter, selected, onSelect)
                            },
                        )
                        PreviewCameraCutoutDot(diameter)
                    }
                }
            }

            val highlight = selected == CustomIslandPosition.MAIN
            Surface(
                modifier = Modifier.width(cardWidth).height(cardHeight).border(
                    if (highlight) 1.5.dp else 0.75.dp,
                    if (highlight) MaterialTheme.colorScheme.primary else outline,
                    cardShape,
                ),
                shape = cardShape,
                color = Color.Black.copy(alpha = opacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable(
                        onClickLabel = "Edit expanded island", role = Role.Button,
                    ) { onSelect(CustomIslandPosition.MAIN) },
                ) {
                    CameraCover(
                        style = cameraCoverStyle,
                        centerX = cardWidth / 2f,
                        centerY = cameraTop + diameter / 2f,
                        diameter = diameter,
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().padding(cardPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CustomSlotPreview(island.main, modifier = Modifier.fillMaxSize())
                    }
                    PreviewCameraCutoutDot(diameter, Modifier.align(Alignment.TopCenter).offset(y = cameraTop))
                }
            }
        }
    }
}

@Composable
private fun CustomWingPreview(
    slot: CustomIslandSlot,
    position: CustomIslandPosition,
    diameter: Dp,
    selected: CustomIslandPosition?,
    onSelect: (CustomIslandPosition) -> Unit,
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
        )
    }
}

@Composable
private fun CustomSlotPreview(
    slot: CustomIslandSlot,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val widgetId = slot.widgetId
    val provider = remember(widgetId) { widgetId?.let(CustomIslandWidgets::info) }
    val iconModifier = Modifier.size(iconSize)
    when {
        widgetId != null && provider != null -> CustomIslandWidget(
            widgetId = widgetId,
            interactive = false,
            modifier = modifier,
            colorKey = slot.colorKey,
        )
        slot.icon != null -> Icon(slot.icon.image, slot.icon.label, iconModifier, MaterialTheme.colorScheme.primary)
        widgetId != null -> Icon(LarpIcons.Apps, "Widget unavailable", iconModifier, MaterialTheme.colorScheme.outline)
        else -> Icon(LarpIcons.Add, "Empty", iconModifier, MaterialTheme.colorScheme.outline)
    }
}
