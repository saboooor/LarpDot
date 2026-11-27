package ca.saboor.larpdot.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandNotifications
import ca.saboor.larpdot.service.CustomIslandStore
import ca.saboor.larpdot.service.CustomIslandTapAction
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.overlay.customIslandSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CustomIslandTapSettings(island: CustomIsland) {
    val options = if (island.isSwipeDownOnly) {
        listOf(CustomIslandTapAction.OPEN_APP, CustomIslandTapAction.NONE)
    } else CustomIslandTapAction.entries
    val selected = island.tapAction.takeIf { it in options } ?: CustomIslandTapAction.NONE
    LarpCard {
        LarpConnectedButtons(
            options = options.map { action ->
                LarpButtonOption(action.label, checked = selected == action) {
                    CustomIslandStore.setTapAction(island.id, action)
                }
            },
            scrollable = true,
        )
        when (selected) {
            CustomIslandTapAction.OPEN_APP -> CustomTapAppOption(island)
            CustomIslandTapAction.OPEN_NOTIFICATION, CustomIslandTapAction.RUN_NOTIFICATION_ACTION -> {
                val hasNotificationSource = island.visibility == CustomIslandVisibility.CONDITIONAL &&
                    (island.condition == CustomIslandCondition.LIVE_ACTIVITY || island.condition == CustomIslandCondition.NOTIFICATION)
                if (!hasNotificationSource) {
                    Text("Choose a Notification or Live Activity condition to use this action.", style = MaterialTheme.typography.bodySmall)
                } else if (island.tapAction == CustomIslandTapAction.RUN_NOTIFICATION_ACTION) {
                    CustomTapNotificationAction(island)
                }
            }
            else -> Unit
        }
        if (island.hasExpandedContent && !island.isSwipeDownOnly) {
            Text("Long-press to open the expanded island.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomTapNotificationAction(island: CustomIsland) {
    val recent by CustomIslandNotifications.recent.collectAsState()
    val sources = customIslandSources(island)
    val activeLabels = sources.notificationActions.map { it.title }
    val recentLabels = if (island.condition == CustomIslandCondition.NOTIFICATION) {
        recent.filter(island.notificationRule::matches).flatMap { it.actionLabels }
    } else emptyList()
    val labels = (activeLabels + recentLabels).distinct()
    OutlinedTextField(
        island.notificationActionTitle,
        { CustomIslandStore.setNotificationActionTitle(island.id, it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Action label") },
        singleLine = true,
    )
    if (labels.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEach { label ->
                TextButton(onClick = { CustomIslandStore.setNotificationActionTitle(island.id, label) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun CustomTapAppOption(island: CustomIsland) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val appName = remember(island.tapAppPackage, context) {
        island.tapAppPackage?.let { packageName ->
            val pm = context.packageManager
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
        }
    }
    OutlinedButton(onClick = { open = true }) { Text(appName ?: "Choose App") }
    if (appName != null) {
        TextButton(onClick = { CustomIslandStore.setTapAppPackage(island.id, null) }) { Text("Use Source App") }
    } else {
        Text("Defaults to the app supplying the island’s content.", style = MaterialTheme.typography.bodySmall)
    }
    if (open) {
        CustomTapAppPicker(onDismiss = { open = false }) {
            CustomIslandStore.setTapAppPackage(island.id, it)
            open = false
        }
    }
}

@Composable
private fun CustomTapAppPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<Pair<String, String>>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            runCatching {
                pm.queryIntentActivities(intent, 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = apps.orEmpty().filter { (id, label) ->
        label.contains(query, ignoreCase = true) || id.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search apps") }, singleLine = true)
                if (filtered.isEmpty()) Text(if (apps == null) "Loading apps…" else "No matching apps")
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.first }) { (id, label) ->
                        TextButton(onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
