package ca.saboor.larpdot.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
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
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.ui.overlay.CompactIslandOverlay
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.ExpandedIslandOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var isOverlayMorphing by mutableStateOf(false)
    private var isCompactHidden by mutableStateOf(false)
    private var isExpandedFromTinyDot by mutableStateOf(false)
    private var isTinyDotHidden by mutableStateOf(false)

    private val _expandedType = MutableStateFlow(IslandType.MEDIA)
    val expandedType: StateFlow<IslandType> = _expandedType.asStateFlow()

    private var controllerJob = Job()
    private var controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
    private var collapseJob: Job? = null
    private var lastObservedHasMedia: Boolean? = null
    private var lastObservedIsPlaying: Boolean? = null

    // Active visibility states coordinated with Compose show/hide animations:
    // When music pauses or flashlight turns off, these remain true during exit animation delays
    // so WindowManager does not prematurely resize or clip the collapsing island.
    private var isMusicActive = false
    private var isFlashlightActive = false
    private var flashlightHideJob: Job? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isOverlayAdded) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        controllerJob.cancel()
        controllerJob = Job()
        controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)

        FlashlightController.init(context)

        val initialTrack = MediaPlaybackState.currentTrack.value
        isMusicActive = MediaPlaybackState.isMusicActive.value
        isFlashlightActive = FlashlightController.isFlashlightOn.value && OverlayPreferences.isShowFlashlightIslandEnabled(context)
        lastObservedIsPlaying = initialTrack.isPlaying
        lastObservedHasMedia = initialTrack.hasMedia

        val cutout = CutoutDetector.detect(context)
        currentCutoutInfo = cutout

        // 1. Setup Compact View & Lifecycle (Added first so it sits behind expanded view in Z-order)
        val compOwner = OverlayLifecycleOwner()
        compactLifecycleOwner = compOwner

        val compView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
                val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                val activeCutout = currentCutoutInfo ?: cutout

                val compactIsExpanded = if (isExpandedFromTinyDot) isTinyDotHidden else isCompactHidden

                CompactIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                    isExpanded = compactIsExpanded,
                    fromTinyDot = isExpandedFromTinyDot,
                    onExpand = { type, fromTiny -> expandOverlay(type, fromTiny) },
                    onFlashlightToggle = { FlashlightController.toggleFlashlight() },
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
            observeFlashlightState()
            observeCutoutConfig()
            observeMinimizedAlbumArtStyle()
            observeDebugPreference()
            OverlayPreferences.isDebugModeEnabled(context)
            OverlayPreferences.isShowFlashlightIslandEnabled(context)
            OverlayPreferences.isFlashlightTapToToggleEnabled(context)
            OverlayPreferences.getMinimizedAlbumArtStyle(context)
            OverlayPreferences.getExpandedAlbumArtStyle(context)
            OverlayPreferences.getMinimizedAlbumArtShape(context)
            OverlayPreferences.getExpandedAlbumArtShape(context)
            OverlayPreferences.isShowProgressOutlineEnabled(context)
            OverlayPreferences.getMinimizedAlbumArtRotation(context)
            OverlayPreferences.getExpandedAlbumArtRotation(context)
            OverlayPreferences.isShowDominantColorGlowEnabled(context)
            OverlayPreferences.isShowCameraSwoopEnabled(context)
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Setup Dedicated Expanded Island View (Full screen width, transparent, click-through background)
        val expOwner = OverlayLifecycleOwner()
        expandedLifecycleOwner = expOwner

        val expView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            setContent {
                val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
                val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                val activeCutout = currentCutoutInfo ?: cutout

                val activeExpandedType by expandedType.collectAsState()

                ExpandedIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                    expandedType = activeExpandedType,
                    fromTinyDot = isExpandedFromTinyDot,
                    isExpanded = isIslandExpanded,
                    startPressScale = 1.08f, // Matches islandScale spring target in CompactIslandOverlay
                    onCollapse = { collapseOverlay() },
                    onFirstFrameDrawn = {
                        if (isIslandExpanded) {
                            if (!isExpandedFromTinyDot) {
                                isCompactHidden = true
                            } else {
                                isTinyDotHidden = true
                            }
                        }
                    },
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

        expView.visibility = View.INVISIBLE
        val expParams = createExpandedLayoutParams(cutout)
        expandedWindowParams = expParams
        try {
            wm.addView(expView, expParams)
            isExpandedWindowAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun expandOverlay(type: IslandType = IslandType.MEDIA, fromDot: Boolean = false) {
        if (!isOverlayAdded || isOverlayMorphing || isIslandExpanded) return
        collapseJob?.cancel()

        _expandedType.value = type
        isExpandedFromTinyDot = fromDot
        isIslandExpanded = true
        isOverlayMorphing = true

        expandedView?.let { expView ->
            expView.visibility = View.VISIBLE
            expandedWindowParams?.let { params ->
                @Suppress("DEPRECATION")
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                try {
                    windowManager?.updateViewLayout(expView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        compactView?.let { compView ->
            compactWindowParams?.let { params ->
                @Suppress("DEPRECATION")
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                try {
                    windowManager?.updateViewLayout(compView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        controllerScope.launch {
            delay(380L)
            isOverlayMorphing = false
        }
    }

    fun collapseOverlay() {
        if (!isOverlayAdded || isOverlayMorphing || !isIslandExpanded) return
        collapseJob?.cancel()

        isIslandExpanded = false
        isOverlayMorphing = true
        isCompactHidden = false
        isTinyDotHidden = false

        compactView?.let { compView ->
            compactWindowParams?.let { params ->
                val hasActive = isMusicActive || isFlashlightActive
                @Suppress("DEPRECATION")
                val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                params.flags = if (!hasActive) {
                    baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                }
                try {
                    windowManager?.updateViewLayout(compView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        collapseJob = controllerScope.launch {
            delay(300L)
            expandedView?.let { expView ->
                expView.visibility = View.INVISIBLE
                expandedWindowParams?.let { params ->
                    @Suppress("DEPRECATION")
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    try {
                        windowManager?.updateViewLayout(expView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            isExpandedFromTinyDot = false
            isOverlayMorphing = false
        }
    }

    @Suppress("DEPRECATION")
    private fun createCompactLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val realMetrics = DisplayMetrics()
        display?.getRealMetrics(realMetrics)

        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else dm.widthPixels
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasActiveMusic = isMusicActive
        val hasFlashlight = isFlashlightActive
        val isSplit = hasActiveMusic && hasFlashlight
        val showFlashlightInMain = hasFlashlight && !hasActiveMusic

        val cutoutDiameterPx = (if (cutout.radiusPx > 0f) cutout.radiusPx * 2f else 24f * density).coerceIn(16f * density, 36f * density)
        val outlineAllowancePx = 2f * density
        val compactPillThicknessPx = cutoutDiameterPx + (outlineAllowancePx * 2f)
        val compactPillThicknessDp = compactPillThicknessPx / density
        val nestedArtSizeDp = (compactPillThicknessDp - 12f).coerceIn(16f, 24f)
        val nestedExtraDp = compactPillThicknessDp + nestedArtSizeDp
        val blendedExtraDp = compactPillThicknessDp * 3f

        val minimizedStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.value
        val compactExtraDp = if (showFlashlightInMain) {
            48f
        } else if (hasActiveMusic) {
            when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedExtraDp
                else -> 60f
            }
        } else if (hasFlashlight) {
            48f
        } else {
            when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedExtraDp
                else -> 60f
            }
        }
        val shouldShowDotOnly = !hasActiveMusic && !hasFlashlight

        val targetWidth: Int
        val targetHeight: Int
        val posX: Int
        val posY: Int

        if (isLandscape) {
            val pillWPx = compactPillThicknessPx.toInt()
            val pillHPx = (cutoutDiameterPx + (compactExtraDp * density)).toInt()
            val splitExtraHPx = if (isSplit) (compactPillThicknessPx + (8f * density)).toInt() else 0
            val paddingPx = (14f * density).toInt()
            val minTitleWPx = (140f * density).toInt()
            val minLandscapeWPx = (90f * density).toInt()

            targetWidth = maxOf(pillWPx + (paddingPx * 2), minLandscapeWPx)
            targetHeight = pillHPx + splitExtraHPx + (paddingPx * 2)
            val orientedCenterX = if (rotation == Surface.ROTATION_270) {
                maxOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            } else {
                minOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            }
            posX = (orientedCenterX - targetWidth / 2f).toInt()
            val topAnchor = (cutout.centerY - (pillHPx / 2f)).toInt().coerceAtLeast(0)
            posY = (topAnchor - paddingPx).coerceAtLeast(0)
        } else {
            val compactWPx = (cutoutDiameterPx + (compactExtraDp * density)).toInt()
            val compactHPx = compactPillThicknessPx.toInt()
            val splitExtraWPx = if (isSplit) (compactPillThicknessPx + (8f * density)).toInt() else 0
            val paddingHorizontalPx = (14f * density).toInt()
            val topAnchor = (cutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast(0)
            val topPaddingPx = (14f * density).toInt()
            val bottomPaddingPx = (14f * density).toInt()
            val windowPosY = (topAnchor - topPaddingPx).coerceAtLeast(0)

            val minTitleWPx = (180f * density).toInt()
            val baseWPx = compactWPx + (paddingHorizontalPx * 2)

            targetWidth = baseWPx + splitExtraWPx
            targetHeight = compactHPx + topPaddingPx + bottomPaddingPx
            posX = (cutout.centerX - (baseWPx / 2f)).toInt()
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
            windowAnimations = 0
            applyCutoutMode(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun createExpandedLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val realMetrics = DisplayMetrics()
        display?.getRealMetrics(realMetrics)

        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else dm.widthPixels
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val targetWidth = if (isLandscape) {
            val minDim = minOf(realMetrics.widthPixels, realMetrics.heightPixels)
            val maxDim = maxOf(realMetrics.widthPixels, realMetrics.heightPixels)
            if (screenWidth == minDim) minDim else maxDim
        } else {
            screenWidth
        }
        val targetHeight = (320f * density).toInt()

        @Suppress("DEPRECATION")
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val flags = if (!isIslandExpanded) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        return WindowManager.LayoutParams(
            targetWidth,
            targetHeight,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            windowAnimations = 0
            applyCutoutMode(this)
        }
    }

    fun updateCutout(cutout: CutoutInfo) {
        currentCutoutInfo = cutout
        updateOverlayLayout()
    }

    fun updateOverlayLayout() {
        val wm = windowManager ?: return
        val cutout = currentCutoutInfo ?: return

        compactView?.let { compView ->
            val compParams = createCompactLayoutParams(cutout)
            compactWindowParams = compParams
            try {
                wm.updateViewLayout(compView, compParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        expandedView?.let { expView ->
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
            MediaPlaybackState.isMusicActive.collectLatest { active ->
                isMusicActive = active
                val hasFlashlight = isFlashlightActive
                if (!active && !hasFlashlight && isIslandExpanded) {
                    collapseOverlay()
                }
                updateOverlayLayout()
            }
        }
        controllerScope.launch {
            MediaPlaybackState.currentTrack.collectLatest { track ->
                val hasMedia = track.hasMedia
                if (hasMedia != lastObservedHasMedia) {
                    lastObservedHasMedia = hasMedia
                    val hasFlashlight = isFlashlightActive
                    if (!hasMedia && !hasFlashlight && isIslandExpanded) {
                        collapseOverlay()
                    }
                }
                updateOverlayLayout()
            }
        }
    }

    private fun observeFlashlightState() {
        controllerScope.launch {
            FlashlightController.isFlashlightOn.collectLatest { isOn ->
                val hasMedia = isMusicActive
                if (!isOn && !hasMedia && isIslandExpanded) {
                    collapseOverlay()
                } else if (!isOn && isIslandExpanded && _expandedType.value == IslandType.FLASHLIGHT) {
                    collapseOverlay()
                }

                val showFlashlight = OverlayPreferences.isShowFlashlightIslandEnabled(context)
                if (isOn && showFlashlight) {
                    flashlightHideJob?.cancel()
                    flashlightHideJob = null
                    isFlashlightActive = true
                    updateOverlayLayout()
                } else {
                    flashlightHideJob?.cancel()
                    if (isFlashlightActive) {
                        flashlightHideJob = controllerScope.launch {
                            delay(350L)
                            isFlashlightActive = false
                            updateOverlayLayout()
                        }
                    } else {
                        isFlashlightActive = false
                        updateOverlayLayout()
                    }
                }
            }
        }
    }

    private fun observeCutoutConfig() {
        controllerScope.launch {
            OverlayPreferences.cutoutConfigFlow.collectLatest {
                updateOverlayLayout()
            }
        }
    }

    private fun observeMinimizedAlbumArtStyle() {
        controllerScope.launch {
            OverlayPreferences.minimizedAlbumArtStyleFlow.collectLatest {
                updateOverlayLayout()
            }
        }
    }

    private fun observeDebugPreference() {
        controllerScope.launch {
            OverlayPreferences.isDebugModeFlow.collectLatest {
                updateOverlayLayout()
            }
        }
    }

    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        currentCutoutInfo = CutoutDetector.detect(context)
        updateOverlayLayout()
    }

    fun hide() {
        dismiss()
    }

    fun destroy() {
        dismiss()
    }

    fun dismiss() {
        if (!isOverlayAdded) return
        isOverlayAdded = false
        controllerJob.cancel()

        compactView?.let { compView ->
            compactLifecycleOwner?.onDestroy()
            compactLifecycleOwner = null
            try {
                windowManager?.removeView(compView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            compactView = null
        }

        expandedView?.let { expView ->
            expandedLifecycleOwner?.onDestroy()
            expandedLifecycleOwner = null
            try {
                windowManager?.removeView(expView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            expandedView = null
            isExpandedWindowAdded = false
        }
    }
}
