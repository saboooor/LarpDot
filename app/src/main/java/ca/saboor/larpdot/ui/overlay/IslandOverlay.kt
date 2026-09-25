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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun bubbleGap(style: OverlayPreferences.BubbleStyle): Dp =
    if (style == OverlayPreferences.BubbleStyle.MATERIAL_3) 4.dp else 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    notificationActivity: NotificationActivityInfo? = null,
    isFlashlightOn: Boolean = false,
    isExpanded: Boolean = false,
    hideExpandedSource: Boolean = isExpanded,
    renderMainPill: Boolean = true,
    renderSecondarySurface: (IslandType) -> Boolean = { true },
    fromTinyDot: Boolean = false,
    expandedStackType: IslandType = IslandType.MEDIA,
    onExpand: (IslandType, Boolean, Rect) -> Unit,
    onCompactBoundsChanged: (Rect) -> Unit = {},
    onCompactScaleChanged: (Float) -> Unit = {},
    onCompactStretchChanged: (Float) -> Unit = {},
    onSecondaryBoundsChanged: (IslandType, Rect) -> Unit = { _, _ -> },
    onSecondaryScaleChanged: (IslandType, Float) -> Unit = { _, _ -> },
    onSecondaryStretchChanged: (IslandType, Float) -> Unit = { _, _ -> },
    onSecondaryAlphaChanged: (IslandType, Float) -> Unit = { _, _ -> },
    onFlashlightToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaAccentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val density = LocalDensity.current
    val mainPillBounds = remember { arrayOfNulls<Rect>(1) }
    val secondaryBounds = remember { mutableMapOf<IslandType, Rect>() }
    fun requestExpand(type: IslandType, fromTinyDot: Boolean) {
        val bounds = if (fromTinyDot) secondaryBounds[type] else mainPillBounds[0]
        bounds?.let { onExpand(type, fromTinyDot, it) }
    }
    val hapticFeedback = LocalHapticFeedback.current
    val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()

    val isMusicActiveGlobal by MediaPlaybackState.isMusicActive.collectAsState()
    val isMusicActive = mediaInfo.hasMedia && isMusicActiveGlobal
    val isFlashlightActive = isFlashlightOn
    val isNotificationActivityActive = notificationActivity != null
    val isPillActive = isMusicActive || isFlashlightActive || isNotificationActivityActive

    var isMediaSessionActive by remember { mutableStateOf(isMusicActive) }
    LaunchedEffect(isMusicActive) {
        if (isMusicActive) {
            isMediaSessionActive = true
        } else {
            delay(320L)
            isMediaSessionActive = false
        }
    }

    val stack = remember(isMusicActive, isFlashlightActive, isNotificationActivityActive, notificationActivity) {
        builtInIslandStack(
            music = isMusicActive,
            flashlight = isFlashlightActive,
            activity = isNotificationActivityActive,
            activityHasRightWing = hasCompactStatusContent(notificationActivity),
            activityKind = notificationActivity?.kind,
        )
    }

    val primaryType = stack.primary?.content
    val secondaryTypes = stack.secondary.map { it.content }

    // When there are 2 tiny dots, dot 0 is placed on the left and dot 1 on the right.
    // When there is 1 tiny dot, it is placed on the right.
    val leftSecondaryType = if (secondaryTypes.size == 2) secondaryTypes[0] else null
    val rightSecondaryType = if (secondaryTypes.size == 2) secondaryTypes[1] else secondaryTypes.firstOrNull()

    var lastLeftType by remember { mutableStateOf<IslandType?>(null) }
    var lastRightType by remember { mutableStateOf<IslandType?>(null) }
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

    val cutoutDiameterDp = with(density) {
        if (cutoutInfo.radiusPx > 0f) (cutoutInfo.radiusPx * 2f).toDp() else 24.dp
    }.coerceIn(16.dp, 36.dp)
    val outlineAllowanceDp = 2.dp
    val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
    val compactCornerRadius = compactPillThickness / 2f
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val secondaryDotSize = secondaryBubbleThicknessDp(compactPillThickness.value, smallerBubbles).dp

    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val isAnnouncing = isSongAnnouncement && showSongAnnouncement && isMusicActive

    val nestedArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
    val nestedActiveExtraDp = compactPillThickness + nestedArtSize
    val blendedActiveExtraDp = compactPillThickness * 3

    val notificationActivityStatus = notificationActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()
    val activityExtraDp = rememberCompactStatusExtraDp(notificationActivityStatus)
    val flashlightExtraDp = rememberCompactStatusExtraDp(flashlightStatus)

    val activeExtraDp = when {
        activeDisplayType == IslandType.NOTIFICATION_ACTIVITY -> activityExtraDp
        activeDisplayType == IslandType.FLASHLIGHT -> flashlightExtraDp
        isMusicActive -> when (minimizedStyle) {
            OverlayPreferences.AlbumArtStyle.BLENDED -> blendedActiveExtraDp
            OverlayPreferences.AlbumArtStyle.NESTED -> nestedActiveExtraDp
            else -> 60.dp
        }
        lastDisplayType == IslandType.NOTIFICATION_ACTIVITY -> activityExtraDp
        lastDisplayType == IslandType.FLASHLIGHT -> flashlightExtraDp
        else -> when (minimizedStyle) {
            OverlayPreferences.AlbumArtStyle.BLENDED -> blendedActiveExtraDp
            OverlayPreferences.AlbumArtStyle.NESTED -> nestedActiveExtraDp
            else -> 60.dp
        }
    }
    val titleExtraDp = if (showMinimizedTitle && isMusicActive) 180.dp else 0.dp
    val announcementExtraDp = if (isAnnouncing) 210.dp else 0.dp
    val compactWidth = if (isLandscape) compactPillThickness else (cutoutDiameterDp + maxOf(activeExtraDp, titleExtraDp, announcementExtraDp))
    val compactHeight = if (isLandscape) (cutoutDiameterDp + maxOf(activeExtraDp, if (isAnnouncing) 140.dp else 0.dp)) else compactPillThickness

    val currentCornerRadius by animateDpAsState(
        targetValue = compactCornerRadius,
        animationSpec = tween(durationMillis = 280, easing = if (isPillActive) MtIslandEnterEasing else MtIslandExitEasing),
        label = "compact_corner",
    )
    val mainShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
        groupedIslandShape(currentCornerRadius, leftSecondaryType != null, rightSecondaryType != null, isLandscape)
    } else RoundedCornerShape(currentCornerRadius)

    val targetPillColor = if (isPillActive) Color.Black else Color.Transparent
    val pillColor by animateColorAsState(
        targetValue = targetPillColor,
        animationSpec = tween(durationMillis = 280, easing = if (isPillActive) MtIslandEnterEasing else MtIslandExitEasing),
        label = "pill_color",
    )

    val currentWidth by animateDpAsState(
        targetValue = if (isPillActive) compactWidth else cutoutDiameterDp,
        animationSpec = tween(durationMillis = 280, easing = if (isPillActive) MtIslandEnterEasing else MtIslandExitEasing),
        label = "compact_width",
    )
    val currentHeight by animateDpAsState(
        targetValue = if (isPillActive) compactHeight else cutoutDiameterDp,
        animationSpec = tween(durationMillis = 280, easing = if (isPillActive) MtIslandEnterEasing else MtIslandExitEasing),
        label = "compact_height",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isPillActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPillActive) 220 else 180),
        label = "compact_content_alpha",
    )

    var isIslandPressed by remember { mutableStateOf(false) }
    val islandScale by animateFloatAsState(
        targetValue = if (isIslandPressed) 1.08f else 1f,
        animationSpec = if (isIslandPressed) {
            spring(dampingRatio = 0.72f, stiffness = 450f)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 360f)
        },
        label = "compact_scale",
    )
    SideEffect { onCompactScaleChanged(islandScale) }

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "compact_progress",
    )

    val pillVisibilityAlpha = if (hideExpandedSource && !fromTinyDot) 0f else 1f

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetAnim = remember { Animatable(0f) }
    val maxDragOffsetPx = with(density) { 8.dp.toPx() }
    // The compact placeholder handles gestures; the separate surface draws the visible stretch.
    val compactStretch = (dragOffsetAnim.value / maxDragOffsetPx).coerceIn(-1f, 1f)
    SideEffect { onCompactStretchChanged(compactStretch) }

    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val showFlashlightOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxTorchStrength by FlashlightController.maxStrength.collectAsState()
    val flashlightFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxTorchStrength) else 0f

    val compactHPx = with(density) { compactHeight.toPx() }
    val topAnchorPx = (cutoutInfo.centerY - (compactHPx / 2f)).coerceAtLeast(0f)
    val pillTopOffsetDp = with(density) { topAnchorPx.toDp() }.coerceAtLeast(0.dp)

    val mainPillModifier = Modifier
        .pointerInput(isPillActive, isExpanded, mediaInfo.hasMedia, activeDisplayType) {
            if (isPillActive && !isExpanded) {
                var totalDragX = 0f
                var totalDragY = 0f
                var hasTriggered = false
                var adjustingFlashlight = false
                var appliedFlashlightLevel = 1
                val swipeThresholdPx = with(density) { 28.dp.toPx() }
                val flashlightSwipe = FlashlightSwipeRepeater(
                    scope = coroutineScope,
                    nearDistancePx = with(density) { 24.dp.toPx() },
                    maxDistancePx = with(density) { 120.dp.toPx() },
                ) { direction, levels ->
                    val newLevel = (appliedFlashlightLevel + direction * levels)
                        .coerceIn(1, FlashlightController.maxStrength.value)
                    if (newLevel != appliedFlashlightLevel) {
                        appliedFlashlightLevel = newLevel
                        FlashlightController.setStrength(newLevel)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        true
                    } else {
                        false
                    }
                }

                fun finishFlashlightSwipe() {
                    if (adjustingFlashlight) {
                        flashlightSwipe.stop()
                        FlashlightController.flushStrength(appliedFlashlightLevel)
                        FlashlightController.setUserInteracting(false)
                        adjustingFlashlight = false
                    }
                }

                try {
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                            hasTriggered = false
                            appliedFlashlightLevel = FlashlightController.torchStrength.value
                        },
                        onDragEnd = {
                            finishFlashlightSwipe()
                            totalDragX = 0f
                            totalDragY = 0f
                            hasTriggered = false
                            coroutineScope.launch {
                                dragOffsetAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                                )
                            }
                        },
                        onDragCancel = {
                            finishFlashlightSwipe()
                            totalDragX = 0f
                            totalDragY = 0f
                            hasTriggered = false
                            coroutineScope.launch {
                                dragOffsetAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                val damped = (totalDragX * 0.10f).coerceIn(-maxDragOffsetPx, maxDragOffsetPx)
                                coroutineScope.launch {
                                    dragOffsetAnim.snapTo(damped)
                                }
                                if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                    isIslandPressed = false
                                    change.consume()
                                }
                            }

                            if (adjustingFlashlight) {
                                change.consume()
                                flashlightSwipe.update(totalDragX)
                            } else if (!hasTriggered) {
                                if (totalDragY > 20f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.3f) {
                                    hasTriggered = true
                                    change.consume()
                                    ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context)
                                } else if (activeDisplayType == IslandType.FLASHLIGHT &&
                                    FlashlightController.maxStrength.value > 1 &&
                                    kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f &&
                                    totalDragX != 0f
                                ) {
                                    adjustingFlashlight = true
                                    isIslandPressed = false
                                    FlashlightController.setUserInteracting(true)
                                    change.consume()
                                    flashlightSwipe.start(totalDragX)
                                } else if (activeDisplayType == IslandType.MEDIA && isMusicActive && totalDragX > swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                    hasTriggered = true
                                    change.consume()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaPlaybackState.skipNext()
                                } else if (activeDisplayType == IslandType.MEDIA && isMusicActive && totalDragX < -swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                    hasTriggered = true
                                    change.consume()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaPlaybackState.skipPrevious()
                                }
                            }
                        }
                    )
                } finally {
                    finishFlashlightSwipe()
                    coroutineScope.launch {
                        dragOffsetAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                        )
                    }
                }
            }
        }
        .pointerInput(isPillActive, isExpanded, activeDisplayType) {
            if (isPillActive && !isExpanded) {
                val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                detectTapGestures(
                    onPress = {
                        MediaPlaybackState.dismissSongAnnouncement()
                        isIslandPressed = true
                        try {
                            val longPressJob = coroutineScope.launch {
                                delay(300L)
                                if (isIslandPressed) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when (activeDisplayType) {
                                        IslandType.MEDIA -> {
                                            if (tapToExpand) {
                                                openPlayerApp(context, mediaInfo)
                                            } else {
                                                requestExpand(IslandType.MEDIA, false)
                                            }
                                        }
                                        IslandType.FLASHLIGHT -> {
                                            if (tapToExpand) {
                                                onFlashlightToggle()
                                            } else {
                                                requestExpand(IslandType.FLASHLIGHT, false)
                                            }
                                        }
                                        IslandType.NOTIFICATION_ACTIVITY -> {
                                            if (tapToExpand) {
                                                notificationActivity?.let(NotificationActivityState::open)
                                            } else {
                                                requestExpand(IslandType.NOTIFICATION_ACTIVITY, false)
                                            }
                                        }
                                    }
                                    tryAwaitRelease()
                                }
                            }
                            tryAwaitRelease()
                            longPressJob.cancel()
                        } finally {
                            isIslandPressed = false
                        }
                    },
                    onTap = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (activeDisplayType) {
                            IslandType.MEDIA -> {
                                if (tapToExpand) {
                                    requestExpand(IslandType.MEDIA, false)
                                } else {
                                    openPlayerApp(context, mediaInfo)
                                }
                            }
                            IslandType.FLASHLIGHT -> {
                                if (tapToExpand) {
                                    requestExpand(IslandType.FLASHLIGHT, false)
                                } else {
                                    onFlashlightToggle()
                                }
                            }
                            IslandType.NOTIFICATION_ACTIVITY -> {
                                if (tapToExpand) {
                                    requestExpand(IslandType.NOTIFICATION_ACTIVITY, false)
                                } else {
                                    notificationActivity?.let(NotificationActivityState::open)
                                }
                            }
                        }
                    },
                )
            }
        }

    val mainPill = @Composable {
        if (!renderMainPill && isPillActive) {
            Box(
                Modifier
                    .width(currentWidth)
                    .height(currentHeight)
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        mainPillBounds[0] = bounds
                        onCompactBoundsChanged(bounds)
                    },
            )
        } else Surface(
            modifier = Modifier
                .width(currentWidth)
                .height(currentHeight)
                .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    mainPillBounds[0] = bounds
                    if (isPillActive) onCompactBoundsChanged(bounds)
                }
                .graphicsLayer {
                    alpha = pillVisibilityAlpha
                    val stretchMag = dragOffsetAnim.value / maxDragOffsetPx
                    val maxStretch = 0.18f
                    val isSwiping = kotlin.math.abs(stretchMag) > 0.01f
                    scaleX = (1f + kotlin.math.abs(stretchMag) * maxStretch) * islandScale
                    scaleY = islandScale
                    transformOrigin = if (isSwiping) {
                        if (stretchMag >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                    } else {
                        TransformOrigin.Center
                    }
                }
                .clip(mainShape)
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = when {
                            activeDisplayType == IslandType.MEDIA && isMusicActive && showProgressOutline -> animatedProgress
                            activeDisplayType == IslandType.FLASHLIGHT && showFlashlightOutline -> flashlightFraction
                            else -> 0f
                        },
                        cornerRadius = currentCornerRadius,
                        shape = mainShape,
                        strokeWidth = 0.75.dp,
                        trackColor = if (activeDisplayType == IslandType.FLASHLIGHT && !showFlashlightOutline) Color.Transparent
                            else Color(0x30FFFFFF).copy(alpha = (48f / 255f) * contentAlpha),
                        progressColor = if (activeDisplayType == IslandType.FLASHLIGHT) Color.White else mediaAccentColor,
                    )
                ),
            shape = mainShape,
            color = pillColor,
            shadowElevation = if (isExpanded) 12.dp else (if (isPillActive) 4.dp else 0.dp),
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
                            animationSpec = tween(durationMillis = 220),
                            label = "compact_content_type",
                        ) { displayType ->
                            when (displayType) {
                                IslandType.MEDIA -> {
                                    CompactIslandContent(
                                        mediaInfo = mediaInfo,
                                        cutoutDiameterDp = cutoutDiameterDp,
                                        onExpand = { requestExpand(IslandType.MEDIA, false) },
                                        showMinimizedTitle = showMinimizedTitle,
                                        isSongAnnouncement = isAnnouncing,
                                    )
                                }
                                IslandType.FLASHLIGHT -> {
                                    CompactFlashlightContent(
                                        cutoutDiameterDp = cutoutDiameterDp,
                                        onExpand = { requestExpand(IslandType.FLASHLIGHT, false) },
                                        onFlashlightToggle = onFlashlightToggle,
                                        isLandscape = isLandscape,
                                        status = flashlightStatus,
                                    )
                                }
                                IslandType.NOTIFICATION_ACTIVITY -> {
                                    if (notificationActivity != null) {
                                        CompactNotificationActivityContent(
                                            activity = notificationActivity,
                                            cutoutDiameterDp = cutoutDiameterDp,
                                            isLandscape = isLandscape,
                                            status = notificationActivityStatus ?: "",
                                        )
                                    }
                                }
                            }
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
            type = displayLeftType,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = notificationActivityStatus,
        ).dp
    } else 0.dp

    val targetLeftHeight = if (hasLeftBubble && displayLeftType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayLeftType,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = notificationActivityStatus,
        ).dp else secondaryDotSize
    } else 0.dp

    val leftBubbleWidth by animateDpAsState(
        targetValue = targetLeftWidth,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "left_bubble_width",
    )
    val leftBubbleHeight by animateDpAsState(
        targetValue = targetLeftHeight,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "left_bubble_height",
    )
    val leftBubbleAlpha by animateFloatAsState(
        targetValue = if (hasLeftBubble) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "left_bubble_alpha",
    )
    val leftBubbleScale by animateFloatAsState(
        targetValue = if (hasLeftBubble) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "left_bubble_scale",
    )
    val leftBubbleGap by animateDpAsState(
        targetValue = if (hasLeftBubble) bubbleGap(bubbleStyle) else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "left_bubble_gap",
    )

    val targetRightWidth = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryDotSize else secondaryItemWidthDp(
            type = displayRightType,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = notificationActivityStatus,
        ).dp
    } else 0.dp

    val targetRightHeight = if (hasRightBubble && displayRightType != null) {
        if (isLandscape) secondaryItemWidthDp(
            type = displayRightType,
            thicknessDp = secondaryDotSize.value,
            isMiniPill = showDotRightSideInfo,
            hasMedia = isMusicActive,
            flashlightStatus = flashlightStatus,
            activityStatus = notificationActivityStatus,
        ).dp else secondaryDotSize
    } else 0.dp

    val rightBubbleWidth by animateDpAsState(
        targetValue = targetRightWidth,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "right_bubble_width",
    )
    val rightBubbleHeight by animateDpAsState(
        targetValue = targetRightHeight,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "right_bubble_height",
    )
    val rightBubbleAlpha by animateFloatAsState(
        targetValue = if (hasRightBubble) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "right_bubble_alpha",
    )
    val rightBubbleScale by animateFloatAsState(
        targetValue = if (hasRightBubble) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "right_bubble_scale",
    )
    val rightBubbleGap by animateDpAsState(
        targetValue = if (hasRightBubble) bubbleGap(bubbleStyle) else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
        label = "right_bubble_gap",
    )

    @Composable
    fun SecondaryDotBubble(
        type: IslandType,
        beforeMain: Boolean,
        bubbleWidth: Dp,
        bubbleHeight: Dp,
        bubbleAlpha: Float,
        bubbleScale: Float,
        isActive: Boolean,
        isMiniPill: Boolean,
    ) {
        if ((bubbleWidth <= 0.5.dp && bubbleHeight <= 0.5.dp) || bubbleAlpha <= 0.01f) return

        var isPressed by remember { mutableStateOf(false) }
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 1.15f else 1f,
            animationSpec = spring(dampingRatio = 0.70f, stiffness = 500f),
            label = "dot_press_scale",
        )
        SideEffect { onSecondaryScaleChanged(type, pressScale * bubbleScale) }
        SideEffect { onSecondaryAlphaChanged(type, bubbleAlpha) }
        val stretchAnim = remember { Animatable(0f) }
        val maxDotDragPx = with(density) { 8.dp.toPx() }
        val dotStretch = (stretchAnim.value / maxDotDragPx).coerceIn(-1f, 1f)
        SideEffect { onSecondaryStretchChanged(type, dotStretch) }

        val isHidingDueToExpansion = fromTinyDot && hideExpandedSource && expandedStackType == type
        val effectiveAlpha = (if (isHidingDueToExpansion) 0f else pillVisibilityAlpha) * bubbleAlpha
        val bubbleCornerRadius = minOf(bubbleWidth, bubbleHeight) / 2f
        val bubbleShape = if (bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3) {
            groupedIslandShape(bubbleCornerRadius, !beforeMain, beforeMain, isLandscape)
        } else RoundedCornerShape(bubbleCornerRadius)

        val bubbleModifier = Modifier
                .size(width = bubbleWidth, height = bubbleHeight)
                .onGloballyPositioned {
                    val bounds = it.boundsInRoot()
                    secondaryBounds[type] = bounds
                    onSecondaryBoundsChanged(type, bounds)
                }
                .pointerInput(isExpanded, isActive, type) {
                    if (!isExpanded && isActive) {
                        var dragX = 0f
                        var dragY = 0f
                        var adjusting = false
                        var appliedLevel = 1
                        val flashlightSwipe = FlashlightSwipeRepeater(
                            scope = coroutineScope,
                            nearDistancePx = with(density) { 24.dp.toPx() },
                            maxDistancePx = with(density) { 120.dp.toPx() },
                        ) { direction, levels ->
                            val newLevel = (appliedLevel + direction * levels)
                                .coerceIn(1, FlashlightController.maxStrength.value)
                            if (newLevel != appliedLevel) {
                                appliedLevel = newLevel
                                FlashlightController.setStrength(newLevel)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                true
                            } else {
                                false
                            }
                        }

                        fun finishSwipe() {
                            if (adjusting) {
                                flashlightSwipe.stop()
                                FlashlightController.flushStrength(appliedLevel)
                                FlashlightController.setUserInteracting(false)
                                adjusting = false
                            }
                            coroutineScope.launch {
                                stretchAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                                )
                            }
                        }

                        try {
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                    appliedLevel = FlashlightController.torchStrength.value
                                },
                                onDragEnd = { finishSwipe() },
                                onDragCancel = { finishSwipe() },
                                onDrag = { change, dragAmount ->
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                    if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) {
                                        val damped = (dragX * 0.10f).coerceIn(-maxDotDragPx, maxDotDragPx)
                                        coroutineScope.launch { stretchAnim.snapTo(damped) }
                                        if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.3f) {
                                            isPressed = false
                                            change.consume()
                                        }
                                    }
                                    if (!adjusting && type == IslandType.FLASHLIGHT && FlashlightController.maxStrength.value > 1 &&
                                        kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.3f && dragX != 0f
                                    ) {
                                        adjusting = true
                                        isPressed = false
                                        FlashlightController.setUserInteracting(true)
                                        flashlightSwipe.start(dragX)
                                    }
                                    if (adjusting) {
                                        change.consume()
                                        flashlightSwipe.update(dragX)
                                    }
                                },
                            )
                        } finally {
                            finishSwipe()
                        }
                    }
                }
                .pointerInput(isExpanded, isActive, type) {
                    if (!isExpanded && isActive) {
                        val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    val job = coroutineScope.launch {
                                        delay(300L)
                                        if (isPressed) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            when (type) {
                                                IslandType.NOTIFICATION_ACTIVITY -> {
                                                    requestExpand(IslandType.NOTIFICATION_ACTIVITY, true)
                                                }
                                                IslandType.MEDIA -> {
                                                    if (tapToExpand) {
                                                        openPlayerApp(context, mediaInfo)
                                                    } else {
                                                        requestExpand(IslandType.MEDIA, true)
                                                    }
                                                }
                                                IslandType.FLASHLIGHT -> {
                                                    if (tapToExpand) {
                                                        onFlashlightToggle()
                                                    } else {
                                                        requestExpand(IslandType.FLASHLIGHT, true)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onTap = {
                                isPressed = false
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (type) {
                                    IslandType.NOTIFICATION_ACTIVITY -> {
                                        if (tapToExpand) {
                                            requestExpand(IslandType.NOTIFICATION_ACTIVITY, true)
                                        } else {
                                            notificationActivity?.let(NotificationActivityState::open)
                                        }
                                    }
                                    IslandType.MEDIA -> {
                                        if (tapToExpand) {
                                            requestExpand(IslandType.MEDIA, true)
                                        } else {
                                            MediaPlaybackState.togglePlayPause()
                                        }
                                    }
                                    IslandType.FLASHLIGHT -> {
                                        if (tapToExpand) {
                                            requestExpand(IslandType.FLASHLIGHT, true)
                                        } else {
                                            onFlashlightToggle()
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
        if (!renderSecondarySurface(type)) {
            Box(bubbleModifier)
            return
        }

        Surface(
            modifier = bubbleModifier
                .scale(pressScale * bubbleScale)
                .graphicsLayer {
                    alpha = effectiveAlpha
                    clip = true
                    shape = bubbleShape
                }
                .clip(bubbleShape)
                .then(if (type == IslandType.FLASHLIGHT && showFlashlightOutline) {
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
            shadowElevation = if (isExpanded) 12.dp else if (bubbleAlpha > 0.1f) 4.dp else 0.dp,
        ) {
            SecondaryDotContent(
                type = type,
                bubbleWidth = bubbleWidth,
                bubbleHeight = bubbleHeight,
                bubbleThickness = secondaryDotSize,
                isMiniPill = isMiniPill,
                isLandscape = isLandscape,
                beforeMain = beforeMain,
                bubbleStyle = bubbleStyle,
                notificationActivity = notificationActivity,
                mediaInfo = mediaInfo,
                notificationActivityStatus = notificationActivityStatus,
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

        // Anchor the main pill itself to the camera. When there are 2 tiny dots/mini-pills, one sits on the
        // left and one sits on the right. When there is 1, it sits on the right.
        // Symmetrically offsetting the container keeps the main island centered on the camera.
        val pillStartOffset = ((maxWidth - currentWidth) / 2).coerceAtLeast(0.dp)
        val pillTopOffsetLandscape = ((maxHeight - currentHeight) / 2).coerceAtLeast(0.dp)

        if (isLandscape) {
            val topExtraOffset = if (isLeftBubbleVisible) (leftBubbleHeight + leftBubbleGap) else 0.dp
            val colTopOffset = (pillTopOffsetLandscape - topExtraOffset).coerceAtLeast(0.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = colTopOffset),
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
                    SecondaryDotBubble(
                        type = displayRightType,
                        beforeMain = false,
                        bubbleWidth = rightBubbleWidth,
                        bubbleHeight = rightBubbleHeight,
                        bubbleAlpha = rightBubbleAlpha,
                        bubbleScale = rightBubbleScale,
                        isActive = hasRightBubble,
                        isMiniPill = showDotRightSideInfo,
                    )
                }
            }
        } else {
            val leftExtraOffset = if (isLeftBubbleVisible) (leftBubbleWidth + leftBubbleGap) else 0.dp
            val rowStartOffset = (pillStartOffset - leftExtraOffset).coerceAtLeast(0.dp)

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = pillTopOffsetDp.coerceAtLeast(0.dp),
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
                    SecondaryDotBubble(
                        type = displayRightType,
                        beforeMain = false,
                        bubbleWidth = rightBubbleWidth,
                        bubbleHeight = rightBubbleHeight,
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
) {
    val mediaAccentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val bubbleIconSize = (bubbleThickness - 8.dp).coerceIn(12.dp, 20.dp)
    val isPillShaped = isMiniPill && (if (isLandscape) bubbleHeight > bubbleWidth + 4.dp else bubbleWidth > bubbleHeight + 4.dp)
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val outerPadding = 6.dp
    val innerPadding = if (grouped) 4.dp else outerPadding
    val contentShift = if (grouped) 1.5.dp else 0.dp
    val infiniteTransition = rememberInfiniteTransition(label = "dot_torch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
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
                            .size(bubbleIconSize)
                            .clip(CircleShape),
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
                    tint = Color.White.copy(alpha = pulseAlpha),
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            IslandType.MEDIA -> {
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 11.5f,
                    accentColor = mediaAccentColor,
                    barCount = 3,
                    barWidth = 2.5.dp,
                    barSpacing = 1.5.dp,
                    currentPositionMs = mediaInfo.positionMs,
                )
            }
            IslandType.FLASHLIGHT -> {
                val isPercentage = flashlightStatus.contains('%')
                Text(
                    text = formatCompactStatus(flashlightStatus),
                    color = Color.White,
                    fontSize = if (isPercentage) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    if (isPillShaped) {
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (beforeMain) outerPadding else innerPadding,
                        bottom = if (beforeMain) innerPadding else outerPadding,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.size(bubbleIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    leftContent()
                }
                Box(
                    modifier = Modifier.padding(bottom = if (grouped && beforeMain) 0.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (beforeMain) outerPadding else innerPadding,
                        end = if (beforeMain) innerPadding else outerPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.size(bubbleIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    leftContent()
                }
                Box(
                    modifier = Modifier.padding(end = if (grouped && beforeMain) 0.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(
                    x = if (isLandscape) 0.dp else if (beforeMain) contentShift else -contentShift,
                    y = if (!isLandscape) 0.dp else if (beforeMain) contentShift else -contentShift,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
            if (type == IslandType.MEDIA && mediaInfo.albumArt != null) {
                Image(
                    bitmap = mediaInfo.albumArt.asImageBitmap(),
                    contentDescription = "Music Artwork",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.5.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(bubbleIconSize)
                        .scale(iconScale),
                    contentAlignment = Alignment.Center,
                ) {
                    leftContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IslandSurfaceOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    notificationActivity: NotificationActivityInfo? = null,
    isFlashlightOn: Boolean = false,
    expandedType: IslandType = IslandType.MEDIA,
    stackPrimaryType: IslandType = IslandType.MEDIA,
    stackDotIndex: Int = 0,
    stackDotCount: Int = 1,
    fromTinyDot: Boolean = false,
    isSecondarySurface: Boolean = false,
    hasBubbleBefore: Boolean = false,
    hasBubbleAfter: Boolean = false,
    isExpanded: Boolean,
    isCompactVisible: Boolean = true,
    compactOpacity: Float = 1f,
    onCollapse: () -> Unit,
    onFirstFrameDrawn: () -> Unit = {},
    onExitFinished: () -> Unit = {},
    sourceBounds: Rect? = null,
    compactPressScale: Float = 1f,
    compactStretch: Float = 0f,
    startPressScale: Float = 1.0f, // Scale of the compact pill at moment of expansion tap
    modifier: Modifier = Modifier,
) {
    var keepComposedForExit by remember { mutableStateOf(isExpanded) }

    val mediaAccentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val drawWindowWidthPx = LocalView.current.width.takeIf { it > 0 }?.toFloat()
        ?: with(density) { screenWidthDp.toPx() }

    val cutoutDiameterDp = with(density) {
        if (cutoutInfo.radiusPx > 0f) (cutoutInfo.radiusPx * 2f).toDp() else 24.dp
    }.coerceIn(16.dp, 36.dp)
    val outlineAllowanceDp = 2.dp
    val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
    val compactCornerRadius = compactPillThickness / 2f
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val secondaryBubbleThickness = secondaryBubbleThicknessDp(compactPillThickness.value, smallerBubbles).dp
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    // The expanded card starts 14dp below the screen top. Keep its content below
    // the physical camera, including a small gap around the cutout.
    val expandedCutoutClearanceDp = if (isLandscape) 0.dp else
        (cutoutCenterYDp + cutoutDiameterDp / 2f + 8.dp - 14.dp).coerceAtLeast(0.dp)
    val displayRadiusDp = with(density) { cutoutInfo.displayCornerRadiusPx.toDp() }.coerceAtLeast(24.dp)

    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val nestedArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
    val compactExtraDp = when {
        expandedType == IslandType.FLASHLIGHT -> 48.dp
        minimizedStyle == OverlayPreferences.AlbumArtStyle.BLENDED -> compactPillThickness * 3  // Matches CompactIslandOverlay's blendedActiveExtraDp
        minimizedStyle == OverlayPreferences.AlbumArtStyle.NESTED -> compactPillThickness + nestedArtSize
        else -> 60.dp
    }
    val compactWidth = if (isLandscape) compactPillThickness else (cutoutDiameterDp + compactExtraDp)
    val compactHeight = if (isLandscape) (cutoutDiameterDp + compactExtraDp) else compactPillThickness
    val showProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val showCompactProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val showCompactFlashlightOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val showExpandedFlashlightOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxTorchStrength by FlashlightController.maxStrength.collectAsState()
    val flashlightFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxTorchStrength) else 0f
    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val usesVerticalMediaLayout = expandedType == IslandType.MEDIA &&
            expandedPlayerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE
    val hasExtraMediaButtons = expandedElementVisibility.appActions && mediaInfo.sessionActions.any {
        it.iconResourceId != 0 && it.iconPackageName != null
    }

    val topMarginDp = (cutoutCenterYDp - (compactHeight / 2f)).coerceAtLeast(0.dp)
    val horizontalMarginDp = if (isLandscape) 14.dp else topMarginDp.coerceAtLeast(14.dp)
    val cardWidth = when (expandedType) {
        IslandType.FLASHLIGHT -> minOf(screenWidthDp - 96.dp, 220.dp)
        IslandType.NOTIFICATION_ACTIVITY -> minOf(screenWidthDp - 32.dp, 360.dp)
        IslandType.MEDIA -> if (usesVerticalMediaLayout) {
            minOf(screenWidthDp - 88.dp, 248.dp)
        } else {
            screenWidthDp - (horizontalMarginDp * 2)
        }
    }
    val cardHeight = when (expandedType) {
        IslandType.FLASHLIGHT -> if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
        IslandType.NOTIFICATION_ACTIVITY -> if (isLandscape) minOf(screenHeightDp - 36.dp, 200.dp) else 190.dp
        IslandType.MEDIA -> if (usesVerticalMediaLayout) {
            val hasAlbumArt = showExpandedAlbumArt && mediaInfo.albumArt != null
            val materialHeight = when {
                hasAlbumArt && hasExtraMediaButtons -> 420.dp
                hasAlbumArt -> 340.dp
                hasExtraMediaButtons -> 280.dp
                else -> 200.dp
            }
            if (isLandscape) minOf(screenHeightDp - 36.dp, materialHeight) else materialHeight
        } else {
            220.dp
        }
    }
    val mediaCardHorizontalMarginDp = (screenWidthDp - cardWidth) / 2f

    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)

    val cutoutOffsetX = with(density) {
        val screenWidthPx = screenWidthDp.toPx()
        (cutoutInfo.centerX - (screenWidthPx / 2f)).toDp()
    }

    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val notificationActivityStatus = notificationActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()
    val secondaryItemWidth = secondaryItemWidthDp(
        type = expandedType,
        thicknessDp = secondaryBubbleThickness.value,
        isMiniPill = showDotRightSideInfo,
        hasMedia = mediaInfo.hasMedia,
        flashlightStatus = flashlightStatus,
        activityStatus = notificationActivityStatus,
    ).dp

    val dotWidth = if (isLandscape) secondaryBubbleThickness else secondaryItemWidth
    val dotHeight = if (isLandscape) secondaryItemWidth else secondaryBubbleThickness
    val compactBubbleGap = bubbleGap(bubbleStyle)

    val bubbleOffsetXDp = if (isLandscape) {
        cutoutOffsetX
    } else if (stackDotCount == 2 && stackDotIndex == 0) {
        // Left dot
        cutoutOffsetX - (compactWidth / 2f) - (secondaryItemWidth / 2f) - compactBubbleGap
    } else {
        // Right dot (or single dot)
        cutoutOffsetX + (compactWidth / 2f) + (secondaryItemWidth / 2f) + compactBubbleGap
    }

    val bubbleOffsetYDp = if (!isLandscape) {
        0.dp
    } else if (stackDotCount == 2 && stackDotIndex == 0) {
        // Top dot
        - ((compactHeight / 2f) + (secondaryItemWidth / 2f) + compactBubbleGap)
    } else {
        // Bottom dot (or single dot)
        (compactHeight / 2f) + (secondaryItemWidth / 2f) + compactBubbleGap
    }

    val startWidth = sourceBounds?.let { with(density) { it.width.toDp() } }
        ?: if (fromTinyDot) dotWidth else compactWidth * startPressScale
    val startHeight = sourceBounds?.let { with(density) { it.height.toDp() } }
        ?: if (fromTinyDot) dotHeight else compactHeight * startPressScale
    val startOffsetX = sourceBounds?.let {
        with(density) { (it.center.x - drawWindowWidthPx / 2f).toDp() }
    } ?: if (fromTinyDot) bubbleOffsetXDp else cutoutOffsetX
    val startOffsetY = sourceBounds?.let { with(density) { it.top.toDp() } - 14.dp }
        ?: if (fromTinyDot) bubbleOffsetYDp else cutoutCenterYDp - startHeight / 2f - 14.dp
    val startCorner = sourceBounds?.let { minOf(startWidth, startHeight) / 2f }
        ?: if (fromTinyDot) compactCornerRadius else compactCornerRadius * startPressScale

    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(isExpanded, fromTinyDot) {
        if (isExpanded) {
            keepComposedForExit = true
            morphProgress.snapTo(0f)
            withFrameNanos { }
            onFirstFrameDrawn()
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 320f,
                ),
            )
        } else {
            if (keepComposedForExit) {
                morphProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 380, easing = MtIslandExitEasing),
                )
                keepComposedForExit = false
                onExitFinished()
            }
        }
    }

    if (!isCompactVisible && !isExpanded && !keepComposedForExit) return

    val currentWidth = androidx.compose.ui.unit.lerp(startWidth, cardWidth, morphProgress.value)
    val currentHeight = androidx.compose.ui.unit.lerp(startHeight, cardHeight, morphProgress.value)
    val currentOffsetX = androidx.compose.ui.unit.lerp(startOffsetX, 0.dp, morphProgress.value)
    val currentOffsetY = androidx.compose.ui.unit.lerp(startOffsetY, 0.dp, morphProgress.value)
    val currentCornerRadius = androidx.compose.ui.unit.lerp(startCorner, expandedCornerRadiusDp, morphProgress.value)
    val innerCorner = androidx.compose.ui.unit.lerp(
        (startCorner * 0.3f).coerceAtMost(6.dp),
        currentCornerRadius,
        morphProgress.value,
    )
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val beforeCorner = if (grouped && hasBubbleBefore) innerCorner else currentCornerRadius
    val afterCorner = if (grouped && hasBubbleAfter) innerCorner else currentCornerRadius
    val containerShape = squircleShape(
        radiusPx = with(density) { currentCornerRadius.toPx() },
        curvatureFactor = 0.5522848f + (0.8f - 0.5522848f) * morphProgress.value,
        topLeftRadiusPx = with(density) { beforeCorner.toPx() },
        topRightRadiusPx = with(density) { (if (isLandscape) beforeCorner else afterCorner).toPx() },
        bottomRightRadiusPx = with(density) { afterCorner.toPx() },
        bottomLeftRadiusPx = with(density) { (if (isLandscape) afterCorner else beforeCorner).toPx() },
    )

    val compactAlpha = (1f - (morphProgress.value / 0.40f)).coerceIn(0f, 1f)
    val expandedAlpha = ((morphProgress.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val expandedContentAlpha = ((morphProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val expandedContentScale = 0.94f + (0.06f * morphProgress.value)
    val expandedOffsetY = 16.dp * (1f - morphProgress.value)

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "expanded_progress",
    )

    val currentElevation = androidx.compose.ui.unit.lerp(4.dp, 12.dp, morphProgress.value)
    val showFlashlightStrengthOutline =
        if (morphProgress.value < 0.5f) showCompactFlashlightOutline else showExpandedFlashlightOutline

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (isExpanded) Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onCollapse() })
            } else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 14.dp)
                .offset(x = currentOffsetX, y = currentOffsetY)
                .size(width = currentWidth, height = currentHeight)
                .graphicsLayer {
                    val pressScale = 1f + (compactPressScale - 1f) * (1f - morphProgress.value).coerceIn(0f, 1f)
                    val stretch = compactStretch * (1f - morphProgress.value).coerceIn(0f, 1f)
                    scaleX = pressScale * (1f + kotlin.math.abs(stretch) * if (isSecondarySurface) 0.22f else 0.18f)
                    scaleY = pressScale
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    alpha = if (isExpanded || keepComposedForExit) 1f else compactOpacity
                    clip = true
                    shape = containerShape
                }
                .clip(containerShape)
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = when (expandedType) {
                            IslandType.MEDIA -> if ((!isSecondarySurface || morphProgress.value >= 0.5f) &&
                                mediaInfo.hasMedia &&
                                (if (morphProgress.value < 0.5f) showCompactProgressOutline else showProgressOutline)
                            ) animatedProgress else 0f
                            IslandType.FLASHLIGHT -> if (showFlashlightStrengthOutline) flashlightFraction else 0f
                            IslandType.NOTIFICATION_ACTIVITY -> 0f
                        },
                        cornerRadius = currentCornerRadius,
                        shape = containerShape,
                        strokeWidth = 0.75.dp,
                        trackColor = if (expandedType == IslandType.FLASHLIGHT && !showFlashlightStrengthOutline)
                            Color.Transparent else Color(0x30FFFFFF),
                        progressColor = if (expandedType == IslandType.FLASHLIGHT) Color.White else mediaAccentColor,
                    )
                ),
            shape = containerShape,
            color = Color.Black,
            shadowElevation = currentElevation,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (compactAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = compactAlpha },
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
                            )
                        } else if (expandedType == IslandType.NOTIFICATION_ACTIVITY && notificationActivity != null) {
                            CompactNotificationActivityContent(
                                activity = notificationActivity,
                                cutoutDiameterDp = cutoutDiameterDp,
                                isLandscape = isLandscape,
                                status = notificationActivityStatus ?: "",
                            )
                        } else if (expandedType == IslandType.FLASHLIGHT) {
                            CompactFlashlightContent(
                                cutoutDiameterDp = cutoutDiameterDp,
                                onExpand = {},
                                onFlashlightToggle = { ca.saboor.larpdot.flashlight.FlashlightController.toggleFlashlight() },
                                isLandscape = isLandscape,
                                status = flashlightStatus,
                            )
                        } else {
                            CompactIslandContent(
                                mediaInfo = mediaInfo,
                                cutoutDiameterDp = cutoutDiameterDp,
                                onExpand = {},
                                showMinimizedTitle = showMinimizedTitle,
                                isSongAnnouncement = isSongAnnouncement && showSongAnnouncement,
                            )
                        }
                    }
                }

                if (expandedAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = expandedAlpha
                                scaleX = expandedContentScale
                                scaleY = expandedContentScale
                                translationY = with(density) { expandedOffsetY.toPx() }
                            },
                    ) {
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
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
