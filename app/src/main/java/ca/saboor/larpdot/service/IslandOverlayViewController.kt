package ca.saboor.larpdot.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.ui.overlay.CompactIslandOverlay
import ca.saboor.larpdot.ui.overlay.ExpandedIslandOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Two-window Dynamic Island overlay controller:
 *
 * 1. Compact Island Window: Sized strictly to the compact pill (~138dp wide), centered over
 *    the camera cutout. Because it never spans the full screen, the entire status bar area
 *    to the left and right of the island remains completely uncovered, allowing 100% native
 *    Android Notification Center pull-down gestures to work with zero interference.
 *
 * 2. Dedicated Expanded Island Window: Sized to full screen width (width = screenWidth, posX = 0).
 *    Appears only when the compact pill is expanded, morphing smoothly outward from the compact
 *    pill dimensions to the full card, and collapses back down before detaching. Because posX is
 *    always 0 and width is always screenWidth, WindowManager never resizes or shifts during morphing,
 *    eliminating all SurfaceFlinger buffer reallocation jumps and snaps.
 */
class IslandOverlayViewController(
    private val context: Context,
    private val windowType: Int,
) {
    private var windowManager: WindowManager? = null

    // Compact Island Window
    private var compactView: ComposeView? = null
    private var compactLifecycleOwner: OverlayLifecycleOwner? = null
    private var compactWindowParams: WindowManager.LayoutParams? = null

    // Dedicated Expanded Island Window
    private var expandedView: ComposeView? = null
    private var expandedLifecycleOwner: OverlayLifecycleOwner? = null
    private var expandedWindowParams: WindowManager.LayoutParams? = null
    private var isExpandedWindowAdded = false

    var isOverlayAdded = false
        private set

    private var currentCutoutInfo by mutableStateOf<CutoutInfo?>(null)
    private var isIslandExpanded by mutableStateOf(false)

    private var controllerJob = Job()
    private val controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
    private var collapseJob: Job? = null
    private var lastObservedHasMedia: Boolean? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isOverlayAdded) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val cutout = CutoutDetector.detect(context)
        currentCutoutInfo = cutout

        // 1. Setup Compact View & Lifecycle (Added first so it sits behind expanded view in Z-order)
        val compOwner = OverlayLifecycleOwner()
        compactLifecycleOwner = compOwner

        val compView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)

            // Live hardware rounded corners listener directly from view's WindowInsets (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setOnApplyWindowInsetsListener { _, insets ->
                    val tl = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                    val tr = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
                    val r = tl?.radius ?: tr?.radius ?: 0
                    if (r > 0 && currentCutoutInfo?.displayCornerRadiusPx != r.toFloat()) {
                        currentCutoutInfo = currentCutoutInfo?.copy(displayCornerRadiusPx = r.toFloat())
                    }
                    insets
                }
            }

            setContent {
                val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
                val activeCutout = currentCutoutInfo ?: cutout

                CompactIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isExpanded = isIslandExpanded,
                    onExpand = { expandOverlay() },
                )
            }
        }
        compOwner.attach(compView)
        compOwner.onCreate()
        compactView = compView

        val compParams = createCompactLayoutParams(cutout)
        compactWindowParams = compParams

        try {
            wm.addView(compView, compParams)
            isOverlayAdded = true
            observeMediaState()
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Setup Dedicated Expanded View & Lifecycle (Added second so it sits directly above compact view)
        val expOwner = OverlayLifecycleOwner()
        expandedLifecycleOwner = expOwner

        val expView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)

            setContent {
                val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
                val activeCutout = currentCutoutInfo ?: cutout

                ExpandedIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isExpanded = isIslandExpanded,
                    onCollapse = { collapseOverlay() },
                )
            }

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (isIslandExpanded) {
                        collapseOverlay()
                    }
                    return@setOnTouchListener false
                }
                false
            }
        }
        expOwner.attach(expView)
        expOwner.onCreate()
        expandedView = expView

        expView.visibility = View.GONE
        val expParams = createExpandedLayoutParams(cutout)
        expandedWindowParams = expParams
        try {
            wm.addView(expView, expParams)
            isExpandedWindowAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun expandOverlay() {
        collapseJob?.cancel()
        collapseJob = null
        if (!isIslandExpanded) {
            isIslandExpanded = true
            showExpandedWindow()
        }
    }

    fun collapseOverlay() {
        if (isIslandExpanded) {
            isIslandExpanded = false
            collapseJob?.cancel()
            collapseJob = controllerScope.launch {
                delay(440)
                collapseJob = null
                if (!isIslandExpanded) {
                    hideExpandedWindow()
                }
            }
        }
    }

    private fun showExpandedWindow() {
        val wm = windowManager ?: return
        val expView = expandedView ?: return
        val cutout = currentCutoutInfo ?: CutoutDetector.detect(context)

        val expParams = createExpandedLayoutParams(cutout)
        expandedWindowParams = expParams
        try {
            wm.updateViewLayout(expView, expParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        expView.visibility = View.VISIBLE

        updateOverlayLayout()
    }

    private fun hideExpandedWindow() {
        val wm = windowManager ?: return
        val expView = expandedView ?: return
        val cutout = currentCutoutInfo ?: CutoutDetector.detect(context)

        expView.visibility = View.GONE
        val expParams = createExpandedLayoutParams(cutout)
        expandedWindowParams = expParams
        try {
            wm.updateViewLayout(expView, expParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateOverlayLayout()
    }

    private fun createCompactLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = dm.widthPixels
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasMedia = MediaPlaybackState.currentTrack.value.hasMedia

        val paddingHorizontalPx = (28f * density).toInt()
        val topPaddingPx = (14f * density).toInt()
        val bottomPaddingPx = (28f * density).toInt()

        val cutoutDiameterPx = (cutout.radiusPx * 2f).coerceIn(24f * density, 36f * density)
        val compactWPx = (cutoutDiameterPx + (74f * density)).toInt()
        val compactHPx = (36f * density).toInt()

        val topAnchor = (cutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
        val windowPosY = (topAnchor - topPaddingPx).coerceAtLeast(0)

        val targetWidth: Int
        val targetHeight: Int
        val posX: Int
        val posY: Int

        val shouldShowDotOnly = !hasMedia

        if (shouldShowDotOnly) {
            val diameter = ((cutout.radiusPx * 2) + (10f * density)).toInt()
            targetWidth = diameter
            targetHeight = diameter
            posX = (cutout.centerX - diameter / 2f).toInt()
            posY = (cutout.centerY - diameter / 2f).toInt()
        } else {
            // Sized consistently with generous padding so the window never resizes and touch area remains stable
            targetWidth = compactWPx + (paddingHorizontalPx * 2)
            targetHeight = compactHPx + topPaddingPx + bottomPaddingPx
            val pillCenterX = if (isLandscape) cutout.centerX else screenWidth / 2f
            posX = (pillCenterX - targetWidth / 2f).toInt()
            posY = windowPosY
        }

        @Suppress("DEPRECATION")
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val flags = if (shouldShowDotOnly || isIslandExpanded) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        return WindowManager.LayoutParams(
            targetWidth,
            targetHeight,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
            applyCutoutMode(this)
        }
    }

    private fun createExpandedLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = dm.widthPixels

        val paddingPx = (14f * density).toInt()
        val compactHPx = (36f * density).toInt()
        val topAnchor = (cutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
        val windowPosY = (topAnchor - paddingPx).coerceAtLeast(0)
        val cardHPx = (190f * density).toInt()

        @Suppress("DEPRECATION")
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val touchFlags = if (isIslandExpanded) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return WindowManager.LayoutParams(
            screenWidth,
            cardHPx + (paddingPx * 2),
            windowType,
            baseFlags or touchFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = windowPosY
            applyCutoutMode(this)
        }
    }

    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } catch (_: Exception) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } catch (_: Exception) {}
        }
    }

    fun updateOverlayLayout() {
        val wm = windowManager ?: return
        val compView = compactView ?: return
        if (!isOverlayAdded) return

        val cutout = currentCutoutInfo ?: CutoutDetector.detect(context)
        val newParams = createCompactLayoutParams(cutout)
        compactWindowParams = newParams

        try {
            wm.updateViewLayout(compView, newParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isExpandedWindowAdded) {
            val expView = expandedView ?: return
            val expParams = createExpandedLayoutParams(cutout)
            expandedWindowParams = expParams
            try {
                wm.updateViewLayout(expView, expParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeMediaState() {
        controllerScope.launch {
            MediaPlaybackState.currentTrack.collectLatest { track ->
                val hasMedia = track.hasMedia

                if (hasMedia != lastObservedHasMedia) {
                    lastObservedHasMedia = hasMedia
                    if (!hasMedia && isIslandExpanded) {
                        collapseOverlay()
                    }
                }
                updateOverlayLayout()
            }
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        currentCutoutInfo = CutoutDetector.detect(context)
        updateOverlayLayout()
    }

    fun hide() {
        if (!isOverlayAdded) return
        val wm = windowManager

        collapseJob?.cancel()
        collapseJob = null
        isIslandExpanded = false

        controllerJob.cancel()
        controllerJob = Job()

        if (isExpandedWindowAdded && expandedView != null && wm != null) {
            try {
                wm.removeView(expandedView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isExpandedWindowAdded = false
        }

        if (wm != null && compactView != null) {
            try {
                wm.removeView(compactView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        compactLifecycleOwner?.onDestroy()
        compactLifecycleOwner = null
        compactView = null

        expandedLifecycleOwner?.onDestroy()
        expandedLifecycleOwner = null
        expandedView = null

        isOverlayAdded = false
        compactWindowParams = null
        expandedWindowParams = null
        lastObservedHasMedia = null
    }

    fun destroy() {
        hide()
    }
}
