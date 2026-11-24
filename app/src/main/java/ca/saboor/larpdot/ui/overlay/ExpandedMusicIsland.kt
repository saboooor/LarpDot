package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextAlign
import ca.saboor.larpdot.ui.components.MarqueeText
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.AudioOutputChip
import ca.saboor.larpdot.visualizer.BpmChip
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ca.saboor.larpdot.ui.theme.LarpMotion

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
    topContentInset: Dp = 0.dp,
    albumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState().value,
    showAlbumArt: Boolean = OverlayPreferences.showExpandedAlbumArtFlow.collectAsState().value,
    artworkAnchor: ArtworkAnchor? = null,
    playerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.expandedPlayerLayoutFlow.collectAsState().value,
    showProgressSquiggles: Boolean = OverlayPreferences.showProgressSquigglesFlow.collectAsState().value,
    progressBarAccentColor: Boolean = OverlayPreferences.progressBarAccentColorFlow.collectAsState().value,
    playbackControlsStyle: OverlayPreferences.PlaybackControlsStyle = OverlayPreferences.playbackControlsStyleFlow.collectAsState().value,
    trackInfoStyle: OverlayPreferences.TrackInfoStyle = OverlayPreferences.trackInfoStyleFlow.collectAsState().value,
    elementVisibility: OverlayPreferences.ExpandedElementVisibility = OverlayPreferences.expandedElementVisibilityFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState().value,
    showDominantGlow: Boolean = OverlayPreferences.showExpandedDominantColorGlowFlow.collectAsState().value,
    glowSides: OverlayPreferences.ExpandedGlowSides = OverlayPreferences.expandedGlowSidesFlow.collectAsState().value,
    cameraCoverStyle: OverlayPreferences.CameraCoverStyle = OverlayPreferences.cameraCoverStyleFlow.collectAsState().value,
    backgroundOpacity: Float = OverlayPreferences.expandedBackgroundOpacity.collectAsState().value,
    waveformBandCount: Int = OverlayPreferences.waveformBandCountFlow.collectAsState().value,
    bpmPulseEnabled: Boolean = OverlayPreferences.expandedBpmPulseEnabledFlow.collectAsState().value,
    pulseAmount: Float = OverlayPreferences.pulseAmountFlow.collectAsState().value,
    fontStyle: OverlayPreferences.IslandFontStyle = OverlayPreferences.islandFontStyleFlow.collectAsState().value,
    showExpandedProgressRing: Boolean = OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState().value,
    progressProvider: (() -> Float)? = null,
    onAlbumArtClick: (() -> Unit)? = null,
    onVisualizerClick: (() -> Unit)? = null,
    onProgressBarClick: (() -> Unit)? = null,
    onPlaybackControlsClick: (() -> Unit)? = null,
    onTrackInfoClick: (() -> Unit)? = null,
    highlightAlbumArt: Boolean = false,
    highlightVisualizer: Boolean = false,
    highlightProgressBar: Boolean = false,
    highlightPlaybackControls: Boolean = false,
    highlightTrackInfo: Boolean = false,
) {
    val context = LocalContext.current
    val artworkGlow = rememberAlbumArtGlow(mediaInfo)
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val accentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor

    val currentBpm by BpmDetector.currentBpm.collectAsState()
    LaunchedEffect(mediaInfo.title, mediaInfo.artist) {
        if (mediaInfo.title.isNotBlank()) {
            BpmDetector.syncTrack(mediaInfo.title, mediaInfo.artist)
        }
    }
    val livePositionMs = rememberLiveMediaPositionMs(mediaInfo, useLiveStream = progressProvider == null)
    val bpmPulseState = rememberBpmPulse(
        bpm = currentBpm,
        positionMsProvider = { livePositionMs.value },
        isPlaying = mediaInfo.isPlaying,
        enabled = bpmPulseEnabled,
    )

    val albumArtPlaybackScale by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 1f else 0.86f,
        animationSpec = LarpMotion.fade(260),
        label = "album_art_playback_scale",
    )
    val albumArtPlaybackAlpha by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 1f else 0.55f,
        animationSpec = LarpMotion.fade(260),
        label = "album_art_playback_alpha",
    )

    // Interactive scrub state
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFractionProvider: () -> Float = {
        if (isDragging) {
            dragFraction
        } else if (progressProvider != null) {
            progressProvider()
        } else if (mediaInfo.durationMs > 0) {
            (livePositionMs.value.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val expandedArtProgressProvider = if (showExpandedProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L) {
        activeFractionProvider
    } else null

    val cardDragOffsetAnim = remember { androidx.compose.animation.core.Animatable(0f) }
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
            .pointerInput(mediaInfo.hasMedia) {
                if (!mediaInfo.hasMedia) return@pointerInput
                var totalDragX = 0f
                var hasTriggered = false
                val swipeThresholdPx = with(density) { 32.dp.toPx() }
                fun resetDrag() {
                    totalDragX = 0f
                    hasTriggered = false
                    coroutineScope.launch {
                        cardDragOffsetAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = LarpMotion.release,
                        )
                    }
                }

                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        hasTriggered = false
                    },
                    onDragEnd = { resetDrag() },
                    onDragCancel = { resetDrag() },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDragX += dragAmount
                        val damped = (totalDragX * 0.10f).coerceIn(-maxCardDragOffsetPx, maxCardDragOffsetPx)
                        coroutineScope.launch {
                            cardDragOffsetAnim.snapTo(damped)
                        }
                        if (!hasTriggered) {
                            if (totalDragX > swipeThresholdPx) {
                                hasTriggered = true
                                change.consume()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipNext()
                            } else if (totalDragX < -swipeThresholdPx) {
                                hasTriggered = true
                                change.consume()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipPrevious()
                            }
                        }
                    },
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
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = backgroundOpacity }) {
            // Background Album Art based on albumArtStyle
            if ((albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND ||
                    albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND) &&
                mediaInfo.albumArt != null
            ) {
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
                        }
                        .then(
                            if (albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND) {
                                Modifier.blur(24.dp)
                            } else Modifier
                        ),
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
            } else if (albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED && mediaInfo.albumArt != null) {
                val bitmap = mediaInfo.albumArt.asImageBitmap()
                val side = minOf(bitmap.width, bitmap.height)
                val cropX = (bitmap.width - side) / 2
                val cropY = (bitmap.height - side) / 2
                val eighth = side / 8

                val isVertical = playerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE

                if (isVertical) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.Transparent,
                                            0.20f to Color.Transparent,
                                            0.50f to Color.White.copy(alpha = 0.30f),
                                            0.80f to Color.White.copy(alpha = 0.65f),
                                            1.00f to Color.White.copy(alpha = 0.70f),
                                        )
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        Canvas(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val artW = size.width
                            val artSide = artW
                            val topSpace = size.height - artSide

                            if (topSpace <= 0f) {
                                val visibleFraction = (size.height / artSide).coerceIn(0f, 1f)
                                val visibleSrcHeight = (side * visibleFraction).roundToInt()
                                val srcY = cropY + (side - visibleSrcHeight)
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, srcY),
                                    srcSize = IntSize(side, visibleSrcHeight),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(artW.roundToInt(), size.height.roundToInt()),
                                )
                            } else {
                                // Primary Album Art anchored at bottom
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, cropY),
                                    srcSize = IntSize(side, side),
                                    dstOffset = IntOffset(0, topSpace.roundToInt()),
                                    dstSize = IntSize(artW.roundToInt(), artSide.roundToInt()),
                                )
                                // Flipped Top Reflection filling space above real art
                                scale(scaleX = 1f, scaleY = -1f, pivot = Offset(artW / 2f, topSpace / 2f)) {
                                    drawImage(
                                        image = bitmap,
                                        srcOffset = IntOffset(cropX, cropY),
                                        srcSize = IntSize(side, eighth),
                                        dstOffset = IntOffset.Zero,
                                        dstSize = IntSize(artW.roundToInt(), topSpace.roundToInt()),
                                    )
                                }
                            }
                        }
                    }
                } else {
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
                }
            } else if (albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLENDED && mediaInfo.albumArt != null) {
                val bitmap = mediaInfo.albumArt.asImageBitmap()
                val side = minOf(bitmap.width, bitmap.height)
                val cropX = (bitmap.width - side) / 2
                val cropY = (bitmap.height - side) / 2
                val eighth = side / 8

                val isVertical = playerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE

                if (isVertical) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                // Dark scrim over expanded album art so text is clear and readable
                                drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.Transparent,
                                            0.20f to Color.Transparent,
                                            0.50f to Color.White.copy(alpha = 0.45f),
                                            0.80f to Color.White.copy(alpha = 0.85f),
                                            1.00f to Color.White,
                                        )
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .progressiveBlur(
                                    direction = 2,
                                    maxBlurDp = 40.dp,
                                    startFraction = 0.20f,
                                ),
                        ) {
                            val artW = size.width
                            val artSide = artW
                            val topSpace = size.height - artSide

                            if (topSpace <= 0f) {
                                val visibleFraction = (size.height / artSide).coerceIn(0f, 1f)
                                val visibleSrcHeight = (side * visibleFraction).roundToInt()
                                val srcY = cropY + (side - visibleSrcHeight)
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, srcY),
                                    srcSize = IntSize(side, visibleSrcHeight),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(artW.roundToInt(), size.height.roundToInt()),
                                )
                            } else {
                                // Primary Album Art anchored at bottom
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(cropX, cropY),
                                    srcSize = IntSize(side, side),
                                    dstOffset = IntOffset(0, topSpace.roundToInt()),
                                    dstSize = IntSize(artW.roundToInt(), artSide.roundToInt()),
                                )
                                // Flipped Top Reflection filling space above real art
                                scale(scaleX = 1f, scaleY = -1f, pivot = Offset(artW / 2f, topSpace / 2f)) {
                                    drawImage(
                                        image = bitmap,
                                        srcOffset = IntOffset(cropX, cropY),
                                        srcSize = IntSize(side, eighth),
                                        dstOffset = IntOffset.Zero,
                                        dstSize = IntSize(artW.roundToInt(), topSpace.roundToInt()),
                                    )
                                }
                            }
                        }
                    }
                } else {
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
            }
        }

        CameraCover(
            style = cameraCoverStyle,
            centerX = dotCenterXDp,
            centerY = dotCenterYDp,
            diameter = cutoutDiameterDp,
        )

        // Dominant Color Glow layer (rendered on top of background art, behind player content)
        if (showDominantGlow && (glowSides.left || glowSides.right || glowSides.bottom || glowSides.top)) {
            val pulse = bpmPulseState.value * pulseAmount
            val glowAlpha = if (bpmPulseEnabled && mediaInfo.isPlaying) {
                0.16f + (0.22f * pulse)
            } else {
                0.25f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val leftGradient = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to accentColor,
                                0.40f to Color.Transparent,
                            )
                        )
                        val rightGradient = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.60f to Color.Transparent,
                                1.00f to accentColor,
                            )
                        )
                        val bottomGradient = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.40f to Color.Transparent,
                                1.00f to accentColor,
                            )
                        )
                        val topGradient = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to accentColor,
                                0.40f to Color.Transparent,
                            )
                        )
                        onDrawBehind {
                            if (glowSides.left) drawRect(leftGradient, alpha = glowAlpha)
                            if (glowSides.right) drawRect(rightGradient, alpha = glowAlpha)
                            if (glowSides.bottom) drawRect(bottomGradient, alpha = glowAlpha)
                            if (glowSides.top) drawRect(topGradient, alpha = glowAlpha)
                        }
                    }
            )
        }

        if (playerLayout == OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS) Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = if (topContentInset > 0.dp) 0.dp else 14.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Row 1: Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(32.dp, topContentInset)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                if (elementVisibility.bpm && currentBpm != null) {
                    BpmChip(bpm = currentBpm)
                }
                AudioOutputChip(
                    onClick = {
                        AudioOutputTracker.openOutputSwitcher(context)
                        onCollapse()
                    },
                )
            }

            // Row 2: Track Title & Artist (Left) + Visualizer stacked above Play/Pause Button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showAlbumArt && mediaInfo.albumArt != null) {
                    ExpandedArtwork(
                        artwork = mediaInfo.albumArt,
                        anchor = artworkAnchor,
                        glow = artworkGlow,
                        shape = artworkShape,
                        opacity = { albumArtPlaybackAlpha },
                        progressProvider = expandedArtProgressProvider,
                        progressColor = accentColor,
                        progressStrokeWidth = 2.5.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .then(
                                if (highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, artworkShape)
                                else Modifier
                            )
                            .then(if (onAlbumArtClick != null) Modifier.clickable(onClick = onAlbumArtClick) else Modifier)
                            .graphicsLayer {
                                scaleX = albumArtPlaybackScale
                                scaleY = albumArtPlaybackScale
                                alpha = albumArtPlaybackAlpha
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                    )
                    Spacer(Modifier.width(14.dp))
                }

                if (elementVisibility.trackInfo) ExpandedTrackInfoColumn(
                    mediaInfo = mediaInfo,
                    trackInfoStyle = trackInfoStyle,
                    accentColor = accentColor,
                    fontStyle = fontStyle,
                    titleTextStyle = MaterialTheme.typography.headlineSmall,
                    artistTextStyle = MaterialTheme.typography.titleMedium,
                    artistAlpha = 0.80f,
                    titleArtistSpacing = 4.dp,
                    onClick = { onTrackInfoClick?.invoke() ?: openPlayerApp(context, mediaInfo) },
                    highlight = highlightTrackInfo,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                )

                // Controls Column: 5-bar visualizer in a fixed container stacked directly above the Play/Pause button
                if (elementVisibility.visualizer || elementVisibility.mainControls) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .height(20.dp)
                            .then(
                                if (highlightVisualizer) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .then(
                                if (onVisualizerClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onVisualizerClick)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerWaveform(
                            mediaInfo = mediaInfo,
                            maxHeightDp = 20f,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 3.2.dp else 4.dp,
                            barSpacing = if (waveformBandCount > 5) 2.4.dp else 3.dp,
                            minHeight = 4.dp,
                            barCornerRadius = 2.dp,
                        )
                    }
                    if (elementVisibility.visualizer && elementVisibility.mainControls) Spacer(Modifier.height(24.dp))
                    if (elementVisibility.mainControls) {
                        val playOuter = playbackControlsStyle.cornerRadiusDp.dp
                        val playInner = (playbackControlsStyle.cornerRadiusDp * 0.35f).coerceIn(3f, 8f).dp
                        val playShape = RoundedCornerShape(
                            if (playbackControlsStyle.material3ButtonGroup) playInner else playOuter
                        )
                        val hasPlayBg = playbackControlsStyle.playPauseBackground
                        val usePlayAccent = playbackControlsStyle.playPauseAccentColor
                        val playBgColor = if (hasPlayBg) {
                            if (usePlayAccent) accentColor else Color.White.copy(alpha = 0.94f)
                        } else Color.Transparent
                        val playIconTint = playPauseIconTint(
                            hasBackground = hasPlayBg,
                            useAccentColor = usePlayAccent,
                            accentColor = accentColor,
                        )
                        Surface(
                            onClick = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
                            shape = playShape,
                            color = playBgColor,
                            modifier = Modifier
                                .width(64.dp)
                                .height(46.dp)
                                .then(
                                    if (highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, playShape)
                                    else Modifier
                                )
                                .graphicsLayer {
                                    if (bpmPulseEnabled && mediaInfo.isPlaying) {
                                        val scale = 1f + (0.08f * bpmPulseState.value * pulseAmount)
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                },
                            shadowElevation = if (hasPlayBg) 2.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PlaybackIcon(
                                    playing = mediaInfo.isPlaying,
                                    tint = playIconTint,
                                    size = 26.dp,
                                )
                            }
                        }
                    }
                }
            }

            // Row 3: Scrubber Line with M3 Expressive Wavy Indicator & Full Action Controls
            if (elementVisibility.progress || elementVisibility.mainControls || elementVisibility.appActions) Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val skipOuter = playbackControlsStyle.cornerRadiusDp.dp
                val skipInner = (playbackControlsStyle.cornerRadiusDp * 0.35f).coerceIn(3f, 8f).dp
                val prevShape = if (playbackControlsStyle.material3ButtonGroup) {
                    RoundedCornerShape(topStart = skipOuter, bottomStart = skipOuter, topEnd = skipInner, bottomEnd = skipInner)
                } else RoundedCornerShape(skipOuter)
                val nextShape = if (playbackControlsStyle.material3ButtonGroup) {
                    RoundedCornerShape(topStart = skipInner, bottomStart = skipInner, topEnd = skipOuter, bottomEnd = skipOuter)
                } else RoundedCornerShape(skipOuter)
                val skipBgColor = if (playbackControlsStyle.skipBackground) Color.White.copy(alpha = 0.14f) else Color.Transparent

                if (elementVisibility.mainControls) Surface(
                    onClick = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
                    shape = prevShape,
                    color = skipBgColor,
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, prevShape)
                            else Modifier
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = LarpIcons.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                if (elementVisibility.progress) ExpandedMusicProgressBar(
                    durationMs = mediaInfo.durationMs,
                    isPlaying = mediaInfo.isPlaying,
                    activeFractionProvider = activeFractionProvider,
                    accentColor = accentColor,
                    useAccentColor = progressBarAccentColor,
                    showSquiggles = showProgressSquiggles,
                    onDragStart = { fraction ->
                        isDragging = true
                        dragFraction = fraction
                    },
                    onDragChange = { fraction -> dragFraction = fraction },
                    onDragEnd = { fraction ->
                        if (mediaInfo.durationMs > 0) {
                            MediaPlaybackState.seekTo((fraction * mediaInfo.durationMs).toLong())
                        }
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onProgressBarClick = onProgressBarClick,
                    highlight = highlightProgressBar,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )

                if (elementVisibility.mainControls) Surface(
                    onClick = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
                    shape = nextShape,
                    color = skipBgColor,
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, nextShape)
                            else Modifier
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = LarpIcons.SkipNext,
                            contentDescription = "Next track",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                if (elementVisibility.appActions) mediaInfo.sessionActions
                    .filter { it.iconResourceId != 0 && it.iconPackageName != null }
                    .take(3)
                    .forEach { action ->
                    val appIcon = remember(action.iconPackageName, action.iconResourceId) {
                        loadMediaSessionActionIcon(context, action.iconPackageName, action.iconResourceId)
                    }
                    if (appIcon != null) {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                MediaPlaybackState.performSessionAction(action)
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            val iconTint = if (action.active) accentColor else Color.White.copy(alpha = 0.82f)
                            Image(
                                bitmap = appIcon.asImageBitmap(),
                                contentDescription = action.label,
                                colorFilter = ColorFilter.tint(iconTint),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        } else if (playerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE) {
            val materialSessionActions = mediaInfo.sessionActions
                .filter { it.iconResourceId != 0 && it.iconPackageName != null }
                .take(3)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (topContentInset > 0.dp) topContentInset else 18.dp,
                        bottom = 20.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                if (showAlbumArt && mediaInfo.albumArt != null) {
                    ExpandedArtwork(
                        artwork = mediaInfo.albumArt,
                        anchor = artworkAnchor,
                        glow = artworkGlow,
                        shape = artworkShape,
                        opacity = { albumArtPlaybackAlpha },
                        progressProvider = expandedArtProgressProvider,
                        progressColor = accentColor,
                        progressStrokeWidth = 3.5.dp,
                        progressGap = 3.dp,
                        modifier = Modifier
                            .size(140.dp)
                            .then(
                                if (highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, artworkShape)
                                else Modifier
                            )
                            .graphicsLayer {
                                scaleX = albumArtPlaybackScale
                                scaleY = albumArtPlaybackScale
                                alpha = albumArtPlaybackAlpha
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .clickable { onAlbumArtClick?.invoke() ?: openPlayerApp(context, mediaInfo) },
                    )
                }

                if (elementVisibility.trackInfo || elementVisibility.visualizer) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (elementVisibility.trackInfo) ExpandedTrackInfoColumn(
                        mediaInfo = mediaInfo,
                        trackInfoStyle = trackInfoStyle,
                        accentColor = accentColor,
                        fontStyle = fontStyle,
                        titleTextStyle = MaterialTheme.typography.titleMedium,
                        artistTextStyle = MaterialTheme.typography.bodyMedium,
                        artistAlpha = 0.72f,
                        titleArtistSpacing = 2.dp,
                        textAlign = TextAlign.Start,
                        bpmChip = if (elementVisibility.bpm && currentBpm != null) {
                            { BpmChip(bpm = currentBpm) }
                        } else null,
                        onClick = { onTrackInfoClick?.invoke() ?: openPlayerApp(context, mediaInfo) },
                        highlight = highlightTrackInfo,
                        modifier = Modifier.weight(1f),
                    )
                    if (elementVisibility.trackInfo && elementVisibility.visualizer) Spacer(Modifier.width(14.dp))
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 20.dp)
                            .then(
                                if (highlightVisualizer) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .then(
                                if (onVisualizerClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onVisualizerClick)
                                else Modifier
                            )
                            .padding(end = 14.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        EqualizerWaveform(
                            mediaInfo = mediaInfo,
                            maxHeightDp = 20f,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 2.8.dp else 3.5.dp,
                            barSpacing = if (waveformBandCount > 5) 2.dp else 2.5.dp,
                            minHeight = 3.dp,
                        )
                    }
                }

                if (elementVisibility.progress) ExpandedMusicProgressBar(
                    durationMs = mediaInfo.durationMs,
                    isPlaying = mediaInfo.isPlaying,
                    activeFractionProvider = activeFractionProvider,
                    accentColor = accentColor,
                    useAccentColor = progressBarAccentColor,
                    showSquiggles = showProgressSquiggles,
                    onDragStart = { fraction ->
                        isDragging = true
                        dragFraction = fraction
                    },
                    onDragChange = { fraction -> dragFraction = fraction },
                    onDragEnd = { fraction ->
                        if (mediaInfo.durationMs > 0) {
                            MediaPlaybackState.seekTo((fraction * mediaInfo.durationMs).toLong())
                        }
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onProgressBarClick = onProgressBarClick,
                    highlight = highlightProgressBar,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (elementVisibility.mainControls) ExpandedPlaybackControlsRow(
                    isPlaying = mediaInfo.isPlaying,
                    accentColor = accentColor,
                    style = playbackControlsStyle,
                    skipWidth = 60.dp,
                    skipHeight = 50.dp,
                    skipIconSize = 28.dp,
                    playWidth = 68.dp,
                    playHeight = 56.dp,
                    playIconSize = 31.dp,
                    defaultSpacing = 8.dp,
                    bpmPulseScale = {
                        if (bpmPulseEnabled && mediaInfo.isPlaying) 1f + (0.08f * bpmPulseState.value * pulseAmount)
                        else 1f
                    },
                    onPrevious = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
                    onPlayPause = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
                    onNext = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
                    highlight = highlightPlaybackControls,
                )

                if (elementVisibility.appActions && materialSessionActions.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        materialSessionActions.forEach { action ->
                        val appIcon = remember(action.iconPackageName, action.iconResourceId) {
                            loadMediaSessionActionIcon(context, action.iconPackageName, action.iconResourceId)
                        }
                        if (appIcon != null) {
                            IconButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaPlaybackState.performSessionAction(action)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Image(
                                    bitmap = appIcon.asImageBitmap(),
                                    contentDescription = action.label,
                                    colorFilter = ColorFilter.tint(
                                        if (action.active) accentColor else Color.White.copy(alpha = 0.82f)
                                    ),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 22.dp,
                        end = 22.dp,
                        top = if (topContentInset > 0.dp) topContentInset else 16.dp,
                        bottom = 18.dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (showAlbumArt && mediaInfo.albumArt != null) {
                        ExpandedArtwork(
                            artwork = mediaInfo.albumArt,
                            anchor = artworkAnchor,
                            glow = artworkGlow,
                            shape = RoundedCornerShape(12.dp),
                            opacity = { albumArtPlaybackAlpha },
                            progressProvider = expandedArtProgressProvider,
                            progressColor = accentColor,
                            progressStrokeWidth = 2.5.dp,
                            modifier = Modifier
                                .size(64.dp)
                                .then(
                                    if (highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .then(if (onAlbumArtClick != null) Modifier.clickable(onClick = onAlbumArtClick) else Modifier)
                                .graphicsLayer {
                                    scaleX = albumArtPlaybackScale
                                    scaleY = albumArtPlaybackScale
                                    alpha = albumArtPlaybackAlpha
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                },
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (elementVisibility.trackInfo) ExpandedTrackInfoColumn(
                        mediaInfo = mediaInfo,
                        trackInfoStyle = trackInfoStyle,
                        accentColor = accentColor,
                        fontStyle = fontStyle,
                        titleTextStyle = MaterialTheme.typography.titleLarge,
                        artistTextStyle = MaterialTheme.typography.bodyMedium,
                        artistAlpha = 0.62f,
                        titleArtistSpacing = 3.dp,
                        bpmChip = if (elementVisibility.bpm && currentBpm != null) {
                            { BpmChip(bpm = currentBpm) }
                        } else null,
                        onClick = { onTrackInfoClick?.invoke() ?: openPlayerApp(context, mediaInfo) },
                        highlight = highlightTrackInfo,
                        modifier = Modifier.weight(1f),
                    )
                    if (elementVisibility.trackInfo && elementVisibility.visualizer) Spacer(Modifier.width(12.dp))
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 20.dp)
                            .then(
                                if (highlightVisualizer) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .then(
                                if (onVisualizerClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onVisualizerClick)
                                else Modifier
                            )
                            .padding(end = 14.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        EqualizerWaveform(
                            mediaInfo = mediaInfo,
                            maxHeightDp = 20f,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 2.8.dp else 3.5.dp,
                            barSpacing = if (waveformBandCount > 5) 2.dp else 2.5.dp,
                            minHeight = 3.dp,
                        )
                    }
                }

                if (elementVisibility.progress) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val timeTextStyle = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = fontStyle.fontFamily,
                        fontWeight = androidx.compose.ui.text.font.FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
                    )
                    MediaTimeLabel(
                        positionState = livePositionMs,
                        format = ::formatTime,
                        style = timeTextStyle,
                    )
                    ExpandedMusicProgressBar(
                        durationMs = mediaInfo.durationMs,
                        isPlaying = mediaInfo.isPlaying,
                        activeFractionProvider = activeFractionProvider,
                        accentColor = accentColor,
                        useAccentColor = progressBarAccentColor,
                        showSquiggles = showProgressSquiggles,
                        onDragStart = { fraction ->
                            isDragging = true
                            dragFraction = fraction
                        },
                        onDragChange = { fraction -> dragFraction = fraction },
                        onDragEnd = { fraction ->
                            if (mediaInfo.durationMs > 0) {
                                MediaPlaybackState.seekTo((fraction * mediaInfo.durationMs).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                        onProgressBarClick = onProgressBarClick,
                        highlight = highlightProgressBar,
                        modifier = Modifier.weight(1f),
                    )
                    MediaTimeLabel(
                        positionState = livePositionMs,
                        format = { pos -> formatRemainingTime(pos, mediaInfo.durationMs) },
                        style = timeTextStyle,
                    )
                }

                if (elementVisibility.mainControls || elementVisibility.appActions) Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (elementVisibility.mainControls) ExpandedPlaybackControlsRow(
                        isPlaying = mediaInfo.isPlaying,
                        accentColor = accentColor,
                        style = playbackControlsStyle,
                        skipWidth = if (playbackControlsStyle.material3ButtonGroup || playbackControlsStyle.skipBackground) 44.dp else 38.dp,
                        skipHeight = 40.dp,
                        skipIconSize = 25.dp,
                        playWidth = if (playbackControlsStyle.material3ButtonGroup || playbackControlsStyle.playPauseBackground) 52.dp else 46.dp,
                        playHeight = 46.dp,
                        playIconSize = 30.dp,
                        defaultSpacing = 12.dp,
                        bpmPulseScale = {
                            if (bpmPulseEnabled && mediaInfo.isPlaying) 1f + (0.08f * bpmPulseState.value * pulseAmount)
                            else 1f
                        },
                        onPrevious = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
                        onPlayPause = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
                        onNext = { onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
                        highlight = highlightPlaybackControls,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    if (elementVisibility.appActions) mediaInfo.sessionActions
                        .filter { it.iconResourceId != 0 && it.iconPackageName != null }
                        .take(2)
                        .forEachIndexed { index, action ->
                            val appIcon = remember(action.iconPackageName, action.iconResourceId) {
                                loadMediaSessionActionIcon(context, action.iconPackageName, action.iconResourceId)
                            }
                            if (appIcon != null) {
                                IconButton(
                                    onClick = { MediaPlaybackState.performSessionAction(action) },
                                    modifier = Modifier
                                        .align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd)
                                        .size(38.dp),
                                ) {
                                    Image(
                                        bitmap = appIcon.asImageBitmap(),
                                        contentDescription = action.label,
                                        colorFilter = ColorFilter.tint(
                                            if (action.active) accentColor else Color.White.copy(alpha = 0.72f)
                                        ),
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}


@Composable
private fun ExpandedTrackInfoColumn(
    mediaInfo: MediaTrackInfo,
    trackInfoStyle: OverlayPreferences.TrackInfoStyle,
    accentColor: Color,
    fontStyle: OverlayPreferences.IslandFontStyle,
    titleTextStyle: androidx.compose.ui.text.TextStyle,
    artistTextStyle: androidx.compose.ui.text.TextStyle,
    artistAlpha: Float,
    titleArtistSpacing: Dp,
    onClick: () -> Unit,
    highlight: Boolean = false,
    textAlign: TextAlign? = null,
    bpmChip: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val showArtistRow = trackInfoStyle.showArtist || bpmChip != null
    val showAlbumRow = trackInfoStyle.showAlbum && mediaInfo.album.isNotBlank()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (highlight) Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                else Modifier
            )
            .clickable(onClick = onClick),
    ) {
        if (trackInfoStyle.showTitle) {
            MarqueeText(
                text = mediaInfo.title.ifEmpty { "No Media Playing" },
                style = titleTextStyle.copy(
                    fontFamily = fontStyle.fontFamily,
                    fontWeight = fontWeight,
                ),
                color = if (trackInfoStyle.titleAccentColor) accentColor else Color.White,
                textAlign = textAlign ?: TextAlign.Start,
            )
        }
        if (showArtistRow) {
            if (trackInfoStyle.showTitle) Spacer(Modifier.height(titleArtistSpacing))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trackInfoStyle.showArtist) {
                    MarqueeText(
                        text = mediaInfo.artist,
                        style = artistTextStyle.copy(
                            fontFamily = fontStyle.fontFamily,
                            fontWeight = fontWeight,
                        ),
                        color = Color.White.copy(alpha = artistAlpha),
                        textAlign = textAlign ?: TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (bpmChip != null) {
                    if (trackInfoStyle.showArtist && mediaInfo.artist.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                    }
                    bpmChip()
                }
            }
        }
        if (showAlbumRow) {
            if (trackInfoStyle.showTitle || showArtistRow) Spacer(Modifier.height(2.dp))
            MarqueeText(
                text = mediaInfo.album,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontStyle.fontFamily,
                    fontWeight = fontWeight,
                ),
                color = Color.White.copy(alpha = 0.58f),
                textAlign = textAlign ?: TextAlign.Start,
            )
        }
    }
}


@Composable
private fun ExpandedArtwork(
    artwork: Bitmap,
    anchor: ArtworkAnchor?,
    glow: AlbumArtGlowState,
    shape: Shape,
    opacity: () -> Float,
    modifier: Modifier = Modifier,
    progressProvider: (() -> Float)? = null,
    progressColor: Color = Color.White,
    progressStrokeWidth: Dp = 3.dp,
    progressGap: Dp = 2.dp,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (progressProvider != null) {
            RadialProgressRing(
                progressProvider = progressProvider,
                color = progressColor,
                trackColor = Color.White.copy(alpha = 0.28f),
                strokeWidth = progressStrokeWidth,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedContent(
            targetState = artwork,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (progressProvider != null) Modifier.padding(progressStrokeWidth + progressGap)
                    else Modifier
                )
                .albumArtGlow(glow, anchor)
                .clip(shape)
                .artworkTile(anchor, ArtworkStyle(shape), opacity),
            transitionSpec = { fadeOverPrevious(250) },
            label = "album_art",
        ) { bitmap ->
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<*>.fadeOverPrevious(durationMillis: Int) =
    (fadeIn(LarpMotion.fade(durationMillis)) togetherWith ExitTransition.KeepUntilTransitionsFinished)
        .apply { targetContentZIndex = 1f }
