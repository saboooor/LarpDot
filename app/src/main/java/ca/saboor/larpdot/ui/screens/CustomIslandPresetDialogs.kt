package ca.saboor.larpdot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandPreset
import ca.saboor.larpdot.service.CustomIslandPresetCodec
import ca.saboor.larpdot.service.CustomIslandPresetStore
import ca.saboor.larpdot.service.modeLabel
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpListItem
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
internal fun CustomIslandPresetsCard(
    presets: List<CustomIslandPreset>,
    onImportClick: () -> Unit,
    onApplyPreset: (CustomIslandPreset) -> Unit,
    onSharePreset: (CustomIslandPreset) -> Unit,
    onDeletePreset: (CustomIslandPreset) -> Unit,
) {
    LarpCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onImportClick) {
                Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import")
            }
        }
        if (presets.isEmpty()) {
            Text(
                "No saved presets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val icon = preset.island.left.icon?.image
                        ?: preset.island.right.icon?.image
                        ?: preset.island.expandedLeft.icon?.image
                        ?: preset.island.expandedRight.icon?.image
                        ?: LarpIcons.Layers
                    val subtitle = buildList {
                        add(preset.island.modeLabel())
                        if (preset.description.isNotBlank()) add(preset.description)
                    }.joinToString(" • ")
                    LarpListItem(
                        title = preset.name,
                        subtitle = subtitle,
                        icon = icon,
                        subtitleMaxLines = 2,
                        onClick = { onApplyPreset(preset) },
                        trailing = {
                            IconButton(
                                onClick = { onSharePreset(preset) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.Share,
                                    contentDescription = "Share ${preset.name}",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onDeletePreset(preset) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.DeleteOutline,
                                    contentDescription = "Delete ${preset.name}",
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ShareOrSavePresetDialog(
    sourceIsland: CustomIsland,
    initialName: String,
    initialDescription: String = "",
    allowSave: Boolean,
    onDismiss: () -> Unit,
    onSaved: (CustomIslandPreset) -> Unit = {},
) {
    val context = LocalContext.current
    var name by rememberSaveable(sourceIsland.id, initialName) { mutableStateOf(initialName.take(48)) }
    var description by rememberSaveable(sourceIsland.id, initialDescription) { mutableStateOf(initialDescription.take(160)) }
    val (portableIsland, omittedWidgets) = remember(sourceIsland) {
        CustomIslandPresetCodec.stripDeviceLocalWidgets(sourceIsland)
    }
    val currentPreset = remember(name, description, portableIsland) {
        CustomIslandPreset(
            name = name.trim().ifBlank { "Custom Island" },
            description = description.trim(),
            island = portableIsland.copy(name = name.trim().ifBlank { "Custom Island" }),
        )
    }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = CustomIslandPresetCodec.toShareJson(currentPreset)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Preset exported" else "Could not export preset",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allowSave) "Save or Share Preset" else "Share Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(160) },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (omittedWidgets > 0) {
                    Text(
                        "$omittedWidgets app widget${if (omittedWidgets == 1) "" else "s"} won't be included because Android widgets are tied to your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        sharePresetViaIntent(context, currentPreset)
                    }) {
                        Icon(LarpIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(onClick = {
                        val code = CustomIslandPresetCodec.toShareCode(currentPreset)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText(currentPreset.name, code))
                        Toast.makeText(context, "Share code copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code")
                    }
                    OutlinedButton(onClick = {
                        val slug = currentPreset.name.lowercase()
                            .replace(Regex("[^a-z0-9]+"), "-")
                            .trim('-')
                            .ifBlank { "custom-island" }
                        exportFileLauncher.launch("$slug-preset.json")
                    }) {
                        Icon(LarpIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export .json")
                    }
                }
            }
        },
        confirmButton = {
            if (allowSave) {
                Button(onClick = {
                    val saved = CustomIslandPresetStore.saveFromIsland(
                        name = currentPreset.name,
                        description = currentPreset.description,
                        island = portableIsland,
                    )
                    if (saved != null) {
                        Toast.makeText(context, "Preset saved", Toast.LENGTH_SHORT).show()
                        onSaved(saved)
                        onDismiss()
                    }
                }) {
                    Text("Save Preset")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = if (allowSave) {
            { TextButton(onClick = onDismiss) { Text("Close") } }
        } else null,
    )
}

@Composable
internal fun ImportPresetDialog(
    onDismiss: () -> Unit,
    onSaveOnly: (CustomIslandPreset) -> Unit,
    onImportAndAddIsland: (CustomIslandPreset) -> Unit,
) {
    val context = LocalContext.current
    var rawInput by rememberSaveable { mutableStateOf("") }
    val parsed = remember(rawInput) { CustomIslandPresetCodec.fromShareInput(rawInput) }
    val showError = rawInput.isNotBlank() && parsed == null

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val loaded = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (loaded != null) {
                rawInput = loaded
            } else {
                Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Custom Island Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Paste share code or JSON") },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Not a valid LarpDot custom island preset") }
                    } else null,
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (clip.isNotBlank()) rawInput = clip
                        else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                    OutlinedButton(onClick = {
                        importFileLauncher.launch(arrayOf("application/json", "text/*"))
                    }) {
                        Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open .json")
                    }
                }
                if (parsed != null) {
                    val island = parsed.preset.island
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(parsed.preset.name, style = MaterialTheme.typography.titleSmall)
                            if (parsed.preset.description.isNotBlank()) {
                                Text(
                                    parsed.preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                island.modeLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (parsed.omittedWidgets > 0) {
                                Text(
                                    "Original island had ${parsed.omittedWidgets} app widget${if (parsed.omittedWidgets == 1) "" else "s"} (not transferable across devices).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parsed != null) {
                    TextButton(onClick = {
                        onSaveOnly(parsed.preset)
                        onDismiss()
                    }) {
                        Text("Save Only")
                    }
                }
                Button(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let {
                            onImportAndAddIsland(it.preset)
                            onDismiss()
                        }
                    },
                ) {
                    Text("Add Island")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun sharePresetViaIntent(context: Context, preset: CustomIslandPreset) {
    val code = CustomIslandPresetCodec.toShareCode(preset)
    val shareText = buildString {
        append("LarpDot Custom Island Preset: ${preset.name}")
        if (preset.description.isNotBlank()) {
            append("\n${preset.description}")
        }
        append("\n\n")
        append(code)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "LarpDot Preset: ${preset.name}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share preset"))
    }
}
