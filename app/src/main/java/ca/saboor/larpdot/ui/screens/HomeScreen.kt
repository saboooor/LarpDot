package ca.saboor.larpdot.ui.screens

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.customIslandDisplayInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.IslandPriorityItem
import ca.saboor.larpdot.ui.overlay.islandPriorityOrderWithCustom
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    cutoutInfo: CutoutInfo,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val config by OverlayPreferences.cutoutConfigFlow.collectAsState()
    val tapToExpand by OverlayPreferences.tapToExpandFlow.collectAsState()
    val hapticsEnabled by OverlayPreferences.hapticsEnabledFlow.collectAsState()
    val expandedAutoCollapseSeconds by OverlayPreferences.expandedAutoCollapseSecondsFlow.collectAsState()
    val holdToExpandDurationMs by OverlayPreferences.holdToExpandDurationMsFlow.collectAsState()
    val minimizedMaxWidth by OverlayPreferences.minimizedMaxWidthPercent.collectAsState()
    val hideWhenScreenOff by OverlayPreferences.hideWhenScreenOffFlow.collectAsState()
    val hideOnLockScreen by OverlayPreferences.hideOnLockScreenFlow.collectAsState()
    val hideInFullscreen by OverlayPreferences.hideInFullscreenFlow.collectAsState()
    val hideInLandscape by OverlayPreferences.hideInLandscapeFlow.collectAsState()
    val hideInDnd by OverlayPreferences.hideInDndFlow.collectAsState()
    val islandPriorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
    val customIslandSettings by CustomIslandStore.settings.collectAsState()
    val appBlacklistEnabled by OverlayPreferences.appBlacklistEnabledFlow.collectAsState()
    val blacklistedPackages by OverlayPreferences.blacklistedPackagesFlow.collectAsState()
    var showBlacklistScreen by rememberSaveable { mutableStateOf(false) }

    if (showBlacklistScreen) {
        AppBlacklistScreen(
            onBack = { showBlacklistScreen = false },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.isTapToExpandEnabled(context)
        OverlayPreferences.getHoldToExpandDurationMs(context)
        OverlayPreferences.loadIslandAppearance(context)
        OverlayPreferences.isDebugModeEnabled(context)
        OverlayPreferences.isAppBlacklistEnabled(context)
        OverlayPreferences.getBlacklistedPackages(context)
        OverlayPreferences.isHideWhenScreenOffEnabled(context)
        OverlayPreferences.isHideOnLockScreenEnabled(context)
        OverlayPreferences.isHideInFullscreenEnabled(context)
        OverlayPreferences.isHideInLandscapeEnabled(context)
        OverlayPreferences.isHideInDndEnabled(context)
        OverlayPreferences.getIslandPriorityOrder(context)
        FlashlightController.init(context)
    }

    val hardwareCutout = remember(configuration) { CutoutDetector.detectHardwareCutout(context) }
    val defaultDiameterDp = with(density) { (hardwareCutout.radiusPx * 2f).toDp() }.value.roundToInt()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(title = "Cutout Alignment")
            CutoutAlignmentCard(
                cutoutInfo = cutoutInfo,
                config = config,
                defaultDiameterDp = defaultDiameterDp,
                onConfigChange = { newConfig ->
                    OverlayPreferences.setCutoutConfig(context, newConfig)
                },
            )
        }
        item {
            SectionHeader(title = "Island Priority")
            IslandPriorityCard(
                islandPriorityOrder = islandPriorityOrder,
                customIslands = customIslandSettings.islands,
                onOrderChange = { OverlayPreferences.setIslandPriorityOrder(context, it) },
            )
        }
        item {
            SectionHeader(title = "Island Behavior")
            IslandBehaviorCard(
                minimizedMaxWidth = minimizedMaxWidth,
                tapToExpand = tapToExpand,
                hapticsEnabled = hapticsEnabled,
                expandedAutoCollapseSeconds = expandedAutoCollapseSeconds,
                holdToExpandDurationMs = holdToExpandDurationMs,
                hideWhenScreenOff = hideWhenScreenOff,
                hideOnLockScreen = hideOnLockScreen,
                hideInFullscreen = hideInFullscreen,
                hideInLandscape = hideInLandscape,
                hideInDnd = hideInDnd,
                appBlacklistEnabled = appBlacklistEnabled,
                blacklistedCount = blacklistedPackages.size,
                onOpenBlacklist = { showBlacklistScreen = true },
            )
        }
    }
}

@Composable
private fun IslandPriorityCard(
    islandPriorityOrder: List<IslandPriorityItem>,
    customIslands: List<CustomIsland>,
    onOrderChange: (List<IslandPriorityItem>) -> Unit,
) {
    val order = islandPriorityOrderWithCustom(islandPriorityOrder, customIslands)
    val defaultOrder = islandPriorityOrderWithCustom(emptyList(), customIslands)
    LarpCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "When multiple islands are active, higher items stay on the main island and lower ones move to side dots.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (order != defaultOrder) {
                TextButton(onClick = { onOrderChange(defaultOrder) }) {
                    Text("Reset")
                }
            }
        }

        val context = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            order.forEachIndexed { index, item ->
                val isTop = index == 0
                val label = if (item is IslandPriorityItem.Custom) {
                    customIslands.firstOrNull { it.id == item.islandId }?.let { island ->
                        customIslandDisplayInfo(island) { widgetId ->
                            CustomIslandWidgets.widgetLabels(context, widgetId)
                        }.title
                    } ?: item.label
                } else item.label
                val itemIcon = when (item) {
                    IslandPriorityItem.SHORT_TERM_ALERTS -> LarpIcons.BatteryChargingFull
                    IslandPriorityItem.LIVE_ACTIVITIES -> LarpIcons.NotificationsActive
                    IslandPriorityItem.MEDIA -> LarpIcons.MusicNote
                    IslandPriorityItem.PINNED_SHORT_TERM -> LarpIcons.PushPin
                    IslandPriorityItem.NAVIGATION -> LarpIcons.Navigation
                    IslandPriorityItem.FLASHLIGHT -> LarpIcons.FlashlightOn
                    is IslandPriorityItem.Custom -> LarpIcons.Apps
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isTop) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isTop) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTop) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = if (isTop) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isTop) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    ) {
                                        Text(
                                            text = "Main",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val updated = order.toMutableList()
                                        updated.add(index - 1, updated.removeAt(index))
                                        onOrderChange(updated)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.KeyboardArrowUp,
                                    contentDescription = "Move $label up",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index < order.lastIndex) {
                                        val updated = order.toMutableList()
                                        updated.add(index + 1, updated.removeAt(index))
                                        onOrderChange(updated)
                                    }
                                },
                                enabled = index < order.lastIndex,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.KeyboardArrowDown,
                                    contentDescription = "Move $label down",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandBehaviorCard(
    minimizedMaxWidth: Float,
    tapToExpand: Boolean,
    hapticsEnabled: Boolean,
    expandedAutoCollapseSeconds: Int,
    holdToExpandDurationMs: Int,
    hideWhenScreenOff: Boolean,
    hideOnLockScreen: Boolean,
    hideInFullscreen: Boolean,
    hideInLandscape: Boolean,
    hideInDnd: Boolean,
    appBlacklistEnabled: Boolean,
    blacklistedCount: Int,
    onOpenBlacklist: () -> Unit,
) {
    val context = LocalContext.current
    LarpCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CutoutSliderRow(
                label = "Maximum minimized side width", value = minimizedMaxWidth,
                valueRange = 40f..100f, unit = "%",
                onValueChange = { OverlayPreferences.setMinimizedMaxWidthPercent(context, it) },
                onNudge = { OverlayPreferences.setMinimizedMaxWidthPercent(context, minimizedMaxWidth + it) },
            )
            Text(
                "Limits each side independently. 100% allows each side to reach its screen edge, reserving space for nearby bubbles.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LarpDivider()

        LarpSwitchRow(
            title = "Tap to Expand",
            subtitle = "Tap expands island, hold-tap opens player",
            icon = LarpIcons.TouchApp,
            checked = tapToExpand,
            onCheckedChange = { OverlayPreferences.setTapToExpandEnabled(context, it) },
        )

        CutoutSliderRow(
            label = "Hold Duration to Expand",
            value = holdToExpandDurationMs.toFloat(),
            valueRange = OverlayPreferences.MIN_HOLD_TO_EXPAND_DURATION_MS.toFloat()..OverlayPreferences.MAX_HOLD_TO_EXPAND_DURATION_MS.toFloat(),
            unit = "ms",
            onValueChange = { raw ->
                val snapped = ((raw / 25f).roundToInt() * 25)
                OverlayPreferences.setHoldToExpandDurationMs(context, snapped)
            },
            onNudge = { delta ->
                OverlayPreferences.setHoldToExpandDurationMs(context, holdToExpandDurationMs + delta * 25)
            },
        )
        Text(
            text = "How long to press and hold the minimized island before it expands",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Haptic Feedback",
            subtitle = "Vibrate on island taps, holds, swipes, and controls",
            icon = LarpIcons.Vibration,
            checked = hapticsEnabled,
            onCheckedChange = { OverlayPreferences.setHapticsEnabled(context, it) },
        )

        LarpDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Auto-Collapse Expanded Island",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (expandedAutoCollapseSeconds == 0) {
                    "Keep expanded islands open until you tap outside or swipe up"
                } else {
                    "Automatically collapse after ${expandedAutoCollapseSeconds}s of inactivity"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val autoCollapseOptions = listOf(
                0 to "Off",
                5 to "5s",
                10 to "10s",
                15 to "15s",
                30 to "30s",
            )
            LarpConnectedButtons(
                options = autoCollapseOptions.map { (seconds, label) ->
                    LarpButtonOption(
                        checked = expandedAutoCollapseSeconds == seconds,
                        label = label,
                        onCheckedChange = {
                            OverlayPreferences.setExpandedAutoCollapseSeconds(context, seconds)
                        },
                    )
                },
            )
        }

        LarpDivider()

        LarpSwitchRow(
            title = "Hide When Phone Is Off",
            subtitle = "Dismiss the island overlay whenever the screen turns off",
            icon = LarpIcons.PowerSettingsNew,
            checked = hideWhenScreenOff,
            onCheckedChange = { OverlayPreferences.setHideWhenScreenOffEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide on Lock Screen",
            subtitle = "Dismiss the island overlay while the device is locked",
            icon = LarpIcons.Lock,
            checked = hideOnLockScreen,
            onCheckedChange = { OverlayPreferences.setHideOnLockScreenEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Fullscreen & Games",
            subtitle = "Auto-hide during immersive video players and games (requires Accessibility)",
            icon = LarpIcons.Fullscreen,
            checked = hideInFullscreen,
            onCheckedChange = { OverlayPreferences.setHideInFullscreenEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Landscape",
            subtitle = "Automatically hide the island whenever the phone rotates horizontally",
            icon = LarpIcons.ScreenRotation,
            checked = hideInLandscape,
            onCheckedChange = { OverlayPreferences.setHideInLandscapeEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide During Do Not Disturb",
            subtitle = "Pause the island overlay while Do Not Disturb is active",
            icon = LarpIcons.DoNotDisturbOn,
            checked = hideInDnd,
            onCheckedChange = { OverlayPreferences.setHideInDndEnabled(context, it) },
        )

        LarpDivider()

        // App Blacklist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenBlacklist),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = LarpIcons.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(text = "App Blacklist", style = MaterialTheme.typography.titleMedium)
                    val statusText = when {
                        !appBlacklistEnabled -> "Disabled • All apps allowed"
                        blacklistedCount == 0 -> "None ignored • Tap to configure"
                        else -> "$blacklistedCount app${if (blacklistedCount == 1) "" else "s"} ignored • Tap to configure"
                    }
                    Text(
                        text = "Ignore media & notifications from selected apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Icon(
                imageVector = LarpIcons.ArrowForwardIos,
                contentDescription = "Configure App Blacklist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CutoutAlignmentCard(
    cutoutInfo: CutoutInfo,
    config: OverlayPreferences.CutoutConfig,
    defaultDiameterDp: Int,
    onConfigChange: (OverlayPreferences.CutoutConfig) -> Unit,
) {
    val context = LocalContext.current
    LarpCard {
        LarpSwitchRow(
            title = "Manual Alignment",
            subtitle = if (config.isManualEnabled) "Custom offsets active" else "Auto-detected from hardware",
            icon = LarpIcons.Tune,
            checked = config.isManualEnabled,
            onCheckedChange = { onConfigChange(config.copy(isManualEnabled = it)) },
        )

        Spacer(Modifier.height(4.dp))

        // Readout of current computed position
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Current Cutout Anchor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "X: ${cutoutInfo.centerX.toInt()} • Y: ${cutoutInfo.centerY.toInt()} px",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()
        LarpSwitchRow(
            title = "Debug Mode",
            subtitle = "Always show the camera cutout outline",
            icon = LarpIcons.BugReport,
            checked = isDebugMode,
            onCheckedChange = { OverlayPreferences.setDebugModeEnabled(context, it) },
        )

        AnimatedVisibility(
            visible = config.isManualEnabled,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Horizontal Offset (X) Slider
                CutoutSliderRow(
                    label = "Horizontal Offset (X)",
                    value = config.offsetX,
                    valueRange = -80f..80f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(offsetX = it)) },
                    onNudge = { delta ->
                        val newX = (config.offsetX + delta).coerceIn(-80f, 80f)
                        onConfigChange(config.copy(offsetX = newX))
                    },
                )

                // Vertical Offset (Y) Slider
                CutoutSliderRow(
                    label = "Vertical Offset (Y)",
                    value = config.offsetY,
                    valueRange = -40f..60f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(offsetY = it)) },
                    onNudge = { delta ->
                        val newY = (config.offsetY + delta).coerceIn(-40f, 60f)
                        onConfigChange(config.copy(offsetY = newY))
                    },
                )

                // Custom Cutout Diameter Slider
                val currentDiameter = if (config.customDiameterDp > 0f) config.customDiameterDp else defaultDiameterDp.toFloat()
                CutoutSliderRow(
                    label = "Hole Punch Diameter",
                    value = currentDiameter,
                    valueRange = 16f..40f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(customDiameterDp = it)) },
                    onNudge = { delta ->
                        val newDiameter = (currentDiameter + delta).coerceIn(16f, 40f)
                        onConfigChange(config.copy(customDiameterDp = newDiameter))
                    },
                )

                // Reset to Default button
                OutlinedButton(
                    onClick = {
                        onConfigChange(
                            OverlayPreferences.CutoutConfig(
                                isManualEnabled = true,
                                offsetX = 0f,
                                offsetY = 0f,
                                customDiameterDp = defaultDiameterDp.toFloat(),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = LarpIcons.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Offsets to Default")
                }
            }
        }
    }
}

@Composable
internal fun CutoutSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    onNudge: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onNudge(-1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(LarpIcons.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }

            val sliderState = remember(valueRange) {
                SliderState(
                    value = value,
                    trackRange = valueRange,
                )
            }
            sliderState.value = value
            Slider(
                state = sliderState,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )

            FilledTonalIconButton(
                onClick = { onNudge(1) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(LarpIcons.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
        }
    }
}
