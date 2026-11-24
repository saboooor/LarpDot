package ca.saboor.larpdot.service

import android.content.Context
import android.content.Intent
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

    /** Opens Android's native output/volume sheet or falls back to Bluetooth settings. */
    fun openOutputSwitcher(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_VOLUME))
            }
            add(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            add(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
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
