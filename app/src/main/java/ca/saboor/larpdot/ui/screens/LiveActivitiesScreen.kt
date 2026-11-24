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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
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
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.service.SystemEventManager
import ca.saboor.larpdot.ui.components.AccentCard
import ca.saboor.larpdot.ui.components.LarpCard
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
fun LiveActivitiesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
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
            subtitle = "Low power alert",
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
        ActivityToggleItem(
            title = "Mini Pill Dots",
            subtitle = "Info on side dots",
            icon = LarpIcons.MoreHoriz,
            checked = showDotRightSideInfo,
            onCheckedChange = {
                OverlayPreferences.setShowDotRightSideInfoEnabled(context, it)
            },
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(title = "Live Activities")
            LarpCard {
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
                            imageVector = LarpIcons.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Show Live Activities",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Turn ongoing notifications into island activities",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = {
                            OverlayPreferences.setNotificationActivitySettings(
                                context,
                                settings.copy(enabled = it),
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = settings.enabled,
                    enter = LarpMotion.reveal,
                    exit = LarpMotion.conceal,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                            imageVector = LarpIcons.OpenInFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Show Expanded",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                            )
                            Text(
                                text = "Open short-term alerts expanded instead of minimized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = settings.expandShortTermAlerts,
                        onCheckedChange = {
                            OverlayPreferences.setNotificationActivitySettings(
                                context,
                                settings.copy(expandShortTermAlerts = it),
                            )
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ActivityToggleGrid(items = shortTermItems)

                AnimatedVisibility(
                    visible = settings.chargeLimitAlert,
                    enter = LarpMotion.reveal,
                    exit = LarpMotion.conceal,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Charge limit target",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${settings.chargeLimitPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val limitSliderState = remember {
                            SliderState(
                                value = settings.chargeLimitPercent.toFloat(),
                                steps = 5,
                                trackRange = 70f..100f,
                            )
                        }
                        limitSliderState.value = settings.chargeLimitPercent.toFloat()
                        Slider(
                            state = limitSliderState,
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
        ToggleButton(
            checked = item.checked,
            onCheckedChange = item.onCheckedChange,
            modifier = Modifier.fillMaxWidth(),
            shapes = ToggleButtonShapes(
                shape = RoundedCornerShape(16.dp),
                pressedShape = RoundedCornerShape(12.dp),
                checkedShape = RoundedCornerShape(22.dp),
            ),
            colors = ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.permanent != null && item.onPermanentChange != null) {
            FilterChip(
                selected = item.permanent,
                enabled = item.checked,
                onClick = { item.onPermanentChange.invoke(!item.permanent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                label = {
                    Text(
                        text = if (item.permanent) "Pinned" else "Auto-dismiss",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = LarpIcons.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}
