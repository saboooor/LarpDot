package ca.saboor.larpdot.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.IslandThemePresets
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.BackdropBlurStatus
import ca.saboor.larpdot.ui.overlay.rememberBackdropBlurStatus
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(modifier: Modifier = Modifier) {
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
    val blurStatus = rememberBackdropBlurStatus()

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
                            checked = blurEnabled && blurStatus == BackdropBlurStatus.AVAILABLE,
                            enabled = blurStatus == BackdropBlurStatus.AVAILABLE,
                            onCheckedChange = { OverlayPreferences.setBackdropBlurEnabled(context, it) },
                        )
                    }
                    Text(blurStatus.description, style = MaterialTheme.typography.bodySmall)
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
                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
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
                            imageVector = LarpIcons.BubbleChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Smaller Bubbles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
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
            }
        }

        item {
            SectionHeader(title = "Expanded Cutout")
            LarpCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Camera Cover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
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
                            imageVector = LarpIcons.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Island Font Style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Font family applied to island tracks, artists, and live notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

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
                    val fontWeightSliderState = remember {
                        SliderState(
                            value = fontWeight.toFloat(),
                            steps = 7,
                            trackRange = 100f..900f,
                        )
                    }
                    fontWeightSliderState.value = fontWeight.toFloat()
                    Slider(
                        state = fontWeightSliderState,
                        onValueChange = { OverlayPreferences.setIslandFontWeight(context, (it / 100).roundToInt() * 100) },
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
                }
            }
        }
    }
}
