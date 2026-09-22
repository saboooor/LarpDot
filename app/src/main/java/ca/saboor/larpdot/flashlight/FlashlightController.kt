package ca.saboor.larpdot.flashlight

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
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
    const val PIXELLIGHT_SETTINGS_ACTIVITY = "com.chiller3.pixellight.MainActivity"

    // Pixel experimental Camera2 metadata key used by PixelLight
    private const val PIXEL_EXPERIMENTAL_MAX_BRIGHTNESS_KEY =
        "com.google.pixel.experimental2020.flashlightBrightnessLevelMax"

    private var appContext: Context? = null
    private var cameraManager: CameraManager? = null
    private var targetCameraId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    var lastFlashlightOnTime: Long = 0L
        private set

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
    private var pixelLightMaxStrength = 1
    private var isStandardTorchOn = false

    private var lastStrengthUpdateTime = 0L
    private var pendingStrengthJob: Job? = null
    private var pixelLightTurnOffPendingIntent: android.app.PendingIntent? = null

    @Volatile
    private var lastLarpDotCommandTime = 0L

    @Volatile
    private var isUserInteracting = false
    @Volatile
    private var lastUserSetStrengthTime = 0L

    private val knownCameraPackages = hashSetOf(
        "com.google.android.GoogleCamera",
        "com.android.camera",
        "com.android.camera2",
        "org.codeaurora.snapcam",
        "net.sourceforge.opencamera",
        "com.rawcam.app",
        "com.sec.android.app.camera",
        "com.samsung.android.camera",
        "com.oppo.camera",
        "com.oneplus.camera",
        "com.huawei.camera",
        "com.motorola.camera",
        "com.motorola.camera2",
        "com.motorola.camera3"
    )

    fun setUserInteracting(interacting: Boolean) {
        isUserInteracting = interacting
        if (interacting) {
            lastUserSetStrengthTime = SystemClock.uptimeMillis()
        }
    }

    fun setPixelLightTurnOffPendingIntent(pendingIntent: android.app.PendingIntent?) {
        pixelLightTurnOffPendingIntent = pendingIntent
    }

    private val cameraAvailabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                Log.d(TAG, "onCameraUnavailable: Camera $cameraId is now unavailable")
                handleCameraBecameUnavailable(cameraId)
            }
        }

        override fun onCameraAvailable(cameraId: String) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                Log.d(TAG, "onCameraAvailable: Camera $cameraId is now available")
                _isAvailable.value = true
                if (_isPixelLightActive.value) {
                    Log.i(TAG, "Camera $cameraId available again: PixelLight torch closed")
                    _isPixelLightActive.value = false
                    notifyTorchStateChanged(false)
                }
            }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                isStandardTorchOn = enabled
                if (!enabled && _isPixelLightActive.value) {
                    val now = SystemClock.uptimeMillis()
                    val larpDotInitiated = (now - lastLarpDotCommandTime) < 2500L
                    if (larpDotInitiated) return
                    val pixelLightInitiated = (now - DotAccessibilityService.lastPixelLightActivityTime) < 2500L
                    if (pixelLightInitiated) return
                }
                if (!_isPixelLightActive.value) {
                    notifyTorchStateChanged(enabled)
                }
                _isAvailable.value = true
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if ((cameraId == targetCameraId || targetCameraId == null) && !_isPixelLightActive.value) {
                _torchStrength.value = newStrengthLevel
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == targetCameraId || targetCameraId == null) {
                if (!_isPixelLightActive.value) {
                    _isAvailable.value = false
                    notifyTorchStateChanged(false)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(PIXELLIGHT_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PIXELLIGHT_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
        _isPixelLightInstalled.value = installed
        if (installed && shouldUsePixelLight()) {
            applyPixelLightStrengths()
        }
        return installed
    }

    fun checkPixelLightInstalled() {
        val context = appContext ?: return
        checkPixelLightInstallation(context)
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

            // Query Pixel-specific experimental flashlight key across all available cameras
            try {
                val pixelMaxKey = CameraCharacteristics.Key(
                    PIXEL_EXPERIMENTAL_MAX_BRIGHTNESS_KEY,
                    java.lang.Integer.TYPE
                )
                for (camId in cm.cameraIdList) {
                    try {
                        val c = cm.getCameraCharacteristics(camId)
                        val pixelMax = c.get(pixelMaxKey)
                        if (pixelMax != null && pixelMax > 1) {
                            pixelLightMaxStrength = pixelMax
                            OverlayPreferences.setLastKnownPixelLightMax(appContext, pixelMax)
                            Log.i(TAG, "Hardware max PixelLight brightness detected on camera $camId: $pixelMax")
                            break
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.d(TAG, "No Pixel experimental flashlight key found")
            }

            if (shouldUsePixelLight()) {
                applyPixelLightStrengths()
            } else if (!_isPixelLightActive.value) {
                _maxStrength.value = standardMaxStrength
                _torchStrength.value = standardTorchStrength
                _isStrengthSupported.value = standardStrengthSupported
            }

            _isAvailable.value = flashFound

            Log.i(TAG, "Flashlight target camera: $targetCameraId, strength supported: ${_isStrengthSupported.value} (max: ${_maxStrength.value})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query camera characteristics", e)
            _hasHardwareFlash.value = false
            _isAvailable.value = false
        }
    }

    private fun applyPixelLightStrengths() {
        val context = appContext
        val max = if (pixelLightMaxStrength > 1) {
            pixelLightMaxStrength
        } else {
            val savedMax = OverlayPreferences.getLastKnownPixelLightMax(context)
            if (savedMax > 1) savedMax else 127
        }
        _maxStrength.value = max
        _isStrengthSupported.value = max > 1

        val savedStrength = OverlayPreferences.getLastKnownPixelLightStrength(context)
        if (_torchStrength.value <= 1 && savedStrength > 1) {
            _torchStrength.value = savedStrength.coerceIn(1, max)
        } else if (_torchStrength.value > max) {
            _torchStrength.value = max
        }
    }

    private fun registerCallback() {
        if (isCallbackRegistered) return
        val cm = cameraManager ?: return
        try {
            cm.registerTorchCallback(torchCallback, mainHandler)
            cm.registerAvailabilityCallback(cameraAvailabilityCallback, mainHandler)
            isCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register camera callbacks", e)
        }
    }

    private fun handleCameraBecameUnavailable(cameraId: String) {
        val now = SystemClock.uptimeMillis()
        val larpDotTriggered = (now - lastLarpDotCommandTime) < 3000L
        val pixelLightTriggered = (now - DotAccessibilityService.lastPixelLightActivityTime) < 3000L
        val isCameraApp = DotAccessibilityService.isCameraAppInForeground()

        Log.i(TAG, "handleCameraBecameUnavailable: cam=$cameraId, larpDotTriggered=$larpDotTriggered, " +
                "pixelLightTriggered=$pixelLightTriggered, isCameraApp=$isCameraApp, shouldUsePixelLight=${shouldUsePixelLight()}")

        if (shouldUsePixelLight()) {
            if (larpDotTriggered || pixelLightTriggered || !isCameraApp) {
                Log.i(TAG, "Camera $cameraId unavailable due to PixelLight. Activating flashlight state.")
                _isPixelLightActive.value = true
                applyPixelLightStrengths()
                notifyTorchStateChanged(true)
                _isAvailable.value = true
                return
            }
        }

        if (!_isPixelLightActive.value) {
            _isAvailable.value = false
        }
    }

    fun onPixelLightActivityTriggered() {
        val now = SystemClock.uptimeMillis()
        val larpDotInitiated = (now - lastLarpDotCommandTime) < 1500L
        if (larpDotInitiated) return

        val target = !_isFlashlightOn.value
        Log.i(TAG, "External PixelLight activity observed. Toggling state to: $target")
        _isPixelLightActive.value = target
        if (target) {
            applyPixelLightStrengths()
        }
        notifyTorchStateChanged(target)
    }

    fun isKnownCameraPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        if (packageName == PIXELLIGHT_PACKAGE || packageName == "com.android.systemui" || packageName == appContext?.packageName) {
            return false
        }
        if (knownCameraPackages.contains(packageName)) return true
        if (packageName.endsWith(".camera") || packageName.contains(".camera.") || packageName.contains(".cam.")) {
            knownCameraPackages.add(packageName)
            return true
        }

        val context = appContext ?: return false
        try {
            val pm = context.packageManager
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).setPackage(packageName)
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (list.isNotEmpty()) {
                knownCameraPackages.add(packageName)
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    private fun notifyTorchStateChanged(enabled: Boolean) {
        val wasOn = _isFlashlightOn.value
        _isFlashlightOn.value = enabled
        if (enabled && !wasOn) {
            lastFlashlightOnTime = SystemClock.uptimeMillis()
        }

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
            pixelLightMaxStrength = max
            _maxStrength.value = max
            _isStrengthSupported.value = max > 1
            OverlayPreferences.setLastKnownPixelLightMax(appContext, max)
        }
        if (progress > 0) {
            val now = SystemClock.uptimeMillis()
            // Stale notification protection: do not let notification echo overwrite torch strength
            // if user is actively dragging the slider or recently set the strength
            if (!isUserInteracting && (now - lastUserSetStrengthTime > 800L)) {
                _torchStrength.value = progress
                OverlayPreferences.setLastKnownPixelLightStrength(appContext, progress)
            }
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
        if (_isSimulated.value) {
            setTorch(!_isFlashlightOn.value)
            return
        }

        if (shouldUsePixelLight()) {
            lastLarpDotCommandTime = SystemClock.uptimeMillis()
            launchPixelLightToggle()
            return
        }
        setTorch(!_isFlashlightOn.value)
    }

    fun turnOn() = setTorch(true)
    fun turnOff() = setTorch(false)

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
            lastLarpDotCommandTime = SystemClock.uptimeMillis()
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
        val changed = _torchStrength.value != clamped
        _torchStrength.value = clamped
        lastUserSetStrengthTime = SystemClock.uptimeMillis()

        if (_isSimulated.value) return

        if (shouldUsePixelLight()) {
            OverlayPreferences.setLastKnownPixelLightStrength(appContext, clamped)
            if (changed) {
                schedulePixelLightBrightness(clamped)
            }
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
        lastUserSetStrengthTime = SystemClock.uptimeMillis()
        if (shouldUsePixelLight()) {
            OverlayPreferences.setLastKnownPixelLightStrength(appContext, clamped)
            pendingStrengthJob?.cancel()
            sendPixelLightBrightness(clamped)
        } else if (_isFlashlightOn.value) {
            val cm = cameraManager
            val camId = targetCameraId
            if (cm != null && camId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _isStrengthSupported.value) {
                try {
                    cm.turnOnTorchWithStrengthLevel(camId, clamped)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to flush torch strength level to $clamped", e)
                }
            }
        }
    }

    private fun schedulePixelLightBrightness(level: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStrengthUpdateTime > 200L) {
            lastStrengthUpdateTime = now
            sendPixelLightBrightness(level)
        } else {
            pendingStrengthJob?.cancel()
            pendingStrengthJob = coroutineScope.launch {
                delay(200L)
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
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

    fun openPlayStoreForPixelLight(context: Context) {
        try {
            val uri = Uri.parse("market://details?id=$PIXELLIGHT_PACKAGE")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$PIXELLIGHT_PACKAGE")
            val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun setSimulated(enabled: Boolean) {
        _isSimulated.value = enabled
        if (enabled) {
            _hasHardwareFlash.value = true
            _isAvailable.value = true
            _isStrengthSupported.value = true
            _maxStrength.value = 127
            if (_torchStrength.value <= 1) {
                _torchStrength.value = 64
            }
        }
    }

    fun toggleSimulationMode() {
        setSimulated(!_isSimulated.value)
    }
}
