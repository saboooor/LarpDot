package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun bubbleGap(style: OverlayPreferences.BubbleStyle): Dp =
    if (style == OverlayPreferences.BubbleStyle.MATERIAL_3) 4.dp else 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    isMusicAllowed: Boolean = true,
    notificationActivity: NotificationActivityInfo? = null,
    shortTermActivities: List<NotificationActivityInfo> = emptyList(),
    isFlashlightOn: Boolean = false,
    isExpanded: Boolean = false,
    hideCompactStack: Boolean = false,
    hiddenSecondarySlot: IslandSlot? = null,
    onExpand: (IslandType, IslandSlot?, CompactIslandFrame) -> Unit,
    onDismiss: (IslandType, IslandSlot?) -> Unit = { _, _ -> },
    onCompactFrameChanged: (CompactIslandFrame) -> Unit = {},
    onCompactScaleChanged: (Float) -> Unit = {},
    onCompactStretchChanged: (Float) -> Unit = {},
    onSecondaryFrameChanged: (IslandSlot, CompactIslandFrame) -> Unit = { _, _ -> },
    onSecondaryScaleChanged: (IslandSlot, Float) -> Unit = { _, _ -> },
    onSecondaryStretchChanged: (IslandSlot, Float) -> Unit = { _, _ -> },
    onFlashlightToggle: () -> Unit = {},
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
    val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()

    val isMusicActiveGlobal by MediaPlaybackState.isMusicActive.collectAsState()
    val hideMedia by ca.saboor.larpdot.service.ForegroundAppTracker.shouldHideMedia.collectAsState()
    val isMusicActive = mediaInfo.hasMedia && isMusicActiveGlobal && !hideMedia && isMusicAllowed
    val isFlashlightActive = isFlashlightOn
    val isNotificationActivityActive = notificationActivity != null || shortTermActivities.isNotEmpty()
    val isPillActive = isMusicActive || isFlashlightActive || isNotificationActivityActive

    val priorityOrder by OverlayPreferences.islandPriorityOrderFlow.collectAsState()
    val stack = builtInIslandSlots(
        isMusicActive,
        isFlashlightActive,
        notificationActivity,
        shortTermActivities,
        priorityOrder = priorityOrder,
    )
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

    // When there are 2 tiny dots, dot 0 is placed on the left and dot 1 on the right.
    // When there is 1 tiny dot, it is placed on the right.
    val leftSecondaryType = if (secondaryTypes.size >= 2) secondaryTypes[0] else null
    val rightSecondaryType = if (secondaryTypes.size >= 2) secondaryTypes[1] else secondaryTypes.firstOrNull()

    var lastLeftType by remember { mutableStateOf<IslandSlot?>(null) }
    var lastRightType by remember { mutableStateOf<IslandSlot?>(null) }
    LaunchedEffect(leftSecondaryType) {
        if (leftSecondaryType != null) lastLeftType = leftSecondaryType
    }
    LaunchedEffect(rightSecondaryType) {
        if (rightSecondaryType != null) lastRightType = rightSecondaryType
    }
    val displayLeftType = leftSecondaryType ?: lastLeftType
    val displayRightType = rightSecondaryType ?: lastRightType

    var lastDisplayType by remember { mutableStateOf(IslandType.MEDIA) }
    LaunchedEffect(primaryType) {
        if (primaryType != null) {
            lastDisplayType = primaryType
        }
    }

    val activeDisplayType = primaryType ?: lastDisplayType

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
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val isAnnouncing = isSongAnnouncement && showSongAnnouncement && isMusicActive
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
        compactPillThickness, isAnnouncing,
    )
    val presentation = presentations.getValue(activeDisplayType)
    val activeExtraDp = presentation.compactExtra
    val visualizerWidthDp = compactVisualizerWidthDp(
        showVisualizer = showMinimizedVisualizer,
        bandCount = waveformBandCount,
        barWidth = waveformBarWidth,
        barSpacing = waveformBarSpacing,
    )
    val mediaWings = configuredCompactMediaWings(
        mediaInfo = mediaInfo,
        extraWidthDp = activeExtraDp.value,
        hasTrailingBubble = !isLandscape && rightSecondaryType != null,
        showTitle = showMinimizedTitle && isMusicActive,
        announcing = isAnnouncing,
        visualizerWidthDp = visualizerWidthDp,
        cameraDiameterDp = cutoutDiameterDp.value,
        cameraCenterXPx = cutoutInfo.centerX,
        bubbleThicknessDp = secondaryDotSize.value,
        hasLeadingBubble = leftSecondaryType != null,
        landscape = isLandscape,
    )
    val compactWidth = presentation.width(cutoutDiameterDp, compactPillThickness, isLandscape,
        if (activeDisplayType == IslandType.MEDIA) mediaWings.widthDp.dp else null)
    val compactHeight = presentation.height(cutoutDiameterDp, compactPillThickness, isLandscape)

    val currentCornerRadius by animateDpAsState(
        targetValue = compactCornerRadius,
        animationSpec = LarpMotion.resize(),
        label = "compact_corner",
    )
    val mainShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
        groupedIslandShape(currentCornerRadius, leftSecondaryType != null, rightSecondaryType != null, isLandscape)
    } else RoundedCornerShape(currentCornerRadius)

    val targetPillColor = if (isPillActive) Color.Black else Color.Transparent
    val pillColor by animateColorAsState(
        targetValue = targetPillColor,
        animationSpec = LarpMotion.fade(240),
        label = "pill_color",
    )

    val targetExtra = if (isPillActive) (compactWidth - cutoutDiameterDp).value.coerceAtLeast(0f) else 0f
    val targetTrailing = when {
        isLandscape -> 0.5f
        activeDisplayType == IslandType.MEDIA -> mediaWings.trailingFraction
        else -> presentation.compactTrailingFraction
    }
    val animatedWings by animateOffsetAsState(
        targetValue = Offset(targetExtra * (1f - targetTrailing), targetExtra * targetTrailing),
        animationSpec = LarpMotion.resize(),
        label = "compact_wings",
    )
    val leadingWidth = animatedWings.x.coerceAtLeast(0f)
    val trailingWidth = animatedWings.y.coerceAtLeast(0f)
    val wingWidth = leadingWidth + trailingWidth
    val currentWidth = cutoutDiameterDp + wingWidth.dp
    val trailingWingFraction = if (wingWidth > 0f) (trailingWidth / wingWidth).coerceIn(0.001f, 0.999f) else 0.5f
    val currentHeight by animateDpAsState(
        targetValue = if (isPillActive) compactHeight else cutoutDiameterDp,
        animationSpec = LarpMotion.resize(),
        label = "compact_height",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isPillActive) 1f else 0f,
        animationSpec = LarpMotion.fade(if (isPillActive) 230 else 150),
        label = "compact_content_alpha",
    )

    fun islandModel(type: IslandType, secondary: Boolean = false): IslandModel {
        val activate: () -> Unit = when (type) {
            IslandType.MEDIA -> if (secondary) {
                { MediaPlaybackState.togglePlayPause() }
            } else {
                { openPlayerApp(context, mediaInfo) }
            }
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
                onDown = { ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context) },
                onUp = dismissAction,
            )
        }
    }

    val mainPillModifier = Modifier
        .then(primaryPress.modifier)
        .islandDragGestures(
            activeDisplayType, isPillActive && !isExpanded, dragOffsetAnim, maxDragOffsetPx,
            density.density, dragActions(activeDisplayType, secondary = false, slot = stack.primary?.content),
        )
        .islandTapGestures(
            identity = activeDisplayType,
            enabled = isPillActive && !isExpanded,
            tapToExpand = tapToExpand,
            holdDurationMs = holdToExpandDurationMs,
            model = islandModel(activeDisplayType),
            onExpand = { requestExpand(activeDisplayType) },
            onFeedback = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
            onPress = { MediaPlaybackState.dismissSongAnnouncement() },
        )

    val mainPill = @Composable {
        Surface(
            modifier = Modifier
                .width(currentWidth)
                .height(currentHeight)
                .onGloballyPositioned {
                    val frame = CompactIslandFrame(
                        bounds = it.boundsInRoot(),
                        trailingFraction = trailingWingFraction,
                        hasBubbleBefore = leftSecondaryType != null,
                        hasBubbleAfter = rightSecondaryType != null,
                        announcing = isAnnouncing,
                    )
                    mainPillFrame[0] = frame
                    if (isPillActive) onCompactFrameChanged(frame)
                }
                .graphicsLayer {
                    alpha = if (hideCompactStack) 0f else 1f
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
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = { 0f },
                        cornerRadius = currentCornerRadius,
                        shape = mainShape,
                        strokeWidth = 0.75.dp,
                        trackColor = {
                            val trackAlpha = compactOutlineStyle(
                                showOutline = false,
                                progress = 0f,
                                contentAlpha = contentAlpha,
                            ).trackAlpha
                            Color(0x30FFFFFF).copy(alpha = trackAlpha)
                        },
                        progressColor = { Color.Transparent },
                    )
                ),
            shape = mainShape,
            color = pillColor,
            shadowElevation = if (isPillActive) 4.dp else 0.dp,
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
                            IslandCompactContent(
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

                if (isDebugMode && contentAlpha < 0.05f) {
                    val dotDiameter = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceAtLeast(18.dp)
                    Box(
                        modifier = Modifier
                            .size(dotDiameter)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size((dotDiameter - 4.dp).coerceAtLeast(12.dp))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }

    val hasLeftBubble = leftSecondaryType != null
    val hasRightBubble = rightSecondaryType != null

    val leftSlotActivity = resolveIslandSlotActivity(displayLeftType, notificationActivity, shortTermActivities)
    val leftActivityStatus = leftSlotActivity?.let { rememberCompactActivityStatus(it) }
    val targetLeftWidth = if (hasLeftBubble && displayLeftType != null) {
        if (isLandscape) secondaryDotSize else secondaryItemWidthDp(
            type = displayLeftType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = leftActivityStatus,
        ).dp
    } else 0.dp

    val targetLeftHeight = if (hasLeftBubble && displayLeftType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayLeftType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = leftActivityStatus,
        ).dp else secondaryDotSize
    } else 0.dp

    val leftBubbleWidth by animateDpAsState(
        targetValue = targetLeftWidth,
        animationSpec = LarpMotion.resize(),
        label = "left_bubble_width",
    )
    val leftBubbleHeight by animateDpAsState(
        targetValue = targetLeftHeight,
        animationSpec = LarpMotion.resize(),
        label = "left_bubble_height",
    )
    val leftBubbleAlpha by animateFloatAsState(
        targetValue = if (hasLeftBubble) 1f else 0f,
        animationSpec = LarpMotion.fade(200),
        label = "left_bubble_alpha",
    )
    val leftBubbleScale by animateFloatAsState(
        targetValue = if (hasLeftBubble) 1f else 0.82f,
        animationSpec = LarpMotion.release,
        label = "left_bubble_scale",
    )
    val leftBubbleGap by animateDpAsState(
        targetValue = if (hasLeftBubble) bubbleGap(bubbleStyle) else 0.dp,
        animationSpec = LarpMotion.resize(),
        label = "left_bubble_gap",
    )

    val rightSlots = if (secondaryTypes.size >= 2) secondaryTypes.drop(1) else secondaryTypes
    val rightCount = rightSlots.size.coerceAtLeast(1)
    val rightSlotActivity = resolveIslandSlotActivity(displayRightType, notificationActivity, shortTermActivities)
    val rightActivityStatus = rightSlotActivity?.let { rememberCompactActivityStatus(it) }
    val targetRightWidth = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryDotSize else secondaryItemWidthDp(
            type = displayRightType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = rightActivityStatus,
        ).dp
    } else 0.dp

    val targetRightHeight = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayRightType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = rightActivityStatus,
        ).dp else secondaryDotSize
    } else 0.dp

    val rightBubbleWidth by animateDpAsState(
        targetValue = targetRightWidth * rightCount,
        animationSpec = LarpMotion.resize(),
        label = "right_bubble_width",
    )
    val rightBubbleHeight by animateDpAsState(
        targetValue = targetRightHeight * if (isLandscape) rightCount else 1,
        animationSpec = LarpMotion.resize(),
        label = "right_bubble_height",
    )
    val rightBubbleAlpha by animateFloatAsState(
        targetValue = if (hasRightBubble) 1f else 0f,
        animationSpec = LarpMotion.fade(200),
        label = "right_bubble_alpha",
    )
    val rightBubbleScale by animateFloatAsState(
        targetValue = if (hasRightBubble) 1f else 0.82f,
        animationSpec = LarpMotion.release,
        label = "right_bubble_scale",
    )
    val rightBubbleGap by animateDpAsState(
        targetValue = if (hasRightBubble) bubbleGap(bubbleStyle) else 0.dp,
        animationSpec = LarpMotion.resize(),
        label = "right_bubble_gap",
    )

    @Composable
    fun SecondaryDotBubble(
        type: IslandSlot,
        beforeMain: Boolean,
        bubbleWidth: Dp,
        bubbleHeight: Dp,
        bubbleAlpha: Float,
        bubbleScale: Float,
        isActive: Boolean,
        isMiniPill: Boolean,
    ) {
        if ((bubbleWidth <= 0.5.dp && bubbleHeight <= 0.5.dp) || bubbleAlpha <= 0.01f) return

        val liveSlotActivity = resolveIslandSlotActivity(type, notificationActivity, shortTermActivities)
        var lastSlotActivity by remember(type) { mutableStateOf(liveSlotActivity) }
        LaunchedEffect(liveSlotActivity) {
            if (liveSlotActivity != null) lastSlotActivity = liveSlotActivity
        }
        val slotActivity = liveSlotActivity ?: lastSlotActivity
        val statusArrivalOffset = rememberStatusArrivalOffset(slotActivity, !isExpanded)
        val secondaryPress = rememberIslandPressFeedback(
            identity = type,
            enabled = isActive && !isExpanded,
            pressedScale = 1.12f,
            pressDurationMs = holdToExpandDurationMs,
        )
        val pressScale = secondaryPress.scale
        SideEffect { onSecondaryScaleChanged(type, pressScale * bubbleScale) }
        val stretchAnim = remember { Animatable(0f) }
        val maxDotDragPx = with(density) { 8.dp.toPx() }
        val dotStretch = (stretchAnim.value / maxDotDragPx).coerceIn(-1f, 1f)
        SideEffect { onSecondaryStretchChanged(type, dotStretch) }

        val effectiveAlpha = if (hideCompactStack || hiddenSecondarySlot == type) 0f else bubbleAlpha
        val bubbleCornerRadius = minOf(bubbleWidth, bubbleHeight) / 2f
        val bubbleShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
            groupedIslandShape(bubbleCornerRadius, !beforeMain, beforeMain, isLandscape)
        } else RoundedCornerShape(bubbleCornerRadius)

        val bubbleModifier = Modifier
                .then(secondaryPress.modifier)
                .size(width = bubbleWidth, height = bubbleHeight)
                .onGloballyPositioned {
                    val frame = CompactIslandFrame(
                        bounds = it.boundsInRoot(),
                        hasBubbleBefore = !beforeMain,
                        hasBubbleAfter = beforeMain,
                    )
                    secondaryFrames[type] = frame
                    onSecondaryFrameChanged(type, frame)
                }
                .islandDragGestures(
                    type, isActive && !isExpanded, stretchAnim, maxDotDragPx,
                    density.density, dragActions(type.type, secondary = true, slot = type),
                )
                .islandTapGestures(
                    identity = type,
                    enabled = isActive && !isExpanded,
                    tapToExpand = tapToExpand,
                    holdDurationMs = holdToExpandDurationMs,
                    model = islandModel(type.type, secondary = true).copy(
                        onActivate = { type.activity?.let(NotificationActivityState::open) ?: islandModel(type.type, true).onActivate() },
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
        Surface(
            modifier = bubbleModifier
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
                .border(0.75.dp, Color(0x30FFFFFF).copy(alpha = (48f / 255f) * bubbleAlpha), bubbleShape),
            shape = bubbleShape,
            color = Color.Black,
            shadowElevation = if (bubbleAlpha > 0.1f) 4.dp else 0.dp,
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
                notificationActivityStatus = slotActivity?.let { rememberCompactActivityStatus(it) },
                flashlightStatus = flashlightStatus,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isLandscape) Alignment.Center else Alignment.TopCenter,
    ) {
        val isLeftBubbleVisible = hasLeftBubble || leftBubbleWidth > 0.5.dp || leftBubbleHeight > 0.5.dp || leftBubbleAlpha > 0.01f
        val isRightBubbleVisible = hasRightBubble || rightBubbleWidth > 0.5.dp || rightBubbleHeight > 0.5.dp || rightBubbleAlpha > 0.01f

        // One animated wing split positions the shell and its content around the camera.
        val cutoutCenterX = with(density) { cutoutInfo.centerX.toDp() }
        val cutoutCenterY = with(density) { cutoutInfo.centerY.toDp() }
        val pillStartOffset = (cutoutCenterX - cutoutDiameterDp / 2 - leadingWidth.dp).coerceAtLeast(0.dp)
        val pillTopOffsetDp = (cutoutCenterY - currentHeight / 2).coerceAtLeast(0.dp)

        if (isLandscape) {
            val topExtraOffset = if (isLeftBubbleVisible) (leftBubbleHeight + leftBubbleGap) else 0.dp
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
                if (isLeftBubbleVisible && displayLeftType != null) {
                    SecondaryDotBubble(
                        type = displayLeftType,
                        beforeMain = true,
                        bubbleWidth = leftBubbleWidth,
                        bubbleHeight = leftBubbleHeight,
                        bubbleAlpha = leftBubbleAlpha,
                        bubbleScale = leftBubbleScale,
                        isActive = hasLeftBubble,
                        isMiniPill = showDotRightSideInfo,
                    )
                    Spacer(modifier = Modifier.height(leftBubbleGap))
                }
                Box(modifier = mainPillModifier) {
                    mainPill()
                }
                if (isRightBubbleVisible && displayRightType != null) {
                    Spacer(modifier = Modifier.height(rightBubbleGap))
                    rightSlots.ifEmpty { listOf(displayRightType) }.forEachIndexed { index, slot ->
                    if (index > 0) Spacer(Modifier.then(if (isLandscape) Modifier.height(rightBubbleGap) else Modifier.width(rightBubbleGap)))
                    SecondaryDotBubble(
                        type = slot,
                        beforeMain = false,
                        bubbleWidth = rightBubbleWidth / if (isLandscape) 1 else rightCount,
                        bubbleHeight = rightBubbleHeight / if (isLandscape) rightCount else 1,
                        bubbleAlpha = rightBubbleAlpha,
                        bubbleScale = rightBubbleScale,
                        isActive = hasRightBubble,
                        isMiniPill = showDotRightSideInfo,
                    )
                    }
                }
            }
        } else {
            val leftExtraOffset = if (isLeftBubbleVisible) (leftBubbleWidth + leftBubbleGap) else 0.dp
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
                if (isLeftBubbleVisible && displayLeftType != null) {
                    SecondaryDotBubble(
                        type = displayLeftType,
                        beforeMain = true,
                        bubbleWidth = leftBubbleWidth,
                        bubbleHeight = leftBubbleHeight,
                        bubbleAlpha = leftBubbleAlpha,
                        bubbleScale = leftBubbleScale,
                        isActive = hasLeftBubble,
                        isMiniPill = showDotRightSideInfo,
                    )
                    Spacer(modifier = Modifier.width(leftBubbleGap))
                }
                Box(modifier = mainPillModifier) {
                    mainPill()
                }
                if (isRightBubbleVisible && displayRightType != null) {
                    Spacer(modifier = Modifier.width(rightBubbleGap))
                    rightSlots.ifEmpty { listOf(displayRightType) }.forEachIndexed { index, slot ->
                    if (index > 0) Spacer(Modifier.then(if (isLandscape) Modifier.height(rightBubbleGap) else Modifier.width(rightBubbleGap)))
                    SecondaryDotBubble(
                        type = slot,
                        beforeMain = false,
                        bubbleWidth = rightBubbleWidth / if (isLandscape) 1 else rightCount,
                        bubbleHeight = rightBubbleHeight / if (isLandscape) rightCount else 1,
                        bubbleAlpha = rightBubbleAlpha,
                        bubbleScale = rightBubbleScale,
                        isActive = hasRightBubble,
                        isMiniPill = showDotRightSideInfo,
                    )
                    }
                }
            }
        }
    }
}

