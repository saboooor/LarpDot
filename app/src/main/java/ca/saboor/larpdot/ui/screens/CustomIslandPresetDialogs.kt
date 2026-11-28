package ca.saboor.larpdot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.CatalogFilterKind
import ca.saboor.larpdot.service.CatalogPage
import ca.saboor.larpdot.service.CatalogPresetEntry
import ca.saboor.larpdot.service.CatalogSortOption
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandAppGroup
import ca.saboor.larpdot.service.CustomIslandCatalogClient
import ca.saboor.larpdot.service.CustomIslandPreset
import ca.saboor.larpdot.service.CustomIslandPresetBundle
import ca.saboor.larpdot.service.CustomIslandPresetCodec
import ca.saboor.larpdot.service.CustomIslandPresetStore
import ca.saboor.larpdot.service.LARPDOT_CATALOG_BASE_URL
import ca.saboor.larpdot.service.groupByApp
import ca.saboor.larpdot.service.modeLabel
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpListItem
import ca.saboor.larpdot.ui.overlay.image
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun resolveAppLabel(context: Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName).ifBlank { packageName }

@Composable
internal fun AppCategoryHeader(
    packageName: String?,
    appName: String,
    count: Int,
    modifier: Modifier = Modifier,
    onShareBundle: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appIcon by produceState<Bitmap?>(null, context, packageName) {
        value = if (packageName != null) {
            withContext(Dispatchers.IO) { NotificationActivityState.getOrLoadAppIcon(context, packageName) }
        } else null
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val iconBitmap = appIcon?.takeUnless(Bitmap::isRecycled)
        if (iconBitmap != null) {
            val imageBitmap = remember(iconBitmap) { iconBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = appName,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Icon(
                imageVector = LarpIcons.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onShareBundle != null) {
            TextButton(onClick = onShareBundle) {
                Icon(LarpIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (count > 1) "Share bundle" else "Share")
            }
        }
    }
}

@Composable
internal fun CustomIslandPresetsCard(
    presets: List<CustomIslandPreset>,
    onImportClick: () -> Unit,
    onBrowseCatalogClick: () -> Unit,
    onApplyPreset: (CustomIslandPreset) -> Unit,
    onSharePreset: (CustomIslandPreset) -> Unit,
    onShareBundle: (CustomIslandAppGroup<CustomIslandPreset>) -> Unit,
    onDeletePreset: (CustomIslandPreset) -> Unit,
) {
    val context = LocalContext.current
    val groups = remember(presets, context) {
        groupByApp(
            items = presets,
            packageOf = { it.island.associatedAppPackage() },
            resolveAppName = { pkg -> resolveAppLabel(context, pkg) },
        )
    }

    LarpCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBrowseCatalogClick) {
                Icon(LarpIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Catalog")
            }
            OutlinedButton(onClick = onImportClick) {
                Icon(LarpIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) LarpDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppCategoryHeader(
                            packageName = group.packageName,
                            appName = group.appName,
                            count = group.items.size,
                            onShareBundle = if (group.packageName != null || group.items.size > 1) {
                                { onShareBundle(group) }
                            } else null,
                        )
                        group.items.forEach { preset ->
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
    }
}

@Composable
internal fun ShareOrSaveBundleDialog(
    appPackage: String?,
    appName: String,
    sourcePresets: List<CustomIslandPreset>,
    allowSaveAsPresets: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val initialKey = remember(appPackage, appName, sourcePresets.size) { "$appPackage/$appName/${sourcePresets.size}" }
    var name by rememberSaveable(initialKey) { mutableStateOf(appName.take(48)) }
    var description by rememberSaveable(initialKey) { mutableStateOf("") }
    var selectedIds by remember(sourcePresets) { mutableStateOf(sourcePresets.map { it.id }.toSet()) }

    val chosenPresets = remember(sourcePresets, selectedIds) {
        sourcePresets.filter { it.id in selectedIds }
    }
    val omittedWidgets = remember(chosenPresets) {
        chosenPresets.sumOf { CustomIslandPresetCodec.stripDeviceLocalWidgets(it.island).second }
    }
    val currentBundle = remember(name, description, appPackage, appName, chosenPresets) {
        CustomIslandPresetBundle(
            name = name.trim().ifBlank { appName.ifBlank { "App Bundle" } },
            description = description.trim(),
            appPackage = appPackage,
            appName = appName,
            presets = chosenPresets,
        )
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null && chosenPresets.isNotEmpty()) {
            val json = CustomIslandPresetCodec.toBundleShareJson(currentBundle)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Bundle exported" else "Could not export bundle",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share $appName Bundle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Bundle name") },
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
                Column(
                    modifier = Modifier.heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sourcePresets.forEach { preset ->
                        val checked = preset.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedIds = if (checked) selectedIds - preset.id else selectedIds + preset.id
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIds = if (isChecked) selectedIds + preset.id else selectedIds - preset.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = preset.island.modeLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
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
                    OutlinedButton(
                        enabled = chosenPresets.isNotEmpty(),
                        onClick = { shareBundleViaIntent(context, currentBundle) },
                    ) {
                        Icon(LarpIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        enabled = chosenPresets.isNotEmpty(),
                        onClick = {
                            val code = CustomIslandPresetCodec.toBundleShareCode(currentBundle)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText(currentBundle.name, code))
                            Toast.makeText(context, "Bundle code copied", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code")
                    }
                    OutlinedButton(
                        enabled = chosenPresets.isNotEmpty(),
                        onClick = {
                            val slug = currentBundle.name.lowercase()
                                .replace(Regex("[^a-z0-9]+"), "-")
                                .trim('-')
                                .ifBlank { "custom-islands" }
                            exportFileLauncher.launch("$slug-bundle.json")
                        },
                    ) {
                        Icon(LarpIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export .json")
                    }
                    OutlinedButton(
                        enabled = chosenPresets.isNotEmpty(),
                        onClick = {
                            val code = CustomIslandPresetCodec.toBundleShareCode(currentBundle)
                            openWebCatalogSubmit(context, currentBundle.name, code)
                        },
                    ) {
                        Icon(LarpIcons.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Publish")
                    }
                }
            }
        },
        confirmButton = {
            if (allowSaveAsPresets) {
                Button(
                    enabled = chosenPresets.isNotEmpty(),
                    onClick = {
                        val saved = CustomIslandPresetStore.saveAllFromIslands(
                            items = chosenPresets.map { it.name to it.island },
                            description = description,
                        )
                        if (saved.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                "Saved ${saved.size} preset${if (saved.size == 1) "" else "s"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            onDismiss()
                        }
                    },
                ) {
                    Text("Save Presets")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = if (allowSaveAsPresets) {
            { TextButton(onClick = onDismiss) { Text("Close") } }
        } else null,
    )
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
                    OutlinedButton(onClick = {
                        val code = CustomIslandPresetCodec.toShareCode(currentPreset)
                        openWebCatalogSubmit(context, currentPreset.name, code)
                    }) {
                        Icon(LarpIcons.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Publish")
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
    onSaveOnly: (List<CustomIslandPreset>) -> Unit,
    onImportAndAddIslands: (List<CustomIslandPreset>) -> Unit,
) {
    val context = LocalContext.current
    var rawInput by rememberSaveable { mutableStateOf("") }
    val parsed = remember(rawInput) { CustomIslandPresetCodec.fromAnyShareInput(rawInput) }
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
        title = { Text("Import Preset or Bundle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Paste share code or JSON") },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Not a valid LarpDot preset or bundle") }
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
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(parsed.bundleName, style = MaterialTheme.typography.titleSmall)
                            if (parsed.description.isNotBlank()) {
                                Text(
                                    parsed.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (parsed.isBundle) {
                                Text(
                                    "${parsed.presets.size} island${if (parsed.presets.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                val names = parsed.presets.joinToString(", ") { it.name }
                                Text(
                                    names,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    parsed.presets.first().island.modeLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (parsed.omittedWidgets > 0) {
                                Text(
                                    "${parsed.omittedWidgets} app widget${if (parsed.omittedWidgets == 1) "" else "s"} omitted (device-specific).",
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
                        onSaveOnly(parsed.presets)
                        onDismiss()
                    }) {
                        Text(if (parsed.isBundle) "Save Presets" else "Save Only")
                    }
                }
                Button(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let {
                            onImportAndAddIslands(it.presets)
                            onDismiss()
                        }
                    },
                ) {
                    Text(if (parsed?.isBundle == true) "Add All" else "Add Island")
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

private fun shareBundleViaIntent(context: Context, bundle: CustomIslandPresetBundle) {
    val code = CustomIslandPresetCodec.toBundleShareCode(bundle)
    val shareText = buildString {
        append("LarpDot Island Bundle: ${bundle.name} (${bundle.presets.size} island${if (bundle.presets.size == 1) "" else "s"})")
        if (bundle.description.isNotBlank()) {
            append("\n${bundle.description}")
        }
        append("\n\n")
        append(code)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "LarpDot Bundle: ${bundle.name}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share bundle"))
    }
}

private fun openWebCatalogSubmit(context: Context, title: String, shareCode: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(title, shareCode))
    val url = CustomIslandCatalogClient.buildWebSubmitUrl(shareCode)
    val launched = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.isSuccess
    Toast.makeText(
        context,
        if (launched) "Opening catalog submit (code also copied)" else "Share code copied — open $LARPDOT_CATALOG_BASE_URL/presets/submit",
        Toast.LENGTH_SHORT,
    ).show()
}

@Composable
internal fun BrowseCatalogDialog(
    onDismiss: () -> Unit,
    onSaveOnly: (List<CustomIslandPreset>) -> Unit,
    onImportAndAddIslands: (List<CustomIslandPreset>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var debouncedSearch by rememberSaveable { mutableStateOf("") }
    var filterKind by rememberSaveable { mutableStateOf(CatalogFilterKind.ALL) }
    var sortOption by rememberSaveable { mutableStateOf(CatalogSortOption.POPULAR) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf<CatalogPage?>(null) }
    var items by remember { mutableStateOf<List<CatalogPresetEntry>>(emptyList()) }

    LaunchedEffect(search) {
        delay(280)
        debouncedSearch = search.trim()
    }

    LaunchedEffect(debouncedSearch, filterKind, sortOption, refreshNonce) {
        loading = true
        errorMessage = null
        val result = CustomIslandCatalogClient.fetchCatalog(
            search = debouncedSearch,
            kind = filterKind,
            sort = sortOption,
            page = 1,
        )
        result.onSuccess { page ->
            currentPage = page
            items = page.items
            errorMessage = null
        }.onFailure { err ->
            errorMessage = err.message ?: "Could not load catalog"
            items = emptyList()
            currentPage = null
        }
        loading = false
    }

    fun applyEntry(entry: CatalogPresetEntry, addToIslands: Boolean) {
        val parsed = CustomIslandPresetCodec.fromAnyShareInput(entry.shareCode)
        if (parsed == null) {
            Toast.makeText(context, "Could not decode preset", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch { CustomIslandCatalogClient.recordCopy(entry.slug) }
        if (addToIslands) {
            onImportAndAddIslands(parsed.presets)
            Toast.makeText(
                context,
                if (parsed.isBundle) "Added ${parsed.presets.size} islands" else "Added ${entry.name}",
                Toast.LENGTH_SHORT,
            ).show()
            onDismiss()
        } else {
            onSaveOnly(parsed.presets)
            Toast.makeText(
                context,
                if (parsed.isBundle) "Saved ${parsed.presets.size} presets" else "Saved ${entry.name}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community Catalog") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(80) },
                    label = { Text("Search presets or apps") },
                    singleLine = true,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            IconButton(onClick = { search = "" }) {
                                Icon(LarpIcons.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                LarpConnectedButtons(
                    options = CatalogFilterKind.entries.map { kind ->
                        LarpButtonOption(
                            label = kind.label,
                            checked = filterKind == kind,
                            onCheckedChange = { filterKind = kind },
                        )
                    },
                )
                LarpConnectedButtons(
                    options = CatalogSortOption.entries.map { sort ->
                        LarpButtonOption(
                            label = sort.label,
                            checked = sortOption == sort,
                            onCheckedChange = { sortOption = sort },
                        )
                    },
                )

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { refreshNonce++ }) {
                                Text("Retry")
                            }
                        }
                    }
                    items.isEmpty() -> {
                        Text(
                            text = if (debouncedSearch.isNotBlank()) "No presets matching \"$debouncedSearch\"." else "No community presets yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { it.slug }) { entry ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = if (entry.isBundle) "${entry.islandCount} islands" else entry.visibility.lowercase().replace('_', ' '),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        val meta = buildList {
                                            entry.appName?.let(::add)
                                            add("by ${entry.author}")
                                            if (entry.copies > 0) add("${entry.copies} copies")
                                        }.joinToString(" • ")
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (entry.description.isNotBlank()) {
                                            Text(
                                                text = entry.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            OutlinedButton(onClick = { applyEntry(entry, addToIslands = false) }) {
                                                Text(if (entry.isBundle) "Save All" else "Save")
                                            }
                                            Button(onClick = { applyEntry(entry, addToIslands = true) }) {
                                                Text(if (entry.isBundle) "Add All" else "Add Island")
                                            }
                                        }
                                    }
                                }
                            }
                            if (currentPage?.hasMore == true) {
                                item(key = "load_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (loadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    val nextPage = (currentPage?.page ?: 1) + 1
                                                    scope.launch {
                                                        loadingMore = true
                                                        CustomIslandCatalogClient.fetchCatalog(
                                                            search = debouncedSearch,
                                                            kind = filterKind,
                                                            sort = sortOption,
                                                            page = nextPage,
                                                        ).onSuccess { page ->
                                                            currentPage = page
                                                            items = (items + page.items).distinctBy { it.slug }
                                                        }
                                                        loadingMore = false
                                                    }
                                                },
                                            ) {
                                                Text("Load more")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "$LARPDOT_CATALOG_BASE_URL/presets".toUri()))
                    }
                },
            ) {
                Text("Open Website")
            }
        },
    )
}
