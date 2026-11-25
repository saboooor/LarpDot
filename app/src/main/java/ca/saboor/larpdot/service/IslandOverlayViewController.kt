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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import ca.saboor.larpdot.ui.overlay.cameraDotSwipeBounds
import ca.saboor.larpdot.ui.overlay.drawCameraCutoutOutline
import ca.saboor.larpdot.ui.overlay.interactivePullProgress
import ca.saboor.larpdot.ui.overlay.isCameraDotSwipeDown
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandTouchRegion
import ca.saboor.larpdot.ui.overlay.shouldCommitSwipeDownPull
import ca.saboor.larpdot.ui.overlay.IslandType
import ca.saboor.larpdot.ui.overlay.IslandSurfaceOverlay
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.ui.overlay.IslandSlot
import ca.saboor.larpdot.ui.overlay.builtInIslandSlots
import ca.saboor.larpdot.ui.overlay.IslandStack
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Allow cleanup if the draw window stops receiving frames.
private const val OVERLAY_EXIT_TIMEOUT_MS = 4_000L

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
    private var isHiding by mutableStateOf(false)
    private var compactExitFinished by mutableStateOf(false)
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
    private var expandedCustomIsland by mutableStateOf<CustomIsland?>(null)
    private var isSwipeDownCustomExpanded by mutableStateOf(false)
    private var isPreviewCustomExpanded by mutableStateOf(false)
    private var swipeDownPullProgress by mutableStateOf<Float?>(null)
    private val seenShortTermTitles = mutableMapOf<String, String>()
    private var autoCollapseJob: Job? = null
    private var expandedInactivityJob: Job? = null
    private val dismissalTracker = IslandDismissalTracker()
    private var dismissalRevision by mutableIntStateOf(0)
    private var idleHideJob: Job? = null

    private fun mediaDismissToken(track: MediaTrackInfo): String? =
        if (!track.hasMedia) null else "${track.playerPackageName.orEmpty()}|${track.title}|${track.artist}"

    private fun dismissIsland(type: IslandType, slot: IslandSlot?) {
        when (type) {
            IslandType.CUSTOM -> CustomIslandStore.setActive(null)
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

    private data class VisibleOverlaySnapshot(
        val isMusicAllowed: Boolean,
        val isFlashlightVisible: Boolean,
        val currentActivity: NotificationActivityInfo?,
        val visibleShortTerms: List<NotificationActivityInfo>,
        val stack: IslandStack<IslandSlot>,
    )

    private fun resolveVisibleSnapshot(
        track: MediaTrackInfo,
        musicEnabled: Boolean,
        hideMedia: Boolean,
        flashlightOn: Boolean,
        showFlashlight: Boolean,
        primaryActivity: NotificationActivityInfo?,
        shortTerms: List<NotificationActivityInfo>,
        priorityOrder: List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem>,
        customIsland: CustomIsland?,
    ): VisibleOverlaySnapshot {
        val activeKeys = buildSet {
            primaryActivity?.key?.let(::add)
            shortTerms.forEach { add(it.key) }
        }
        dismissalTracker.pruneActivities(activeKeys)
        val isMusicAllowed = !dismissalTracker.isMediaDismissed(mediaDismissToken(track))
        val isFlashlightVisible = flashlightOn && showFlashlight &&
            !dismissalTracker.isFlashlightDismissed(flashlightOn, FlashlightController.lastFlashlightOnTime)
        val currentActivity = primaryActivity?.takeUnless { dismissalTracker.isActivityDismissed(it.key) }
        val visibleShortTerms = shortTerms.filterNot { dismissalTracker.isActivityDismissed(it.key) }
        val stack = builtInIslandSlots(
            music = musicEnabled && track.hasMedia && !hideMedia && isMusicAllowed,
            flashlight = isFlashlightVisible,
            activity = currentActivity,
            shortTerms = visibleShortTerms,
            priorityOrder = priorityOrder,
            custom = customIsland,
        )
        return VisibleOverlaySnapshot(
            isMusicAllowed = isMusicAllowed,
            isFlashlightVisible = isFlashlightVisible,
            currentActivity = currentActivity,
            visibleShortTerms = visibleShortTerms,
            stack = stack,
        )
    }

    private fun currentStack(): IslandStack<IslandSlot> = resolveVisibleSnapshot(
        track = MediaPlaybackState.currentTrack.value,
        musicEnabled = isMusicActive,
        hideMedia = ForegroundAppTracker.shouldHideMedia.value,
        flashlightOn = isFlashlightActive && FlashlightController.isFlashlightOn.value,
        showFlashlight = OverlayPreferences.showFlashlightIslandFlow.value,
        primaryActivity = NotificationActivityState.current.value,
        shortTerms = NotificationActivityState.shortTermActivities.value,
        priorityOrder = OverlayPreferences.islandPriorityOrderFlow.value,
        customIsland = CustomIslandStore.settings.value.activeIsland,
    ).stack

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
        if (isOverlayAdded) {
            if (isHiding) {
                isHiding = false
                updateOverlayLayout()
            }
            return
        }

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
                OverlayRootContent(initialCutout = cutout)
            }
        }
        compOwner.attach(compView)
        compOwner.onCreate()
        compactView = compView

        // Setup touch forwarding view
        var trackingSwipeDown = false
        var forwardedDownToCompact = false
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var interactivePullActive = false
        var pullRangePx = 140f
        var velocityTracker: VelocityTracker? = null
        fun resetVelocityTracker() {
            velocityTracker?.recycle()
            velocityTracker = null
        }
        fun addScreenSpaceVelocitySample(event: MotionEvent) {
            val tracker = velocityTracker ?: return
            val screenCopy = MotionEvent.obtain(event)
            screenCopy.setLocation(event.rawX, event.rawY)
            tracker.addMovement(screenCopy)
            screenCopy.recycle()
        }
        fun forwardToCompact(event: MotionEvent): Boolean {
            val comp = compactView ?: return false
            val cParams = compactWindowParams ?: return false
            val tParams = touchWindowParams ?: return false
            val copy = MotionEvent.obtain(event)
            copy.offsetLocation((tParams.x - cParams.x).toFloat(), (tParams.y - cParams.y).toFloat())
            val handled = comp.dispatchTouchEvent(copy)
            copy.recycle()
            return handled
        }
        val tView = View(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) = resetVelocityTracker()
            })
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (isIslandExpanded) collapseOverlay()
                    return@setOnTouchListener false
                }
                if (isIslandExpanded && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP)) {
                    scheduleExpandedAutoCollapse()
                }
                val density = context.resources.displayMetrics.density
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        interactivePullActive = false
                        forwardedDownToCompact = false
                        resetVelocityTracker()
                        val canSwipeDown = !isIslandExpanded &&
                            swipeDownPullProgress == null &&
                            (!isOverlayMorphing || isSwipeDownCustomExpanded)
                        val isIdle = currentStack().ordered.isEmpty() && canSwipeDown
                        val swipeZone = if (canSwipeDown) {
                            currentSwipeDownZone(padded = true)
                        } else null
                        val screenX = event.rawX
                        val screenY = event.rawY
                        trackingSwipeDown = swipeZone != null && (
                            isIdle || (
                                screenX >= swipeZone.left && screenX <= swipeZone.right &&
                                    screenY >= swipeZone.top && screenY <= swipeZone.bottom
                                )
                            )
                        if (trackingSwipeDown) {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            velocityTracker = VelocityTracker.obtain()
                            addScreenSpaceVelocitySample(event)
                            val swipeHeightDp = (CustomIslandStore.settings.value.swipeDownIsland?.main?.height ?: 150).toFloat()
                            val cutoutDiameterDp = currentCutoutInfo?.let { islandCameraGeometry(it.radiusPx, density).diameter } ?: 24f
                            pullRangePx = ((swipeHeightDp - cutoutDiameterDp) * density).coerceIn(90f * density, 175f * density)
                        }
                        if (isIdle) {
                            return@setOnTouchListener trackingSwipeDown
                        }
                        val handled = forwardToCompact(event)
                        forwardedDownToCompact = handled
                        return@setOnTouchListener handled || trackingSwipeDown
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (trackingSwipeDown || interactivePullActive) {
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            addScreenSpaceVelocitySample(event)
                        }
                        val dx = lastRawX - downRawX
                        val dy = lastRawY - downRawY
                        if (interactivePullActive) {
                            swipeDownPullProgress = interactivePullProgress(dy, pullRangePx)
                            return@setOnTouchListener true
                        }
                        if (trackingSwipeDown && isCameraDotSwipeDown(dx = dx, dy = dy, density = density)) {
                            if (forwardedDownToCompact) {
                                forwardedDownToCompact = false
                                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                                forwardToCompact(cancel)
                                cancel.recycle()
                            }
                            val initialProgress = interactivePullProgress(dy, pullRangePx)
                            if (beginInteractiveSwipeDownCustomIsland(initialProgress)) {
                                interactivePullActive = true
                                trackingSwipeDown = false
                                view.performIslandHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                return@setOnTouchListener true
                            }
                        }
                        return@setOnTouchListener if (forwardedDownToCompact) forwardToCompact(event) else trackingSwipeDown
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasPulling = interactivePullActive
                        val wasTracking = trackingSwipeDown
                        val wasForwarded = forwardedDownToCompact
                        val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                        if (!cancelled && (wasPulling || wasTracking)) {
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            addScreenSpaceVelocitySample(event)
                        }
                        if (wasPulling || wasTracking) {
                            velocityTracker?.computeCurrentVelocity(1000)
                        }
                        val finalVelocity = velocityTracker?.yVelocity ?: 0f
                        resetVelocityTracker()
                        interactivePullActive = false
                        trackingSwipeDown = false
                        forwardedDownToCompact = false
                        val dx = lastRawX - downRawX
                        val dy = lastRawY - downRawY
                        if (wasPulling) {
                            val finalProgress = interactivePullProgress(dy, pullRangePx)
                            val commit = shouldCommitSwipeDownPull(finalProgress, finalVelocity, density, cancelled)
                            if (commit) view.performIslandHaptic(HapticFeedbackConstants.LONG_PRESS)
                            finishInteractiveSwipeDownCustomIsland(commit)
                            return@setOnTouchListener true
                        }
                        if (wasTracking && (isCameraDotSwipeDown(dx, dy, density) ||
                                (dy > 4f * density && finalVelocity > 180f * density))
                        ) {
                            if (wasForwarded) {
                                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                                forwardToCompact(cancel)
                                cancel.recycle()
                            }
                            view.performIslandHaptic(HapticFeedbackConstants.LONG_PRESS)
                            expandSwipeDownCustomIsland()
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener if (wasForwarded) forwardToCompact(event) else wasTracking
                    }
                }
                forwardToCompact(event)
            }
        }
        touchView = tView

        val compParams = createExpandedLayoutParams()
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
            observeCustomIsland()
            observeCutoutConfig()
            observeScreenAndAutoCollapse()
            updateOverlayLayout()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    @androidx.compose.runtime.Composable
    private fun OverlayRootContent(initialCutout: CutoutInfo) {
        LarpDotTheme(darkTheme = true) {
            val mediaTrack by MediaPlaybackState.currentTrack.collectAsState()
            val isMusicActiveNow by MediaPlaybackState.isMusicActive.collectAsState()
            val hideMedia by ForegroundAppTracker.shouldHideMedia.collectAsState()
            val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
            val showFlashlightIsland by OverlayPreferences.showFlashlightIslandFlow.collectAsState()
            val debugModeEnabled by OverlayPreferences.isDebugModeFlow.collectAsState()
            val activeCutout = currentCutoutInfo ?: initialCutout
            val primaryActivity by NotificationActivityState.current.collectAsState()
            val shortTermActivities by NotificationActivityState.shortTermActivities.collectAsState()
            val priorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
            val customIslandSettings by CustomIslandStore.settings.collectAsState()
            val customIsland = customIslandSettings.activeIsland
            val activeExpandedType by expandedType.collectAsState()
            @Suppress("UNUSED_VARIABLE")
            val rev = dismissalRevision

            val snapshot = resolveVisibleSnapshot(
                track = mediaTrack,
                musicEnabled = isMusicActiveNow,
                hideMedia = hideMedia,
                flashlightOn = isFlashlightOn,
                showFlashlight = showFlashlightIsland,
                primaryActivity = primaryActivity,
                shortTerms = shortTermActivities,
                priorityOrder = priorityOrder,
                customIsland = customIsland,
            )
            val compactSlot = snapshot.stack.primary?.content
            val compactType = compactSlot?.type
            val primaryStackActivity = ca.saboor.larpdot.ui.overlay.resolveIslandSlotActivity(
                slot = compactSlot,
                primaryActivity = snapshot.currentActivity,
                shortTerms = snapshot.visibleShortTerms,
            ) ?: snapshot.currentActivity
            val secondaryTypes = snapshot.stack.secondary.map { it.content }

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

            val morphing = isIslandExpanded || isOverlayMorphing || swipeDownPullProgress != null
            Box(Modifier.fillMaxSize()) {
                CompactIslandOverlay(
                    cutoutInfo = activeCutout,
                    mediaInfo = mediaTrack,
                    isMusicAllowed = snapshot.isMusicAllowed,
                    notificationActivity = snapshot.currentActivity,
                    shortTermActivities = snapshot.visibleShortTerms,
                    customIsland = customIsland,
                    hasSwipeDownCustomIsland = customIslandSettings.swipeDownIsland != null,
                    isFlashlightOn = snapshot.isFlashlightVisible,
                    isExpanded = morphing,
                    isExiting = isHiding && !morphing,
                    hideCompactPill = isCompactSurfaceHidden && selectedSlot == null,
                    hiddenSecondarySlot = selectedSlot.takeIf { isCompactSurfaceHidden },
                    onExpand = ::expandOverlay,
                    onSwipeDownCustomExpand = ::expandSwipeDownCustomIsland,
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
                    onExitStateChanged = { compactExitFinished = it },
                )
                // Draw the card over the stationary compact stack.
                if (morphing) expansionSourceFrame?.let { source ->
                    val slot = selectedSlot
                    val liveFrame = if (isSwipeDownCustomExpanded) {
                        compactSurfaceFrame
                    } else if (slot != null) {
                        secondarySurfaceFrames[slot].takeIf { slot in secondaryTypes }
                    } else {
                        compactSurfaceFrame.takeIf { compactType == activeExpandedType }
                    }
                    val activeAutoExpanded = autoExpandedActivityKey?.let { key ->
                        snapshot.visibleShortTerms.firstOrNull { it.key == key }
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
                            if (isIslandExpanded || swipeDownPullProgress != null) isCompactSurfaceHidden = true
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
                        customIsland = expandedCustomIsland,
                        interactiveProgress = swipeDownPullProgress,
                    )
                }
                if (debugModeEnabled) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCameraCutoutOutline(
                            center = Offset(activeCutout.centerX, activeCutout.centerY),
                            radius = activeCutout.radiusPx,
                            outside = true,
                        )
                    }
                }
            }
        }
    }

    private fun beginInteractiveSwipeDownCustomIsland(initialProgress: Float): Boolean {
        if (!isOverlayAdded || isHiding || isIslandExpanded) return false
        if (isOverlayMorphing && !isSwipeDownCustomExpanded) return false
        val swipeDown = CustomIslandStore.settings.value.swipeDownIsland ?: return false
        val cutout = currentCutoutInfo ?: return false
        if (swipeDown.main.widgetId == null) return false

        MediaPlaybackState.dismissSongAnnouncement()
        isSongAnnouncementActive = false

        _expandedType.value = IslandType.CUSTOM
        selectedSlot = null
        autoExpandedActivityKey = null
        retainedExpandedActivity = null
        expandedCustomIsland = swipeDown
        isSwipeDownCustomExpanded = true
        expansionSourceFrame = compactSurfaceFrame ?: cutoutOriginFrame(cutout)
        expandedCardBounds = null
        expansionSourceScale = compactPressScale
        expansionSourceStretch = compactStretch
        isIslandExpanded = false
        isOverlayMorphing = true
        swipeDownPullProgress = initialProgress

        syncDrawWindowVisibility()
        return true
    }

    private fun finishInteractiveSwipeDownCustomIsland(commit: Boolean) {
        if (swipeDownPullProgress == null) return
        swipeDownPullProgress = null
        if (!isOverlayAdded || isHiding) {
            finishCollapse()
            return
        }
        if (commit && CustomIslandStore.settings.value.swipeDownIsland != null) {
            isIslandExpanded = true
            isOverlayMorphing = true
            DotAccessibilityService.dismissNotificationShade()
            scheduleExpandedAutoCollapse()
            layoutTouchWindow()
        } else {
            isIslandExpanded = false
            isOverlayMorphing = true
            expandedCardBounds = null
            layoutTouchWindow()
        }
        syncDrawWindowVisibility()
    }

    private fun expandSwipeDownCustomIsland() {
        val swipeDown = CustomIslandStore.settings.value.swipeDownIsland ?: return
        val cutout = currentCutoutInfo ?: return
        DotAccessibilityService.dismissNotificationShade()
        expandOverlayInternal(
            type = IslandType.CUSTOM,
            slot = null,
            source = compactSurfaceFrame ?: cutoutOriginFrame(cutout),
            autoExpandedKey = null,
            initialActivity = null,
            customOverride = swipeDown,
            swipeDownCustom = true,
        )
    }

    private fun expandPreviewCustomIsland(island: CustomIsland) {
        if (!isOverlayAdded || isHiding) return
        val cutout = currentCutoutInfo ?: return
        if (island.main.widgetId == null) return
        if (isIslandExpanded && _expandedType.value == IslandType.CUSTOM) {
            expandedCustomIsland = island
            isPreviewCustomExpanded = true
            isSwipeDownCustomExpanded = false
            layoutTouchWindow()
            return
        }
        expandOverlayInternal(
            type = IslandType.CUSTOM,
            slot = null,
            source = compactSurfaceFrame ?: cutoutOriginFrame(cutout),
            autoExpandedKey = null,
            initialActivity = null,
            customOverride = island,
            swipeDownCustom = false,
            previewCustom = true,
        )
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
        customOverride: CustomIsland? = null,
        swipeDownCustom: Boolean = false,
        previewCustom: Boolean = false,
    ) {
        if (!isOverlayAdded || isHiding || isOverlayMorphing || isIslandExpanded) return
        if (type == IslandType.CUSTOM) {
            val custom = customOverride ?: CustomIslandStore.settings.value.activeIsland ?: return
            if (custom.main.widgetId == null) return
            expandedCustomIsland = custom
            isSwipeDownCustomExpanded = swipeDownCustom
            isPreviewCustomExpanded = previewCustom
        } else {
            isSwipeDownCustomExpanded = false
            isPreviewCustomExpanded = false
        }

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

        if (autoExpandedKey == null) {
            scheduleExpandedAutoCollapse()
        }
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
        if (!isOverlayAdded || isHiding) return
        val cutout = currentCutoutInfo ?: return
        if (isIslandExpanded && autoExpandedActivityKey == null) return

        autoCollapseJob?.cancel()
        autoCollapseJob = null
        cancelExpandedAutoCollapse()
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

    private fun scheduleExpandedAutoCollapse() {
        expandedInactivityJob?.cancel()
        expandedInactivityJob = null
        if (!isOverlayAdded || !isIslandExpanded || autoExpandedActivityKey != null) return
        val delayMs = resolveExpandedAutoCollapseDelayMs(OverlayPreferences.expandedAutoCollapseSecondsFlow.value) ?: return
        expandedInactivityJob = controllerScope.launch {
            delay(delayMs)
            if (isOverlayAdded && isIslandExpanded && autoExpandedActivityKey == null) {
                collapseOverlay()
            }
        }
    }

    private fun cancelExpandedAutoCollapse() {
        expandedInactivityJob?.cancel()
        expandedInactivityJob = null
    }

    fun collapseOverlay() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        cancelExpandedAutoCollapse()
        if (!isOverlayAdded || !isIslandExpanded) return

        isIslandExpanded = false
        isOverlayMorphing = true
        expandedCardBounds = null
        layoutTouchWindow()
    }

    private fun finishCollapse() {
        if (!isOverlayAdded || isIslandExpanded || !isOverlayMorphing) return
        cancelExpandedAutoCollapse()
        isCompactSurfaceHidden = false
        selectedSlot = null
        autoExpandedActivityKey = null
        retainedExpandedActivity = null
        expandedCustomIsland = null
        isSwipeDownCustomExpanded = false
        isPreviewCustomExpanded = false
        swipeDownPullProgress = null
        isOverlayMorphing = false
        expansionSourceFrame = null
        expandedCardBounds = null
        expansionSourceScale = 1f
        expansionSourceStretch = 0f
        if (currentStack().ordered.isNotEmpty() || CustomIslandStore.settings.value.swipeDownIsland != null) {
            layoutTouchWindow()
        } else {
            removeTouchWindow()
        }
        syncDrawWindowVisibility()
    }

    /** Hide the translucent draw surface when completely idle so SurfaceFlinger skips compositing it. */
    private fun syncDrawWindowVisibility() {
        val view = compactView ?: return
        val active = isHiding || currentStack().ordered.isNotEmpty() ||
            isIslandExpanded ||
            isOverlayMorphing ||
            OverlayPreferences.isDebugModeFlow.value
        if (active) {
            idleHideJob?.cancel()
            idleHideJob = null
            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
        } else if (view.visibility != View.GONE && idleHideJob?.isActive != true) {
            idleHideJob = controllerScope.launch {
                awaitCompactExit()
                if (isOverlayAdded &&
                    !isHiding &&
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

    private suspend fun awaitCompactExit() {
        withTimeoutOrNull(OVERLAY_EXIT_TIMEOUT_MS) {
            snapshotFlow { compactExitFinished }.first { it }
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

    private fun currentSwipeDownZone(padded: Boolean = false): Rect? {
        if (CustomIslandStore.settings.value.swipeDownIsland == null) return null
        val cutout = currentCutoutInfo ?: return null
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = (compactWindowParams?.width ?: dm.widthPixels).toFloat()
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val base = cameraDotSwipeBounds(
            cameraCenterX = cutout.centerX,
            cameraCenterY = cutout.centerY,
            cameraRadiusPx = cutout.radiusPx,
            screenWidthPx = screenWidth,
            density = density,
            isLandscape = isLandscape,
        )
        val compactBounds = if (currentStack().primary != null) compactSurfaceFrame?.bounds else null
        val combined = if (compactBounds != null) {
            Rect(
                left = minOf(base.left, compactBounds.left),
                top = minOf(base.top, compactBounds.top),
                right = maxOf(base.right, compactBounds.right),
                bottom = maxOf(base.bottom, compactBounds.bottom),
            )
        } else base
        if (!padded) return combined
        val pad = 6f * density
        return Rect(
            left = (combined.left - pad).coerceAtLeast(0f),
            top = (combined.top - pad).coerceAtLeast(0f),
            right = (combined.right + pad).coerceAtMost(screenWidth),
            bottom = combined.bottom + pad,
        )
    }

    private fun layoutTouchWindow() {
        if (isHiding) {
            removeTouchWindow()
            return
        }
        // Keep the touch window stationary during an active pull so InputDispatcher never cancels the gesture.
        if (swipeDownPullProgress != null && touchAdded) return
        val wm = windowManager ?: return
        val view = touchView ?: return

        // One shared touch-region path for both compact and expanded islands.
        // Expanded mode sizes touchView strictly to the expanded card and uses
        // FLAG_WATCH_OUTSIDE_TOUCH so taps outside the card both collapse it and pass through.
        val stack = currentStack()
        val secondaryBounds = stack.secondary.mapNotNull { secondarySurfaceFrames[it.content]?.bounds }
        val swipeDownBounds = if (!isIslandExpanded) currentSwipeDownZone(padded = false) else null
        val bounds = activeIslandTouchBounds(
            isExpanded = isIslandExpanded,
            expandedBounds = expandedCardBounds,
            compactBounds = if (stack.primary != null) compactSurfaceFrame?.bounds else null,
            secondaryBounds = secondaryBounds,
            swipeDownBounds = swipeDownBounds,
        )
        val region = islandTouchRegion(
            bounds, 6f * context.resources.displayMetrics.density,
        )
        if (region == null) {
            removeTouchWindow()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, region.width, region.height))
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
        val geometryChanged = tParams.geometryDiffersFrom(touchWindowParams)
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

    private fun createExpandedLayoutParams(): WindowManager.LayoutParams {
        val dm = context.resources.displayMetrics
        val density = dm.density
        val targetWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.currentWindowMetrics?.bounds?.width() ?: dm.widthPixels
        } else {
            dm.widthPixels
        }
        val targetHeight = (460f * density).toInt()

        // Single stable draw surface; input is handled by the lightweight forwarding window.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

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

    fun updateOverlayLayout() {
        val wm = windowManager ?: return
        if (currentCutoutInfo == null) return

        compactView?.let { compView ->
            val compParams = createExpandedLayoutParams()
            val changed = compParams.geometryDiffersFrom(compactWindowParams)
            compactWindowParams = compParams
            if (changed) {
                try {
                    wm.updateViewLayout(compView, compParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val stack = currentStack()
        if ((isIslandExpanded || swipeDownPullProgress != null) && _expandedType.value == IslandType.CUSTOM) {
            val sourceStillVisible = when {
                isPreviewCustomExpanded -> {
                    val previewId = expandedCustomIsland?.id
                    previewId != null && CustomIslandStore.settings.value.islands.any {
                        it.id == previewId && it.main.widgetId != null
                    }
                }
                isSwipeDownCustomExpanded -> CustomIslandStore.settings.value.swipeDownIsland != null
                else -> {
                    val slot = selectedSlot
                    if (slot == null) {
                        stack.primary?.content?.type == IslandType.CUSTOM
                    } else stack.secondary.any { it.content == slot }
                }
            }
            if (!sourceStillVisible) {
                swipeDownPullProgress = null
                collapseOverlay()
            }
        }
        val hasVisibleIsland = stack.ordered.isNotEmpty()
        val hasSwipeDownTarget = CustomIslandStore.settings.value.swipeDownIsland != null && currentCutoutInfo != null
        val shouldShowTouch = (hasVisibleIsland || hasSwipeDownTarget || isIslandExpanded) && isOverlayAdded
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

    private fun observeCustomIsland() {
        controllerScope.launch {
            CustomIslandStore.settings.collectLatest { settings ->
                if (_expandedType.value == IslandType.CUSTOM) {
                    if (isPreviewCustomExpanded) {
                        val previewId = expandedCustomIsland?.id
                        val updated = previewId?.let { id -> settings.islands.firstOrNull { it.id == id } }
                        if (updated == null || updated.main.widgetId == null) {
                            collapseOverlay()
                        } else if (updated != expandedCustomIsland) {
                            expandedCustomIsland = updated
                        }
                    } else {
                        val expected = if (isSwipeDownCustomExpanded) settings.swipeDownIsland else settings.activeIsland
                        if (expandedCustomIsland != expected) collapseOverlay()
                    }
                }
                updateOverlayLayout()
            }
        }
        controllerScope.launch {
            CustomIslandStore.previewRequests.collect { island ->
                expandPreviewCustomIsland(island)
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

    private fun observeScreenAndAutoCollapse() {
        controllerScope.launch {
            ScreenStateTracker.isScreenOn.collectLatest { screenOn ->
                if (!screenOn && (isIslandExpanded || swipeDownPullProgress != null)) {
                    swipeDownPullProgress = null
                    collapseOverlay()
                }
            }
        }
        controllerScope.launch {
            OverlayPreferences.expandedAutoCollapseSecondsFlow.collectLatest {
                if (isIslandExpanded && autoExpandedActivityKey == null) {
                    scheduleExpandedAutoCollapse()
                }
            }
        }
    }

    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.fitInsetsTypes = 0
            params.fitInsetsSides = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        currentCutoutInfo = CutoutDetector.detect(context)
        updateOverlayLayout()
    }

    suspend fun hide() {
        if (!isOverlayAdded) return
        if (compactView?.visibility == View.VISIBLE && ScreenStateTracker.isScreenOn.value) {
            isHiding = true
            removeTouchWindow()
            idleHideJob?.cancel()
            idleHideJob = null
            if (isOverlayMorphing || isIslandExpanded || swipeDownPullProgress != null) {
                swipeDownPullProgress = null
                collapseOverlay()
                // The screen can turn off before Compose gets another frame.
                withTimeoutOrNull(OVERLAY_EXIT_TIMEOUT_MS) {
                    snapshotFlow { isOverlayMorphing }.first { !it }
                } ?: finishCollapse()
            }
            awaitCompactExit()
        }
        dismiss()
    }

    fun destroy() {
        dismiss()
    }

    private fun dismiss() {
        if (!isOverlayAdded) return
        isOverlayAdded = false
        isHiding = false
        compactExitFinished = false
        rotationDisplayManager?.unregisterDisplayListener(displayListener)
        rotationDisplayManager = null
        lastDisplayRotation = null
        controllerJob.cancel()
        idleHideJob?.cancel()
        idleHideJob = null
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        cancelExpandedAutoCollapse()
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
        expandedCustomIsland = null
        isSwipeDownCustomExpanded = false
        isPreviewCustomExpanded = false
        swipeDownPullProgress = null

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

private fun View.performIslandHaptic(feedbackConstant: Int) {
    if (OverlayPreferences.hapticsEnabledFlow.value) {
        performHapticFeedback(feedbackConstant)
    }
}

internal fun resolveExpandedAutoCollapseDelayMs(timeoutSeconds: Int): Long? =
    if (timeoutSeconds <= 0) null else timeoutSeconds.toLong() * 1000L

private fun WindowManager.LayoutParams.geometryDiffersFrom(other: WindowManager.LayoutParams?): Boolean =
    other == null ||
        width != other.width ||
        height != other.height ||
        x != other.x ||
        y != other.y ||
        flags != other.flags ||
        gravity != other.gravity

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
                ((info.kind == NotificationActivityKind.SOUND_MODE ||
                    info.kind == NotificationActivityKind.AUDIO_OUTPUT ||
                    info.kind == NotificationActivityKind.DND) &&
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
    NotificationActivityKind.DND -> settings.permanentDnd &&
        !info.title.contains("Off", ignoreCase = true)
    NotificationActivityKind.BLUETOOTH -> settings.permanentBluetooth &&
        info.text?.contains("Disconnected", ignoreCase = true) != true
    NotificationActivityKind.AUDIO_OUTPUT -> settings.permanentAudioOutput
    NotificationActivityKind.SCREENSHOT -> settings.permanentScreenshotPocket
    else -> false
}
