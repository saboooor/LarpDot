package ca.saboor.larpdot.ui.screens

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
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
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.SystemEventManager
import ca.saboor.larpdot.ui.components.LarpCard
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

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasBluetoothPermission = granted || SystemEventManager.hasBluetoothPermission(context)
        if (hasBluetoothPermission) SystemEventManager.syncConnectedBluetoothDevices(context)
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.getNotificationActivitySettings(context)
        hasBluetoothPermission = SystemEventManager.hasBluetoothPermission(context)
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
            subtitle = "Countdowns & timers",
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
            subtitle = "At ${settings.lowBatteryThresholdPercent}%",
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
            subtitle = "At ${settings.chargeLimitPercent}%",
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
            subtitle = "Silence state",
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
            subtitle = "Playback device",
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
    )

    val appearanceItems = listOf(
        ActivityToggleItem(
            title = "Radial Progress",
            subtitle = "Ring around progress",
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
                LarpSwitchRow(
                    title = "Expand Automatically",
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
            SectionHeader(title = "Appearance")
            LarpCard {
                ActivityToggleGrid(items = appearanceItems)
            }
        }
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
