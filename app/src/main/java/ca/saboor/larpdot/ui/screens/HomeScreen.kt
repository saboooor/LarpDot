package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ExpandedIslandPreview
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
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
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
    }

    val hardwareCutout = remember(configuration) { CutoutDetector.detectHardwareCutout(context) }
    val defaultDiameterDp = with(density) { (hardwareCutout.radiusPx * 2f).toDp() }.value.roundToInt()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Live Expanded Island Card Preview
        item {
            ExpandedIslandPreview(cutoutInfo = cutoutInfo)
        }

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

        // Island Customization & Display Options
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
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Show Song Title",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Display track title above minimized island",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = showTitle,
                        onCheckedChange = { isChecked ->
                            OverlayPreferences.setShowMinimizedTitleEnabled(context, isChecked)
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
                Text(
                    text = "Adjust offsets and dot diameter to align the island with your camera cutout in real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 1. Horizontal Offset (X)
                AlignmentSliderControl(
                    label = "Horizontal Offset (X)",
                    valueText = "${if (config.offsetX > 0) "+" else ""}${config.offsetX.roundToInt()} dp",
                    value = config.offsetX,
                    range = -80f..80f,
                    onValueChange = { onConfigChange(config.copy(offsetX = it)) },
                    onNudge = { delta ->
                        val next = (config.offsetX + delta).coerceIn(-80f, 80f)
                        onConfigChange(config.copy(offsetX = next))
                    },
                )

                // 2. Vertical Offset (Y)
                AlignmentSliderControl(
                    label = "Vertical Offset (Y)",
                    valueText = "${if (config.offsetY > 0) "+" else ""}${config.offsetY.roundToInt()} dp",
                    value = config.offsetY,
                    range = -40f..60f,
                    onValueChange = { onConfigChange(config.copy(offsetY = it)) },
                    onNudge = { delta ->
                        val next = (config.offsetY + delta).coerceIn(-40f, 60f)
                        onConfigChange(config.copy(offsetY = next))
                    },
                )

                // 3. Cutout Dot Size (Diameter)
                val currentDiameter = if (config.customDiameterDp > 0f) {
                    config.customDiameterDp
                } else {
                    defaultDiameterDp.toFloat()
                }
                AlignmentSliderControl(
                    label = "Cutout Dot Size",
                    valueText = if (config.customDiameterDp <= 0f) {
                        "Auto ($defaultDiameterDp dp)"
                    } else {
                        "${config.customDiameterDp.roundToInt()} dp"
                    },
                    value = currentDiameter.coerceIn(18f, 40f),
                    range = 18f..40f,
                    onValueChange = { onConfigChange(config.copy(customDiameterDp = it)) },
                    onNudge = { delta ->
                        val next = (currentDiameter + delta).coerceIn(18f, 40f)
                        onConfigChange(config.copy(customDiameterDp = next))
                    },
                )

                // Reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            onConfigChange(
                                OverlayPreferences.CutoutConfig(
                                    isManualEnabled = true,
                                    offsetX = 0f,
                                    offsetY = 0f,
                                    customDiameterDp = 0f,
                                )
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Reset Offsets",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignmentSliderControl(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onNudge: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onNudge(-1f) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(18.dp),
                )
            }

            Slider(
                value = value,
                onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                valueRange = range,
                modifier = Modifier.weight(1f),
            )

            FilledTonalIconButton(
                onClick = { onNudge(1f) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

