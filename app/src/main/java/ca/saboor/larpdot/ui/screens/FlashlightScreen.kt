package ca.saboor.larpdot.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.FlashlightPreviewElement
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.motionItem

private enum class FlashlightSettingsSection(val label: String) {
    LAYOUT("Layout"), ISLAND("Island"), HARDWARE("Hardware"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlashlightScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settingsSection by rememberSaveable { mutableStateOf<FlashlightSettingsSection?>(FlashlightSettingsSection.LAYOUT) }
    var selectedPreviewElement by remember { mutableStateOf<FlashlightPreviewElement?>(null) }

    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val beamBlur by OverlayPreferences.flashlightBeamBlurFlow.collectAsState()
    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isBoostSupported by FlashlightController.isBoostSupported.collectAsState()
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
    val showExpandedOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()

    LaunchedEffect(Unit) {
        FlashlightController.init(context)
        FlashlightController.refreshHardware()
        OverlayPreferences.getFlashlightExpandedStyle(context)
        OverlayPreferences.isFlashlightBeamBlurEnabled(context)
        OverlayPreferences.isShowFlashlightIslandEnabled(context)
        OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context)
        OverlayPreferences.getMinimizedFlashlightLeftSide(context)
        OverlayPreferences.getMinimizedFlashlightRightSide(context)
        OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context)
        OverlayPreferences.isFlashlightBoostEnabled(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "settings_sections") {
            LarpConnectedButtons(
                options = FlashlightSettingsSection.entries.map { section ->
                    LarpButtonOption(section.label, checked = settingsSection == section) { checked ->
                        settingsSection = section.takeIf { checked }
                    }
                },
                singleSelection = false,
            )
        }
        val section = settingsSection
        if (section == FlashlightSettingsSection.LAYOUT) {
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
        } else if (section != null) {
            flashlightSettingsItems(
                section = section,
                context = context,
                showInIsland = showInIsland,
                showExpandedOutline = showExpandedOutline,
                isFlashlightOn = isFlashlightOn,
                isBoostSupported = isBoostSupported,
                isBoostStarting = isBoostStarting,
                boostEnabled = boostEnabled,
                flashlightError = flashlightError,
                onSetTorch = onSetTorch,
                onSetBoost = onSetBoost,
                onCheckCameraSupport = onCheckCameraSupport,
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
                    beamBlur = beamBlur,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlashlightPreviewInspectorCard(
    element: FlashlightPreviewElement,
    onDismiss: () -> Unit,
    context: Context,
    beamBlur: Boolean,
) {
    val leftSide by OverlayPreferences.minimizedFlashlightLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedFlashlightRightSideFlow.collectAsState()
    fun sideIcon(side: OverlayPreferences.MinimizedFlashlightSideContent) = when (side) {
        OverlayPreferences.MinimizedFlashlightSideContent.ICON -> LarpIcons.FlashlightOn
        OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS -> LarpIcons.DonutLarge
        OverlayPreferences.MinimizedFlashlightSideContent.PERCENTAGE -> LarpIcons.BrightnessMedium
    }
    val (title, icon) = when (element) {
        FlashlightPreviewElement.MINIMIZED_PILL -> "Minimized Left Side" to sideIcon(leftSide)
        FlashlightPreviewElement.MINIMIZED_RADIAL -> "Minimized Right Side" to sideIcon(rightSide)
        FlashlightPreviewElement.EXPANDED_BEAM -> "Beam Settings" to LarpIcons.WbSunny
    }

    LarpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreviewInspectorHeader(title, icon, onDismiss)

            when (element) {
                FlashlightPreviewElement.MINIMIZED_PILL, FlashlightPreviewElement.MINIMIZED_RADIAL -> {
                    val isLeft = element == FlashlightPreviewElement.MINIMIZED_PILL
                    val selectedSide = if (isLeft) leftSide else rightSide
                    LarpConnectedButtons(
                        options = OverlayPreferences.MinimizedFlashlightSideContent.entries.map { option ->
                            LarpButtonOption(option.label, checked = selectedSide == option) {
                                if (isLeft) OverlayPreferences.setMinimizedFlashlightLeftSide(context, option)
                                else OverlayPreferences.setMinimizedFlashlightRightSide(context, option)
                            }
                        },
                    )
                }

                FlashlightPreviewElement.EXPANDED_BEAM -> {
                    LarpSwitchRow(
                        title = "Blur Beam",
                        checked = beamBlur,
                        onCheckedChange = { OverlayPreferences.setFlashlightBeamBlurEnabled(context, it) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.flashlightSettingsItems(
    section: FlashlightSettingsSection,
    context: Context,
    showInIsland: Boolean,
    showExpandedOutline: Boolean,
    isFlashlightOn: Boolean,
    isBoostSupported: Boolean,
    isBoostStarting: Boolean,
    boostEnabled: Boolean,
    flashlightError: String?,
    onSetTorch: (Boolean) -> Unit,
    onSetBoost: (Boolean) -> Unit,
    onCheckCameraSupport: () -> Unit,
) {
    when (section) {
        FlashlightSettingsSection.LAYOUT -> Unit
        FlashlightSettingsSection.ISLAND -> {
            motionItem(key = "minimized_controls") {
                SectionHeader(title = "Island")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LarpSwitchRow(
                            title = "Show in Island",
                            icon = LarpIcons.BrightnessMedium,
                            checked = showInIsland,
                            onCheckedChange = { OverlayPreferences.setShowFlashlightIslandEnabled(context, it) },
                        )

                        LarpDivider()

                        LarpSwitchRow(
                            title = "Expanded Radial Progress",
                            checked = showExpandedOutline,
                            onCheckedChange = { OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, it) },
                        )
                    }
                }
            }
        }
        FlashlightSettingsSection.HARDWARE -> {
            motionItem(key = "expanded_controls") {
                SectionHeader(title = "Flashlight")
                LarpCard {
                    LarpSwitchRow(
                        title = "Flashlight",
                        subtitle = if (isBoostStarting) "Starting boost…" else null,
                        icon = if (isFlashlightOn) LarpIcons.FlashlightOn else LarpIcons.FlashlightOff,
                        checked = isFlashlightOn || isBoostStarting,
                        onCheckedChange = onSetTorch,
                    )
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
                SectionHeader(title = "Brightness Boost")
                LarpCard {
                    LarpSwitchRow(
                        title = "Camera Boost",
                        subtitle = if (isBoostSupported) {
                            "Allows brightness above 100% using the camera. No photos or video are saved."
                        } else {
                            "Not supported on this device."
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
                }
            }
        }
    }
}
