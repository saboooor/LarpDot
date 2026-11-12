package ca.saboor.larpdot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isFlashlightAvailable by FlashlightController.isFlashlightAvailable.collectAsState()
    val isStrengthSupported by FlashlightController.isStrengthSupported.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val standardMaximum by FlashlightController.standardMaxStrength.collectAsState()
    val isBoostSupported by FlashlightController.isBoostSupported.collectAsState()
    val isBoostActive by FlashlightController.isBoostActive.collectAsState()
    val isBoostStarting by FlashlightController.isBoostStarting.collectAsState()
    val boostEnabled by OverlayPreferences.flashlightBoostFlow.collectAsState()
    val flashlightError by FlashlightController.lastError.collectAsState()
    var pendingBoostPermission by remember { mutableStateOf(false) }
    var pendingTorchPermission by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            FlashlightController.refreshHardware()
            if (pendingBoostPermission) FlashlightController.setBoostEnabled(true)
            if (pendingTorchPermission) FlashlightController.turnOn()
        }
        pendingBoostPermission = false
        pendingTorchPermission = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) FlashlightController.refreshHardware()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showInIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
    val showMinimizedOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val showExpandedOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val tapToToggle by OverlayPreferences.flashlightTapToToggleFlow.collectAsState()

    LaunchedEffect(Unit) {
        FlashlightController.init(context)
        FlashlightController.refreshHardware()
        OverlayPreferences.isShowFlashlightIslandEnabled(context)
        OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context)
        OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context)
        OverlayPreferences.isFlashlightTapToToggleEnabled(context)
        OverlayPreferences.isFlashlightBoostEnabled(context)
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

        item {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("Minimized") },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("Expanded") },
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { onTabSelected(2) },
                    text = { Text("Overview") },
                )
            }
        }

        // 2. Master Controls Card
        if (selectedTabIndex == 1) item {
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
                                text = if (isBoostStarting) "Starting boost…" else if (isFlashlightOn) "Illuminating" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isFlashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = isFlashlightOn || isBoostStarting,
                        onCheckedChange = {
                            if (it && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                pendingTorchPermission = true
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            } else FlashlightController.setTorch(it)
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
                                text = "${(torchStrength.toFloat() / standardMaximum.coerceAtLeast(1) * 100).roundToInt()}% ($torchStrength / $maxStrength)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        val sliderState = remember(maxStrength) {
                            SliderState(
                                value = torchStrength.toFloat(),
                                steps = maxStrength - 2,
                                trackRange = 1f..maxStrength.toFloat(),
                            )
                        }
                        sliderState.value = torchStrength.toFloat()
                        Slider(
                            state = sliderState,
                            onValueChange = { newLevel ->
                                FlashlightController.setStrength(newLevel.roundToInt())
                            },
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
                                val targetLevel = ((pct / 100f) * standardMaximum).roundToInt().coerceIn(1, maxStrength)
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
        if (selectedTabIndex == 0) item {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strength Outline", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show torch brightness around the compact island",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showMinimizedOutline,
                        onCheckedChange = { OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, it) },
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
                        text = "Swipe right on the flashlight island to raise brightness, or left to lower it. Hold to keep stepping; move farther for faster, larger steps. Tap or long-press to open the island card or toggle power.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (selectedTabIndex == 1) item {
            SectionHeader(title = "Expanded Appearance")
            LarpCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strength Outline", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show torch brightness around the expanded card",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showExpandedOutline,
                        onCheckedChange = { OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, it) },
                    )
                }
            }
        }

        if (flashlightError != null) item {
            Text(flashlightError.orEmpty(), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }

        if (selectedTabIndex == 2) item {
            SectionHeader(title = "Flashlight Boost")
            LarpCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Built-in camera boost", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isBoostSupported) "Unlocks brightness above 100%. The camera is used only above the standard flashlight maximum."
                            else "This device does not expose boost controls. Standard flashlight control is available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = boostEnabled && isBoostSupported,
                        enabled = isBoostSupported,
                        onCheckedChange = { enabled ->
                            if (enabled && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                pendingBoostPermission = true
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            } else FlashlightController.setBoostEnabled(enabled)
                        },
                    )
                }
                if (!isBoostSupported) {
                    TextButton(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        } else FlashlightController.refreshHardware()
                    }) { Text("Check camera support") }
                }
                if (isBoostSupported) {
                    Spacer(Modifier.height(12.dp))
                    Text("Above 100%, camera permission and a camera session are required. Returning to 100% releases the camera. No photos or video are saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 4. Hardware & Diagnostics Card
        if (selectedTabIndex == 2) item {
            SectionHeader(title = "Hardware & Diagnostics")
            LarpCard {
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
                        label = "Camera Boost",
                        value = if (isBoostActive) "Active" else if (isBoostSupported) "Supported" else "Not Supported",
                        isHighlight = isBoostSupported,
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
