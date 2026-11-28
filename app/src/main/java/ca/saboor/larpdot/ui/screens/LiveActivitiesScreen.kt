package ca.saboor.larpdot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.NotificationAccessService
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ShizukuEarbudBattery
import ca.saboor.larpdot.service.ShizukuLiveUpdateStatus
import ca.saboor.larpdot.service.ShizukuLiveUpdateSuppressor
import ca.saboor.larpdot.service.SystemEventManager
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.LiveActivitiesPreview
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.roundToInt

private data class ActivityListItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val autoExpand: Boolean,
    val onAutoExpandChange: (Boolean) -> Unit,
    val expandOnUpdates: Boolean? = null,
    val onExpandOnUpdatesChange: ((Boolean) -> Unit)? = null,
    val permanent: Boolean? = null,
    val onPermanentChange: ((Boolean) -> Unit)? = null,
    val extraContent: (@Composable () -> Unit)? = null,
)

@Composable
fun LiveActivitiesScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val shizukuLiveUpdateStatus by ShizukuLiveUpdateSuppressor.status.collectAsState()
    val shizukuSuppressedCount by ShizukuLiveUpdateSuppressor.suppressedCount.collectAsState()
    val shizukuLastError by ShizukuLiveUpdateSuppressor.lastError.collectAsState()
    var hasBluetoothPermission by remember { mutableStateOf(SystemEventManager.hasBluetoothPermission(context)) }
    var expandedRowId by rememberSaveable { mutableStateOf<String?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasBluetoothPermission = granted || SystemEventManager.hasBluetoothPermission(context)
        if (hasBluetoothPermission) SystemEventManager.syncConnectedBluetoothDevices(context)
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.getNotificationActivitySettings(context)
        hasBluetoothPermission = SystemEventManager.hasBluetoothPermission(context)
        if (ShizukuLiveUpdateSuppressor.isSupportedApi()) {
            ShizukuLiveUpdateSuppressor.initialize(context)
            ShizukuLiveUpdateSuppressor.refreshStatus()
        }
    }

    val ongoingItems = listOf(
        ActivityListItem(
            id = "live_updates",
            title = "Live Updates",
            subtitle = "Android live activities",
            icon = LarpIcons.NotificationsActive,
            checked = settings.liveUpdates,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(liveUpdates = it))
            },
            autoExpand = settings.autoExpandLiveUpdates,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandLiveUpdates = it))
            },
            expandOnUpdates = settings.autoExpandLiveUpdatesOnChange,
            onExpandOnUpdatesChange = {
                OverlayPreferences.setNotificationActivitySettings(
                    context,
                    settings.copy(autoExpandLiveUpdatesOnChange = it),
                )
            },
        ),
        ActivityListItem(
            id = "progress",
            title = "Progress",
            subtitle = "Downloads & installs",
            icon = LarpIcons.Download,
            checked = settings.progress,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(progress = it))
            },
            autoExpand = settings.autoExpandProgress,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandProgress = it))
            },
        ),
        ActivityListItem(
            id = "navigation",
            title = "Navigation",
            subtitle = "Turn-by-turn trips",
            icon = LarpIcons.Navigation,
            checked = settings.navigation,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(navigation = it))
            },
            autoExpand = settings.autoExpandNavigation,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandNavigation = it))
            },
        ),
        ActivityListItem(
            id = "calls",
            title = "Calls",
            subtitle = "Active call status",
            icon = LarpIcons.Call,
            checked = settings.calls,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(calls = it))
            },
            autoExpand = settings.autoExpandCalls,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandCalls = it))
            },
        ),
        ActivityListItem(
            id = "timers",
            title = "Timers",
            subtitle = "Countdowns & timers",
            icon = LarpIcons.Timer,
            checked = settings.timers,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(timers = it))
            },
            autoExpand = settings.autoExpandTimers,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandTimers = it))
            },
        ),
    )

    val shortTermItems = listOf(
        ActivityListItem(
            id = "charging",
            title = "Charging",
            subtitle = "Watts & time left",
            icon = LarpIcons.BatteryChargingFull,
            checked = settings.batteryCharging,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(batteryCharging = it))
            },
            autoExpand = settings.autoExpandCharging,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandCharging = it))
            },
            permanent = settings.permanentCharging,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentCharging = it))
            },
        ),
        ActivityListItem(
            id = "low_battery",
            title = "Low Battery",
            subtitle = "At ${settings.lowBatteryThresholdPercent}%",
            icon = LarpIcons.BatteryAlert,
            checked = settings.batteryLow,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(batteryLow = it))
            },
            autoExpand = settings.autoExpandLowBattery,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandLowBattery = it))
            },
            permanent = settings.permanentLowBattery,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentLowBattery = it))
            },
            extraContent = {
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
            },
        ),
        ActivityListItem(
            id = "charge_limit",
            title = "Charge Limit",
            subtitle = "At ${settings.chargeLimitPercent}%",
            icon = LarpIcons.BatterySaver,
            checked = settings.chargeLimitAlert,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(chargeLimitAlert = it))
            },
            autoExpand = settings.autoExpandChargeLimit,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandChargeLimit = it))
            },
            extraContent = {
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
            },
        ),
        ActivityListItem(
            id = "sound_mode",
            title = "Sound Mode",
            subtitle = "Ring, vibrate, silent",
            icon = LarpIcons.VolumeUp,
            checked = settings.soundMode,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(soundMode = it))
            },
            autoExpand = settings.autoExpandSoundMode,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandSoundMode = it))
            },
            permanent = settings.permanentSoundMode,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentSoundMode = it))
            },
        ),
        ActivityListItem(
            id = "dnd",
            title = "Do Not Disturb",
            subtitle = "Silence state",
            icon = LarpIcons.DoNotDisturbOn,
            checked = settings.dnd,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(dnd = it))
            },
            autoExpand = settings.autoExpandDnd,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandDnd = it))
            },
            permanent = settings.permanentDnd,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentDnd = it))
            },
        ),
        ActivityListItem(
            id = "bluetooth",
            title = "Bluetooth",
            subtitle = "Watch & earbuds",
            icon = LarpIcons.Bluetooth,
            checked = settings.bluetooth,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(bluetooth = it))
            },
            autoExpand = settings.autoExpandBluetooth,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandBluetooth = it))
            },
            permanent = settings.permanentBluetooth,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentBluetooth = it))
            },
        ),
        ActivityListItem(
            id = "audio_output",
            title = "Audio Output",
            subtitle = "Playback device",
            icon = LarpIcons.Headphones,
            checked = settings.audioOutput,
            onCheckedChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(audioOutput = it))
            },
            autoExpand = settings.autoExpandAudioOutput,
            onAutoExpandChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(autoExpandAudioOutput = it))
            },
            permanent = settings.permanentAudioOutput,
            onPermanentChange = {
                OverlayPreferences.setNotificationActivitySettings(context, settings.copy(permanentAudioOutput = it))
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
                        ActivityList(
                            items = ongoingItems,
                            expandedRowId = expandedRowId,
                            onToggleExpandRow = { id ->
                                expandedRowId = if (expandedRowId == id) null else id
                            },
                        )

                        if (ShizukuLiveUpdateSuppressor.isSupportedApi()) {
                            LarpDivider()
                            val needsShizuku = settings.hideStatusBarLiveUpdates &&
                                shizukuLiveUpdateStatus != ShizukuLiveUpdateStatus.READY
                            LarpSwitchRow(
                                title = "Hide Status Bar Live Updates",
                                subtitle = when {
                                    !settings.hideStatusBarLiveUpdates ->
                                        "Temporarily revoke Android Live Updates via Shizuku while active"
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.READY && !shizukuLastError.isNullOrBlank() ->
                                        "Shizuku error: $shizukuLastError"
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.READY && shizukuSuppressedCount > 0 ->
                                        "Suppressing $shizukuSuppressedCount active Live Update app${if (shizukuSuppressedCount == 1) "" else "s"}"
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.READY ->
                                        "Revokes promotion on live update and restores it when dismissed"
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION ->
                                        "Allow LarpDot in Shizuku to manage Live Updates"
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.PERMISSION_DENIED ->
                                        "Shizuku permission denied — enable LarpDot in Shizuku"
                                    else -> "Start Shizuku to manage Android Live Updates"
                                },
                                icon = LarpIcons.Block,
                                checked = settings.hideStatusBarLiveUpdates,
                                onCheckedChange = { enabled ->
                                    OverlayPreferences.setNotificationActivitySettings(
                                        context,
                                        settings.copy(hideStatusBarLiveUpdates = enabled),
                                    )
                                    if (!enabled) {
                                        ShizukuLiveUpdateSuppressor.restoreAll(context)
                                    } else if (shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION) {
                                        ShizukuLiveUpdateSuppressor.requestPermission()
                                    } else if (shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.READY) {
                                        NotificationAccessService.reconcileLiveUpdates()
                                    }
                                },
                            )
                            AnimatedVisibility(
                                visible = settings.hideStatusBarLiveUpdates,
                                enter = LarpMotion.reveal,
                                exit = LarpMotion.conceal,
                            ) {
                                LarpSwitchRow(
                                    title = "Minimize Notification",
                                    subtitle = "Also temporarily minimize the notification channel until the live update ends",
                                    icon = LarpIcons.Remove,
                                    checked = settings.minimizeStatusBarLiveUpdates,
                                    onCheckedChange = { minimize ->
                                        OverlayPreferences.setNotificationActivitySettings(
                                            context,
                                            settings.copy(minimizeStatusBarLiveUpdates = minimize),
                                        )
                                        if (!minimize) {
                                            ShizukuLiveUpdateSuppressor.restoreMinimizedChannels(context)
                                        } else if (shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.READY) {
                                            NotificationAccessService.reconcileLiveUpdates()
                                        }
                                    },
                                )
                            }
                            if (needsShizuku) {
                                val requestPermission =
                                    shizukuLiveUpdateStatus == ShizukuLiveUpdateStatus.NEEDS_PERMISSION
                                val launchIntent = if (!requestPermission) {
                                    ShizukuEarbudBattery.managerLaunchIntent(context)
                                } else null
                                if (requestPermission || launchIntent != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (requestPermission) {
                                                ShizukuLiveUpdateSuppressor.requestPermission()
                                            } else {
                                                launchIntent?.let(context::startActivity)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (requestPermission) "Allow Shizuku" else "Open Shizuku")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (settings.bluetooth && !hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                LarpCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LarpIcons.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nearby Devices permission",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Needed for Bluetooth connections and battery levels.",
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

        item {
            SectionHeader(title = "Status Alerts")
            LarpCard {
                ActivityList(
                    items = shortTermItems,
                    expandedRowId = expandedRowId,
                    onToggleExpandRow = { id ->
                        expandedRowId = if (expandedRowId == id) null else id
                    },
                )
            }
        }

        item {
            SectionHeader(title = "Appearance")
            LarpCard {
                LarpSwitchRow(
                    title = "Radial Progress",
                    subtitle = "Ring around progress",
                    icon = LarpIcons.DonutLarge,
                    checked = settings.progressOutline,
                    onCheckedChange = {
                        OverlayPreferences.setNotificationActivitySettings(context, settings.copy(progressOutline = it))
                    },
                )
                LarpDivider()
                LarpSwitchRow(
                    title = "App Icon Color",
                    subtitle = "Use app color on icons",
                    icon = LarpIcons.Palette,
                    checked = settings.useAppColorForIcons,
                    onCheckedChange = {
                        OverlayPreferences.setNotificationActivitySettings(
                            context,
                            settings.copy(useAppColorForIcons = it),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityList(
    items: List<ActivityListItem>,
    expandedRowId: String?,
    onToggleExpandRow: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            ActivityListRow(
                item = item,
                expanded = expandedRowId == item.id,
                onToggleExpanded = { onToggleExpandRow(item.id) },
            )
        }
    }
}

@Composable
private fun ActivityListRow(
    item: ActivityListItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val containerColor = if (item.checked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dynamicSubtitle = remember(
        item.subtitle,
        item.checked,
        item.permanent,
        item.autoExpand,
        item.expandOnUpdates,
    ) {
        if (!item.checked) {
            item.subtitle
        } else {
            buildList {
                add(item.subtitle)
                if (item.permanent == true) add("Pinned")
                if (item.autoExpand) add("Auto-expand")
                if (item.expandOnUpdates == true) add("On updates")
            }.joinToString(" · ")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dynamicSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.permanent != null && item.onPermanentChange != null) {
                        ActivityQuickIconToggle(
                            icon = LarpIcons.PushPin,
                            checked = item.permanent,
                            enabled = item.checked,
                            contentDescription = "Keep ${item.title} pinned",
                            onCheckedChange = item.onPermanentChange,
                        )
                    }
                    if (item.expandOnUpdates != null && item.onExpandOnUpdatesChange != null) {
                        ActivityQuickIconToggle(
                            icon = LarpIcons.RestartAlt,
                            checked = item.expandOnUpdates,
                            enabled = item.checked,
                            contentDescription = "Expand ${item.title} on updates",
                            onCheckedChange = item.onExpandOnUpdatesChange,
                        )
                    }
                    ActivityQuickIconToggle(
                        icon = LarpIcons.OpenInFull,
                        checked = item.autoExpand,
                        enabled = item.checked,
                        contentDescription = "Auto-expand ${item.title}",
                        onCheckedChange = item.onAutoExpandChange,
                    )
                    Spacer(Modifier.width(2.dp))
                    Switch(
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = LarpMotion.reveal,
                exit = LarpMotion.conceal,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LarpDivider()
                    LarpSwitchRow(
                        title = "Expand automatically",
                        subtitle = "Briefly expand when this activity starts",
                        icon = LarpIcons.OpenInFull,
                        checked = item.autoExpand,
                        enabled = item.checked,
                        onCheckedChange = item.onAutoExpandChange,
                    )
                    if (item.expandOnUpdates != null && item.onExpandOnUpdatesChange != null) {
                        LarpSwitchRow(
                            title = "Expand on updates",
                            subtitle = "Re-expand whenever live update content changes",
                            icon = LarpIcons.RestartAlt,
                            checked = item.expandOnUpdates,
                            enabled = item.checked,
                            onCheckedChange = item.onExpandOnUpdatesChange,
                        )
                    }
                    if (item.permanent != null && item.onPermanentChange != null) {
                        LarpSwitchRow(
                            title = "Keep pinned",
                            subtitle = "Stay in the island instead of auto-dismissing",
                            icon = LarpIcons.PushPin,
                            checked = item.permanent,
                            enabled = item.checked,
                            onCheckedChange = item.onPermanentChange,
                        )
                    }
                    if (item.extraContent != null) {
                        LarpDivider()
                        item.extraContent.invoke()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityQuickIconToggle(
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val active = checked && enabled
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = if (checked) "On" else "Off"
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
