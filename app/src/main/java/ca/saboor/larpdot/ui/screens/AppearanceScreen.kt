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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.IslandBackupConfig
import ca.saboor.larpdot.service.IslandThemePresets
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
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val animationSpeed by OverlayPreferences.animationSpeedFlow.collectAsState()
    val userPresets by IslandThemePresets.userPresets.collectAsState()
    val blurStatus = rememberBackdropBlurStatus()
    var showResetDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val json = IslandThemePresets.exportFromPreferences(context)
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
            val config = IslandThemePresets.fromJson(json) ?: return@runCatching false
            IslandThemePresets.applyToPreferences(context, config, includeCutout = true)
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
        OverlayPreferences.getBubbleStyle(context)
        OverlayPreferences.areSmallerBubblesEnabled(context)
        IslandThemePresets.loadUserPresets(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(title = "Quick Presets")
            LarpCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Apply a visual look without changing your cutout calibration or notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            newPresetName = ""
                            showSavePresetDialog = true
                        },
                    ) {
                        Icon(LarpIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
                if (userPresets.isNotEmpty()) {
                    Text(
                        text = "Saved by you",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(userPresets, key = { it.id }) { preset ->
                            Surface(
                                onClick = {
                                    IslandThemePresets.applyThemePreset(context, preset)
                                    Toast.makeText(context, "Applied ${preset.name}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.width(184.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = LarpIcons.StarBorder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Text(
                                                text = preset.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                IslandThemePresets.deleteUserPreset(context, preset.id)
                                                Toast.makeText(context, "Deleted ${preset.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = LarpIcons.DeleteOutline,
                                                contentDescription = "Delete ${preset.name}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = preset.tagline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    LarpDivider()
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(IslandThemePresets.builtInPresets, key = { it.id }) { preset ->
                        Surface(
                            onClick = {
                                IslandThemePresets.applyThemePreset(context, preset)
                                Toast.makeText(context, "Applied ${preset.name}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.width(164.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = LarpIcons.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = preset.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    LarpSwitchRow(
                        title = "Squircle corners",
                        checked = squircleEnabled && !blurEnabled,
                        enabled = !blurEnabled,
                        onCheckedChange = { OverlayPreferences.setIslandSquircleEnabled(context, it) },
                    )
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
                    LarpSwitchRow(
                        title = "Backdrop blur",
                        subtitle = blurStatus.description,
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
                            Text("Lower the background opacity to see the blur.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Bubbles")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bubble Style",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Shape of the island and nearby bubbles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LarpConnectedButtons(
                        options = OverlayPreferences.BubbleStyle.entries.map { style ->
                            LarpButtonOption(
                                checked = bubbleStyle == style,
                                label = style.label,
                                onCheckedChange = { OverlayPreferences.setBubbleStyle(context, style) },
                            )
                        },
                    )
                }

                LarpDivider()

                LarpSwitchRow(
                    title = "Smaller Bubbles",
                    subtitle = "Reduce secondary bubble thickness by 20%",
                    icon = LarpIcons.BubbleChart,
                    checked = smallerBubbles,
                    onCheckedChange = { OverlayPreferences.setSmallerBubblesEnabled(context, it) },
                )
            }
        }

        item {
            SectionHeader(title = "Expanded Cutout")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Camera Cover",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = cameraCoverStyle.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            imageVector = LarpIcons.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Island Font Style",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Font family applied to island tracks, artists, and live notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

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
            SectionHeader(title = "Motion")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Island Animation Speed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = animationSpeed.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        }

        item {
            SectionHeader(title = "Backup")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Export or import your LarpDot configuration as JSON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { exportBackupLauncher.launch("larpdot-preset.json") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val json = IslandThemePresets.exportFromPreferences(context)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("LarpDot Preset", json))
                                Toast.makeText(context, "Preset JSON copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                val config = IslandThemePresets.fromJson(pasted)
                                if (config != null) {
                                    IslandThemePresets.applyToPreferences(context, config, includeCutout = true)
                                    Toast.makeText(context, "Preset imported from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No valid LarpDot JSON on clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste JSON")
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

    if (showSavePresetDialog) {
        val trimmedName = newPresetName.trim()
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            icon = { Icon(LarpIcons.Palette, contentDescription = null) },
            title = { Text("Save custom preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saves your current visual theme so you can switch back to it in one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { if (it.length <= 32) newPresetName = it },
                        label = { Text("Preset name") },
                        placeholder = { Text("e.g. Midnight Glass") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = trimmedName.isNotEmpty(),
                    onClick = {
                        val saved = IslandThemePresets.saveCurrentAsUserPreset(context, trimmedName)
                        showSavePresetDialog = false
                        if (saved != null) {
                            Toast.makeText(context, "Saved \"${saved.name}\"", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
                        IslandThemePresets.applyToPreferences(
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
