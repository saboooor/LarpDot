package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffset
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun bubbleGap(style: OverlayPreferences.BubbleStyle): Dp =
    if (style == OverlayPreferences.BubbleStyle.MATERIAL_3) 4.dp else 8.dp

@Composable
private fun rememberIslandVisibility(visible: Boolean, label: String): Transition<Boolean> {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    return rememberTransition(state, label = label)
}

private fun Transition<Boolean>.isIdleAndHidden(): Boolean =
    !targetState && !currentState && !isRunning

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    isMusicAllowed: Boolean = true,
    notificationActivity: NotificationActivityInfo? = null,
    shortTermActivities: List<NotificationActivityInfo> = emptyList(),
    customIsland: CustomIsland? = null,
    hasSwipeDownCustomIsland: Boolean = false,
    isFlashlightOn: Boolean = false,
    isExpanded: Boolean = false,
    isExiting: Boolean = false,
    hideCompactPill: Boolean = false,
    hiddenSecondarySlot: IslandSlot? = null,
    onExpand: (IslandType, IslandSlot?, CompactIslandFrame) -> Unit,
    onSwipeDownCustomExpand: () -> Unit = {},
    onDismiss: (IslandType, IslandSlot?) -> Unit = { _, _ -> },
    onCompactFrameChanged: (CompactIslandFrame) -> Unit = {},
    onCompactScaleChanged: (Float) -> Unit = {},
    onCompactStretchChanged: (Float) -> Unit = {},
    onSecondaryFrameChanged: (IslandSlot, CompactIslandFrame) -> Unit = { _, _ -> },
    onSecondaryScaleChanged: (IslandSlot, Float) -> Unit = { _, _ -> },
    onSecondaryStretchChanged: (IslandSlot, Float) -> Unit = { _, _ -> },
    onFlashlightToggle: () -> Unit = {},
    onExitStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mainPillFrame = remember { arrayOfNulls<CompactIslandFrame>(1) }
    val secondaryFrames = remember { mutableMapOf<IslandSlot, CompactIslandFrame>() }
    fun requestExpand(type: IslandType, slot: IslandSlot? = null) {
        val frame = if (slot == null) mainPillFrame[0] else secondaryFrames[slot]
        frame?.let { onExpand(type, slot, it) }
    }
    val hapticFeedback = LocalHapticFeedback.current
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()

    val isMusicActiveGlobal by MediaPlaybackState.isMusicActive.collectAsState()
    val hideMedia by ca.saboor.larpdot.service.ForegroundAppTracker.shouldHideMedia.collectAsState()
    val isMusicActive = !isExiting && mediaInfo.hasMedia && isMusicActiveGlobal && !hideMedia && isMusicAllowed
    val isFlashlightActive = !isExiting && isFlashlightOn

    val priorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
    val stack = builtInIslandSlots(
        isMusicActive,
        isFlashlightActive,
        notificationActivity.takeUnless { isExiting },
        if (isExiting) emptyList() else shortTermActivities,
        priorityOrder = priorityOrder,
        custom = customIsland.takeUnless { isExiting },
    )
    val isPillActive = stack.primary != null
    val primarySlot = stack.primary?.content
    val primaryType = primarySlot?.type
    val secondaryTypes = stack.secondary.map { it.content }

    val resolvedPrimaryActivity = if (primaryType == IslandType.NOTIFICATION_ACTIVITY) {
        resolveIslandSlotActivity(primarySlot, notificationActivity, shortTermActivities) ?: notificationActivity
    } else null
    var lastPrimaryActivity by remember { mutableStateOf<NotificationActivityInfo?>(null) }
    LaunchedEffect(resolvedPrimaryActivity) {
        if (resolvedPrimaryActivity != null) lastPrimaryActivity = resolvedPrimaryActivity
    }
    val displayPrimaryActivity = resolvedPrimaryActivity ?: lastPrimaryActivity ?: notificationActivity
    val statusArrivalOffset = rememberStatusArrivalOffset(displayPrimaryActivity, !isExpanded)

    // Alternate secondary dots across right and left sides: 1st -> right, 2nd -> left, 3rd -> right, 4th -> left, ...
    val secondarySplit = splitSecondarySlots(secondaryTypes)
    val leftSlots = secondarySplit.left
    val rightSlots = secondarySplit.right

    var lastLeftSlots by remember { mutableStateOf(emptyList<IslandSlot>()) }
    var lastRightSlots by remember { mutableStateOf(emptyList<IslandSlot>()) }
    LaunchedEffect(leftSlots) {
        if (leftSlots.isNotEmpty()) lastLeftSlots = leftSlots
    }
    LaunchedEffect(rightSlots) {
        if (rightSlots.isNotEmpty()) lastRightSlots = rightSlots
    }
    val displayLeftSlots = leftSlots.ifEmpty { lastLeftSlots }
    val displayRightSlots = rightSlots.ifEmpty { lastRightSlots }

    var miniPillSlots by remember { mutableStateOf(setOf<IslandSlot>()) }
    LaunchedEffect(secondaryTypes) {
        miniPillSlots = miniPillSlots.intersect(secondaryTypes.toSet())
    }

    var lastDisplayType by remember { mutableStateOf(IslandType.MEDIA) }
    LaunchedEffect(primaryType) {
        if (primaryType != null) {
            lastDisplayType = primaryType
        }
    }

    val activeDisplayType = primaryType ?: lastDisplayType
    var lastCustomIsland by remember { mutableStateOf(customIsland) }
    LaunchedEffect(customIsland) {
        if (customIsland != null) lastCustomIsland = customIsland
    }
    val displayCustomIsland = customIsland ?: lastCustomIsland

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val compactCornerRadius = cameraGeometry.cornerRadius.dp
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val secondaryDotSize = secondaryBubbleThicknessDp(compactPillThickness.value, smallerBubbles).dp

    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val showMinimizedVisualizer by OverlayPreferences.showMinimizedVisualizerFlow.collectAsState()
    val waveformBandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val waveformBarWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val waveformBarSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val isAnnouncing = isSongAnnouncement && showSongAnnouncement && isMusicActive && primaryType != IslandType.CUSTOM
    val pulseEntireIsland by OverlayPreferences.pulseEntireIslandEnabledFlow.collectAsState()
    val pulseAmount by OverlayPreferences.pulseAmountFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isBpmResolved by BpmDetector.isResolved.collectAsState()
    val liveMediaPositionMs = rememberLiveMediaPositionMs(mediaInfo)
    val bpmPulseState = rememberBpmPulse(
        bpm = if (isBpmResolved) currentBpm else null,
        positionMsProvider = { liveMediaPositionMs.value },
        isPlaying = mediaInfo.isPlaying,
        enabled = pulseEntireIsland && isMusicActive,
    )

    val notificationActivityStatus = displayPrimaryActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()

    val presentations = rememberIslandPresentations(
        mediaInfo, displayPrimaryActivity, notificationActivityStatus, flashlightStatus,
        getFlashlightStrengthFraction(FlashlightController.torchStrength.collectAsState().value,
            FlashlightController.maxStrength.collectAsState().value),
        compactPillThickness, cutoutDiameterDp, isAnnouncing, displayCustomIsland,
    )
    val presentation = presentations.getValue(activeDisplayType)
    val activeExtraDp = presentation.compactExtra
    val visualizerWidthDp = compactVisualizerWidthDp(
        showVisualizer = showMinimizedVisualizer,
        bandCount = waveformBandCount,
        barWidth = waveformBarWidth,
        barSpacing = waveformBarSpacing,
    )
    val hasLeftBubble = leftSlots.isNotEmpty()
    val hasRightBubble = rightSlots.isNotEmpty()
    val gapDp = bubbleGap(bubbleStyle)

    @Composable
    fun secondaryExtent(slot: IslandSlot?, active: Boolean, isMiniPill: Boolean): Dp {
        if (slot == null || !active) return 0.dp
        val activity = resolveIslandSlotActivity(slot, notificationActivity, shortTermActivities)
        val status = activity?.let { rememberCompactActivityStatus(it) }
        return secondaryItemWidthDp(
            type = slot.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = isMiniPill,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = status,
            smaller = smallerBubbles,
            hasEarbudBatteryLevels = activity?.pairedEarbudBatteryLevels != null,
        ).dp
    }

    val leftReserve = if (hasLeftBubble) {
        leftSlots.fold(0f) { acc, slot ->
            acc + secondaryExtent(slot, active = true, isMiniPill = slot in miniPillSlots).value + gapDp.value
        }
    } else 0f
    val rightReserve = if (hasRightBubble) {
        rightSlots.fold(0f) { acc, slot ->
            acc + secondaryExtent(slot, active = true, isMiniPill = slot in miniPillSlots).value + gapDp.value
        }
    } else 0f
    val compactWings = if (isLandscape) {
        CompactMediaWings(0f, 0f)
    } else if (activeDisplayType == IslandType.MEDIA) configuredCompactMediaWings(
        mediaInfo = mediaInfo,
        extraWidthDp = activeExtraDp.value,
        showTitle = showMinimizedTitle && isMusicActive,
        announcing = isAnnouncing,
        visualizerWidthDp = visualizerWidthDp,
        cameraDiameterDp = cutoutDiameterDp.value,
        cameraCenterXPx = cutoutInfo.centerX,
        leadingBubbleExtentDp = leftReserve,
        trailingBubbleExtentDp = rightReserve,
        landscape = false,
    ) else {
        val wing = activeExtraDp.value / 2f
        rememberFittedCompactWings(
            CompactMediaWings(wing, wing), cutoutDiameterDp.value, cutoutInfo.centerX,
            leftReserve, rightReserve,
        )
    }
    val compactWidth = presentation.width(cutoutDiameterDp, compactPillThickness, isLandscape, compactWings.widthDp.dp)
    val compactHeight = presentation.height(cutoutDiameterDp, compactPillThickness, isLandscape)

    val currentCornerRadius by animateDpAsState(
        targetValue = compactCornerRadius,
        animationSpec = LarpMotion.resize(),
        label = "compact_corner",
    )
    val mainShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
        groupedIslandShape(currentCornerRadius, hasLeftBubble, hasRightBubble, isLandscape)
    } else RoundedCornerShape(currentCornerRadius)

    val pillVisibility = rememberIslandVisibility(isPillActive, "compact_pill")
    val targetExtra = (compactWidth - cutoutDiameterDp).value.coerceAtLeast(0f)
    val targetTrailing = compactWings.trailingFraction
    val animatedWings by pillVisibility.animateOffset({ LarpMotion.islandVisibility() }, label = "compact_wings") { visible ->
        if (visible) Offset(targetExtra * (1f - targetTrailing), targetExtra * targetTrailing) else Offset.Zero
    }
    val leadingWidth = animatedWings.x.coerceAtLeast(0f)
    val trailingWidth = animatedWings.y.coerceAtLeast(0f)
    val wingWidth = leadingWidth + trailingWidth
    val currentWidth = cutoutDiameterDp + wingWidth.dp
    val trailingWingFraction = if (wingWidth > 0f) (trailingWidth / wingWidth).coerceIn(0.001f, 0.999f) else 0.5f
    val currentHeight by pillVisibility.animateDp({ LarpMotion.islandVisibility() }, label = "compact_height") { visible ->
        if (visible) compactHeight else cutoutDiameterDp
    }
    val contentAlpha by pillVisibility.animateFloat({ LarpMotion.islandVisibility() }, label = "compact_alpha") { visible ->
        if (visible) 1f else 0f
    }

    fun islandModel(type: IslandType): IslandModel {
        val activate: () -> Unit = when (type) {
            IslandType.CUSTOM -> { { requestExpand(type) } }
            IslandType.MEDIA -> { { openPlayerApp(context, mediaInfo) } }
            IslandType.FLASHLIGHT -> onFlashlightToggle
            IslandType.NOTIFICATION_ACTIVITY -> {
                { displayPrimaryActivity?.let(NotificationActivityState::open) }
            }
        }
        val hold: () -> Unit = when {
            type == IslandType.MEDIA -> { { openPlayerApp(context, mediaInfo) } }
            else -> activate
        }
        return IslandModel(type, activate, hold, presentations.getValue(type))
    }

    val tapToExpand by OverlayPreferences.tapToExpandFlow.collectAsState()
    val holdToExpandDurationMs by OverlayPreferences.holdToExpandDurationMsFlow.collectAsState()
    val primaryPress = rememberIslandPressFeedback(
        identity = activeDisplayType,
        enabled = isPillActive && !isExpanded,
        pressedScale = 1.10f,
        pressDurationMs = holdToExpandDurationMs,
    )
    val islandScale = primaryPress.scale
    SideEffect { onCompactScaleChanged(islandScale) }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetAnim = remember { Animatable(0f) }
    val maxDragOffsetPx = with(density) { 8.dp.toPx() }
    val compactStretch = (dragOffsetAnim.value / maxDragOffsetPx).coerceIn(-1f, 1f)
    SideEffect { onCompactStretchChanged(compactStretch) }

    fun dragActions(type: IslandType, secondary: Boolean, slot: IslandSlot? = null): IslandDragActions {
        val base = when (type) {
            IslandType.CUSTOM -> IslandDragActions()
            IslandType.FLASHLIGHT -> flashlightDragActions(coroutineScope, density.density) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            IslandType.MEDIA -> if (!secondary && isMusicActive) IslandDragActions(onSwipe = { next ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                if (next) MediaPlaybackState.skipNext() else MediaPlaybackState.skipPrevious()
            }) else IslandDragActions()
            IslandType.NOTIFICATION_ACTIVITY -> IslandDragActions()
        }
        val dismissAction: () -> Unit = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onDismiss(type, slot)
        }
        return if (secondary) {
            base.copy(onUp = dismissAction)
        } else {
            base.copy(
                onDown = {
                    if (hasSwipeDownCustomIsland) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwipeDownCustomExpand()
                    } else {
                        ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context)
                    }
                },
                onUp = dismissAction,
            )
        }
    }

    val mainPillModifier = if (isExpanded || !isPillActive) Modifier else Modifier
        .then(primaryPress.modifier)
        .islandDragGestures(
            activeDisplayType, true, dragOffsetAnim, maxDragOffsetPx,
            density.density, dragActions(activeDisplayType, secondary = false, slot = stack.primary?.content),
        )
        .islandTapGestures(
            identity = activeDisplayType,
            enabled = true,
            tapToExpand = tapToExpand || activeDisplayType == IslandType.CUSTOM,
            holdDurationMs = holdToExpandDurationMs,
            model = islandModel(activeDisplayType),
            onExpand = { requestExpand(activeDisplayType) },
            onFeedback = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
            onPress = { MediaPlaybackState.dismissSongAnnouncement() },
        )

    val mainPill = @Composable {
        Box(
            modifier = Modifier
                .width(currentWidth)
                .height(currentHeight)
                .onGloballyPositioned {
                    val frame = CompactIslandFrame(
                        bounds = it.boundsInRoot(),
                        trailingFraction = trailingWingFraction,
                        hasBubbleBefore = hasLeftBubble,
                        hasBubbleAfter = hasRightBubble,
                        announcing = isAnnouncing,
                    )
                    mainPillFrame[0] = frame
                    if (isPillActive) onCompactFrameChanged(frame)
                }
                .graphicsLayer {
                    alpha = if (hideCompactPill) 0f else 1f
                    val stretchMag = dragOffsetAnim.value / maxDragOffsetPx
                    val maxStretch = 0.18f
                    val isSwiping = kotlin.math.abs(stretchMag) > 0.01f
                    val pulse = bpmPulseState.value
                    val pulseScale = if (pulseEntireIsland && isMusicActive && mediaInfo.isPlaying && activeDisplayType == IslandType.MEDIA && pulse > 0f) {
                        1f + (0.035f * pulse * pulseAmount)
                    } else {
                        1f
                    }
                    translationX = if (activeDisplayType == IslandType.NOTIFICATION_ACTIVITY) statusArrivalOffset else 0f
                    scaleX = (1f + kotlin.math.abs(stretchMag) * maxStretch) * islandScale * pulseScale
                    scaleY = islandScale * pulseScale
                    transformOrigin = if (isSwiping) {
                        if (stretchMag >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                    } else {
                        TransformOrigin.Center
                    }
                }
                .islandOutline(mainShape, currentCornerRadius, contentAlpha)
                .shadow(4.dp * contentAlpha, mainShape, clip = false)
                .clip(mainShape)
                .background(Color.Black.copy(alpha = contentAlpha)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (contentAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = contentAlpha },
                    ) {
                        Crossfade(
                            targetState = activeDisplayType to (
                                if (activeDisplayType == IslandType.NOTIFICATION_ACTIVITY) displayPrimaryActivity?.key else null
                            ),
                            animationSpec = LarpMotion.fade(200),
                            label = "compact_content_type",
                        ) { (displayType, displayActivityKey) ->
                            val resolvedActivity = if (displayType == IslandType.NOTIFICATION_ACTIVITY) {
                                displayActivityKey?.let { key ->
                                    shortTermActivities.firstOrNull { it.key == key }
                                        ?: notificationActivity?.takeIf { it.key == key }
                                        ?: displayPrimaryActivity?.takeIf { it.key == key }
                                } ?: displayPrimaryActivity
                            } else displayPrimaryActivity
                            if (displayType == IslandType.CUSTOM) {
                                displayCustomIsland?.let {
                                    CustomIslandCompactContent(it, cutoutDiameterDp, isLandscape, trailingWingFraction)
                                }
                            } else IslandCompactContent(
                                model = islandModel(displayType), media = mediaInfo, activity = resolvedActivity,
                                diameter = cutoutDiameterDp, landscape = isLandscape,
                                showTitle = showMinimizedTitle, announcing = isAnnouncing,
                                trailingWingFraction = trailingWingFraction,
                                onExpand = { requestExpand(displayType) },
                                onFlashlightToggle = onFlashlightToggle,
                            )
                        }
                    }
                }

            }
        }
    }

    val leftVisibility = rememberIslandVisibility(hasLeftBubble, "left_bubble")
    val leftReveal by leftVisibility.animateFloat({ LarpMotion.islandVisibility() }, label = "left_reveal") { visible ->
        if (visible) 1f else 0f
    }
    val leftBubbleAlpha by leftVisibility.animateFloat({ LarpMotion.islandVisibility() }, label = "left_alpha") { visible ->
        if (visible) 1f else 0f
    }
    val leftBubbleGap by leftVisibility.animateDp({ LarpMotion.islandVisibility() }, label = "left_gap") { visible ->
        if (visible) bubbleGap(bubbleStyle) else 0.dp
    }
    val leftSlotExtents = displayLeftSlots.map { slot ->
        key(slot) {
            val target = secondaryExtent(slot, active = true, isMiniPill = slot in miniPillSlots)
            val animated by animateDpAsState(target, LarpMotion.resize(), label = "left_slot_extent")
            animated * leftReveal
        }
    }

    val rightVisibility = rememberIslandVisibility(hasRightBubble, "right_bubble")
    val rightReveal by rightVisibility.animateFloat({ LarpMotion.islandVisibility() }, label = "right_reveal") { visible ->
        if (visible) 1f else 0f
    }
    val rightBubbleAlpha by rightVisibility.animateFloat({ LarpMotion.islandVisibility() }, label = "right_alpha") { visible ->
        if (visible) 1f else 0f
    }
    val rightBubbleGap by rightVisibility.animateDp({ LarpMotion.islandVisibility() }, label = "right_gap") { visible ->
        if (visible) bubbleGap(bubbleStyle) else 0.dp
    }
    val rightSlotExtents = displayRightSlots.map { slot ->
        key(slot) {
            val target = secondaryExtent(slot, active = true, isMiniPill = slot in miniPillSlots)
            val animated by animateDpAsState(target, LarpMotion.resize(), label = "right_slot_extent")
            animated * rightReveal
        }
    }

    val exitFinished = pillVisibility.isIdleAndHidden() &&
        leftVisibility.isIdleAndHidden() && rightVisibility.isIdleAndHidden()
    LaunchedEffect(exitFinished) { onExitStateChanged(exitFinished) }

    fun toggleSecondaryMiniPill(slot: IslandSlot) {
        miniPillSlots = if (slot in miniPillSlots) miniPillSlots - slot else miniPillSlots + slot
    }

    @Composable
    fun SecondaryDotBubble(
        type: IslandSlot,
        beforeMain: Boolean,
        hasNeighborBefore: Boolean,
        hasNeighborAfter: Boolean,
        bubbleWidth: Dp,
        bubbleHeight: Dp,
        bubbleAlpha: Float,
        isActive: Boolean,
        isMiniPill: Boolean,
        onToggleMiniPill: () -> Unit,
    ) {
        if ((bubbleWidth <= 0.5.dp && bubbleHeight <= 0.5.dp) || bubbleAlpha <= 0.01f) return

        val liveSlotActivity = resolveIslandSlotActivity(type, notificationActivity, shortTermActivities)
        var lastSlotActivity by remember(type) { mutableStateOf(liveSlotActivity) }
        LaunchedEffect(liveSlotActivity) {
            if (liveSlotActivity != null) lastSlotActivity = liveSlotActivity
        }
        val slotActivity = liveSlotActivity ?: lastSlotActivity
        val slotActivityStatus = slotActivity?.let { rememberCompactActivityStatus(it) }
        val statusArrivalOffset = rememberStatusArrivalOffset(slotActivity, !isExpanded)
        val secondaryPress = rememberIslandPressFeedback(
            identity = type,
            enabled = isActive && !isExpanded,
            pressedScale = 1.12f,
            pressDurationMs = holdToExpandDurationMs,
        )
        val pressScale = secondaryPress.scale
        val bubbleScale = 0.82f + 0.18f * bubbleAlpha
        SideEffect { onSecondaryScaleChanged(type, pressScale * bubbleScale) }
        val stretchAnim = remember { Animatable(0f) }
        val maxDotDragPx = with(density) { 8.dp.toPx() }
        val dotStretch = (stretchAnim.value / maxDotDragPx).coerceIn(-1f, 1f)
        SideEffect { onSecondaryStretchChanged(type, dotStretch) }

        val effectiveAlpha = if (hiddenSecondarySlot == type) 0f else bubbleAlpha
        val bubbleCornerRadius = minOf(bubbleWidth, bubbleHeight) / 2f
        val bubbleShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
            groupedIslandShape(bubbleCornerRadius, hasNeighborBefore, hasNeighborAfter, isLandscape)
        } else RoundedCornerShape(bubbleCornerRadius)
        val canShowMiniPill = secondaryItemWidthDp(
            type = type.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = true,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = slotActivityStatus,
            smaller = smallerBubbles,
            hasEarbudBatteryLevels = slotActivity?.pairedEarbudBatteryLevels != null,
        ) > secondaryDotSize.value + 0.5f

        val gestures = if (isExpanded || !isActive) Modifier else Modifier
                .then(secondaryPress.modifier)
                .islandDragGestures(
                    type, true, stretchAnim, maxDotDragPx,
                    density.density, dragActions(type.type, secondary = true, slot = type),
                )
                .islandTapGestures(
                    identity = type,
                    enabled = true,
                    tapToExpand = false,
                    holdDurationMs = holdToExpandDurationMs,
                    model = islandModel(type.type).copy(
                        onActivate = {
                            if (canShowMiniPill || isMiniPill) {
                                onToggleMiniPill()
                            }
                        },
                        onHold = { requestExpand(type.type, type) },
                    ),
                    onExpand = { requestExpand(type.type, type) },
                    onFeedback = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
        val pulse = bpmPulseState.value
        val secondaryPulseScale = if (pulseEntireIsland && isMusicActive && mediaInfo.isPlaying && type.type == IslandType.MEDIA && pulse > 0f) {
            1f + (0.035f * pulse * pulseAmount)
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .size(width = bubbleWidth, height = bubbleHeight)
                .onGloballyPositioned {
                    val frame = CompactIslandFrame(
                        bounds = it.boundsInRoot(),
                        hasBubbleBefore = hasNeighborBefore,
                        hasBubbleAfter = hasNeighborAfter,
                        isMiniPill = isMiniPill,
                    )
                    secondaryFrames[type] = frame
                    onSecondaryFrameChanged(type, frame)
                }
                .then(gestures)
                .graphicsLayer {
                    val scale = pressScale * bubbleScale * secondaryPulseScale
                    val stretch = (stretchAnim.value / maxDotDragPx).coerceIn(-1f, 1f)
                    scaleX = scale * (1f + kotlin.math.abs(stretch) * 0.22f)
                    scaleY = scale
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    translationX = statusArrivalOffset
                    alpha = effectiveAlpha
                    clip = false
                    shape = bubbleShape
                }
                .islandOutline(bubbleShape, bubbleCornerRadius, bubbleAlpha)
                .shadow(4.dp * bubbleAlpha, bubbleShape, clip = false)
                .clip(bubbleShape)
                .background(Color.Black),
        ) {
            SecondaryDotContent(
                type = type.type,
                bubbleWidth = bubbleWidth,
                bubbleHeight = bubbleHeight,
                bubbleThickness = secondaryDotSize,
                isMiniPill = isMiniPill,
                isLandscape = isLandscape,
                beforeMain = beforeMain,
                bubbleStyle = bubbleStyle,
                notificationActivity = slotActivity,
                mediaInfo = mediaInfo,
                notificationActivityStatus = slotActivityStatus,
                flashlightStatus = flashlightStatus,
                customIsland = displayCustomIsland,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isLandscape) Alignment.Center else Alignment.TopCenter,
    ) {
        val leftTotalExtent = leftSlotExtents.fold(0.dp) { acc, extent -> acc + extent }
        val rightTotalExtent = rightSlotExtents.fold(0.dp) { acc, extent -> acc + extent }
        val showLeftBubbles = (hasLeftBubble || leftTotalExtent > 0.5.dp) && displayLeftSlots.isNotEmpty() && leftBubbleAlpha > 0.01f
        val showRightBubbles = (hasRightBubble || rightTotalExtent > 0.5.dp) && displayRightSlots.isNotEmpty() && rightBubbleAlpha > 0.01f

        // One animated wing split positions the shell and its content around the camera.
        val cutoutCenterX = with(density) { cutoutInfo.centerX.toDp() }
        val cutoutCenterY = with(density) { cutoutInfo.centerY.toDp() }
        val pillStartOffset = (cutoutCenterX - cutoutDiameterDp / 2 - leadingWidth.dp).coerceAtLeast(0.dp)
        val pillTopOffsetDp = (cutoutCenterY - currentHeight / 2).coerceAtLeast(0.dp)

        if (isLandscape) {
            val topExtraOffset = if (showLeftBubbles) (leftTotalExtent + leftBubbleGap * displayLeftSlots.size) else 0.dp
            val colTopOffset = (pillTopOffsetDp - topExtraOffset).coerceAtLeast(0.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .absoluteOffset(
                        x = cutoutCenterX - maxWidth / 2,
                        y = colTopOffset,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showLeftBubbles) {
                    displayLeftSlots.forEachIndexed { index, slot ->
                        if (index > 0) Spacer(modifier = Modifier.height(leftBubbleGap))
                        SecondaryDotBubble(
                            type = slot,
                            beforeMain = true,
                            hasNeighborBefore = index > 0,
                            hasNeighborAfter = true,
                            bubbleWidth = secondaryDotSize * leftReveal,
                            bubbleHeight = leftSlotExtents[index],
                            bubbleAlpha = leftBubbleAlpha,
                            isActive = hasLeftBubble,
                            isMiniPill = slot in miniPillSlots,
                            onToggleMiniPill = { toggleSecondaryMiniPill(slot) },
                        )
                    }
                    Spacer(modifier = Modifier.height(leftBubbleGap))
                }
                Box(modifier = mainPillModifier) {
                    mainPill()
                }
                if (showRightBubbles) {
                    Spacer(modifier = Modifier.height(rightBubbleGap))
                    displayRightSlots.forEachIndexed { index, slot ->
                        if (index > 0) Spacer(Modifier.height(rightBubbleGap))
                        SecondaryDotBubble(
                            type = slot,
                            beforeMain = false,
                            hasNeighborBefore = true,
                            hasNeighborAfter = index < displayRightSlots.lastIndex,
                            bubbleWidth = secondaryDotSize * rightReveal,
                            bubbleHeight = rightSlotExtents[index],
                            bubbleAlpha = rightBubbleAlpha,
                            isActive = hasRightBubble,
                            isMiniPill = slot in miniPillSlots,
                            onToggleMiniPill = { toggleSecondaryMiniPill(slot) },
                        )
                    }
                }
            }
        } else {
            val leftExtraOffset = if (showLeftBubbles) (leftTotalExtent + leftBubbleGap * displayLeftSlots.size) else 0.dp
            val rowStartOffset = (pillStartOffset - leftExtraOffset).coerceAtLeast(0.dp)

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = pillTopOffsetDp,
                        start = rowStartOffset,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLeftBubbles) {
                    displayLeftSlots.forEachIndexed { index, slot ->
                        if (index > 0) Spacer(modifier = Modifier.width(leftBubbleGap))
                        SecondaryDotBubble(
                            type = slot,
                            beforeMain = true,
                            hasNeighborBefore = index > 0,
                            hasNeighborAfter = true,
                            bubbleWidth = leftSlotExtents[index],
                            bubbleHeight = secondaryDotSize * leftReveal,
                            bubbleAlpha = leftBubbleAlpha,
                            isActive = hasLeftBubble,
                            isMiniPill = slot in miniPillSlots,
                            onToggleMiniPill = { toggleSecondaryMiniPill(slot) },
                        )
                    }
                    Spacer(modifier = Modifier.width(leftBubbleGap))
                }
                Box(modifier = mainPillModifier) {
                    mainPill()
                }
                if (showRightBubbles) {
                    Spacer(modifier = Modifier.width(rightBubbleGap))
                    displayRightSlots.forEachIndexed { index, slot ->
                        if (index > 0) Spacer(Modifier.width(rightBubbleGap))
                        SecondaryDotBubble(
                            type = slot,
                            beforeMain = false,
                            hasNeighborBefore = true,
                            hasNeighborAfter = index < displayRightSlots.lastIndex,
                            bubbleWidth = rightSlotExtents[index],
                            bubbleHeight = secondaryDotSize * rightReveal,
                            bubbleAlpha = rightBubbleAlpha,
                            isActive = hasRightBubble,
                            isMiniPill = slot in miniPillSlots,
                            onToggleMiniPill = { toggleSecondaryMiniPill(slot) },
                        )
                    }
                }
            }
        }
    }
}

