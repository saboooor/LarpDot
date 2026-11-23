package ca.saboor.larpdot.service

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listens for system events (battery charging, low battery, sound mode changes, and bluetooth devices)
 * and surfaces them as short-term or permanent alerts in the Dynamic Island.
 */
object SystemEventManager {
    // Android publishes this battery event even though its constants are not in the public SDK.
    private const val BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private val bluetoothBatteryLevels = mutableMapOf<String, Int>()

    private var isRegistered = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val dismissJobs = mutableMapOf<String, Job>()
    private var lastRingerMode: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return
            val settings = OverlayPreferences.getNotificationActivitySettings(context)
            if (!settings.enabled) return

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    if (settings.batteryCharging) {
                        val pct = getBatteryPercentage(context)
                        postAlert(
                            key = "system_battery_charging",
                            title = "Charging",
                            text = if (pct > 0) "$pct% • Connected to power" else "Connected to power",
                            kind = NotificationActivityKind.BATTERY_CHARGING,
                            permanent = settings.permanentCharging,
                        )
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    dismissAlert("system_battery_charging")
                }
                Intent.ACTION_BATTERY_LOW -> {
                    if (settings.batteryLow) {
                        val pct = getBatteryPercentage(context)
                        postAlert(
                            key = "system_battery_low",
                            title = "Low Battery",
                            text = if (pct > 0) "$pct% remaining • Connect charger" else "Battery is running low",
                            kind = NotificationActivityKind.BATTERY_LOW,
                            permanent = settings.permanentLowBattery,
                        )
                    }
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    dismissAlert("system_battery_low")
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                    val currentMode = am.ringerMode
                    if (lastRingerMode == null) {
                        lastRingerMode = currentMode
                        return
                    }
                    if (lastRingerMode == currentMode) return
                    lastRingerMode = currentMode

                    if (settings.soundMode) {
                        val (title, text) = when (currentMode) {
                            AudioManager.RINGER_MODE_SILENT -> "Silent Mode" to "Calls and alerts muted"
                            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate Mode" to "Calls and alerts will vibrate"
                            else -> "Ring Mode" to "Calls and alerts will ring"
                        }
                        postAlert(
                            key = "system_sound_mode",
                            title = title,
                            text = text,
                            kind = NotificationActivityKind.SOUND_MODE,
                            permanent = settings.permanentSoundMode,
                        )
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = getBluetoothDevice(intent)
                    val name = getDeviceName(device)
                    val id = runCatching { device?.address }.getOrNull() ?: name
                    val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
                    if (!connected) bluetoothBatteryLevels.remove(id)
                    val battery = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
                    if (battery != null && connected) bluetoothBatteryLevels[id] = battery
                    if (settings.bluetooth) {
                        val type = bluetoothDeviceType(device)
                        postAlert(
                            key = "system_bluetooth_$id",
                            title = name,
                            text = "$type • ${if (connected) "Connected" else "Disconnected"}",
                            kind = NotificationActivityKind.BLUETOOTH,
                            permanent = settings.permanentBluetooth,
                            bluetoothDeviceType = type,
                            bluetoothBatteryPercent = if (connected) bluetoothBatteryLevels[id] else null,
                        )
                    }
                }
                BATTERY_LEVEL_CHANGED -> {
                    val device = getBluetoothDevice(intent) ?: return
                    val id = runCatching { device.address }.getOrNull() ?: getDeviceName(device)
                    val battery = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
                    if (battery != null) bluetoothBatteryLevels[id] = battery else bluetoothBatteryLevels.remove(id)
                    // Update an existing alert without restarting its lifetime or bringing it back.
                    NotificationActivityState.updateBluetoothBattery("system_bluetooth_$id", battery)
                }
            }
        }
    }

    fun init(context: Context) {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        lastRingerMode = am?.ringerMode
        context.applicationContext.registerReceiver(receiver, filter)
        isRegistered = true
    }

    private fun postAlert(
        key: String,
        title: String,
        text: String,
        kind: NotificationActivityKind,
        permanent: Boolean,
        bluetoothDeviceType: String? = null,
        bluetoothBatteryPercent: Int? = null,
    ) {
        val info = NotificationActivityInfo(
            key = key,
            bluetoothDeviceType = bluetoothDeviceType,
            bluetoothBatteryPercent = bluetoothBatteryPercent,
            packageName = "android",
            appName = "System",
            title = title,
            text = text,
            kind = kind,
            progress = null,
            progressMax = null,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = System.currentTimeMillis(),
            contentIntent = null,
            actions = emptyList(),
            appIcon = null,
        )
        NotificationActivityState.postSystemActivity(info)

        dismissJobs[key]?.cancel()
        if (!permanent) {
            dismissJobs[key] = scope.launch {
                delay(3500)
                NotificationActivityState.removeSystemActivity(key)
                dismissJobs.remove(key)
            }
        }
    }

    private fun dismissAlert(key: String) {
        dismissJobs.remove(key)?.cancel()
        NotificationActivityState.removeSystemActivity(key)
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    @Suppress("DEPRECATION")
    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun bluetoothDeviceType(device: BluetoothDevice?): String {
        val deviceClass = runCatching { device?.bluetoothClass }.getOrNull() ?: return "Bluetooth device"
        return when (deviceClass.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Headset"
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Car audio"
            else -> when (deviceClass.majorDeviceClass) {
                BluetoothClass.Device.Major.COMPUTER -> "Computer"
                BluetoothClass.Device.Major.PHONE -> "Phone"
                BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                BluetoothClass.Device.Major.PERIPHERAL -> when (deviceClass.deviceClass and 0xC0) {
                    0x40 -> "Keyboard"
                    0x80 -> "Mouse"
                    0xC0 -> "Keyboard / mouse"
                    else -> "Peripheral"
                }
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio device"
                else -> "Bluetooth device"
            }
        }
    }

    private fun getDeviceName(device: BluetoothDevice?): String {
        return runCatching { device?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
    }
}
