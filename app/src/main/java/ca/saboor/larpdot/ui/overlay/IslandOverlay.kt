package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.ui.theme.motionBlur
import ca.saboor.larpdot.ui.theme.motionFraction
import ca.saboor.larpdot.ui.theme.rememberMotionBlur
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlinx.coroutines.launch

private fun bubbleGap(style: OverlayPreferences.BubbleStyle): Dp =
    if (style == OverlayPreferences.BubbleStyle.MATERIAL_3) 4.dp else 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    notificationActivity: NotificationActivityInfo? = null,
    isFlashlightOn: Boolean = false,
    isExpanded: Boolean = false,
    hideCompactStack: Boolean = false,
    hiddenSecondarySlot: IslandSlot? = null,
    onExpand: (IslandType, IslandSlot?, CompactIslandFrame) -> Unit,
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
    val statusArrivalOffset = rememberStatusArrivalOffset(notificationActivity, !isExpanded)
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
    val isMusicActive = mediaInfo.hasMedia && isMusicActiveGlobal && !hideMedia
    val isFlashlightActive = isFlashlightOn
    val isNotificationActivityActive = notificationActivity != null
    val isPillActive = isMusicActive || isFlashlightActive || isNotificationActivityActive

    val shortTerms by NotificationActivityState.shortTermActivities.collectAsState()
    val stack = builtInIslandSlots(isMusicActive, isFlashlightActive, notificationActivity, shortTerms)
    val primaryType = stack.primary?.content?.type
    val secondaryTypes = stack.secondary.map { it.content }

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
    val bpmPulseState = rememberBpmPulse(
        bpm = if (isBpmResolved) currentBpm else null,
        currentPositionMs = mediaInfo.positionMs,
        isPlaying = mediaInfo.isPlaying,
        enabled = pulseEntireIsland && isMusicActive,
    )

    val notificationActivityStatus = notificationActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()

    val presentations = rememberIslandPresentations(
        mediaInfo, notificationActivityStatus, flashlightStatus,
        getFlashlightStrengthFraction(FlashlightController.torchStrength.collectAsState().value,
            FlashlightController.maxStrength.collectAsState().value),
        compactPillThickness, isAnnouncing,
    )
    val presentation = presentations.getValue(activeDisplayType)
    val activeExtraDp = presentation.compactExtra
    val visualizerWidthDp = if (showMinimizedVisualizer) {
        waveformBandCount * waveformBarWidth + (waveformBandCount - 1).coerceAtLeast(0) * waveformBarSpacing
    } else 0f
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
    val targetTrailing = if (activeDisplayType == IslandType.MEDIA && !isLandscape) mediaWings.trailingFraction else 0.5f
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
                { notificationActivity?.let(NotificationActivityState::open) }
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

    val progressFraction = presentation.progress

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = LinearEasing),
        label = "compact_progress",
    )

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetAnim = remember { Animatable(0f) }
    val maxDragOffsetPx = with(density) { 8.dp.toPx() }
    val compactStretch = (dragOffsetAnim.value / maxDragOffsetPx).coerceIn(-1f, 1f)
    SideEffect { onCompactStretchChanged(compactStretch) }

    val showFlashlightOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxTorchStrength by FlashlightController.maxStrength.collectAsState()
    val flashlightFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxTorchStrength) else 0f

    fun dragActions(type: IslandType, secondary: Boolean): IslandDragActions {
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
        return if (secondary) base else base.copy(onDown = {
            ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context)
        })
    }

    val mainPillModifier = Modifier
        .then(primaryPress.modifier)
        .islandDragGestures(
            activeDisplayType, isPillActive && !isExpanded, dragOffsetAnim, maxDragOffsetPx,
            density.density, dragActions(activeDisplayType, secondary = false),
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
                        progressFraction = if (presentation.compactOutline) animatedProgress else 0f,
                        cornerRadius = currentCornerRadius,
                        shape = mainShape,
                        strokeWidth = 0.75.dp,
                        trackColor = Color(0x30FFFFFF).copy(alpha = (48f / 255f) * contentAlpha),
                        progressColor = presentation.accent,
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
                            targetState = activeDisplayType,
                            animationSpec = LarpMotion.fade(200),
                            label = "compact_content_type",
                        ) { displayType ->
                            IslandCompactContent(
                                model = islandModel(displayType), media = mediaInfo, activity = notificationActivity,
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

    val targetLeftWidth = if (hasLeftBubble && displayLeftType != null) {
        if (isLandscape) secondaryDotSize else secondaryItemWidthDp(
            type = displayLeftType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = displayLeftType.activity?.let { rememberCompactActivityStatus(it) } ?: notificationActivityStatus,
        ).dp
    } else 0.dp

    val targetLeftHeight = if (hasLeftBubble && displayLeftType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayLeftType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = displayLeftType.activity?.let { rememberCompactActivityStatus(it) } ?: notificationActivityStatus,
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
    val targetRightWidth = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryDotSize else secondaryItemWidthDp(
            type = displayRightType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = displayRightType.activity?.let { rememberCompactActivityStatus(it) } ?: notificationActivityStatus,
        ).dp
    } else 0.dp

    val targetRightHeight = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayRightType.type,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = displayRightType.activity?.let { rememberCompactActivityStatus(it) } ?: notificationActivityStatus,
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

        val statusArrivalOffset = rememberStatusArrivalOffset(type.activity, !isExpanded)
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
                    density.density, dragActions(type.type, secondary = true),
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
                .then(if (type.type == IslandType.FLASHLIGHT && showFlashlightOutline) {
                    Modifier.islandFluidProgressBorder(
                        progressFraction = flashlightFraction,
                        cornerRadius = bubbleCornerRadius,
                        shape = bubbleShape,
                        strokeWidth = 0.75.dp,
                        progressColor = Color.White,
                    )
                } else Modifier)
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
                notificationActivity = type.activity ?: notificationActivity,
                mediaInfo = mediaInfo,
                notificationActivityStatus = type.activity?.let { rememberCompactActivityStatus(it) } ?: notificationActivityStatus,
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

@Composable
internal fun SecondaryDotContent(
    type: IslandType,
    bubbleWidth: Dp,
    bubbleHeight: Dp,
    bubbleThickness: Dp,
    isMiniPill: Boolean,
    isLandscape: Boolean,
    beforeMain: Boolean,
    bubbleStyle: OverlayPreferences.BubbleStyle,
    notificationActivity: NotificationActivityInfo?,
    mediaInfo: MediaTrackInfo,
    notificationActivityStatus: String?,
    flashlightStatus: String,
    artworkAnchor: ArtworkAnchor? = null,
) {
    val flashlightAccent = flashlightAccentColor()
    val mediaAccentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val artworkGlow = if (type == IslandType.MEDIA) albumArtGlowColor(mediaInfo) else Color.Transparent
    val bubbleIconSize = (bubbleThickness - 8.dp).coerceIn(12.dp, 20.dp)
    val extraSpan = if (isLandscape) (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp) else (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
    val isPillShaped = isMiniPill && extraSpan > 4.dp
    val pillFraction = if (isMiniPill) (extraSpan / 12.dp).coerceIn(0f, 1f) else 0f
    val mediaArtSize = androidx.compose.ui.unit.lerp(
        (bubbleThickness - 3.dp).coerceAtLeast(bubbleIconSize),
        bubbleIconSize,
        pillFraction,
    )
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val outerPadding = 6.dp
    val innerPadding = if (grouped) 4.dp else outerPadding
    val contentShift = if (grouped && !isPillShaped) 1.5.dp else 0.dp
    val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "dot_torch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = LarpMotion.timing(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    val leftContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                if (notificationActivity != null) {
                    ActivityIcon(
                        activity = notificationActivity,
                        modifier = Modifier.size(bubbleIconSize),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Ongoing activity",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(bubbleIconSize),
                    )
                }
            }
            IslandType.MEDIA -> {
                if (mediaInfo.albumArt != null) {
                    Image(
                        bitmap = mediaInfo.albumArt.asImageBitmap(),
                        contentDescription = "Music Artwork",
                        modifier = Modifier
                            .size(mediaArtSize)
                            .albumArtGlow(artworkGlow, artworkAnchor)
                            .clip(CircleShape)
                            .artworkTile(artworkAnchor, ArtworkStyle(CircleShape)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Music Active",
                        tint = mediaAccentColor.copy(alpha = 0.9f),
                        modifier = Modifier.size(bubbleIconSize),
                    )
                }
            }
            IslandType.FLASHLIGHT -> {
                Icon(
                    imageVector = Icons.Default.FlashlightOn,
                    contentDescription = "Flashlight Active",
                    tint = flashlightAccent.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(bubbleIconSize),
                )
            }
        }
    }

    val rightContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                val actStatus = notificationActivityStatus ?: ""
                val isPercentage = actStatus.contains('%')
                Text(
                    text = formatCompactStatus(actStatus),
                    color = Color.White,
                    fontSize = if (isPercentage) 10.sp else 11.sp,
                    fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            IslandType.MEDIA -> {
                EqualizerWaveform(
                    mediaInfo = mediaInfo,
                    maxHeightDp = 11.5f,
                    barCount = 3,
                    barWidth = 2.5.dp,
                    barSpacing = 1.5.dp,
                )
            }
            IslandType.FLASHLIGHT -> {
                val isPercentage = flashlightStatus.contains('%')
                Text(
                    text = formatCompactStatus(flashlightStatus),
                    color = flashlightAccent,
                    fontSize = if (isPercentage) 10.sp else 11.sp,
                    fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    val leadingSlotSize = if (type == IslandType.MEDIA && mediaInfo.albumArt != null) mediaArtSize else bubbleIconSize
    val leadingScale = if (type == IslandType.MEDIA && mediaInfo.albumArt != null) 1f else iconScale

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        top = if (beforeMain) outerPadding else innerPadding,
                        bottom = if (beforeMain) innerPadding else outerPadding,
                    ) else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(leadingSlotSize)
                    .scale(leadingScale),
                contentAlignment = Alignment.Center,
            ) {
                leftContent()
            }
            if (isPillShaped) {
                Box(
                    modifier = Modifier.padding(bottom = if (grouped && beforeMain) 0.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        start = if (beforeMain) outerPadding else innerPadding,
                        end = if (beforeMain) innerPadding else outerPadding,
                    ) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(leadingSlotSize)
                    .scale(leadingScale),
                contentAlignment = Alignment.Center,
            ) {
                leftContent()
            }
            if (isPillShaped) {
                Box(
                    modifier = Modifier.padding(end = if (grouped && beforeMain) 0.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IslandSurfaceOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    notificationActivity: NotificationActivityInfo?,
    expandedType: IslandType,
    fromTinyDot: Boolean,
    isExpanded: Boolean,
    sourceFrame: CompactIslandFrame,
    returnFrame: CompactIslandFrame?,
    onCollapse: () -> Unit,
    onFirstFrameDrawn: () -> Unit,
    onExitFinished: () -> Unit,
    compactPressScale: Float,
    compactStretch: Float,
    modifier: Modifier = Modifier,
) {
    val statusArrivalOffset = rememberStatusArrivalOffset(notificationActivity, !isExpanded && expandedType == IslandType.NOTIFICATION_ACTIVITY)
    val morphProgress = remember { Animatable(0f) }
    var collapseFrom by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            collapseFrom = null
            // Compose both artwork anchors before handing the compact surface to the morph.
            withFrameNanos { }
            onFirstFrameDrawn()
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.79f,
                    stiffness = 320f,
                    visibilityThreshold = 0.002f,
                ),
            )
        } else {
            collapseFrom = morphProgress.value.coerceIn(0f, 1f)
            morphProgress.animateTo(
                targetValue = 0f,
                animationSpec = LarpMotion.quickSpring(dampingRatio = 1f, stiffness = 440f, visibilityThreshold = 0.002f),
            )
            onExitFinished()
        }
    }

    val expansion = morphProgress.value.coerceIn(0f, 1f)
    val settleScale = 1f + (morphProgress.value - 1f).coerceIn(0f, 0.025f)
    val handoff = collapseFrom?.let { start ->
        if (start <= 0f) 1f else motionFraction(start - expansion, 0f, start)
    } ?: 0f
    val compactFrame = returnFrame?.let { sourceFrame.toward(it, handoff) } ?: sourceFrame
    val startBounds = compactFrame.bounds
    val hasBubbleBefore = compactFrame.hasBubbleBefore
    val hasBubbleAfter = compactFrame.hasBubbleAfter

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val drawWindowWidthPx = LocalView.current.width.takeIf { it > 0 }?.toFloat()
        ?: with(density) { screenWidthDp.toPx() }

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val compactCornerRadius = cameraGeometry.cornerRadius.dp
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val secondaryBubbleThickness = secondaryBubbleThicknessDp(compactPillThickness.value, smallerBubbles).dp
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    // The expanded card starts 14dp below the screen top. Keep its content below
    // the physical camera, including a small gap around the cutout.
    val expandedCutoutClearanceDp = if (isLandscape) 0.dp else
        (cutoutCenterYDp + cutoutDiameterDp / 2f + 8.dp - 14.dp).coerceAtLeast(0.dp)

    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val pulseEntireIsland by OverlayPreferences.pulseEntireIslandEnabledFlow.collectAsState()
    val pulseAmount by OverlayPreferences.pulseAmountFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isBpmResolved by BpmDetector.isResolved.collectAsState()
    val bpmPulseState = rememberBpmPulse(
        bpm = if (isBpmResolved) currentBpm else null,
        currentPositionMs = mediaInfo.positionMs,
        isPlaying = mediaInfo.isPlaying,
        enabled = pulseEntireIsland && expandedType == IslandType.MEDIA,
    )
    val notificationActivityStatus = notificationActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()
    val announcementActive = compactFrame.announcing
    val presentations = rememberIslandPresentations(
        mediaInfo, notificationActivityStatus, flashlightStatus,
        getFlashlightStrengthFraction(FlashlightController.torchStrength.collectAsState().value,
            FlashlightController.maxStrength.collectAsState().value),
        compactPillThickness, announcementActive,
    )
    val presentation = presentations.getValue(expandedType)
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val usesVerticalMediaLayout = expandedType == IslandType.MEDIA &&
            expandedPlayerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE
    val hasExtraMediaButtons = expandedElementVisibility.appActions && mediaInfo.sessionActions.any {
        it.iconResourceId != 0 && it.iconPackageName != null
    }

    val topMarginDp = (cutoutCenterYDp - compactPillThickness / 2f).coerceAtLeast(0.dp)
    val drawWindowWidthDp = with(density) { drawWindowWidthPx.toDp() }
    val cutoutCenterXDp = with(density) { cutoutInfo.centerX.toDp() }
    val horizontalMarginDp = if (isLandscape) {
        islandSideMargin(cutoutCenterXDp.value, drawWindowWidthDp.value, cutoutDiameterDp.value / 2f).dp
    } else topMarginDp.coerceAtLeast(14.dp)
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()
    val flashlightExpandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val wideFlashlight = flashlightExpandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER
    val activitySize = expandedActivitySize(
        notificationActivity,
        maxWidth = (if (isLandscape) drawWindowWidthDp - horizontalMarginDp - 14.dp else screenWidthDp - 32.dp).coerceAtLeast(80.dp),
        maxHeight = (screenHeightDp - 36.dp).coerceAtLeast(80.dp),
        topInset = expandedCutoutClearanceDp,
    )
    val cardWidth = when (expandedType) {
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            if (isLandscape) minOf(drawWindowWidthDp - horizontalMarginDp - 14.dp, 420.dp)
            else screenWidthDp - (horizontalMarginDp * 2)
        } else minOf(screenWidthDp - 96.dp, 220.dp)
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.width
        IslandType.MEDIA -> if (usesVerticalMediaLayout) {
            minOf(screenWidthDp - 88.dp, 248.dp)
        } else {
            if (isLandscape) minOf(drawWindowWidthDp - horizontalMarginDp - 14.dp, 420.dp)
            else screenWidthDp - (horizontalMarginDp * 2)
        }
    }
    val cardHeight = when (expandedType) {
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            maxOf(cutoutDiameterDp, expandedCutoutClearanceDp) + 64.dp
        } else if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.height
        IslandType.MEDIA -> if (usesVerticalMediaLayout) {
            val hasAlbumArt = showExpandedAlbumArt && mediaInfo.albumArt != null
            val materialHeight = when {
                hasAlbumArt && hasExtraMediaButtons -> 430.dp
                hasAlbumArt -> 350.dp
                hasExtraMediaButtons -> 290.dp
                else -> 210.dp
            }
            if (isLandscape) minOf(screenHeightDp - 36.dp, materialHeight) else materialHeight
        } else {
            220.dp
        }
    }
    val mediaCardHorizontalMarginDp = (screenWidthDp - cardWidth) / 2f

    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(cardWidth, cardHeight) / 2f)

    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val startWidth = with(density) { startBounds.width.toDp() }
    val startHeight = with(density) { startBounds.height.toDp() }
    val startOffsetX = with(density) { (startBounds.center.x - drawWindowWidthPx / 2f).toDp() }
    val startOffsetY = with(density) { startBounds.top.toDp() } - 14.dp
    val startCorner = minOf(startWidth, startHeight) / 2f

    val artwork = mediaInfo.albumArt?.takeIf { expandedType == IslandType.MEDIA && showExpandedAlbumArt }
    val artworkMotion = remember(fromTinyDot, artwork != null) {
        if (artwork != null) IslandArtworkMotion { morphProgress.value } else null
    }
    val expandedWidth by animateDpAsState(cardWidth, LarpMotion.resize(), label = "expanded_width")
    val expandedHeight by animateDpAsState(cardHeight, LarpMotion.resize(), label = "expanded_height")
    val currentWidth = androidx.compose.ui.unit.lerp(startWidth, expandedWidth, expansion)
    val currentHeight = androidx.compose.ui.unit.lerp(startHeight, expandedHeight, expansion)
    // Expand inward from the camera's edge, keeping the panel clear of the hole punch.
    val panelPlacement = islandPanelPlacement(
        drawWindowWidthDp.value, screenHeightDp.value, cutoutCenterXDp.value,
        cutoutCenterYDp.value, cutoutDiameterDp.value / 2f, expandedWidth.value, expandedHeight.value, isLandscape,
    )
    val expandedOffsetX = panelPlacement.offsetX.dp
    val expandedOffsetY = panelPlacement.top.dp - 14.dp
    val currentOffsetX = androidx.compose.ui.unit.lerp(startOffsetX, expandedOffsetX, expansion)
    val currentOffsetY = androidx.compose.ui.unit.lerp(startOffsetY, expandedOffsetY, expansion)
    val currentCornerRadius = androidx.compose.ui.unit.lerp(startCorner, expandedCornerRadiusDp, expansion)
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val useRoundedCorners = !squircleEnabled || blurEnabled
    val cornerMix = if (useRoundedCorners) motionFraction(expansion, 0f, 0.12f) else expansion
    val innerCorner = androidx.compose.ui.unit.lerp(
        (startCorner * 0.3f).coerceAtMost(6.dp),
        currentCornerRadius,
        cornerMix,
    )
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val beforeCorner = if (grouped && hasBubbleBefore) innerCorner else currentCornerRadius
    val afterCorner = if (grouped && hasBubbleAfter) innerCorner else currentCornerRadius
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val blurAvailable = blurEnabled && rememberBackdropBlurStatus() == BackdropBlurStatus.AVAILABLE
    val blurReveal = motionFraction(expansion, 0.12f, 0.88f)
    val useBackdropBlur = blurAvailable && blurReveal > 0f
    // Native backdrop blur can only follow evenly rounded corners.
    val containerShape = if (useRoundedCorners && (!grouped || expansion >= 0.12f)) {
        RoundedCornerShape(currentCornerRadius)
    } else squircleShape(
        radiusPx = with(density) { currentCornerRadius.toPx() },
        curvatureFactor = if (useRoundedCorners) 0.5522848f else 0.5522848f + 0.2477152f * expansion,
        topLeftRadiusPx = with(density) { beforeCorner.toPx() },
        topRightRadiusPx = with(density) { (if (isLandscape) beforeCorner else afterCorner).toPx() },
        bottomRightRadiusPx = with(density) { afterCorner.toPx() },
        bottomLeftRadiusPx = with(density) { (if (isLandscape) afterCorner else beforeCorner).toPx() },
    )

    val compactAlpha = 1f - motionFraction(expansion, 0.04f, 0.54f)
    val expandedAlpha = motionFraction(expansion, 0.23f, 0.85f)
    val expandedContentScale = 0.96f + (0.04f * expansion)
    val expandedContentOffsetY = 10.dp * (1f - expansion)
    val contentBlur = rememberMotionBlur()

    val progressFraction = presentation.progress

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = LinearEasing),
        label = "expanded_progress",
    )

    val surfacePress = rememberIslandPressFeedback(expandedType, isExpanded)

    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val backgroundAlpha = islandBackgroundAlpha(backgroundOpacity, motionFraction(expansion, 0.08f, 0.94f))

    val currentElevation = androidx.compose.ui.unit.lerp(4.dp, 12.dp, expansion)
    val outlineMix = motionFraction(expansion, 0.2f, 0.75f)
    val compactOutline = presentation.compactOutline && (!fromTinyDot || expandedType != IslandType.MEDIA)
    val outlineAlpha = (if (compactOutline) 1f - outlineMix else 0f) +
        (if (presentation.expandedOutline) outlineMix else 0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isExpanded) Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onCollapse() })
            } else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .absoluteOffset(x = currentOffsetX, y = currentOffsetY)
                .size(width = currentWidth, height = currentHeight)
                .then(surfacePress.modifier)
                .graphicsLayer {
                    translationX = statusArrivalOffset
                    val pressCarry = collapseFrom?.let { start ->
                        if (start <= 0f) 0f else (1f - start) * (expansion / start)
                    } ?: (1f - expansion)
                    val pressScale = maxOf(surfacePress.scale, 1f + (compactPressScale - 1f) * pressCarry)
                    val stretch = compactStretch * pressCarry
                    val pulse = bpmPulseState.value
                    val islandPulseScale = if (pulseEntireIsland && mediaInfo.isPlaying && expandedType == IslandType.MEDIA && pulse > 0f) {
                        val pulseMagnitude = 0.035f - (0.015f * expansion)
                        1f + (pulseMagnitude * pulse * pulseAmount)
                    } else {
                        1f
                    }
                    val totalScaleX = pressScale * (1f + kotlin.math.abs(stretch) * if (fromTinyDot) 0.22f else 0.18f) * islandPulseScale
                    val totalScaleY = pressScale * islandPulseScale
                    // Overshoot the complete clipped panel so artwork, blur and controls spring together.
                    scaleX = totalScaleX * settleScale
                    scaleY = totalScaleY * settleScale
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        settleScale > 1f && !isLandscape -> TransformOrigin(0.5f, 0f)
                        settleScale > 1f -> TransformOrigin(if (cutoutInfo.centerX < drawWindowWidthPx / 2f) 0f else 1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    alpha = if (isExpanded || returnFrame != null) 1f else motionFraction(expansion, 0f, 0.3f)
                }
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = if (outlineAlpha > 0f) animatedProgress else 0f,
                        cornerRadius = currentCornerRadius,
                        shape = containerShape,
                        strokeWidth = 0.75.dp,
                        trackColor = Color(0x30FFFFFF),
                        progressColor = presentation.accent.copy(alpha = presentation.accent.alpha * outlineAlpha),
                    )
                )
                .shadow(currentElevation, containerShape, clip = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clip(containerShape),
                contentAlignment = Alignment.Center,
            ) {
                if (useBackdropBlur) {
                    IslandBackdropBlur(
                        radiusPx = with(density) { blurRadius.dp.toPx() } * blurReveal,
                        cornerRadiusPx = with(density) { currentCornerRadius.toPx() },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = backgroundAlpha)))
                if (compactAlpha > 0.01f || (artworkMotion != null && !isExpanded)) {
                    Box(
                        modifier = Modifier
                            .requiredSize(startWidth, startHeight)
                            .graphicsLayer {
                                alpha = compactAlpha
                                renderEffect = contentBlur.motionBlur(1f - compactAlpha)
                            }
                            .artworkContainer(artworkMotion?.compact),
                    ) {
                        if (fromTinyDot) {
                            SecondaryDotContent(
                                type = expandedType,
                                bubbleWidth = startWidth,
                                bubbleHeight = startHeight,
                                bubbleThickness = secondaryBubbleThickness,
                                isMiniPill = showDotRightSideInfo,
                                isLandscape = isLandscape,
                                beforeMain = hasBubbleAfter,
                                bubbleStyle = bubbleStyle,
                                notificationActivity = notificationActivity,
                                mediaInfo = mediaInfo,
                                notificationActivityStatus = notificationActivityStatus,
                                flashlightStatus = flashlightStatus,
                                artworkAnchor = artworkMotion?.compact,
                            )
                        } else {
                            IslandCompactContent(
                                model = IslandModel(expandedType, {}, presentation = presentations.getValue(expandedType)),
                                media = mediaInfo, activity = notificationActivity,
                                diameter = cutoutDiameterDp, landscape = isLandscape,
                                showTitle = showMinimizedTitle,
                                announcing = announcementActive, trailingWingFraction = compactFrame.trailingFraction,
                                artworkAnchor = artworkMotion?.compact,
                                onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                            )
                        }
                    }
                }

                if (expandedAlpha > 0.01f || (artworkMotion != null && isExpanded)) {
                    Box(
                        modifier = Modifier
                            .requiredSize(expandedWidth, expandedHeight)
                            .graphicsLayer {
                                alpha = expandedAlpha
                                renderEffect = contentBlur.motionBlur(1f - expandedAlpha)
                                scaleX = expandedContentScale
                                scaleY = expandedContentScale
                                translationY = with(density) { expandedContentOffsetY.toPx() }
                            }
                            .artworkContainer(artworkMotion?.expanded),
                    ) {
                        if (expandedType != IslandType.MEDIA) CameraCover(
                            style = cameraCoverStyle,
                            centerX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                            centerY = cutoutCenterYDp - panelPlacement.top.dp,
                            diameter = cutoutDiameterDp,
                        )
                        when (expandedType) {
                            IslandType.NOTIFICATION_ACTIVITY -> {
                                if (notificationActivity != null) {
                                    ExpandedNotificationActivityContent(
                                        activity = notificationActivity,
                                        onCollapse = onCollapse,
                                        topContentInset = expandedCutoutClearanceDp,
                                    )
                                }
                            }
                            IslandType.FLASHLIGHT -> {
                                ExpandedFlashlightContent(
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    isExpanded = isExpanded,
                                    onCollapse = onCollapse,
                                    cutoutInfo = cutoutInfo,
                                    cardHorizontalMarginDp = horizontalMarginDp,
                                    topContentInset = expandedCutoutClearanceDp,
                                )
                            }
                            IslandType.MEDIA -> {
                                ExpandedIslandContent(
                                    mediaInfo = mediaInfo,
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    isExpanded = isExpanded,
                                    onCollapse = onCollapse,
                                    cutoutInfo = cutoutInfo,
                                    cardHorizontalMarginDp = if (usesVerticalMediaLayout) mediaCardHorizontalMarginDp else horizontalMarginDp,
                                    artworkAnchor = artworkMotion?.expanded,
                                )
                            }
                        }

                    }
                }
                if (artworkMotion != null && artwork != null) {
                    TravelingArtwork(
                        bitmap = remember(artwork) { artwork.asImageBitmap() },
                        motion = artworkMotion,
                        glowColor = albumArtGlowColor(mediaInfo),
                        isLandscape = isLandscape,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
