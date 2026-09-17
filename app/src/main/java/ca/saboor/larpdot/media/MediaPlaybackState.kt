package ca.saboor.larpdot.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaTrackInfo(
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val dominantColor: Color = DominantColorExtractor.DEFAULT_ACCENT,
    val isSimulated: Boolean = false,
    val controller: MediaController? = null,
    val playerPackageName: String? = null,
    val appName: String? = null,
) {
    val hasMedia: Boolean get() = title.isNotBlank() || isPlaying
}

object MediaPlaybackState {
    private val _currentTrack = MutableStateFlow(MediaTrackInfo())
    val currentTrack: StateFlow<MediaTrackInfo> = _currentTrack.asStateFlow()

    private var activeController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null

    private val playbackScope = CoroutineScope(Dispatchers.Main)
    private var progressTickerJob: Job? = null

    fun updateFromControllers(controllers: List<MediaController>?) {
        val playingController = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        if (playingController != null) {
            attachController(playingController)
        } else if (!_currentTrack.value.isSimulated) {
            clear()
        }
    }

    private fun attachController(controller: MediaController) {
        if (activeController?.sessionToken != controller.sessionToken) {
            controllerCallback?.let { activeController?.unregisterCallback(it) }
            activeController = controller

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    refreshFromController()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    refreshFromController()
                }
            }
            controllerCallback = callback
            controller.registerCallback(callback)
        }
        refreshFromController()
    }

    private fun refreshFromController() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Playing Track"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Media Player"
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        val rawPosition = state?.position ?: 0L
        val lastUpdate = state?.lastPositionUpdateTime ?: 0L
        val speed = if ((state?.playbackSpeed ?: 1f) > 0f) state?.playbackSpeed ?: 1f else 1f
        val calculatedPosition = if (isPlaying && lastUpdate > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - lastUpdate
            (rawPosition + (elapsed * speed).toLong()).coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        } else {
            rawPosition.coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        }

        val dominant = DominantColorExtractor.extractDominantColor(albumArt, (title + artist).hashCode())

        _currentTrack.value = MediaTrackInfo(
            title = title,
            artist = artist,
            albumArt = albumArt,
            isPlaying = isPlaying,
            positionMs = calculatedPosition,
            durationMs = duration,
            dominantColor = dominant,
            controller = controller,
            playerPackageName = controller.packageName,
            isSimulated = false,
        )

        checkTickerState(isPlaying)
    }

    fun updateNotificationMetadata(
        title: String?,
        artist: String?,
        artwork: Bitmap?,
        packageName: String? = null,
        appName: String? = null,
    ) {
        val current = _currentTrack.value
        if (current.isSimulated) return
        val newTitle = title ?: current.title
        val newArtist = artist ?: current.artist
        val newArt = when {
            artwork == null -> current.albumArt
            current.albumArt == null -> artwork
            artwork.width.toLong() * artwork.height >=
                current.albumArt.width.toLong() * current.albumArt.height -> artwork
            else -> current.albumArt
        }
        val dominant = DominantColorExtractor.extractDominantColor(newArt, (newTitle + newArtist).hashCode())
        _currentTrack.value = current.copy(
            title = newTitle,
            artist = newArtist,
            albumArt = newArt,
            dominantColor = dominant,
            playerPackageName = packageName ?: current.playerPackageName,
            appName = appName ?: current.appName,
        )
    }

    fun togglePlayPause() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            val newPlaying = !track.isPlaying
            _currentTrack.value = track.copy(isPlaying = newPlaying)
            checkTickerState(newPlaying)
            return
        }
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipNext() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            val nextTitle = if (track.title == "Starboy") "Blinding Lights" else "Starboy"
            val artwork = DominantColorExtractor.createSampleArtwork(nextTitle)
            val dominant = DominantColorExtractor.extractDominantColor(artwork)
            _currentTrack.value = track.copy(
                title = nextTitle,
                artist = "The Weeknd",
                albumArt = artwork,
                dominantColor = dominant,
                positionMs = 0L,
            )
            return
        }
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            val prevTitle = if (track.title == "Starboy") "Die For You" else "Starboy"
            val artwork = DominantColorExtractor.createSampleArtwork(prevTitle)
            val dominant = DominantColorExtractor.extractDominantColor(artwork)
            _currentTrack.value = track.copy(
                title = prevTitle,
                artist = "The Weeknd",
                albumArt = artwork,
                dominantColor = dominant,
                positionMs = 0L,
            )
            return
        }
        activeController?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value
        if (track.isSimulated) {
            _currentTrack.value = track.copy(positionMs = positionMs.coerceIn(0L, track.durationMs))
            return
        }
        activeController?.transportControls?.seekTo(positionMs)
    }

    private fun checkTickerState(isPlaying: Boolean) {
        if (isPlaying) {
            startProgressTicker()
        } else {
            progressTickerJob?.cancel()
            progressTickerJob = null
        }
    }

    private fun startProgressTicker() {
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = playbackScope.launch {
            while (isActive) {
                delay(200L) // 5 updates per second for silky smooth active song progress
                val current = _currentTrack.value
                if (!current.isPlaying || current.durationMs <= 0L) continue

                if (current.isSimulated) {
                    val nextPos = (current.positionMs + 200L).let {
                        if (it > current.durationMs) 0L else it
                    }
                    _currentTrack.value = current.copy(positionMs = nextPos)
                } else {
                    val controller = activeController ?: continue
                    val pbState = controller.playbackState ?: continue
                    if (pbState.state == PlaybackState.STATE_PLAYING) {
                        val lastUpdateTime = pbState.lastPositionUpdateTime
                        val speed = if (pbState.playbackSpeed > 0f) pbState.playbackSpeed else 1f
                        val elapsed = if (lastUpdateTime > 0L) {
                            SystemClock.elapsedRealtime() - lastUpdateTime
                        } else 0L
                        val actualPos = (pbState.position + (elapsed * speed).toLong())
                            .coerceIn(0L, current.durationMs)
                        _currentTrack.value = current.copy(positionMs = actualPos)
                    }
                }
            }
        }
    }

    fun setSimulatedPlayback(enabled: Boolean) {
        progressTickerJob?.cancel()
        progressTickerJob = null

        if (enabled) {
            val sampleArt = DominantColorExtractor.createSampleArtwork("Starboy")
            val dominant = DominantColorExtractor.extractDominantColor(sampleArt)

            _currentTrack.value = MediaTrackInfo(
                title = "Starboy",
                artist = "The Weeknd",
                albumArt = sampleArt,
                isPlaying = true,
                positionMs = 65000L,
                durationMs = 230000L,
                dominantColor = dominant,
                isSimulated = true,
                playerPackageName = "com.spotify.music",
                appName = "Spotify",
            )

            checkTickerState(true)
        } else {
            clear()
        }
    }

    fun clear() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        controllerCallback?.let { activeController?.unregisterCallback(it) }
        controllerCallback = null
        activeController = null
        _currentTrack.value = MediaTrackInfo()
    }
}
