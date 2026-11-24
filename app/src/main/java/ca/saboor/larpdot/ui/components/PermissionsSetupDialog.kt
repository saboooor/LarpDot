package ca.saboor.larpdot.ui.components

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.service.SystemEventManager
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
private fun hasNotificationAccess(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    if (enabledListeners.contains(context.packageName)) return true
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}

/**
 * Checks if Overlay ("Display over other apps") permission is granted.
 */
private fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

@Composable
fun PermissionsSetupDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationAccessGranted by remember {
        mutableStateOf(hasNotificationAccess(context))
    }
    var overlayGranted by remember {
        mutableStateOf(hasOverlayPermission(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(ca.saboor.larpdot.service.DotAccessibilityService.isAccessibilityEnabled(context))
    }

    var usageAccessGranted by remember { mutableStateOf(ForegroundAppTracker.refreshUsageAccess(context)) }
    var bluetoothGranted by remember { mutableStateOf(SystemEventManager.hasBluetoothPermission(context)) }
    var photosGranted by remember { mutableStateOf(ScreenshotPocketManager.hasPermission(context)) }

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
                accessibilityGranted = ca.saboor.larpdot.service.DotAccessibilityService.isAccessibilityEnabled(context)
                bluetoothGranted = SystemEventManager.hasBluetoothPermission(context)
                photosGranted = ScreenshotPocketManager.hasPermission(context)
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
                            Uri.parse("package:${context.packageName}")
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
                }

                // 5. Photos Access (Zero-step Screenshot Pocket)
                PermissionSetupCard(
                    title = "Photos Access (Screenshot Pocket)",
                    description = "Automatically catch new screenshots in the island for 1-tap share, copy, or delete.",
                    icon = LarpIcons.PhotoLibrary,
                    isGranted = photosGranted,
                    onGrantClick = {
                        photosLauncher.launch(ScreenshotPocketManager.requiredPermission())
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
private fun PermissionSetupCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
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
                        text = if (isGranted) "Granted" else "Required",
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

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Grant Permission", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
