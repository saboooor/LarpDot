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
import android.view.WindowInsets
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
            liveTopSystemGestureInsetPx = 0f
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
    private var autoExpandedHasOwnTimer = false
    private val pendingAutoExpandQueue = AutoExpandAlertQueue()
    private var pendingAutoExpandDrainJob: Job? = null
    private var retainedExpandedActivity by mutableStateOf<NotificationActivityInfo?>(null)
    private var expandedCustomIsland by mutableStateOf<CustomIsland?>(null)
    private var isSwipeDownCustomExpanded by mutableStateOf(false)
    private var swipeDownPullProgress by mutableStateOf<Float?>(null)
    private var activeTouchGesture = false
    private var pendingTouchLayoutAfterGesture = false
    private var liveTopSystemGestureInsetPx = 0f
    private val seenActivitySignatures = mutableMapOf<String, String>()
    private var autoCollapseJob: Job? = null
    private var expandedInactivityJob: Job? = null
    private val dismissalTracker = IslandDismissalTracker()
    private var dismissalRevision by mutableIntStateOf(0)
    private var idleHideJob: Job? = null

    private fun mediaDismissToken(track: MediaTrackInfo): String? =
        if (!track.hasMedia) null else "${track.playerPackageName.orEmpty()}|${track.title}|${track.artist}"

    private fun dismissIsland(type: IslandType, slot: IslandSlot?) {
        when (type) {
            IslandType.CUSTOM -> {
                val targetId = slot?.customIslandId ?: currentStack().primary?.content?.customIslandId
                if (targetId != null) CustomIslandStore.setIslandActive(targetId, false)
                else CustomIslandStore.setActive(null)
            }
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
        val visibleCustomIslands: List<CustomIsland>,
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
        customIslands: List<CustomIsland>,
        eligibleActivities: List<NotificationActivityInfo> = NotificationActivityState.eligibleActivities.value,
        customNotifications: Collection<CustomNotification> = CustomIslandNotifications.active.value.values,
        deviceLocked: Boolean = ScreenStateTracker.isDeviceLocked.value,
        foregroundPackage: String? = ForegroundAppTracker.foregroundPackage.value,
        hideInRelatedApp: Boolean = OverlayPreferences.hideInRelatedAppFlow.value,
    ): VisibleOverlaySnapshot {
        val activeKeys = buildSet {
            primaryActivity?.key?.let(::add)
            shortTerms.forEach { add(it.key) }
            eligibleActivities.forEach { add(it.key) }
        }
        dismissalTracker.pruneActivities(activeKeys)
        val isMusicAllowed = !dismissalTracker.isMediaDismissed(mediaDismissToken(track))
        val isFlashlightVisible = flashlightOn && showFlashlight &&
            !dismissalTracker.isFlashlightDismissed(flashlightOn, FlashlightController.lastFlashlightOnTime)
        val filteredEligible = filterActivitiesForForeground(eligibleActivities, hideInRelatedApp, foregroundPackage)
        val filteredPrimary = selectBestVisibleActivity(
            primaryActivity = primaryActivity,
            eligibleActivities = filteredEligible,
            hideInRelatedApp = hideInRelatedApp,
            foregroundPackage = foregroundPackage,
        )
        val filteredShortTerms = filterActivitiesForForeground(shortTerms, hideInRelatedApp, foregroundPackage)
        val currentActivity = filteredPrimary?.takeUnless { dismissalTracker.isActivityDismissed(it.key) }
        val visibleShortTerms = filteredShortTerms.filterNot { dismissalTracker.isActivityDismissed(it.key) }
        val visibleCustomIslands = filterCustomIslandsForForeground(
            customIslands = customIslands,
            hideInRelatedApp = hideInRelatedApp,
            foregroundPackage = foregroundPackage,
            activeMediaPackage = track.playerPackageName,
            eligibleActivities = filteredEligible,
            activeNotifications = customNotifications,
            resolveWidgetPackage = { CustomIslandWidgets.info(it)?.provider?.packageName },
        )
        val matchingNotificationIds = matchingCustomNotificationIds(
            customIslands = visibleCustomIslands,
            notifications = customNotifications,
            deviceLocked = deviceLocked,
        )
        val stack = builtInIslandSlots(
            music = musicEnabled && track.hasMedia && !hideMedia && isMusicAllowed,
            flashlight = isFlashlightVisible,
            activity = currentActivity,
            shortTerms = visibleShortTerms,
            priorityOrder = priorityOrder,
            eligibleActivities = filteredEligible,
            customIslands = visibleCustomIslands,
            matchingNotificationIds = matchingNotificationIds,
        )
        return VisibleOverlaySnapshot(
            isMusicAllowed = isMusicAllowed,
            isFlashlightVisible = isFlashlightVisible,
            currentActivity = currentActivity,
            visibleShortTerms = visibleShortTerms,
            visibleCustomIslands = visibleCustomIslands,
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
        customIslands = CustomIslandStore.settings.value.activeIslands,
    ).stack

    private val _expandedType = MutableStateFlow(IslandType.MEDIA)
    val expandedType: StateFlow<IslandType> = _expandedType.asStateFlow()

    private var controllerJob = kotlinx.coroutines.SupervisorJob()
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
        controllerJob = kotlinx.coroutines.SupervisorJob()
        controllerScope = CoroutineScope(Dispatchers.Main + controllerJob)

        FlashlightController.init(context)

        isMusicActive = MediaPlaybackState.isMusicActive.value && !ForegroundAppTracker.shouldHideMedia.value
        isFlashlightActive = FlashlightController.isFlashlightOn.value && OverlayPreferences.isShowFlashlightIslandEnabled(context)
        isSongAnnouncementActive = MediaPlaybackState.isSongAnnouncementActive.value && OverlayPreferences.isShowSongAnnouncementEnabled(context)
        isNotificationActivityActive = selectBestVisibleActivity(
            primaryActivity = NotificationActivityState.current.value,
            eligibleActivities = NotificationActivityState.eligibleActivities.value,
            hideInRelatedApp = OverlayPreferences.hideInRelatedAppFlow.value,
            foregroundPackage = ForegroundAppTracker.foregroundPackage.value,
        ) != null

        val cutout = CutoutDetector.detect(context)
        currentCutoutInfo = cutout

        // 1. Setup Compact View & Lifecycle (Draw Surface)
        val compOwner = OverlayLifecycleOwner()
        compactLifecycleOwner = compOwner

        val compView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Live hardware rounded corners and top system-gesture inset from WindowInsets
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnApplyWindowInsetsListener { _, insets ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val tl = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                        val tr = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
                        val r = tl?.radius ?: tr?.radius ?: 0
                        if (r > 0 && currentCutoutInfo?.displayCornerRadiusPx != r.toFloat()) {
                            currentCutoutInfo = currentCutoutInfo?.copy(displayCornerRadiusPx = r.toFloat())
                        }
                    }
                    val topInset = extractTopSystemGestureInsetPx(insets)
                    if (topInset > 0f && topInset != liveTopSystemGestureInsetPx) {
                        liveTopSystemGestureInsetPx = topInset
                        scheduleTouchLayout()
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
                override fun onViewDetachedFromWindow(v: View) {
                    activeTouchGesture = false
                    pendingTouchLayoutAfterGesture = false
                    resetVelocityTracker()
                }
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
                        val touchedSecondary = secondarySurfaceFrames.values.any { frame ->
                            val b = frame.bounds
                            screenX >= b.left && screenX <= b.right && screenY >= b.top && screenY <= b.bottom
                        }
                        trackingSwipeDown = swipeZone != null && !touchedSecondary && (
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
                            resetVelocityTracker()
                            // Recycled via resetVelocityTracker() on ACTION_UP/ACTION_CANCEL, next ACTION_DOWN, and detach.
                            @Suppress("Recycle")
                            velocityTracker = VelocityTracker.obtain()
                            addScreenSpaceVelocitySample(event)
                            val swipeHeightDp = (CustomIslandStore.settings.value.swipeDownIsland?.main?.height ?: 150).toFloat()
                            val cutoutDiameterDp = currentCutoutInfo?.let { islandCameraGeometry(it.radiusPx, density).diameter } ?: 24f
                            pullRangePx = ((swipeHeightDp - cutoutDiameterDp) * density).coerceIn(90f * density, 175f * density)
                        }
                        val consumed = if (isIdle) {
                            trackingSwipeDown
                        } else {
                            val handled = forwardToCompact(event)
                            forwardedDownToCompact = handled
                            handled || trackingSwipeDown
                        }
                        activeTouchGesture = consumed
                        return@setOnTouchListener consumed
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
                        val sensitivity = OverlayPreferences.swipeDownSensitivityFlow.value
                        if (trackingSwipeDown && isCameraDotSwipeDown(dx = dx, dy = dy, density = density, sensitivity = sensitivity)) {
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
                        activeTouchGesture = false
                        interactivePullActive = false
                        trackingSwipeDown = false
                        forwardedDownToCompact = false
                        val flushPendingTouchLayout = pendingTouchLayoutAfterGesture
                        pendingTouchLayoutAfterGesture = false
                        val dx = lastRawX - downRawX
                        val dy = lastRawY - downRawY
                        val sensitivity = OverlayPreferences.swipeDownSensitivityFlow.value
                        if (wasPulling) {
                            val finalProgress = interactivePullProgress(dy, pullRangePx)
                            val commit = shouldCommitSwipeDownPull(
                                progress = finalProgress,
                                velocityYPxPerSec = finalVelocity,
                                density = density,
                                cancelled = cancelled,
                                sensitivity = sensitivity,
                            )
                            if (commit) view.performIslandHaptic(HapticFeedbackConstants.LONG_PRESS)
                            finishInteractiveSwipeDownCustomIsland(commit)
                            return@setOnTouchListener true
                        }
                        if (!cancelled && wasTracking && (isCameraDotSwipeDown(dx, dy, density, sensitivity) ||
                                (dy > 4f * density && finalVelocity > sensitivity.commitVelocityDpPerSec * density))
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
                        val result = if (wasForwarded) forwardToCompact(event) else wasTracking
                        if (flushPendingTouchLayout) scheduleTouchLayout()
                        return@setOnTouchListener result
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
            OverlayPreferences.getNotificationActivitySettings(context)
            OverlayPreferences.getIslandPriorityOrder(context)
            OverlayPreferences.initializeMusicSettings(context)
            seenActivitySignatures.clear()
            NotificationActivityState.eligibleActivities.value.forEach {
                seenActivitySignatures[it.key] = autoExpandActivitySignature(it)
            }
            observeMediaState()
            observeFlashlightState()
            observeNotificationActivityState()
            observeAutoExpandActivities()
            observePriorityOrder()
            observeDebugMode()
            observeCustomIsland()
            controllerScope.launch {
                combine(CustomIslandNotifications.active, ScreenStateTracker.isDeviceLocked,
                    NotificationActivityState.eligibleActivities) { _, _, _ -> Unit }
                    .collect { updateOverlayLayout() }
            }
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
            val customIslands = customIslandSettings.activeIslands
            val eligibleActivities by NotificationActivityState.eligibleActivities.collectAsState()
            val customNotifications by CustomIslandNotifications.active.collectAsState()
            val deviceLocked by ScreenStateTracker.isDeviceLocked.collectAsState()
            val foregroundPackage by ForegroundAppTracker.foregroundPackage.collectAsState()
            val hideInRelatedApp by OverlayPreferences.hideInRelatedAppFlow.collectAsState()
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
                customIslands = customIslands,
                eligibleActivities = eligibleActivities,
                customNotifications = customNotifications.values,
                deviceLocked = deviceLocked,
                foregroundPackage = foregroundPackage,
                hideInRelatedApp = hideInRelatedApp,
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
                    customIslands = snapshot.visibleCustomIslands,
                    hasSwipeDownCustomIsland = customIslandSettings.swipeDownIsland != null,
                    isFlashlightOn = snapshot.isFlashlightVisible,
                    isExpanded = morphing,
                    isExiting = isHiding && !morphing,
                    hideCompactPill = isCompactSurfaceHidden && selectedSlot == null,
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
                            ?: filterActivitiesForForeground(eligibleActivities, hideInRelatedApp, foregroundPackage)
                                .firstOrNull { it.key == key }
                    }
                    val expandedNotificationActivity = if (autoExpandedActivityKey != null) {
                        activeAutoExpanded ?: retainedExpandedActivity
                    } else {
                        slot?.activity ?: primaryStackActivity ?: retainedExpandedActivity
                    }
                    IslandSurfaceOverlay(
                        cutoutInfo = activeCutout,
                        mediaInfo = mediaTrack,
                        notificationActivity = expandedNotificationActivity,
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
        if (!swipeDown.hasExpandedContent) return false

        MediaPlaybackState.dismissSongAnnouncement()
        isSongAnnouncementActive = false

        _expandedType.value = IslandType.CUSTOM
        selectedSlot = null
        clearPendingAutoExpansions()
        autoExpandedActivityKey = null
        autoExpandedHasOwnTimer = false
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
    ) {
        if (!isOverlayAdded || isHiding || isOverlayMorphing || isIslandExpanded) return
        if (type == IslandType.CUSTOM) {
            val targetId = slot?.customIslandId ?: currentStack().primary?.content?.customIslandId
            val custom = customOverride
                ?: CustomIslandStore.settings.value.activeIslands.firstOrNull { it.id == targetId }
                ?: CustomIslandStore.settings.value.activeIsland
                ?: return
            if (!custom.hasExpandedContent) return
            expandedCustomIsland = custom
            isSwipeDownCustomExpanded = swipeDownCustom
        } else {
            isSwipeDownCustomExpanded = false
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
            clearPendingAutoExpansions()
            autoExpandedHasOwnTimer = false
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

    private fun clearPendingAutoExpansions() {
        pendingAutoExpandDrainJob?.cancel()
        pendingAutoExpandDrainJob = null
        pendingAutoExpandQueue.clear()
    }

    private fun autoExpandActivity(
        info: NotificationActivityInfo,
        settings: NotificationActivitySettings,
        fromQueue: Boolean = false,
    ) {
        if (!isOverlayAdded || isHiding) return
        val cutout = currentCutoutInfo ?: return
        if (swipeDownPullProgress != null) return
        if (isIslandExpanded && autoExpandedActivityKey == null) return

        val needsOwnTimer = fromQueue || !info.isSystemActivity || isPermanentShortTermAlert(info, settings)

        // Same activity already temporarily expanded: update its content in place.
        if (isIslandExpanded && autoExpandedActivityKey == info.key) {
            retainedExpandedActivity = info
            if (autoExpandedHasOwnTimer || needsOwnTimer) {
                autoExpandedHasOwnTimer = true
                scheduleAutoExpandCollapseTimer(info.key)
            }
            return
        }

        // Another activity is currently temporarily expanded or still collapsing: wait until it hides itself.
        if (isIslandExpanded || isOverlayMorphing) {
            pendingAutoExpandQueue.enqueue(info)
            return
        }

        pendingAutoExpandDrainJob?.cancel()
        pendingAutoExpandDrainJob = null
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        cancelExpandedAutoCollapse()
        autoExpandedHasOwnTimer = needsOwnTimer

        expandOverlayInternal(
            type = IslandType.NOTIFICATION_ACTIVITY,
            slot = null,
            source = compactSurfaceFrame ?: cutoutOriginFrame(cutout),
            autoExpandedKey = info.key,
            initialActivity = info,
        )

        if (autoExpandedHasOwnTimer) {
            scheduleAutoExpandCollapseTimer(info.key)
        }
    }

    private fun scheduleAutoExpandCollapseTimer(activityKey: String) {
        autoCollapseJob?.cancel()
        autoCollapseJob = controllerScope.launch {
            delay(AUTO_EXPAND_DURATION_MS)
            if (isIslandExpanded && autoExpandedActivityKey == activityKey) {
                collapseOverlay()
            }
        }
    }

    private fun scheduleNextQueuedAutoExpand() {
        if (!isOverlayAdded || isHiding || isIslandExpanded || isOverlayMorphing || swipeDownPullProgress != null) return
        if (!pendingAutoExpandQueue.isNotEmpty() || !ScreenStateTracker.isScreenOn.value) return
        pendingAutoExpandDrainJob?.cancel()
        pendingAutoExpandDrainJob = controllerScope.launch {
            delay(AUTO_EXPAND_QUEUE_GAP_MS)
            drainNextAutoExpandAlert()
        }
    }

    private fun drainNextAutoExpandAlert() {
        pendingAutoExpandDrainJob = null
        if (!isOverlayAdded || isHiding || isIslandExpanded || isOverlayMorphing || swipeDownPullProgress != null) return
        if (!ScreenStateTracker.isScreenOn.value) {
            pendingAutoExpandQueue.clear()
            return
        }
        val settings = OverlayPreferences.notificationActivitySettingsFlow.value
        val foreground = ForegroundAppTracker.foregroundPackage.value
        val hideInRelatedApp = OverlayPreferences.hideInRelatedAppFlow.value
        val visibleEligible = filterActivitiesForForeground(
            activities = NotificationActivityState.eligibleActivities.value,
            hideInRelatedApp = hideInRelatedApp,
            foregroundPackage = foreground,
        )
        while (pendingAutoExpandQueue.isNotEmpty()) {
            val next = pendingAutoExpandQueue.pollNext(dismissalTracker::isActivityDismissed) ?: return
            if (!settings.allowsAutoExpand(next)) continue
            if (shouldHideForForegroundApp(hideInRelatedApp, foreground, next.packageName)) continue
            val live = visibleEligible.firstOrNull { it.key == next.key } ?: next
            autoExpandActivity(live, settings, fromQueue = true)
            return
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
        autoExpandedHasOwnTimer = false
        retainedExpandedActivity = null
        expandedCustomIsland = null
        isSwipeDownCustomExpanded = false
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
        scheduleNextQueuedAutoExpand()
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun extractTopSystemGestureInsetPx(insets: WindowInsets): Float {
        val statusTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val gestureTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures()).top
        val cutoutTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).top
        return maxOf(statusTop, gestureTop, cutoutTop).toFloat()
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun topStatusBarGestureBottomPx(density: Float): Float {
        val metricsInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.currentWindowMetrics?.windowInsets?.let(::extractTopSystemGestureInsetPx) ?: 0f
        } else 0f
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val dimenInset = if (resId > 0) context.resources.getDimensionPixelSize(resId).toFloat() else 0f
        return maxOf(liveTopSystemGestureInsetPx, metricsInset, dimenInset).coerceIn(0f, 80f * density)
    }

    private fun currentSwipeDownZone(padded: Boolean = false): Rect? {
        if (CustomIslandStore.settings.value.swipeDownIsland == null) return null
        val cutout = currentCutoutInfo ?: return null
        val dm = context.resources.displayMetrics
        val density = dm.density
        val screenWidth = (compactWindowParams?.width ?: dm.widthPixels).toFloat()
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val statusBarBottomPx = if (isLandscape) 0f else topStatusBarGestureBottomPx(density)
        val base = cameraDotSwipeBounds(
            cameraCenterX = cutout.centerX,
            cameraCenterY = cutout.centerY,
            cameraRadiusPx = cutout.radiusPx,
            screenWidthPx = screenWidth,
            density = density,
            isLandscape = isLandscape,
            sensitivity = OverlayPreferences.swipeDownSensitivityFlow.value,
            statusBarBottomPx = statusBarBottomPx,
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

    @android.annotation.SuppressLint("RtlHardcoded")
    private fun layoutTouchWindow() {
        if (isHiding) {
            IslandBackdropLuminanceTracker.updateIslandRegion(null)
            removeTouchWindow()
            return
        }
        // Keep the touch window stationary during an active touch or pull so InputDispatcher never cancels the gesture.
        if ((activeTouchGesture || swipeDownPullProgress != null) && touchAdded) {
            pendingTouchLayoutAfterGesture = true
            return
        }
        val wm = windowManager ?: return
        val view = touchView ?: return

        // One shared touch-region path for both compact and expanded islands.
        // Expanded mode sizes touchView strictly to the expanded card and uses
        // FLAG_WATCH_OUTSIDE_TOUCH so taps outside the card both collapse it and pass through.
        val stack = currentStack()
        val secondaryBounds = stack.secondary.mapNotNull { secondarySurfaceFrames[it.content]?.bounds }
        val swipeDownBounds = if (!isIslandExpanded) currentSwipeDownZone(padded = false) else null
        val compactBounds = if (stack.primary != null) compactSurfaceFrame?.bounds else null
        val bounds = activeIslandTouchBounds(
            isExpanded = isIslandExpanded,
            expandedBounds = expandedCardBounds,
            compactBounds = compactBounds,
            secondaryBounds = secondaryBounds,
            swipeDownBounds = swipeDownBounds,
        )
        val winOffsetX = (compactWindowParams?.x ?: 0).toFloat()
        val winOffsetY = (compactWindowParams?.y ?: 0).toFloat()
        val samplePrimary = (if (isIslandExpanded) expandedCardBounds else compactBounds)
            ?.translate(winOffsetX, winOffsetY)
        val sampleExcluded = if (isIslandExpanded) {
            emptyList()
        } else {
            secondaryBounds.map { it.translate(winOffsetX, winOffsetY) }
        }
        IslandBackdropLuminanceTracker.updateIslandRegion(samplePrimary, sampleExcluded)
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
                isSwipeDownCustomExpanded -> CustomIslandStore.settings.value.swipeDownIsland != null
                else -> {
                    val slot = selectedSlot
                    if (slot == null) {
                        val primary = stack.primary?.content
                        primary?.type == IslandType.CUSTOM && primary.customIslandId == expandedCustomIsland?.id
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
            combine(
                NotificationActivityState.current,
                NotificationActivityState.eligibleActivities,
                ForegroundAppTracker.foregroundPackage,
                OverlayPreferences.hideInRelatedAppFlow,
            ) { current, eligible, foreground, hideEnabled ->
                Triple(
                    selectBestVisibleActivity(current, eligible, hideEnabled, foreground),
                    foreground,
                    hideEnabled,
                )
            }.collectLatest { (visibleActivity, foreground, hideEnabled) ->
                if (isIslandExpanded && _expandedType.value == IslandType.NOTIFICATION_ACTIVITY) {
                    val expandedActivity = if (autoExpandedActivityKey != null) {
                        retainedExpandedActivity
                    } else {
                        selectedSlot?.activity ?: retainedExpandedActivity
                    }
                    if (expandedActivity != null && shouldHideForForegroundApp(hideEnabled, foreground, expandedActivity.packageName)) {
                        collapseOverlay()
                    }
                }
                updateVisibility(VisibilityKey.ACTIVITY, visibleActivity != null)
                updateOverlayLayout()
            }
        }
    }

    private fun observeAutoExpandActivities() {
        controllerScope.launch {
            NotificationActivityState.eligibleActivities.collectLatest { eligible ->
                val settings = OverlayPreferences.notificationActivitySettingsFlow.value
                val visibleActivities = filterActivitiesForForeground(
                    activities = eligible,
                    hideInRelatedApp = OverlayPreferences.hideInRelatedAppFlow.value,
                    foregroundPackage = ForegroundAppTracker.foregroundPackage.value,
                )
                pendingAutoExpandQueue.updateLive(visibleActivities)
                val activeAutoKey = autoExpandedActivityKey
                if (activeAutoKey != null) {
                    val dismissed = dismissalTracker.isActivityDismissed(activeAutoKey)
                    val liveAuto = if (dismissed) null else visibleActivities.firstOrNull { it.key == activeAutoKey }
                    if (liveAuto != null) {
                        retainedExpandedActivity = liveAuto
                    } else if (isIslandExpanded && (dismissed || !autoExpandedHasOwnTimer)) {
                        collapseOverlay()
                    }
                }
                val activeSlot = selectedSlot
                if (isIslandExpanded && activeSlot?.activityKey != null) {
                    val stillPresent = currentStack().ordered.any { it.content == activeSlot }
                    if (!stillPresent) collapseOverlay()
                }
                val candidates = selectAutoExpandActivities(
                    activities = visibleActivities,
                    seenSignatures = seenActivitySignatures,
                    settings = settings,
                    isDismissed = dismissalTracker::isActivityDismissed,
                )
                seenActivitySignatures.clear()
                eligible.forEach { seenActivitySignatures[it.key] = autoExpandActivitySignature(it) }
                candidates.forEach { candidate ->
                    autoExpandActivity(candidate, settings)
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
            val foregroundSignals = combine(
                ForegroundAppTracker.foregroundPackage,
                OverlayPreferences.hideInRelatedAppFlow,
                MediaPlaybackState.currentTrack,
            ) { foreground, hideEnabled, track -> Triple(foreground, hideEnabled, track) }
            combine(
                CustomIslandStore.settings,
                foregroundSignals,
                NotificationActivityState.eligibleActivities,
                CustomIslandNotifications.active,
            ) { settings, (foreground, hideEnabled, track), eligible, notifications ->
                val filteredEligible = filterActivitiesForForeground(eligible, hideEnabled, foreground)
                val visibleCustomIslands = filterCustomIslandsForForeground(
                    customIslands = settings.activeIslands,
                    hideInRelatedApp = hideEnabled,
                    foregroundPackage = foreground,
                    activeMediaPackage = track.playerPackageName,
                    eligibleActivities = filteredEligible,
                    activeNotifications = notifications.values,
                    resolveWidgetPackage = { CustomIslandWidgets.info(it)?.provider?.packageName },
                )
                settings to visibleCustomIslands
            }.collectLatest { (settings, visibleCustomIslands) ->
                if (_expandedType.value == IslandType.CUSTOM) {
                    val expected = if (isSwipeDownCustomExpanded) {
                        settings.swipeDownIsland
                    } else {
                        expandedCustomIsland?.id?.let { id -> visibleCustomIslands.firstOrNull { it.id == id } }
                    }
                    if (expandedCustomIsland != expected) {
                        if (expected != null) expandedCustomIsland = expected
                        else collapseOverlay()
                    }
                }
                updateOverlayLayout()
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
                if (!screenOn) {
                    clearPendingAutoExpansions()
                    if (isIslandExpanded || swipeDownPullProgress != null) {
                        swipeDownPullProgress = null
                        collapseOverlay()
                    }
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
        controllerScope.launch {
            OverlayPreferences.swipeDownSensitivityFlow.collectLatest {
                scheduleTouchLayout()
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
        liveTopSystemGestureInsetPx = 0f
        currentCutoutInfo = CutoutDetector.detect(context)
        updateOverlayLayout()
    }

    suspend fun hide() {
        if (!isOverlayAdded) return
        if (compactView?.visibility == View.VISIBLE && ScreenStateTracker.isScreenOn.value) {
            isHiding = true
            clearPendingAutoExpansions()
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
        clearPendingAutoExpansions()
        cancelExpandedAutoCollapse()
        seenActivitySignatures.clear()
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
        autoExpandedHasOwnTimer = false
        retainedExpandedActivity = null
        expandedCustomIsland = null
        isSwipeDownCustomExpanded = false
        swipeDownPullProgress = null
        activeTouchGesture = false
        pendingTouchLayoutAfterGesture = false
        liveTopSystemGestureInsetPx = 0f

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

private const val AUTO_EXPAND_DURATION_MS = 3_500L
private const val AUTO_EXPAND_QUEUE_GAP_MS = 160L

internal class AutoExpandAlertQueue {
    private val queued = ArrayDeque<NotificationActivityInfo>()

    fun isNotEmpty(): Boolean = queued.isNotEmpty()

    fun enqueue(info: NotificationActivityInfo) {
        val existingIndex = queued.indexOfFirst { it.key == info.key }
        if (existingIndex >= 0) {
            queued[existingIndex] = info
        } else {
            queued.addLast(info)
        }
    }

    fun updateLive(liveShortTerms: List<NotificationActivityInfo>) {
        if (queued.isEmpty()) return
        val liveByKey = liveShortTerms.associateBy { it.key }
        for (i in queued.indices) {
            liveByKey[queued[i].key]?.let { queued[i] = it }
        }
    }

    fun pollNext(isDismissed: (String) -> Boolean): NotificationActivityInfo? {
        while (queued.isNotEmpty()) {
            val next = queued.removeFirst()
            if (!isDismissed(next.key)) return next
        }
        return null
    }

    fun clear() {
        queued.clear()
    }
}

internal fun autoExpandActivitySignature(info: NotificationActivityInfo): String =
    if (info.kind == NotificationActivityKind.LIVE_UPDATE) {
        "${info.title}\u0000${info.text.orEmpty()}\u0000${info.shortCriticalText.orEmpty()}"
    } else {
        info.title
    }

internal fun selectAutoExpandActivities(
    activities: List<NotificationActivityInfo>,
    seenSignatures: Map<String, String>,
    settings: NotificationActivitySettings,
    isDismissed: (String) -> Boolean,
): List<NotificationActivityInfo> = activities
    .filter { !isDismissed(it.key) }
    .filter { info ->
        val previous = seenSignatures[info.key]
        val current = autoExpandActivitySignature(info)
        if (previous == null) {
            settings.shouldAutoExpandOnStart(info)
        } else {
            previous != current && settings.shouldAutoExpandOnUpdate(info)
        }
    }
    .sortedWith(compareBy({ it.postedAtMillis }, { it.key }))

internal fun selectAutoExpandActivity(
    activities: List<NotificationActivityInfo>,
    seenSignatures: Map<String, String>,
    settings: NotificationActivitySettings,
    isDismissed: (String) -> Boolean,
): NotificationActivityInfo? = selectAutoExpandActivities(
    activities = activities,
    seenSignatures = seenSignatures,
    settings = settings,
    isDismissed = isDismissed,
).lastOrNull()

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
    else -> false
}
