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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.ui.overlay.CompactIslandOverlay
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.ExpandedIslandOverlay
import ca.saboor.larpdot.ui.theme.LarpDotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Dynamic Island overlay controller:
 *
 * Architecture inspired by Essentials (IslandWindowHost):
 * 1. Compact Island Draw Window (compactView):
 *    Has a stable width/position centered horizontally on the camera cutout and
 *    FLAG_NOT_TOUCHABLE. Because the draw window never moves its (x, y) coordinates or resizes
 *    during compact state morphs (e.g. song announcement expanding and shrinking back), WindowManager
 *    and SurfaceFlinger never perform asynchronous window repositioning, eliminating all visual shifts
 *    and buffer latency jumps.
 *
 * 2. Compact Touch Interception Window (touchView):
 *    A lightweight, transparent view sized and positioned strictly over the active compact pill.
 *    It intercepts user touches and forwards them to compactView.dispatchTouchEvent.
 *    Because touchView has no visual drawing or surface buffer, repositioning/resizing it
 *    creates zero visual artifacts. Outside of touchView, touches pass directly through to the status bar.
 *
 * Compact and expanded content share this one stable draw window. The expanded surface starts at
 * the compact surface's bounds and morphs in place, avoiding a cross-window frame handoff.
 */
class IslandOverlayViewController(
    private val context: Context,
    private val windowType: Int,
) {
    private var windowManager: WindowManager? = null

    // Compact Island Draw Window
    private var compactView: ComposeView? = null
    private var compactLifecycleOwner: OverlayLifecycleOwner? = null
    private var compactWindowParams: WindowManager.LayoutParams? = null

    // Compact Island Touch Interception Window
    private var touchView: View? = null
    private var touchWindowParams: WindowManager.LayoutParams? = null
    private var touchAdded = false

    var isOverlayAdded = false
        private set

    private var currentCutoutInfo by mutableStateOf<CutoutInfo?>(null)
    private var isIslandExpanded by mutableStateOf(false)
    private var isOverlayMorphing by mutableStateOf(false)
    private var isExpandedFromTinyDot by mutableStateOf(false)

    private val _expandedType = MutableStateFlow(IslandType.MEDIA)
    val expandedType: StateFlow<IslandType> = _expandedType.asStateFlow()

    private var controllerJob = Job()
    private var controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
    private var collapseJob: Job? = null
    private var lastObservedHasMedia: Boolean? = null
    private var lastObservedIsPlaying: Boolean? = null

    // Active visibility states coordinated with Compose show/hide animations:
    private var isMusicActive = false
    private var isFlashlightActive = false
    private var flashlightHideJob: Job? = null
    private var isSongAnnouncementActive = false
    private var announcementHideJob: Job? = null

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
        isSongAnnouncementActive = MediaPlaybackState.isSongAnnouncementActive.value && OverlayPreferences.isShowSongAnnouncementEnabled(context)
        lastObservedIsPlaying = initialTrack.isPlaying
        lastObservedHasMedia = initialTrack.hasMedia

        val cutout = CutoutDetector.detect(context)
        currentCutoutInfo = cutout

        // 1. Setup Compact View & Lifecycle (Draw Surface)
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
                LarpDotTheme {
                    val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
                    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                    val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                    val activeCutout = currentCutoutInfo ?: cutout

                    val activeExpandedType by expandedType.collectAsState()

                    Box(Modifier.fillMaxSize()) {
                        CompactIslandOverlay(
                            cutoutInfo = activeCutout,
                            mediaInfo = mediaTrack,
                            isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                            isExpanded = isIslandExpanded,
                            fromTinyDot = isExpandedFromTinyDot,
                            onExpand = { type, fromTiny -> expandOverlay(type, fromTiny) },
                            onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                        )
                        ExpandedIslandOverlay(
                            cutoutInfo = activeCutout,
                            mediaInfo = mediaTrack,
                            isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                            expandedType = activeExpandedType,
                            fromTinyDot = isExpandedFromTinyDot,
                            isExpanded = isIslandExpanded,
                            onCollapse = { collapseOverlay() },
                        )
                    }
                }
            }
        }
        compOwner.attach(compView)
        compOwner.onCreate()
        compactView = compView

        // Setup touch forwarding view
        val tView = View(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (isIslandExpanded) collapseOverlay()
                    return@setOnTouchListener false
                }
                val comp = compactView ?: return@setOnTouchListener false
                val cParams = compactWindowParams ?: return@setOnTouchListener false
                val tParams = touchWindowParams ?: return@setOnTouchListener false
                val copy = MotionEvent.obtain(event)
                copy.offsetLocation((tParams.x - cParams.x).toFloat(), (tParams.y - cParams.y).toFloat())
                val handled = comp.dispatchTouchEvent(copy)
                copy.recycle()
                handled
            }
        }
        touchView = tView

        val compParams = createExpandedLayoutParams(cutout)
        compactWindowParams = compParams

        try {
            wm.addView(compView, compParams)
            isOverlayAdded = true
            observeMediaState()
            observeFlashlightState()
            observeCutoutConfig()
            observeMinimizedAlbumArtStyle()
            OverlayPreferences.isShowMinimizedTitleEnabled(context)
            OverlayPreferences.isShowSongAnnouncementEnabled(context)
            observeDebugPreference()
            OverlayPreferences.isDebugModeEnabled(context)
            OverlayPreferences.isShowFlashlightIslandEnabled(context)
            OverlayPreferences.isFlashlightTapToToggleEnabled(context)
            OverlayPreferences.getMinimizedAlbumArtStyle(context)
            OverlayPreferences.getMinimizedAlbumArtRotation(context)
            OverlayPreferences.getExpandedAlbumArtRotation(context)
            OverlayPreferences.isShowExpandedAlbumArtEnabled(context)
            OverlayPreferences.isShowDominantColorGlowEnabled(context)
            OverlayPreferences.isShowCameraSwoopEnabled(context)
            OverlayPreferences.getVisualizerMode(context)
            OverlayPreferences.getWaveformBandCount(context)
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun expandOverlay(type: IslandType = IslandType.MEDIA, fromDot: Boolean = false) {
        if (!isOverlayAdded || isOverlayMorphing || isIslandExpanded) return
        collapseJob?.cancel()

        MediaPlaybackState.dismissSongAnnouncement()
        announcementHideJob?.cancel()
        announcementHideJob = null
        isSongAnnouncementActive = false

        _expandedType.value = type
        isExpandedFromTinyDot = fromDot
        isIslandExpanded = true
        isOverlayMorphing = true

        currentCutoutInfo?.let(::layoutTouchWindow)
    }

    fun collapseOverlay() {
        if (!isOverlayAdded || !isIslandExpanded) return
        collapseJob?.cancel()

        isIslandExpanded = false
        isOverlayMorphing = true

        collapseJob = controllerScope.launch {
            delay(300L)
            isExpandedFromTinyDot = false
            isOverlayMorphing = false
            currentCutoutInfo?.let { cutout ->
                val hasMusic = isMusicActive
                val hasFlash = isFlashlightActive
                if (hasMusic || hasFlash) {
                    layoutTouchWindow(cutout)
                }
            }
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

        // Draw window maintains a stable maximum width during music playback so WindowManager
        // never resizes or shifts the draw surface on announcements or track changes.
        val maxMusicExtraDp = 210f
        val compactExtraDp = if (showFlashlightInMain) {
            48f
        } else if (hasActiveMusic) {
            maxOf(blendedExtraDp, maxMusicExtraDp)
        } else if (hasFlashlight) {
            48f
        } else {
            0f
        }

        val targetWidth: Int
        val targetHeight: Int
        val posX: Int
        val posY: Int

        if (isLandscape) {
            val effectiveLandscapeExtraDp = if (hasActiveMusic) 140f else compactExtraDp
            val pillWPx = compactPillThicknessPx.toInt()
            val pillHPx = (cutoutDiameterPx + (effectiveLandscapeExtraDp * density)).toInt()
            val splitExtraHPx = if (isSplit) (compactPillThicknessPx + (8f * density)).toInt() else 0
            val paddingPx = (14f * density).toInt()
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

        // compactView is purely for drawing and never blocks touches directly;
        // touches are intercepted and routed by touchView
        val flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

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

    private fun createTouchLayoutParams(
        cutout: CutoutInfo,
        pillWidthPx: Int,
        pillHeightPx: Int,
        splitExtraPx: Int,
        isLandscape: Boolean,
        rotation: Int,
        screenWidth: Int,
    ): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val touchPad = (6f * density).toInt()

        val touchWidth: Int
        val touchHeight: Int
        val posX: Int
        val posY: Int

        if (isLandscape) {
            val totalHPx = pillHeightPx + splitExtraPx
            touchWidth = pillWidthPx + (touchPad * 2)
            touchHeight = totalHPx + (touchPad * 2)
            val orientedCenterX = if (rotation == Surface.ROTATION_270) {
                maxOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            } else {
                minOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            }
            posX = (orientedCenterX - touchWidth / 2f).toInt()
            val topAnchor = (cutout.centerY - (pillHeightPx / 2f)).toInt().coerceAtLeast(0)
            posY = (topAnchor - touchPad).coerceAtLeast(0)
        } else {
            val totalWPx = pillWidthPx + splitExtraPx
            touchWidth = totalWPx + (touchPad * 2)
            touchHeight = pillHeightPx + (touchPad * 2)
            posX = (cutout.centerX - (pillWidthPx / 2f) - touchPad).toInt()
            val topAnchor = (cutout.centerY - (pillHeightPx / 2f)).toInt().coerceAtLeast(0)
            posY = (topAnchor - touchPad).coerceAtLeast(0)
        }

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        return WindowManager.LayoutParams(
            touchWidth,
            touchHeight,
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

    private fun layoutTouchWindow(cutout: CutoutInfo) {
        val wm = windowManager ?: return
        val view = touchView ?: return

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val dm = context.resources.displayMetrics
        val density = dm.density
        val realMetrics = DisplayMetrics()
        display?.getRealMetrics(realMetrics)
        val screenWidth = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else dm.widthPixels

        val hasActiveMusic = isMusicActive
        val hasFlashlight = isFlashlightActive
        val showFlashlightInMain = hasFlashlight && !hasActiveMusic
        val isSplit = hasActiveMusic && hasFlashlight

        val cutoutDiameterPx = (if (cutout.radiusPx > 0f) cutout.radiusPx * 2f else 24f * density).coerceIn(16f * density, 36f * density)
        val outlineAllowancePx = 2f * density
        val compactPillThicknessPx = cutoutDiameterPx + (outlineAllowancePx * 2f)
        val compactPillThicknessDp = compactPillThicknessPx / density
        val nestedArtSizeDp = (compactPillThicknessDp - 12f).coerceIn(16f, 24f)
        val nestedExtraDp = compactPillThicknessDp + nestedArtSizeDp
        val blendedExtraDp = compactPillThicknessDp * 3f

        val minimizedStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.value
        val showMinimizedTitle = OverlayPreferences.showMinimizedTitleFlow.value
        val titleExtraDp = if (showMinimizedTitle && hasActiveMusic) 180f else 0f
        val announcementExtraDp = 210f

        val activeExtraDp = if (showFlashlightInMain) {
            48f
        } else if (hasActiveMusic) {
            val baseExtra = when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedExtraDp
                else -> 60f
            }
            val musicExtra = maxOf(baseExtra, titleExtraDp)
            if (isSongAnnouncementActive) maxOf(musicExtra, announcementExtraDp) else musicExtra
        } else if (hasFlashlight) {
            48f
        } else {
            0f
        }

        val pillWPx = if (isLandscape) compactPillThicknessPx.toInt() else (cutoutDiameterPx + (activeExtraDp * density)).toInt()
        val landscapeAnnouncementExtraDp = if (isSongAnnouncementActive) 140f else activeExtraDp
        val pillHPx = if (isLandscape) (cutoutDiameterPx + (landscapeAnnouncementExtraDp * density)).toInt() else compactPillThicknessPx.toInt()
        val splitExtraPx = if (isSplit) (compactPillThicknessPx + (8f * density)).toInt() else 0

        val tParams = if (isIslandExpanded) {
            createExpandedTouchLayoutParams()
        } else {
            createTouchLayoutParams(cutout, pillWPx, pillHPx, splitExtraPx, isLandscape, rotation, screenWidth)
        }
        touchWindowParams = tParams

        try {
            if (touchAdded) {
                wm.updateViewLayout(view, tParams)
            } else {
                wm.addView(view, tParams)
                touchAdded = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeTouchWindow() {
        if (!touchAdded) return
        val view = touchView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeViewImmediate(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        touchAdded = false
    }

    private fun createExpandedTouchLayoutParams(): WindowManager.LayoutParams {
        val draw = compactWindowParams
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            draw?.width ?: context.resources.displayMetrics.widthPixels,
            draw?.height ?: (320f * density).toInt(),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = draw?.x ?: 0
            y = draw?.y ?: 0
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

        // This is the single stable draw surface. Input is always handled by the lightweight
        // forwarding window, so WindowManager never has to swap or resize a visual surface.
        val flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

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
            val compParams = createExpandedLayoutParams(cutout)
            val oldParams = compactWindowParams
            val changed = oldParams == null ||
                    oldParams.x != compParams.x ||
                    oldParams.y != compParams.y ||
                    oldParams.width != compParams.width ||
                    oldParams.height != compParams.height ||
                    oldParams.flags != compParams.flags
            compactWindowParams = compParams
            if (changed) {
                try {
                    wm.updateViewLayout(compView, compParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val hasActiveMusic = isMusicActive
        val hasFlashlight = isFlashlightActive
        val shouldShowDotOnly = !hasActiveMusic && !hasFlashlight
        val shouldShowTouch = (!shouldShowDotOnly || isIslandExpanded) && isOverlayAdded
        if (shouldShowTouch) {
            layoutTouchWindow(cutout)
        } else {
            removeTouchWindow()
        }

    }

    private fun observeMediaState() {
        controllerScope.launch {
            MediaPlaybackState.isMusicActive.collectLatest { active ->
                isMusicActive = active
                val hasFlashlight = isFlashlightActive
                if (!active && !hasFlashlight && isIslandExpanded) {
                    collapseOverlay()
                } else if (!active && isIslandExpanded && _expandedType.value == IslandType.MEDIA) {
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
        controllerScope.launch {
            MediaPlaybackState.isSongAnnouncementActive.collectLatest { active ->
                val showAnnouncement = OverlayPreferences.isShowSongAnnouncementEnabled(context)
                if (active && showAnnouncement) {
                    announcementHideJob?.cancel()
                    announcementHideJob = null
                    isSongAnnouncementActive = true
                    updateOverlayLayout()
                } else {
                    announcementHideJob?.cancel()
                    if (isSongAnnouncementActive) {
                        announcementHideJob = controllerScope.launch {
                            delay(350L) // Wait for Compose smooth collapse animation before updating touch bounds
                            isSongAnnouncementActive = false
                            updateOverlayLayout()
                        }
                    } else {
                        isSongAnnouncementActive = false
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
        collapseJob?.cancel()
        collapseJob = null
        flashlightHideJob?.cancel()
        flashlightHideJob = null
        announcementHideJob?.cancel()
        announcementHideJob = null
        isSongAnnouncementActive = false
        isIslandExpanded = false
        isOverlayMorphing = false
        isExpandedFromTinyDot = false

        removeTouchWindow()
        touchView = null
        touchWindowParams = null

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

    }
}
