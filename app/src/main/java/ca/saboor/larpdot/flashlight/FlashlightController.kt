package ca.saboor.larpdot.flashlight

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import ca.saboor.larpdot.service.DotAccessibilityService
import ca.saboor.larpdot.service.DotOverlayService
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller for hardware flashlight (torch) and dynamic island flashlight state.
 * Supports standard Android CameraManager API as well as PixelLight (com.chiller3.pixellight)
 * engine integration for maximum brightness boost on Google Pixel devices.
 */
object FlashlightController {
    private const val TAG = "FlashlightController"

    const val PIXELLIGHT_PACKAGE = "com.chiller3.pixellight"
    const val PIXELLIGHT_TOGGLE_ACTIVITY = "com.chiller3.pixellight.ToggleActivity"

    private var appContext: Context? = null
    private var cameraManager: CameraManager? = null
    private var targetCameraId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    val isFlashlightAvailable: StateFlow<Boolean> get() = _isAvailable.asStateFlow()

    private val _hasHardwareFlash = MutableStateFlow(false)
    val hasHardwareFlash: StateFlow<Boolean> = _hasHardwareFlash.asStateFlow()

    private val _torchStrength = MutableStateFlow(1)
    val torchStrength: StateFlow<Int> = _torchStrength.asStateFlow()

    private val _maxStrength = MutableStateFlow(1)
    val maxStrength: StateFlow<Int> = _maxStrength.asStateFlow()

    private val _isStrengthSupported = MutableStateFlow(false)
    val isStrengthSupported: StateFlow<Boolean> = _isStrengthSupported.asStateFlow()

    private val _isSimulated = MutableStateFlow(false)
    val isSimulated: StateFlow<Boolean> = _isSimulated.asStateFlow()

    private val _isPixelLightInstalled = MutableStateFlow(false)
    val isPixelLightInstalled: StateFlow<Boolean> = _isPixelLightInstalled.asStateFlow()

    private val _isPixelLightActive = MutableStateFlow(false)
    val isPixelLightActive: StateFlow<Boolean> = _isPixelLightActive.asStateFlow()

    val activeCameraId: String?
        get() = targetCameraId

    private var isInitialized = false
    private var isCallbackRegistered = false

    private var standardMaxStrength = 1
    private var standardTorchStrength = 1
    private var standardStrengthSupported = false
    private var isStandardTorchOn = false

    private var lastStrengthUpdateTime = 0L
    private var pendingStrengthJob: Job? = null
    private var pixelLightTurnOffPendingIntent: android.app.PendingIntent? = null

    fun setPixelLightTurnOffPendingIntent(pendingIntent: android.app.PendingIntent?) {
        pixelLightTurnOffPendingIntent = pendingIntent
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                isStandardTorchOn = enabled
                // If PixelLight is active, let PixelLight notification state prevail
                if (!_isPixelLightActive.value) {
                    notifyTorchStateChanged(enabled)
                }
                _isAvailable.value = true
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                // If PixelLight is currently using camera, torch is active via PixelLight
                if (!_isPixelLightActive.value) {
                    _isAvailable.value = false
                }
            }
        }
    }

    /**
     * Initializes the flashlight controller with the application context.
     * Safe to call multiple times.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        checkPixelLightInstallation(app)

        if (isInitialized) return

        val cm = app.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraManager = cm

        if (cm == null) {
            Log.w(TAG, "CameraManager not available on this device.")
            _hasHardwareFlash.value = false
            _isAvailable.value = false
            isInitialized = true
            return
        }

        findFlashCamera(cm)
        registerCallback()
        isInitialized = true
    }

    fun checkPixelLightInstallation(context: Context): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(PIXELLIGHT_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
        _isPixelLightInstalled.value = installed
        return installed
    }

    private fun findFlashCamera(cm: CameraManager) {
        try {
            val cameraIds = cm.cameraIdList
            var rearCameraWithFlash: String? = null
            var fallbackCameraWithFlash: String? = null

            for (id in cameraIds) {
                val chars = cm.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (flashAvailable) {
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraWithFlash = id
                        break
                    } else if (fallbackCameraWithFlash == null) {
                        fallbackCameraWithFlash = id
                    }
                }
            }

            val chosenId = rearCameraWithFlash ?: fallbackCameraWithFlash
            targetCameraId = chosenId
            val flashFound = chosenId != null
            _hasHardwareFlash.value = flashFound

            if (chosenId != null) {
                val chars = cm.getCameraCharacteristics(chosenId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    val defaultLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
                    standardMaxStrength = maxLevel
                    standardTorchStrength = defaultLevel
                    standardStrengthSupported = maxLevel > 1
                } else {
                    standardMaxStrength = 1
                    standardTorchStrength = 1
                    standardStrengthSupported = false
                }
            }

            if (!_isPixelLightActive.value) {
                _maxStrength.value = standardMaxStrength
                _torchStrength.value = standardTorchStrength
                _isStrengthSupported.value = standardStrengthSupported
            }

            _isAvailable.value = flashFound

            Log.i(TAG, "Flashlight target camera: $targetCameraId, strength supported: ${_isStrengthSupported.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query camera characteristics", e)
            _hasHardwareFlash.value = false
            _isAvailable.value = false
        }
    }

    private fun registerCallback() {
        if (isCallbackRegistered) return
        val cm = cameraManager ?: return
        try {
            cm.registerTorchCallback(torchCallback, mainHandler)
            isCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register TorchCallback", e)
        }
    }

    private fun notifyTorchStateChanged(enabled: Boolean) {
        _isFlashlightOn.value = enabled
        val context = appContext ?: return
        if (enabled && OverlayPreferences.isShowFlashlightIslandEnabled(context)) {
            val hasAccessibility = DotAccessibilityService.isServiceConnected.value
            val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true

            if (!hasAccessibility && canOverlay) {
                DotOverlayService.start(context)
            }
        }
    }

    /**
     * Called when a notification from PixelLight is posted or updated.
     */
    fun onPixelLightNotificationPosted(progress: Int, max: Int) {
        _isPixelLightActive.value = true
        if (max > 0) {
            _maxStrength.value = max
            _isStrengthSupported.value = max > 1
        }
        if (progress > 0) {
            _torchStrength.value = progress
        }
        _isAvailable.value = true
        notifyTorchStateChanged(true)
    }

    /**
     * Called when a notification from PixelLight is removed / cancelled.
     */
    fun onPixelLightNotificationRemoved() {
        _isPixelLightActive.value = false
        pixelLightTurnOffPendingIntent = null
        if (!isStandardTorchOn) {
            notifyTorchStateChanged(false)
            _maxStrength.value = standardMaxStrength
            _torchStrength.value = standardTorchStrength
            _isStrengthSupported.value = standardStrengthSupported
        }
    }

    /**
     * Determines whether commands should be delegated to PixelLight.
     */
    fun shouldUsePixelLight(): Boolean {
        val context = appContext ?: return false
        val installed = _isPixelLightInstalled.value
        val preferred = OverlayPreferences.isUsePixelLightEnabled(context)
        val active = _isPixelLightActive.value
        return (installed && preferred) || active
    }

    /**
     * Toggles flashlight state between on and off.
     */
    fun toggleFlashlight() {
        if (shouldUsePixelLight()) {
            launchPixelLightToggle()
            return
        }
        setTorch(!_isFlashlightOn.value)
    }

    /**
     * Sets flashlight mode on or off.
     * Supports simulation mode for emulators or testing.
     */
    fun setTorch(enabled: Boolean) {
        if (_isSimulated.value) {
            notifyTorchStateChanged(enabled)
            return
        }

        if (shouldUsePixelLight()) {
            launchPixelLightSetTorch(enabled)
            return
        }

        val cm = cameraManager
        val camId = targetCameraId

        if (cm == null || camId == null) {
            _isSimulated.value = true
            notifyTorchStateChanged(enabled)
            Log.w(TAG, "Hardware flash unavailable. Operating in simulation mode.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enabled && _isStrengthSupported.value) {
                cm.turnOnTorchWithStrengthLevel(camId, _torchStrength.value.coerceIn(1, _maxStrength.value))
                notifyTorchStateChanged(true)
            } else {
                cm.setTorchMode(camId, enabled)
                notifyTorchStateChanged(enabled)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException when setting torch mode: $enabled", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting torch mode: $enabled", e)
        }
    }

    /**
     * Sets torch brightness strength level.
     * If torch is currently active, immediately applies the new strength.
     */
    fun setStrengthLevel(level: Int) {
        val clamped = level.coerceIn(1, _maxStrength.value)
        _torchStrength.value = clamped

        if (_isSimulated.value) return

        if (shouldUsePixelLight()) {
            schedulePixelLightBrightness(clamped)
            return
        }

        if (_isFlashlightOn.value) {
            val cm = cameraManager
            val camId = targetCameraId
            if (cm != null && camId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _isStrengthSupported.value) {
                try {
                    cm.turnOnTorchWithStrengthLevel(camId, clamped)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update torch strength level to $clamped", e)
                }
            }
        }
    }

    fun setStrength(level: Int) {
        setStrengthLevel(level)
    }

    /**
     * Flushes the brightness strength immediately (e.g. on slider release).
     */
    fun flushStrength(level: Int) {
        val clamped = level.coerceIn(1, _maxStrength.value)
        _torchStrength.value = clamped
        if (shouldUsePixelLight()) {
            pendingStrengthJob?.cancel()
            sendPixelLightBrightness(clamped)
        }
    }

    private fun schedulePixelLightBrightness(level: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStrengthUpdateTime > 80L) {
            lastStrengthUpdateTime = now
            sendPixelLightBrightness(level)
        } else {
            pendingStrengthJob?.cancel()
            pendingStrengthJob = coroutineScope.launch {
                delay(80L)
                lastStrengthUpdateTime = SystemClock.uptimeMillis()
                sendPixelLightBrightness(level)
            }
        }
    }

    private fun launchPixelLightToggle() {
        val context = appContext ?: return
        try {
            val intent = Intent().apply {
                component = ComponentName(PIXELLIGHT_PACKAGE, PIXELLIGHT_TOGGLE_ACTIVITY)
                putExtra("brightness", -2)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch PixelLight ToggleActivity", e)
        }
    }

    private fun launchPixelLightSetTorch(enabled: Boolean) {
        if (!enabled && pixelLightTurnOffPendingIntent != null) {
            try {
                pixelLightTurnOffPendingIntent?.send()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send turnOffPendingIntent, falling back to ToggleActivity", e)
            }
        }

        val context = appContext ?: return
        try {
            val intent = Intent().apply {
                component = ComponentName(PIXELLIGHT_PACKAGE, PIXELLIGHT_TOGGLE_ACTIVITY)
                putExtra("brightness", if (enabled) -1 else 0)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode on PixelLight", e)
        }
    }

    private fun sendPixelLightBrightness(level: Int) {
        val context = appContext ?: return
        try {
            val intent = Intent().apply {
                component = ComponentName(PIXELLIGHT_PACKAGE, PIXELLIGHT_TOGGLE_ACTIVITY)
                putExtra("brightness", level)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send brightness $level to PixelLight", e)
        }
    }

    /**
     * Opens the PixelLight app if installed.
     */
    fun openPixelLightApp(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(PIXELLIGHT_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PixelLight app", e)
        }
    }

    fun setSimulated(enabled: Boolean) {
        _isSimulated.value = enabled
    }

    fun toggleSimulationMode() {
        _isSimulated.value = !_isSimulated.value
    }
}
