package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import ca.saboor.larpdot.flashlight.FlashlightController
import androidx.compose.runtime.withFrameNanos

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import org.intellij.lang.annotations.Language
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.roundToInt

/**
 * Authentic cubic-bezier spring and ease curves extracted directly from com.pryshedko.mtisland
 * (zl0.java, jc1.java, f92.java).
 */
val MtIslandEnterEasing = CubicBezierEasing(0.25f, 0.9f, 0.35f, 1.02f) // Soft overshoot bounce
val MtIslandExitEasing = CubicBezierEasing(0.42f, 0.0f, 0.12f, 1.0f)  // Apple-style snap collapse
val MtIslandDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
val MtIslandStandard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

enum class IslandType {
    MEDIA,
    FLASHLIGHT,
}

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

    var pauseHideReady by remember { mutableStateOf(false) }
    LaunchedEffect(mediaInfo.isPlaying, mediaInfo.hasMedia) {
        pauseHideReady = false
        if (mediaInfo.hasMedia && !mediaInfo.isPlaying) {
            delay(5_000L)
            pauseHideReady = true
        }
    }

    val isPaused = mediaInfo.hasMedia && !mediaInfo.isPlaying && pauseHideReady
    val isMusicActive = mediaInfo.hasMedia && !isPaused
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

    var lastDisplayType by remember { mutableStateOf(IslandType.MEDIA) }
    LaunchedEffect(isMusicActive, isFlashlightActive) {
        if (isMusicActive) {
            lastDisplayType = IslandType.MEDIA
        } else if (isFlashlightActive) {
            lastDisplayType = IslandType.FLASHLIGHT
        }
    }

    val isSplit = isMusicActive && isFlashlightActive
    val activeDisplayType = when {
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
            isMusicActive -> when (minimizedStyle) {
                OverlayPreferences.AlbumArtStyle.BLENDED -> blendedActiveExtraDp
                OverlayPreferences.AlbumArtStyle.NESTED -> nestedActiveExtraDp
                else -> 60.dp
            }
            isFlashlightActive -> 48.dp
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
        val pillVisibilityAlpha = if (isExpanded && !fromTinyDot) 0f else 1f

        val coroutineScope = rememberCoroutineScope()
        val dragOffsetAnim = remember { Animatable(0f) }
        val maxDragOffsetPx = with(density) { 8.dp.toPx() }

        val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
        val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
        val isTitleActive = showMinimizedTitle && isMediaSessionActive && mediaInfo.title.isNotBlank() && !isExpanded
        val showTitleText = isTitleActive && isMusicActive

        val compactHPx = with(density) { compactHeight.toPx() }
        val topPaddingPx = with(density) { (if (showMinimizedTitle) 20.dp else 14.dp).toPx() }
        val topAnchorPx = (cutoutInfo.centerY - (compactHPx / 2f)).coerceAtLeast(0f)
        val windowPosY = (topAnchorPx - topPaddingPx).coerceAtLeast(0f)
        val pillTopOffsetDp = with(density) { (topAnchorPx - windowPosY).toDp() }.coerceAtLeast(0.dp)

        val paddingHorizontalDp = 14.dp
        val minTitleWDp = 180.dp
        val baseWDp = maxOf(compactWidth, currentWidth) + (paddingHorizontalDp * 2)
        val effectiveBaseWDp = if (showMinimizedTitle) maxOf(baseWDp, minTitleWDp) else baseWDp
        val pillStartOffset = ((effectiveBaseWDp - currentWidth) / 2).coerceAtLeast(0.dp)

        val paddingLandscapeDp = 14.dp
        val topExtraLandscapeDp = if (showMinimizedTitle) 20.dp else 0.dp
        val landscapeTopOffsetDp = paddingLandscapeDp + topExtraLandscapeDp
        val pillTopOffsetLandscape = (landscapeTopOffsetDp + (compactHeight - currentHeight) / 2).coerceAtLeast(0.dp)

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

                            if (isMusicActive && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
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
                                } else if (isMusicActive && totalDragX > swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                    hasTriggered = true
                                    change.consume()
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaPlaybackState.skipNext()
                                } else if (isMusicActive && totalDragX < -swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
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
            .pointerInput(isPillActive, isExpanded) {
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
                                        if (isMusicActive) {
                                            if (tapToExpand) {
                                                openPlayerApp(context, mediaInfo)
                                            } else {
                                                onExpand(IslandType.MEDIA, false)
                                            }
                                        } else {
                                            onExpand(IslandType.FLASHLIGHT, false)
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
                            if (isMusicActive) {
                                if (tapToExpand) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExpand(IslandType.MEDIA, false)
                                } else {
                                    openPlayerApp(context, mediaInfo)
                                }
                            } else {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExpand(IslandType.FLASHLIGHT, false)
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
                    .scale(islandScale)
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
                            progressFraction = if (isMusicActive && showProgressOutline) animatedProgress else 0f,
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
                            if (activeDisplayType == IslandType.MEDIA) {
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

        var isTorchPressed by remember { mutableStateOf(false) }
        val torchPressScale by animateFloatAsState(
            targetValue = if (isTorchPressed) 1.15f else 1f,
            animationSpec = spring(dampingRatio = 0.70f, stiffness = 500f),
            label = "torch_press_scale",
        )

        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                isIslandPressed = false
                isTorchPressed = false
            }
        }

        val tinyFlashlightBubble = @Composable {
            if (bubbleSize > 0.5.dp || bubbleAlpha > 0.01f) {
                Surface(
                    modifier = Modifier
                        .size(bubbleSize)
                        .scale(torchPressScale * bubbleScale)
                        .graphicsLayer {
                            alpha = (if (fromTinyDot && isExpanded) 0f else pillVisibilityAlpha) * bubbleAlpha
                            clip = true
                            shape = CircleShape
                        }
                        .clip(CircleShape)
                        .border(0.75.dp, Color(0x30FFFFFF).copy(alpha = (48f / 255f) * bubbleAlpha), CircleShape)
                        .pointerInput(isExpanded, isSplit) {
                            if (!isExpanded && isSplit) {
                                val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                                detectTapGestures(
                                    onPress = {
                                        isTorchPressed = true
                                        try {
                                            val job = coroutineScope.launch {
                                                delay(300L)
                                                if (isTorchPressed) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (tapToExpand) {
                                                        onFlashlightToggle()
                                                    } else {
                                                        onExpand(IslandType.FLASHLIGHT, true)
                                                    }
                                                }
                                            }
                                            tryAwaitRelease()
                                            job.cancel()
                                        } finally {
                                            isTorchPressed = false
                                        }
                                    },
                                    onTap = {
                                        isTorchPressed = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (tapToExpand) {
                                            onExpand(IslandType.FLASHLIGHT, true)
                                        } else {
                                            onFlashlightToggle()
                                        }
                                    }
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

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = if (isLandscape) Alignment.Center else Alignment.TopCenter,
        ) {
            if (!isLandscape && isTitleActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(effectiveBaseWDp)
                        .height(pillTopOffsetDp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (mediaInfo.artist.isNotBlank()) "${mediaInfo.title} · ${mediaInfo.artist}" else mediaInfo.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .graphicsLayer { alpha = pillVisibilityAlpha * contentAlpha },
                    )
                }
            }

            if (isLandscape && isTitleActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 60.dp)
                        .padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        .graphicsLayer { alpha = pillVisibilityAlpha * contentAlpha },
                ) {
                    Text(
                        text = if (mediaInfo.artist.isNotBlank()) "${mediaInfo.title} · ${mediaInfo.artist}" else mediaInfo.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }

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
                        tinyFlashlightBubble()
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
                        tinyFlashlightBubble()
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
        minimizedStyle == OverlayPreferences.AlbumArtStyle.BLENDED -> 100.dp
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

    val startWidth = if (fromTinyDot) compactPillThickness else compactWidth
    val startHeight = if (fromTinyDot) compactPillThickness else compactHeight
    val startOffsetX = if (fromTinyDot) bubbleOffsetXDp else cutoutOffsetX
    val startOffsetY = if (fromTinyDot) bubbleOffsetYDp else 0.dp
    val startCorner = compactCornerRadius

    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(isExpanded, fromTinyDot) {
        if (isExpanded) {
            morphProgress.snapTo(0f)
            withFrameNanos { }
            onFirstFrameDrawn()
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = MtIslandEnterEasing,
                ),
            )
        } else {
            morphProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = MtIslandExitEasing,
                ),
            )
        }
    }

    val progress = morphProgress.value

    fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
        (start.value + (stop.value - start.value) * fraction).dp

    val currentWidth = lerpDp(startWidth, cardWidth, progress)
    val currentHeight = lerpDp(startHeight, cardHeight, progress)
    val currentOffsetX = lerpDp(startOffsetX, 0.dp, progress)
    val currentOffsetY = lerpDp(startOffsetY, 0.dp, progress)
    val currentCornerRadius = lerpDp(startCorner, expandedCornerRadiusDp, progress)
    val currentElevation = lerpDp(if (fromTinyDot) 4.dp else 2.dp, 14.dp, progress.coerceIn(0f, 1f))

    val squircleRadiusPx = with(density) { currentCornerRadius.toPx() }
    val containerShape = squircleShape(squircleRadiusPx)

    // Staggered crossfade matching Apple dynamic island:
    // Compact content / icon gently fades out in the first 25% of the morph
    val compactAlpha = (1f - (progress / 0.25f)).coerceIn(0f, 1f)
    // Expanded controls gently fade in from 30% to 80% as the card opens up
    val expandedAlpha = ((progress - 0.30f) / 0.50f).coerceIn(0f, 1f)

    val expandedContentScale = 0.94f + (0.06f * progress.coerceIn(0f, 1f))
    val expandedOffsetY = 10.dp * (1f - progress.coerceIn(0f, 1f))

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "expanded_progress",
    )

    var isTouchFeedbackActive by remember { mutableStateOf(false) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            isTouchFeedbackActive = true
            delay(180L)
            isTouchFeedbackActive = false
        }
    }

    val touchScale by animateFloatAsState(
        targetValue = if (isTouchFeedbackActive) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
        label = "expanded_touch_scale",
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
                .scale(touchScale)
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
                                Icon(
                                    imageVector = Icons.Default.FlashlightOn,
                                    contentDescription = "Flashlight Active",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (expandedType == IslandType.FLASHLIGHT) {
                            CompactFlashlightContent(
                                cutoutDiameterDp = cutoutDiameterDp,
                                onExpand = {},
                                onFlashlightToggle = { FlashlightController.toggleFlashlight() },
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

internal fun squircleShape(radiusPx: Float, curvatureFactor: Float = 0.8f) = GenericShape { size, _ ->
    val radius = radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    val controlDistance = radius * curvatureFactor

    moveTo(radius, 0f)
    lineTo(size.width - radius, 0f)
    cubicTo(
        size.width - radius + controlDistance,
        0f,
        size.width,
        radius - controlDistance,
        size.width,
        radius,
    )
    lineTo(size.width, size.height - radius)
    cubicTo(
        size.width,
        size.height - radius + controlDistance,
        size.width - radius + controlDistance,
        size.height,
        size.width - radius,
        size.height,
    )
    lineTo(radius, size.height)
    cubicTo(
        radius - controlDistance,
        size.height,
        0f,
        size.height - radius + controlDistance,
        0f,
        size.height - radius,
    )
    lineTo(0f, radius)
    cubicTo(
        0f,
        radius - controlDistance,
        radius - controlDistance,
        0f,
        radius,
        0f,
    )
    close()
}

/**
 * Sweeps song playback progress along the perimeter of the fluid morphing capsule/card.
 * Accurately traces from 12 o'clock (top center above the camera cutout) clockwise,
 * dynamically matching the container's animated width, height, and corner radius.
 */
fun Modifier.islandFluidProgressBorder(
    progressFraction: Float,
    cornerRadius: Dp,
    shape: Shape,
    strokeWidth: Dp = 0.75.dp,
    trackColor: Color = Color(0x30FFFFFF),
    progressColor: Color = Color(0xFF00E676),
): Modifier = this.drawWithContent {
    drawContent()

    val strokePx = strokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val w = size.width
    val h = size.height
    val rPx = (cornerRadius.toPx() - halfStroke).coerceIn(0f, (minOf(w, h) / 2f) - halfStroke)

    val left = halfStroke
    val top = halfStroke
    val right = w - halfStroke
    val bottom = h - halfStroke

    val outline = shape.createOutline(size, layoutDirection, this)

    // 1. Ambient hairline outline follows the surface shape, including the expanded squircle.
    when (outline) {
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = trackColor,
            style = Stroke(width = strokePx),
        )
        is Outline.Rounded -> drawRoundRect(
            color = trackColor,
            topLeft = Offset(outline.roundRect.left + halfStroke, outline.roundRect.top + halfStroke),
            size = Size(outline.roundRect.width - strokePx, outline.roundRect.height - strokePx),
            cornerRadius = CornerRadius(
                (outline.roundRect.topLeftCornerRadius.x - halfStroke).coerceAtLeast(0f),
                (outline.roundRect.topLeftCornerRadius.y - halfStroke).coerceAtLeast(0f),
            ),
            style = Stroke(width = strokePx),
        )
        is Outline.Rectangle -> drawRect(
            color = trackColor,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height),
            style = Stroke(width = strokePx),
        )
    }

    // 2. Sweeping active playback progress arc
    if (progressFraction > 0.002f) {
        if (outline is Outline.Generic) {
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(outline.path, forceClosed = false)
            val progressSegment = Path()
            val squircleRadius = cornerRadius.toPx()
                .coerceAtMost(minOf(w, h) / 2f)
            val startDistance = ((w / 2f) - squircleRadius)
                .coerceIn(0f, pathMeasure.length)
            val progressDistance = pathMeasure.length * progressFraction.coerceIn(0f, 1f)
            val endDistance = startDistance + progressDistance
            pathMeasure.getSegment(
                startDistance,
                endDistance.coerceAtMost(pathMeasure.length),
                progressSegment,
                startWithMoveTo = true,
            )
            if (endDistance > pathMeasure.length) {
                pathMeasure.getSegment(
                    0f,
                    endDistance - pathMeasure.length,
                    progressSegment,
                    startWithMoveTo = false,
                )
            }
            drawPath(
                path = progressSegment,
                color = progressColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        } else {
        val centerX = w / 2f
        val path = Path().apply {
            moveTo(centerX, top)
            lineTo(right - rPx, top)
            arcTo(
                rect = Rect(right - 2 * rPx, top, right, top + 2 * rPx),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(right, bottom - rPx)
            arcTo(
                rect = Rect(right - 2 * rPx, bottom - 2 * rPx, right, bottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(left + rPx, bottom)
            arcTo(
                rect = Rect(left, bottom - 2 * rPx, left + 2 * rPx, bottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(left, top + rPx)
            arcTo(
                rect = Rect(left, top, left + 2 * rPx, top + 2 * rPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(centerX, top)
        }

        val pathMeasure = PathMeasure()
        pathMeasure.setPath(path, forceClosed = true)
        val progressLength = pathMeasure.length * progressFraction.coerceIn(0f, 1f)

        val progressSegment = Path()
        pathMeasure.getSegment(0f, progressLength, progressSegment, startWithMoveTo = true)

        drawPath(
            path = progressSegment,
            color = progressColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        }
    }
}

@Language("AGSL")
private const val PROGRESSIVE_BLUR_SHADER = """
    uniform shader composable;
    uniform float2 size;
    uniform float direction; // 0 = L->R (curved arc), 1 = T->B (curved arc), 2 = B->T, 3 = 2D (expanded island leak)
    uniform float maxBlur;
    uniform float startFraction;
    uniform float uTopSeam;
    uniform float uRightSeam;
    uniform float uDotRadius;

    const float GOLDEN_ANGLE = 2.39996323;

    float gaussian(float r, float sigma) {
        return exp(-0.5 * (r * r) / (sigma * sigma));
    }

    half4 main(float2 coord) {
        float progress = 0.0;
        float fadeFactor = 1.0;

        if (direction < 0.5) {
            // ONLY blur and fade on the RIGHT side towards the dot:
            // The left perimeter and body of the album art remain 100% unblurred and solid.
            // The right boundary curves outward in the center like a circular dome: )
            float yc = size.y * 0.5;
            float dy = coord.y - yc;
            float rCurve = size.y * 0.85;
            float curveOffset = rCurve - sqrt(max(rCurve * rCurve - dy * dy, 0.0));
            float xEff = coord.x + curveOffset;

            float blurStart = size.x * 0.40;
            float blurEnd = size.x * 0.90;
            float blurT = clamp((xEff - blurStart) / max(blurEnd - blurStart, 1.0), 0.0, 1.0);
            progress = smoothstep(0.0, 1.0, blurT);

            float fadeStart = size.x * 0.50;
            float fadeEnd = size.x * 0.96;
            float fadeT = clamp((xEff - fadeStart) / max(fadeEnd - fadeStart, 1.0), 0.0, 1.0);
            fadeFactor = 1.0 - smoothstep(0.0, 1.0, fadeT);
        } else if (direction < 1.5) {
            // Landscape: ONLY blur and fade on the BOTTOM side towards the dot
            float xc = size.x * 0.5;
            float dx = coord.x - xc;
            float rCurve = size.x * 0.85;
            float curveOffset = rCurve - sqrt(max(rCurve * rCurve - dx * dx, 0.0));
            float yEff = coord.y + curveOffset;

            float blurStart = size.y * 0.40;
            float blurEnd = size.y * 0.90;
            float blurT = clamp((yEff - blurStart) / max(blurEnd - blurStart, 1.0), 0.0, 1.0);
            progress = smoothstep(0.0, 1.0, blurT);

            float fadeStart = size.y * 0.50;
            float fadeEnd = size.y * 0.96;
            float fadeT = clamp((yEff - fadeStart) / max(fadeEnd - fadeStart, 1.0), 0.0, 1.0);
            fadeFactor = 1.0 - smoothstep(0.0, 1.0, fadeT);
        } else if (direction < 2.5) {
            // Vertical: bottom (0) -> top (1) with startFraction leak
            float t = 1.0 - (coord.y / size.y);
            progress = clamp((t - startFraction) / max(1.0 - startFraction, 0.001), 0.0, 1.0);
        } else {
            // Direction 3: 2D leak for expanded island
            float leakTop = 20.0;
            float topBound = uTopSeam + leakTop;
            float progTop = clamp((topBound - coord.y) / max(topBound, 1.0), 0.0, 1.0);

            float leakRight = 24.0;
            float rightBound = uRightSeam - leakRight;
            float progRight = clamp((coord.x - rightBound) / max(size.x - rightBound, 1.0), 0.0, 1.0);

            progress = max(progTop, progRight);
        }

        // If completely faded out past the fade edge, return transparent black immediately
        if (fadeFactor <= 0.001) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        float blurFactor = smoothstep(0.0, 1.0, progress);
        float currentRadius = blurFactor * maxBlur;

        if (currentRadius < 0.5) {
            return composable.eval(coord) * fadeFactor;
        }

        half4 accumColor = half4(0.0);
        float accumWeight = 0.0;
        float sigma = max(currentRadius * 0.45, 0.8);

        float centerWeight = gaussian(0.0, sigma);
        accumColor += composable.eval(coord) * centerWeight;
        accumWeight += centerWeight;

        const int SAMPLES = 24;
        for (int i = 0; i < SAMPLES; ++i) {
            float fi = float(i);
            float r = sqrt((fi + 0.5) / float(SAMPLES)) * currentRadius;
            float theta = fi * GOLDEN_ANGLE;
            float2 offset = float2(cos(theta), sin(theta)) * r;
            float weight = gaussian(r, sigma);

            accumColor += composable.eval(coord + offset) * weight;
            accumWeight += weight;
        }

        // The fade factor is applied AFTER blur accumulation, ensuring the blur is fully faded
        return (accumColor / accumWeight) * fadeFactor;
    }
"""

@Language("AGSL")
private const val ORGANIC_SCOOP_FADE_SHADER = """
    uniform float2 uSize;
    uniform float2 uDotCenter;
    uniform float uDotRadius;
    uniform float uScoopDepth;
    uniform float uFadeWidth;
    uniform float uHalfWidth;

    float scoopY(float x) {
        float dist = abs(x - uDotCenter.x);
        float t = clamp(1.0 - (dist / uHalfWidth), 0.0, 1.0);
        
        // Perfectly symmetrical smooth sine bump
        float bump = sin(t * 1.5707963);
        bump = pow(max(bump, 0.0), 1.5);
        return bump * uScoopDepth;
    }

    half4 main(float2 coord) {
        // Guaranteed solid black over the physical camera cutout
        if (length(coord - uDotCenter) <= uDotRadius) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        float sy = scoopY(coord.x);
        float diff = coord.y - sy;
        
        if (diff <= 0.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }
        
        if (diff >= uFadeWidth) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }
        
        float alpha = smoothstep(uFadeWidth, 0.0, diff);
        return half4(0.0, 0.0, 0.0, alpha);
    }
"""

@Composable
private fun Modifier.progressiveBlur(
    direction: Int, // 0 = L->R, 1 = T->B, 2 = B->T, 3 = 2D leak
    maxBlurDp: Dp = 16.dp,
    startFraction: Float = 0f,
    topSeamPx: Float = 0f,
    rightSeamPx: Float = 0f,
    dotRadiusPx: Float = 0f,
): Modifier {
    val density = LocalDensity.current
    val maxBlurPx = with(density) { maxBlurDp.toPx() }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(PROGRESSIVE_BLUR_SHADER) }
        return this.graphicsLayer {
            if (size.width > 0f && size.height > 0f) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("direction", direction.toFloat())
                shader.setFloatUniform("maxBlur", maxBlurPx)
                shader.setFloatUniform("startFraction", startFraction)
                shader.setFloatUniform("uTopSeam", topSeamPx)
                shader.setFloatUniform("uRightSeam", rightSeamPx)
                shader.setFloatUniform("uDotRadius", dotRadiusPx)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable")
                    .asComposeRenderEffect()
            }
        }
    } else {
        return this
    }
}

/**
 * Compact Dynamic Island pill content following mtisland's layout:
 * Left Wing: Album Art thumbnail circular glyph snug against the camera cutout.
 * Center: Precise clearance cushion for the physical camera dot.
 * Right Wing: 4-bar equalizer dancing organically to playback.
 */
/**
 * Resolves a Material 3 Expressive shape for nested album art presentation.
 */
class RotatedShape(
    private val baseShape: Shape,
    private val rotationDegrees: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
        if (normalizedRotation == 0f) {
            return baseShape.createOutline(size, layoutDirection, density)
        }
        val baseOutline = baseShape.createOutline(size, layoutDirection, density)
        val composePath = when (baseOutline) {
            is Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(baseOutline.rect) }
            is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(baseOutline.roundRect) }
            is Outline.Generic -> baseOutline.path
        }
        val matrix = android.graphics.Matrix().apply {
            postRotate(normalizedRotation, size.width / 2f, size.height / 2f)
        }
        val rotatedAndroidPath = android.graphics.Path()
        composePath.asAndroidPath().transform(matrix, rotatedAndroidPath)
        return Outline.Generic(rotatedAndroidPath.asComposePath())
    }
}

/**
 * Resolves a Material 3 Expressive shape for nested album art presentation, with optional rotation
 * while keeping album art inside strictly upright and unrotated.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun nestedAlbumArtShape(
    shapeOption: OverlayPreferences.NestedAlbumArtShape,
    rotationDegrees: Float = 0f,
): Shape {
    val baseShape = when (shapeOption) {
        OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE -> MaterialShapes.Square.toShape()
        OverlayPreferences.NestedAlbumArtShape.CIRCLE -> MaterialShapes.Circle.toShape()
        OverlayPreferences.NestedAlbumArtShape.SLANTED -> MaterialShapes.Slanted.toShape()
        OverlayPreferences.NestedAlbumArtShape.ARCH -> MaterialShapes.Arch.toShape()
        OverlayPreferences.NestedAlbumArtShape.FAN -> MaterialShapes.Fan.toShape()
        OverlayPreferences.NestedAlbumArtShape.ARROW -> MaterialShapes.Arrow.toShape()
        OverlayPreferences.NestedAlbumArtShape.SEMI_CIRCLE -> MaterialShapes.SemiCircle.toShape()
        OverlayPreferences.NestedAlbumArtShape.OVAL -> MaterialShapes.Oval.toShape()
        OverlayPreferences.NestedAlbumArtShape.PILL -> MaterialShapes.Pill.toShape()
        OverlayPreferences.NestedAlbumArtShape.TRIANGLE -> MaterialShapes.Triangle.toShape()
        OverlayPreferences.NestedAlbumArtShape.DIAMOND -> MaterialShapes.Diamond.toShape()
        OverlayPreferences.NestedAlbumArtShape.CLAM_SHELL -> MaterialShapes.ClamShell.toShape()
        OverlayPreferences.NestedAlbumArtShape.PENTAGON -> MaterialShapes.Pentagon.toShape()
        OverlayPreferences.NestedAlbumArtShape.GEM -> MaterialShapes.Gem.toShape()
        OverlayPreferences.NestedAlbumArtShape.SUNNY -> MaterialShapes.Sunny.toShape()
        OverlayPreferences.NestedAlbumArtShape.VERY_SUNNY -> MaterialShapes.VerySunny.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE -> MaterialShapes.Cookie4Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE_6 -> MaterialShapes.Cookie6Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE_7 -> MaterialShapes.Cookie7Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE_9 -> MaterialShapes.Cookie9Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE_12 -> MaterialShapes.Cookie12Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.GHOSTISH -> MaterialShapes.Ghostish.toShape()
        OverlayPreferences.NestedAlbumArtShape.CLOVER -> MaterialShapes.Clover4Leaf.toShape()
        OverlayPreferences.NestedAlbumArtShape.CLOVER_8 -> MaterialShapes.Clover8Leaf.toShape()
        OverlayPreferences.NestedAlbumArtShape.BURST -> MaterialShapes.Burst.toShape()
        OverlayPreferences.NestedAlbumArtShape.SOFT_BURST -> MaterialShapes.SoftBurst.toShape()
        OverlayPreferences.NestedAlbumArtShape.BOOM -> MaterialShapes.Boom.toShape()
        OverlayPreferences.NestedAlbumArtShape.SOFT_BOOM -> MaterialShapes.SoftBoom.toShape()
        OverlayPreferences.NestedAlbumArtShape.FLOWER -> MaterialShapes.Flower.toShape()
        OverlayPreferences.NestedAlbumArtShape.PUFFY -> MaterialShapes.Puffy.toShape()
        OverlayPreferences.NestedAlbumArtShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond.toShape()
        OverlayPreferences.NestedAlbumArtShape.PIXEL_CIRCLE -> MaterialShapes.PixelCircle.toShape()
        OverlayPreferences.NestedAlbumArtShape.PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle.toShape()
        OverlayPreferences.NestedAlbumArtShape.BUN -> MaterialShapes.Bun.toShape()
        OverlayPreferences.NestedAlbumArtShape.HEART -> MaterialShapes.Heart.toShape()
    }
    return if (rotationDegrees % 360f == 0f) {
        baseShape
    } else {
        RotatedShape(baseShape, rotationDegrees)
    }
}

@Composable
internal fun CompactIslandContent(
    mediaInfo: MediaTrackInfo,
    cutoutDiameterDp: Dp,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState().value,
    showDominantGlow: Boolean = OverlayPreferences.showDominantColorGlowFlow.collectAsState().value,
    waveformBandCount: Int = OverlayPreferences.waveformBandCountFlow.collectAsState().value,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dotRadiusPx = with(density) { (cutoutDiameterDp / 2f).toPx() }
    val fadeRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + cutoutDiameterDp).toPx() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 0.28f else 0.08f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "compact_glow_alpha",
    )

    if (isLandscape) {
        // Landscape Mode: Vertical Dynamic Island Pill
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Wing: Album art based on chosen AlbumArtStyle
            if (mediaInfo.albumArt != null) {
                when (albumArtStyle) {
                    OverlayPreferences.AlbumArtStyle.NESTED -> {
                        val outlineAllowanceDp = 2.dp
                        val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(albumArtSize)
                                    .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = false)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.00f to Color.White,
                                                    1.00f to Color.Transparent,
                                                )
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                    OverlayPreferences.AlbumArtStyle.BLENDED -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top,
                        ) {
                            val bitmap = mediaInfo.albumArt.asImageBitmap()
                            val side = minOf(bitmap.width, bitmap.height)
                            val cropX = (bitmap.width - side) / 2
                            val cropY = (bitmap.height - side) / 2
                            val eighth = side / 8

                            // Primary Art (spanning island width) + Flipped 1/8 Reflection up to the dot with curved progressive blur
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        val extraPx = 6.dp.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                                maxHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                            )
                                        )
                                        layout(placeable.width.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
                                            placeable.place(0, 0)
                                        }
                                    }
                                    .progressiveBlur(
                                        direction = 1,
                                        startFraction = 0.65f,
                                        maxBlurDp = 24.dp,
                                        dotRadiusPx = dotRadiusPx,
                                    ),
                            ) {
                                val w = size.width
                                val artH = w
                                val refH = size.height - artH
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, cropY),
                                    srcSize = IntSize(side, side),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(w.roundToInt(), artH.roundToInt()),
                                )
                                scale(scaleX = 1f, scaleY = -1f, pivot = Offset(w / 2f, artH + refH / 2f)) {
                                    drawImage(
                                        image = bitmap,
                                        srcOffset = IntOffset(cropX, cropY + side - eighth),
                                        srcSize = IntSize(side, eighth),
                                        dstOffset = IntOffset(0, artH.roundToInt()),
                                        dstSize = IntSize(w.roundToInt(), refH.roundToInt()),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.height(cutoutDiameterDp))

            // Bottom Wing: Equalizer with ambient glow extending from the dot to the bottom edge
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (showDominantGlow) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        mediaInfo.dominantColor.copy(alpha = glowAlpha),
                                    )
                                )
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 13.5f,
                    accentColor = mediaInfo.dominantColor,
                    barCount = waveformBandCount,
                )
            }
        }
    } else {
        // Portrait Mode: Horizontal Dynamic Island Pill
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Wing: Album art based on chosen AlbumArtStyle
            if (mediaInfo.albumArt != null) {
                when (albumArtStyle) {
                    OverlayPreferences.AlbumArtStyle.NESTED -> {
                        val outlineAllowanceDp = 2.dp
                        val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(albumArtSize)
                                    .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colorStops = arrayOf(
                                                    0.00f to Color.White,
                                                    1.00f to Color.Transparent,
                                                )
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                    OverlayPreferences.AlbumArtStyle.BLENDED -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            val bitmap = mediaInfo.albumArt.asImageBitmap()
                            val side = minOf(bitmap.width, bitmap.height)
                            val cropX = (bitmap.width - side) / 2
                            val cropY = (bitmap.height - side) / 2
                            val eighth = side / 8

                            // Primary Art (spanning island height) + Flipped 1/8 Reflection up to the dot with curved progressive blur
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        val extraPx = 6.dp.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                                maxWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                            )
                                        )
                                        layout(constraints.maxWidth.coerceAtLeast(0), placeable.height.coerceAtLeast(0)) {
                                            placeable.place(0, 0)
                                        }
                                    }
                                    .progressiveBlur(
                                        direction = 0,
                                        startFraction = 0.65f,
                                        maxBlurDp = 24.dp,
                                        dotRadiusPx = dotRadiusPx,
                                    ),
                            ) {
                                val h = size.height
                                val artW = h
                                val refW = size.width - artW
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, cropY),
                                    srcSize = IntSize(side, side),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(artW.roundToInt(), h.roundToInt()),
                                )
                                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(artW + refW / 2f, h / 2f)) {
                                    drawImage(
                                        image = bitmap,
                                        srcOffset = IntOffset(cropX + side - eighth, cropY),
                                        srcSize = IntSize(eighth, side),
                                        dstOffset = IntOffset(artW.roundToInt(), 0),
                                        dstSize = IntSize(refW.roundToInt(), h.roundToInt()),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.width(cutoutDiameterDp))

            // Right Wing: Equalizer with ambient glow extending from the dot to the right edge
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (showDominantGlow) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        mediaInfo.dominantColor.copy(alpha = glowAlpha),
                                    )
                                )
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 13.5f,
                    accentColor = mediaInfo.dominantColor,
                    barCount = waveformBandCount,
                )
            }
        }
    }
}

/**
 * Expanded Dynamic Island card content following mtisland's layout:
 * Top Row: Large rounded album art (54dp) on left, center hole punch clearance, right animated equalizer.
 * Middle: Track title & artist with single-line ellipsis and 1-tap player launch.
 * Scrubber: Interactive drag & tap scrub bar, elapsed time on left, remaining time (-M:SS) on right.
 * Controls: Apple-style Previous, Circular Play/Pause, and Next buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpandedIslandContent(
    mediaInfo: MediaTrackInfo,
    cutoutDiameterDp: Dp,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    cutoutInfo: CutoutInfo? = null,
    cardHorizontalMarginDp: Dp = 14.dp,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState().value,
    showDominantGlow: Boolean = OverlayPreferences.showDominantColorGlowFlow.collectAsState().value,
    showCameraSwoop: Boolean = OverlayPreferences.showCameraSwoopFlow.collectAsState().value,
    waveformBandCount: Int = OverlayPreferences.waveformBandCountFlow.collectAsState().value,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    // Interactive scrub state
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else progressFraction

    val cardDragOffsetAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val maxCardDragOffsetPx = with(density) { 8.dp.toPx() }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dotCenterXDp = if (cutoutInfo != null) {
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val orientedCenterX = if (isLandscape) {
            minOf(cutoutInfo.centerX, screenWidthPx - cutoutInfo.centerX)
        } else {
            cutoutInfo.centerX
        }
        with(density) { orientedCenterX.toDp() } - cardHorizontalMarginDp
    } else {
        (configuration.screenWidthDp.dp - (cardHorizontalMarginDp * 2)) / 2f
    }
    val dotCenterYDp = 18.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Stretch the side being swiped toward instead of translating
                val stretchMag = cardDragOffsetAnim.value / maxCardDragOffsetPx // -1..1
                val maxStretch = 0.04f // 4% max stretch on expanded card
                scaleX = 1f + kotlin.math.abs(stretchMag) * maxStretch
                transformOrigin = if (stretchMag >= 0f) {
                    TransformOrigin(0f, 0.5f) // swiping right → pivot left, stretch right
                } else {
                    TransformOrigin(1f, 0.5f) // swiping left → pivot right, stretch left
                }
            }
            .background(Color.Black)
            .drawWithCache {
                if (albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND) {
                    onDrawBehind { /* Full background image rendered inside content */ }
                } else {
                    val dominantTint = if (showDominantGlow) {
                        mediaInfo.dominantColor.copy(alpha = 0.25f)
                    } else {
                        Color.Transparent
                    }
                    val rightGradient = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.60f to Color.Transparent,
                            1.00f to dominantTint,
                        )
                    )
                    val bottomGradient = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.40f to Color.Transparent,
                            1.00f to dominantTint,
                        )
                    )
                    onDrawBehind {
                        drawRect(rightGradient)
                        drawRect(bottomGradient)
                    }
                }
            }
            .pointerInput(mediaInfo.hasMedia) {
                var totalDragX = 0f
                var totalDragY = 0f
                var hasTriggered = false
                val swipeThresholdPx = with(density) { 32.dp.toPx() }

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
                            cardDragOffsetAnim.animateTo(
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
                            cardDragOffsetAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
                            )
                        }
                    },
                    onDrag = { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        // Live interactive translation: move card slightly with the finger
                        if (mediaInfo.hasMedia && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                            val damped = (totalDragX * 0.10f).coerceIn(-maxCardDragOffsetPx, maxCardDragOffsetPx)
                            coroutineScope.launch {
                                cardDragOffsetAnim.snapTo(damped)
                            }
                        }

                        if (!hasTriggered) {
                            // Drag up to collapse
                            if (totalDragY < -20f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.3f) {
                                hasTriggered = true
                                change.consume()
                                onCollapse()
                            }
                            // Swipe right on music to skip next
                            else if (mediaInfo.hasMedia && totalDragX > swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                hasTriggered = true
                                change.consume()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipNext()
                            }
                            // Swipe left on music to go to previous track
                            else if (mediaInfo.hasMedia && totalDragX < -swipeThresholdPx && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f) {
                                hasTriggered = true
                                change.consume()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipPrevious()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Tapping background collapses back to compact pill
                        onCollapse()
                    }
                )
            }
    ) {
        // Background Album Art based on albumArtStyle
        if (albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND && mediaInfo.albumArt != null) {
            Image(
                bitmap = mediaInfo.albumArt.asImageBitmap(),
                contentDescription = "Background album art",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Offset downwards so the center of the album art is shown better below the camera swoop
                        translationY = 22.dp.toPx()
                        scaleX = 1.15f
                        scaleY = 1.15f
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
            // Android Media Player scrim: dark vertical gradient to maintain contrast and legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.45f),
                                0.40f to Color.Black.copy(alpha = 0.58f),
                                1.00f to Color.Black.copy(alpha = 0.75f),
                            )
                        )
                    )
            )
        } else if (albumArtStyle == OverlayPreferences.AlbumArtStyle.BASIC_FADED && mediaInfo.albumArt != null) {
            val bitmap = mediaInfo.albumArt.asImageBitmap()
            val side = minOf(bitmap.width, bitmap.height)
            val cropX = (bitmap.width - side) / 2
            val cropY = (bitmap.height - side) / 2
            val eighth = side / 8

            val realArtHeight = 190.dp
            val topReflectionHeight = 30.dp
            val totalArtWidth = realArtHeight
            val topSeamPx = with(density) { topReflectionHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(totalArtWidth)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.White.copy(alpha = 0.65f),
                                    1.00f to Color.Transparent,
                                )
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                // Unified Canvas containing Real Art + Top Reflection with progressive blur
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .progressiveBlur(
                            direction = 3,
                            maxBlurDp = 48.dp,
                            topSeamPx = topSeamPx,
                            rightSeamPx = 99999f,
                        ),
                ) {
                    val realArtW = size.width
                    val realArtH = with(density) { realArtHeight.toPx() }
                    val topRefH = topSeamPx

                    // 1. Primary Album Art (1:1 square, completely uncut and visible below camera swoop)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(cropX, cropY),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset(0, topRefH.roundToInt()),
                        dstSize = IntSize(realArtW.roundToInt(), realArtH.roundToInt()),
                    )

                    // 2. Flipped 1/8 Top Reflection that absorbs the camera swoop cover
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(realArtW / 2f, topRefH / 2f)) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX, cropY),
                            srcSize = IntSize(side, eighth),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(realArtW.roundToInt(), topRefH.roundToInt()),
                        )
                    }
                }
            }
        } else if (albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED && mediaInfo.albumArt != null) {
            val bitmap = mediaInfo.albumArt.asImageBitmap()
            val side = minOf(bitmap.width, bitmap.height)
            val cropX = (bitmap.width - side) / 2
            val cropY = (bitmap.height - side) / 2
            val eighth = side / 8

            val realArtHeight = 190.dp
            val topReflectionHeight = 30.dp
            val rightReflectionWidth = realArtHeight * 0.5f
            val totalArtWidth = realArtHeight + rightReflectionWidth
            val topSeamPx = with(density) { topReflectionHeight.toPx() }
            val rightSeamPx = with(density) { realArtHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(totalArtWidth)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // Dark scrim over expanded album art so text is clear and readable
                        drawRect(Color.Black.copy(alpha = 0.35f))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.White, Color.Transparent)
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                // Unified Canvas containing Real Art + Top/Right/Corner Reflections
                // With 2D progressive blur that leaks into the top and right edges of the main album art!
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .progressiveBlur(
                            direction = 3,
                            maxBlurDp = 48.dp,
                            topSeamPx = topSeamPx,
                            rightSeamPx = rightSeamPx,
                        ),
                ) {
                    val realArtW = rightSeamPx
                    val realArtH = rightSeamPx
                    val topRefH = topSeamPx
                    val rightRefW = size.width - realArtW

                    // 1. Primary Album Art (1:1 square, completely uncut and visible!)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(cropX, cropY),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset(0, topRefH.roundToInt()),
                        dstSize = IntSize(realArtW.roundToInt(), realArtH.roundToInt()),
                    )

                    // 2. Flipped 1/8 Top Reflection
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(realArtW / 2f, topRefH / 2f)) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX, cropY),
                            srcSize = IntSize(side, eighth),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(realArtW.roundToInt(), topRefH.roundToInt()),
                        )
                    }

                    // 3. Flipped 1/8 Right Reflection
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(realArtW + rightRefW / 2f, topRefH + realArtH / 2f)) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX + side - eighth, cropY),
                            srcSize = IntSize(eighth, side),
                            dstOffset = IntOffset(realArtW.roundToInt(), topRefH.roundToInt()),
                            dstSize = IntSize(rightRefW.roundToInt(), realArtH.roundToInt()),
                        )
                    }

                    // 4. Flipped 1/8 Corner Reflection
                    scale(scaleX = -1f, scaleY = -1f, pivot = Offset(realArtW + rightRefW / 2f, topRefH / 2f)) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX + side - eighth, cropY),
                            srcSize = IntSize(eighth, eighth),
                            dstOffset = IntOffset(realArtW.roundToInt(), 0),
                            dstSize = IntSize(rightRefW.roundToInt(), topRefH.roundToInt()),
                        )
                    }
                }
            }
        }
        // Organic scoop-shaped black fade: perfectly symmetrical around the camera hole punch,
        // raised snug under the camera cutout with an ultra-smooth wide fade.
        if (showCameraSwoop) {
            val dotXPx = with(density) { dotCenterXDp.toPx() }
            val dotYPx = with(density) { dotCenterYDp.toPx() }
            val dotRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + 3.dp).toPx() }
            val scoopDepthPx = with(density) { (dotCenterYDp + (cutoutDiameterDp / 2f) - 2.dp).toPx() }
            val fadeWidthPx = with(density) { 72.dp.toPx() }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val scoopShader = remember { RuntimeShader(ORGANIC_SCOOP_FADE_SHADER) }
                val scoopBrush = remember(scoopShader) { ShaderBrush(scoopShader) }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val halfWidthPx = minOf(dotXPx, size.width - dotXPx) * 0.75f
                    scoopShader.setFloatUniform("uSize", size.width, size.height)
                    scoopShader.setFloatUniform("uDotCenter", dotXPx, dotYPx)
                    scoopShader.setFloatUniform("uDotRadius", dotRadiusPx)
                    scoopShader.setFloatUniform("uScoopDepth", scoopDepthPx)
                    scoopShader.setFloatUniform("uFadeWidth", fadeWidthPx)
                    scoopShader.setFloatUniform("uHalfWidth", halfWidthPx)

                    drawRect(brush = scoopBrush)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Row 1: Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Symmetrical clearance spacer hugging the hole punch camera
                Spacer(modifier = Modifier.width(cutoutDiameterDp))
            }

            // Row 2: Track Title & Artist (Left) + Visualizer stacked above Play/Pause Button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED && mediaInfo.albumArt != null) {
                    Image(
                        bitmap = mediaInfo.albumArt.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                        .clickable { openPlayerApp(context, mediaInfo) },
                ) {
                    Text(
                        text = mediaInfo.title.ifEmpty { "No Media Playing" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mediaInfo.artist.ifEmpty { "" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Controls Column: 5-bar visualizer in a fixed container stacked directly above the Play/Pause button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerWaveform(
                            isPlaying = mediaInfo.isPlaying,
                            maxHeightDp = 20f,
                            accentColor = mediaInfo.dominantColor,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 3.2.dp else 4.dp,
                            barSpacing = if (waveformBandCount > 5) 2.4.dp else 3.dp,
                            minHeight = 4.dp,
                            barCornerRadius = 2.dp,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    // Native Android 13/14 M3 wide pill Play/Pause button
                    Surface(
                        onClick = {
                            if (!mediaInfo.hasMedia && !mediaInfo.isSimulated) {
                                MediaPlaybackState.setSimulatedPlayback(true)
                            } else {
                                MediaPlaybackState.togglePlayPause()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier
                            .width(64.dp)
                            .height(46.dp),
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF1B1A1E),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }

            // Row 3: Scrubber Line with M3 Expressive Wavy Indicator & Full Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { MediaPlaybackState.skipPrevious() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .height(32.dp)
                        .pointerInput(mediaInfo.durationMs) {
                            detectTapGestures { offset ->
                                if (mediaInfo.durationMs > 0) {
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    MediaPlaybackState.seekTo((fraction * mediaInfo.durationMs).toLong())
                                }
                            }
                        }
                        .pointerInput(mediaInfo.durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    if (mediaInfo.durationMs > 0) {
                                        MediaPlaybackState.seekTo((dragFraction * mediaInfo.durationMs).toLong())
                                    }
                                    isDragging = false
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LinearWavyProgressIndicator(
                        progress = { activeFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                        color = mediaInfo.dominantColor,
                        trackColor = mediaInfo.dominantColor.copy(alpha = 0.32f),
                        amplitude = { if (mediaInfo.isPlaying) 0.5f else 0.15f },
                        wavelength = 28.dp,
                    )
                }

                IconButton(
                    onClick = { MediaPlaybackState.skipNext() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Animated 5-bar equalizer visualizer component modeled after com.pryshedko.mtisland.
 * Each bar oscillates smoothly with organic sinusoidal harmonics when audio is playing,
 * and gently settles to resting dots when paused.
 */
@Composable
private fun EqualizerWaveform(
    isPlaying: Boolean,
    maxHeightDp: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT,
    barWidth: Dp = 2.2.dp,
    barSpacing: Dp = 2.dp,
    minHeight: Dp = 2.8.dp,
    barCornerRadius: Dp = barWidth / 2f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_transition")

    // Organic sinusoidal frequency phases
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_1",
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 980, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_2",
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_3",
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 860, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_4",
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 640, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_5",
    )
    val bar6 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.64f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_6",
    )
    val bar7 by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.56f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_7",
    )

    // Staggered physics springs from center outward for playing <-> paused transitions
    val playProgress3 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "play_p3",
    )
    val playProgress2 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "play_p2",
    )
    val playProgress4 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "play_p4",
    )
    val playProgress1 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "play_p1",
    )
    val playProgress5 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "play_p5",
    )
    val playProgress6 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.70f, stiffness = 160f)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 160f)
        },
        label = "play_p6",
    )
    val playProgress7 by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            spring(dampingRatio = 0.70f, stiffness = 160f)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 160f)
        },
        label = "play_p7",
    )

    // Soft opacity transition: vibrant full color during playback, gently subdued when paused
    val playAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.50f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "eq_alpha",
    )

    val restingFraction = (minHeight.value / maxHeightDp).coerceIn(0.10f, 0.35f)

    val animatedH1 = restingFraction + (bar1 - restingFraction) * playProgress1.coerceAtLeast(0f)
    val animatedH2 = restingFraction + (bar2 - restingFraction) * playProgress2.coerceAtLeast(0f)
    val animatedH3 = restingFraction + (bar3 - restingFraction) * playProgress3.coerceAtLeast(0f)
    val animatedH4 = restingFraction + (bar4 - restingFraction) * playProgress4.coerceAtLeast(0f)
    val animatedH5 = restingFraction + (bar5 - restingFraction) * playProgress5.coerceAtLeast(0f)
    val animatedH6 = restingFraction + (bar6 - restingFraction) * playProgress6.coerceAtLeast(0f)
    val animatedH7 = restingFraction + (bar7 - restingFraction) * playProgress7.coerceAtLeast(0f)

    val heights = when {
        barCount <= 3 -> listOf(animatedH1, animatedH2, animatedH3)
        barCount == 4 -> listOf(animatedH1, animatedH2, animatedH3, animatedH4)
        barCount == 5 -> listOf(animatedH1, animatedH2, animatedH3, animatedH4, animatedH5)
        barCount == 6 -> listOf(animatedH6, animatedH1, animatedH2, animatedH3, animatedH4, animatedH5)
        else -> listOf(animatedH6, animatedH1, animatedH2, animatedH3, animatedH4, animatedH5, animatedH7)
    }

    val effectiveBarWidth = if (barWidth == 2.2.dp && barCount >= 7) 2.0.dp else barWidth
    val effectiveBarSpacing = if (barSpacing == 2.dp && barCount >= 7) 1.6.dp else barSpacing
    val effectiveCornerRadius = if (barCornerRadius == barWidth / 2f) effectiveBarWidth / 2f else barCornerRadius

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(effectiveBarSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(effectiveBarWidth)
                    .height((maxHeightDp * fraction).dp.coerceAtLeast(minHeight))
                    .clip(RoundedCornerShape(effectiveCornerRadius))
                    .background(accentColor.copy(alpha = playAlpha))
            )
        }
    }
}

private fun openPlayerApp(context: Context, track: MediaTrackInfo) {
    val controller = track.controller
    val pkg = track.playerPackageName ?: controller?.packageName

    if (pkg != null) {
        val launched = runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (launched) return
    }

    runCatching {
        controller?.sessionActivity?.send()
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

private fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val remaining = (durationMs - positionMs).coerceAtLeast(0)
    val totalSec = remaining / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("-%d:%02d", min, sec)
}
