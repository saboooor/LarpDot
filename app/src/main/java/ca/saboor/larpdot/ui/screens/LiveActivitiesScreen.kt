package ca.saboor.larpdot.ui.screens

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.QuickTimerManager
import ca.saboor.larpdot.service.QuickTimerMode
import ca.saboor.larpdot.service.QuickTimerState
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.service.SystemEventManager
import ca.saboor.larpdot.service.formatQuickTimerDuration
import ca.saboor.larpdot.ui.components.AccentCard
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.LarpToggleTile
import ca.saboor.larpdot.ui.components.LiveActivitiesPreview
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

private data class ActivityToggleItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val permanent: Boolean? = null,
    val onPermanentChange: ((Boolean) -> Unit)? = null,
)

@Composable
fun LiveActivitiesScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    var hasBluetoothPermission by remember { mutableStateOf(SystemEventManager.hasBluetoothPermission(context)) }
    var hasScreenshotPermission by remember { mutableStateOf(ScreenshotPocketManager.hasPermission(context)) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasBluetoothPermission = granted || SystemEventManager.hasBluetoothPermission(context)
        if (hasBluetoothPermission) SystemEventManager.syncConnectedBluetoothDevices(context)
    }

    val screenshotPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasScreenshotPermission = granted || ScreenshotPocketManager.hasPermission(context)
        if (hasScreenshotPermission) ScreenshotPocketManager.syncObserver(context)
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.getNotificationActivitySettings(context)
        hasBluetoothPermission = SystemEventManager.hasBluetoothPermission(context)
        hasScreenshotPermission = ScreenshotPocketManager.hasPermission(context)
    }

    val ongoingItems = listOf(
        ActivityToggleItem(
            title = "Progress",
            subtitle = "Downloads & installs",
            icon = LarpIcons.Download,
            checked = settings.progress,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(progress = it))
            },
        ),
        ActivityToggleItem(
            title = "Navigation",
            subtitle = "Turn-by-turn trips",
            icon = LarpIcons.Navigation,
            checked = settings.navigation,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(navigation = it))
            },
        ),
        ActivityToggleItem(
            title = "Calls",
            subtitle = "Active call status",
            icon = LarpIcons.Call,
            checked = settings.calls,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(calls = it))
            },
        ),
        ActivityToggleItem(
            title = "Timers",
            subtitle = "Live chronometers",
            icon = LarpIcons.Timer,
            checked = settings.timers,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(timers = it))
            },
        ),
    )

    val shortTermItems = listOf(
        ActivityToggleItem(
            title = "Charging",
            subtitle = "Watts & time left",
            icon = LarpIcons.BatteryChargingFull,
            checked = settings.batteryCharging,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(batteryCharging = it))
            },
            permanent = settings.permanentCharging,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentCharging = it))
            },
        ),
        ActivityToggleItem(
            title = "Low Battery",
            subtitle = "Alert at ${settings.lowBatteryThresholdPercent}%",
            icon = LarpIcons.BatteryAlert,
            checked = settings.batteryLow,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(batteryLow = it))
            },
            permanent = settings.permanentLowBattery,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentLowBattery = it))
            },
        ),
        ActivityToggleItem(
            title = "Charge Limit",
            subtitle = "Alert at ${settings.chargeLimitPercent}%",
            icon = LarpIcons.BatterySaver,
            checked = settings.chargeLimitAlert,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(chargeLimitAlert = it))
            },
        ),
        ActivityToggleItem(
            title = "Sound Mode",
            subtitle = "Ring, vibrate, silent",
            icon = LarpIcons.VolumeUp,
            checked = settings.soundMode,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(soundMode = it))
            },
            permanent = settings.permanentSoundMode,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentSoundMode = it))
            },
        ),
        ActivityToggleItem(
            title = "Do Not Disturb",
            subtitle = "Focus & silence state",
            icon = LarpIcons.DoNotDisturbOn,
            checked = settings.dnd,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(dnd = it))
            },
            permanent = settings.permanentDnd,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentDnd = it))
            },
        ),
        ActivityToggleItem(
            title = "Volume HUD",
            subtitle = "Media volume pill",
            icon = LarpIcons.Tune,
            checked = settings.volumeHud,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(volumeHud = it))
            },
        ),
        ActivityToggleItem(
            title = "Bluetooth",
            subtitle = "Watch & earbuds",
            icon = LarpIcons.Bluetooth,
            checked = settings.bluetooth,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(bluetooth = it))
            },
            permanent = settings.permanentBluetooth,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentBluetooth = it))
            },
        ),
        ActivityToggleItem(
            title = "Audio Output",
            subtitle = "Route switch alerts",
            icon = LarpIcons.Headphones,
            checked = settings.audioOutput,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(audioOutput = it))
            },
            permanent = settings.permanentAudioOutput,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentAudioOutput = it))
            },
        ),
        ActivityToggleItem(
            title = "Screenshot Pocket",
            subtitle = "1-tap share & delete",
            icon = LarpIcons.Screenshot,
            checked = settings.screenshotPocket,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(screenshotPocket = it))
            },
            permanent = settings.permanentScreenshotPocket,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentScreenshotPocket = it))
            },
        ),
    )

    val appearanceItems = listOf(
        ActivityToggleItem(
            title = "Radial Progress",
            subtitle = "Radial ring on progress",
            icon = LarpIcons.DonutLarge,
            checked = settings.progressOutline,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(progressOutline = it))
            },
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "live_activities_preview") {
            LiveActivitiesPreview(cutoutInfo = cutoutInfo)
        }

        item {
            SectionHeader(title = "Live Activities")
            LarpCard {
                LarpSwitchRow(
                    title = "Show Live Activities",
                    subtitle = "Turn ongoing notifications into island activities",
                    icon = LarpIcons.NotificationsActive,
                    checked = settings.enabled,
                    onCheckedChange = {
                        OverlayPreferences.setNotificationActivitySettings(context, settings.copy(enabled = it))
                    },
                )

                AnimatedVisibility(
                    visible = settings.enabled,
                    enter = LarpMotion.reveal,
                    exit = LarpMotion.conceal,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LarpDivider()
                        ActivityToggleGrid(items = ongoingItems)
                    }
                }
            }
        }

        if (settings.bluetooth && !hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                AccentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LarpIcons.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nearby Devices permission needed",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Android requires Nearby Devices access to show Pixel Watch, earbud connections, and battery levels.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Allow Nearby Devices")
                    }
                }
            }
        }

        if (settings.screenshotPocket && !hasScreenshotPermission) {
            item {
                AccentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LarpIcons.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Photos access needed for Screenshot Pocket",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Allow read access so new screenshots automatically appear in the island with 1-tap Share, Copy, and Delete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { screenshotPermissionLauncher.launch(ScreenshotPocketManager.requiredPermission()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Allow Photos Access")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Short-Term Status Alerts")
            LarpCard {
                LarpSwitchRow(
                    title = "Show Expanded",
                    subtitle = "Open short-term alerts expanded instead of minimized",
                    icon = LarpIcons.OpenInFull,
                    checked = settings.expandShortTermAlerts,
                    onCheckedChange = {
                        OverlayPreferences.setNotificationActivitySettings(
                            context,
                            settings.copy(expandShortTermAlerts = it),
                        )
                    },
                )

                LarpDivider()

                ActivityToggleGrid(items = shortTermItems)

                AnimatedVisibility(
                    visible = settings.batteryLow,
                    enter = LarpMotion.reveal,
                    exit = LarpMotion.conceal,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LarpDivider()
                        LarpSliderRow(
                            title = "Low battery alert threshold",
                            valueLabel = "${settings.lowBatteryThresholdPercent}%",
                            value = settings.lowBatteryThresholdPercent.toFloat(),
                            valueRange = 10f..30f,
                            steps = 3,
                            onValueChange = { raw ->
                                val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(10, 30)
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(lowBatteryThresholdPercent = snapped),
                                )
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = settings.chargeLimitAlert,
                    enter = LarpMotion.reveal,
                    exit = LarpMotion.conceal,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LarpDivider()
                        LarpSliderRow(
                            title = "Charge limit target",
                            valueLabel = "${settings.chargeLimitPercent}%",
                            value = settings.chargeLimitPercent.toFloat(),
                            valueRange = 70f..100f,
                            steps = 5,
                            onValueChange = { raw ->
                                val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(70, 100)
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(chargeLimitPercent = snapped),
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Quick Timer & Stopwatch")
            QuickTimerCard()
        }

        item {
            SectionHeader(title = "Island Pocket")
            IslandPocketCard(context = context)
        }

        item {
            SectionHeader(title = "Appearance")
            LarpCard {
                ActivityToggleGrid(items = appearanceItems)
            }
        }
    }
}

@Composable
private fun QuickTimerCard() {
    val timerState by QuickTimerManager.state.collectAsState()
    val presets = listOf(1, 3, 5, 10, 15, 25)
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("2") }
    var customSecondsText by remember { mutableStateOf("30") }
    var customLabelText by remember { mutableStateOf("") }

    LarpCard {
        Text(
            text = when (val state = timerState) {
                QuickTimerState.Idle -> "Start a countdown timer or stopwatch right in your Dynamic Island"
                is QuickTimerState.Running -> "${state.label} • ${formatQuickTimerDuration(state.displayMs)}"
                is QuickTimerState.Paused -> "${state.label} paused at ${formatQuickTimerDuration(state.pausedDisplayMs)}"
                is QuickTimerState.Finished -> "${state.label} finished!"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (timerState is QuickTimerState.Idle) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )

        LarpConnectedButtons(
            options = presets.map { minutes ->
                LarpButtonOption(
                    checked = false,
                    label = "${minutes}m",
                    onCheckedChange = { QuickTimerManager.startCountdown(minutes) },
                )
            } + listOf(
                LarpButtonOption(
                    checked = false,
                    label = "Custom",
                    onCheckedChange = { showCustomDialog = true },
                ),
                LarpButtonOption(
                    checked = (timerState as? QuickTimerState.Running)?.mode == QuickTimerMode.STOPWATCH,
                    label = "Watch",
                    onCheckedChange = { QuickTimerManager.startStopwatch() },
                ),
            ),
            maxButtonsPerRow = 4,
        )

        if (timerState !is QuickTimerState.Idle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val state = timerState) {
                    is QuickTimerState.Running -> {
                        FilledTonalButton(
                            onClick = { QuickTimerManager.togglePause() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Pause")
                        }
                        if (state.mode == QuickTimerMode.COUNTDOWN) {
                            FilledTonalButton(onClick = { QuickTimerManager.addOneMinute() }) {
                                Text("+1m")
                            }
                        }
                        FilledTonalButton(onClick = { QuickTimerManager.stop() }) {
                            Text("Stop")
                        }
                    }
                    is QuickTimerState.Paused -> {
                        FilledTonalButton(
                            onClick = { QuickTimerManager.togglePause() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Resume")
                        }
                        FilledTonalButton(onClick = { QuickTimerManager.stop() }) {
                            Text("Stop")
                        }
                    }
                    is QuickTimerState.Finished -> {
                        FilledTonalButton(
                            onClick = { QuickTimerManager.restart() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Restart")
                        }
                        FilledTonalButton(onClick = { QuickTimerManager.stop() }) {
                            Text("Dismiss")
                        }
                    }
                    QuickTimerState.Idle -> Unit
                }
            }
        }
    }

    if (showCustomDialog) {
        val mins = customMinutesText.toIntOrNull()?.coerceIn(0, 240) ?: 0
        val secs = customSecondsText.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val totalSeconds = mins * 60 + secs
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            icon = { Icon(LarpIcons.Timer, contentDescription = null) },
            title = { Text("Custom timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { input ->
                                customMinutesText = input.filter(Char::isDigit).take(3)
                            },
                            label = { Text("Min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customSecondsText,
                            onValueChange = { input ->
                                customSecondsText = input.filter(Char::isDigit).take(2)
                            },
                            label = { Text("Sec") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = customLabelText,
                        onValueChange = { if (it.length <= 24) customLabelText = it },
                        label = { Text("Label (optional)") },
                        placeholder = { Text("e.g. Tea, Pomodoro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (totalSeconds in 1..4) {
                        Text(
                            text = "Minimum timer duration is 5 seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = totalSeconds >= 5,
                    onClick = {
                        QuickTimerManager.startCountdownSeconds(
                            totalSeconds = totalSeconds,
                            label = customLabelText.ifBlank { null },
                        )
                        showCustomDialog = false
                    },
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun IslandPocketCard(context: Context) {
    val shortTerms by NotificationActivityState.shortTermActivities.collectAsState()
    val pocketItems = remember(shortTerms) {
        shortTerms.filter { it.kind == NotificationActivityKind.SCREENSHOT }
    }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    LarpCard {
        Text(
            text = "Pin clipboard snippets, links, or quick notes into your Dynamic Island for 1-tap copy and share.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val text = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                    if (text.isNotEmpty()) {
                        ScreenshotPocketManager.holdSharedText(context, text)
                        Toast.makeText(context, "Clipboard pinned to Island", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard has no text to pin", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hold Clipboard", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    noteTitle = ""
                    noteText = ""
                    showNoteDialog = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(LarpIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Quick Note", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (pocketItems.isNotEmpty()) {
            LarpDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pocketItems.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = LarpIcons.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.text?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { ScreenshotPocketManager.dismissHeldItem(item.key) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = LarpIcons.Close,
                                    contentDescription = "Dismiss ${item.title}",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        val cleanText = noteText.trim()
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            icon = { Icon(LarpIcons.PushPin, contentDescription = null) },
            title = { Text("Pin note to Island") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { if (it.length <= 40) noteTitle = it },
                        label = { Text("Title (optional)") },
                        placeholder = { Text("e.g. Gate B14, Verification Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= 500) noteText = it },
                        label = { Text("Note or link") },
                        placeholder = { Text("Text or URL to hold in the island") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cleanText.isNotEmpty(),
                    onClick = {
                        ScreenshotPocketManager.holdSharedText(
                            context = context,
                            text = cleanText,
                            subject = noteTitle.ifBlank { null },
                        )
                        showNoteDialog = false
                        Toast.makeText(context, "Pinned to Dynamic Island", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Pin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ActivityToggleGrid(
    items: List<ActivityToggleItem>,
    columns: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    ActivityToggleTile(
                        item = item,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActivityToggleTile(
    item: ActivityToggleItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LarpToggleTile(
            title = item.title,
            subtitle = item.subtitle,
            icon = item.icon,
            checked = item.checked,
            onCheckedChange = item.onCheckedChange,
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.permanent != null && item.onPermanentChange != null) {
            ToggleButton(
                checked = item.permanent,
                enabled = item.checked,
                onCheckedChange = { item.onPermanentChange.invoke(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shapes = ToggleButtonShapes(
                    shape = RoundedCornerShape(10.dp),
                    pressedShape = RoundedCornerShape(8.dp),
                    checkedShape = RoundedCornerShape(16.dp),
                ),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = LarpIcons.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (item.permanent) "Pinned" else "Auto-dismiss",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
