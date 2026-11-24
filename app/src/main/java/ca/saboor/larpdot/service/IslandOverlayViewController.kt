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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import ca.saboor.larpdot.cutout.CutoutDetector
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.ui.overlay.CompactIslandFrame
import ca.saboor.larpdot.ui.overlay.CompactIslandOverlay
import ca.saboor.larpdot.ui.overlay.IslandDismissalTracker
import ca.saboor.larpdot.ui.overlay.IslandLifecycle
import ca.saboor.larpdot.ui.overlay.activeIslandTouchBounds
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandTouchRegion
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.IslandSurfaceOverlay
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.ui.overlay.IslandSlot
import ca.saboor.larpdot.ui.overlay.builtInIslandSlots
import ca.saboor.larpdot.ui.overlay.IslandStack
import ca.saboor.larpdot.ui.overlay.builtInIslandStack
import ca.saboor.larpdot.ui.overlay.hasCompactStatusContent
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
 * CompactIslandOverlay lays out each compact surface and its touch region together.
 * Only the selected item uses IslandSurfaceOverlay during expansion and collapse, in the
 * same draw window and with its measured compact frame as the handoff.
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
    private var compactPressScale by mutableFloatStateOf(1f)
    private var compactStretch by mutableFloatStateOf(0f)
    private var selectedSlot by mutableStateOf<IslandSlot?>(null)
    private var compactSurfaceFrame by mutableStateOf<CompactIslandFrame?>(null)
    private val secondarySurfaceFrames = mutableStateMapOf<IslandSlot, CompactIslandFrame>()
    private val secondarySurfaceScales = mutableStateMapOf<IslandSlot, Float>()
    private val secondarySurfaceStretches = mutableStateMapOf<IslandSlot, Float>()
    private var expansionSourceFrame by mutableStateOf<CompactIslandFrame?>(null)
    private var expandedCardBounds by mutableStateOf<Rect?>(null)
    private var expansionSourceScale by mutableFloatStateOf(1f)
    private var expansionSourceStretch by mutableFloatStateOf(0f)
    private var autoExpandedActivityKey by mutableStateOf<String?>(null)
    private var retainedExpandedActivity by mutableStateOf<NotificationActivityInfo?>(null)
    private val seenShortTermTitles = mutableMapOf<String, String>()
    private var autoCollapseJob: Job? = null
    private val dismissalTracker = IslandDismissalTracker()
    private var dismissalRevision by mutableIntStateOf(0)
    private var idleHideJob: Job? = null

    private fun mediaDismissToken(track: MediaTrackInfo): String? =
        if (!track.hasMedia) null else "${track.playerPackageName.orEmpty()}|${track.title}|${track.artist}"

    private fun dismissIsland(type: IslandType, slot: IslandSlot?) {
        when (type) {
            IslandType.MEDIA -> mediaDismissToken(MediaPlaybackState.currentTrack.value)?.let(dismissalTracker::dismissMedia)
            IslandType.FLASHLIGHT -> dismissalTracker.dismissFlashlight(FlashlightController.lastFlashlightOnTime)
            IslandType.NOTIFICATION_ACTIVITY -> {
                val key = slot?.activityKey
                    ?: currentStack().primary?.content?.activityKey
                    ?: NotificationActivityState.current.value?.key
                if (key != null) dismissalTracker.dismissActivity(key)
            }
        }
        dismissalRevision++
        updateOverlayLayout()
    }

    private fun currentStack(): IslandStack<IslandSlot> {
        val track = MediaPlaybackState.currentTrack.value
        val primaryActivity = NotificationActivityState.current.value
        val shortTerms = NotificationActivityState.shortTermActivities.value
        val activeKeys = buildSet {
            primaryActivity?.key?.let(::add)
            shortTerms.forEach { add(it.key) }
        }
        dismissalTracker.pruneActivities(activeKeys)
        val musicVisible = isMusicActive && track.hasMedia && !ForegroundAppTracker.shouldHideMedia.value &&
            !dismissalTracker.isMediaDismissed(mediaDismissToken(track))
        val flashlightVisible = isFlashlightActive && OverlayPreferences.showFlashlightIslandFlow.value &&
            !dismissalTracker.isFlashlightDismissed(FlashlightController.isFlashlightOn.value, FlashlightController.lastFlashlightOnTime)
        return builtInIslandSlots(
            music = musicVisible,
            flashlight = flashlightVisible,
            activity = primaryActivity?.takeUnless { dismissalTracker.isActivityDismissed(it.key) },
            shortTerms = shortTerms.filterNot { dismissalTracker.isActivityDismissed(it.key) },
            priorityOrder = OverlayPreferences.islandPriorityOrderFlow.value,
        )
    }

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
                LarpDotTheme(darkTheme = true) {
                    val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
                    val isMusicActiveNow by MediaPlaybackState.isMusicActive.collectAsState()
                    val hideMedia by ForegroundAppTracker.shouldHideMedia.collectAsState()
                    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
                    val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
                    val activeCutout = currentCutoutInfo ?: cutout
                    val primaryActivity by NotificationActivityState.current.collectAsState()
                    val shortTermActivities by NotificationActivityState.shortTermActivities.collectAsState()
                    @Suppress("UNUSED_VARIABLE")
                    val rev = dismissalRevision
                    val activeKeys = buildSet {
                        primaryActivity?.key?.let(::add)
                        shortTermActivities.forEach { add(it.key) }
                    }
                    dismissalTracker.pruneActivities(activeKeys)
                    val isMusicAllowed = !dismissalTracker.isMediaDismissed(mediaDismissToken(mediaTrack))
                    val isFlashlightVisible = isFlashlightOn && showFlashlightIsland &&
                        !dismissalTracker.isFlashlightDismissed(isFlashlightOn, FlashlightController.lastFlashlightOnTime)
                    val currentActivity = primaryActivity?.takeUnless { dismissalTracker.isActivityDismissed(it.key) }
                    val visibleShortTerms = shortTermActivities.filterNot { dismissalTracker.isActivityDismissed(it.key) }
                    val activeExpandedType by expandedType.collectAsState()
                    val priorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
                    val stack = builtInIslandSlots(
                        mediaTrack.hasMedia && isMusicActiveNow && !hideMedia && isMusicAllowed,
                        isFlashlightVisible, currentActivity, visibleShortTerms,
                        priorityOrder = priorityOrder,
                    )
                    val compactSlot = stack.primary?.content
                    val compactType = compactSlot?.type
                    val primaryStackActivity = ca.saboor.larpdot.ui.overlay.resolveIslandSlotActivity(
                        slot = compactSlot,
                        primaryActivity = currentActivity,
                        shortTerms = visibleShortTerms,
                    ) ?: currentActivity
                    val secondaryTypes = stack.secondary.map { it.content }
                    LaunchedEffect(compactType) {
                        if (compactType == null) compactSurfaceFrame = null
                    }
                    LaunchedEffect(secondaryTypes) {
                        secondarySurfaceFrames.keys.toList().forEach { slot ->
                            if (slot !in secondaryTypes) {
                                secondarySurfaceFrames.remove(slot)
                                secondarySurfaceScales.remove(slot)
                                secondarySurfaceStretches.remove(slot)
                                scheduleTouchLayout()
                            }
                        }
                    }

                    val morphing = isIslandExpanded || isOverlayMorphing
                    Box(Modifier.fillMaxSize()) {
                        CompactIslandOverlay(
                            cutoutInfo = activeCutout,
                            mediaInfo = mediaTrack,
                            isMusicAllowed = isMusicAllowed,
                            notificationActivity = currentActivity,
                            shortTermActivities = visibleShortTerms,
                            isFlashlightOn = isFlashlightVisible,
                            isExpanded = morphing,
                            hideCompactStack = isCompactSurfaceHidden && selectedSlot == null,
                            hiddenSecondarySlot = selectedSlot.takeIf { isCompactSurfaceHidden },
                            onExpand = ::expandOverlay,
                            onDismiss = ::dismissIsland,
                            onCompactFrameChanged = { frame ->
                                if (compactSurfaceFrame != frame) {
                                    val boundsChanged = compactSurfaceFrame?.bounds != frame.bounds
                                    compactSurfaceFrame = frame
                                    if (boundsChanged) scheduleTouchLayout()
                                }
                            },
                            onCompactScaleChanged = { scale ->
                                if (compactPressScale != scale) compactPressScale = scale
                            },
                            onCompactStretchChanged = { stretch ->
                                if (compactStretch != stretch) compactStretch = stretch
                            },
                            onSecondaryFrameChanged = { slot, frame ->
                                if (slot in secondaryTypes && secondarySurfaceFrames[slot] != frame) {
                                    val boundsChanged = secondarySurfaceFrames[slot]?.bounds != frame.bounds
                                    secondarySurfaceFrames[slot] = frame
                                    if (boundsChanged) scheduleTouchLayout()
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
                            onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                        )
                        if (morphing) expansionSourceFrame?.let { source ->
                            val slot = selectedSlot
                            val liveFrame = if (slot != null) {
                                secondarySurfaceFrames[slot].takeIf { slot in secondaryTypes }
                            } else {
                                compactSurfaceFrame.takeIf { compactType == activeExpandedType }
                            }
                            val activeAutoExpanded = autoExpandedActivityKey?.let { key ->
                                visibleShortTerms.firstOrNull { it.key == key }
                            }
                            IslandSurfaceOverlay(
                                cutoutInfo = activeCutout,
                                mediaInfo = mediaTrack,
                                notificationActivity = activeAutoExpanded ?: slot?.activity ?: primaryStackActivity ?: retainedExpandedActivity,
                                expandedType = activeExpandedType,
                                fromTinyDot = slot != null,
                                isExpanded = isIslandExpanded,
                                sourceFrame = source,
                                returnFrame = liveFrame,
                                onCollapse = ::collapseOverlay,
                                onFirstFrameDrawn = {
                                    if (isIslandExpanded) isCompactSurfaceHidden = true
                                },
                                onExitFinished = ::finishCollapse,
                                onExpandedBoundsChanged = { bounds ->
                                    if (expandedCardBounds != bounds) {
                                        expandedCardBounds = bounds
                                        scheduleTouchLayout()
                                    }
                                },
                                compactPressScale = expansionSourceScale,
                                compactStretch = expansionSourceStretch,
                            )
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
            OverlayPreferences.isDebugModeEnabled(context)
            OverlayPreferences.isShowFlashlightIslandEnabled(context)
            OverlayPreferences.isFlashlightTapToToggleEnabled(context)
            OverlayPreferences.getNotificationActivitySettings(context)
            OverlayPreferences.getIslandPriorityOrder(context)
            OverlayPreferences.initializeMusicSettings(context)
            seenShortTermTitles.clear()
            NotificationActivityState.shortTermActivities.value.forEach { seenShortTermTitles[it.key] = it.title }
            observeMediaState()
            observeFlashlightState()
            observeNotificationActivityState()
            observeShortTermActivities()
            observePriorityOrder()
            observeDebugMode()
            observeCutoutConfig()
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun expandOverlay(type: IslandType, slot: IslandSlot?, source: CompactIslandFrame) {
        expandOverlayInternal(
            type = type,
            slot = slot,
            source = source,
            autoExpandedKey = null,
            initialActivity = slot?.activity
                ?: currentStack().primary?.content?.activity
                ?: NotificationActivityState.current.value,
        )
    }

    private fun expandOverlayInternal(
        type: IslandType,
        slot: IslandSlot?,
        source: CompactIslandFrame,
        autoExpandedKey: String?,
        initialActivity: NotificationActivityInfo?,
    ) {
        if (!isOverlayAdded || isOverlayMorphing || isIslandExpanded) return

        MediaPlaybackState.dismissSongAnnouncement()
        isSongAnnouncementActive = false

        _expandedType.value = type
        selectedSlot = slot
        autoExpandedActivityKey = autoExpandedKey
        retainedExpandedActivity = initialActivity
        expansionSourceFrame = source
        expandedCardBounds = null
        expansionSourceScale = if (slot == null) compactPressScale else secondarySurfaceScales[slot] ?: 1f
        expansionSourceStretch = if (slot == null) compactStretch else secondarySurfaceStretches[slot] ?: 0f
        isIslandExpanded = true
        isOverlayMorphing = true

        syncDrawWindowVisibility()
        layoutTouchWindow()
    }

    private fun cutoutOriginFrame(cutout: CutoutInfo): CompactIslandFrame {
        val density = context.resources.displayMetrics.density
        val camera = islandCameraGeometry(cutout.radiusPx, density)
        val sizePx = camera.diameter * density
        val left = cutout.centerX - sizePx / 2f
        val top = cutout.centerY - sizePx / 2f
        return CompactIslandFrame(
            bounds = Rect(left, top, left + sizePx, top + sizePx),
        )
    }

    private fun autoExpandShortTermAlert(
        info: NotificationActivityInfo,
        settings: NotificationActivitySettings,
    ) {
        if (!isOverlayAdded) return
        val cutout = currentCutoutInfo ?: return
        if (isIslandExpanded && autoExpandedActivityKey == null) return

        autoCollapseJob?.cancel()
        autoCollapseJob = null
        autoExpandedActivityKey = info.key
        retainedExpandedActivity = info

        if (isIslandExpanded) {
            _expandedType.value = IslandType.NOTIFICATION_ACTIVITY
        } else if (!isOverlayMorphing) {
            expandOverlayInternal(
                type = IslandType.NOTIFICATION_ACTIVITY,
                slot = null,
                source = compactSurfaceFrame ?: cutoutOriginFrame(cutout),
                autoExpandedKey = info.key,
                initialActivity = info,
            )
        }

        if (isPermanentShortTermAlert(info, settings)) {
            autoCollapseJob = controllerScope.launch {
                delay(3_500L)
                if (isIslandExpanded && autoExpandedActivityKey == info.key) {
                    collapseOverlay()
                }
            }
        }
    }

    fun collapseOverlay() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        if (!isOverlayAdded || !isIslandExpanded) return

        isIslandExpanded = false
        isOverlayMorphing = true
        expandedCardBounds = null
        layoutTouchWindow()
    }

    private fun finishCollapse() {
        if (!isOverlayAdded || isIslandExpanded || !isOverlayMorphing) return
        isCompactSurfaceHidden = false
        selectedSlot = null
        autoExpandedActivityKey = null
        retainedExpandedActivity = null
        isOverlayMorphing = false
        expansionSourceFrame = null
        expandedCardBounds = null
        expansionSourceScale = 1f
        expansionSourceStretch = 0f
        if (currentStack().ordered.isNotEmpty()) {
            layoutTouchWindow()
        } else {
            removeTouchWindow()
        }
        syncDrawWindowVisibility()
    }

    /** Hide the 460dp translucent draw surface when completely idle so SurfaceFlinger skips compositing it. */
    private fun syncDrawWindowVisibility() {
        val view = compactView ?: return
        val active = currentStack().ordered.isNotEmpty() ||
            isIslandExpanded ||
            isOverlayMorphing ||
            OverlayPreferences.isDebugModeFlow.value
        if (active) {
            idleHideJob?.cancel()
            idleHideJob = null
            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
        } else if (view.visibility != View.GONE && idleHideJob?.isActive != true) {
            idleHideJob = controllerScope.launch {
                delay(420L)
                if (isOverlayAdded &&
                    currentStack().ordered.isEmpty() &&
                    !isIslandExpanded &&
                    !isOverlayMorphing &&
                    !OverlayPreferences.isDebugModeFlow.value
                ) {
                    compactView?.visibility = View.GONE
                }
            }
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

        // One shared touch-region path for both compact and expanded islands.
        // Expanded mode sizes touchView strictly to the expanded card and uses
        // FLAG_WATCH_OUTSIDE_TOUCH so taps outside the card both collapse it and pass through.
        val stack = currentStack()
        val secondaryBounds = stack.secondary.mapNotNull { secondarySurfaceFrames[it.content]?.bounds }
        val bounds = activeIslandTouchBounds(
            isExpanded = isIslandExpanded,
            expandedBounds = expandedCardBounds,
            compactBounds = if (stack.primary != null) compactSurfaceFrame?.bounds else null,
            secondaryBounds = secondaryBounds,
        )
        val region = islandTouchRegion(
            bounds, 6f * context.resources.displayMetrics.density,
        )
        if (region == null) {
            removeTouchWindow()
            return
        }
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val tParams = WindowManager.LayoutParams(
            region.width, region.height, windowType,
            if (isIslandExpanded) baseFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else baseFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = region.left + (compactWindowParams?.x ?: 0)
            y = region.top + (compactWindowParams?.y ?: 0)
            windowAnimations = 0
            applyCutoutMode(this)
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
        touchWindowParams = null
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

        val hasVisibleIsland = currentStack().ordered.isNotEmpty()
        val shouldShowTouch = (hasVisibleIsland || isIslandExpanded) && isOverlayAdded
        if (shouldShowTouch) {
            layoutTouchWindow()
        } else {
            removeTouchWindow()
        }
        syncDrawWindowVisibility()
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

    private fun observeShortTermActivities() {
        controllerScope.launch {
            NotificationActivityState.shortTermActivities.collectLatest { shortTerms ->
                val settings = OverlayPreferences.notificationActivitySettingsFlow.value
                val activeAutoKey = autoExpandedActivityKey
                if (activeAutoKey != null) {
                    val liveAuto = shortTerms.firstOrNull {
                        it.key == activeAutoKey && !dismissalTracker.isActivityDismissed(it.key)
                    }
                    if (liveAuto != null) {
                        retainedExpandedActivity = liveAuto
                    } else if (isIslandExpanded) {
                        collapseOverlay()
                    }
                }
                val activeSlot = selectedSlot
                if (isIslandExpanded && activeSlot?.activityKey != null) {
                    val stillPresent = currentStack().ordered.any { it.content == activeSlot }
                    if (!stillPresent) collapseOverlay()
                }
                val candidate = selectAutoExpandShortTermAlert(
                    shortTerms = shortTerms,
                    seenSignatures = seenShortTermTitles,
                    expandEnabled = settings.expandShortTermAlerts,
                    isDismissed = dismissalTracker::isActivityDismissed,
                )
                seenShortTermTitles.clear()
                shortTerms.forEach { seenShortTermTitles[it.key] = it.title }
                if (candidate != null) {
                    autoExpandShortTermAlert(candidate, settings)
                }
                updateOverlayLayout()
            }
        }
    }

    private fun observePriorityOrder() {
        controllerScope.launch {
            OverlayPreferences.islandPriorityOrderFlow.collectLatest {
                updateOverlayLayout()
            }
        }
    }

    private fun observeDebugMode() {
        controllerScope.launch {
            OverlayPreferences.isDebugModeFlow.collectLatest {
                syncDrawWindowVisibility()
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
        idleHideJob?.cancel()
        idleHideJob = null
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        seenShortTermTitles.clear()
        dismissalTracker.clear()
        islandLifecycle.clear()
        isSongAnnouncementActive = false
        isIslandExpanded = false
        isOverlayMorphing = false
        isCompactSurfaceHidden = false
        compactPressScale = 1f
        compactStretch = 0f
        compactSurfaceFrame = null
        secondarySurfaceFrames.clear()
        secondarySurfaceScales.clear()
        secondarySurfaceStretches.clear()
        expansionSourceFrame = null
        expandedCardBounds = null
        expansionSourceScale = 1f
        expansionSourceStretch = 0f
        selectedSlot = null
        autoExpandedActivityKey = null
        retainedExpandedActivity = null

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

internal fun selectAutoExpandShortTermAlert(
    shortTerms: List<NotificationActivityInfo>,
    seenSignatures: Map<String, String>,
    expandEnabled: Boolean,
    isDismissed: (String) -> Boolean,
): NotificationActivityInfo? {
    if (!expandEnabled) return null
    return shortTerms
        .filter { !isDismissed(it.key) }
        .filter { info ->
            val previousTitle = seenSignatures[info.key]
            previousTitle == null ||
                ((info.kind == NotificationActivityKind.SOUND_MODE || info.kind == NotificationActivityKind.AUDIO_OUTPUT) &&
                    previousTitle != info.title)
        }
        .maxByOrNull { it.postedAtMillis }
}

internal fun isPermanentShortTermAlert(
    info: NotificationActivityInfo,
    settings: NotificationActivitySettings,
): Boolean = when (info.kind) {
    NotificationActivityKind.BATTERY_CHARGING -> info.key == "system_battery_charging" && settings.permanentCharging
    NotificationActivityKind.BATTERY_LOW -> settings.permanentLowBattery
    NotificationActivityKind.SOUND_MODE -> settings.permanentSoundMode
    NotificationActivityKind.BLUETOOTH -> settings.permanentBluetooth &&
        info.text?.contains("Disconnected", ignoreCase = true) != true
    NotificationActivityKind.AUDIO_OUTPUT -> settings.permanentAudioOutput
    NotificationActivityKind.SCREENSHOT -> settings.permanentScreenshotPocket
    else -> false
}
