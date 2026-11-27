package ca.saboor.larpdot.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOutputKind {
    PHONE_SPEAKER,
    BLUETOOTH_AUDIO,
    WIRED_HEADSET,
    USB_AUDIO,
    CAR_AUDIO,
    HEARING_AID,
    EXTERNAL_DISPLAY,
}

data class AudioOutputDevice(
    val id: String,
    val name: String,
    val kind: AudioOutputKind,
    val isExternal: Boolean,
)

/** Picks the active media output route, preferring external routes over the built-in speaker. */
internal fun selectPrimaryAudioOutput(devices: List<AudioOutputDevice>): AudioOutputDevice {
    val bestExternal = devices
        .filter { it.isExternal }
        .maxByOrNull { outputPriority(it.kind) }
    return bestExternal ?: AudioOutputDevice(
        id = "builtin_speaker",
        name = "This phone",
        kind = AudioOutputKind.PHONE_SPEAKER,
        isExternal = false,
    )
}

private fun outputPriority(kind: AudioOutputKind): Int = when (kind) {
    AudioOutputKind.CAR_AUDIO -> 60
    AudioOutputKind.BLUETOOTH_AUDIO -> 50
    AudioOutputKind.HEARING_AID -> 45
    AudioOutputKind.USB_AUDIO -> 40
    AudioOutputKind.WIRED_HEADSET -> 35
    AudioOutputKind.EXTERNAL_DISPLAY -> 20
    AudioOutputKind.PHONE_SPEAKER -> 0
}

/**
 * Tracks active media audio outputs using [AudioManager.AudioDeviceCallback].
 * Works without BLUETOOTH_CONNECT permission and reliably detects Bluetooth A2DP,
 * LE Audio earbuds, USB-C DACs/headsets, wired headphones, and speaker fallback.
 */
object AudioOutputTracker {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val MEDIA_OUTPUT_DIALOG = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
    private const val SYSTEM_OUTPUT_DIALOG = "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
    private const val LEGACY_MEDIA_OUTPUT_PANEL = "com.android.settings.panel.action.MEDIA_OUTPUT"

    private val defaultSpeaker = AudioOutputDevice(
        id = "builtin_speaker",
        name = "This phone",
        kind = AudioOutputKind.PHONE_SPEAKER,
        isExternal = false,
    )

    private val _activeOutput = MutableStateFlow(defaultSpeaker)
    val activeOutput: StateFlow<AudioOutputDevice> = _activeOutput.asStateFlow()

    private val _externalOutputs = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val externalOutputs: StateFlow<List<AudioOutputDevice>> = _externalOutputs.asStateFlow()

    private var initialized = false
    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var onOutputChanged: ((previous: AudioOutputDevice, current: AudioOutputDevice) -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh(notify = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh(notify = true)
        }
    }

    fun init(
        context: Context,
        listener: ((previous: AudioOutputDevice, current: AudioOutputDevice) -> Unit)? = null,
    ) {
        if (listener != null) onOutputChanged = listener
        if (initialized) {
            refresh(notify = false)
            return
        }
        initialized = true
        appContext = context.applicationContext
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        refresh(notify = false)
        am.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun refresh(notify: Boolean = false) {
        val am = audioManager ?: return
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull(::toAudioOutputDevice)
            .distinctBy { it.id }
        val previous = _activeOutput.value
        val next = selectPrimaryAudioOutput(outputs)
        _externalOutputs.value = outputs.filter { it.isExternal }
        _activeOutput.value = next
        if (notify && previous.id != next.id) {
            onOutputChanged?.invoke(previous, next)
        }
    }

    /** Opens the playback app's native route picker, with Bluetooth settings as a fallback. */
    fun openOutputSwitcher(context: Context, playerPackage: String? = null) {
        val packageName = playerPackage?.takeIf(String::isNotBlank)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The public MediaRouter2 entry point targets the foreground caller, while the island
            // runs in an overlay and needs the media app's routes.
            if (packageName == null && showSystemUiOutputDialog(context, Intent(SYSTEM_OUTPUT_DIALOG))) return
            val playerDialog = Intent(MEDIA_OUTPUT_DIALOG).putExtra("package_name", packageName ?: context.packageName)
            if (showSystemUiOutputDialog(context, playerDialog)) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val panel = Intent(LEGACY_MEDIA_OUTPUT_PANEL)
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", packageName ?: context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = context.packageManager.queryIntentActivities(panel, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.canLaunchSystemComponent(context) }
                ?.activityInfo
            if (activity != null && runCatching {
                    context.startActivity(panel.setComponent(ComponentName(activity.packageName, activity.name)))
                }.isSuccess
            ) return
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun showSystemUiOutputDialog(context: Context, intent: Intent): Boolean {
        intent.setPackage(SYSTEM_UI_PACKAGE)
        if (context.packageManager.queryBroadcastReceivers(intent, 0).none {
                it.activityInfo.canLaunchSystemComponent(context)
            }
        ) return false
        return runCatching { context.sendBroadcast(intent) }.isSuccess
    }

    private fun ActivityInfo?.canLaunchSystemComponent(context: Context): Boolean {
        val info = this ?: return false
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return info.exported && info.applicationInfo.flags and systemFlags != 0 &&
            (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
    }

    private fun toAudioOutputDevice(info: AudioDeviceInfo): AudioOutputDevice? {
        val rawName = info.productName?.toString()?.trim().orEmpty()
        val kind = when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> if (rawName.contains("car", ignoreCase = true) || rawName.contains("auto", ignoreCase = true)) {
                AudioOutputKind.CAR_AUDIO
            } else {
                AudioOutputKind.BLUETOOTH_AUDIO
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> AudioOutputKind.WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> AudioOutputKind.USB_AUDIO
            AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputKind.HEARING_AID
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> AudioOutputKind.EXTERNAL_DISPLAY
            else -> {
                val isBleAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && info.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
                    )
                if (isBleAudio) AudioOutputKind.BLUETOOTH_AUDIO else return null
            }
        }

        val fallbackName = when (kind) {
            AudioOutputKind.BLUETOOTH_AUDIO -> "Bluetooth Audio"
            AudioOutputKind.CAR_AUDIO -> "Car Audio"
            AudioOutputKind.WIRED_HEADSET -> "Wired Headphones"
            AudioOutputKind.USB_AUDIO -> "USB Audio"
            AudioOutputKind.HEARING_AID -> "Hearing Aid"
            AudioOutputKind.EXTERNAL_DISPLAY -> "External Display"
            AudioOutputKind.PHONE_SPEAKER -> "This phone"
        }
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.address.orEmpty() else ""
        val aliasName = if (kind == AudioOutputKind.BLUETOOTH_AUDIO || kind == AudioOutputKind.CAR_AUDIO) {
            appContext?.let { SystemEventManager.lookupFriendlyBluetoothNameByAddress(it, address) }
        } else null
        val name = aliasName
            ?: rawName.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: fallbackName
        val id = "${kind.name}:${address.ifBlank { name }}"
        return AudioOutputDevice(
            id = id,
            name = name,
            kind = kind,
            isExternal = true,
        )
    }
}
