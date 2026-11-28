package ca.saboor.larpdot.ui.components

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.DotAccessibilityService
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.ShizukuBatteryStatus
import ca.saboor.larpdot.service.ShizukuEarbudBattery
import ca.saboor.larpdot.service.SystemEventManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Checks if Notification Access (NotificationListenerService) is enabled for the app.
 */
internal fun hasNotificationAccess(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    if (enabledListeners.contains(context.packageName)) return true
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}

/**
 * Checks if Overlay ("Display over other apps") permission is granted.
 */
internal fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}

internal data class SystemHealthSummary(
    val isCoreReady: Boolean,
    val overlayEngineLabel: String,
    val listenerLabel: String,
    val grantedCount: Int,
    val totalCount: Int,
)

internal fun buildSystemHealthSummary(
    overlayEnabled: Boolean,
    accessibilityConnected: Boolean,
    overlayServiceRunning: Boolean,
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    notificationAccessGranted: Boolean,
    listenerConnected: Boolean,
    optionalGrants: List<Boolean>,
): SystemHealthSummary {
    val hasDisplayPath = accessibilityGranted || overlayGranted
    val isCoreReady = notificationAccessGranted && hasDisplayPath
    val overlayEngineLabel = when {
        !overlayEnabled -> "Standby (toggle off)"
        accessibilityConnected -> "Accessibility (Shade & Lock)"
        overlayServiceRunning -> "Standard Overlay"
        hasDisplayPath -> "Ready to start"
        else -> "Permission needed"
    }
    val listenerLabel = when {
        listenerConnected -> "Connected"
        notificationAccessGranted -> "Enabled (waiting)"
        else -> "Not granted"
    }
    val coreItems = listOf(notificationAccessGranted, overlayGranted, accessibilityGranted)
    val allItems = coreItems + optionalGrants
    return SystemHealthSummary(
        isCoreReady = isCoreReady,
        overlayEngineLabel = overlayEngineLabel,
        listenerLabel = listenerLabel,
        grantedCount = allItems.count { it },
        totalCount = allItems.size,
    )
}

@Composable
fun PermissionsSetupDialog(
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") cutoutInfo: CutoutInfo? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shizukuStatus by ShizukuEarbudBattery.status.collectAsState()

    var notificationAccessGranted by remember {
        mutableStateOf(hasNotificationAccess(context))
    }
    var overlayGranted by remember {
        mutableStateOf(hasOverlayPermission(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(DotAccessibilityService.isAccessibilityEnabled(context))
    }

    var usageAccessGranted by remember { mutableStateOf(ForegroundAppTracker.refreshUsageAccess(context)) }
    var bluetoothGranted by remember { mutableStateOf(SystemEventManager.hasBluetoothPermission(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothGranted = granted || SystemEventManager.hasBluetoothPermission(context)
        if (bluetoothGranted) SystemEventManager.syncConnectedBluetoothDevices(context)
    }

    // Automatically refresh permission status when user returns from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageAccessGranted = ForegroundAppTracker.refreshUsageAccess(context)
                notificationAccessGranted = hasNotificationAccess(context)
                overlayGranted = hasOverlayPermission(context)
                accessibilityGranted = DotAccessibilityService.isAccessibilityEnabled(context)
                bluetoothGranted = SystemEventManager.hasBluetoothPermission(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ShizukuEarbudBattery.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PermissionSetupCard(
                    title = "Notification Access",
                    description = "Read media playback and live notifications.",
                    icon = LarpIcons.NotificationsActive,
                    isGranted = notificationAccessGranted,
                    onGrantClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                )

                PermissionSetupCard(
                    title = "Display Over Other Apps",
                    description = "Show the island on top of other apps.",
                    icon = LarpIcons.Layers,
                    isGranted = overlayGranted,
                    onGrantClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    },
                )

                PermissionSetupCard(
                    title = "Accessibility Service",
                    description = "Show on the lock screen and notification shade, and hide in fullscreen.",
                    icon = LarpIcons.Security,
                    isGranted = accessibilityGranted,
                    onGrantClick = {
                        ca.saboor.larpdot.service.DotAccessibilityService.openAccessibilitySettings(context)
                    },
                )

                PermissionSetupCard(
                    title = "Usage Access",
                    description = "Hide music, live activities, and custom islands while their app is open.",
                    icon = LarpIcons.Apps,
                    isGranted = usageAccessGranted,
                    pendingStatus = "Optional",
                    onGrantClick = { ForegroundAppTracker.openUsageAccessSettings(context) },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionSetupCard(
                        title = "Nearby Devices",
                        description = "Show Bluetooth connections and battery levels.",
                        icon = LarpIcons.Bluetooth,
                        isGranted = bluetoothGranted,
                        pendingStatus = "Optional",
                        onGrantClick = {
                            bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                    )
                    ShizukuPermissionSetupCard(shizukuStatus)
                }

                PermissionSetupCard(
                    title = "Unrestricted Battery",
                    description = "Prevent Android from stopping the overlay in the background.",
                    icon = LarpIcons.BatterySaver,
                    isGranted = batteryUnrestricted,
                    pendingStatus = "Optional",
                    grantLabel = "Open Settings",
                    onGrantClick = {
                        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        val fallbackIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                        runCatching { context.startActivity(batteryIntent) }
                            .onFailure { runCatching { context.startActivity(fallbackIntent) } }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Done")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun ShizukuPermissionSetupCard(status: ShizukuBatteryStatus) {
    val context = LocalContext.current
    val launchIntent = if (
        status == ShizukuBatteryStatus.NOT_RUNNING || status == ShizukuBatteryStatus.PERMISSION_DENIED
    ) ShizukuEarbudBattery.managerLaunchIntent(context) else null
    val requestPermission = status == ShizukuBatteryStatus.NEEDS_PERMISSION
    PermissionSetupCard(
        title = "Shizuku",
        description = when (status) {
            ShizukuBatteryStatus.NOT_RUNNING -> "Start Shizuku for separate earbud battery levels and hiding status bar Live Updates."
            ShizukuBatteryStatus.NEEDS_PERMISSION -> "Allow access for separate earbud battery levels and status bar Live Updates."
            ShizukuBatteryStatus.PERMISSION_DENIED -> "Allow LarpDot in Shizuku."
            ShizukuBatteryStatus.UNSUPPORTED -> "Bluetooth battery data unavailable in this Shizuku session."
            ShizukuBatteryStatus.READY -> "Shizuku access enabled."
        },
        icon = LarpIcons.Earbuds,
        isGranted = status == ShizukuBatteryStatus.READY,
        pendingStatus = "Optional",
        grantLabel = if (requestPermission) "Allow" else "Open Shizuku",
        onGrantClick = if (requestPermission) ShizukuEarbudBattery::requestPermission else {
            launchIntent?.let { intent -> { context.startActivity(intent) } }
        },
    )
}

@Composable
private fun PermissionSetupCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    pendingStatus: String = "Required",
    grantLabel: String = "Allow",
    onGrantClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (isGranted) {
                    Icon(
                        imageVector = LarpIcons.Check,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = pendingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pendingStatus == "Required") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isGranted && onGrantClick != null) {
                FilledTonalButton(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(grantLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
