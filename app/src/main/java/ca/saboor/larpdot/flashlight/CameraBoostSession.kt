package ca.saboor.larpdot.flashlight

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Size

/** Owns exactly one camera and a non-recording output for Pixel flashlight vendor requests. */
internal class CameraBoostSession(
    context: Context,
    private val cameraId: String,
    private val maximum: Int,
    private val outputSize: Size,
    private val onLevel: (Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val policy = BoostSessionPolicy()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var startupTimeout: Runnable? = null

    @SuppressLint("MissingPermission")
    fun start(level: Int) {
        stop()
        val token = policy.start(level, maximum)
        startupTimeout = Runnable { fail(token, "The camera did not finish starting flashlight boost.") }.also {
            handler.postDelayed(it, 10_000L)
        }
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!policy.accepts(token)) { device.close(); return }
                    camera = device
                    configure(device, token)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    fail(token, "Boost stopped because the camera disconnected or another app needs it.")
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    fail(token, "Unable to open the boost camera (error $error). It may be in use by another app.")
                }
            }, handler)
        } catch (error: Exception) {
            fail(token, "Unable to start camera boost: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun configure(device: CameraDevice, token: Int) {
        try {
            val output = ImageReader.newInstance(outputSize.width, outputSize.height, ImageFormat.YUV_420_888, 2)
            reader = output
            // Discard buffers immediately. No images are saved or processed.
            output.setOnImageAvailableListener({ source ->
                runCatching { source.acquireLatestImage()?.close() }
            }, handler)
            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(output.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (!policy.accepts(token)) { configured.close(); return }
                    session = configured
                    capture(token)
                }
                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    fail(token, "This camera could not configure flashlight boost.")
                }
            }, handler)
        } catch (error: Exception) {
            fail(token, "Unable to configure camera boost: ${error.message}")
        }
    }

    fun strength(level: Int) {
        policy.strength(level, maximum)
        if (session != null && policy.requestedLevel > 0) capture(policy.generation)
    }

    private fun capture(token: Int) {
        if (!policy.accepts(token)) return
        val device = camera ?: return
        val configured = session ?: return
        val surface = reader?.surface ?: return
        val level = policy.requestedLevel
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
                addTarget(surface)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                set(CaptureRequest.Key<Boolean>(ENABLED_KEY, java.lang.Boolean.TYPE), true)
                set(CaptureRequest.Key<Int>(BRIGHTNESS_KEY, Integer.TYPE), level)
            }.build()
            configured.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    if (policy.accepts(token) && level == policy.requestedLevel) {
                        startupTimeout?.let(handler::removeCallbacks)
                        startupTimeout = null
                        onLevel(level)
                    }
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    fail(token, "The camera rejected the flashlight boost request.")
                }
            }, handler)
        } catch (error: Exception) {
            fail(token, "Unable to apply flashlight boost: ${error.message}")
        }
    }

    private fun fail(token: Int, message: String) {
        if (!policy.accepts(token)) return
        stop()
        onError(message)
    }

    fun stop() {
        startupTimeout?.let(handler::removeCallbacks)
        startupTimeout = null
        policy.stop()
        runCatching { session?.close() }
        session = null
        runCatching { camera?.close() }
        camera = null
        runCatching { reader?.close() }
        reader = null
    }

    companion object {
        const val MAXIMUM_KEY = "com.google.pixel.experimental2020.flashlightBrightnessLevelMax"
        const val ENABLED_KEY = "com.google.pixel.experimental2020.flashlightBrightnessEnabled"
        const val BRIGHTNESS_KEY = "com.google.pixel.experimental2020.flashlightBrightness"
    }
}
