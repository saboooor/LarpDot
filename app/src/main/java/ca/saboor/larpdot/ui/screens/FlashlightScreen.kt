package ca.saboor.larpdot.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.flashlight.FlashlightMode
import ca.saboor.larpdot.flashlight.StrobeSpeed
import ca.saboor.larpdot.flashlight.formatAutoOffRemaining
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.FlashlightPreviewElement
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSubPageTopBar
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.ui.theme.motionItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlashlightScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slideTravel = with(LocalDensity.current) { 24.dp.roundToPx() }
    var showFlashlightSettings by rememberSaveable { mutableStateOf(false) }
    var selectedPreviewElement by remember { mutableStateOf<FlashlightPreviewElement?>(null) }

    BackHandler(enabled = showFlashlightSettings) {
        showFlashlightSettings = false
    }

    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val beamBlur by OverlayPreferences.flashlightBeamBlurFlow.collectAsState()
    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isFlashlightAvailable by FlashlightController.isFlashlightAvailable.collectAsState()
    val isStrengthSupported by FlashlightController.isStrengthSupported.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
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

    val onSetTorch: (Boolean) -> Unit = { enabled ->
        if (enabled && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchPermission = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            FlashlightController.setTorch(enabled)
        }
    }

    val onSetBoost: (Boolean) -> Unit = { enabled ->
        if (enabled && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingBoostPermission = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            FlashlightController.setBoostEnabled(enabled)
        }
    }

    val onCheckCameraSupport: () -> Unit = {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            FlashlightController.refreshHardware()
        }
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
    val autoOffMinutes by OverlayPreferences.flashlightAutoOffMinutesFlow.collectAsState()
    val strobeSpeed by OverlayPreferences.flashlightStrobeSpeedFlow.collectAsState()
    val flashlightMode by FlashlightController.flashlightMode.collectAsState()
    val autoOffRemainingMs by FlashlightController.autoOffRemainingMs.collectAsState()

    LaunchedEffect(Unit) {
        FlashlightController.init(context)
        FlashlightController.refreshHardware()
        OverlayPreferences.getFlashlightExpandedStyle(context)
        OverlayPreferences.isFlashlightBeamBlurEnabled(context)
        OverlayPreferences.isShowFlashlightIslandEnabled(context)
        OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context)
        OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context)
        OverlayPreferences.isFlashlightTapToToggleEnabled(context)
        OverlayPreferences.isFlashlightBoostEnabled(context)
        OverlayPreferences.getFlashlightAutoOffMinutes(context)
        OverlayPreferences.getFlashlightStrobeSpeed(context)
    }

    AnimatedContent(
        targetState = showFlashlightSettings,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        label = "flashlight_screen_view",
        transitionSpec = {
            LarpMotion.horizontalSlideTransition(if (targetState) 1 else -1, slideTravel)
        },
    ) { isSettingsOpen ->
        if (!isSettingsOpen) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "preview_top_bar") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = { showFlashlightSettings = true },
                        ) {
                            Icon(
                                imageVector = LarpIcons.Settings,
                                contentDescription = "Flashlight Settings",
                            )
                        }
                    }
                }

                item(key = "expanded_style_layout") {
                    LarpConnectedButtons(
                        options = OverlayPreferences.FlashlightExpandedStyle.entries.map { style ->
                            LarpButtonOption(
                                checked = expandedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setFlashlightExpandedStyle(context, style)
                                    if (style != OverlayPreferences.FlashlightExpandedStyle.BEAM &&
                                        selectedPreviewElement == FlashlightPreviewElement.EXPANDED_BEAM
                                    ) {
                                        selectedPreviewElement = null
                                    }
                                },
                            )
                        },
                    )
                }

                item(key = "preview") {
                    IslandPreview(
                        cutoutInfo = cutoutInfo,
                        type = IslandPreviewType.FLASHLIGHT,
                        selectedFlashlightElement = selectedPreviewElement,
                        onFlashlightElementClick = { element ->
                            selectedPreviewElement = if (selectedPreviewElement == element) null else element
                        },
                    )
                }

                selectedPreviewElement?.let { activeElement ->
                    motionItem(key = "flashlight_inspector_${activeElement.name}") {
                        FlashlightPreviewInspectorCard(
                            element = activeElement,
                            onDismiss = { selectedPreviewElement = null },
                            context = context,
                            showMinimizedOutline = showMinimizedOutline,
                            tapToToggle = tapToToggle,
                            beamBlur = beamBlur,
                        )
                    }
                }
            }
        } else {
            FlashlightSettingsPage(
                context = context,
                showInIsland = showInIsland,
                showExpandedOutline = showExpandedOutline,
                tapToToggle = tapToToggle,
                autoOffMinutes = autoOffMinutes,
                strobeSpeed = strobeSpeed,
                flashlightMode = flashlightMode,
                autoOffRemainingMs = autoOffRemainingMs,
                isFlashlightOn = isFlashlightOn,
                isFlashlightAvailable = isFlashlightAvailable,
                isStrengthSupported = isStrengthSupported,
                maxStrength = maxStrength,
                isBoostSupported = isBoostSupported,
                isBoostActive = isBoostActive,
                isBoostStarting = isBoostStarting,
                boostEnabled = boostEnabled,
                flashlightError = flashlightError,
                onSetTorch = onSetTorch,
                onSetBoost = onSetBoost,
                onCheckCameraSupport = onCheckCameraSupport,
                onBack = { showFlashlightSettings = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlashlightPreviewInspectorCard(
    element: FlashlightPreviewElement,
    onDismiss: () -> Unit,
    context: Context,
    showMinimizedOutline: Boolean,
    tapToToggle: Boolean,
    beamBlur: Boolean,
) {
    val (title, icon) = when (element) {
        FlashlightPreviewElement.MINIMIZED_PILL -> "Minimized Pill" to LarpIcons.FlashlightOn
        FlashlightPreviewElement.MINIMIZED_RADIAL -> "Radial Progress" to LarpIcons.DonutLarge
        FlashlightPreviewElement.EXPANDED_BEAM -> "Beam Settings" to LarpIcons.WbSunny
    }

    LarpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreviewInspectorHeader(title, icon, onDismiss)

            when (element) {
                FlashlightPreviewElement.MINIMIZED_PILL -> {
                    CompactTapActionSelector(
                        context = context,
                        tapToToggle = tapToToggle,
                    )
                    LarpDivider()
                    LarpSwitchRow(
                        title = "Radial Progress",
                        subtitle = "Show radial brightness gauge on the compact island and dots",
                        checked = showMinimizedOutline,
                        onCheckedChange = { OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, it) },
                    )
                }

                FlashlightPreviewElement.MINIMIZED_RADIAL -> {
                    LarpSwitchRow(
                        title = "Radial Progress",
                        subtitle = "Show radial brightness gauge on the compact island and dots",
                        checked = showMinimizedOutline,
                        onCheckedChange = { OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, it) },
                    )
                }

                FlashlightPreviewElement.EXPANDED_BEAM -> {
                    LarpSwitchRow(
                        title = "Blur Beam",
                        subtitle = "Soften the light cone with a diffused blur",
                        checked = beamBlur,
                        onCheckedChange = { OverlayPreferences.setFlashlightBeamBlurEnabled(context, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashlightSettingsPage(
    context: Context,
    showInIsland: Boolean,
    showExpandedOutline: Boolean,
    tapToToggle: Boolean,
    autoOffMinutes: Int,
    strobeSpeed: StrobeSpeed,
    flashlightMode: FlashlightMode,
    autoOffRemainingMs: Long?,
    isFlashlightOn: Boolean,
    isFlashlightAvailable: Boolean,
    isStrengthSupported: Boolean,
    maxStrength: Int,
    isBoostSupported: Boolean,
    isBoostActive: Boolean,
    isBoostStarting: Boolean,
    boostEnabled: Boolean,
    flashlightError: String?,
    onSetTorch: (Boolean) -> Unit,
    onSetBoost: (Boolean) -> Unit,
    onCheckCameraSupport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        LarpSubPageTopBar(
            title = "Flashlight Settings",
            subtitle = "Island integration, boost, and hardware",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            motionItem(key = "minimized_controls") {
                SectionHeader(title = "Dynamic Island Integration")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LarpSwitchRow(
                            title = "Show in Dynamic Island",
                            subtitle = "Morph pill into active torch when flashlight is on",
                            icon = LarpIcons.BrightnessMedium,
                            checked = showInIsland,
                            onCheckedChange = { OverlayPreferences.setShowFlashlightIslandEnabled(context, it) },
                        )

                        LarpDivider()

                        LarpSwitchRow(
                            title = "Expanded Radial Progress",
                            subtitle = "Show radial brightness ring around the expanded torch icon",
                            checked = showExpandedOutline,
                            onCheckedChange = { OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, it) },
                        )

                        LarpDivider()

                        CompactTapActionSelector(
                            context = context,
                            tapToToggle = tapToToggle,
                        )
                    }
                }
            }

            motionItem(key = "expanded_controls") {
                SectionHeader(title = "Master Controls")
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
                                color = if (isFlashlightOn) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFlashlightOn) LarpIcons.FlashlightOn else LarpIcons.FlashlightOff,
                                        contentDescription = null,
                                        tint = if (isFlashlightOn) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Flashlight Power",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = when {
                                        isBoostStarting -> "Starting boost…"
                                        isFlashlightOn && autoOffRemainingMs != null ->
                                            "${flashlightMode.label} • Auto-off in ${formatAutoOffRemaining(autoOffRemainingMs)}"
                                        isFlashlightOn && flashlightMode != FlashlightMode.STEADY ->
                                            "${flashlightMode.label} active"
                                        isFlashlightOn -> "Illuminating"
                                        else -> "Off"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isFlashlightOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = isFlashlightOn || isBoostStarting,
                            onCheckedChange = onSetTorch,
                        )
                    }

                    LarpDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Signal Mode",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Switch between steady illumination, rapid strobe, or international Morse SOS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LarpConnectedButtons(
                            options = FlashlightMode.entries.map { mode ->
                                LarpButtonOption(
                                    checked = flashlightMode == mode && (mode == FlashlightMode.STEADY || isFlashlightOn),
                                    label = mode.label,
                                    onCheckedChange = { FlashlightController.setMode(mode) },
                                )
                            },
                        )
                        Text(
                            text = "Strobe Speed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LarpConnectedButtons(
                            options = StrobeSpeed.entries.map { speed ->
                                LarpButtonOption(
                                    checked = strobeSpeed == speed,
                                    label = speed.label,
                                    onCheckedChange = {
                                        OverlayPreferences.setFlashlightStrobeSpeed(context, speed)
                                        FlashlightController.refreshStrobeSpeed()
                                    },
                                )
                            },
                        )
                    }

                    LarpDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Safety Auto-Off Timer",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (autoOffMinutes == 0) {
                                "Stay on until you turn the flashlight off manually"
                            } else {
                                "Automatically turn off the flashlight after $autoOffMinutes min to protect battery and thermals"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val autoOffOptions = listOf(0 to "Off", 1 to "1m", 5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m")
                        LarpConnectedButtons(
                            options = autoOffOptions.map { (minutes, label) ->
                                LarpButtonOption(
                                    checked = autoOffMinutes == minutes,
                                    label = label,
                                    onCheckedChange = {
                                        OverlayPreferences.setFlashlightAutoOffMinutes(context, minutes)
                                        FlashlightController.refreshAutoOffTimer()
                                    },
                                )
                            },
                            maxButtonsPerRow = 3,
                        )
                    }
                }
            }

            if (flashlightError != null) {
                motionItem(key = "hardware_error") {
                    Text(
                        text = flashlightError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            motionItem(key = "overview_controls") {
                SectionHeader(title = "Flashlight Boost")
                LarpCard {
                    LarpSwitchRow(
                        title = "Built-in camera boost",
                        subtitle = if (isBoostSupported) {
                            "Unlocks brightness above 100%. The camera is used only above the standard flashlight maximum."
                        } else {
                            "This device does not expose boost controls. Standard flashlight control is available."
                        },
                        checked = boostEnabled && isBoostSupported,
                        enabled = isBoostSupported,
                        onCheckedChange = onSetBoost,
                    )
                    if (!isBoostSupported) {
                        TextButton(onClick = onCheckCameraSupport) {
                            Text("Check camera support")
                        }
                    }
                    if (isBoostSupported) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Above 100%, camera permission and a camera session are required. Returning to 100% releases the camera. No photos or video are saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            motionItem(key = "diagnostics") {
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompactTapActionSelector(
    context: Context,
    tapToToggle: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LarpIcons.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Compact Pill Tap Action",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        LarpConnectedButtons(
            options = listOf(
                LarpButtonOption(
                    checked = tapToToggle,
                    label = "Tap to Toggle",
                    onCheckedChange = {
                        OverlayPreferences.setFlashlightTapToToggleEnabled(context, true)
                    },
                ),
                LarpButtonOption(
                    checked = !tapToToggle,
                    label = "Tap to Expand",
                    onCheckedChange = {
                        OverlayPreferences.setFlashlightTapToToggleEnabled(context, false)
                    },
                ),
            ),
        )

        Text(
            text = "Swipe right on the flashlight island to raise brightness, or left to lower it. Hold to keep stepping; move farther for faster, larger steps. Tap or long-press to open the island card or toggle power.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
