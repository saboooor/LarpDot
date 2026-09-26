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
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.IslandSurfaceOverlay
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.ui.overlay.IslandStack
import ca.saboor.larpdot.ui.overlay.builtInIslandStack
import ca.saboor.larpdot.ui.overlay.hasCompactStatusContent
import ca.saboor.larpdot.ui.overlay.compactStatusFallback
import ca.saboor.larpdot.ui.overlay.stackedPillWidth
import ca.saboor.larpdot.ui.overlay.stackedPillWidthWithExtents
import ca.saboor.larpdot.ui.overlay.secondaryItemWidthDp
import ca.saboor.larpdot.ui.overlay.secondaryBubbleThicknessDp
import ca.saboor.larpdot.ui.overlay.compactMediaWings
import ca.saboor.larpdot.ui.overlay.getFlashlightPercentText
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
 * Each visible island item uses one rendered IslandSurfaceOverlay across compact and expanded
 * states. CompactIslandOverlay supplies measured bounds and gestures; the selected surface
 * morphs from those bounds without swapping its rendered surface or draw window.
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
    private var isNotificationActivityActive = false

    private fun currentStack(): IslandStack<IslandType> = builtInIslandStack(
        music = isMusicActive && MediaPlaybackState.currentTrack.value.hasMedia,
        flashlight = isFlashlightActive && OverlayPreferences.showFlashlightIslandFlow.value,
        activity = isNotificationActivityActive && NotificationActivityState.current.value != null,
        activityHasRightWing = hasCompactStatusContent(NotificationActivityState.current.value),
        activityKind = NotificationActivityState.current.value?.kind,
    )

    private val _expandedType = MutableStateFlow(IslandType.MEDIA)
    val expandedType: StateFlow<IslandType> = _expandedType.asStateFlow()

    private var controllerJob = Job()
    private var controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)
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
                    val isMusicActiveNow by MediaPlaybackState.isMusicActive.collectAsState()
                    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                    val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                    val activeCutout = currentCutoutInfo ?: cutout
                    val currentActivity by NotificationActivityState.current.collectAsState()
                    val activeExpandedType by expandedType.collectAsState()
                    val stack = builtInIslandStack(
                        music = mediaTrack.hasMedia && isMusicActiveNow,
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
                                if (compactSurfaceBounds != bounds) compactSurfaceBounds = bounds
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
            observeMediaState()
            observeFlashlightState()
            observeNotificationActivityState()
            observeCutoutConfig()
            observeMinimizedAlbumArtStyle()
            observeCompactMediaGeometry()
            observeDebugPreference()
            observeBubblePreferences()
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
        announcementHideJob?.cancel()
        announcementHideJob = null
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

        currentCutoutInfo?.let(::layoutTouchWindow)
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
        currentCutoutInfo?.let { cutout ->
            if (isMusicActive || isFlashlightActive || isNotificationActivityActive) {
                layoutTouchWindow(cutout)
            }
        }
    }

    private fun createCompactLayoutParams(cutout: CutoutInfo): WindowManager.LayoutParams {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.currentWindowMetrics?.bounds?.width() ?: dm.widthPixels
        } else {
            dm.widthPixels
        }
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

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
        leftExtentPx: Int,
        rightExtentPx: Int,
        pillCenterBiasPx: Int,
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
            val totalHPx = pillHeightPx + leftExtentPx + rightExtentPx
            touchWidth = pillWidthPx + (touchPad * 2)
            touchHeight = totalHPx + (touchPad * 2)
            val orientedCenterX = if (rotation == Surface.ROTATION_270) {
                maxOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            } else {
                minOf(cutout.centerX, screenWidth.toFloat() - cutout.centerX)
            }
            posX = (orientedCenterX - touchWidth / 2f).toInt()
            val topAnchor = (cutout.centerY - (pillHeightPx / 2f) - leftExtentPx).toInt().coerceAtLeast(0)
            posY = (topAnchor - touchPad).coerceAtLeast(0)
        } else {
            val totalWPx = pillWidthPx + leftExtentPx + rightExtentPx
            touchWidth = totalWPx + (touchPad * 2)
            touchHeight = pillHeightPx + (touchPad * 2)
            val leftAnchor = cutout.centerX - (pillWidthPx / 2f) - pillCenterBiasPx - leftExtentPx
            posX = (leftAnchor - touchPad).toInt()
            val topAnchor = (cutout.centerY - (pillHeightPx / 2f)).toInt().coerceAtLeast(0)
            posY = (topAnchor - touchPad).coerceAtLeast(0)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wmInstance = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wmInstance?.currentWindowMetrics?.bounds?.width() ?: dm.widthPixels
        } else {
            dm.widthPixels
        }

        val stack = currentStack()
        val dotCount = stack.secondary.size

        val cutoutDiameterPx = (if (cutout.radiusPx > 0f) cutout.radiusPx * 2f else 24f * density).coerceIn(16f * density, 36f * density)
        val outlineAllowancePx = 2f * density
        val compactPillThicknessPx = cutoutDiameterPx + (outlineAllowancePx * 2f)
        val compactPillThicknessDp = compactPillThicknessPx / density

        val isMiniPill = OverlayPreferences.showDotRightSideInfoFlow.value
        val bubbleThicknessDp = secondaryBubbleThicknessDp(
            compactPillThicknessDp,
            OverlayPreferences.smallerBubblesFlow.value,
        )
        val bubbleSpacingDp = if (OverlayPreferences.bubbleStyleFlow.value == OverlayPreferences.BubbleStyle.MATERIAL_3) 4f else 8f
        val secondaryTypes = stack.secondary.map { it.content }
        val leftType = if (secondaryTypes.size == 2) secondaryTypes[0] else null
        val rightType = if (secondaryTypes.size == 2) secondaryTypes[1] else secondaryTypes.firstOrNull()

        val currentActivity = NotificationActivityState.current.value
        val activityStatus = currentActivity?.let { compactStatusFallback(it) }
        val flashlightStatus = getFlashlightPercentText(
            FlashlightController.torchStrength.value,
            FlashlightController.maxStrength.value,
        )

        val leftWidthDp = if (leftType != null) {
            secondaryItemWidthDp(
                type = leftType,
                thicknessDp = bubbleThicknessDp,
                isMiniPill = isMiniPill,
                hasMedia = isMusicActive,
                flashlightStatus = flashlightStatus,
                activityStatus = activityStatus,
            )
        } else 0f

        val rightWidthDp = if (rightType != null) {
            secondaryItemWidthDp(
                type = rightType,
                thicknessDp = bubbleThicknessDp,
                isMiniPill = isMiniPill,
                hasMedia = isMusicActive,
                flashlightStatus = flashlightStatus,
                activityStatus = activityStatus,
            )
        } else 0f

        val leftExtentPx = if (leftType != null) ((leftWidthDp + bubbleSpacingDp) * density).toInt() else 0
        val rightExtentPx = if (rightType != null) ((rightWidthDp + bubbleSpacingDp) * density).toInt() else 0

        val nestedArtSizeDp = (compactPillThicknessDp - 12f).coerceIn(16f, 24f)
        val nestedExtraDp = compactPillThicknessDp + nestedArtSizeDp
        val blendedExtraDp = compactPillThicknessDp * 3f

        val minimizedStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.value
        val showMinimizedTitle = OverlayPreferences.showMinimizedTitleFlow.value
        val titleExtraDp = if (showMinimizedTitle && isMusicActive) 180f else 0f
        val announcementExtraDp = 210f

        val activityExtraDp = if (currentActivity == null || activityStatus.isNullOrEmpty()) 48f else 80f

        val activeExtraDp = when (stack.primary?.content) {
            IslandType.NOTIFICATION_ACTIVITY -> activityExtraDp
            IslandType.FLASHLIGHT -> 48f
            IslandType.MEDIA -> {
                val baseExtra = when (minimizedStyle) {
                    OverlayPreferences.AlbumArtStyle.BLENDED -> blendedExtraDp
                    OverlayPreferences.AlbumArtStyle.NESTED -> nestedExtraDp
                    else -> 60f
                }
                val musicExtra = maxOf(baseExtra, titleExtraDp)
                if (isSongAnnouncementActive) maxOf(musicExtra, announcementExtraDp) else musicExtra
            }
            null -> 0f
        }

        val mediaWings = if (!isLandscape && stack.primary?.content == IslandType.MEDIA) {
            val showVisualizer = OverlayPreferences.showMinimizedVisualizerFlow.value
            val visualizerWidthDp = if (showVisualizer) {
                val count = OverlayPreferences.waveformBandCountFlow.value
                count * OverlayPreferences.waveformBarWidthFlow.value +
                    (count - 1).coerceAtLeast(0) * OverlayPreferences.waveformBarSpacingFlow.value
            } else 0f
            compactMediaWings(
                extraWidthDp = activeExtraDp,
                hasTrailingBubble = rightType != null,
                showTitle = showMinimizedTitle && isMusicActive,
                announcing = isSongAnnouncementActive,
                visualizerWidthDp = visualizerWidthDp,
                announcementText = MediaPlaybackState.currentTrack.value.artist.ifBlank { "Media Player" },
            )
        } else null
        val pillCenterBiasPx = if (mediaWings != null) {
            ((mediaWings.leadingDp - mediaWings.trailingDp) * density / 2f).toInt()
        } else 0
        val pillWPx = if (isLandscape) compactPillThicknessPx.toInt() else if (mediaWings != null) {
            ((cutoutDiameterPx / density + mediaWings.widthDp) * density).toInt()
        } else (stackedPillWidthWithExtents(
            cutoutDiameterPx / density + activeExtraDp,
            compactPillThicknessDp,
            if (leftType != null) leftWidthDp + bubbleSpacingDp else 0f,
            if (rightType != null) rightWidthDp + bubbleSpacingDp else 0f,
            screenWidth / density,
        ) * density).toInt()
        val landscapeAnnouncementExtraDp = if (isSongAnnouncementActive) 140f else activeExtraDp
        val pillHPx = if (isLandscape) (cutoutDiameterPx + (landscapeAnnouncementExtraDp * density)).toInt() else compactPillThicknessPx.toInt()

        val tParams = if (isIslandExpanded || isOverlayMorphing) {
            createExpandedTouchLayoutParams()
        } else {
            createTouchLayoutParams(cutout, pillWPx, pillHPx, leftExtentPx, rightExtentPx, pillCenterBiasPx, isLandscape, rotation, screenWidth)
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
                    updateOverlayLayout()
                }
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

    private fun observeNotificationActivityState() {
        controllerScope.launch {
            NotificationActivityState.current.collectLatest { activity ->
                isNotificationActivityActive = activity != null
                if (activity == null && isIslandExpanded && _expandedType.value == IslandType.NOTIFICATION_ACTIVITY) {
                    collapseOverlay()
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

    private fun observeCompactMediaGeometry() {
        controllerScope.launch {
            combine(
                OverlayPreferences.showMinimizedTitleFlow,
                OverlayPreferences.showMinimizedVisualizerFlow,
                OverlayPreferences.waveformBandCountFlow,
                OverlayPreferences.waveformBarWidthFlow,
                OverlayPreferences.waveformBarSpacingFlow,
            ) { _, _, _, _, _ -> Unit }.collectLatest {
                updateOverlayLayout()
            }
        }
    }

    private fun observeBubblePreferences() {
        controllerScope.launch {
            combine(
                OverlayPreferences.showDotRightSideInfoFlow,
                OverlayPreferences.smallerBubblesFlow,
                OverlayPreferences.bubbleStyleFlow,
            ) { _, _, _ -> Unit }.collectLatest {
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
        flashlightHideJob?.cancel()
        flashlightHideJob = null
        announcementHideJob?.cancel()
        announcementHideJob = null
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
