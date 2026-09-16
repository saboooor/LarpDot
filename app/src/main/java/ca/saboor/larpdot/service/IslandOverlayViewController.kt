package ca.saboor.larpdot.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
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
import ca.saboor.larpdot.ui.overlay.MtIslandOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Encapsulates the WindowManager lifecycle and Jetpack Compose overlay view for the Dynamic Island.
 * Coordinates with Compose's fluid morphing physics so that WindowManager provides an unclipped
 * canvas during expansion, and restores tight touch passthrough after collapse completes.
 */
class IslandOverlayViewController(
    private val context: Context,
    private val windowType: Int,
) {
    private var windowManager: WindowManager? = null
    private var composeView: PassthroughComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    var isOverlayAdded = false
        private set
    private var windowParams: WindowManager.LayoutParams? = null

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

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner

        val view = PassthroughComposeView(context)

        // Attach lifecycle/viewmodel/savedstate to the outer root view — ViewTree lookup traverses
        // up from the ComposeView to the window root, so the root must have these set.
        owner.attach(view)
        owner.onCreate()

        // Compose APIs go on the inner ComposeView
        view.composeView.apply {
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

                MtIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isExpanded = isIslandExpanded,
                    onExpandChange = { expanded ->
                        if (expanded) {
                            expandOverlay()
                        } else {
                            collapseOverlay()
                        }
                    },
                )
            }
        }

        // Outside-touch detector on the outer FrameLayout (receives ACTION_OUTSIDE events)
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isIslandExpanded) {
                    collapseOverlay()
                }
                return@setOnTouchListener true
            }
            false
        }

        val dm = context.resources.displayMetrics
        val density = dm.density
        val diameter = ((cutout.radiusPx * 2) + (10f * density)).toInt()

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            diameter,
            diameter,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (cutout.centerX - diameter / 2f).toInt()
            y = (cutout.centerY - diameter / 2f).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } catch (_: Exception) {}
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } catch (_: Exception) {}
            }
        }

        try {
            wm.addView(view, params)
            composeView = view
            windowParams = params
            isOverlayAdded = true
            observeMediaState()
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun expandOverlay() {
        collapseJob?.cancel()
        if (!isIslandExpanded) {
            isIslandExpanded = true
            // Expand the window height to the full card size immediately so Compose has canvas room.
            // posX and width never change, so there is no horizontal jump.
            updateOverlayLayout()
        }
    }

    fun collapseOverlay() {
        if (isIslandExpanded) {
            isIslandExpanded = false
            collapseJob?.cancel()
            // Wait for Compose's collapse animation before shrinking the window height back.
            collapseJob = controllerScope.launch {
                delay(320)
                if (!isIslandExpanded) {
                    updateOverlayLayout()
                }
            }
        }
    }

    /**
     * Updates overlay layout bounds and flags.
     *
     * Key invariant: posX and width are ALWAYS equal to the expanded card values while media is
     * present, so the window never moves horizontally. Only the HEIGHT changes between compact
     * and expanded states. Since the compact pill sits at the TOP of both windows, a height change
     * is invisible — it just clips or extends the canvas below the pill.
     *
     * Touch passthrough for the area below the compact pill is handled by FLAG_NOT_TOUCH_MODAL:
     * touches outside the window's bounding rect pass through to windows behind automatically.
     * When compact, the window is only ~64dp tall, so the "expanded island area" is outside the
     * window frame and clicks there reach underlying apps.
     */
    fun updateOverlayLayout() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        if (!isOverlayAdded) return

        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = dm.widthPixels
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cutout = currentCutoutInfo ?: CutoutDetector.detect(context)
        val hasMedia = MediaPlaybackState.currentTrack.value.hasMedia

        val targetWidth: Int
        val targetHeight: Int
        val posX: Int
        val posY: Int

        // 14dp padding around the dynamic island canvas
        val paddingPx = (14f * density).toInt()

        // Compact pill measurements
        val cutoutDiameterPx = (cutout.radiusPx * 2f).coerceIn(24f * density, 36f * density)
        val compactHPx = (36f * density).toInt()

        // Fixed vertical anchor: camera cutout center minus half compact height
        val topAnchor = (cutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
        val windowPosY = (topAnchor - paddingPx).coerceAtLeast(0)

        // Expanded card measurements — width is ALWAYS this value for media states
        val outerMarginPx = if (isLandscape) paddingPx else topAnchor
        val cardWPx = screenWidth - (outerMarginPx * 2)
        val cardHPx = (190f * density).toInt()

        if (!hasMedia) {
            // Idle Mode: tiny circle over the camera hole, fully touch-transparent
            val diameter = ((cutout.radiusPx * 2) + (10f * density)).toInt()
            targetWidth = diameter
            targetHeight = diameter
            posX = (cutout.centerX - diameter / 2f).toInt()
            posY = (cutout.centerY - diameter / 2f).toInt()
        } else {
            // Width is always the expanded card width (centered on screen).
            // Height is compact when collapsed, full card height when expanded.
            // posX never changes between compact and expanded → no horizontal jump.
            targetWidth = cardWPx + (paddingPx * 2)
            targetHeight = if (isIslandExpanded) {
                cardHPx + (paddingPx * 2)           // full expanded canvas
            } else {
                compactHPx + (paddingPx * 2)         // compact strip only
            }
            posX = ((screenWidth - targetWidth) / 2).coerceAtLeast(0)
            posY = windowPosY
        }

        // Also update the PassthroughComposeView's pill rect for left/right touch passthrough
        view.compactPillRect = if (!hasMedia || isIslandExpanded) {
            null // no restriction when expanded or no media
        } else {
            val windowWidth = targetWidth
            val compactWPx = (cutoutDiameterPx + (74f * density)).toInt()
            val pillCenterX = if (isLandscape) cutout.centerX else windowWidth / 2f
            val pillLeft = (pillCenterX - compactWPx / 2f).toInt()
            val pillTop = paddingPx
            android.graphics.Rect(pillLeft, pillTop, pillLeft + compactWPx, pillTop + compactHPx)
        }

        val params = windowParams ?: (view.layoutParams as? WindowManager.LayoutParams) ?: return
        params.width = targetWidth
        params.height = targetHeight
        params.x = posX
        params.y = posY
        params.gravity = Gravity.TOP or Gravity.START

        @Suppress("DEPRECATION")
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        params.flags = if (!hasMedia) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (isIslandExpanded) {
            baseFlags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

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

        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeMediaState() {
        controllerScope.launch {
            MediaPlaybackState.currentTrack.collectLatest { track ->
                val hasMedia = track.hasMedia
                if (hasMedia != lastObservedHasMedia) {
                    lastObservedHasMedia = hasMedia
                    if (!hasMedia && isIslandExpanded) {
                        isIslandExpanded = false
                    }
                    updateOverlayLayout()
                }
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
        val view = composeView

        collapseJob?.cancel()
        controllerJob.cancel()
        controllerJob = Job()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null

        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        composeView = null
        isOverlayAdded = false
        windowParams = null
        lastObservedHasMedia = null
    }

    fun destroy() {
        hide()
    }
}
