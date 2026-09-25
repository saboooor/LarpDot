package ca.saboor.larpdot.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import ca.saboor.larpdot.media.MediaPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object ForegroundAppTracker {
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private val _isMusicAppOpenFlow = MutableStateFlow(false)
    val isMusicAppOpenFlow: StateFlow<Boolean> = _isMusicAppOpenFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var isInitialized = false
    private var appContext: Context? = null

    // Cache package name -> isAudioCategory
    private val audioCategoryCache = ConcurrentHashMap<String, Boolean>()

    // Ignored system packages (volume panels, navigation, keyboards, system popups)
    private val baseIgnoredPackages = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    // Known popular audio streaming and player packages
    private val knownMusicPackages = setOf(
        "com.spotify.music",
        "com.spotify.music.canary",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.soundcloud.android",
        "org.videolan.vlc",
        "com.maxmpz.audioplayer",
        "com.pandora.android",
        "deezer.android.app",
        "com.aspiro.tidal",
        "com.audiomack",
        "tunein.player",
        "com.shazam.android",
        "com.bandcamp.android",
        "com.aimp.player",
        "ch.blinkenlights.android.medialibrary",
        "com.jetappfactory.jetaudio",
        "com.jrtstudio.AnotherMusicPlayer",
        "gonemad.gmmp",
        "com.foobar2000.foobar2000",
        "in.krosbits.musicolet",
        "com.shadow.player",
    )

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val app = context.applicationContext
        appContext = app

        scope.launch {
            MediaPlaybackState.currentTrack.map { it.playerPackageName }.distinctUntilChanged().collectLatest {
                recomputeMusicAppOpen()
            }
        }

        scope.launch {
            _foregroundPackage.collectLatest {
                recomputeMusicAppOpen()
            }
        }
    }

    fun updateFromAccessibility(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return
        }

        val pkg = event.packageName?.toString() ?: return

        // Don't treat system UI (volume sliders/notification pulls) or keyboard as app switches
        if (baseIgnoredPackages.contains(pkg) || isInputMethodPackage(pkg)) {
            return
        }

        if (_foregroundPackage.value != pkg) {
            _foregroundPackage.value = pkg
        }
    }

    fun setForegroundPackage(pkg: String?) {
        val current = _foregroundPackage.value
        if (pkg != null && current != pkg) {
            if (baseIgnoredPackages.contains(pkg) || isInputMethodPackage(pkg)) {
                return
            }
            _foregroundPackage.value = pkg
        }
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        val app = appContext ?: return false
        try {
            val imm = app.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
            val ims = imm.enabledInputMethodList
            for (im in ims) {
                if (im.packageName == pkg) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isAudioApp(packageName: String): Boolean {
        if (knownMusicPackages.contains(packageName)) return true

        val activePlayer = MediaPlaybackState.currentTrack.value.playerPackageName
        if (!activePlayer.isNullOrBlank() && activePlayer == packageName) return true

        return audioCategoryCache.getOrPut(packageName) {
            checkAudioCategory(packageName)
        }
    }

    private fun checkAudioCategory(packageName: String): Boolean {
        val app = appContext ?: return false
        return try {
            val pm = app.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ai.category == ApplicationInfo.CATEGORY_AUDIO
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun recomputeMusicAppOpen() {
        val fg = _foregroundPackage.value
        if (fg.isNullOrBlank()) {
            _isMusicAppOpenFlow.value = false
            return
        }

        val activePlayer = MediaPlaybackState.currentTrack.value.playerPackageName
        val isMatch = (activePlayer != null && activePlayer == fg) || isAudioApp(fg)
        if (_isMusicAppOpenFlow.value != isMatch) {
            _isMusicAppOpenFlow.value = isMatch
        }
    }
}
