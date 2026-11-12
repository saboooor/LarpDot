package ca.saboor.larpdot.flashlight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.util.Log
import ca.saboor.larpdot.service.DotAccessibilityService
import ca.saboor.larpdot.service.DotOverlayService
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Standard Android torch control plus an owned Camera2 boost session on supported Pixels. */
object FlashlightController {
    private var appContext: Context? = null
    private var manager: CameraManager? = null
    private var initialized = false
    private val handler = Handler(Looper.getMainLooper())
    private var standardMaximum = 1
    private var standardOn = false
    private var cameraBusy = false
    private var desiredLevel = 1
    private var pendingStandardLevel: Int? = null
    private var standardHandoffAccepted = false
    private var standardHandoffDeadline = 0L
    private val finishStandardHandoff = object : Runnable {
        override fun run() {
            val level = pendingStandardLevel ?: return
            if (SystemClock.uptimeMillis() > standardHandoffDeadline) {
                pendingStandardLevel = null
                _lastError.value = "Unable to return to the standard flashlight after releasing the camera."
                notifyState(false)
                return
            }
            try {
                applyStandardTorch(true, level)
                standardHandoffAccepted = true
                handler.postDelayed(this, 100L)
            } catch (_: Exception) { handler.postDelayed(this, 50L) }
        }
    }
    private var boostRequested = false
    internal val isBoostRequested get() = boostRequested
    internal var boostRequestId = 0L
        private set
    private val updateBoostStrength = Runnable {
        if (boostRequested) {
            try { sendBoostCommand(foreground = false) }
            catch (error: Exception) { _lastError.value = "Unable to update boost: ${error.message}" }
        }
    }

    private fun sendBoostCommand(foreground: Boolean) {
        val context = appContext ?: return
        val intent = Intent(context, FlashlightBoostService::class.java)
            .putExtra(FlashlightBoostService.EXTRA_REQUEST_ID, boostRequestId)
        if (foreground) context.startForegroundService(intent) else context.startService(intent)
    }
    internal data class BoostConfiguration(val cameraId: String, val maximum: Int, val outputSize: Size)
    internal var boostConfiguration: BoostConfiguration? = null
        private set
    var activeCameraId: String? = null
        private set
    var lastFlashlightOnTime = 0L
        private set

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable = _isAvailable.asStateFlow()
    val isFlashlightAvailable = isAvailable
    private val _hasHardwareFlash = MutableStateFlow(false)
    val hasHardwareFlash = _hasHardwareFlash.asStateFlow()
    private val _torchStrength = MutableStateFlow(1)
    val torchStrength = _torchStrength.asStateFlow()
    private val _standardMaxStrength = MutableStateFlow(1)
    val standardMaxStrength = _standardMaxStrength.asStateFlow()
    private val _maxStrength = MutableStateFlow(1)
    val maxStrength = _maxStrength.asStateFlow()
    private val _isStrengthSupported = MutableStateFlow(false)
    val isStrengthSupported = _isStrengthSupported.asStateFlow()
    private val _isSimulated = MutableStateFlow(false)
    val isSimulated = _isSimulated.asStateFlow()
    private val _isBoostSupported = MutableStateFlow(false)
    val isBoostSupported = _isBoostSupported.asStateFlow()
    private val _isBoostStarting = MutableStateFlow(false)
    val isBoostStarting = _isBoostStarting.asStateFlow()
    private val _isBoostActive = MutableStateFlow(false)
    val isBoostActive = _isBoostActive.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        manager = context.getSystemService(CameraManager::class.java)
        discover()
        try {
            manager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId != activeCameraId) return
                    val wasOn = standardOn
                    standardOn = enabled
                    if (pendingStandardLevel != null && enabled && standardHandoffAccepted) {
                        pendingStandardLevel = null
                        handler.removeCallbacks(finishStandardHandoff)
                    }
                    if (!boostRequested && pendingStandardLevel == null) {
                        if (wasOn != enabled) refreshRange()
                        _isAvailable.value = true
                        notifyState(enabled)
                    }
                }
                override fun onTorchModeUnavailable(cameraId: String) {
                    if (cameraId != activeCameraId) return
                    standardOn = false
                    if (!boostRequested && pendingStandardLevel == null) {
                        refreshRange()
                        _isAvailable.value = false
                        notifyState(false)
                    }
                }
                override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                    if (cameraId == activeCameraId && !boostRequested && pendingStandardLevel == null) {
                        desiredLevel = newStrengthLevel.coerceIn(1, standardMaximum)
                        _torchStrength.value = desiredLevel
                    }
                }
            }, handler)
            manager?.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(cameraId: String) { availability(cameraId, false) }
                override fun onCameraUnavailable(cameraId: String) { availability(cameraId, true) }
            }, handler)
        } catch (error: Exception) {
            _lastError.value = "Unable to monitor flashlight hardware: ${error.message}"
        }
        initialized = true
    }

    private fun availability(id: String, busy: Boolean) {
        if (id != activeCameraId && id != boostConfiguration?.cameraId) return
        if (id == activeCameraId) cameraBusy = busy
        _isAvailable.value = boostRequested || pendingStandardLevel != null || !cameraBusy
        if (!busy && pendingStandardLevel != null) {
            handler.removeCallbacks(finishStandardHandoff)
            handler.post(finishStandardHandoff)
        }
        // Availability never implies that a flashlight is on.
    }

    private fun discover() {
        val cm = manager ?: return
        try {
            val cameras = cm.cameraIdList.mapNotNull { id ->
                runCatching { id to cm.getCameraCharacteristics(id) }.onFailure {
                    Log.w("FlashlightController", "Unable to inspect camera $id", it)
                }.getOrNull()
            }
            val flash = cameras.filter { it.second.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            val selected = flash.firstOrNull { it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: flash.firstOrNull()
            activeCameraId = selected?.first
            _hasHardwareFlash.value = selected != null
            _isAvailable.value = selected != null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && selected != null) {
                standardMaximum = selected.second.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            }
            // Pixel vendor support is determined by the maximum-brightness characteristic.
            // Vendor request keys need not be advertised in availableCaptureRequestKeys,
            // and the boost camera need not have standard flash/lens-facing metadata.
            boostConfiguration = cameras.firstNotNullOfOrNull { (id, characteristics) ->
                runCatching {
                    val max = characteristics.get(CameraCharacteristics.Key<Int>(CameraBoostSession.MAXIMUM_KEY, Integer.TYPE)) ?: 0
                    val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.YUV_420_888)
                    val size = sizes?.minByOrNull { it.width.toLong() * it.height }
                    if (max > 1 && size != null) {
                        BoostConfiguration(id, max, size)
                    } else null
                }.onFailure { Log.d("FlashlightController", "Boost probe failed for camera $id", it) }.getOrNull()
            }
            _isBoostSupported.value = (boostConfiguration?.maximum ?: 0) > standardMaximum
            _standardMaxStrength.value = standardMaximum
            val context = appContext!!
            if (desiredLevel == 1 && !_isFlashlightOn.value) {
                desiredLevel = OverlayPreferences.getBoostStrength(context).coerceIn(1, boostConfiguration?.maximum ?: standardMaximum)
            }
            refreshRange()
        } catch (error: Exception) {
            _lastError.value = "Unable to query flashlight hardware: ${error.message}"
            _isAvailable.value = false
        }
    }

    /** Camera metadata can change visibility after runtime permission is granted. */
    fun refreshHardware() {
        if (!boostRequested) discover()
    }

    private fun extendedRangeEnabled(): Boolean = appContext?.let {
        OverlayPreferences.isFlashlightBoostEnabled(it)
    } == true && _isBoostSupported.value

    private fun needsCamera(level: Int): Boolean = extendedRangeEnabled() &&
        FlashlightBrightnessRange(standardMaximum, boostConfiguration?.maximum ?: standardMaximum).needsCamera(level)

    private fun refreshRange() {
        _maxStrength.value = if (extendedRangeEnabled()) boostConfiguration?.maximum ?: standardMaximum else standardMaximum
        _isStrengthSupported.value = _maxStrength.value > 1
        desiredLevel = desiredLevel.coerceIn(1, _maxStrength.value)
        _torchStrength.value = desiredLevel
    }

    private fun applyStandardTorch(enabled: Boolean, level: Int) {
        val id = activeCameraId ?: error("No hardware flashlight is available.")
        val cm = manager ?: error("Camera service is unavailable.")
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && standardMaximum > 1) {
            cm.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, standardMaximum))
        } else cm.setTorchMode(id, enabled)
    }

    private fun stopBoost(resumeAt: Int? = null) {
        boostRequested = false
        _isBoostStarting.value = false
        boostRequestId++
        handler.removeCallbacks(updateBoostStrength)
        handler.removeCallbacks(finishStandardHandoff)
        pendingStandardLevel = resumeAt
        standardHandoffAccepted = false
        _isBoostActive.value = false
        appContext?.stopService(Intent(appContext, FlashlightBoostService::class.java))
        if (resumeAt != null) {
            standardHandoffDeadline = SystemClock.uptimeMillis() + 3_000L
            handler.postDelayed(finishStandardHandoff, 50L)
        }
    }

    fun setBoostEnabled(enabled: Boolean) {
        val context = appContext ?: return
        if (enabled && !boostRequested) refreshHardware()
        if (enabled && !_isBoostSupported.value) {
            _lastError.value = "This device does not expose flashlight boost controls."
            return
        }
        if (enabled && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _lastError.value = "Allow camera access to enable flashlight boost."
            return
        }
        if (_isFlashlightOn.value || boostRequested) setTorch(false)
        OverlayPreferences.setFlashlightBoostEnabled(context, enabled)
        refreshRange()
    }

    fun toggleFlashlight() = setTorch(!(_isFlashlightOn.value || boostRequested || pendingStandardLevel != null))
    fun turnOn() = setTorch(true)
    fun turnOff() = setTorch(false)

    fun setTorch(enabled: Boolean) {
        val context = appContext ?: return
        _lastError.value = null
        if (_isSimulated.value) { notifyState(enabled); return }
        if (!enabled) {
            val hadBoost = boostRequested || pendingStandardLevel != null
            if (hadBoost) stopBoost()
            pendingStandardLevel = null
            handler.removeCallbacks(finishStandardHandoff)
            try { applyStandardTorch(false, desiredLevel) }
            catch (error: Exception) { if (!hadBoost) _lastError.value = "Unable to turn off flashlight: ${error.message}" }
            if (hadBoost) notifyState(false)
            return
        }
        if (enabled && needsCamera(desiredLevel)) {
            if (boostRequested) return
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                _lastError.value = "Camera permission is required for boost. Open Flashlight settings to grant it or disable boost."
                return
            }
            pendingStandardLevel = null
            handler.removeCallbacks(finishStandardHandoff)
            boostRequested = true
            _isBoostStarting.value = true
            boostRequestId++
            try {
                // Release a standard torch before opening our own camera session.
                if (standardOn) activeCameraId?.let { manager?.setTorchMode(it, false) }
                sendBoostCommand(foreground = true)
            } catch (error: Exception) {
                onBoostError("Unable to start boost. Open LarpDot and try again: ${error.message}")
            }
            return
        }
        if (boostRequested) {
            stopBoost(resumeAt = desiredLevel.coerceAtMost(standardMaximum))
            return
        }
        if (pendingStandardLevel != null) {
            pendingStandardLevel = desiredLevel
            return
        }
        try { applyStandardTorch(enabled, desiredLevel) }
        catch (error: Exception) { _lastError.value = "Unable to change flashlight: ${error.message}" }
    }

    internal fun onBoostLevel(requestId: Long, level: Int) {
        if (!boostRequested || requestId != boostRequestId) return
        _isBoostStarting.value = false
        _isBoostActive.value = true
        _torchStrength.value = level
        _isAvailable.value = true
        notifyState(true)
    }

    internal fun onBoostError(message: String, requestId: Long = boostRequestId) {
        if (requestId != boostRequestId) return
        _lastError.value = message
        onBoostStopped(requestId)
    }

    internal fun onBoostStopped(requestId: Long) {
        if (requestId != boostRequestId) return
        boostRequested = false
        _isBoostStarting.value = false
        handler.removeCallbacks(updateBoostStrength)
        _isBoostActive.value = false
        _isAvailable.value = !cameraBusy && _hasHardwareFlash.value
        standardOn = false
        refreshRange()
        notifyState(false)
    }

    fun setStrengthLevel(level: Int) {
        desiredLevel = level.coerceIn(1, _maxStrength.value)
        _torchStrength.value = desiredLevel
        if (_isSimulated.value) return
        appContext?.let { OverlayPreferences.setBoostStrength(it, desiredLevel) }
        val active = _isFlashlightOn.value || boostRequested || pendingStandardLevel != null
        if (!active) return
        if (boostRequested && needsCamera(desiredLevel)) {
            handler.removeCallbacks(updateBoostStrength)
            handler.postDelayed(updateBoostStrength, 40L)
        } else setTorch(true)
    }

    fun setStrength(level: Int) = setStrengthLevel(level)
    fun flushStrength(level: Int) {
        setStrengthLevel(level)
        handler.removeCallbacks(updateBoostStrength)
        if (boostRequested) updateBoostStrength.run()
    }

    fun setSimulated(enabled: Boolean) {
        if (_isFlashlightOn.value || boostRequested) setTorch(false)
        _isSimulated.value = enabled
        if (enabled) {
            _isAvailable.value = true
            _isStrengthSupported.value = true
            _standardMaxStrength.value = 127
            _maxStrength.value = 127
            _torchStrength.value = 64
        } else {
            notifyState(false)
            _standardMaxStrength.value = standardMaximum
            _isAvailable.value = _hasHardwareFlash.value && !cameraBusy
            refreshRange()
        }
    }
    fun toggleSimulationMode() = setSimulated(!_isSimulated.value)

    private fun notifyState(enabled: Boolean) {
        val wasOn = _isFlashlightOn.value
        _isFlashlightOn.value = enabled
        if (enabled && !wasOn) lastFlashlightOnTime = SystemClock.uptimeMillis()
        val context = appContext ?: return
        if (enabled && OverlayPreferences.isShowFlashlightIslandEnabled(context) &&
            !DotAccessibilityService.isServiceConnected.value && Settings.canDrawOverlays(context)) {
            DotOverlayService.start(context)
        }
    }
}
