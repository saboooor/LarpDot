package ca.saboor.larpdot.ui.overlay

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaSessionAction
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.media.MusicSleepTimer
import ca.saboor.larpdot.media.formatSleepTimerBadge
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
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
    modifier: Modifier = Modifier,
    cutoutInfo: CutoutInfo? = null,
    cardHorizontalMarginDp: Dp = 14.dp,
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
    val sleepTimerState by MusicSleepTimer.state.collectAsState()
    val sleepTimerBadge = formatSleepTimerBadge(sleepTimerState)
    val activeAudioOutput by AudioOutputTracker.activeOutput.collectAsState()
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
            centerX = dotCenterXDp,
            centerY = dotCenterYDp,
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
        val layoutParams = ExpandedLayoutParams(
            context = context,
            hapticFeedback = hapticFeedback,
            mediaInfo = mediaInfo,
            livePositionMs = livePositionMs,
            topContentInset = topContentInset,
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
            sleepTimerBadge = sleepTimerBadge,
            hasExternalAudioOutput = activeAudioOutput.isExternal,
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
            onVisualizerClick = onVisualizerClick,
            onProgressBarClick = onProgressBarClick,
            onPlaybackControlsClick = onPlaybackControlsClick,
            highlightAlbumArt = highlightAlbumArt,
            highlightTrackInfo = highlightTrackInfo,
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

@Composable
private fun SleepTimerChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = LarpIcons.Timer,
                contentDescription = "Cancel sleep timer",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private class ExpandedLayoutParams(
    val context: Context,
    val hapticFeedback: HapticFeedback,
    val mediaInfo: MediaTrackInfo,
    val livePositionMs: State<Long>,
    val topContentInset: Dp,
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
    val sleepTimerBadge: String?,
    val hasExternalAudioOutput: Boolean,
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
    val onVisualizerClick: (() -> Unit)?,
    val onProgressBarClick: (() -> Unit)?,
    val onPlaybackControlsClick: (() -> Unit)?,
    val highlightAlbumArt: Boolean,
    val highlightTrackInfo: Boolean,
    val highlightVisualizer: Boolean,
    val highlightProgressBar: Boolean,
    val highlightPlaybackControls: Boolean,
) {
    val showsArtwork get() = showAlbumArt && mediaInfo.albumArt != null
    val showsTrackText get() = elementVisibility.trackInfo &&
        (trackInfoStyle.showTitle || (trackInfoStyle.showArtist && mediaInfo.artist.isNotBlank()) ||
            (trackInfoStyle.showAlbum && mediaInfo.album.isNotBlank()))
    val hasTrackBadges get() = (elementVisibility.bpm && currentBpm != null) ||
        sleepTimerBadge != null ||
        hasExternalAudioOutput
    val showsTrackInfoWithBpm get() = showsTrackText ||
        (elementVisibility.trackInfo && hasTrackBadges)
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
    BoxWithConstraints(
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
    ) {
        if (waveformBandCount <= 0 || maxWidth <= 0.dp) return@BoxWithConstraints
        val naturalWidth = compactVisualizerWidthDp(true, waveformBandCount, barWidth, barSpacing)
        // Preserve the width-to-spacing ratio when all the configured bands exceed the header slot.
        val fit = (maxWidth.value / naturalWidth).coerceAtMost(1f)
        val renderedBarWidth = (barWidth * fit).dp
        EqualizerWaveform(
            mediaInfo = mediaInfo,
            maxHeightDp = 20f,
            barCount = waveformBandCount,
            barWidth = renderedBarWidth,
            barSpacing = (barSpacing * fit).dp,
            minHeight = minHeight,
            barCornerRadius = minOf(barCornerRadius, renderedBarWidth / 2f),
            visualizerAnchor = visualizerAnchor,
        )
    }
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
            modifier = barModifier,
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

@Composable
private fun ExpandedTrackBadgesRow(p: ExpandedLayoutParams) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p.elementVisibility.bpm && p.currentBpm != null) {
            BpmChip(bpm = p.currentBpm)
        }
        if (p.sleepTimerBadge != null) {
            SleepTimerChip(
                label = p.sleepTimerBadge,
                onClick = {
                    p.hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
                    MusicSleepTimer.cancel()
                },
            )
        }
        if (p.hasExternalAudioOutput) {
            AudioOutputChip(
                onClick = {
                    AudioOutputTracker.openOutputSwitcher(p.context)
                    p.onCollapse()
                },
            )
        }
    }
}

private fun quickSeekHandler(p: ExpandedLayoutParams, deltaMs: Long): (() -> Unit)? =
    if (p.onPlaybackControlsClick == null && p.mediaInfo.durationMs > 0L) {
        {
            p.hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
            MediaPlaybackState.seekBy(deltaMs)
        }
    } else null

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedAndroidMediaLayout(p: ExpandedLayoutParams) {
    val showTimestampedProgress = p.elementVisibility.progress &&
        p.timestampPlacement != OverlayPreferences.ProgressTimestampPlacement.HIDDEN
    val showInlineProgress = p.elementVisibility.progress && !showTimestampedProgress
    val sessionActions = p.sessionActions.take(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = if (p.topContentInset > 0.dp) 0.dp else 14.dp,
                bottom = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(32.dp, p.topContentInset)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            if (p.sleepTimerBadge != null) {
                SleepTimerChip(
                    label = p.sleepTimerBadge,
                    onClick = {
                        p.hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
                        MusicSleepTimer.cancel()
                    },
                )
            }
            if (p.elementVisibility.bpm && p.currentBpm != null) {
                BpmChip(bpm = p.currentBpm)
            }
            AudioOutputChip(
                onClick = {
                    AudioOutputTracker.openOutputSwitcher(p.context)
                    p.onCollapse()
                },
            )
        }

        if (p.showsArtwork || p.showsTrackText || p.elementVisibility.visualizer || p.elementVisibility.mainControls) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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
                    progressStrokeWidth = 2.5.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (p.highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, p.artworkShape)
                            else Modifier
                        )
                        .then(if (p.onAlbumArtClick != null) Modifier.clickable(onClick = p.onAlbumArtClick) else Modifier)
                        .graphicsLayer {
                            scaleX = p.albumArtPlaybackScale
                            scaleY = p.albumArtPlaybackScale
                            alpha = p.albumArtPlaybackAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                )
                Spacer(Modifier.width(14.dp))
            }

            if (p.showsTrackText) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.trackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.headlineSmall,
                artistTextStyle = MaterialTheme.typography.titleMedium,
                artistAlpha = 0.80f,
                titleArtistSpacing = 4.dp,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            )

            if (p.elementVisibility.visualizer || p.elementVisibility.mainControls) Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                    mediaInfo = p.mediaInfo,
                    waveformBandCount = p.waveformBandCount,
                    minHeight = 4.dp,
                    barCornerRadius = 2.dp,
                    highlight = p.highlightVisualizer,
                    onClick = p.onVisualizerClick,
                    visualizerAnchor = p.visualizerAnchor,
                    modifier = Modifier.height(20.dp),
                )
                if (p.elementVisibility.visualizer && p.elementVisibility.mainControls) Spacer(Modifier.height(24.dp))
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

        if (showTimestampedProgress) ExpandedTrackProgress(p, Modifier.fillMaxWidth())

        if (showInlineProgress || p.elementVisibility.mainControls || sessionActions.isNotEmpty()) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showTimestampedProgress) {
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            } else Arrangement.Start,
        ) {
            val visuals = resolvePlaybackButtonVisuals(p.playbackControlsStyle, p.accentColor)

            if (p.elementVisibility.mainControls) Surface(
                shape = visuals.prevShape,
                color = visuals.skipBgColor,
                modifier = Modifier
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

            if (showInlineProgress) ExpandedTrackProgress(p, Modifier.weight(1f).padding(horizontal = 4.dp))

            if (p.elementVisibility.mainControls) Surface(
                shape = visuals.nextShape,
                color = visuals.skipBgColor,
                modifier = Modifier
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

            sessionActions.forEach { action ->
                ExpandedSessionActionButton(
                    context = p.context,
                    action = action,
                    accentColor = p.accentColor,
                    buttonSize = 30.dp,
                    iconSize = 18.dp,
                    hapticFeedback = p.hapticFeedback,
                )
            }
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

        if (p.showsTrackInfoWithBpm || p.elementVisibility.visualizer) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (p.showsTrackInfoWithBpm) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.trackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.titleMedium,
                artistTextStyle = MaterialTheme.typography.bodyMedium,
                artistAlpha = 0.72f,
                titleArtistSpacing = 2.dp,
                textAlign = TextAlign.Start,
                bpmChip = if (p.hasTrackBadges) {
                    { ExpandedTrackBadgesRow(p) }
                } else null,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier.weight(1f),
            )
            if (p.showsTrackInfoWithBpm && p.elementVisibility.visualizer) Spacer(Modifier.width(14.dp))
            if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                mediaInfo = p.mediaInfo,
                waveformBandCount = p.waveformBandCount,
                minHeight = 3.dp,
                highlight = p.highlightVisualizer,
                onClick = p.onVisualizerClick,
                visualizerAnchor = p.visualizerAnchor,
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.size(width = 72.dp, height = 20.dp).padding(end = 14.dp),
            )
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
    val sessionActions = p.sessionActions.take(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = if (p.topContentInset > 0.dp) p.topContentInset else 16.dp,
                bottom = 18.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (p.showsArtwork || p.showsTrackInfoWithBpm || p.elementVisibility.visualizer) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (p.showAlbumArt && p.mediaInfo.albumArt != null) {
                ExpandedArtwork(
                    artwork = p.mediaInfo.albumArt,
                    anchor = p.artworkAnchor,
                    glow = p.artworkGlow,
                    shape = RoundedCornerShape(12.dp),
                    opacity = { p.albumArtPlaybackAlpha },
                    progressProvider = p.expandedArtProgressProvider,
                    progressColor = p.accentColor,
                    progressStrokeWidth = 2.5.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            if (p.highlightAlbumArt) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .then(if (p.onAlbumArtClick != null) Modifier.clickable(onClick = p.onAlbumArtClick) else Modifier)
                        .graphicsLayer {
                            scaleX = p.albumArtPlaybackScale
                            scaleY = p.albumArtPlaybackScale
                            alpha = p.albumArtPlaybackAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                )
                Spacer(Modifier.width(14.dp))
            }
            if (p.showsTrackInfoWithBpm) ExpandedTrackInfoColumn(
                mediaInfo = p.mediaInfo,
                trackInfoStyle = p.trackInfoStyle,
                accentColor = p.accentColor,
                fontStyle = p.fontStyle,
                titleTextStyle = MaterialTheme.typography.titleLarge,
                artistTextStyle = MaterialTheme.typography.bodyMedium,
                artistAlpha = 0.62f,
                titleArtistSpacing = 3.dp,
                bpmChip = if (p.hasTrackBadges) {
                    { ExpandedTrackBadgesRow(p) }
                } else null,
                onClick = { p.onTrackInfoClick?.invoke() ?: openPlayerApp(p.context, p.mediaInfo) },
                highlight = p.highlightTrackInfo,
                modifier = Modifier.weight(1f),
            )
            if (p.showsTrackInfoWithBpm && p.elementVisibility.visualizer) Spacer(Modifier.width(12.dp))
            if (p.elementVisibility.visualizer) ExpandedVisualizerSlot(
                mediaInfo = p.mediaInfo,
                waveformBandCount = p.waveformBandCount,
                minHeight = 3.dp,
                highlight = p.highlightVisualizer,
                onClick = p.onVisualizerClick,
                visualizerAnchor = p.visualizerAnchor,
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.size(width = 72.dp, height = 20.dp).padding(end = 14.dp),
            )
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
        if (showAlbum && !inlineAlbum) {
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
