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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    isFlashlightOn: Boolean = false,
    isExpanded: Boolean = false,
    fromTinyDot: Boolean = false,
    onExpand: (IslandType, Boolean) -> Unit,
    onFlashlightToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val isDebugMode by OverlayPreferences.isDebugModeFlow.collectAsState()

    val isMusicActiveGlobal by MediaPlaybackState.isMusicActive.collectAsState()
    val isMusicActive = mediaInfo.hasMedia && isMusicActiveGlobal
    val isFlashlightActive = isFlashlightOn
    val isPillActive = isMusicActive || isFlashlightActive

    var isMediaSessionActive by remember { mutableStateOf(isMusicActive) }
    LaunchedEffect(isMusicActive) {
        if (isMusicActive) {
            isMediaSessionActive = true
        } else {
            delay(320L)
            isMediaSessionActive = false
        }
    }

    val isSplit = isMusicActive && isFlashlightActive
    val showFlashlightInMain = isFlashlightActive && !isMusicActive

    var lastDisplayType by remember { mutableStateOf(IslandType.MEDIA) }
    LaunchedEffect(showFlashlightInMain, isMusicActive, isFlashlightActive) {
        if (showFlashlightInMain) {
            lastDisplayType = IslandType.FLASHLIGHT
        } else if (isMusicActive) {
            lastDisplayType = IslandType.MEDIA
        } else if (isFlashlightActive) {
            lastDisplayType = IslandType.FLASHLIGHT
        }
    }

    val activeDisplayType = when {
        showFlashlightInMain -> IslandType.FLASHLIGHT
        isMusicActive -> IslandType.MEDIA
        isFlashlightActive -> IslandType.FLASHLIGHT
        else -> lastDisplayType
    }

    val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val cutoutDiameterDp = with(density) {
            if (cutoutInfo.radiusPx > 0f) (cutoutInfo.radiusPx * 2f).toDp() else 24.dp
        }.coerceIn(16.dp, 36.dp)
        val outlineAllowanceDp = 2.dp
        val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
        val compactCornerRadius = compactPillThickness / 2f

        val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
        val nestedArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
        val nestedActiveExtraDp = compactPillThickness + nestedArtSize
        val blendedActiveExtraDp = compactPillThickness * 3

        val activeExtraDp = when {
            activeDisplayType == IslandType.FLASHLIGHT -> 48.dp
            isMusicActive -> when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedActiveExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedActiveExtraDp
                else -> 60.dp
            }
            lastDisplayType == IslandType.FLASHLIGHT -> 48.dp
            else -> when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedActiveExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedActiveExtraDp
                else -> 60.dp
            }
        }
        val compactWidth = if (isLandscape) compactPillThickness else (cutoutDiameterDp + activeExtraDp)
        val compactHeight = if (isLandscape) (cutoutDiameterDp + activeExtraDp) else compactPillThickness

        val currentCornerRadius by animateDpAsState(
            targetValue = compactCornerRadius,
            animationSpec = tween(durationMillis = 280, easing = if (isPillActive) MtIslandEnterEasing else MtIslandExitEasing),
            label = "compact_corner",
        )

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

        val progressFraction = if (mediaInfo.durationMs > 0) {
            (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
        } else 0f

        val animatedProgress by animateFloatAsState(
            targetValue = progressFraction,
            animationSpec = tween(durationMillis = 350, easing = LinearEasing),
            label = "compact_progress",
        )

        // The minimized main island NEVER hides when expanding from tiny dot!
        // Instant hide on expand: the expanded overlay starts at the exact same position/size,
        // so an immediate cutover is seamless. Collapse smoothness comes from the expanded
        // window's exit animation + the compact pill's own animateDpAsState enter animation.
        val pillVisibilityAlpha = if (isExpanded && !fromTinyDot) 0f else 1f

        val coroutineScope = rememberCoroutineScope()
        val dragOffsetAnim = remember { Animatable(0f) }
        val maxDragOffsetPx = with(density) { 8.dp.toPx() }

        val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()

        val compactHPx = with(density) { compactHeight.toPx() }
        val topPaddingPx = with(density) { 14.dp.toPx() }
        val topAnchorPx = (cutoutInfo.centerY - (compactHPx / 2f)).coerceAtLeast(0f)
        val windowPosY = (topAnchorPx - topPaddingPx).coerceAtLeast(0f)
        val pillTopOffsetDp = with(density) { (topAnchorPx - windowPosY).toDp() }.coerceAtLeast(0.dp)

        val paddingHorizontalDp = 14.dp
        val minTitleWDp = 180.dp
        val baseWDp = maxOf(compactWidth, currentWidth) + (paddingHorizontalDp * 2)
        val pillStartOffset = ((baseWDp - currentWidth) / 2).coerceAtLeast(0.dp)

        val paddingLandscapeDp = 14.dp
        val pillTopOffsetLandscape = (paddingLandscapeDp + (compactHeight - currentHeight) / 2).coerceAtLeast(0.dp)

        val mainPillModifier = Modifier
            .pointerInput(isPillActive, isExpanded, mediaInfo.hasMedia) {
                if (isPillActive && !isExpanded) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var hasTriggered = false
                    val swipeThresholdPx = with(density) { 28.dp.toPx() }

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                            hasTriggered = false
                        },
                        onDragEnd = {
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

                            if (activeDisplayType == IslandType.MEDIA && isMusicActive && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                val damped = (totalDragX * 0.10f).coerceIn(-maxDragOffsetPx, maxDragOffsetPx)
                                coroutineScope.launch {
                                    dragOffsetAnim.snapTo(damped)
                                }
                            }

                            if (!hasTriggered) {
                                if (totalDragY > 20f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.3f) {
                                    hasTriggered = true
                                    change.consume()
                                    ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context)
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
                }
            }
            .pointerInput(isPillActive, isExpanded, activeDisplayType) {
                if (isPillActive && !isExpanded) {
                    val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                    detectTapGestures(
                        onPress = {
                            isIslandPressed = true
                            try {
                                val longPressJob = coroutineScope.launch {
                                    delay(300L)
                                    if (isIslandPressed) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (activeDisplayType == IslandType.MEDIA) {
                                            if (tapToExpand) {
                                                openPlayerApp(context, mediaInfo)
                                            } else {
                                                onExpand(IslandType.MEDIA, false)
                                            }
                                        } else {
                                            if (tapToExpand) {
                                                onFlashlightToggle()
                                            } else {
                                                onExpand(IslandType.FLASHLIGHT, false)
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
                            if (activeDisplayType == IslandType.MEDIA) {
                                if (tapToExpand) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExpand(IslandType.MEDIA, false)
                                } else {
                                    openPlayerApp(context, mediaInfo)
                                }
                            } else {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (tapToExpand) {
                                    onExpand(IslandType.FLASHLIGHT, false)
                                } else {
                                    onFlashlightToggle()
                                }
                            }
                        },
                    )
                }
            }

        val mainPill = @Composable {
            Surface(
                modifier = Modifier
                    .width(currentWidth)
                    .height(currentHeight)
                    .graphicsLayer {
                        alpha = pillVisibilityAlpha
                        val stretchMag = dragOffsetAnim.value / maxDragOffsetPx
                        val maxStretch = 0.06f
                        val isSwiping = kotlin.math.abs(stretchMag) > 0.01f
                        scaleX = (1f + kotlin.math.abs(stretchMag) * maxStretch) * islandScale
                        scaleY = islandScale
                        transformOrigin = if (isSwiping) {
                            if (stretchMag >= 0f) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                        } else {
                            TransformOrigin.Center
                        }
                    }
                    .clip(RoundedCornerShape(currentCornerRadius))
                    .then(
                        Modifier.islandFluidProgressBorder(
                            progressFraction = if (activeDisplayType == IslandType.MEDIA && isMusicActive && showProgressOutline) animatedProgress else 0f,
                            cornerRadius = currentCornerRadius,
                            shape = RoundedCornerShape(currentCornerRadius),
                            strokeWidth = 0.75.dp,
                            trackColor = Color(0x30FFFFFF).copy(alpha = (48f / 255f) * contentAlpha),
                            progressColor = mediaInfo.dominantColor,
                        )
                    ),
                shape = RoundedCornerShape(currentCornerRadius),
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
                                if (displayType == IslandType.MEDIA) {
                                    CompactIslandContent(
                                        mediaInfo = mediaInfo,
                                        cutoutDiameterDp = cutoutDiameterDp,
                                        onExpand = { onExpand(IslandType.MEDIA, false) },
                                    )
                                } else {
                                    CompactFlashlightContent(
                                        cutoutDiameterDp = cutoutDiameterDp,
                                        onExpand = { onExpand(IslandType.FLASHLIGHT, false) },
                                        onFlashlightToggle = onFlashlightToggle,
                                        isLandscape = isLandscape,
                                    )
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
                                .background(Color(0x4000E676)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((dotDiameter - 4.dp).coerceAtLeast(12.dp))
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676)),
                            )
                        }
                    }
                }
            }
        }

        val bubbleSize by animateDpAsState(
            targetValue = if (isSplit) compactPillThickness else 0.dp,
            animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
            label = "bubble_size",
        )
        val bubbleAlpha by animateFloatAsState(
            targetValue = if (isSplit) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "bubble_alpha",
        )
        val bubbleScale by animateFloatAsState(
            targetValue = if (isSplit) 1f else 0.4f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
            label = "bubble_scale",
        )
        val bubbleGap by animateDpAsState(
            targetValue = if (isSplit) 8.dp else 0.dp,
            animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
            label = "bubble_gap",
        )

        val infiniteTransition = rememberInfiniteTransition(label = "compact_torch_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )

        // Unified secondary bubble — single Surface shared by both music and flashlight.
        // Only the inner content and tap actions vary by activeDisplayType.
        var isBubblePressed by remember { mutableStateOf(false) }
        val bubblePressScale by animateFloatAsState(
            targetValue = if (isBubblePressed) 1.15f else 1f,
            animationSpec = spring(dampingRatio = 0.70f, stiffness = 500f),
            label = "bubble_press_scale",
        )

        val secondaryBubble = @Composable {
            if (bubbleSize > 0.5.dp || bubbleAlpha > 0.01f) {
                // Determine secondary type: the one that is NOT in the main pill
                val secondaryType = if (activeDisplayType == IslandType.FLASHLIGHT) IslandType.MEDIA else IslandType.FLASHLIGHT
                Surface(
                    modifier = Modifier
                        .size(bubbleSize)
                        .scale(bubblePressScale * bubbleScale)
                        .graphicsLayer {
                            alpha = (if (fromTinyDot && isExpanded) 0f else pillVisibilityAlpha) * bubbleAlpha
                            clip = true
                            shape = CircleShape
                        }
                        .clip(CircleShape)
                        .border(0.75.dp, Color(0x30FFFFFF).copy(alpha = (48f / 255f) * bubbleAlpha), CircleShape)
                        .pointerInput(isExpanded, isSplit, secondaryType) {
                            if (!isExpanded && isSplit) {
                                val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                                detectTapGestures(
                                    onPress = {
                                        isBubblePressed = true
                                        try {
                                            val job = coroutineScope.launch {
                                                delay(300L)
                                                if (isBubblePressed) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (secondaryType == IslandType.MEDIA) {
                                                        if (tapToExpand) {
                                                            openPlayerApp(context, mediaInfo)
                                                        } else {
                                                            onExpand(IslandType.MEDIA, true)
                                                        }
                                                    } else {
                                                        if (tapToExpand) {
                                                            onFlashlightToggle()
                                                        } else {
                                                            onExpand(IslandType.FLASHLIGHT, true)
                                                        }
                                                    }
                                                }
                                            }
                                            tryAwaitRelease()
                                            job.cancel()
                                        } finally {
                                            isBubblePressed = false
                                        }
                                    },
                                    onTap = {
                                        isBubblePressed = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (secondaryType == IslandType.MEDIA) {
                                            if (tapToExpand) {
                                                onExpand(IslandType.MEDIA, true)
                                            } else {
                                                MediaPlaybackState.togglePlayPause()
                                            }
                                        } else {
                                            if (tapToExpand) {
                                                onExpand(IslandType.FLASHLIGHT, true)
                                            } else {
                                                onFlashlightToggle()
                                            }
                                        }
                                    },
                                )
                            }
                        },
                    shape = CircleShape,
                    color = Color.Black,
                    shadowElevation = if (isExpanded) 12.dp else if (bubbleAlpha > 0.1f) 4.dp else 0.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (secondaryType == IslandType.MEDIA) {
                            if (mediaInfo.albumArt != null) {
                                Image(
                                    bitmap = mediaInfo.albumArt.asImageBitmap(),
                                    contentDescription = "Music Artwork",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                val bubbleIconSize = (cutoutDiameterDp - 6.dp).coerceIn(14.dp, 20.dp)
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Music Active",
                                    tint = mediaInfo.dominantColor.copy(alpha = 0.9f),
                                    modifier = Modifier.size(bubbleIconSize),
                                )
                            }
                        } else {
                            val iconScale = (bubbleSize / cutoutDiameterDp).coerceIn(0f, 1f)
                            val bubbleIconSize = (cutoutDiameterDp - 6.dp).coerceIn(14.dp, 20.dp)
                            Icon(
                                imageVector = Icons.Default.FlashlightOn,
                                contentDescription = "Flashlight Active",
                                tint = Color.White.copy(alpha = pulseAlpha),
                                modifier = Modifier
                                    .size(bubbleIconSize)
                                    .scale(iconScale),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = if (isLandscape) Alignment.Center else Alignment.TopCenter,
        ) {
            val isBubbleVisible = isSplit || bubbleSize > 0.5.dp || bubbleAlpha > 0.01f

            if (isLandscape) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = pillTopOffsetLandscape.coerceAtLeast(0.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = mainPillModifier) {
                        mainPill()
                    }
                    if (isBubbleVisible) {
                        Spacer(modifier = Modifier.height(bubbleGap))
                        secondaryBubble()
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            top = pillTopOffsetDp.coerceAtLeast(0.dp),
                            start = pillStartOffset.coerceAtLeast(0.dp),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = mainPillModifier) {
                        mainPill()
                    }
                    if (isBubbleVisible) {
                        Spacer(modifier = Modifier.width(bubbleGap))
                        secondaryBubble()
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandedIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    isFlashlightOn: Boolean = false,
    expandedType: IslandType = IslandType.MEDIA,
    fromTinyDot: Boolean = false,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    onFirstFrameDrawn: () -> Unit = {},
    startPressScale: Float = 1.0f, // Scale of the compact pill at moment of expansion tap
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val cutoutDiameterDp = with(density) {
        if (cutoutInfo.radiusPx > 0f) (cutoutInfo.radiusPx * 2f).toDp() else 24.dp
    }.coerceIn(16.dp, 36.dp)
    val outlineAllowanceDp = 2.dp
    val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
    val compactCornerRadius = compactPillThickness / 2f
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
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
    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()

    val topMarginDp = (cutoutCenterYDp - (compactHeight / 2f)).coerceAtLeast(0.dp)
    val horizontalMarginDp = if (isLandscape) 14.dp else topMarginDp.coerceAtLeast(14.dp)
    val cardWidth = if (expandedType == IslandType.FLASHLIGHT) {
        minOf(screenWidthDp - 96.dp, 220.dp)
    } else {
        screenWidthDp - (horizontalMarginDp * 2)
    }
    val cardHeight = if (expandedType == IslandType.FLASHLIGHT) {
        if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
    } else {
        220.dp
    }

    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)

    val cutoutOffsetX = with(density) {
        val screenWidthPx = screenWidthDp.toPx()
        (cutoutInfo.centerX - (screenWidthPx / 2f)).toDp()
    }

    val bubbleOffsetXDp = if (isLandscape) cutoutOffsetX else cutoutOffsetX + (compactWidth / 2f) + (compactPillThickness / 2f) + 8.dp
    val bubbleOffsetYDp = if (isLandscape) (compactHeight / 2f) + (compactPillThickness / 2f) + 8.dp else 0.dp

    val startWidth = if (fromTinyDot) compactPillThickness else compactWidth * startPressScale
    val startHeight = if (fromTinyDot) compactPillThickness else compactHeight * startPressScale
    val startOffsetX = if (fromTinyDot) bubbleOffsetXDp else cutoutOffsetX
    val startOffsetY = if (fromTinyDot) {
        bubbleOffsetYDp
    } else if (!isLandscape) {
        // Align the morphing card's visual CENTER with the compact pill's center (cutoutCenterYDp).
        // The expanded Box has 14.dp top padding, so the card's natural top = 14.dp from screen top.
        // We need offset = compact_pill_center - (14.dp + startHeight/2).
        cutoutCenterYDp - (startHeight / 2f) - 14.dp
    } else {
        0.dp
    }
    // startCorner must also be scaled: graphicsLayer scaleY on the compact pill scales its clip
    // shape too, so the visual corner radius = compactCornerRadius * islandScale. Match that here
    // so startCorner/startHeight = 0.5 (full pill) regardless of the press scale.
    val startCorner = if (fromTinyDot) compactCornerRadius else compactCornerRadius * startPressScale

    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(isExpanded, fromTinyDot) {
        if (isExpanded) {
            morphProgress.snapTo(0f)
            withFrameNanos { }
            onFirstFrameDrawn()
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.80f,  // Slight overshoot — card briefly larger than target
                    stiffness = 340f,
                ),
            )
        } else {
            morphProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = MtIslandExitEasing,
                ),
            )
        }
    }

    val progress = morphProgress.value
    // Clamped to [0,1] for alpha/scale — the spring can overshoot beyond 1.0 freely on size/shape
    val clampedProgress = progress.coerceIn(0f, 1f)

    fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
        (start.value + (stop.value - start.value) * fraction).dp

    // Use raw progress for size/corner so spring overshoot makes the card bounce slightly larger
    val currentWidth = lerpDp(startWidth, cardWidth, progress)
    val currentHeight = lerpDp(startHeight, cardHeight, progress)
    val currentOffsetX = lerpDp(startOffsetX, 0.dp, progress)
    val currentOffsetY = lerpDp(startOffsetY, 0.dp, progress)
    val currentCornerRadius = lerpDp(startCorner, expandedCornerRadiusDp, clampedProgress)
    val currentElevation = lerpDp(if (fromTinyDot) 4.dp else 2.dp, 14.dp, clampedProgress)

    val squircleRadiusPx = with(density) { currentCornerRadius.toPx() }
    // Morph curvature: 0.55f ≈ circular bezier arc (pill corners) → 0.80f (squircle)
    val curvatureFactor = 0.55f + (0.25f * clampedProgress)
    val containerShape = squircleShape(squircleRadiusPx, curvatureFactor)

    // Staggered crossfade:
    // Compact content fades out quickly in the first 20% of the morph
    val compactAlpha = (1f - (clampedProgress / 0.20f)).coerceIn(0f, 1f)
    // Expanded controls fade in starting at 18% — slight overlap with compact exit for seamlessness
    val expandedAlpha = ((clampedProgress - 0.18f) / 0.45f).coerceIn(0f, 1f)

    // Subtle entrance: expanded content slides up slightly and scales up from 96% as it appears
    val expandedContentScale = 0.96f + (0.04f * clampedProgress)
    val expandedOffsetY = 8.dp * (1f - clampedProgress)

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "expanded_progress",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCollapse() })
            }
            .padding(14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .offset(x = currentOffsetX, y = currentOffsetY)
                .width(currentWidth)
                .height(currentHeight)
                .clip(containerShape)
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = if (showProgressOutline && mediaInfo.hasMedia && expandedType == IslandType.MEDIA) animatedProgress else 0f,
                        cornerRadius = currentCornerRadius,
                        shape = containerShape,
                        strokeWidth = 0.75.dp,
                        trackColor = Color(0x30FFFFFF),
                        progressColor = mediaInfo.dominantColor,
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
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (expandedType == IslandType.FLASHLIGHT) {
                                    Icon(
                                        imageVector = Icons.Default.FlashlightOn,
                                        contentDescription = "Flashlight Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    if (mediaInfo.albumArt != null) {
                                        Image(
                                            bitmap = mediaInfo.albumArt.asImageBitmap(),
                                            contentDescription = "Music Artwork",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        val iconSize = (cutoutDiameterDp - 6.dp).coerceIn(14.dp, 20.dp)
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Music Active",
                                            tint = mediaInfo.dominantColor.copy(alpha = 0.9f),
                                            modifier = Modifier.size(iconSize),
                                        )
                                    }
                                }
                            }
                        } else if (expandedType == IslandType.FLASHLIGHT) {
                            CompactFlashlightContent(
                                cutoutDiameterDp = cutoutDiameterDp,
                                onExpand = {},
                                onFlashlightToggle = { ca.saboor.larpdot.flashlight.FlashlightController.toggleFlashlight() },
                                isLandscape = isLandscape,
                            )
                        } else {
                            CompactIslandContent(
                                mediaInfo = mediaInfo,
                                cutoutDiameterDp = cutoutDiameterDp,
                                onExpand = {},
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
                        if (expandedType == IslandType.FLASHLIGHT) {
                            ExpandedFlashlightContent(
                                cutoutDiameterDp = cutoutDiameterDp,
                                isExpanded = isExpanded,
                                onCollapse = onCollapse,
                                cutoutInfo = cutoutInfo,
                                cardHorizontalMarginDp = horizontalMarginDp,
                            )
                        } else {
                            ExpandedIslandContent(
                                mediaInfo = mediaInfo,
                                cutoutDiameterDp = cutoutDiameterDp,
                                isExpanded = isExpanded,
                                onCollapse = onCollapse,
                                cutoutInfo = cutoutInfo,
                                cardHorizontalMarginDp = horizontalMarginDp,
                            )
                        }
                    }
                }
            }
        }
    }
}
