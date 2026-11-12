package ca.saboor.larpdot.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import ca.saboor.larpdot.media.MediaPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ForegroundAppTracker {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage = _foregroundPackage.asStateFlow()
    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess = _hasUsageAccess.asStateFlow()
    val isMusicAppOpenFlow = combine(foregroundPackage, MediaPlaybackState.currentTrack) { foreground, track ->
        isActiveMediaAppOpen(foreground, track.playerPackageName)
    }.stateIn(scope, SharingStarted.Eagerly, false)
    val shouldHideMedia = combine(
        foregroundPackage, MediaPlaybackState.currentTrack, OverlayPreferences.hideMusicWhenAppOpenFlow,
    ) { foreground, track, enabled -> shouldHideMediaApp(enabled, foreground, track.playerPackageName) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var appContext: Context? = null
    private var initialized = false
    @Volatile private var keyboardPackages: Set<String> = emptySet()
    @Volatile private var keyboardCacheTime = 0L
    private val ignored = setOf("com.android.systemui", "android", "com.google.android.permissioncontroller", "com.android.permissioncontroller")

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        appContext = app
        refreshUsageAccess(app)
        scope.launch {
            val usage = app.getSystemService(UsageStatsManager::class.java)
            var cursor = 0L
            var previouslyGranted = false
            while (isActive) {
                val granted = refreshUsageAccess(app)
                if (previouslyGranted && !granted) _foregroundPackage.value = null
                previouslyGranted = granted
                val trackingNeeded = MediaPlaybackState.currentTrack.value.hasMedia && MediaPlaybackState.isMusicActive.value &&
                    OverlayPreferences.hideMusicWhenAppOpenFlow.value && ScreenStateTracker.isScreenOn.value
                if (granted && trackingNeeded && usage != null) {
                    val now = System.currentTimeMillis()
                    val start = if (cursor == 0L || cursor > now) now - 24 * 60 * 60 * 1000L else cursor
                    try {
                        val events = usage.queryEvents(start, now)
                        val event = UsageEvents.Event()
                        var foreground = _foregroundPackage.value
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && accepted(event.packageName)) {
                                foreground = event.packageName
                            } else if (event.eventType == UsageEvents.Event.KEYGUARD_SHOWN) {
                                foreground = null
                            }
                        }
                        if (_hasUsageAccess.value) _foregroundPackage.value = foreground
                        // Usage events can arrive late. Re-read the tail without publishing intermediate events.
                        cursor = now - 5_000L
                    } catch (_: Exception) {
                        cursor = 0L
                        _foregroundPackage.value = null
                    }
                } else {
                    cursor = 0L
                    if (!ScreenStateTracker.isScreenOn.value) _foregroundPackage.value = null
                }
                delay(if (trackingNeeded) 500L else 2_000L)
            }
        }
    }

    @Suppress("DEPRECATION") // Needed for usage-access checks on older Android versions.
    fun refreshUsageAccess(context: Context): Boolean {
        val granted = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        _hasUsageAccess.value = granted
        return granted
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun updateFromAccessibility(event: AccessibilityEvent) {
        if (_hasUsageAccess.value) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) setForegroundPackage(event.packageName?.toString())
    }

    fun setForegroundPackage(pkg: String?) {
        if (!_hasUsageAccess.value && accepted(pkg)) _foregroundPackage.value = pkg
    }

    private fun accepted(pkg: String?): Boolean {
        if (pkg.isNullOrBlank() || pkg in ignored) return false
        val app = appContext ?: return true
        val now = SystemClock.elapsedRealtime()
        if (keyboardCacheTime == 0L || now - keyboardCacheTime > 30_000L) {
            keyboardPackages = runCatching {
                app.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
            keyboardCacheTime = now
        }
        return pkg !in keyboardPackages
    }
}
