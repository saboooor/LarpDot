package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.FormatSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.FlashlightAmber
import kotlin.math.roundToInt
import ca.saboor.larpdot.ui.theme.LarpMotion

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    cutoutInfo: CutoutInfo,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val islandFontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight by OverlayPreferences.islandFontWeightFlow.collectAsState()
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val config by OverlayPreferences.cutoutConfigFlow.collectAsState()
    val tapToExpand by OverlayPreferences.tapToExpandFlow.collectAsState()
    val holdToExpandDurationMs by OverlayPreferences.holdToExpandDurationMsFlow.collectAsState()
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val minimizedMaxWidth by OverlayPreferences.minimizedMaxWidthPercent.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val blurStatus = ca.saboor.larpdot.ui.overlay.rememberBackdropBlurStatus()
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val hideWhenScreenOff by OverlayPreferences.hideWhenScreenOffFlow.collectAsState()
    val hideOnLockScreen by OverlayPreferences.hideOnLockScreenFlow.collectAsState()
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
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
        OverlayPreferences.getBubbleStyle(context)
        OverlayPreferences.areSmallerBubblesEnabled(context)
        OverlayPreferences.isDebugModeEnabled(context)
        OverlayPreferences.isAppBlacklistEnabled(context)
        OverlayPreferences.getBlacklistedPackages(context)
        OverlayPreferences.isHideWhenScreenOffEnabled(context)
        OverlayPreferences.isHideOnLockScreenEnabled(context)
        OverlayPreferences.isShowDotRightSideInfoEnabled(context)
        FlashlightController.init(context)
    }

    val hardwareCutout = remember(configuration) { CutoutDetector.detectHardwareCutout(context) }
    val defaultDiameterDp = with(density) { (hardwareCutout.radiusPx * 2f).toDp() }.value.roundToInt()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Camera Cutout Information & Manual Alignment Card
            item {
                SectionHeader(title = "Expanded Cutout")
                LarpCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Camera Cover",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                        )
                        Text(
                            text = cameraCoverStyle.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                        ) {
                            OverlayPreferences.CameraCoverStyle.entries.forEach { style ->
                                toggleableItem(
                                    checked = cameraCoverStyle == style,
                                    label = style.label,
                                    onCheckedChange = { OverlayPreferences.setCameraCoverStyle(context, style) },
                                    weight = 1f,
                                )
                            }
                        }
                    }
                }
            }
        item {
            SectionHeader(title = "Typography")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Island Font Style",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                                )
                                Text(
                                    text = "Font family applied to island tracks, artists, and live notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OverlayPreferences.IslandFontStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = islandFontStyle == style,
                                    onClick = { OverlayPreferences.setIslandFontStyle(context, style) },
                                    label = {
                                        Text(
                                            text = style.label,
                                            fontFamily = style.fontFamily,
                                        )
                                    },
                                )
                            }
                        }
                        Text("Font weight: $fontWeight")
                        Slider(
                            value = fontWeight.toFloat(),
                            onValueChange = { OverlayPreferences.setIslandFontWeight(context, (it / 100).roundToInt() * 100) },
                            valueRange = 100f..900f,
                            steps = 7,
                        )
                    }
                }
            }
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

        // Island Customization & Interaction Options
        item {
            SectionHeader(title = "Island Background")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CutoutSliderRow(
                        label = "Expanded corner radius", value = cornerRadius,
                        valueRange = 0f..100f, unit = "dp",
                        onValueChange = { OverlayPreferences.setIslandCornerRadius(context, it) },
                        onNudge = { OverlayPreferences.setIslandCornerRadius(context, cornerRadius + it) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Squircle corners", modifier = Modifier.weight(1f))
                        Switch(
                            checked = squircleEnabled && !blurEnabled,
                            enabled = !blurEnabled,
                            onCheckedChange = { OverlayPreferences.setIslandSquircleEnabled(context, it) },
                        )
                    }
                    AnimatedVisibility(blurEnabled, enter = LarpMotion.reveal, exit = LarpMotion.conceal) {
                        Text("Turn off backdrop blur to enable squircle corners.", style = MaterialTheme.typography.bodySmall)
                    }
                    CutoutSliderRow(
                        label = "Expanded background opacity", value = backgroundOpacity * 100f,
                        valueRange = 0f..100f, unit = "%",
                        onValueChange = { OverlayPreferences.setExpandedBackgroundOpacity(context, it / 100f) },
                        onNudge = { OverlayPreferences.setExpandedBackgroundOpacity(context, backgroundOpacity + it / 100f) },
                    )
                    Text("The minimized island and camera cover stay opaque.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Backdrop blur", modifier = Modifier.weight(1f))
                        Switch(
                            checked = blurEnabled && blurStatus == ca.saboor.larpdot.ui.overlay.BackdropBlurStatus.AVAILABLE,
                            enabled = blurStatus == ca.saboor.larpdot.ui.overlay.BackdropBlurStatus.AVAILABLE,
                            onCheckedChange = { OverlayPreferences.setBackdropBlurEnabled(context, it) },
                        )
                    }
                    Text(blurStatus.description, style = MaterialTheme.typography.bodySmall)
                    AnimatedVisibility(
                        visible = blurEnabled && blurStatus == ca.saboor.larpdot.ui.overlay.BackdropBlurStatus.AVAILABLE,
                        enter = LarpMotion.reveal,
                        exit = LarpMotion.conceal,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CutoutSliderRow(
                                label = "Blur amount", value = blurRadius, valueRange = 5f..120f, unit = "dp",
                                onValueChange = { OverlayPreferences.setBackdropBlurRadius(context, it) },
                                onNudge = { OverlayPreferences.setBackdropBlurRadius(context, blurRadius + it * 5f) },
                            )
                            Text("Lower the background opacity to see the blur.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(title = "Island Customization")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CutoutSliderRow(
                        label = "Maximum minimized side width", value = minimizedMaxWidth,
                        valueRange = 40f..100f, unit = "%",
                        onValueChange = { OverlayPreferences.setMinimizedMaxWidthPercent(context, it) },
                        onNudge = { OverlayPreferences.setMinimizedMaxWidthPercent(context, minimizedMaxWidth + it) },
                    )
                    Text("Limits each side independently. 100% allows each side to reach its screen edge, reserving space for nearby bubbles.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Bubble Style",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                    )
                    Text(
                        text = "Shape of the island and nearby bubbles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        OverlayPreferences.BubbleStyle.entries.forEach { style ->
                            toggleableItem(
                                checked = bubbleStyle == style,
                                label = style.label,
                                onCheckedChange = { OverlayPreferences.setBubbleStyle(context, style) },
                                weight = 1f,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.BubbleChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Smaller Bubbles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Reduce secondary bubble thickness by 20%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = smallerBubbles,
                        onCheckedChange = { OverlayPreferences.setSmallerBubblesEnabled(context, it) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Tap to Expand",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Tap expands island, hold-tap opens player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = tapToExpand,
                        onCheckedChange = { isChecked ->
                            OverlayPreferences.setTapToExpandEnabled(context, isChecked)
                        },
                    )
                }

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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Hide Island When Phone Is Off
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Hide When Phone Is Off",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Dismiss the island overlay whenever the screen turns off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = hideWhenScreenOff,
                        onCheckedChange = { isChecked ->
                            OverlayPreferences.setHideWhenScreenOffEnabled(context, isChecked)
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Hide on Lock Screen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Hide on Lock Screen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Dismiss the island overlay while the device is locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = hideOnLockScreen,
                        onCheckedChange = { isChecked ->
                            OverlayPreferences.setHideOnLockScreenEnabled(context, isChecked)
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Show Right Side Info on Dots (Mini Pill)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Show Right Side Info on Dots",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Show info alongside icons on secondary dots, making them mini pills",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = showDotRightSideInfo,
                        onCheckedChange = { isChecked ->
                            OverlayPreferences.setShowDotRightSideInfoEnabled(context, isChecked)
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // App Blacklist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showBlacklistScreen = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "App Blacklist",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            val count = blacklistedPackages.size
                            val statusText = if (!appBlacklistEnabled) {
                                "Disabled • All apps allowed"
                            } else if (count == 0) {
                                "None ignored • Tap to configure"
                            } else {
                                "$count app${if (count == 1) "" else "s"} ignored • Tap to configure"
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Configure App Blacklist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
        // Header row with Title, status subtitle, and Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(
                        text = "Manual Alignment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                    )
                    Text(
                        text = if (config.isManualEnabled) {
                            "Custom offsets active"
                        } else {
                            "Auto-detected from hardware"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = config.isManualEnabled,
                onCheckedChange = { isChecked ->
                    onConfigChange(config.copy(isManualEnabled = isChecked))
                },
            )
        }

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
                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Debug mode / Green dot toggle
        val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = if (isDebugMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(
                        text = "Debug Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                    )
                    Text(
                        text = "Show green cutout alignment dot when idle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = isDebugMode,
                onCheckedChange = { isChecked ->
                    OverlayPreferences.setDebugModeEnabled(context, isChecked)
                },
            )
        }

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
                        imageVector = Icons.Default.RestartAlt,
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
private fun CutoutSliderRow(
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
                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
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
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
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
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
        }
    }
}
