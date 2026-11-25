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
import ca.saboor.larpdot.service.DotOverlayService
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.NotificationAccessService
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.service.ShizukuBatteryStatus
import ca.saboor.larpdot.service.ShizukuEarbudBattery
import ca.saboor.larpdot.service.SystemEventManager
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    cutoutInfo: CutoutInfo? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shizukuStatus by ShizukuEarbudBattery.status.collectAsState()
    val overlayEnabled by OverlayPreferences.isEnabledFlow.collectAsState()
    val accessibilityConnected by DotAccessibilityService.isServiceConnected.collectAsState()
    val overlayServiceRunning by DotOverlayService.isRunning.collectAsState()
    val listenerConnected by NotificationAccessService.isListenerConnected.collectAsState()

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
    var photosGranted by remember { mutableStateOf(ScreenshotPocketManager.hasPermission(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothGranted = granted || SystemEventManager.hasBluetoothPermission(context)
        if (bluetoothGranted) SystemEventManager.syncConnectedBluetoothDevices(context)
    }

    val photosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        photosGranted = granted || ScreenshotPocketManager.hasPermission(context)
        if (photosGranted) ScreenshotPocketManager.syncObserver(context)
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
                photosGranted = ScreenshotPocketManager.hasPermission(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ShizukuEarbudBattery.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val optionalGrants = buildList {
        add(usageAccessGranted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(bluetoothGranted)
        add(photosGranted)
        add(batteryUnrestricted)
    }
    val health = buildSystemHealthSummary(
        overlayEnabled = overlayEnabled,
        accessibilityConnected = accessibilityConnected,
        overlayServiceRunning = overlayServiceRunning,
        accessibilityGranted = accessibilityGranted,
        overlayGranted = overlayGranted,
        notificationAccessGranted = notificationAccessGranted,
        listenerConnected = listenerConnected,
        optionalGrants = optionalGrants,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LarpIcons.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "App Setup",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SystemHealthSummaryCard(
                    health = health,
                    cutoutInfo = cutoutInfo,
                )

                Text(
                    text = "Grant notification access and overlay permissions to enable background tracking and on-screen controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 1. Notification Access (NotificationListenerService)
                PermissionSetupCard(
                    title = "Access to Notifications",
                    description = "Enables notification listener access to read and react to incoming system notifications.",
                    icon = LarpIcons.NotificationsActive,
                    isGranted = notificationAccessGranted,
                    onGrantClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                )

                // 2. Overlay Over Apps (SYSTEM_ALERT_WINDOW)
                PermissionSetupCard(
                    title = "Display Over Other Apps",
                    description = "Allows the app to display floating overlays and on-screen controls on top of other applications.",
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
                    title = "Usage Access (Optional)",
                    description = "Hide the music island while the app providing the current media session is open.",
                    icon = LarpIcons.Layers,
                    isGranted = usageAccessGranted,
                    onGrantClick = { ForegroundAppTracker.openUsageAccessSettings(context) },
                )

                // 3. Accessibility Service (Draw over Notification Panel & Lock Screen)
                PermissionSetupCard(
                    title = "Accessibility Service (Lock Screen & Shade)",
                    description = "Allows the Dynamic Island to stay visible when swiping down the notification panel, on the lock screen, and auto-hide in fullscreen apps.",
                    icon = LarpIcons.Security,
                    isGranted = accessibilityGranted,
                    onGrantClick = {
                        ca.saboor.larpdot.service.DotAccessibilityService.openAccessibilitySettings(context)
                    },
                )

                // 4. Nearby Devices (Bluetooth alerts & battery level)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionSetupCard(
                        title = "Nearby Devices (Bluetooth Alerts)",
                        description = "Required on Android 12+ to detect Bluetooth earbuds, watches, and battery levels.",
                        icon = LarpIcons.Bluetooth,
                        isGranted = bluetoothGranted,
                        onGrantClick = {
                            bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                    )
                    ShizukuPermissionSetupCard(shizukuStatus)
                }

                // 5. Photos Access (Zero-step Screenshot Pocket)
                PermissionSetupCard(
                    title = "Photos Access (Screenshot Pocket)",
                    description = "Automatically catch new screenshots in the island for 1-tap share, copy, or delete.",
                    icon = LarpIcons.PhotoLibrary,
                    isGranted = photosGranted,
                    pendingStatus = "Optional",
                    onGrantClick = {
                        photosLauncher.launch(ScreenshotPocketManager.requiredPermission())
                    },
                )

                // 6. Battery Optimization (Prevent OEM background termination)
                PermissionSetupCard(
                    title = "Unrestricted Battery",
                    description = "Prevents Android from putting the Dynamic Island overlay or notification listener to sleep.",
                    icon = LarpIcons.BatterySaver,
                    isGranted = batteryUnrestricted,
                    pendingStatus = "Recommended",
                    grantLabel = "Open Battery Settings",
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
private fun SystemHealthSummaryCard(
    health: SystemHealthSummary,
    cutoutInfo: CutoutInfo?,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val statusColor = if (health.isCoreReady) Color(0xFF00C853) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (health.isCoreReady) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (health.isCoreReady) "System Ready" else "Core Setup Needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
                Text(
                    text = "${health.grantedCount}/${health.totalCount} active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Overlay: ${health.overlayEngineLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Notifications: ${health.listenerLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cutoutInfo != null) {
                val diameterDp = ((cutoutInfo.radiusPx * 2f) / density.coerceAtLeast(1f)).roundToInt()
                Text(
                    text = "Cutout: ${diameterDp}dp @ (${cutoutInfo.centerX.roundToInt()}, ${cutoutInfo.centerY.roundToInt()} px)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
            ShizukuBatteryStatus.NOT_RUNNING -> "Start Shizuku to use Android’s separate earbud battery readings when available."
            ShizukuBatteryStatus.NEEDS_PERMISSION -> "Allow LarpDot to read separate earbud battery levels when available."
            ShizukuBatteryStatus.PERMISSION_DENIED -> "Allow LarpDot in Shizuku’s authorized apps."
            ShizukuBatteryStatus.UNSUPPORTED -> "This Shizuku session does not have access to Bluetooth battery data."
            ShizukuBatteryStatus.READY -> "Separate earbud levels appear when Android has them."
        },
        icon = LarpIcons.Earbuds,
        isGranted = status == ShizukuBatteryStatus.READY,
        pendingStatus = "Optional",
        grantLabel = if (requestPermission) "Allow Shizuku" else "Open Shizuku",
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
    grantLabel: String = "Grant Permission",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                // Status Pill
                Row(
                    modifier = Modifier
                        .background(
                            color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (isGranted) LarpIcons.Check else LarpIcons.Close,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = if (isGranted) "Granted" else pendingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
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
