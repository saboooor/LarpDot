package ca.saboor.larpdot.ui.screens

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.customIslandDisplayInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpListItem
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
    val hideInRelatedApp by OverlayPreferences.hideInRelatedAppFlow.collectAsState()
    val usageAccessGranted by ForegroundAppTracker.hasUsageAccess.collectAsState()
    val islandPriorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
    val customIslandSettings by CustomIslandStore.settings.collectAsState()
    val appBlacklistEnabled by OverlayPreferences.appBlacklistEnabledFlow.collectAsState()
    val blacklistedPackages by OverlayPreferences.blacklistedPackagesFlow.collectAsState()
    var showBlacklistScreen by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        ForegroundAppTracker.refreshUsageAccess(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ForegroundAppTracker.refreshUsageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        OverlayPreferences.isHideInRelatedAppEnabled(context)
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
                config = config,
                defaultDiameterDp = defaultDiameterDp,
                onConfigChange = { newConfig ->
                    OverlayPreferences.setCutoutConfig(context, newConfig)
                },
            )
        }
        item {
            val priorityOrder = islandPriorityOrderWithCustom(islandPriorityOrder, customIslandSettings.islands)
            val defaultPriorityOrder = islandPriorityOrderWithCustom(emptyList(), customIslandSettings.islands)
            SectionHeader(
                title = "Island Priority",
                trailing = if (priorityOrder != defaultPriorityOrder) {
                    {
                        TextButton(onClick = { OverlayPreferences.setIslandPriorityOrder(context, defaultPriorityOrder) }) {
                            Text("Reset")
                        }
                    }
                } else null,
            )
            IslandPriorityCard(
                order = priorityOrder,
                customIslands = customIslandSettings.islands,
                onOrderChange = { OverlayPreferences.setIslandPriorityOrder(context, it) },
            )
        }
        item {
            SectionHeader(title = "Behavior")
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
                hideInRelatedApp = hideInRelatedApp,
                usageAccessGranted = usageAccessGranted,
                appBlacklistEnabled = appBlacklistEnabled,
                blacklistedCount = blacklistedPackages.size,
                onOpenBlacklist = { showBlacklistScreen = true },
            )
        }
    }
}

@Composable
private fun IslandPriorityCard(
    order: List<IslandPriorityItem>,
    customIslands: List<CustomIsland>,
    onOrderChange: (List<IslandPriorityItem>) -> Unit,
) {
    LarpCard {
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
                LarpListItem(
                    title = label,
                    subtitle = item.subtitle,
                    highlighted = isTop,
                    rank = index + 1,
                    icon = itemIcon,
                    onMoveUp = {
                        if (index > 0) {
                            val updated = order.toMutableList()
                            updated.add(index - 1, updated.removeAt(index))
                            onOrderChange(updated)
                        }
                    },
                    canMoveUp = index > 0,
                    onMoveDown = {
                        if (index < order.lastIndex) {
                            val updated = order.toMutableList()
                            updated.add(index + 1, updated.removeAt(index))
                            onOrderChange(updated)
                        }
                    },
                    canMoveDown = index < order.lastIndex,
                )
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
    hideInRelatedApp: Boolean,
    usageAccessGranted: Boolean,
    appBlacklistEnabled: Boolean,
    blacklistedCount: Int,
    onOpenBlacklist: () -> Unit,
) {
    val context = LocalContext.current
    LarpCard {
        CutoutSliderRow(
            label = "Max minimized width",
            value = minimizedMaxWidth,
            valueRange = 40f..100f,
            unit = "%",
            onValueChange = { OverlayPreferences.setMinimizedMaxWidthPercent(context, it) },
            onNudge = { OverlayPreferences.setMinimizedMaxWidthPercent(context, minimizedMaxWidth + it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Tap to Expand",
            subtitle = "Tap expands island, hold opens app",
            icon = LarpIcons.TouchApp,
            checked = tapToExpand,
            onCheckedChange = { OverlayPreferences.setTapToExpandEnabled(context, it) },
        )

        CutoutSliderRow(
            label = "Hold duration",
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

        LarpDivider()

        LarpSwitchRow(
            title = "Haptic Feedback",
            icon = LarpIcons.Vibration,
            checked = hapticsEnabled,
            onCheckedChange = { OverlayPreferences.setHapticsEnabled(context, it) },
        )

        LarpDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Auto-Collapse",
                style = MaterialTheme.typography.titleMedium,
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
            title = "Hide When Screen Is Off",
            icon = LarpIcons.PowerSettingsNew,
            checked = hideWhenScreenOff,
            onCheckedChange = { OverlayPreferences.setHideWhenScreenOffEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide on Lock Screen",
            icon = LarpIcons.Lock,
            checked = hideOnLockScreen,
            onCheckedChange = { OverlayPreferences.setHideOnLockScreenEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Fullscreen",
            subtitle = "Requires Accessibility",
            icon = LarpIcons.Fullscreen,
            checked = hideInFullscreen,
            onCheckedChange = { OverlayPreferences.setHideInFullscreenEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Landscape",
            icon = LarpIcons.ScreenRotation,
            checked = hideInLandscape,
            onCheckedChange = { OverlayPreferences.setHideInLandscapeEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Do Not Disturb",
            icon = LarpIcons.DoNotDisturbOn,
            checked = hideInDnd,
            onCheckedChange = { OverlayPreferences.setHideInDndEnabled(context, it) },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Hide in Related App",
            subtitle = "Hide music, live activities, and custom islands while their app is open",
            icon = LarpIcons.Apps,
            checked = hideInRelatedApp,
            onCheckedChange = { OverlayPreferences.setHideInRelatedAppEnabled(context, it) },
        )

        if (hideInRelatedApp && !usageAccessGranted) {
            Text(
                text = "Allow usage access to detect which app is currently open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { ForegroundAppTracker.openUsageAccessSettings(context) }) {
                Text("Grant usage access")
            }
        }

        LarpDivider()

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
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "App Blacklist", style = MaterialTheme.typography.titleMedium)
                    val statusText = when {
                        !appBlacklistEnabled -> "Off"
                        blacklistedCount == 0 -> "No apps blocked"
                        else -> "$blacklistedCount app${if (blacklistedCount == 1) "" else "s"} blocked"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = LarpIcons.ArrowForwardIos,
                contentDescription = "App Blacklist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CutoutAlignmentCard(
    config: OverlayPreferences.CutoutConfig,
    defaultDiameterDp: Int,
    onConfigChange: (OverlayPreferences.CutoutConfig) -> Unit,
) {
    val context = LocalContext.current
    LarpCard {
        LarpSwitchRow(
            title = "Manual Alignment",
            icon = LarpIcons.Tune,
            checked = config.isManualEnabled,
            onCheckedChange = { onConfigChange(config.copy(isManualEnabled = it)) },
        )

        LarpDivider()

        val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()
        LarpSwitchRow(
            title = "Show Cutout Outline",
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
                CutoutSliderRow(
                    label = "Horizontal Offset",
                    value = config.offsetX,
                    valueRange = -80f..80f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(offsetX = it)) },
                    onNudge = { delta ->
                        val newX = (config.offsetX + delta).coerceIn(-80f, 80f)
                        onConfigChange(config.copy(offsetX = newX))
                    },
                )

                CutoutSliderRow(
                    label = "Vertical Offset",
                    value = config.offsetY,
                    valueRange = -40f..60f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(offsetY = it)) },
                    onNudge = { delta ->
                        val newY = (config.offsetY + delta).coerceIn(-40f, 60f)
                        onConfigChange(config.copy(offsetY = newY))
                    },
                )

                val currentDiameter = if (config.customDiameterDp > 0f) config.customDiameterDp else defaultDiameterDp.toFloat()
                CutoutSliderRow(
                    label = "Cutout Diameter",
                    value = currentDiameter,
                    valueRange = 16f..40f,
                    unit = "dp",
                    onValueChange = { onConfigChange(config.copy(customDiameterDp = it)) },
                    onNudge = { delta ->
                        val newDiameter = (currentDiameter + delta).coerceIn(16f, 40f)
                        onConfigChange(config.copy(customDiameterDp = newDiameter))
                    },
                )

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
                    Text("Reset to Default")
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
