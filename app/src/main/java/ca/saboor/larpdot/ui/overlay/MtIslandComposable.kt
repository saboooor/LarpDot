package ca.saboor.larpdot.ui.overlay

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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactIslandOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    isExpanded: Boolean = false,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    var pauseHideReady by remember { mutableStateOf(false) }
    LaunchedEffect(mediaInfo.hasMedia, mediaInfo.isPlaying) {
        pauseHideReady = false
        if (mediaInfo.hasMedia && !mediaInfo.isPlaying) {
            delay(5_000)
            pauseHideReady = true
        }
    }

    val isPaused = mediaInfo.hasMedia && !mediaInfo.isPlaying && pauseHideReady

    if (!mediaInfo.hasMedia) {
        // Idle Dot Mode: Subtle glowing ring strictly covering the hole punch camera
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
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
    } else {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val cutoutDiameterDp = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceIn(20.dp, 32.dp)
        val compactWidth = if (isLandscape) 36.dp else (cutoutDiameterDp + 72.dp)
        val compactHeight = if (isLandscape) (cutoutDiameterDp + 72.dp) else 36.dp

        val currentWidth by animateDpAsState(
            targetValue = if (isPaused) cutoutDiameterDp else compactWidth,
            animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
            label = "compact_width",
        )
        val currentHeight by animateDpAsState(
            targetValue = if (isPaused) cutoutDiameterDp else compactHeight,
            animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
            label = "compact_height",
        )
        val currentCornerRadius by animateDpAsState(
            targetValue = if (isPaused) cutoutDiameterDp / 2f else 18.dp,
            animationSpec = tween(durationMillis = 280, easing = MtIslandExitEasing),
            label = "compact_corner",
        )

        var isIslandPressed by remember { mutableStateOf(false) }
        val islandScale by animateFloatAsState(
            targetValue = if (isIslandPressed) 1.10f else 1f,
            animationSpec = if (isIslandPressed) {
                spring(dampingRatio = 0.9f, stiffness = 300f)
            } else {
                tween(durationMillis = 260, easing = MtIslandDecelerate)
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

        val compactAlpha by animateFloatAsState(
            targetValue = if (isExpanded) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (isExpanded) 180 else 240,
                delayMillis = 0,
                easing = if (isExpanded) MtIslandDecelerate else MtIslandStandard,
            ),
            label = "compact_alpha",
        )

        val coroutineScope = rememberCoroutineScope()
        val dragOffsetAnim = remember { Animatable(0f) }
        val maxDragOffsetPx = with(density) { 8.dp.toPx() }

        val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
        val showTitleText = showMinimizedTitle && !isPaused && mediaInfo.hasMedia && mediaInfo.title.isNotBlank() && !isExpanded

        val compactHPx = with(density) { 36.dp.toPx() }
        val topPaddingPx = with(density) { (if (showTitleText) 20.dp else 14.dp).toPx() }
        val topAnchorPx = (cutoutInfo.centerY - (compactHPx / 2f)).coerceAtLeast(with(density) { 8.dp.toPx() })
        val windowPosY = (topAnchorPx - topPaddingPx).coerceAtLeast(0f)
        val pillTopOffsetDp = with(density) { (topAnchorPx - windowPosY).toDp() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(isPaused, isExpanded, mediaInfo.hasMedia) {
                    if (!isPaused && !isExpanded) {
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

                                // Live interactive translation: rubber-band dampened displacement with finger
                                if (mediaInfo.hasMedia && kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                    val damped = (totalDragX * 0.10f).coerceIn(-maxDragOffsetPx, maxDragOffsetPx)
                                    coroutineScope.launch {
                                        dragOffsetAnim.snapTo(damped)
                                    }
                                }

                                if (!hasTriggered) {
                                    // Swipe down to open notification shade
                                    if (totalDragY > 20f && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.3f) {
                                        hasTriggered = true
                                        change.consume()
                                        ca.saboor.larpdot.service.DotAccessibilityService.openNotificationShade(context)
                                    }
                                    // Swipe right on music to skip to next track
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
                }
                .pointerInput(isPaused, isExpanded) {
                    if (!isPaused && !isExpanded) {
                        val tapToExpand = OverlayPreferences.tapToExpandFlow.value
                        detectTapGestures(
                            onPress = { offset ->
                                isIslandPressed = true
                                try {
                                    // Shorter long-press: check after 300ms instead of system default ~500ms
                                    val longPressJob = coroutineScope.launch {
                                        kotlinx.coroutines.delay(300L)
                                        if (isIslandPressed) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (tapToExpand) {
                                                openPlayerApp(context, mediaInfo)
                                            } else {
                                                onExpand()
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
                                if (tapToExpand) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExpand()
                                } else {
                                    openPlayerApp(context, mediaInfo)
                                }
                            },
                        )
                    }
                },
            contentAlignment = if (isLandscape) Alignment.Center else Alignment.TopCenter,
        ) {
            if (!isLandscape && showTitleText) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            .graphicsLayer { alpha = compactAlpha },
                    )
                }
            }

            if (isLandscape && showTitleText) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 60.dp)
                        .padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        .graphicsLayer { alpha = compactAlpha },
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

            Surface(
                modifier = Modifier
                    .then(if (isLandscape) Modifier else Modifier.padding(top = pillTopOffsetDp))
                    .width(currentWidth)
                    .height(currentHeight)
                    .scale(islandScale)
                    .graphicsLayer {
                        alpha = compactAlpha
                        // Stretch the side being swiped toward instead of translating
                        val stretchMag = dragOffsetAnim.value / maxDragOffsetPx // -1..1
                        val maxStretch = 0.06f // 6% max stretch
                        scaleX = 1f + kotlin.math.abs(stretchMag) * maxStretch
                        transformOrigin = if (stretchMag >= 0f) {
                            TransformOrigin(0f, 0.5f) // swiping right → pivot left, stretch right
                        } else {
                            TransformOrigin(1f, 0.5f) // swiping left → pivot right, stretch left
                        }
                    }
                    .clip(RoundedCornerShape(currentCornerRadius))
                    .then(
                        if (!isPaused) {
                            Modifier.islandFluidProgressBorder(
                                progressFraction = animatedProgress,
                                cornerRadius = currentCornerRadius,
                                shape = RoundedCornerShape(currentCornerRadius),
                                strokeWidth = 0.75.dp,
                                trackColor = Color(0x30FFFFFF),
                                progressColor = mediaInfo.dominantColor,
                            )
                        } else Modifier
                    ),
                shape = RoundedCornerShape(currentCornerRadius),
                color = Color.Black,
                shadowElevation = if (isExpanded) 12.dp else 4.dp,
            ) {
                if (!isPaused) {
                    CompactIslandContent(
                        mediaInfo = mediaInfo,
                        cutoutDiameterDp = cutoutDiameterDp,
                        onExpand = onExpand,
                    )
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
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val cutoutDiameterDp = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceIn(20.dp, 32.dp)
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    val displayRadiusDp = with(density) { cutoutInfo.displayCornerRadiusPx.toDp() }.coerceAtLeast(24.dp)

    val compactWidth = if (isLandscape) 36.dp else (cutoutDiameterDp + 72.dp)
    val compactHeight = if (isLandscape) (cutoutDiameterDp + 72.dp) else 36.dp

    val topMarginDp = (cutoutCenterYDp - (compactHeight / 2f)).coerceAtLeast(8.dp)
    val horizontalMarginDp = if (isLandscape) 14.dp else topMarginDp.coerceAtLeast(14.dp)
    val cardWidth = screenWidthDp - (horizontalMarginDp * 2)
    val cardHeight = 190.dp

    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)

    var morphExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isExpanded) {
        morphExpanded = isExpanded
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (morphExpanded) cardWidth else compactWidth,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 360 else 280,
            easing = if (morphExpanded) MtIslandEnterEasing else MtIslandExitEasing,
        ),
        label = "expanded_morph_width",
    )

    val animatedHeight by animateDpAsState(
        targetValue = if (morphExpanded) cardHeight else compactHeight,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 360 else 280,
            easing = if (morphExpanded) MtIslandEnterEasing else MtIslandExitEasing,
        ),
        label = "expanded_morph_height",
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (morphExpanded) expandedCornerRadiusDp else 18.dp,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 360 else 280,
            easing = if (morphExpanded) MtIslandEnterEasing else MtIslandExitEasing,
        ),
        label = "expanded_morph_corner",
    )
    val animatedCornerRadiusPx = with(density) { animatedCornerRadius.toPx() }

    val containerShape = if (morphExpanded) {
        squircleShape(animatedCornerRadiusPx)
    } else {
        RoundedCornerShape(animatedCornerRadius)
    }

    val animatedElevation by animateDpAsState(
        targetValue = if (morphExpanded) 14.dp else 4.dp,
        animationSpec = tween(durationMillis = if (morphExpanded) 240 else 280),
        label = "expanded_elevation",
    )

    val cutoutOffsetX = with(density) {
        val screenWidthPx = screenWidthDp.toPx()
        (cutoutInfo.centerX - (screenWidthPx / 2f)).toDp()
    }
    val animatedOffsetX by animateDpAsState(
        targetValue = if (morphExpanded) 0.dp else cutoutOffsetX,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 360 else 280,
            easing = if (morphExpanded) MtIslandEnterEasing else MtIslandExitEasing,
        ),
        label = "expanded_morph_offset_x",
    )

    val compactAlpha by animateFloatAsState(
        targetValue = if (morphExpanded) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 90 else 180,
            delayMillis = if (morphExpanded) 0 else 140,
            easing = LinearEasing,
        ),
        label = "expanded_compact_alpha",
    )

    val expandedAlpha by animateFloatAsState(
        targetValue = if (morphExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 160 else 70,
            delayMillis = if (morphExpanded) 45 else 0,
            easing = if (morphExpanded) MtIslandDecelerate else LinearEasing,
        ),
        label = "expanded_alpha",
    )

    val expandedOffsetY by animateDpAsState(
        targetValue = if (morphExpanded) 0.dp else 16.dp,
        animationSpec = tween(
            durationMillis = if (morphExpanded) 280 else 70,
            delayMillis = if (morphExpanded) 35 else 0,
            easing = if (morphExpanded) MtIslandEnterEasing else LinearEasing,
        ),
        label = "expanded_offset_y",
    )

    var isExpansionBounceActive by remember { mutableStateOf(false) }
    LaunchedEffect(morphExpanded) {
        if (morphExpanded) {
            isExpansionBounceActive = true
            delay(220)
            isExpansionBounceActive = false
        } else {
            isExpansionBounceActive = false
        }
    }

    val islandScale by animateFloatAsState(
        targetValue = if (isExpansionBounceActive) 1.02f else 1f,
        animationSpec = if (isExpansionBounceActive) {
            spring(dampingRatio = 0.9f, stiffness = 300f)
        } else {
            tween(durationMillis = 260, easing = MtIslandDecelerate)
        },
        label = "expanded_bounce_scale",
    )

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
                .offset(x = animatedOffsetX)
                .width(animatedWidth)
                .height(animatedHeight)
                .scale(islandScale)
                .islandFluidProgressBorder(
                    progressFraction = animatedProgress,
                    cornerRadius = animatedCornerRadius,
                    shape = containerShape,
                    strokeWidth = 0.75.dp,
                    trackColor = Color(0x30FFFFFF),
                    progressColor = mediaInfo.dominantColor,
                ),
            shape = containerShape,
            color = Color.Black,
            shadowElevation = animatedElevation,
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
                        CompactIslandContent(
                            mediaInfo = mediaInfo,
                            cutoutDiameterDp = cutoutDiameterDp,
                            onExpand = {},
                        )
                    }
                }

                if (expandedAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = expandedAlpha
                                translationY = expandedOffsetY.toPx()
                            },
                    ) {
                        ExpandedIslandContent(
                            mediaInfo = mediaInfo,
                            cutoutDiameterDp = cutoutDiameterDp,
                            isExpanded = morphExpanded,
                            onCollapse = onCollapse,
                        )
                    }
                }
            }
        }
    }
}

internal fun squircleShape(radiusPx: Float) = GenericShape { size, _ ->
    val radius = radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    val controlDistance = radius * 0.8f

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
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = CornerRadius(
                outline.roundRect.topLeftCornerRadius.x,
                outline.roundRect.topLeftCornerRadius.y,
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

/**
 * Compact Dynamic Island pill content following mtisland's layout:
 * Left Wing: Album Art thumbnail circular glyph snug against the camera cutout.
 * Center: Precise clearance cushion for the physical camera dot.
 * Right Wing: 4-bar equalizer dancing organically to playback.
 */
/**
 * Resolves a Material 3 Expressive shape for nested album art presentation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun nestedAlbumArtShape(shapeOption: OverlayPreferences.NestedAlbumArtShape): Shape {
    return when (shapeOption) {
        OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE -> MaterialShapes.Square.toShape()
        OverlayPreferences.NestedAlbumArtShape.CIRCLE -> MaterialShapes.Circle.toShape()
        OverlayPreferences.NestedAlbumArtShape.COOKIE -> MaterialShapes.Cookie4Sided.toShape()
        OverlayPreferences.NestedAlbumArtShape.CLOVER -> MaterialShapes.Clover4Leaf.toShape()
        OverlayPreferences.NestedAlbumArtShape.SUNNY -> MaterialShapes.Sunny.toShape()
        OverlayPreferences.NestedAlbumArtShape.HEART -> MaterialShapes.Heart.toShape()
    }
}

@Composable
internal fun CompactIslandContent(
    mediaInfo: MediaTrackInfo,
    cutoutDiameterDp: Dp,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.nestedAlbumArtShapeFlow.collectAsState().value,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape Mode: Vertical Dynamic Island Pill
        val landscapeBgBrush = when (albumArtStyle) {
            OverlayPreferences.AlbumArtStyle.BLENDED -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black,
                    0.66f to Color.Black,
                    1.00f to mediaInfo.dominantColor.copy(alpha = 0.25f),
                )
            )
            OverlayPreferences.AlbumArtStyle.BASIC_FADED,
            OverlayPreferences.AlbumArtStyle.NESTED -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black,
                    1.00f to Color.Black,
                )
            )
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(landscapeBgBrush),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Wing: Album art fills the full pill width or nested rounded square
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    Alignment.Center
                } else {
                    Alignment.TopCenter
                },
            ) {
                if (mediaInfo.albumArt != null) {
                    when (albumArtStyle) {
                        OverlayPreferences.AlbumArtStyle.NESTED -> {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(nestedAlbumArtShape(nestedShape)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
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
                        OverlayPreferences.AlbumArtStyle.BLENDED -> {
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
                                                    0.80f to Color.White.copy(alpha = 0.5f),
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
                }
            }

            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.height(cutoutDiameterDp))

            // Bottom Wing: 4-Bar Equalizer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 13.5f,
                    accentColor = mediaInfo.dominantColor,
                )
            }
        }
    } else {
        // Portrait Mode: Horizontal Dynamic Island Pill
        val portraitBgBrush = when (albumArtStyle) {
            OverlayPreferences.AlbumArtStyle.BLENDED -> Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black,
                    0.66f to Color.Black,
                    1.00f to mediaInfo.dominantColor.copy(alpha = 0.25f),
                )
            )
            OverlayPreferences.AlbumArtStyle.BASIC_FADED,
            OverlayPreferences.AlbumArtStyle.NESTED -> Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black,
                    1.00f to Color.Black,
                )
            )
        }

        Row(
            modifier = modifier
                .fillMaxSize()
                .background(portraitBgBrush),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Wing: Album art fills the full pill height or nested rounded square
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    Alignment.Center
                } else {
                    Alignment.CenterStart
                },
            ) {
                if (mediaInfo.albumArt != null) {
                    when (albumArtStyle) {
                        OverlayPreferences.AlbumArtStyle.NESTED -> {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(nestedAlbumArtShape(nestedShape)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
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
                        OverlayPreferences.AlbumArtStyle.BLENDED -> {
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
                                                    0.80f to Color.White.copy(alpha = 0.5f),
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
                }
            }

            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.width(cutoutDiameterDp))

            // Right Wing: 4-Bar Equalizer (no image, no circle)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 13.5f,
                    accentColor = mediaInfo.dominantColor,
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
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.nestedAlbumArtShapeFlow.collectAsState().value,
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
                val dominantTint = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED) {
                    mediaInfo.dominantColor.copy(alpha = 0.25f)
                } else {
                    Color.Transparent
                }
                val blackTint = Color.Black.copy(alpha = 0.5f)
                val rightGradient = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.60f to blackTint,
                        1.00f to dominantTint,
                    )
                )
                val bottomGradient = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to blackTint,
                        0.25f to blackTint,
                        1.00f to dominantTint,
                    )
                )
                onDrawBehind {
                    drawRect(rightGradient)
                    drawRect(bottomGradient)
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
        // For Basic Faded and Blended, keep the album art on the left and fade its right edge into the black card.
        if (albumArtStyle != OverlayPreferences.AlbumArtStyle.NESTED && mediaInfo.albumArt != null) {
            val fadeBrush = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.BASIC_FADED) {
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.65f),
                        1.00f to Color.Transparent,
                    )
                )
            } else {
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.65f),
                        0.80f to Color.White.copy(alpha = 0.10f),
                        1.00f to Color.Transparent,
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
            ) {
                Image(
                    bitmap = mediaInfo.albumArt.asImageBitmap(),
                    contentDescription = "Album art",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = fadeBrush,
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
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

            // Row 2: Track Title & Artist (Left) + Squircle 18dp Play/Pause Button (Right)
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
                            .clip(nestedAlbumArtShape(nestedShape)),
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
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = mediaInfo.artist.ifEmpty { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Dancing 4-bar equalizer (prominent and large on expanded island)
                EqualizerWaveform(
                    isPlaying = mediaInfo.isPlaying,
                    maxHeightDp = 28f,
                    accentColor = mediaInfo.dominantColor,
                    barWidth = 5.dp,
                    barSpacing = 3.5.dp,
                    minHeight = 5.dp,
                    barCornerRadius = 2.5.dp,
                )

                // Symmetrical clearance spacer hugging the hole punch camera
                Spacer(modifier = Modifier.width(16.dp))

                // Native Android 13/14 M3 18dp squircle Play/Pause button
                Surface(
                    onClick = {
                        if (!mediaInfo.hasMedia && !mediaInfo.isSimulated) {
                            MediaPlaybackState.setSimulatedPlayback(true)
                        } else {
                            MediaPlaybackState.togglePlayPause()
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(52.dp),
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF1B1A1E),
                            modifier = Modifier.size(28.dp),
                        )
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
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.32f),
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
 * Animated 4-bar equalizer visualizer component modeled after com.pryshedko.mtisland.
 * Each bar oscillates smoothly with organic sinusoidal harmonics when audio is playing,
 * and gently settles to resting dots when paused.
 */
@Composable
private fun EqualizerWaveform(
    isPlaying: Boolean,
    maxHeightDp: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
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
        initialValue = 0.24f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq_4",
    )

    val restingFraction = (minHeight.value / maxHeightDp).coerceIn(0.10f, 0.35f)

    val animatedH1 by animateFloatAsState(
        targetValue = if (isPlaying) bar1 else restingFraction,
        animationSpec = tween(durationMillis = 2),
        label = "h1",
    )
    val animatedH2 by animateFloatAsState(
        targetValue = if (isPlaying) bar2 else restingFraction,
        animationSpec = tween(durationMillis = 4),
        label = "h2",
    )
    val animatedH3 by animateFloatAsState(
        targetValue = if (isPlaying) bar3 else restingFraction,
        animationSpec = tween(durationMillis = 8),
        label = "h3",
    )
    val animatedH4 by animateFloatAsState(
        targetValue = if (isPlaying) bar4 else restingFraction,
        animationSpec = tween(durationMillis = 16),
        label = "h4",
    )

    val heights = listOf(animatedH1, animatedH2, animatedH3, animatedH4)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height((maxHeightDp * fraction).dp.coerceAtLeast(minHeight))
                    .clip(RoundedCornerShape(barCornerRadius))
                    .background(accentColor)
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
