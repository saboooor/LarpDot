package ca.saboor.larpdot.media

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenStateTracker
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaSessionAction(
    val id: String,
    val label: String,
    val active: Boolean = false,
    val iconName: String? = null,
    val iconResourceId: Int = 0,
    val iconPackageName: String? = null,
    val pendingIntent: PendingIntent? = null,
)

data class MediaTrackInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkColors: ArtworkColors = DominantColorExtractor.defaultColors,
    val isSimulated: Boolean = false,
    val controller: MediaController? = null,
    val playerPackageName: String? = null,
    val appName: String? = null,
    val sessionActions: List<MediaSessionAction> = emptyList(),
) {
    val dominantColor: Color get() = artworkColors.primary
    val hasMedia: Boolean get() = title.isNotBlank() || isPlaying
}

object MediaPlaybackState {
    private var applicationContext: Context? = null

    private var isBlacklistObserverStarted = false

    fun isPackageBlacklisted(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (!OverlayPreferences.appBlacklistEnabledFlow.value) return false
        return OverlayPreferences.blacklistedPackagesFlow.value.contains(packageName)
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        // Hydrate blacklist and pause-timeout flows once so callbacks stay lock/IO-free.
        OverlayPreferences.isAppBlacklistEnabled(applicationContext!!)
        OverlayPreferences.getMusicPauseTimeoutSeconds(applicationContext!!)
        if (!isBlacklistObserverStarted) {
            isBlacklistObserverStarted = true
            playbackScope.launch {
                combine(
                    OverlayPreferences.appBlacklistEnabledFlow,
                    OverlayPreferences.blacklistedPackagesFlow,
                ) { enabled, blacklisted ->
                    enabled to blacklisted
                }.collectLatest { (enabled, blacklisted) ->
                    val currentPkg = _currentTrack.value.playerPackageName ?: activeController?.packageName
                    if (enabled && currentPkg != null && blacklisted.contains(currentPkg)) {
                        clear()
                    }
                }
            }
        }
    }

    private fun inferActionActive(label: String, id: String = "", iconName: String? = null): Boolean {
        val icon = iconName.orEmpty().lowercase()
        if (listOf("_off", "outline", "unselected").any { it in icon }) return false
        if (listOf("_on", "favorited", "filled", "selected").any { it in icon }) return true

        val value = "$label $id".lowercase()
        return when {
            listOf("add to", "turn on", "enable", "not favorite", "not favourite").any { it in value } -> false
            listOf("remove from", "unlike", "turn off", "disable", "enabled", "active").any { it in value } -> true
            ("repeat one" in value || "repeat all" in value) -> true
            else -> false
        }
    }

    private val _currentTrack = MutableStateFlow(MediaTrackInfo())
    val currentTrack: StateFlow<MediaTrackInfo> = _currentTrack.asStateFlow()

    /** High-frequency playback position stream, decoupled from [currentTrack] to avoid recomposing the whole overlay 10x/sec. */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isMusicActive = MutableStateFlow(false)
    val isMusicActive: StateFlow<Boolean> = _isMusicActive.asStateFlow()

    private val _isSongAnnouncementActive = MutableStateFlow(false)
    val isSongAnnouncementActive: StateFlow<Boolean> = _isSongAnnouncementActive.asStateFlow()
    private var songAnnouncementJob: Job? = null
    private var lastAnnouncedTrackKey: String = ""

    fun triggerSongAnnouncement(durationMs: Long = 3600L) {
        songAnnouncementJob?.cancel()
        _isSongAnnouncementActive.value = true
        songAnnouncementJob = playbackScope.launch {
            delay(durationMs)
            _isSongAnnouncementActive.value = false
        }
    }

    fun dismissSongAnnouncement() {
        songAnnouncementJob?.cancel()
        songAnnouncementJob = null
        _isSongAnnouncementActive.value = false
    }

    var lastPlayTime: Long = 0L
        private set
    var lastPauseTime: Long = 0L
        private set

    private var musicGraceJob: Job? = null
    private var activeController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null
    private var sessionMetadata: MediaMetadata? = null
    private var sessionArtwork: Bitmap? = null

    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null
    private var artworkUpdateJob: Job? = null
    private var lastArtworkCandidate: Bitmap? = null

    private fun resetArtworkUpdate() {
        artworkUpdateJob?.cancel()
        artworkUpdateJob = null
        lastArtworkCandidate = null
    }

    private fun updateArtwork(candidate: Bitmap?) {
        if (candidate == null || candidate.isRecycled || candidate === lastArtworkCandidate) return
        lastArtworkCandidate = candidate
        artworkUpdateJob?.cancel()
        val displayedArtwork = _currentTrack.value.albumArt
        if (candidate === displayedArtwork) return

        artworkUpdateJob = playbackScope.launch {
            val colors = withContext(Dispatchers.Default) {
                // Media apps can send a fresh Bitmap containing the same cover on each update.
                if (displayedArtwork != null && !displayedArtwork.isRecycled && displayedArtwork.sameAs(candidate)) null
                else DominantColorExtractor.extractArtworkColors(candidate)
            } ?: return@launch
            _currentTrack.value = _currentTrack.value.copy(albumArt = candidate, artworkColors = colors)
        }
    }

    fun updateFromControllers(controllers: List<MediaController>?) {
        val filtered = controllers?.filter { !isPackageBlacklisted(it.packageName) }
        val playingController = filtered?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: filtered?.firstOrNull()

        if (playingController != null) {
            attachController(playingController)
        } else if (!_currentTrack.value.isSimulated) {
            clear()
        }
    }

    fun attachDirectController(controller: MediaController) {
        if (isPackageBlacklisted(controller.packageName)) {
            return
        }
        attachController(controller)
    }

    private fun attachController(controller: MediaController) {
        if (isPackageBlacklisted(controller.packageName)) {
            return
        }
        if (activeController?.sessionToken != controller.sessionToken) {
            controllerCallback?.let { activeController?.unregisterCallback(it) }
            resetArtworkUpdate()
            activeController = controller
            sessionMetadata = controller.metadata
            sessionArtwork = null

            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (controllerCallback !== this) return
                    sessionMetadata = metadata
                    refreshFromController()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (controllerCallback !== this) return
                    refreshFromController()
                }

                override fun onSessionDestroyed() {
                    if (controllerCallback !== this) return
                    clear()
                }

            }
            controllerCallback = callback
            controller.registerCallback(callback)
        }
        refreshFromController()
    }

    private fun refreshFromController() {
        val controller = activeController ?: return
        if (isPackageBlacklisted(controller.packageName)) {
            clear()
            return
        }
        val metadata = sessionMetadata
        val state = controller.playbackState

        val current = _currentTrack.value

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val rawAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val rawArtwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
        val samePlayer = controller.packageName == current.playerPackageName

        val title = when {
            !rawTitle.isNullOrBlank() -> rawTitle
            current.title.isNotBlank() && current.title != "Playing Track" -> current.title
            else -> "Playing Track"
        }
        val artist = when {
            !rawArtist.isNullOrBlank() -> rawArtist
            current.artist.isNotBlank() && current.artist != "Media Player" -> current.artist
            else -> "Media Player"
        }
        val album = when {
            !rawAlbum.isNullOrBlank() -> rawAlbum
            samePlayer && title == current.title -> current.album
            else -> ""
        }
        if (rawArtwork != null || !samePlayer || title != current.title) {
            sessionArtwork = rawArtwork
        }
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L)
            ?: current.durationMs.coerceAtLeast(0L)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val playerResources = runCatching {
            applicationContext?.packageManager?.getResourcesForApplication(controller.packageName)
        }.getOrNull()
        val notificationActions = if (controller.packageName == current.playerPackageName) {
            current.sessionActions.filter { it.pendingIntent != null }
        } else {
            emptyList()
        }
        val sessionActions = buildList {
            state?.customActions.orEmpty().forEach { action ->
                val label = action.name?.toString()?.takeIf { it.isNotBlank() } ?: action.action
                val iconName = runCatching {
                    action.icon.takeIf { it != 0 }?.let { icon ->
                        playerResources?.getResourceEntryName(icon)
                    }
                }.getOrNull()
                val notificationMatch = notificationActions.firstOrNull {
                    it.label.equals(label, ignoreCase = true) ||
                            action.action.contains(it.label, ignoreCase = true) ||
                            it.id.contains(label, ignoreCase = true)
                }
                val displayLabel = notificationMatch?.label ?: label
                add(
                    MediaSessionAction(
                        id = action.action,
                        // Notification labels are intended for display and remain meaningful
                        // after Media3 replaces them with opaque legacy command identifiers.
                        label = displayLabel,
                        active = inferActionActive(displayLabel, action.action, iconName),
                        iconName = iconName,
                        iconResourceId = action.icon,
                        iconPackageName = controller.packageName,
                        pendingIntent = notificationMatch?.pendingIntent,
                    )
                )
            }
            notificationActions.forEach { notificationAction ->
                if (none { it.label.equals(notificationAction.label, ignoreCase = true) }) {
                    add(notificationAction)
                }
            }
        }.sortedByDescending { it.pendingIntent != null }

        val rawPosition = state?.position ?: 0L
        val lastUpdate = state?.lastPositionUpdateTime ?: 0L
        val speed = if ((state?.playbackSpeed ?: 1f) > 0f) state?.playbackSpeed ?: 1f else 1f
        val calculatedPosition = if (isPlaying && lastUpdate > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - lastUpdate
            (rawPosition + (elapsed * speed).toLong()).coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        } else {
            rawPosition.coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        }

        val trackKey = "${title}_${artist}".trim()
        val wasMusicActive = _isMusicActive.value
        val isNewTrack = trackKey.isNotBlank() && (trackKey != lastAnnouncedTrackKey || !wasMusicActive)

        val wasPlaying = current.isPlaying
        val graceDelayMs = resolvePauseGraceDelayMs(OverlayPreferences.musicPauseTimeoutSecondsFlow.value)
        if (isPlaying) {
            musicGraceJob?.cancel()
            musicGraceJob = null
            lastPlayTime = SystemClock.uptimeMillis()
            lastPauseTime = 0L
            _isMusicActive.value = true
            if (isNewTrack) {
                lastAnnouncedTrackKey = trackKey
                MusicSleepTimer.onTrackChanged(trackKey)
                triggerSongAnnouncement()
            }
        } else if (wasPlaying) {
            lastPauseTime = SystemClock.uptimeMillis()
            schedulePauseGrace()
        } else if (graceDelayMs == null && title.isNotBlank() && title != "Playing Track") {
            musicGraceJob?.cancel()
            musicGraceJob = null
            _isMusicActive.value = true
        } else if (graceDelayMs != null && (lastPauseTime == 0L || SystemClock.uptimeMillis() - lastPauseTime >= graceDelayMs)) {
            musicGraceJob?.cancel()
            musicGraceJob = null
            _isMusicActive.value = false
        }

        val isIdenticalTrack = title == current.title &&
            artist == current.artist &&
            album == current.album &&
            isPlaying == current.isPlaying &&
            duration == current.durationMs &&
            samePlayer &&
            sessionActions == current.sessionActions

        if (isIdenticalTrack && kotlin.math.abs(calculatedPosition - _positionMs.value) < 1000L) {
            _positionMs.value = calculatedPosition
            updateArtwork(sessionArtwork)
            checkTickerState(isPlaying)
            return
        }

        _positionMs.value = calculatedPosition
        _currentTrack.value = MediaTrackInfo(
            title = title,
            artist = artist,
            album = album,
            albumArt = current.albumArt.takeIf { samePlayer },
            isPlaying = isPlaying,
            positionMs = calculatedPosition,
            durationMs = duration,
            artworkColors = if (samePlayer) current.artworkColors else DominantColorExtractor.defaultColors,
            controller = controller,
            playerPackageName = controller.packageName,
            appName = current.appName,
            sessionActions = sessionActions,
            isSimulated = false,
        )

        updateArtwork(sessionArtwork)
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
        val controllerPackage = activeController?.packageName
        if (controllerPackage != null && packageName != null && packageName != controllerPackage) return
        val newPackage = packageName ?: current.playerPackageName
        if (isPackageBlacklisted(newPackage)) return

        val controllerTitle = sessionMetadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        }?.takeIf { it.isNotBlank() }

        val controllerArtist = sessionMetadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        }?.takeIf { it.isNotBlank() }

        val controllerAlbum = sessionMetadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }

        // Canonical MediaController metadata is preferred over notification composite text
        val newTitle = when {
            controllerTitle != null -> controllerTitle
            current.title.isNotBlank() && current.title != "Playing Track" -> current.title
            !title.isNullOrBlank() -> title
            else -> current.title
        }

        val newArtist = when {
            controllerArtist != null -> controllerArtist
            current.artist.isNotBlank() && current.artist != "Media Player" -> current.artist
            !artist.isNullOrBlank() -> artist
            else -> current.artist
        }

        val resolvedAppName = appName ?: current.appName
        val samePlayer = newPackage == current.playerPackageName
        val newAlbum = controllerAlbum ?: if (samePlayer && newTitle == current.title) current.album else ""
        val candidate = sessionArtwork ?: artwork
        if (newTitle == current.title &&
            newArtist == current.artist &&
            newAlbum == current.album &&
            samePlayer &&
            resolvedAppName == current.appName
        ) {
            updateArtwork(candidate)
            return
        }

        if (!samePlayer) resetArtworkUpdate()
        _currentTrack.value = current.copy(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            albumArt = current.albumArt.takeIf { samePlayer },
            artworkColors = if (samePlayer) current.artworkColors else DominantColorExtractor.defaultColors,
            playerPackageName = newPackage,
            appName = resolvedAppName,
        )
        updateArtwork(candidate)

        val notifTrackKey = "${newTitle}_${newArtist}".trim()
        if (current.isPlaying && notifTrackKey.isNotBlank() && notifTrackKey != lastAnnouncedTrackKey) {
            lastAnnouncedTrackKey = notifTrackKey
            triggerSongAnnouncement()
        }
    }

    /** Returns the grace delay in ms after pausing, or null when set to keep visible until dismissed. */
    internal fun resolvePauseGraceDelayMs(timeoutSeconds: Int): Long? =
        if (timeoutSeconds <= 0) null else timeoutSeconds.toLong() * 1000L

    private fun schedulePauseGrace() {
        musicGraceJob?.cancel()
        musicGraceJob = null
        val graceDelayMs = resolvePauseGraceDelayMs(OverlayPreferences.musicPauseTimeoutSecondsFlow.value) ?: return
        musicGraceJob = playbackScope.launch {
            delay(graceDelayMs)
            if (!_currentTrack.value.isPlaying) {
                _isMusicActive.value = false
            }
        }
    }

    fun pause() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            if (track.isPlaying) {
                lastPauseTime = SystemClock.uptimeMillis()
                _currentTrack.value = track.copy(isPlaying = false)
                checkTickerState(false)
                schedulePauseGrace()
            }
            return
        }
        try {
            activeController?.transportControls?.pause()
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            val newPlaying = !track.isPlaying
            if (newPlaying) {
                musicGraceJob?.cancel()
                musicGraceJob = null
                lastPlayTime = SystemClock.uptimeMillis()
                lastPauseTime = 0L
                _isMusicActive.value = true
            } else {
                lastPauseTime = SystemClock.uptimeMillis()
                schedulePauseGrace()
            }
            _currentTrack.value = track.copy(isPlaying = newPlaying)
            checkTickerState(newPlaying)
            if (newPlaying) {
                triggerSongAnnouncement()
            }
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
            val artworkColors = DominantColorExtractor.extractArtworkColors(artwork)
            lastAnnouncedTrackKey = "${nextTitle}_The Weeknd"
            _positionMs.value = 0L
            _currentTrack.value = track.copy(
                title = nextTitle,
                artist = "The Weeknd",
                albumArt = artwork,
                artworkColors = artworkColors,
                positionMs = 0L,
            )
            triggerSongAnnouncement()
            return
        }
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        val track = _currentTrack.value
        if (track.isSimulated) {
            val prevTitle = if (track.title == "Starboy") "Die For You" else "Starboy"
            val artwork = DominantColorExtractor.createSampleArtwork(prevTitle)
            val artworkColors = DominantColorExtractor.extractArtworkColors(artwork)
            lastAnnouncedTrackKey = "${prevTitle}_The Weeknd"
            _positionMs.value = 0L
            _currentTrack.value = track.copy(
                title = prevTitle,
                artist = "The Weeknd",
                albumArt = artwork,
                artworkColors = artworkColors,
                positionMs = 0L,
            )
            triggerSongAnnouncement()
            return
        }
        activeController?.transportControls?.skipToPrevious()
    }

    const val DEFAULT_QUICK_SEEK_MS: Long = 10_000L

    internal fun computeSeekTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val safeCurrent = currentMs.coerceAtLeast(0L)
        val rawTarget = safeCurrent + deltaMs
        return if (durationMs > 0L) rawTarget.coerceIn(0L, durationMs) else rawTarget.coerceAtLeast(0L)
    }

    fun seekBy(deltaMs: Long) {
        val track = _currentTrack.value
        val target = computeSeekTarget(_positionMs.value, deltaMs, track.durationMs)
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value
        val clamped = if (track.durationMs > 0L) {
            positionMs.coerceIn(0L, track.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        _positionMs.value = clamped
        if (track.isSimulated) {
            _currentTrack.value = track.copy(positionMs = clamped)
            return
        }
        activeController?.transportControls?.seekTo(clamped)
    }

    fun performSessionAction(action: MediaSessionAction) {
        // Give immediate visual feedback. The next notification/session update replaces this
        // optimistic value with the player's authoritative state.
        val current = _currentTrack.value
        _currentTrack.value = current.copy(
            sessionActions = current.sessionActions.map {
                if (it.id == action.id) it.copy(active = !it.active) else it
            },
        )

        action.pendingIntent?.let { intent ->
            try {
                intent.send()
                return
            } catch (_: PendingIntent.CanceledException) {
                // The notification may have just been replaced; try the live session below.
            }
        }
        val controller = activeController ?: return
        // Some players put required command arguments in the CustomAction itself. Sending only
        // the action string (with null extras) makes those buttons appear valid but do nothing.
        val publishedAction = controller.playbackState?.customActions
            ?.firstOrNull { it.action == action.id }
            ?: return
        // Use the object overload so Media3/compat sessions receive the complete platform action,
        // including the metadata used to reconstruct a session command.
        controller.transportControls.sendCustomAction(publishedAction, publishedAction.extras)

        // Most sessions emit a playback-state callback after handling the command. Refresh as a
        // fallback as well so toggled labels/icons (Favorite -> Remove favorite, etc.) update.
        playbackScope.launch {
            delay(150L)
            if (activeController?.sessionToken == controller.sessionToken) refreshFromController()
        }
    }

    fun updateNotificationActions(
        packageName: String,
        actions: List<Pair<String, PendingIntent>>,
    ) {
        val current = _currentTrack.value
        if (current.isSimulated || current.playerPackageName != packageName) return

        val notificationActions = actions.mapIndexed { index, (label, intent) ->
            MediaSessionAction(
                id = "notification:$packageName:$index:$label",
                label = label,
                active = inferActionActive(label),
                pendingIntent = intent,
            )
        }
        // Keep media-session entries even when a previous notification refresh already attached
        // a PendingIntent. They carry the authoritative app icon resource and on/off state.
        val sessionActions = current.sessionActions.filter {
            it.iconResourceId != 0 || !it.id.startsWith("notification:")
        }
        val merged = sessionActions.map { sessionAction ->
            val match = notificationActions.firstOrNull {
                it.label.equals(sessionAction.label, ignoreCase = true) ||
                        sessionAction.id.contains(it.label, ignoreCase = true)
            }
            if (match != null) sessionAction.copy(pendingIntent = match.pendingIntent) else sessionAction
        }.toMutableList()
        notificationActions.forEach { action ->
            if (merged.none { it.label.equals(action.label, ignoreCase = true) }) merged += action
        }
        _currentTrack.value = current.copy(
            sessionActions = merged.sortedByDescending { it.pendingIntent != null },
        )
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
            var previousTick = SystemClock.elapsedRealtime()
            while (isActive) {
                // Suspend with zero wakeups while the display is off; snap position on wake.
                if (!ScreenStateTracker.isScreenOn.value) {
                    ScreenStateTracker.isScreenOn.first { it }
                    previousTick = SystemClock.elapsedRealtime()
                }
                delay(100L)
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - previousTick).coerceAtLeast(0L)
                previousTick = now
                val current = _currentTrack.value
                if (!current.isPlaying || current.durationMs <= 0L) continue

                if (current.isSimulated) {
                    val nextPos = (_positionMs.value + elapsedMs).let {
                        if (it > current.durationMs) 0L else it
                    }
                    _positionMs.value = nextPos
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
                        _positionMs.value = actualPos
                    }
                }
            }
        }
    }

    fun clear() {
        resetArtworkUpdate()
        dismissSongAnnouncement()
        lastAnnouncedTrackKey = ""
        musicGraceJob?.cancel()
        musicGraceJob = null
        _isMusicActive.value = false
        lastPlayTime = 0L
        lastPauseTime = 0L
        progressTickerJob?.cancel()
        progressTickerJob = null
        controllerCallback?.let { activeController?.unregisterCallback(it) }
        controllerCallback = null
        activeController = null
        sessionMetadata = null
        sessionArtwork = null
        _positionMs.value = 0L
        _currentTrack.value = MediaTrackInfo()
    }
}
