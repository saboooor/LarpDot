package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val hideWhenScreenOff by OverlayPreferences.hideWhenScreenOffFlow.collectAsState()
    val hideOnLockScreen by OverlayPreferences.hideOnLockScreenFlow.collectAsState()
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.isTapToExpandEnabled(context)
        OverlayPreferences.isDebugModeEnabled(context)
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
            SectionHeader(title = "Island Customization")
            LarpCard {
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
                                fontWeight = FontWeight.SemiBold,
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
                                fontWeight = FontWeight.SemiBold,
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
                                fontWeight = FontWeight.SemiBold,
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
                                fontWeight = FontWeight.SemiBold,
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
                        fontWeight = FontWeight.SemiBold,
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
                fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.SemiBold,
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
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
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
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
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

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
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
