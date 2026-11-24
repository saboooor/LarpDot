package ca.saboor.larpdot.visualizer

import android.content.Context
import ca.saboor.larpdot.service.AudioCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time hardware Audio Visualizer state holder backed by
 * [AudioCaptureService] (AudioPlaybackCapture via MediaProjection).
 */
object LiveAudioVisualizer {

    private val _liveAmplitudes = MutableStateFlow<FloatArray?>(null)
    val liveAmplitudes: StateFlow<FloatArray?> = _liveAmplitudes.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateAmplitudes(amplitudes: FloatArray) {
        _liveAmplitudes.value = amplitudes
    }

    fun reportCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
        if (!capturing) {
            _liveAmplitudes.value = null
        }
    }

    fun reportError(error: String?) {
        _errorMessage.value = error
    }

    fun stop(context: Context) {
        AudioCaptureService.stop(context)
        _isCapturing.value = false
        _liveAmplitudes.value = null
    }
}
