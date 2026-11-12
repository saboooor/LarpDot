package ca.saboor.larpdot.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.ui.overlay.CompactIslandOverlay
import ca.saboor.larpdot.ui.overlay.IslandLifecycle
import ca.saboor.larpdot.ui.overlay.islandTouchRegion
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.IslandSurfaceOverlay
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.ui.overlay.IslandStack
import ca.saboor.larpdot.ui.overlay.builtInIslandStack
import ca.saboor.larpdot.ui.overlay.hasCompactStatusContent
import ca.saboor.larpdot.ui.theme.LarpDotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Each visible island item uses one rendered IslandSurfaceOverlay across compact and expanded
 * states. CompactIslandOverlay supplies measured bounds and gestures; the selected surface
 * morphs from those bounds without swapping its rendered surface or draw window.
 */
class IslandOverlayViewController(
    private val context: Context,
    private val windowType: Int,
) {
    private var windowManager: WindowManager? = null
    private var rotationDisplayManager: DisplayManager? = null
    private var lastDisplayRotation: Int? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (!isOverlayAdded || displayId != Display.DEFAULT_DISPLAY) return
            val rotation = rotationDisplayManager?.getDisplay(displayId)?.rotation ?: return
            if (rotation == lastDisplayRotation) return
            lastDisplayRotation = rotation
            currentCutoutInfo = CutoutDetector.detect(context)
            updateOverlayLayout()
        }
    }

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
    private var isCompactSurfaceHidden by mutableStateOf(false)
    private var isSelectedDotFallbackHidden by mutableStateOf(false)
    private var compactPressScale by mutableFloatStateOf(1f)
    private var compactStretch by mutableFloatStateOf(0f)
    private var compactSurfaceBounds by mutableStateOf<Rect?>(null)
    private val secondarySurfaceBounds = mutableStateMapOf<IslandType, Rect>()
    private val secondarySurfaceScales = mutableStateMapOf<IslandType, Float>()
    private val secondarySurfaceStretches = mutableStateMapOf<IslandType, Float>()
    private val secondarySurfaceAlphas = mutableStateMapOf<IslandType, Float>()
    private var expansionSourceBounds by mutableStateOf<Rect?>(null)
    private var isExpandedFromTinyDot by mutableStateOf(false)
    private var expansionPrimaryType by mutableStateOf(IslandType.MEDIA)
    private var expansionDotIndex by mutableStateOf(0)
    private var expansionDotCount by mutableStateOf(0)


    private fun currentStack(): IslandStack<IslandType> = builtInIslandStack(
        music = isMusicActive && MediaPlaybackState.currentTrack.value.hasMedia && !ForegroundAppTracker.shouldHideMedia.value,
        flashlight = isFlashlightActive && OverlayPreferences.showFlashlightIslandFlow.value,
        activity = isNotificationActivityActive && NotificationActivityState.current.value != null,
        activityHasRightWing = hasCompactStatusContent(NotificationActivityState.current.value),
        activityKind = NotificationActivityState.current.value?.kind,
    )

    private val _expandedType = MutableStateFlow(IslandType.MEDIA)
    val expandedType: StateFlow<IslandType> = _expandedType.asStateFlow()

    private var controllerJob = Job()
    private var controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
    private enum class VisibilityKey(val type: IslandType? = null) {
        MUSIC(IslandType.MEDIA), FLASHLIGHT(IslandType.FLASHLIGHT),
        ACTIVITY(IslandType.NOTIFICATION_ACTIVITY), ANNOUNCEMENT,
    }
    private val islandLifecycle = IslandLifecycle<VisibilityKey> { controllerScope }
    private var isMusicActive: Boolean
        get() = islandLifecycle.isActive(VisibilityKey.MUSIC)
        set(value) { islandLifecycle.update(VisibilityKey.MUSIC, value) }
    private var isFlashlightActive: Boolean
        get() = islandLifecycle.isActive(VisibilityKey.FLASHLIGHT)
        set(value) { islandLifecycle.update(VisibilityKey.FLASHLIGHT, value) }
    private var isNotificationActivityActive: Boolean
        get() = islandLifecycle.isActive(VisibilityKey.ACTIVITY)
        set(value) { islandLifecycle.update(VisibilityKey.ACTIVITY, value) }
    private var isSongAnnouncementActive: Boolean
        get() = islandLifecycle.isActive(VisibilityKey.ANNOUNCEMENT)
        set(value) { islandLifecycle.update(VisibilityKey.ANNOUNCEMENT, value) }

    private fun updateVisibility(key: VisibilityKey, active: Boolean, exitDelayMs: Long = 0) {
        if (!active && key.type == _expandedType.value && isIslandExpanded) collapseOverlay()
        islandLifecycle.update(key, active, exitDelayMs) { updateOverlayLayout() }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isOverlayAdded) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        controllerJob.cancel()
        controllerJob = Job()
        controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)

        FlashlightController.init(context)

        isMusicActive = MediaPlaybackState.isMusicActive.value && !ForegroundAppTracker.shouldHideMedia.value
        isFlashlightActive = FlashlightController.isFlashlightOn.value && OverlayPreferences.isShowFlashlightIslandEnabled(context)
        isSongAnnouncementActive = MediaPlaybackState.isSongAnnouncementActive.value && OverlayPreferences.isShowSongAnnouncementEnabled(context)
        isNotificationActivityActive = NotificationActivityState.current.value != null

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
                    val isMusicActiveNow by MediaPlaybackState.isMusicActive.collectAsState()
                    val hideMedia by ForegroundAppTracker.shouldHideMedia.collectAsState()
                    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                    val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                    val activeCutout = currentCutoutInfo ?: cutout
                    val currentActivity by NotificationActivityState.current.collectAsState()
                    val activeExpandedType by expandedType.collectAsState()
                    val stack = builtInIslandStack(
                        music = mediaTrack.hasMedia && isMusicActiveNow && !hideMedia,
                        flashlight = isFlashlightOn && showFlashlightIsland,
                        activity = currentActivity != null,
                        activityHasRightWing = hasCompactStatusContent(currentActivity),
                        activityKind = currentActivity?.kind,
                    )
                    val compactType = stack.primary?.content
                    val secondaryTypes = stack.secondary.map { it.content }
                    val surfaceType = if ((isIslandExpanded || isOverlayMorphing) && !isExpandedFromTinyDot) {
                        activeExpandedType
                    } else {
                        compactType ?: activeExpandedType
                    }
                    LaunchedEffect(compactType) {
                        if (compactType == null) compactSurfaceBounds = null
                    }
                    LaunchedEffect(secondaryTypes) {
                        secondarySurfaceBounds.keys.toList().forEach { type ->
                            if (type !in secondaryTypes) {
                                secondarySurfaceBounds.remove(type)
                                secondarySurfaceScales.remove(type)
                                secondarySurfaceStretches.remove(type)
                                secondarySurfaceAlphas.remove(type)
                                scheduleTouchLayout()
                            }
                        }
                    }

                    // Compact placeholders own taps and swipes; visible surfaces own the morph.
                    val surfaceOnTop = isIslandExpanded || isOverlayMorphing
                    Box(Modifier.fillMaxSize()) {
                        CompactIslandOverlay(
                            modifier = Modifier.zIndex(if (surfaceOnTop) 0f else 3f),
                            cutoutInfo = activeCutout,
                            mediaInfo = mediaTrack,
                            notificationActivity = currentActivity,
                            isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                            isExpanded = isIslandExpanded,
                            hideExpandedSource = isCompactSurfaceHidden || isSelectedDotFallbackHidden,
                            renderMainPill = compactSurfaceBounds == null,
                            renderSecondarySurface = { type ->
                                type !in secondaryTypes || secondarySurfaceBounds[type] == null
                            },
                            fromTinyDot = isExpandedFromTinyDot,
                            expandedStackType = activeExpandedType,
                            onExpand = { type, fromTiny, bounds -> expandOverlay(type, fromTiny, bounds) },
                            onCompactBoundsChanged = { bounds ->
                                if (compactSurfaceBounds != bounds) {
                                    compactSurfaceBounds = bounds
                                    scheduleTouchLayout()
                                }
                            },
                            onCompactScaleChanged = { scale ->
                                if (compactPressScale != scale) compactPressScale = scale
                            },
                            onCompactStretchChanged = { stretch ->
                                if (compactStretch != stretch) compactStretch = stretch
                            },
                            onSecondaryBoundsChanged = { type, bounds ->
                                if (type in secondaryTypes && secondarySurfaceBounds[type] != bounds) {
                                    secondarySurfaceBounds[type] = bounds
                                    scheduleTouchLayout()
                                }
                            },
                            onSecondaryScaleChanged = { type, scale ->
                                if (type in secondaryTypes && secondarySurfaceScales[type] != scale) {
                                    secondarySurfaceScales[type] = scale
                                }
                            },
                            onSecondaryStretchChanged = { type, stretch ->
                                if (type in secondaryTypes && secondarySurfaceStretches[type] != stretch) {
                                    secondarySurfaceStretches[type] = stretch
                                }
                            },
                            onSecondaryAlphaChanged = { type, alpha ->
                                if (type in secondaryTypes && secondarySurfaceAlphas[type] != alpha) {
                                    secondarySurfaceAlphas[type] = alpha
                                }
                            },
                            onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                        )
                        IslandSurfaceOverlay(
                            modifier = Modifier.zIndex(1f),
                            cutoutInfo = activeCutout,
                            mediaInfo = mediaTrack,
                            notificationActivity = currentActivity,
                            isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                            expandedType = surfaceType,
                            stackPrimaryType = expansionPrimaryType,
                            stackDotIndex = expansionDotIndex,
                            stackDotCount = expansionDotCount,
                            fromTinyDot = false,
                            hasBubbleBefore = secondaryTypes.size == 2,
                            hasBubbleAfter = secondaryTypes.isNotEmpty(),
                            isExpanded = isIslandExpanded && !isExpandedFromTinyDot,
                            isCompactVisible = compactType != null && compactSurfaceBounds != null,
                            onCollapse = { collapseOverlay() },
                            onFirstFrameDrawn = {
                                if (isIslandExpanded) isCompactSurfaceHidden = true
                            },
                            onExitFinished = { finishCollapse() },
                            sourceBounds = if ((isIslandExpanded || isOverlayMorphing) && !isExpandedFromTinyDot) {
                                expansionSourceBounds ?: compactSurfaceBounds
                            } else {
                                compactSurfaceBounds
                            },
                            compactPressScale = compactPressScale,
                            compactStretch = compactStretch,
                        )
                        val dotSurfaces = buildList {
                            addAll(secondaryTypes)
                            if (isExpandedFromTinyDot && (isIslandExpanded || isOverlayMorphing) &&
                                activeExpandedType !in this
                            ) add(activeExpandedType)
                        }
                        dotSurfaces.forEach { type ->
                            key(type) {
                                val selected = isExpandedFromTinyDot && activeExpandedType == type
                                val bounds = secondarySurfaceBounds[type]
                                IslandSurfaceOverlay(
                                    modifier = Modifier.zIndex(2f),
                                    cutoutInfo = activeCutout,
                                    mediaInfo = mediaTrack,
                                    notificationActivity = currentActivity,
                                    isFlashlightOn = isFlashlightOn && showFlashlightIsland,
                                    expandedType = type,
                                    stackPrimaryType = expansionPrimaryType,
                                    stackDotIndex = expansionDotIndex,
                                    stackDotCount = expansionDotCount,
                                    fromTinyDot = true,
                                    isSecondarySurface = true,
                                    hasBubbleBefore = if (selected) {
                                        !(expansionDotCount == 2 && expansionDotIndex == 0)
                                    } else !(secondaryTypes.size == 2 && secondaryTypes.firstOrNull() == type),
                                    hasBubbleAfter = if (selected) {
                                        expansionDotCount == 2 && expansionDotIndex == 0
                                    } else secondaryTypes.size == 2 && secondaryTypes.firstOrNull() == type,
                                    isExpanded = isIslandExpanded && selected,
                                    isCompactVisible = type in secondaryTypes && bounds != null && !isCompactSurfaceHidden,
                                    compactOpacity = secondarySurfaceAlphas[type] ?: 1f,
                                    onCollapse = { collapseOverlay() },
                                    onFirstFrameDrawn = {
                                        if (selected && isIslandExpanded) isSelectedDotFallbackHidden = true
                                    },
                                    onExitFinished = { if (selected) finishCollapse() },
                                    sourceBounds = if (selected && (isIslandExpanded || isOverlayMorphing)) {
                                        expansionSourceBounds ?: bounds
                                    } else bounds,
                                    compactPressScale = secondarySurfaceScales[type] ?: 1f,
                                    compactStretch = secondarySurfaceStretches[type] ?: 0f,
                                )
                            }
                        }
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
            // A 90-to-270 degree flip preserves the configuration's landscape orientation.
            // Observe the display itself so the camera anchor still follows that rotation.
            rotationDisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            lastDisplayRotation = rotationDisplayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            rotationDisplayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            observeMediaState()
            observeFlashlightState()
            observeNotificationActivityState()
            observeCutoutConfig()
            OverlayPreferences.isDebugModeEnabled(context)
            OverlayPreferences.isShowFlashlightIslandEnabled(context)
            OverlayPreferences.isFlashlightTapToToggleEnabled(context)
            OverlayPreferences.initializeMusicSettings(context)
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun expandOverlay(type: IslandType = IslandType.MEDIA, fromDot: Boolean = false, sourceBounds: Rect? = null) {
        if (!isOverlayAdded || isOverlayMorphing || isIslandExpanded) return

        MediaPlaybackState.dismissSongAnnouncement()
        isSongAnnouncementActive = false

        val stack = currentStack()
        expansionPrimaryType = stack.primary?.content ?: type
        expansionDotCount = stack.secondary.size
        val dotIdx = stack.secondary.indexOfFirst { it.content == type }
        expansionDotIndex = if (dotIdx >= 0) dotIdx else 0

        _expandedType.value = type
        expansionSourceBounds = sourceBounds
        isExpandedFromTinyDot = fromDot
        isIslandExpanded = true
        isOverlayMorphing = true

        layoutTouchWindow()
    }

    fun collapseOverlay() {
        if (!isOverlayAdded || !isIslandExpanded) return

        isIslandExpanded = false
        isOverlayMorphing = true
    }

    private fun finishCollapse() {
        if (!isOverlayAdded || isIslandExpanded || !isOverlayMorphing) return
        isCompactSurfaceHidden = false
        isSelectedDotFallbackHidden = false
        isExpandedFromTinyDot = false
        isOverlayMorphing = false
        expansionSourceBounds = null
        if (isMusicActive || isFlashlightActive || isNotificationActivityActive) {
            layoutTouchWindow()
        }
    }

    private val touchLayoutUpdate = Runnable {
        if (isOverlayAdded) layoutTouchWindow()
    }

    private fun scheduleTouchLayout() {
        compactView?.let { view ->
            view.removeCallbacks(touchLayoutUpdate)
            view.post(touchLayoutUpdate)
        }
    }

    private fun layoutTouchWindow() {
        val wm = windowManager ?: return
        val view = touchView ?: return

        val tParams = if (isIslandExpanded || isOverlayMorphing) {
            createExpandedTouchLayoutParams()
        } else {
            // Compose owns geometry. Use the actual animated bounds instead of estimating
            // every provider's size again in the window controller.
            val stack = currentStack()
            val bounds = buildList {
                if (stack.primary != null) compactSurfaceBounds?.let { add(it) }
                stack.secondary.forEach { candidate ->
                    secondarySurfaceBounds[candidate.content]?.let { add(it) }
                }
            }
            val region = islandTouchRegion(
                bounds, 6f * context.resources.displayMetrics.density,
            ) ?: return
            WindowManager.LayoutParams(
                region.width, region.height, windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = region.left + (compactWindowParams?.x ?: 0)
                y = region.top + (compactWindowParams?.y ?: 0)
                windowAnimations = 0
                applyCutoutMode(this)
            }
        }
        val oldParams = touchWindowParams
        val geometryChanged = oldParams == null || oldParams.width != tParams.width ||
                oldParams.height != tParams.height || oldParams.x != tParams.x ||
                oldParams.y != tParams.y || oldParams.flags != tParams.flags ||
                oldParams.gravity != tParams.gravity
        touchWindowParams = tParams

        try {
            if (touchAdded && geometryChanged) {
                wm.updateViewLayout(view, tParams)
            } else if (!touchAdded) {
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
            draw?.height ?: (460f * density).toInt(),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = draw?.x ?: 0
            y = draw?.y ?: 0
            windowAnimations = 0
            applyCutoutMode(this)
        }
    }

    private fun createExpandedLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val dm = context.resources.displayMetrics
        val density = dm.density
        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val bounds = wm?.currentWindowMetrics?.bounds
            if (bounds != null) {
                Pair(bounds.width(), bounds.height())
            } else {
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } else {
            Pair(dm.widthPixels, dm.heightPixels)
        }
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270 ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val targetWidth = if (isLandscape) {
            val minDim = minOf(screenWidth, screenHeight)
            val maxDim = maxOf(screenWidth, screenHeight)
            if (screenWidth == minDim) minDim else maxDim
        } else {
            screenWidth
        }
        val targetHeight = (460f * density).toInt()

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
        val hasActivity = isNotificationActivityActive
        val shouldShowDotOnly = !hasActiveMusic && !hasFlashlight && !hasActivity
        val shouldShowTouch = (!shouldShowDotOnly || isIslandExpanded || isOverlayMorphing) && isOverlayAdded
        if (shouldShowTouch) {
            layoutTouchWindow()
        } else {
            removeTouchWindow()
        }

    }

    private fun observeMediaState() {
        controllerScope.launch {
            combine(MediaPlaybackState.isMusicActive, MediaPlaybackState.currentTrack, ForegroundAppTracker.shouldHideMedia) { active, track, hidden ->
                active && track.hasMedia && !hidden
            }.collectLatest { updateVisibility(VisibilityKey.MUSIC, it) }
        }
        controllerScope.launch {
            combine(MediaPlaybackState.isSongAnnouncementActive, OverlayPreferences.showSongAnnouncementFlow) { active, enabled ->
                active && enabled
            }.collectLatest { updateVisibility(VisibilityKey.ANNOUNCEMENT, it, 350L) }
        }
    }

    private fun observeNotificationActivityState() {
        controllerScope.launch {
            NotificationActivityState.current.collectLatest {
                updateVisibility(VisibilityKey.ACTIVITY, it != null)
            }
        }
    }

    private fun observeFlashlightState() {
        controllerScope.launch {
            combine(FlashlightController.isFlashlightOn, OverlayPreferences.showFlashlightIslandFlow) { on, enabled ->
                on && enabled
            }.collectLatest { updateVisibility(VisibilityKey.FLASHLIGHT, it, 350L) }
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
        rotationDisplayManager?.unregisterDisplayListener(displayListener)
        rotationDisplayManager = null
        lastDisplayRotation = null
        controllerJob.cancel()
        islandLifecycle.clear()
        isSongAnnouncementActive = false
        isIslandExpanded = false
        isOverlayMorphing = false
        isCompactSurfaceHidden = false
        isSelectedDotFallbackHidden = false
        compactPressScale = 1f
        compactStretch = 0f
        compactSurfaceBounds = null
        secondarySurfaceBounds.clear()
        secondarySurfaceScales.clear()
        secondarySurfaceStretches.clear()
        secondarySurfaceAlphas.clear()
        expansionSourceBounds = null
        isExpandedFromTinyDot = false

        removeTouchWindow()
        touchView = null
        touchWindowParams = null

        compactView?.let { compView ->
            compView.removeCallbacks(touchLayoutUpdate)
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
