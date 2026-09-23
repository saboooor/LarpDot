package ca.saboor.larpdot.ui.screens

import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalUriHandler

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ConnectedButtonGroup
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.FlashlightAmber
import ca.saboor.larpdot.ui.overlay.FlashlightAmberLight
import kotlin.math.roundToInt

private enum class FlashlightTapMode(val label: String, val icon: ImageVector) {
    TAP_TO_TOGGLE("Tap to Toggle", Icons.Default.PowerSettingsNew),
    TAP_TO_EXPAND("Tap to Expand", Icons.Default.OpenInFull),
}

@Composable
fun FlashlightScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isFlashlightAvailable by FlashlightController.isFlashlightAvailable.collectAsState()
    val isStrengthSupported by FlashlightController.isStrengthSupported.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val isSimulated by FlashlightController.isSimulated.collectAsState()
    val isPixelLightInstalled by FlashlightController.isPixelLightInstalled.collectAsState()
    val isPixelLightActive by FlashlightController.isPixelLightActive.collectAsState()
    val usePixelLight by OverlayPreferences.usePixelLightFlow.collectAsState()

    val showInIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
    val tapToToggle by OverlayPreferences.flashlightTapToToggleFlow.collectAsState()

    LaunchedEffect(Unit) {
        FlashlightController.init(context)
        FlashlightController.checkPixelLightInstallation(context)
        OverlayPreferences.isShowFlashlightIslandEnabled(context)
        OverlayPreferences.isFlashlightTapToToggleEnabled(context)
        OverlayPreferences.isUsePixelLightEnabled(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Live Island Preview
        item {
            IslandPreview(cutoutInfo = cutoutInfo, type = IslandPreviewType.FLASHLIGHT)
        }

        // 2. Master Controls Card
        item {
            SectionHeader(title = "Master Controls")
            LarpCard {
                // Power Toggle Row
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
                        Surface(
                            shape = CircleShape,
                            color = if (isFlashlightOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = null,
                                    tint = if (isFlashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Flashlight Power",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isFlashlightOn) "Illuminating" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isFlashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = isFlashlightOn,
                        onCheckedChange = {
                            FlashlightController.setTorch(it)
                        },
                    )
                }

                // Brightness Slider (if hardware or simulation supports strength level)
                if (isStrengthSupported && maxStrength > 1) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Brightness Level",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = "${(torchStrength.toFloat() / maxStrength * 100).roundToInt()}% ($torchStrength / $maxStrength)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Slider(
                            value = torchStrength.toFloat(),
                            onValueChange = { newLevel ->
                                FlashlightController.setStrength(newLevel.roundToInt())
                            },
                            valueRange = 1f..maxStrength.toFloat(),
                            steps = maxStrength - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )

                        // Quick presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(25, 50, 75, 100).forEach { pct ->
                                val targetLevel = ((pct / 100f) * maxStrength).roundToInt().coerceIn(1, maxStrength)
                                FilledTonalButton(
                                    onClick = { FlashlightController.setStrength(targetLevel) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (torchStrength == targetLevel) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                    ),
                                ) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (torchStrength == targetLevel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (torchStrength == targetLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Dynamic Island Behavior Card
        item {
            SectionHeader(title = "Dynamic Island Integration")
            LarpCard {
                // Show in Island switch
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
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Show in Dynamic Island",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Morph pill into active torch when flashlight is on",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = showInIsland,
                        onCheckedChange = { enabled ->
                            OverlayPreferences.setShowFlashlightIslandEnabled(context, enabled)
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Compact Pill Tap Interaction Mode
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Compact Pill Tap Action",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    val tapModes = FlashlightTapMode.entries
                    val selectedIndex = if (tapToToggle) 0 else 1

                    ConnectedButtonGroup(
                        items = tapModes,
                        selectedIndex = selectedIndex,
                        onItemSelected = { idx ->
                            OverlayPreferences.setFlashlightTapToToggleEnabled(context, idx == 0)
                        },
                        label = { it.label },
                        icon = { it.icon },
                    )

                    Text(
                        text = "Tap or long-press expands the island card directly to adjust brightness or toggle power.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // PixelLight Integration Card
        item {
            SectionHeader(title = "PixelLight Engine")
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
                        Surface(
                            shape = CircleShape,
                            color = if (isPixelLightActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isPixelLightActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "PixelLight Boost",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (isPixelLightInstalled) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPixelLightActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        Text(
                                            text = if (isPixelLightActive) "BOOST ON" else "INSTALLED",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isPixelLightActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isPixelLightInstalled) {
                                    "Bypasses standard Android LED limits for maximum hardware brightness."
                                } else {
                                    "Install PixelLight by chenxiaolong to unlock 100% LED hardware power."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (isPixelLightInstalled) {
                        Switch(
                            checked = usePixelLight,
                            onCheckedChange = { enabled ->
                                OverlayPreferences.setUsePixelLightEnabled(context, enabled)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isPixelLightInstalled) {
                    OutlinedButton(
                        onClick = { FlashlightController.openPixelLightApp(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open PixelLight App")
                    }
                } else {
                    val uriHandler = LocalUriHandler.current
                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri("https://github.com/chenxiaolong/PixelLight")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Get PixelLight on GitHub")
                    }
                }
            }
        }

        // 4. Hardware & Diagnostics Card
        item {
            SectionHeader(title = "Hardware & Diagnostics")
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
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Simulation Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Emulate torch hardware for testing and previews",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = isSimulated,
                        onCheckedChange = {
                            FlashlightController.setSimulated(it)
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiagnosticRow(
                        label = "Torch Hardware",
                        value = if (isFlashlightAvailable) "Detected & Ready" else "Not Available",
                        isHighlight = isFlashlightAvailable,
                    )
                    DiagnosticRow(
                        label = "PixelLight Engine",
                        value = if (isPixelLightActive) "Active (Boost Mode)" else if (isPixelLightInstalled) "Installed & Ready" else "Not Installed",
                        isHighlight = isPixelLightInstalled,
                    )
                    DiagnosticRow(
                        label = "Multi-level Brightness",
                        value = if (isStrengthSupported) "Supported (1 to $maxStrength)" else "Standard On/Off",
                        isHighlight = isStrengthSupported,
                    )
                    DiagnosticRow(
                        label = "Active Camera ID",
                        value = FlashlightController.activeCameraId ?: "Auto (Back)",
                        isHighlight = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isHighlight: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
