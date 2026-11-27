package ca.saboor.larpdot.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaSessionAction
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.AudioOutputButton
import ca.saboor.larpdot.ui.components.AudioOutputChip
import ca.saboor.larpdot.ui.components.MarqueeText
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.BpmChip
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    cardWidth: Dp,
    cutoutCenterX: Dp,
    cutoutCenterY: Dp,
    modifier: Modifier = Modifier,
    topContentInset: Dp = 0.dp,
    albumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState().value,
    showAlbumArt: Boolean = OverlayPreferences.showExpandedAlbumArtFlow.collectAsState().value,
    artworkAnchor: ArtworkAnchor? = null,
    visualizerAnchor: IslandElementAnchor? = null,
    playerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.expandedPlayerLayoutFlow.collectAsState().value,
    showProgressSquiggles: Boolean = OverlayPreferences.showProgressSquigglesFlow.collectAsState().value,
    progressBarAccentColor: Boolean = OverlayPreferences.progressBarAccentColorFlow.collectAsState().value,
    timestampPlacement: OverlayPreferences.ProgressTimestampPlacement = OverlayPreferences.progressTimestampPlacementFlow.collectAsState().value,
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
    onAudioOutputClick: (() -> Unit)? = null,
    highlightAlbumArt: Boolean = false,
    highlightVisualizer: Boolean = false,
    highlightProgressBar: Boolean = false,
    highlightPlaybackControls: Boolean = false,
    highlightTrackInfo: Boolean = false,
    highlightAudioOutput: Boolean = false,
) {
    val context = LocalContext.current
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val artworkGlow = rememberAlbumArtGlow(
        media = mediaInfo,
        shape = artworkShape,
        shapeKey = AlbumArtGlowShapeKey.of(nestedShape, nestedRotation),
    )
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val accentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor

    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val audioOutputStyle by OverlayPreferences.audioOutputStyleFlow.collectAsState()
    val quickSeekSeconds by OverlayPreferences.musicQuickSeekSecondsFlow.collectAsState()
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

    val cardDragOffsetAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val maxCardDragOffsetPx = with(density) { 8.dp.toPx() }

    val scrubCallbacks = remember(mediaInfo.durationMs) {
        ScrubCallbacks(
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
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Stretch the side being swiped toward instead of translating
                val stretchMag = cardDragOffsetAnim.value / maxCardDragOffsetPx // -1..1
                val maxStretch = 0.04f // 4% max stretch on expanded card
                scaleX = 1f + kotlin.math.abs(stretchMag) * maxStretch
                transformOrigin = if (stretchMag >= 0f) {
                    TransformOrigin(0f, 0.5f) // swiping right -> pivot left, stretch right
                } else {
                    TransformOrigin(1f, 0.5f) // swiping left -> pivot right, stretch left
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
                                hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipNext()
                            } else if (totalDragX < -swipeThresholdPx) {
                                hasTriggered = true
                                change.consume()
                                hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
                                MediaPlaybackState.skipPrevious()
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCollapse() })
            }
    ) {
        ExpandedMusicBackground(
            albumArt = mediaInfo.albumArt,
            albumArtStyle = albumArtStyle,
            playerLayout = playerLayout,
            backgroundOpacity = backgroundOpacity,
            modifier = Modifier.matchParentSize(),
        )

        CameraCover(
            style = cameraCoverStyle,
            centerX = cutoutCenterX,
            centerY = cutoutCenterY,
            diameter = cutoutDiameterDp,
            modifier = Modifier.matchParentSize(),
        )

        if (showDominantGlow && (glowSides.left || glowSides.right || glowSides.bottom || glowSides.top)) {
            ExpandedDominantGlowLayer(
                accentColor = accentColor,
                glowSides = glowSides,
                modifier = Modifier.matchParentSize(),
                glowAlphaProvider = {
                    if (bpmPulseEnabled && mediaInfo.isPlaying) {
                        0.16f + (0.22f * bpmPulseState.value * pulseAmount)
                    } else {
                        0.25f
                    }
                },
            )
        }

        val bpmPulseScale: () -> Float = {
            if (bpmPulseEnabled && mediaInfo.isPlaying) 1f + (0.08f * bpmPulseState.value * pulseAmount) else 1f
        }
        val cutoutFromStart = if (LocalLayoutDirection.current == LayoutDirection.Ltr) cutoutCenterX else cardWidth - cutoutCenterX
        val layoutParams = ExpandedLayoutParams(
            context = context,
            hapticFeedback = hapticFeedback,
            mediaInfo = mediaInfo,
            livePositionMs = livePositionMs,
            topContentInset = topContentInset,
            cardWidth = cardWidth,
            cutoutStart = cutoutFromStart - cutoutDiameterDp / 2f,
            showAlbumArt = showAlbumArt,
            artworkAnchor = artworkAnchor,
            visualizerAnchor = visualizerAnchor,
            artworkGlow = artworkGlow,
            artworkShape = artworkShape,
            albumArtPlaybackScale = albumArtPlaybackScale,
            albumArtPlaybackAlpha = albumArtPlaybackAlpha,
            expandedArtProgressProvider = expandedArtProgressProvider,
            accentColor = accentColor,
            currentBpm = currentBpm,
            audioOutputStyle = audioOutputStyle,
            quickSeekStepMs = quickSeekSeconds.coerceIn(5, 60) * 1000L,
            trackInfoStyle = trackInfoStyle,
            fontStyle = fontStyle,
            elementVisibility = elementVisibility,
            waveformBandCount = waveformBandCount,
            playbackControlsStyle = playbackControlsStyle,
            progressBarAccentColor = progressBarAccentColor,
            timestampPlacement = timestampPlacement,
            showProgressSquiggles = showProgressSquiggles,
            activeFractionProvider = activeFractionProvider,
            scrubCallbacks = scrubCallbacks,
            bpmPulseScale = bpmPulseScale,
            onCollapse = onCollapse,
            onAlbumArtClick = onAlbumArtClick,
            onTrackInfoClick = onTrackInfoClick,
            onAudioOutputClick = onAudioOutputClick,
            onVisualizerClick = onVisualizerClick,
            onProgressBarClick = onProgressBarClick,
            onPlaybackControlsClick = onPlaybackControlsClick,
            highlightAlbumArt = highlightAlbumArt,
            highlightTrackInfo = highlightTrackInfo,
            highlightAudioOutput = highlightAudioOutput,
            highlightVisualizer = highlightVisualizer,
            highlightProgressBar = highlightProgressBar,
            highlightPlaybackControls = highlightPlaybackControls,
        )

        when (playerLayout) {
            OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS -> ExpandedAndroidMediaLayout(layoutParams)
            OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE -> ExpandedMaterialExpressiveLayout(layoutParams)
            OverlayPreferences.ExpandedPlayerLayout.IPHONE -> ExpandedClassicMediaLayout(layoutParams)
        }
    }
}

private class ExpandedLayoutParams(
    val context: Context,
    val hapticFeedback: HapticFeedback,
    val mediaInfo: MediaTrackInfo,
    val livePositionMs: State<Long>,
    val topContentInset: Dp,
    val cardWidth: Dp,
    val cutoutStart: Dp,
    val showAlbumArt: Boolean,
    val artworkAnchor: ArtworkAnchor?,
    val visualizerAnchor: IslandElementAnchor?,
    val artworkGlow: AlbumArtGlowState,
    val artworkShape: Shape,
    val albumArtPlaybackScale: Float,
    val albumArtPlaybackAlpha: Float,
    val expandedArtProgressProvider: (() -> Float)?,
    val accentColor: Color,
    val currentBpm: Float?,
    val audioOutputStyle: OverlayPreferences.AudioOutputStyle,
    val quickSeekStepMs: Long,
    val trackInfoStyle: OverlayPreferences.TrackInfoStyle,
    val fontStyle: OverlayPreferences.IslandFontStyle,
    val elementVisibility: OverlayPreferences.ExpandedElementVisibility,
    val waveformBandCount: Int,
    val playbackControlsStyle: OverlayPreferences.PlaybackControlsStyle,
    val progressBarAccentColor: Boolean,
    val timestampPlacement: OverlayPreferences.ProgressTimestampPlacement,
    val showProgressSquiggles: Boolean,
    val activeFractionProvider: () -> Float,
    val scrubCallbacks: ScrubCallbacks,
    val bpmPulseScale: () -> Float,
    val onCollapse: () -> Unit,
    val onAlbumArtClick: (() -> Unit)?,
    val onTrackInfoClick: (() -> Unit)?,
    val onAudioOutputClick: (() -> Unit)?,
    val onVisualizerClick: (() -> Unit)?,
    val onProgressBarClick: (() -> Unit)?,
    val onPlaybackControlsClick: (() -> Unit)?,
    val highlightAlbumArt: Boolean,
    val highlightTrackInfo: Boolean,
    val highlightAudioOutput: Boolean,
    val highlightVisualizer: Boolean,
    val highlightProgressBar: Boolean,
    val highlightPlaybackControls: Boolean,
) {
    val showsArtwork get() = showAlbumArt && mediaInfo.albumArt != null
    val showsTrackText get() = elementVisibility.trackInfo &&
        (trackInfoStyle.showTitle || (trackInfoStyle.showArtist && mediaInfo.artist.isNotBlank()) ||
            (trackInfoStyle.showAlbum && mediaInfo.album.isNotBlank()))
    val showsAudioOutputChip get() = audioOutputStyle == OverlayPreferences.AudioOutputStyle.CHIP
    val showsAudioOutputButton get() = audioOutputStyle == OverlayPreferences.AudioOutputStyle.BUTTON
    val showsTrackInfoWithBpm get() = showsTrackText || showsAudioOutputChip ||
        (elementVisibility.trackInfo && elementVisibility.bpm && currentBpm != null)
    val visibleTrackInfoStyle get() = if (elementVisibility.trackInfo) trackInfoStyle
        else trackInfoStyle.copy(showTitle = false, showArtist = false, showAlbum = false)
    val sessionActions get() = if (elementVisibility.appActions) {
        mediaInfo.sessionActions.filter { it.iconResourceId != 0 && it.iconPackageName != null }
    } else emptyList()
}

private class ScrubCallbacks(
    val onDragStart: (Float) -> Unit,
    val onDragChange: (Float) -> Unit,
    val onDragEnd: (Float) -> Unit,
    val onDragCancel: () -> Unit,
)

@Composable
private fun ExpandedMusicBackground(
    albumArt: Bitmap?,
    albumArtStyle: OverlayPreferences.ExpandedBackgroundStyle,
    playerLayout: OverlayPreferences.ExpandedPlayerLayout,
    backgroundOpacity: Float,
    modifier: Modifier,
) {
    if (albumArt == null) return
    val density = LocalDensity.current
    val isVertical = playerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE
    val imageBitmap = remember(albumArt) { albumArt.asImageBitmap() }

    Box(modifier.graphicsLayer { alpha = backgroundOpacity }) {
        when (albumArtStyle) {
            OverlayPreferences.ExpandedBackgroundStyle.NONE -> Unit
            OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND,
            OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Background album art",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
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
            }
            OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED,
            OverlayPreferences.ExpandedBackgroundStyle.BLENDED -> {
                val isBlended = albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLENDED
                val bitmap = imageBitmap
                val side = minOf(bitmap.width, bitmap.height)
                val cropX = (bitmap.width - side) / 2
                val cropY = (bitmap.height - side) / 2
                val eighth = side / 8

                if (isVertical) {
                    val alphaStops = if (isBlended) {
                        arrayOf(
                            0.00f to Color.Transparent,
                            0.20f to Color.Transparent,
                            0.50f to Color.White.copy(alpha = 0.45f),
                            0.80f to Color.White.copy(alpha = 0.85f),
                            1.00f to Color.White,
                        )
                    } else {
                        arrayOf(
                            0.00f to Color.Transparent,
                            0.20f to Color.Transparent,
                            0.50f to Color.White.copy(alpha = 0.30f),
                            0.80f to Color.White.copy(alpha = 0.65f),
                            1.00f to Color.White.copy(alpha = 0.70f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                if (isBlended) drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(
                                    brush = Brush.verticalGradient(colorStops = alphaStops),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isBlended) {
                                        Modifier.progressiveBlur(
                                            direction = 2,
                                            maxBlurDp = 40.dp,
                                            startFraction = 0.20f,
                                        )
                                    } else Modifier
                                ),
                        ) {
                            drawVerticalAlbumWithReflection(bitmap, cropX, cropY, side, eighth)
                        }
                    }
                } else {
                    val realArtHeight = 190.dp
                    val topReflectionHeight = 30.dp
                    val totalArtWidth = if (isBlended) realArtHeight * 1.5f else realArtHeight
                    val topSeamPx = with(density) { topReflectionHeight.toPx() }
                    val realArtPx = with(density) { realArtHeight.toPx() }
                    val fadeBrush = if (isBlended) {
                        Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
                    } else {
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White.copy(alpha = 0.65f),
                                1.00f to Color.Transparent,
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(totalArtWidth)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                if (isBlended) drawRect(Color.Black.copy(alpha = 0.35f))
                                drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                            },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .progressiveBlur(
                                    direction = 3,
                                    maxBlurDp = 48.dp,
                                    topSeamPx = topSeamPx,
                                    rightSeamPx = if (isBlended) realArtPx else 99999f,
                                ),
                        ) {
                            drawHorizontalAlbumWithReflections(
                                bitmap = bitmap,
                                cropX = cropX,
                                cropY = cropY,
                                side = side,
                                eighth = eighth,
                                realArtW = if (isBlended) realArtPx else size.width,
                                realArtH = realArtPx,
                                topRefH = topSeamPx,
                                includeRightAndCorner = isBlended,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawVerticalAlbumWithReflection(
    bitmap: ImageBitmap,
    cropX: Int,
    cropY: Int,
    side: Int,
    eighth: Int,
) {
    val artW = size.width
    val topSpace = size.height - artW
    if (topSpace <= 0f) {
        val visibleFraction = (size.height / artW).coerceIn(0f, 1f)
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
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(cropX, cropY),
            srcSize = IntSize(side, side),
            dstOffset = IntOffset(0, topSpace.roundToInt()),
            dstSize = IntSize(artW.roundToInt(), artW.roundToInt()),
        )
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

private fun DrawScope.drawHorizontalAlbumWithReflections(
    bitmap: ImageBitmap,
    cropX: Int,
    cropY: Int,
    side: Int,
    eighth: Int,
    realArtW: Float,
    realArtH: Float,
    topRefH: Float,
    includeRightAndCorner: Boolean,
) {
    // 1. Primary 1:1 album art below top reflection
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(cropX, cropY),
        srcSize = IntSize(side, side),
        dstOffset = IntOffset(0, topRefH.roundToInt()),
        dstSize = IntSize(realArtW.roundToInt(), realArtH.roundToInt()),
    )
    // 2. Flipped 1/8 top reflection
    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(realArtW / 2f, topRefH / 2f)) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(cropX, cropY),
            srcSize = IntSize(side, eighth),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(realArtW.roundToInt(), topRefH.roundToInt()),
        )
    }
    if (!includeRightAndCorner) return
    val rightRefW = size.width - realArtW
    // 3. Flipped 1/8 right reflection
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(realArtW + rightRefW / 2f, topRefH + realArtH / 2f)) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(cropX + side - eighth, cropY),
            srcSize = IntSize(eighth, side),
            dstOffset = IntOffset(realArtW.roundToInt(), topRefH.roundToInt()),
            dstSize = IntSize(rightRefW.roundToInt(), realArtH.roundToInt()),
        )
    }
    // 4. Flipped 1/8 corner reflection
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

@Composable
private fun ExpandedDominantGlowLayer(
    accentColor: Color,
    glowSides: OverlayPreferences.ExpandedGlowSides,
    glowAlphaProvider: () -> Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.drawWithCache {
            val leftGradient = Brush.horizontalGradient(
                colorStops = arrayOf(0.00f to accentColor, 0.40f to Color.Transparent)
            )
            val rightGradient = Brush.horizontalGradient(
                colorStops = arrayOf(0.60f to Color.Transparent, 1.00f to accentColor)
            )
            val bottomGradient = Brush.verticalGradient(
                colorStops = arrayOf(0.40f to Color.Transparent, 1.00f to accentColor)
            )
            val topGradient = Brush.verticalGradient(
                colorStops = arrayOf(0.00f to accentColor, 0.40f to Color.Transparent)
            )
            onDrawBehind {
                val alpha = glowAlphaProvider()
                if (glowSides.left) drawRect(leftGradient, alpha = alpha)
                if (glowSides.right) drawRect(rightGradient, alpha = alpha)
                if (glowSides.bottom) drawRect(bottomGradient, alpha = alpha)
                if (glowSides.top) drawRect(topGradient, alpha = alpha)
            }
        }
    )
}

@Composable
private fun ExpandedMusicHeader(
    p: ExpandedLayoutParams,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    fixedWidth: Dp,
    showsInlineBpm: Boolean = p.elementVisibility.trackInfo && p.elementVisibility.bpm && p.currentBpm != null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable RowScope.(visualizerMaxWidth: Dp) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val trackStyle = p.visibleTrackInfoStyle
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    fun textWidth(text: String, style: TextStyle): Dp = with(density) {
        textMeasurer.measure(
            text,
            style.copy(fontFamily = p.fontStyle.fontFamily, fontWeight = fontWeight),
            maxLines = 1,
            softWrap = false,
        ).size.width.toDp()
    }
    val artist = p.mediaInfo.artist.takeIf { trackStyle.showArtist && it.isNotBlank() }
    val album = p.mediaInfo.album.takeIf { trackStyle.showAlbum && it.isNotBlank() }
    val inlineAlbum = trackStyle.albumBesideArtist && artist != null && album != null
    val bpmWidth = if (showsInlineBpm) 64.dp else 0.dp
    val desiredTrackWidth = if (p.elementVisibility.visualizer) maxOf(
        if (trackStyle.showTitle) textWidth(p.mediaInfo.title.ifEmpty { "No Media Playing" }, titleStyle) else 0.dp,
        (if (artist != null) textWidth(if (inlineAlbum) "$artist • $album" else artist, artistStyle) else 0.dp) + bpmWidth,
        if (album != null && !inlineAlbum) textWidth(album, MaterialTheme.typography.bodySmall) else 0.dp,
        if (p.showsAudioOutputChip) 84.dp else 0.dp,
    ) else 0.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = (maxWidth - fixedWidth).coerceAtLeast(0.dp)
        val visualizerMaxWidth = (availableWidth - desiredTrackWidth).coerceIn(minOf(22.dp, availableWidth), availableWidth)
        Row(Modifier.fillMaxWidth(), verticalAlignment = verticalAlignment) {
            content(visualizerMaxWidth)
        }
        overlay?.invoke(this)
    }
}

@Composable
private fun ExpandedVisualizerSlot(
    mediaInfo: MediaTrackInfo,
    waveformBandCount: Int,
    minHeight: Dp,
    highlight: Boolean,
    onClick: (() -> Unit)?,
    visualizerAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
    barCornerRadius: Dp = 1.5.dp,
    contentAlignment: Alignment = Alignment.Center,
) {
    val barWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val barSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    FittedEqualizerWaveform(
        mediaInfo = mediaInfo,
        maxHeightDp = 20f,
        barCount = waveformBandCount,
        barWidth = barWidth,
        barSpacing = barSpacing,
        visualizerAnchor = visualizerAnchor,
        modifier = modifier
            .then(
                if (highlight) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = contentAlignment,
        minHeight = minHeight,
        barCornerRadius = barCornerRadius,
    )
}

@Composable
private fun ExpandedSessionActionButton(
    context: Context,
    action: MediaSessionAction,
    accentColor: Color,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    inactiveAlpha: Float = 0.82f,
    hapticFeedback: HapticFeedback? = null,
) {
    val appIcon = remember(action.iconPackageName, action.iconResourceId) {
        loadMediaSessionActionIcon(context, action.iconPackageName, action.iconResourceId)?.asImageBitmap()
    } ?: return
    IconButton(
        onClick = {
            hapticFeedback?.performIslandHaptic(HapticFeedbackType.LongPress)
            MediaPlaybackState.performSessionAction(action)
        },
        modifier = modifier.size(buttonSize),
    ) {
        Image(
            bitmap = appIcon,
            contentDescription = action.label,
            colorFilter = ColorFilter.tint(
                if (action.active) accentColor else Color.White.copy(alpha = inactiveAlpha)
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ExpandedTrackProgress(p: ExpandedLayoutParams, modifier: Modifier = Modifier) {
    val bar: @Composable (Modifier) -> Unit = { barModifier ->
        ExpandedMusicProgressBar(
            durationMs = p.mediaInfo.durationMs,
            isPlaying = p.mediaInfo.isPlaying,
            activeFractionProvider = p.activeFractionProvider,
            accentColor = p.accentColor,
            useAccentColor = p.progressBarAccentColor,
            showSquiggles = p.showProgressSquiggles,
            onDragStart = p.scrubCallbacks.onDragStart,
            onDragChange = p.scrubCallbacks.onDragChange,
            onDragEnd = p.scrubCallbacks.onDragEnd,
            onDragCancel = p.scrubCallbacks.onDragCancel,
            onProgressBarClick = p.onProgressBarClick,
            highlight = p.highlightProgressBar,
            modifier = barModifier.layout { measurable, constraints ->
                val bar = measurable.measure(constraints)
                layout(bar.width, bar.height, mapOf(ScrubberCenter to bar.height / 2)) {
                    bar.placeRelative(0, 0)
                }
            },
        )
    }
    if (p.timestampPlacement == OverlayPreferences.ProgressTimestampPlacement.HIDDEN) {
        bar(modifier)
        return
    }

    val timeTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = p.fontStyle.fontFamily,
        fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
    )
    var showTotalDuration by remember { mutableStateOf(false) }
    val elapsed: @Composable () -> Unit = {
        MediaTimeLabel(positionState = p.livePositionMs, format = ::formatTime, style = timeTextStyle)
    }
    val trailingTime: @Composable () -> Unit = {
        MediaTimeLabel(
            positionState = p.livePositionMs,
            format = { position ->
                if (showTotalDuration) formatTotalDuration(p.mediaInfo.durationMs)
                else formatRemainingTime(position, p.mediaInfo.durationMs)
            },
            style = timeTextStyle,
            onClick = {
                p.hapticFeedback.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                showTotalDuration = !showTotalDuration
            },
        )
    }

    if (p.timestampPlacement == OverlayPreferences.ProgressTimestampPlacement.BESIDE) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            elapsed()
            bar(Modifier.weight(1f))
            trailingTime()
        }
    } else {
        Column(modifier = modifier) {
            bar(Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                elapsed()
                trailingTime()
            }
        }
    }
}

private val ScrubberCenter = HorizontalAlignmentLine(::minOf)

private fun quickSeekHandler(p: ExpandedLayoutParams, deltaMs: Long): (() -> Unit)? =
    if (p.onPlaybackControlsClick == null && p.mediaInfo.durationMs > 0L) {
        {
            p.hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
            MediaPlaybackState.seekBy(deltaMs)
        }
    } else null

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedAudioOutputControl(p: ExpandedLayoutParams) {
    val onClick = p.onAudioOutputClick ?: {
        AudioOutputTracker.openOutputSwitcher(p.context, p.mediaInfo.playerPackageName)
        p.onCollapse()
    }
    if (p.showsAudioOutputChip) {
        AudioOutputChip(onClick = onClick, highlighted = p.highlightAudioOutput)
    } else {
        AudioOutputButton(onClick = onClick, highlighted = p.highlightAudioOutput)
    }
}

@Composable
private fun rememberEstimatedTrackInfoHeightDp(
    p: ExpandedLayoutParams,
    titleTextStyle: TextStyle,
    artistTextStyle: TextStyle,
    titleArtistSpacing: Dp,
    showInlineBpm: Boolean,
    showFooterChip: Boolean,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val trackStyle = p.visibleTrackInfoStyle
    val albumTextStyle = MaterialTheme.typography.bodySmall
    val chipTextStyle = MaterialTheme.typography.labelSmall
    val showTitle = trackStyle.showTitle
    val showArtist = trackStyle.showArtist && p.mediaInfo.artist.isNotBlank()
    val showAlbum = trackStyle.showAlbum && p.mediaInfo.album.isNotBlank()
    val inlineAlbum = trackStyle.albumBesideArtist && showArtist && showAlbum
    val showArtistRow = showArtist || showInlineBpm
    fun lineHeightDp(style: TextStyle): Dp = with(density) {
        textMeasurer.measure(
            "Ag",
            style.copy(fontFamily = p.fontStyle.fontFamily, fontWeight = fontWeight),
            maxLines = 1,
            softWrap = false,
        ).size.height.toDp()
    }
    var total = 0.dp
    if (showTitle) {
        total += lineHeightDp(titleTextStyle)
    }
    if (showArtistRow) {
        if (showTitle) total += titleArtistSpacing
        val artistH = if (showArtist) lineHeightDp(artistTextStyle) else 0.dp
        val bpmH = if (showInlineBpm) maxOf(12.dp, lineHeightDp(chipTextStyle)) + 6.dp else 0.dp
        total += maxOf(artistH, bpmH)
    }
    if (showFooterChip) {
        if (showTitle || showArtistRow) total += 6.dp
        total += maxOf(13.dp, lineHeightDp(chipTextStyle)) + 4.dp
    }
    if (showAlbum && !inlineAlbum) {
        if (showTitle || showArtistRow || showFooterChip) total += 2.dp
        total += lineHeightDp(albumTextStyle)
    }
    if (p.highlightTrackInfo && total > 0.dp) total += 8.dp
    return total
}

@Composable
private fun RowScope.ExpandedHeaderArtwork(
    p: ExpandedLayoutParams,
    shape: Shape,
    artworkSize: Dp,
    artworkTop: Dp,
) {
    val artwork = p.mediaInfo.albumArt ?: return
    ExpandedArtwork(
        artwork = artwork,
        anchor = p.artworkAnchor,
        glow = p.artworkGlow,
        shape = shape,
        opacity = { p.albumArtPlaybackAlpha },
        progressProvider = p.expandedArtProgressProvider,
        progressColor = p.accentColor,
        progressStrokeWidth = 2.5.dp,
        modifier = Modifier
            .align(Alignment.Top)
            .padding(top = artworkTop)
            .size(artworkSize)
            .then(if (p.highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .then(if (p.onAlbumArtClick != null) Modifier.clickable(onClick = p.onAlbumArtClick) else Modifier)
            .graphicsLayer {
                scaleX = p.albumArtPlaybackScale
                scaleY = p.albumArtPlaybackScale
                alpha = p.albumArtPlaybackAlpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    )
}

@Composable
private fun ExpandedAndroidMediaLayout(p: ExpandedLayoutParams) {
    val density = LocalDensity.current
    val showProgress = p.elementVisibility.progress
    val sessionActions = p.sessionActions.take(3)
    val skipButtonsWidth = if (p.elementVisibility.mainControls) 72.dp else 0.dp
    val minimumProgressWidth = if (p.timestampPlacement == OverlayPreferences.ProgressTimestampPlacement.BESIDE) 124.dp else 48.dp
    val separateSessionActions = showProgress && sessionActions.isNotEmpty() &&
        p.cardWidth - 48.dp - skipButtonsWidth - 30.dp * sessionActions.size < minimumProgressWidth
    val actionButtons: @Composable (Modifier) -> Unit = { buttonModifier ->
        sessionActions.forEach { action ->
            ExpandedSessionActionButton(
                context = p.context,
                action = action,
                accentColor = p.accentColor,
                buttonSize = 30.dp,
                iconSize = 18.dp,
                modifier = buttonModifier,
                hapticFeedback = p.hapticFeedback,
            )
        }
    }
    val startPadding = 24.dp
    val showsBpm = p.elementVisibility.bpm && p.currentBpm != null
    val hasTrackInfo = p.showsTrackText || p.showsAudioOutputChip
    val hasRightColumn = p.elementVisibility.visualizer || p.showsAudioOutputButton || p.elementVisibility.mainControls
    val hasHeader = p.showsArtwork || hasTrackInfo || hasRightColumn
    val bpmRowHeight = maxOf(32.dp, p.topContentInset)
    val estimatedInfoHeight = if (hasTrackInfo) {
        rememberEstimatedTrackInfoHeightDp(
            p = p,
            titleTextStyle = MaterialTheme.typography.headlineSmall,
            artistTextStyle = MaterialTheme.typography.titleMedium,
            titleArtistSpacing = 4.dp,
            showInlineBpm = false,
            showFooterChip = p.showsAudioOutputChip,
        )
    } else 0.dp
    val fontWeightKey = OverlayPreferences.islandFontWeightFlow.collectAsState().value
    var measuredInfoHeight by remember(
        p.visibleTrackInfoStyle,
        p.mediaInfo.artist.isNotBlank(),
        p.mediaInfo.album.isNotBlank(),
        p.showsAudioOutputChip,
        p.fontStyle,
        fontWeightKey,
        p.highlightTrackInfo,
    ) { mutableStateOf<Dp?>(null) }
    val infoHeight = if (hasTrackInfo) (measuredInfoHeight ?: estimatedInfoHeight) else 0.dp
    val topRightRowMinWidth = (if (p.elementVisibility.visualizer) 22.dp else 0.dp) +
        (if (p.elementVisibility.visualizer && p.showsAudioOutputButton) 6.dp else 0.dp) +
        (if (p.showsAudioOutputButton) 36.dp else 0.dp)
    val rightMinWidth = maxOf(
        if (p.elementVisibility.mainControls) 64.dp else 0.dp,
        topRightRowMinWidth,
    )
    val artInfoGap = if (p.showsArtwork && hasTrackInfo) 14.dp else 0.dp
    val infoRightGap = if (hasTrackInfo && hasRightColumn) 16.dp else 0.dp
    val minTrackInfoWidth = if (hasTrackInfo) 96.dp else 0.dp
    val maxWidthForArtwork = (p.cardWidth - startPadding * 2 - rightMinWidth - artInfoGap - infoRightGap - minTrackInfoWidth)
        .coerceAtLeast(56.dp)
    val headerLayout = expandedHeaderArtworkLayout(
        startPaddingDp = startPadding.value,
        topContentInsetDp = p.topContentInset.value,
        cutoutStartDp = p.cutoutStart.value,
        baseArtworkSizeDp = 56f,
        infoHeightDp = infoHeight.value,
        maxWidthForArtworkDp = maxWidthForArtwork.value,
        minInfoTopOverrideDp = if (showsBpm && !hasRightColumn) (bpmRowHeight + 6.dp).value else 0f,
    )
    val artworkTop = headerLayout.artworkTopDp.dp
    val artworkSize = headerLayout.artworkSizeDp.dp
    val infoTop = if (p.showsArtwork) {
        headerLayout.infoTopDp.dp
    } else {
        maxOf(startPadding, p.topContentInset, if (showsBpm && !hasRightColumn) bpmRowHeight + 6.dp else 0.dp)
    }
    val rightTop = when {
        showsBpm -> maxOf(artworkTop, bpmRowHeight + 6.dp)
        p.showsArtwork -> artworkTop
        else -> infoTop
    }
    val rightMinHeight = if (p.showsArtwork) {
        (artworkTop + artworkSize - rightTop).coerceAtLeast(0.dp)
    } else infoHeight
    val bpmRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(bpmRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            BpmChip(bpm = p.currentBpm)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                end = startPadding,
                top = if (hasHeader || showsBpm) 0.dp else maxOf(startPadding, p.topContentInset),
                bottom = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasHeader && showsBpm) {
            bpmRow()
        }

        if (hasHeader) ExpandedMusicHeader(
            p, MaterialTheme.typography.headlineSmall, MaterialTheme.typography.titleMedium,
            fixedWidth = (if (p.showsArtwork) artworkSize + artInfoGap else 0.dp) +
                infoRightGap +
                (if (p.showsAudioOutputButton) 42.dp else 0.dp),
            showsInlineBpm = false,
            verticalAlignment = Alignment.Top,
            overlay = if (showsBpm) { { bpmRow() } } else null,
        ) { visualizerMaxWidth ->
            if (p.showsArtwork) {
                ExpandedHeaderArtwork(p, p.artworkShape, artworkSize, artworkTop)
                if (hasTrackInfo) Spacer(Modifier.width(14.dp))
            }

            if (hasTrackInfo) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.visibleTrackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.headlineSmall,
                artistTextStyle = MaterialTheme.typography.titleMedium,
                artistAlpha = 0.80f,
                titleArtistSpacing = 4.dp,
                footerContent = if (p.showsAudioOutputChip) { { ExpandedAudioOutputControl(p) } } else null,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = infoTop, end = if (hasRightColumn) 16.dp else 0.dp)
                    .onSizeChanged { size ->
                        val h = with(density) { size.height.toDp() }
                        if (h > 0.dp && measuredInfoHeight != h) measuredInfoHeight = h
                    },
            ) else if (p.showsArtwork && hasRightColumn) {
                Spacer(Modifier.weight(1f))
            }

            if (hasRightColumn) Column(
                modifier = Modifier
                    .padding(top = rightTop)
                    .heightIn(min = rightMinHeight),
                horizontalAlignment = if (p.showsAudioOutputButton) Alignment.End else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (p.elementVisibility.visualizer || p.showsAudioOutputButton) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                        mediaInfo = p.mediaInfo,
                        waveformBandCount = p.waveformBandCount,
                        minHeight = 4.dp,
                        barCornerRadius = 2.dp,
                        highlight = p.highlightVisualizer,
                        onClick = p.onVisualizerClick,
                        visualizerAnchor = p.visualizerAnchor,
                        contentAlignment = if (p.showsAudioOutputButton) Alignment.CenterEnd else Alignment.Center,
                        modifier = Modifier.widthIn(max = visualizerMaxWidth).height(20.dp),
                    )
                    if (p.showsAudioOutputButton) ExpandedAudioOutputControl(p)
                }
                if ((p.elementVisibility.visualizer || p.showsAudioOutputButton) && p.elementVisibility.mainControls) {
                    Spacer(Modifier.height(if (p.showsAudioOutputButton) 8.dp else 14.dp))
                }
                if (p.elementVisibility.mainControls) {
                    val style = p.playbackControlsStyle
                    val visuals = resolvePlaybackButtonVisuals(style, p.accentColor)
                    Surface(
                        onClick = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
                        shape = visuals.playShape,
                        color = visuals.playBgColor,
                        modifier = Modifier
                            .width(64.dp)
                            .height(46.dp)
                            .then(
                                if (p.highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, visuals.playShape)
                                else Modifier
                            )
                            .graphicsLayer {
                                val scale = p.bpmPulseScale()
                                scaleX = scale
                                scaleY = scale
                            },
                        shadowElevation = if (style.playPauseBackground) 2.dp else 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PlaybackIcon(
                                playing = p.mediaInfo.isPlaying,
                                tint = visuals.playTint,
                                size = 26.dp,
                            )
                        }
                    }
                }
            }
        }

        if (showProgress || p.elementVisibility.mainControls || sessionActions.isNotEmpty()) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val visuals = resolvePlaybackButtonVisuals(p.playbackControlsStyle, p.accentColor)
            val inlineControl = Modifier.alignBy { it.measuredHeight / 2 }

            if (p.elementVisibility.mainControls) Surface(
                shape = visuals.prevShape,
                color = visuals.skipBgColor,
                modifier = inlineControl
                    .size(36.dp)
                    .clip(visuals.prevShape)
                    .combinedClickable(
                        onClick = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
                        onLongClick = quickSeekHandler(p, -p.quickSeekStepMs),
                    )
                    .then(
                        if (p.highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, visuals.prevShape)
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

            if (showProgress) ExpandedTrackProgress(
                p, Modifier.weight(1f).alignBy(ScrubberCenter).padding(horizontal = 4.dp),
            )

            if (p.elementVisibility.mainControls) Surface(
                shape = visuals.nextShape,
                color = visuals.skipBgColor,
                modifier = inlineControl
                    .size(36.dp)
                    .clip(visuals.nextShape)
                    .combinedClickable(
                        onClick = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
                        onLongClick = quickSeekHandler(p, p.quickSeekStepMs),
                    )
                    .then(
                        if (p.highlightPlaybackControls) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, visuals.nextShape)
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

            if (!separateSessionActions) actionButtons(inlineControl)
        }
        if (separateSessionActions) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            actionButtons(Modifier)
        }
    }
}

@Composable
private fun ExpandedMaterialExpressiveLayout(p: ExpandedLayoutParams) {
    val sessionActions = p.sessionActions.take(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (p.topContentInset > 0.dp) p.topContentInset else 18.dp,
                bottom = 20.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (p.showAlbumArt && p.mediaInfo.albumArt != null) {
            ExpandedArtwork(
                artwork = p.mediaInfo.albumArt,
                anchor = p.artworkAnchor,
                glow = p.artworkGlow,
                shape = p.artworkShape,
                opacity = { p.albumArtPlaybackAlpha },
                progressProvider = p.expandedArtProgressProvider,
                progressColor = p.accentColor,
                progressStrokeWidth = 3.5.dp,
                progressGap = 3.dp,
                modifier = Modifier
                    .size(140.dp)
                    .then(
                        if (p.highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, p.artworkShape)
                        else Modifier
                    )
                    .graphicsLayer {
                        scaleX = p.albumArtPlaybackScale
                        scaleY = p.albumArtPlaybackScale
                        alpha = p.albumArtPlaybackAlpha
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .clickable { p.onAlbumArtClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
            )
        }

        if (p.showsTrackInfoWithBpm || p.elementVisibility.visualizer || p.showsAudioOutputButton) ExpandedMusicHeader(
            p, MaterialTheme.typography.titleMedium, MaterialTheme.typography.bodyMedium,
            fixedWidth = (if (p.showsTrackInfoWithBpm) 14.dp else 0.dp) +
                (if (p.showsAudioOutputButton) 42.dp else 0.dp),
        ) { visualizerMaxWidth ->
            if (p.showsTrackInfoWithBpm) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.visibleTrackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.titleMedium,
                artistTextStyle = MaterialTheme.typography.bodyMedium,
                artistAlpha = 0.72f,
                titleArtistSpacing = 2.dp,
                textAlign = TextAlign.Start,
                bpmChip = if (p.elementVisibility.trackInfo && p.elementVisibility.bpm && p.currentBpm != null) {
                    { BpmChip(bpm = p.currentBpm) }
                } else null,
                footerContent = if (p.showsAudioOutputChip) { { ExpandedAudioOutputControl(p) } } else null,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier.weight(1f),
            )
            if (p.showsTrackInfoWithBpm && (p.elementVisibility.visualizer || p.showsAudioOutputButton)) {
                Spacer(Modifier.width(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                    mediaInfo = p.mediaInfo,
                    waveformBandCount = p.waveformBandCount,
                    minHeight = 3.dp,
                    highlight = p.highlightVisualizer,
                    onClick = p.onVisualizerClick,
                    visualizerAnchor = p.visualizerAnchor,
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.widthIn(max = visualizerMaxWidth).height(20.dp),
                )
                if (p.showsAudioOutputButton) ExpandedAudioOutputControl(p)
            }
        }

        if (p.elementVisibility.progress) ExpandedTrackProgress(p, Modifier.fillMaxWidth())

        if (p.elementVisibility.mainControls) ExpandedPlaybackControlsRow(
            isPlaying = p.mediaInfo.isPlaying,
            accentColor = p.accentColor,
            style = p.playbackControlsStyle,
            skipWidth = 60.dp,
            skipHeight = 50.dp,
            skipIconSize = 28.dp,
            playWidth = 68.dp,
            playHeight = 56.dp,
            playIconSize = 31.dp,
            defaultSpacing = 8.dp,
            bpmPulseScale = p.bpmPulseScale,
            onPrevious = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
            onPlayPause = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
            onNext = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
            onLongPressPrevious = quickSeekHandler(p, -p.quickSeekStepMs),
            onLongPressNext = quickSeekHandler(p, p.quickSeekStepMs),
            highlight = p.highlightPlaybackControls,
        )

        if (sessionActions.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sessionActions.forEach { action ->
                    ExpandedSessionActionButton(
                        context = p.context,
                        action = action,
                        accentColor = p.accentColor,
                        buttonSize = 32.dp,
                        iconSize = 18.dp,
                        hapticFeedback = p.hapticFeedback,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedClassicMediaLayout(p: ExpandedLayoutParams) {
    val density = LocalDensity.current
    val sessionActions = p.sessionActions.take(2)
    val startPadding = 22.dp
    val showInlineBpm = p.elementVisibility.trackInfo && p.elementVisibility.bpm && p.currentBpm != null
    val hasTrackInfo = p.showsTrackInfoWithBpm
    val hasRightColumn = p.elementVisibility.visualizer || p.showsAudioOutputButton
    val hasHeader = p.showsArtwork || hasTrackInfo || hasRightColumn
    val estimatedInfoHeight = if (hasTrackInfo) {
        rememberEstimatedTrackInfoHeightDp(
            p = p,
            titleTextStyle = MaterialTheme.typography.titleLarge,
            artistTextStyle = MaterialTheme.typography.bodyMedium,
            titleArtistSpacing = 3.dp,
            showInlineBpm = showInlineBpm,
            showFooterChip = p.showsAudioOutputChip,
        )
    } else 0.dp
    val fontWeightKey = OverlayPreferences.islandFontWeightFlow.collectAsState().value
    var measuredInfoHeight by remember(
        p.visibleTrackInfoStyle,
        p.mediaInfo.artist.isNotBlank(),
        p.mediaInfo.album.isNotBlank(),
        p.showsAudioOutputChip,
        showInlineBpm,
        p.fontStyle,
        fontWeightKey,
        p.highlightTrackInfo,
    ) { mutableStateOf<Dp?>(null) }
    val infoHeight = if (hasTrackInfo) (measuredInfoHeight ?: estimatedInfoHeight) else 0.dp
    val rightMinWidth = (if (p.elementVisibility.visualizer) 22.dp else 0.dp) +
        (if (p.elementVisibility.visualizer && p.showsAudioOutputButton) 6.dp else 0.dp) +
        (if (p.showsAudioOutputButton) 36.dp else 0.dp)
    val artInfoGap = if (p.showsArtwork && hasTrackInfo) 14.dp else 0.dp
    val infoRightGap = if (hasTrackInfo && hasRightColumn) 12.dp else 0.dp
    val minTrackInfoWidth = if (hasTrackInfo) 96.dp else 0.dp
    val maxWidthForArtwork = (p.cardWidth - startPadding * 2 - rightMinWidth - artInfoGap - infoRightGap - minTrackInfoWidth)
        .coerceAtLeast(64.dp)
    val headerLayout = expandedHeaderArtworkLayout(
        startPaddingDp = startPadding.value,
        topContentInsetDp = p.topContentInset.value,
        cutoutStartDp = p.cutoutStart.value,
        baseArtworkSizeDp = 64f,
        infoHeightDp = infoHeight.value,
        maxWidthForArtworkDp = maxWidthForArtwork.value,
    )
    val artworkTop = headerLayout.artworkTopDp.dp
    val artworkSize = headerLayout.artworkSizeDp.dp
    val infoTop = if (p.showsArtwork) headerLayout.infoTopDp.dp else maxOf(startPadding, p.topContentInset)
    val rightTop = if (p.showsArtwork) artworkTop else infoTop
    val rightMinHeight = if (p.showsArtwork) artworkSize else infoHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                end = startPadding,
                top = if (hasHeader) 0.dp else maxOf(startPadding, p.topContentInset),
                bottom = 18.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasHeader) ExpandedMusicHeader(
            p, MaterialTheme.typography.titleLarge, MaterialTheme.typography.bodyMedium,
            fixedWidth = (if (p.showsArtwork) artworkSize + artInfoGap else 0.dp) +
                infoRightGap +
                (if (p.showsAudioOutputButton) 42.dp else 0.dp),
            verticalAlignment = Alignment.Top,
        ) { visualizerMaxWidth ->
            if (p.showsArtwork) {
                ExpandedHeaderArtwork(p, p.artworkShape, artworkSize, artworkTop)
                if (hasTrackInfo) Spacer(Modifier.width(14.dp))
            }
            if (hasTrackInfo) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.visibleTrackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.titleLarge,
                artistTextStyle = MaterialTheme.typography.bodyMedium,
                artistAlpha = 0.62f,
                titleArtistSpacing = 3.dp,
                bpmChip = if (showInlineBpm) {
                    { BpmChip(bpm = p.currentBpm) }
                } else null,
                footerContent = if (p.showsAudioOutputChip) { { ExpandedAudioOutputControl(p) } } else null,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = infoTop)
                    .onSizeChanged { size ->
                        val h = with(density) { size.height.toDp() }
                        if (h > 0.dp && measuredInfoHeight != h) measuredInfoHeight = h
                    },
            ) else if (p.showsArtwork && hasRightColumn) {
                Spacer(Modifier.weight(1f))
            }
            if (hasTrackInfo && hasRightColumn) {
                Spacer(Modifier.width(12.dp))
            }
            if (hasRightColumn) Row(
                modifier = Modifier
                    .padding(top = rightTop)
                    .heightIn(min = rightMinHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                    mediaInfo = p.mediaInfo,
                    waveformBandCount = p.waveformBandCount,
                    minHeight = 3.dp,
                    highlight = p.highlightVisualizer,
                    onClick = p.onVisualizerClick,
                    visualizerAnchor = p.visualizerAnchor,
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier.widthIn(max = visualizerMaxWidth).height(20.dp),
                )
                if (p.showsAudioOutputButton) ExpandedAudioOutputControl(p)
            }
        }

        if (p.elementVisibility.progress) ExpandedTrackProgress(p, Modifier.fillMaxWidth())

        if (p.elementVisibility.mainControls || sessionActions.isNotEmpty()) Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val style = p.playbackControlsStyle
            if (p.elementVisibility.mainControls) ExpandedPlaybackControlsRow(
                isPlaying = p.mediaInfo.isPlaying,
                accentColor = p.accentColor,
                style = style,
                skipWidth = if (style.material3ButtonGroup || style.skipBackground) 44.dp else 38.dp,
                skipHeight = 40.dp,
                skipIconSize = 25.dp,
                playWidth = if (style.material3ButtonGroup || style.playPauseBackground) 52.dp else 46.dp,
                playHeight = 46.dp,
                playIconSize = 30.dp,
                defaultSpacing = 12.dp,
                bpmPulseScale = p.bpmPulseScale,
                onPrevious = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipPrevious() },
                onPlayPause = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.togglePlayPause() },
                onNext = { p.onPlaybackControlsClick?.invoke() ?: MediaPlaybackState.skipNext() },
                onLongPressPrevious = quickSeekHandler(p, -p.quickSeekStepMs),
                onLongPressNext = quickSeekHandler(p, p.quickSeekStepMs),
                highlight = p.highlightPlaybackControls,
                modifier = Modifier.align(Alignment.Center),
            )

            sessionActions.forEachIndexed { index, action ->
                ExpandedSessionActionButton(
                    context = p.context,
                    action = action,
                    accentColor = p.accentColor,
                    buttonSize = 38.dp,
                    iconSize = 19.dp,
                    inactiveAlpha = 0.72f,
                    hapticFeedback = p.hapticFeedback,
                    modifier = Modifier.align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd),
                )
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
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    textAlign: TextAlign? = null,
    bpmChip: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val showArtist = trackInfoStyle.showArtist && mediaInfo.artist.isNotBlank()
    val showAlbum = trackInfoStyle.showAlbum && mediaInfo.album.isNotBlank()
    val inlineAlbum = trackInfoStyle.albumBesideArtist && showArtist && showAlbum
    val showArtistRow = showArtist || bpmChip != null
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
                if (showArtist) {
                    MarqueeText(
                        text = if (inlineAlbum) "${mediaInfo.artist} • ${mediaInfo.album}" else mediaInfo.artist,
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
                    if (showArtist) {
                        Spacer(Modifier.width(6.dp))
                    }
                    bpmChip()
                }
            }
        }
        if (footerContent != null) {
            if (trackInfoStyle.showTitle || showArtistRow) {
                Spacer(Modifier.height(6.dp))
            }
            footerContent()
        }
        if (showAlbum && !inlineAlbum) {
            if (trackInfoStyle.showTitle || showArtistRow || footerContent != null) Spacer(Modifier.height(2.dp))
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
