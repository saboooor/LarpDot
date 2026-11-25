package ca.saboor.larpdot.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import ca.saboor.larpdot.notification.EarbudBatteryLevels
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Listens for system events (smart battery charging, charge limit, low battery, sound mode,
 * Bluetooth devices, and audio output changes) and surfaces them in the Dynamic Island.
 */
object SystemEventManager {
    // Android publishes this battery event even though its constants are not in the public SDK.
    private const val BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val EAR_BATTERY_REFRESH_MS = 60_000L
    private const val DEDUPE_WINDOW_MS = 1_800L

    private val bluetoothBatteryLevels = mutableMapOf<String, Int>()
    private val earbudBatteryLevels = mutableMapOf<String, EarbudBatteryLevels>()
    private val connectedEarbudCandidates = mutableMapOf<String, BluetoothDevice>()
    private val earbudBatteryJobs = mutableMapOf<String, Job>()
    private val earbudProbeStartedAt = mutableMapOf<String, Long>()
    private val auxiliaryLeConnections = mutableSetOf<String>()
    private val recentBluetoothEvents = mutableMapOf<String, Pair<Boolean, Long>>()

    private var appContext: Context? = null
    private var isRegistered = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val dismissJobs = mutableMapOf<String, Job>()
    private var lastRingerMode: Int? = null
    private var isChargingActive = false
    private var chargeLimitNotifiedForSession = false
    private var lastChargingPercent = -1

    fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return
            val settings = OverlayPreferences.notificationActivitySettingsFlow.value
            if (!settings.enabled) return

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isChargingActive = true
                    chargeLimitNotifiedForSession = false
                    if (settings.batteryCharging) {
                        postChargingAlert(context, settings.permanentCharging)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isChargingActive = false
                    chargeLimitNotifiedForSession = false
                    lastChargingPercent = -1
                    dismissAlert("system_battery_charging")
                    dismissAlert("system_battery_charge_limit")
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    handleBatteryChanged(context, intent, settings)
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
                            progress = pct.takeIf { it > 0 },
                            progressMax = 100,
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
                    val device = getBluetoothDevice(intent) ?: return
                    val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
                    val id = bluetoothId(device)
                    val transport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.TRANSPORT_AUTO)
                    } else BluetoothDevice.TRANSPORT_AUTO
                    if (transport != BluetoothDevice.TRANSPORT_BREDR) {
                        // Ignore an auxiliary link opened by an earbud probe on an already-connected device.
                        val probeAt = earbudProbeStartedAt[id]
                        if (connected && probeAt != null && recentBluetoothEvents[id]?.first == true &&
                            SystemClock.elapsedRealtime() - probeAt < EARBUD_BATTERY_READ_TIMEOUT_MS + DEDUPE_WINDOW_MS
                        ) {
                            auxiliaryLeConnections.add(id)
                            return
                        }
                        if (!connected && auxiliaryLeConnections.remove(id)) return
                    }
                    val battery = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
                    handleBluetoothConnection(device, connected, battery, settings, isProfileAudioEvent = false)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_DISCONNECTED) return
                    val device = getBluetoothDevice(intent) ?: return
                    val connected = state == BluetoothProfile.STATE_CONNECTED
                    handleBluetoothConnection(device, connected, null, settings, isProfileAudioEvent = true)
                }
                BATTERY_LEVEL_CHANGED -> {
                    val device = getBluetoothDevice(intent) ?: return
                    val id = bluetoothId(device)
                    val battery = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1).takeIf { it in 0..100 }
                    if (battery != null) bluetoothBatteryLevels[id] = battery else bluetoothBatteryLevels.remove(id)
                    // Update an existing alert without restarting its lifetime or bringing it back.
                    NotificationActivityState.updateBluetoothBattery("system_bluetooth_$id", battery)
                }
                BluetoothDevice.ACTION_ALIAS_CHANGED -> {
                    val device = getBluetoothDevice(intent) ?: return
                    val id = bluetoothId(device)
                    NotificationActivityState.updateBluetoothTitle("system_bluetooth_$id", getDeviceName(device))
                    AudioOutputTracker.refresh(notify = false)
                }
            }
        }
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        // Hydrate activity settings flow once so high-frequency broadcasts read in memory.
        OverlayPreferences.getNotificationActivitySettings(app)
        AudioOutputTracker.init(app) { previous, current ->
            handleAudioOutputChanged(app, previous, current)
        }
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addAction(BluetoothDevice.ACTION_ALIAS_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
            }
        }
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        lastRingerMode = am?.ringerMode
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        isRegistered = true
        ShizukuEarbudBattery.initialize()
        scope.launch {
            var wasReady = false
            ShizukuEarbudBattery.status.collectLatest { status ->
                val ready = status == ShizukuBatteryStatus.READY
                if (ready != wasReady) {
                    wasReady = ready
                    // Revisit active alerts when permission is granted or Shizuku stops.
                    connectedEarbudCandidates.forEach { (id, device) ->
                        earbudBatteryJobs.remove(id)?.cancel()
                        refreshEarbudBatteries(device, id)
                    }
                    if (ready) syncConnectedBluetoothDevices(app)
                }
            }
        }
        syncConnectedBluetoothDevices(app)
    }

    /** Refreshes currently connected Bluetooth devices (useful after granting Nearby Devices or pinning BT alerts). */
    @SuppressLint("MissingPermission")
    fun syncConnectedBluetoothDevices(context: Context) {
        val settings = OverlayPreferences.getNotificationActivitySettings(context)
        if (!settings.enabled || !settings.bluetooth || !settings.permanentBluetooth) return
        if (!hasBluetoothPermission(context)) return
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = btManager.adapter ?: return
        val bonded = try { adapter.bondedDevices.orEmpty() } catch (_: SecurityException) { emptySet() }
        for (device in bonded) {
            val connected = runCatching {
                val method = runCatching { BluetoothDevice::class.java.getMethod("isConnected") }.getOrElse {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw it
                    HiddenApiBypass.getDeclaredMethod(BluetoothDevice::class.java, "isConnected")
                }
                method.invoke(device) as? Boolean == true
            }.getOrDefault(false)
            if (connected) {
                val battery = readBluetoothBatteryReflection(device)
                handleBluetoothConnection(device, connected = true, battery = battery, settings = settings)
            }
        }
    }

    private fun handleBatteryChanged(
        context: Context,
        intent: Intent,
        settings: ca.saboor.larpdot.notification.NotificationActivitySettings,
    ) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = extractBatteryPercent(intent)

        if (!charging) {
            if (isChargingActive) {
                isChargingActive = false
                chargeLimitNotifiedForSession = false
                lastChargingPercent = -1
            }
            return
        }
        isChargingActive = true

        // Smart charge limit / battery full alert (fires once per charging session).
        if (settings.chargeLimitAlert && BatteryStatusFormatter.shouldTriggerChargeLimit(
                isCharging = true,
                percent = pct,
                limitPercent = settings.chargeLimitPercent,
                alreadyNotified = chargeLimitNotifiedForSession,
            )
        ) {
            chargeLimitNotifiedForSession = true
            val isFull = settings.chargeLimitPercent >= 100
            postAlert(
                key = "system_battery_charge_limit",
                title = if (isFull) "Battery Full" else "Charge Limit Reached",
                text = if (isFull) "$pct% • Fully charged" else "$pct% reached • Unplug to protect battery",
                kind = NotificationActivityKind.BATTERY_CHARGING,
                permanent = false,
                progress = pct,
                progressMax = 100,
                dismissMs = 5_000L,
            )
        }

        // Keep pinned charging alert fresh with live percentage, wattage, and time-to-full.
        if (settings.batteryCharging && settings.permanentCharging && pct != lastChargingPercent) {
            lastChargingPercent = pct
            postChargingAlert(context, permanent = true, batteryIntent = intent)
        }
    }

    private fun postChargingAlert(
        context: Context,
        permanent: Boolean,
        batteryIntent: Intent? = null,
    ) {
        val statusIntent = batteryIntent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = extractBatteryPercent(statusIntent)
        val voltageMv = statusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val watts = BatteryStatusFormatter.estimateChargingWatts(currentNow, voltageMv)
        val timeRemainingMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L)
        } else -1L

        lastChargingPercent = pct
        postAlert(
            key = "system_battery_charging",
            title = BatteryStatusFormatter.classifyChargingTitle(watts),
            text = BatteryStatusFormatter.buildChargingSubtitle(pct, watts, timeRemainingMs),
            kind = NotificationActivityKind.BATTERY_CHARGING,
            permanent = permanent,
            progress = pct.takeIf { it > 0 },
            progressMax = 100,
        )
    }

    private fun handleBluetoothConnection(
        device: BluetoothDevice?,
        connected: Boolean,
        battery: Int?,
        settings: ca.saboor.larpdot.notification.NotificationActivitySettings,
        isProfileAudioEvent: Boolean = false,
    ) {
        if (device == null) return
        val name = getDeviceName(device)
        val id = bluetoothId(device)
        if (!connected) {
            connectedEarbudCandidates.remove(id)
            earbudBatteryJobs.remove(id)?.cancel()
            earbudBatteryLevels.remove(id)
            earbudProbeStartedAt.remove(id)
        }
        if (!isUserFacingBluetoothDevice(device, name, isProfileAudioEvent)) {
            if (!connected) {
                bluetoothBatteryLevels.remove(id)
                dismissAlert("system_bluetooth_$id")
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val prev = recentBluetoothEvents[id]
        if (prev != null && prev.first == connected && now - prev.second < DEDUPE_WINDOW_MS) {
            // Still update battery if a richer event arrived shortly after.
            if (battery != null && connected) {
                bluetoothBatteryLevels[id] = battery
                NotificationActivityState.updateBluetoothBattery("system_bluetooth_$id", battery)
            }
            return
        }
        recentBluetoothEvents[id] = connected to now

        if (!connected) {
            bluetoothBatteryLevels.remove(id)
        } else {
            val resolvedBattery = battery ?: readBluetoothBatteryReflection(device)
            if (resolvedBattery != null) bluetoothBatteryLevels[id] = resolvedBattery
        }
        if (!settings.bluetooth) return

        val type = bluetoothDeviceType(device)
        if (!connected && settings.permanentBluetooth) {
            dismissAlert("system_bluetooth_$id")
            return
        }
        postAlert(
            key = "system_bluetooth_$id",
            title = name,
            text = "$type • ${if (connected) "Connected" else "Disconnected"}",
            kind = NotificationActivityKind.BLUETOOTH,
            permanent = settings.permanentBluetooth && connected,
            bluetoothDeviceType = type,
            bluetoothBatteryPercent = if (connected) bluetoothBatteryLevels[id] else null,
            earbudBatteries = earbudBatteryLevels[id],
        )
        if (connected && type in setOf("Earbuds", "Headphones", "Headset", "Audio", "Bluetooth")) {
            connectedEarbudCandidates[id] = device
            refreshEarbudBatteries(device, id)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshEarbudBatteries(device: BluetoothDevice, id: String) {
        val context = appContext ?: return
        if (id in earbudBatteryJobs || !hasBluetoothPermission(context)) return
        val deviceType = try {
            device.type
        } catch (_: SecurityException) {
            return
        }
        val supportsLe = deviceType != BluetoothDevice.DEVICE_TYPE_CLASSIC
        if (!supportsLe && ShizukuEarbudBattery.status.value != ShizukuBatteryStatus.READY) {
            earbudBatteryLevels.remove(id)
            NotificationActivityState.updateEarbudBatteries("system_bluetooth_$id", null)
            return
        }

        earbudBatteryJobs[id] = scope.launch {
            try {
                var tryStandardRead = supportsLe
                var supportsStandardBattery = false
                var standardPreviouslyComplete = false
                while (hasBluetoothPermission(context)) {
                    val settings = OverlayPreferences.notificationActivitySettingsFlow.value
                    if (!settings.enabled || !settings.bluetooth) break

                    // Surface cached metadata promptly so the brief connection alert can show it
                    // while GATT discovery runs. Standard readings take precedence once available.
                    val metadata = if (standardPreviouslyComplete) null else {
                        ShizukuEarbudBattery.readMissingLevels(context, device)
                    }
                    if (!supportsStandardBattery && metadata != null) updateEarbudLevels(id, metadata)

                    val attemptedStandardRead = tryStandardRead
                    val standard = if (attemptedStandardRead) {
                        earbudProbeStartedAt[id] = SystemClock.elapsedRealtime()
                        readEarbudBatteryLevels(context, device)
                    } else null
                    supportsStandardBattery = supportsStandardBattery || standard != null
                    tryStandardRead = supportsStandardBattery
                    standardPreviouslyComplete = standard?.left != null && standard.right != null

                    // The system companion may populate its cache just after the connection broadcast.
                    val fallback = if (metadata == null && attemptedStandardRead && !standardPreviouslyComplete) {
                        ShizukuEarbudBattery.readMissingLevels(context, device, standard)
                    } else metadata
                    val levels = mergeEarbudBatteryLevels(standard, fallback)
                    updateEarbudLevels(id, levels)
                    if (!settings.permanentBluetooth) {
                        if (levels?.left == null || levels.right == null) {
                            delay(700L)
                            val latest = ShizukuEarbudBattery.readMissingLevels(context, device, standard)
                            updateEarbudLevels(id, mergeEarbudBatteryLevels(latest, levels))
                        }
                        break
                    }
                    if (!supportsStandardBattery && ShizukuEarbudBattery.status.value != ShizukuBatteryStatus.READY) break
                    delay(EAR_BATTERY_REFRESH_MS)
                }
            } finally {
                if (earbudBatteryJobs[id] == coroutineContext[Job]) earbudBatteryJobs.remove(id)
            }
        }
    }

    private fun updateEarbudLevels(id: String, levels: EarbudBatteryLevels?) {
        if (levels == null) earbudBatteryLevels.remove(id) else earbudBatteryLevels[id] = levels
        NotificationActivityState.updateEarbudBatteries("system_bluetooth_$id", levels)
    }

    private fun handleAudioOutputChanged(
        context: Context,
        previous: AudioOutputDevice,
        current: AudioOutputDevice,
    ) {
        val settings = OverlayPreferences.notificationActivitySettingsFlow.value
        if (!settings.enabled) return

        // If a Bluetooth audio device just connected and Bluetooth alerts already handled it, skip duplicate.
        val now = SystemClock.elapsedRealtime()
        val recentlyHandledBt = current.kind == AudioOutputKind.BLUETOOTH_AUDIO &&
            recentBluetoothEvents.values.any { (conn, ts) -> conn && now - ts < DEDUPE_WINDOW_MS }

        if (current.kind == AudioOutputKind.BLUETOOTH_AUDIO && !recentlyHandledBt && settings.bluetooth && !hasBluetoothPermission(context)) {
            // Fallback for devices without BLUETOOTH_CONNECT: AudioDeviceCallback still gives real earbud name!
            postAlert(
                key = "system_audio_output",
                title = current.name,
                text = "Bluetooth Audio • Connected",
                kind = NotificationActivityKind.BLUETOOTH,
                permanent = settings.permanentBluetooth,
                bluetoothDeviceType = "Audio",
            )
            return
        }

        if (!settings.audioOutput || recentlyHandledBt) return
        val subtitle = when (current.kind) {
            AudioOutputKind.PHONE_SPEAKER -> "Switched from ${previous.name}"
            AudioOutputKind.WIRED_HEADSET -> "Wired Audio"
            AudioOutputKind.USB_AUDIO -> "USB-C Audio"
            AudioOutputKind.CAR_AUDIO -> "Car Audio"
            AudioOutputKind.BLUETOOTH_AUDIO -> "Bluetooth Audio"
            AudioOutputKind.HEARING_AID -> "Hearing Aid"
            AudioOutputKind.EXTERNAL_DISPLAY -> "External Output"
        }
        postAlert(
            key = "system_audio_output",
            title = current.name,
            text = subtitle,
            kind = NotificationActivityKind.AUDIO_OUTPUT,
            permanent = settings.permanentAudioOutput && current.isExternal,
            bluetoothDeviceType = subtitle,
        )
    }

    private fun postAlert(
        key: String,
        title: String,
        text: String,
        kind: NotificationActivityKind,
        permanent: Boolean,
        bluetoothDeviceType: String? = null,
        bluetoothBatteryPercent: Int? = null,
        earbudBatteries: EarbudBatteryLevels? = null,
        progress: Int? = null,
        progressMax: Int? = null,
        dismissMs: Long = 3_500L,
    ) {
        val info = NotificationActivityInfo(
            key = key,
            bluetoothDeviceType = bluetoothDeviceType,
            bluetoothBatteryPercent = bluetoothBatteryPercent,
            earbudBatteryLevels = earbudBatteries,
            packageName = "android",
            appName = "System",
            title = title,
            text = text,
            kind = kind,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = false,
            chronometerBaseElapsedRealtime = null,
            chronometerCountsDown = false,
            postedAtMillis = System.currentTimeMillis(),
            contentIntent = null,
            actions = emptyList(),
            appIcon = null,
            isPinnedShortTerm = permanent,
        )
        NotificationActivityState.postSystemActivity(info)

        dismissJobs[key]?.cancel()
        if (!permanent) {
            dismissJobs[key] = scope.launch {
                delay(dismissMs)
                NotificationActivityState.removeSystemActivity(key)
                dismissJobs.remove(key)
            }
        }
    }

    private fun dismissAlert(key: String) {
        dismissJobs.remove(key)?.cancel()
        NotificationActivityState.removeSystemActivity(key)
    }

    private fun getBatteryPercentage(context: Context): Int =
        extractBatteryPercent(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

    private fun extractBatteryPercent(intent: Intent?): Int {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun readBluetoothBatteryReflection(device: BluetoothDevice?): Int? {
        if (device == null) return null
        return runCatching {
            val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int
            level?.takeIf { it in 0..100 }
        }.getOrNull()
    }

    private fun bluetoothId(device: BluetoothDevice?): String =
        runCatching { device?.address }.getOrNull()?.takeIf { it.isNotBlank() } ?: getDeviceName(device)

    @Suppress("DEPRECATION")
    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDeviceType(device: BluetoothDevice?): String {
        val name = getDeviceName(device).lowercase()
        if (name.contains("buds") || name.contains("airpods") || name.contains("earpod") || name.contains("freebuds")) {
            return "Earbuds"
        }
        val deviceClass = try { device?.bluetoothClass } catch (_: SecurityException) { null } ?: return "Bluetooth"
        return when (deviceClass.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Headset"
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Car audio"
            else -> when (deviceClass.majorDeviceClass) {
                BluetoothClass.Device.Major.COMPUTER -> "Computer"
                BluetoothClass.Device.Major.PHONE -> "Phone"
                BluetoothClass.Device.Major.WEARABLE -> "Watch"
                BluetoothClass.Device.Major.PERIPHERAL -> when {
                    (deviceClass.deviceClass and 0x0C) == 0x08 || (deviceClass.deviceClass and 0x0C) == 0x04 -> "Controller"
                    (deviceClass.deviceClass and 0xC0) == 0x40 -> "Keyboard"
                    (deviceClass.deviceClass and 0xC0) == 0x80 -> "Mouse"
                    (deviceClass.deviceClass and 0xC0) == 0xC0 -> "Keyboard / mouse"
                    else -> "Peripheral"
                }
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio"
                else -> "Bluetooth"
            }
        }
    }

    @SuppressLint("MissingPermission")
    internal fun getFriendlyBluetoothName(device: BluetoothDevice?): String {
        if (device == null) return "Bluetooth Device"
        val alias = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias
            } else {
                BluetoothDevice::class.java.getMethod("getAliasName").invoke(device) as? String
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
        val rawName = try { device.name } catch (_: SecurityException) { null }
        return resolveFriendlyBluetoothName(alias = alias, rawName = rawName)
    }

    @SuppressLint("MissingPermission")
    internal fun lookupFriendlyBluetoothNameByAddress(context: Context, address: String?): String? {
        if (address.isNullOrBlank() || !hasBluetoothPermission(context)) return null
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return null
        val adapter = btManager.adapter ?: return null
        val matched = try {
            adapter.bondedDevices?.firstOrNull { it.address.equals(address, ignoreCase = true) }
                ?: adapter.getRemoteDevice(address)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        return getFriendlyBluetoothName(matched).takeUnless { it == "Bluetooth Device" }
    }

    private fun getDeviceName(device: BluetoothDevice?): String = getFriendlyBluetoothName(device)

    @SuppressLint("MissingPermission")
    private fun isUserFacingBluetoothDevice(
        device: BluetoothDevice,
        displayName: String,
        isProfileAudioEvent: Boolean,
    ): Boolean {
        val bondState = try { device.bondState } catch (_: SecurityException) { BluetoothDevice.BOND_NONE }
        val majorClass = try { device.bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null }
        val uuids = try { device.uuids?.map { it.uuid.toString() } } catch (_: SecurityException) { null }.orEmpty()
        return shouldSurfaceBluetoothDevice(
            bondState = bondState,
            majorDeviceClass = majorClass,
            uuids = uuids,
            displayName = displayName,
            isProfileAudioEvent = isProfileAudioEvent,
        )
    }
}

internal fun mergeEarbudBatteryLevels(
    standard: EarbudBatteryLevels?,
    metadata: EarbudBatteryLevels?,
): EarbudBatteryLevels? {
    val left = standard?.left ?: metadata?.left
    val right = standard?.right ?: metadata?.right
    return if (left != null || right != null) EarbudBatteryLevels(left, right) else null
}

private val RAW_MAC_REGEX = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
private val LE_PREFIX_REGEX = Regex("^LE[_\\-\\s]+", RegexOption.IGNORE_CASE)

private val ACCESSORY_PROFILE_SHORT_UUIDS = setOf(
    "110b", // A2DP Audio Sink (headphones, earbuds, speakers, car)
    "111e", // HFP Handsfree Unit
    "1108", // HSP Headset
    "1124", // Classic HID (controllers, keyboards, mice)
    "1812", // BLE HID over GATT (HOGP)
    "184e", // LE Audio ASCS
    "1850", // LE Audio PACS
    "184f", // LE Audio BASS
    "1853", // LE Audio CSIS
    "1844", // LE Audio VCS
    "1855", // LE Audio TMAS
    "1854", // Hearing Access Profile (HAP)
    "fdf0", // Android Hearing Aid (ASHA)
)

private val TRACKER_SHORT_UUIDS = setOf(
    "feaa", // Eddystone / Google Find Hub (FMDN)
    "feed", // Tile
    "feec", // Tile
    "fd5a", // Samsung SmartTag
    "fd44", // Apple Find My
    "fe33", // Chipolo
)

private const val GOOGLE_FMDN_FULL_UUID = "15190001-12f4-c226-88ed-2ac5579f2a85"

private fun shortBluetoothUuid(uuid: String): String? {
    val normalized = uuid.trim().lowercase()
    return if (normalized.startsWith("0000") && normalized.endsWith("-0000-1000-8000-00805f9b34fb") && normalized.length == 36) {
        normalized.substring(4, 8)
    } else {
        null
    }
}

/**
 * Matches Android's Bluetooth Quick Settings visibility: only surfaces bonded user-facing
 * accessories (audio devices, wearables, HID peripherals) and suppresses background ACL peers
 * such as Find Hub trackers, other phones, Quick Share / Phone Hub links, and unnamed BLE beacons.
 */
internal fun shouldSurfaceBluetoothDevice(
    bondState: Int,
    majorDeviceClass: Int?,
    uuids: Collection<String>,
    displayName: String?,
    isProfileAudioEvent: Boolean = false,
): Boolean {
    if (majorDeviceClass == BluetoothClass.Device.Major.PHONE) return false
    if (isProfileAudioEvent) return true
    if (bondState != BluetoothDevice.BOND_BONDED) return false

    val cleanName = displayName?.trim().orEmpty()
    if (cleanName.isEmpty() || cleanName == "Bluetooth Device" || RAW_MAC_REGEX.matches(cleanName)) {
        return false
    }

    val normalizedUuids = uuids.map { it.trim().lowercase() }
    val shortUuids = normalizedUuids.mapNotNull(::shortBluetoothUuid).toSet()
    val hasAccessoryProfile = shortUuids.any { it in ACCESSORY_PROFILE_SHORT_UUIDS }
    val hasTrackerUuid = GOOGLE_FMDN_FULL_UUID in normalizedUuids || shortUuids.any { it in TRACKER_SHORT_UUIDS }

    return when (majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO,
        BluetoothClass.Device.Major.WEARABLE,
        -> true
        BluetoothClass.Device.Major.PERIPHERAL -> hasAccessoryProfile || !hasTrackerUuid
        BluetoothClass.Device.Major.COMPUTER,
        BluetoothClass.Device.Major.NETWORKING,
        BluetoothClass.Device.Major.IMAGING,
        BluetoothClass.Device.Major.TOY,
        BluetoothClass.Device.Major.HEALTH,
        -> false
        else -> hasAccessoryProfile
    }
}

internal fun resolveFriendlyBluetoothName(alias: String?, rawName: String?): String {
    val cleanAlias = alias?.trim()?.takeIf { it.isNotEmpty() }
    val cleanRaw = rawName?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanAlias != null && cleanAlias != cleanRaw) {
        return cleanAlias
    }
    val candidate = cleanAlias ?: cleanRaw ?: return "Bluetooth Device"
    return candidate.replace(LE_PREFIX_REGEX, "").trim().ifEmpty { candidate }
}
