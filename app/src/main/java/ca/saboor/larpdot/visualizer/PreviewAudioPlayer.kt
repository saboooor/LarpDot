package ca.saboor.larpdot.visualizer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import ca.saboor.larpdot.media.MediaPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight MediaPlayer controller for listening to the 30-second iTunes song preview.
 *
 * Characteristics:
 * - Automatically pauses the currently playing song (both active system media sessions like Spotify/YT Music
 *   via AudioFocus and simulated tracks in MediaPlaybackState) when preview audio begins.
 * - Drives real-time position updates so that the visualizer animates in exact sync with the preview.
 * - Properly manages audio focus and releases resources on disposal.
 */
object PreviewAudioPlayer {

    private const val TAG = "PreviewAudioPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var currentSource: String? = null
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(30000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    var onProgressTick: ((Long) -> Unit)? = null

    fun togglePlayback(
        context: Context? = null,
        audioSource: String,
        onTick: ((Long) -> Unit)? = null,
    ) {
        onProgressTick = onTick

        if (currentSource == audioSource && mediaPlayer != null) {
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                pause()
            } else {
                // Pause current active media and grab audio focus before starting preview
                MediaPlaybackState.pause()
                context?.let { requestAudioFocus(it.applicationContext) }
                player.start()
                _isPlaying.value = true
                startTicker()
            }
            return
        }

        // New source or first time playing
        stop()
        currentSource = audioSource

        // Pause current active media before starting new preview
        MediaPlaybackState.pause()
        context?.let { requestAudioFocus(it.applicationContext) }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioSource)
                setOnPreparedListener { mp ->
                    MediaPlaybackState.pause()
                    mp.start()
                    _isPlaying.value = true
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(1000L)
                    startTicker()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    stopTicker()
                    abandonAudioFocus()
                    onProgressTick?.invoke(0L)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize preview MediaPlayer: ${e.message}")
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
        _isPlaying.value = false
        stopTicker()
        abandonAudioFocus()
    }

    fun stop() {
        stopTicker()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentSource = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        abandonAudioFocus()
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
            onProgressTick?.invoke(positionMs)
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audioManager = am
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        pause()
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed requesting audio focus: ${e.message}")
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } catch (_: Exception) {}
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                delay(50L) // 20 updates per second for smooth position sync
                val player = mediaPlayer ?: break
                if (player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    _currentPositionMs.value = pos
                    onProgressTick?.invoke(pos)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
