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
        val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
        val compactExtraDp = if (minimizedStyle == OverlayPreferences.AlbumArtStyle.BLENDED) 108.dp else 72.dp
        val compactWidth = if (isLandscape) 36.dp else (cutoutDiameterDp + compactExtraDp)
        val compactHeight = if (isLandscape) (cutoutDiameterDp + compactExtraDp) else 36.dp

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

        // When expanded window is morphing, compact overlay remains completely invisible to avoid double outlines
        val compactAlpha = if (isExpanded) 0f else 1f

        val coroutineScope = rememberCoroutineScope()
        val dragOffsetAnim = remember { Animatable(0f) }
        val maxDragOffsetPx = with(density) { 8.dp.toPx() }

        val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
        val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
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
                    
                    .graphicsLayer {
                        alpha = compactAlpha
                        // Stretch the side being swiped toward instead of translating
                        val stretchMag = dragOffsetAnim.value / maxDragOffsetPx // -1..1
                        val maxStretch = 0.06f // 6% max stretch
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
                        if (!isPaused && showProgressOutline) {
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

    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val compactExtraDp = if (minimizedStyle == OverlayPreferences.AlbumArtStyle.BLENDED) 108.dp else 72.dp
    val compactWidth = if (isLandscape) 36.dp else (cutoutDiameterDp + compactExtraDp)
    val compactHeight = if (isLandscape) (cutoutDiameterDp + compactExtraDp) else 36.dp
    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()

    val topMarginDp = (cutoutCenterYDp - (compactHeight / 2f)).coerceAtLeast(8.dp)
    val horizontalMarginDp = if (isLandscape) 14.dp else topMarginDp.coerceAtLeast(14.dp)
    val cardWidth = screenWidthDp - (horizontalMarginDp * 2)
    val cardHeight = 220.dp

    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)

    val morphSpringDp = if (isExpanded) {
        spring<Dp>(dampingRatio = 0.82f, stiffness = 380f)
    } else {
        spring<Dp>(dampingRatio = 0.88f, stiffness = 420f)
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) cardWidth else compactWidth,
        animationSpec = morphSpringDp,
        label = "expanded_morph_width",
    )

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) cardHeight else compactHeight,
        animationSpec = morphSpringDp,
        label = "expanded_morph_height",
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) expandedCornerRadiusDp else 18.dp,
        animationSpec = morphSpringDp,
        label = "expanded_morph_corner",
    )
    val animatedCornerRadiusPx = with(density) { animatedCornerRadius.toPx() }

    // Smoothly morph between exact circular pill ends (0.5523f) and Apple squircle (0.80f)
    val curvatureFactor by animateFloatAsState(
        targetValue = if (isExpanded) 0.80f else 0.55228475f,
        animationSpec = tween(durationMillis = if (isExpanded) 240 else 200),
        label = "expanded_curvature_factor",
    )
    val containerShape = squircleShape(animatedCornerRadiusPx, curvatureFactor)

    val animatedElevation by animateDpAsState(
        targetValue = if (isExpanded) 14.dp else 2.dp,
        animationSpec = tween(durationMillis = if (isExpanded) 220 else 200),
        label = "expanded_elevation",
    )

    val cutoutOffsetX = with(density) {
        val screenWidthPx = screenWidthDp.toPx()
        (cutoutInfo.centerX - (screenWidthPx / 2f)).toDp()
    }
    val animatedOffsetX by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else cutoutOffsetX,
        animationSpec = morphSpringDp,
        label = "expanded_morph_offset_x",
    )

    val compactAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 120 else 160,
            delayMillis = if (isExpanded) 0 else 60,
            easing = FastOutSlowInEasing,
        ),
        label = "expanded_compact_alpha",
    )

    val expandedAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 220 else 110,
            delayMillis = if (isExpanded) 30 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "expanded_alpha",
    )

    val expandedContentScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.92f,
        animationSpec = if (isExpanded) {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        } else {
            tween(durationMillis = 130, easing = MtIslandExitEasing)
        },
        label = "expanded_content_scale",
    )

    val expandedOffsetY by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 10.dp,
        animationSpec = if (isExpanded) {
            spring(dampingRatio = 0.82f, stiffness = 380f)
        } else {
            tween(durationMillis = 130, easing = MtIslandExitEasing)
        },
        label = "expanded_offset_y",
    )

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
            kotlinx.coroutines.delay(180L)
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
                .offset(x = animatedOffsetX)
                .width(animatedWidth)
                .height(animatedHeight)
                .scale(touchScale)
                .then(
                    if (showProgressOutline) {
                        Modifier.islandFluidProgressBorder(
                            progressFraction = animatedProgress,
                            cornerRadius = animatedCornerRadius,
                            shape = containerShape,
                            strokeWidth = 0.75.dp,
                            trackColor = Color(0x30FFFFFF),
                            progressColor = mediaInfo.dominantColor,
                        )
                    } else Modifier
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
                                scaleX = expandedContentScale
                                scaleY = expandedContentScale
                                translationY = expandedOffsetY.toPx()
                            },
                    ) {
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
    uniform float uFadeRadius;
    uniform float uHalfWidth;

    float getDistanceToSwoop(float2 pos, float wi, float he) {
        pos.x = abs(pos.x);
        float ik = (wi * wi) / max(he, 0.001);
        float curveY = he - (pos.x * pos.x) / ik;
        if (pos.y <= curveY && pos.x <= wi) {
            return 0.0;
        }

        float p = ik * (he - pos.y - 0.5 * ik) / 3.0;
        float q = pos.x * ik * ik * 0.25;
        float h = q * q - p * p * p;
        float r = sqrt(abs(h));
        float x = 0.0;
        if (h > 0.0) {
            float diff = q - r;
            x = pow(max(q + r, 0.0), 1.0 / 3.0) + pow(max(abs(diff), 0.0), 1.0 / 3.0) * sign(diff);
        } else {
            x = 2.0 * cos(atan(r, q) / 3.0) * sqrt(max(p, 0.0));
        }
        x = clamp(x, 0.0, wi);
        float2 closest = float2(x, he - (x * x) / ik);
        return length(pos - closest);
    }

    half4 main(float2 coord) {
        // Guaranteed solid black over the physical camera cutout
        if (length(coord - uDotCenter) <= uDotRadius) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        float2 localPos = float2(coord.x - uDotCenter.x, coord.y);
        float dist = getDistanceToSwoop(localPos, uHalfWidth, uScoopDepth);

        if (dist <= 0.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        if (dist >= uFadeRadius) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        // Strictly uniform fade throughout the entire swoop shape
        float alpha = smoothstep(uFadeRadius, 0.0, dist);
        alpha = pow(alpha, 1.15);
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
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dotRadiusPx = with(density) { (cutoutDiameterDp / 2f).toPx() }
    val fadeRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + 36.dp).toPx() }

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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
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
                            contentAlignment = Alignment.TopCenter,
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
                                        mediaInfo.dominantColor.copy(alpha = 0.28f),
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
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
                            contentAlignment = Alignment.CenterStart,
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
                                        mediaInfo.dominantColor.copy(alpha = 0.28f),
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
        // with analytical 2D SDF distance to guarantee a strictly uniform fade radius everywhere.
        if (showCameraSwoop) {
            val dotXPx = with(density) { dotCenterXDp.toPx() }
            val dotYPx = with(density) { dotCenterYDp.toPx() }
            val dotRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + 3.dp).toPx() }
            val scoopDepthPx = with(density) { (dotCenterYDp + (cutoutDiameterDp / 2f) - 2.dp).toPx() }
            val fadeRadiusPx = with(density) { 52.dp.toPx() }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val scoopShader = remember { RuntimeShader(ORGANIC_SCOOP_FADE_SHADER) }
                val scoopBrush = remember(scoopShader) { ShaderBrush(scoopShader) }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val halfWidthPx = minOf(dotXPx, size.width - dotXPx) * 0.75f
                    scoopShader.setFloatUniform("uSize", size.width, size.height)
                    scoopShader.setFloatUniform("uDotCenter", dotXPx, dotYPx)
                    scoopShader.setFloatUniform("uDotRadius", dotRadiusPx)
                    scoopShader.setFloatUniform("uScoopDepth", scoopDepthPx)
                    scoopShader.setFloatUniform("uFadeRadius", fadeRadiusPx)
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
                            barWidth = 4.dp,
                            barSpacing = 3.dp,
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
    val animatedH5 by animateFloatAsState(
        targetValue = if (isPlaying) bar5 else restingFraction,
        animationSpec = tween(durationMillis = 20),
        label = "h5",
    )

    val heights = listOf(animatedH1, animatedH2, animatedH3, animatedH4, animatedH5)

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
