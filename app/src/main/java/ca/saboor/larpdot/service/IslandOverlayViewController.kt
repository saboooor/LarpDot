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
    private var musicHideJob: Job? = null
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
        isMusicActive = initialTrack.hasMedia && initialTrack.isPlaying
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
            observeTitlePreference()
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

        // 2. Setup Dedicated Expanded View & Lifecycle (Added second so it sits directly above compact view)
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

    fun expandOverlay(type: IslandType = IslandType.MEDIA, fromTinyDot: Boolean = false) {
        _expandedType.value = type
        isExpandedFromTinyDot = fromTinyDot
        collapseJob?.cancel()
        collapseJob = null
        if (!isIslandExpanded) {
            isCompactHidden = false
            isTinyDotHidden = false
            isIslandExpanded = true
            isOverlayMorphing = true
            showExpandedWindow()
        }
    }

    fun collapseOverlay() {
        if (isIslandExpanded) {
            isIslandExpanded = false
            collapseJob?.cancel()
            collapseJob = controllerScope.launch {
                delay(280)
                isCompactHidden = false
                isTinyDotHidden = false
                delay(40)
                isOverlayMorphing = false
                isExpandedFromTinyDot = false
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

        isCompactHidden = false
        isExpandedFromTinyDot = false
        expView.visibility = View.INVISIBLE
        val expParams = createExpandedLayoutParams(cutout)
        expandedWindowParams = expParams
        try {
            wm.updateViewLayout(expView, expParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateOverlayLayout()
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
        val showTitlePref = OverlayPreferences.isShowMinimizedTitleEnabled(context)

        val cutoutDiameterPx = maxOf(cutout.radiusPx * 2f, 36f * density)
        val isBlended = OverlayPreferences.minimizedAlbumArtStyleFlow.value == OverlayPreferences.AlbumArtStyle.BLENDED
        val compactExtraDp = if (hasActiveMusic) {
            if (isBlended) 108f else 72f
        } else if (hasFlashlight) {
            64f
        } else {
            if (isBlended) 108f else 72f
        }
        val shouldShowDotOnly = !hasActiveMusic && !hasFlashlight

        val targetWidth: Int
        val targetHeight: Int
        val posX: Int
        val posY: Int

        if (isLandscape) {
            val pillWPx = (36f * density).toInt()
            val pillHPx = (cutoutDiameterPx + (compactExtraDp * density)).toInt()
            val splitExtraHPx = if (isSplit) ((36f + 8f) * density).toInt() else 0
            val paddingPx = (14f * density).toInt()
            val topExtraPx = if (showTitlePref) (20f * density).toInt() else 0
            val minTitleWPx = (140f * density).toInt()
            val minLandscapeWPx = (90f * density).toInt()

            targetWidth = if (showTitlePref) {
                maxOf(pillWPx + (paddingPx * 2), minTitleWPx)
            } else {
                maxOf(pillWPx + (paddingPx * 2), minLandscapeWPx)
            }
            targetHeight = pillHPx + splitExtraHPx + (paddingPx * 2) + topExtraPx
            val orientedCenterX = if (rotation == Surface.ROTATION_270) {
                maxOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            } else {
                minOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            }
            posX = (orientedCenterX - targetWidth / 2f).toInt()
            val topAnchor = (cutout.centerY - (pillHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
            posY = (topAnchor - paddingPx - topExtraPx).coerceAtLeast(0)
        } else {
            val compactWPx = (cutoutDiameterPx + (compactExtraDp * density)).toInt()
            val compactHPx = (36f * density).toInt()
            val splitExtraWPx = if (isSplit) ((36f + 8f) * density).toInt() else 0
            val paddingHorizontalPx = (14f * density).toInt()
            val topAnchor = (cutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
            val topPaddingPx = if (showTitlePref) (20f * density).toInt() else (14f * density).toInt()
            val bottomPaddingPx = (14f * density).toInt()
            val windowPosY = (topAnchor - topPaddingPx).coerceAtLeast(0)

            val minTitleWPx = (180f * density).toInt()
            val baseWPx = compactWPx + (paddingHorizontalPx * 2)
            val effectiveBaseWPx = if (showTitlePref) maxOf(baseWPx, minTitleWPx) else baseWPx

            targetWidth = effectiveBaseWPx + splitExtraWPx
            targetHeight = compactHPx + topPaddingPx + bottomPaddingPx
            posX = (cutout.centerX - (effectiveBaseWPx / 2f)).toInt()
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
        val orientedCenterX = if (rotation == Surface.ROTATION_270) {
            maxOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
        } else {
            minOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
        }
        val effectiveCutout = if (isLandscape) cutout.copy(centerX = orientedCenterX) else cutout

        val paddingPx = (14f * density).toInt()
        val cutoutDiameterPx = (effectiveCutout.radiusPx * 2f).coerceIn(20f * density, 32f * density)
        val hasMedia = MediaPlaybackState.currentTrack.value.hasMedia
        val isBlended = OverlayPreferences.minimizedAlbumArtStyleFlow.value == OverlayPreferences.AlbumArtStyle.BLENDED
        val compactExtraDp = if (hasMedia) {
            if (isBlended) 108f else 72f
        } else {
            84f
        }
        val compactHPx = if (isLandscape) (cutoutDiameterPx + (compactExtraDp * density)).toInt() else (36f * density).toInt()
        val topAnchor = (effectiveCutout.centerY - (compactHPx / 2f)).toInt().coerceAtLeast((8f * density).toInt())
        val windowPosY = (topAnchor - paddingPx).coerceAtLeast(0)
        val cardHPx = (220f * density).toInt()

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

        val rawCutout = currentCutoutInfo ?: CutoutDetector.detect(context)
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(realMetrics)
        val screenWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val orientedX = if (rotation == Surface.ROTATION_270) {
            maxOf(rawCutout.centerX, screenWidth - rawCutout.centerX)
        } else if (isLandscape) {
            minOf(rawCutout.centerX, screenWidth - rawCutout.centerX)
        } else {
            rawCutout.centerX
        }
        val cutout = if (isLandscape) rawCutout.copy(centerX = orientedX) else rawCutout
        if (currentCutoutInfo != cutout) {
            currentCutoutInfo = cutout
        }

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
                    val hasFlashlight = isFlashlightActive
                    if (!hasMedia && !hasFlashlight && isIslandExpanded) {
                        collapseOverlay()
                    }
                }

                val playingChanged = track.isPlaying != lastObservedIsPlaying
                lastObservedIsPlaying = track.isPlaying

                if (track.hasMedia && track.isPlaying) {
                    musicHideJob?.cancel()
                    musicHideJob = null
                    isMusicActive = true
                    updateOverlayLayout()
                } else if (track.hasMedia && !track.isPlaying) {
                    if (playingChanged) {
                        musicHideJob?.cancel()
                        if (isMusicActive) {
                            musicHideJob = controllerScope.launch {
                                // 5.0s pause timeout + 400ms Compose shrink animation
                                delay(5400L)
                                isMusicActive = false
                                updateOverlayLayout()
                            }
                        } else {
                            updateOverlayLayout()
                        }
                    }
                } else {
                    // No media session
                    musicHideJob?.cancel()
                    musicHideJob = null
                    if (isMusicActive) {
                        musicHideJob = controllerScope.launch {
                            delay(400L)
                            isMusicActive = false
                            updateOverlayLayout()
                        }
                    } else {
                        isMusicActive = false
                        updateOverlayLayout()
                    }
                }
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
        controllerScope.launch {
            OverlayPreferences.showFlashlightIslandFlow.collectLatest { enabled ->
                val isOn = FlashlightController.isFlashlightOn.value
                if (enabled && isOn) {
                    flashlightHideJob?.cancel()
                    flashlightHideJob = null
                    isFlashlightActive = true
                    updateOverlayLayout()
                } else if (!enabled && isFlashlightActive) {
                    flashlightHideJob?.cancel()
                    flashlightHideJob = controllerScope.launch {
                        delay(350L)
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
                currentCutoutInfo = CutoutDetector.detect(context)
                updateOverlayLayout()
            }
        }
    }

    private fun observeTitlePreference() {
        controllerScope.launch {
            OverlayPreferences.showMinimizedTitleFlow.collectLatest {
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

    fun onConfigurationChanged(newConfig: Configuration) {
        currentCutoutInfo = CutoutDetector.detect(context)
        updateOverlayLayout()
    }

    fun hide() {
        val wm = windowManager

        collapseJob?.cancel()
        collapseJob = null
        isIslandExpanded = false

        musicHideJob?.cancel()
        musicHideJob = null
        flashlightHideJob?.cancel()
        flashlightHideJob = null
        isMusicActive = false
        isFlashlightActive = false
        lastObservedIsPlaying = null
        lastObservedHasMedia = null

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
