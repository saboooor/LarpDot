package ca.saboor.larpdot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.DotAccessibilityService
import ca.saboor.larpdot.service.IslandBackdropLuminanceTracker
import ca.saboor.larpdot.service.IslandBackupConfig
import ca.saboor.larpdot.service.IslandConfigurationBackup
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.BackdropBlurStatus
import ca.saboor.larpdot.ui.overlay.rememberBackdropBlurStatus
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val islandFontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight by OverlayPreferences.islandFontWeightFlow.collectAsState()
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()
    val islandOutlineMode by OverlayPreferences.islandOutlineModeFlow.collectAsState()
    val isAccessibilityConnected by DotAccessibilityService.isServiceConnected.collectAsState()
    val smallerBubblesMode by OverlayPreferences.smallerBubblesModeFlow.collectAsState()
    val islandSpacingDp by OverlayPreferences.islandSpacingDpFlow.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val animationSpeed by OverlayPreferences.animationSpeedFlow.collectAsState()
    val blurStatus = rememberBackdropBlurStatus()
    var showResetDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val json = IslandConfigurationBackup.exportFromPreferences(context)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (ok) "Backup exported" else "Failed to export backup",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@runCatching false
            val config = IslandConfigurationBackup.fromJson(json) ?: return@runCatching false
            IslandConfigurationBackup.applyToPreferences(context, config, includeCutout = true)
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (imported) "Backup imported" else "Invalid backup file",
            Toast.LENGTH_SHORT,
        ).show()
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.loadIslandAppearance(context)
        OverlayPreferences.getSmallerBubblesMode(context)
        OverlayPreferences.getIslandSpacingDp(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Island outline",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LarpConnectedButtons(
                            options = OverlayPreferences.IslandOutlineMode.entries.map { mode ->
                                LarpButtonOption(
                                    checked = islandOutlineMode == mode,
                                    label = mode.label,
                                    onCheckedChange = { OverlayPreferences.setIslandOutlineMode(context, mode) },
                                )
                            },
                        )
                        AnimatedVisibility(
                            visible = islandOutlineMode == OverlayPreferences.IslandOutlineMode.DYNAMIC,
                            enter = LarpMotion.reveal,
                            exit = LarpMotion.conceal,
                        ) {
                            val note = when {
                                !IslandBackdropLuminanceTracker.isSupported() ->
                                    "Dynamic outline requires Android 11 or higher."
                                !isAccessibilityConnected ->
                                    "Enable the Accessibility Service to detect pitch-black backgrounds."
                                else ->
                                    "Shows the outline only when the background behind the island is pitch black."
                            }
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LarpSwitchRow(
                        title = "Squircle corners",
                        checked = squircleEnabled && !blurEnabled,
                        enabled = !blurEnabled,
                        onCheckedChange = { OverlayPreferences.setIslandSquircleEnabled(context, it) },
                    )
                    AnimatedVisibility(blurEnabled, enter = LarpMotion.reveal, exit = LarpMotion.conceal) {
                        Text(
                            "Turn off backdrop blur to use squircle corners.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CutoutSliderRow(
                        label = "Expanded background opacity", value = backgroundOpacity * 100f,
                        valueRange = 0f..100f, unit = "%",
                        onValueChange = { OverlayPreferences.setExpandedBackgroundOpacity(context, it / 100f) },
                        onNudge = { OverlayPreferences.setExpandedBackgroundOpacity(context, backgroundOpacity + it / 100f) },
                    )
                    LarpSwitchRow(
                        title = "Backdrop blur",
                        subtitle = blurStatus.takeUnless { it == BackdropBlurStatus.AVAILABLE }?.description,
                        checked = blurEnabled && blurStatus == BackdropBlurStatus.AVAILABLE,
                        enabled = blurStatus == BackdropBlurStatus.AVAILABLE,
                        onCheckedChange = { OverlayPreferences.setBackdropBlurEnabled(context, it) },
                    )
                    AnimatedVisibility(
                        visible = blurEnabled && blurStatus == BackdropBlurStatus.AVAILABLE,
                        enter = LarpMotion.reveal,
                        exit = LarpMotion.conceal,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CutoutSliderRow(
                                label = "Blur amount", value = blurRadius, valueRange = 5f..120f, unit = "dp",
                                onValueChange = { OverlayPreferences.setBackdropBlurRadius(context, it) },
                                onNudge = { OverlayPreferences.setBackdropBlurRadius(context, blurRadius + it * 5f) },
                            )
                            if (backgroundOpacity >= 0.99f) {
                                Text(
                                    "Lower background opacity to see the blur.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Bubbles")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Smaller secondary bubbles",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LarpConnectedButtons(
                        options = OverlayPreferences.SmallerBubblesMode.entries.map { mode ->
                            LarpButtonOption(
                                checked = smallerBubblesMode == mode,
                                label = mode.label,
                                onCheckedChange = { OverlayPreferences.setSmallerBubblesMode(context, mode) },
                            )
                        },
                    )
                }

                LarpDivider()

                CutoutSliderRow(
                    label = "Space between islands",
                    value = islandSpacingDp,
                    valueRange = 0f..24f,
                    unit = "dp",
                    onValueChange = { OverlayPreferences.setIslandSpacingDp(context, it) },
                    onNudge = { OverlayPreferences.setIslandSpacingDp(context, islandSpacingDp + it) },
                )
            }
        }

        item {
            SectionHeader(title = "Camera Cover")
            LarpCard {
                LarpConnectedButtons(
                    options = OverlayPreferences.CameraCoverStyle.entries.map { style ->
                        LarpButtonOption(
                            checked = cameraCoverStyle == style,
                            label = style.label,
                            onCheckedChange = { OverlayPreferences.setCameraCoverStyle(context, style) },
                        )
                    },
                )
            }
        }

        item {
            SectionHeader(title = "Typography")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LarpConnectedButtons(
                        options = OverlayPreferences.IslandFontStyle.entries.map { style ->
                            LarpButtonOption(
                                label = style.label,
                                checked = islandFontStyle == style,
                                fontFamily = style.fontFamily,
                                onCheckedChange = { OverlayPreferences.setIslandFontStyle(context, style) },
                            )
                        },
                        maxButtonsPerRow = 4,
                    )
                    LarpSliderRow(
                        title = "Font weight",
                        valueLabel = "$fontWeight",
                        value = fontWeight.toFloat(),
                        valueRange = 100f..900f,
                        steps = 7,
                        onValueChange = { OverlayPreferences.setIslandFontWeight(context, (it / 100).roundToInt() * 100) },
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Animation Speed")
            LarpCard {
                LarpConnectedButtons(
                    options = OverlayPreferences.AnimationSpeed.entries.map { option ->
                        LarpButtonOption(
                            checked = animationSpeed == option,
                            label = option.label,
                            onCheckedChange = { OverlayPreferences.setAnimationSpeed(context, option) },
                        )
                    },
                )
            }
        }

        item {
            SectionHeader(title = "Backup")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportBackupLauncher.launch("larpdot-backup.json") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val json = IslandConfigurationBackup.exportFromPreferences(context)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("LarpDot Backup", json))
                                Toast.makeText(context, "Backup copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                val config = IslandConfigurationBackup.fromJson(pasted)
                                if (config != null) {
                                    IslandConfigurationBackup.applyToPreferences(context, config, includeCutout = true)
                                    Toast.makeText(context, "Backup imported", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Invalid backup data", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste")
                        }
                    }
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(LarpIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset to Defaults")
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(LarpIcons.RestartAlt, contentDescription = null) },
            title = { Text("Reset settings?") },
            text = {
                Text(
                    "This restores default appearance, music, flashlight, and priority settings. Your camera cutout calibration will be kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        IslandConfigurationBackup.applyToPreferences(
                            context = context,
                            config = IslandBackupConfig(),
                            includeCutout = false,
                        )
                        showResetDialog = false
                        Toast.makeText(context, "Reset to defaults", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
