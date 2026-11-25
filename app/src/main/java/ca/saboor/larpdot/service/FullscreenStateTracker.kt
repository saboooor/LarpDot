package ca.saboor.larpdot.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks immersive fullscreen apps/games and device landscape orientation so the overlay
 * can automatically hide when watching fullscreen video or gaming.
 */
object FullscreenStateTracker {
    private val _isFullscreenOrGame = MutableStateFlow(false)
    val isFullscreenOrGame: StateFlow<Boolean> = _isFullscreenOrGame.asStateFlow()

    private val _isLandscape = MutableStateFlow(false)
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    private val gameCache = HashMap<String, Boolean>()

    fun updateOrientation(configuration: Configuration) {
        _isLandscape.value = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    internal fun detectFullscreen(
        context: Context,
        windows: List<AccessibilityWindowInfo>?,
        foregroundPackage: String? = null,
    ): Boolean? {
        if (windows.isNullOrEmpty()) return null
        val (screenW, screenH) = screenDimensions(context)
        if (screenW <= 0 || screenH <= 0) return null

        val rect = Rect()
        var statusBarVisible = false
        var appCoversFullScreen = false
        var activeAppPackage: String? = foregroundPackage

        for (window in windows) {
            window.getBoundsInScreen(rect)
            when (window.type) {
                AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
                    val isTopBar = rect.top <= 4 && rect.height() in 1..(screenH / 6) && rect.width() >= (screenW * 0.8f).toInt()
                    if (isTopBar && (pkg == null || pkg == "com.android.systemui")) {
                        statusBarVisible = true
                    }
                }
                AccessibilityWindowInfo.TYPE_APPLICATION -> {
                    val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
                    if (pkg != null && pkg != context.packageName && pkg != "com.android.systemui") {
                        if (window.isActive || activeAppPackage == null) {
                            activeAppPackage = pkg
                        }
                        if (isWindowCoveringScreen(rect.left, rect.top, rect.right, rect.bottom, screenW, screenH)) {
                            appCoversFullScreen = true
                        }
                    }
                }
            }
        }

        val isGame = activeAppPackage?.let { isGamePackage(context, it) } == true
        return isFullscreenState(
            appCoversFullScreen = appCoversFullScreen,
            statusBarVisible = statusBarVisible,
            isGame = isGame,
        )
    }

    internal fun setFullscreen(isFullscreen: Boolean) {
        _isFullscreenOrGame.value = isFullscreen
    }

    fun reset() {
        setFullscreen(false)
    }

    internal fun isWindowCoveringScreen(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        return left <= 4 && top <= 4 && right >= screenWidth - 4 && bottom >= screenHeight - 4
    }

    internal fun isFullscreenState(
        appCoversFullScreen: Boolean,
        statusBarVisible: Boolean,
        isGame: Boolean,
    ): Boolean = isGame || (appCoversFullScreen && !statusBarVisible)

    private fun isGamePackage(context: Context, packageName: String): Boolean {
        gameCache[packageName]?.let { return it }
        val result = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
        if (gameCache.size > 200) gameCache.clear()
        gameCache[packageName] = result
        return result
    }

    private fun screenDimensions(context: Context): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val bounds = wm?.currentWindowMetrics?.bounds
            if (bounds != null) bounds.width() to bounds.height()
            else context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
    }
}
