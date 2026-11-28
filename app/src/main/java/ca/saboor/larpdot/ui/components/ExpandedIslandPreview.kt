package ca.saboor.larpdot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.DominantColorExtractor
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactIslandContent
import ca.saboor.larpdot.ui.overlay.ExpandedIslandContent
import ca.saboor.larpdot.ui.overlay.compactMediaBaseExtraDp
import ca.saboor.larpdot.ui.overlay.compactVisualizerWidthDp
import ca.saboor.larpdot.ui.overlay.configuredCompactMediaWings
import ca.saboor.larpdot.ui.overlay.expandedMediaCardMaxHeight
import ca.saboor.larpdot.ui.overlay.expandedMediaCardWidth
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.rememberLiveMediaPositionMs
import ca.saboor.larpdot.ui.theme.LarpMotion

enum class IslandPreviewMode {
    MINIMIZED,
    EXPANDED,
    BOTH,
}

enum class MusicPreviewElement {
    MINIMIZED_ALBUM_ART,
    MINIMIZED_BACKGROUND,
    MINIMIZED_RIGHT_SIDE,
    EXPANDED_ALBUM_ART,
    EXPANDED_BACKGROUND,
    TRACK_INFO,
    AUDIO_OUTPUT,
    VISUALIZER,
    PROGRESS_BAR,
    PLAYBACK_CONTROLS,
}

/**
 * Authentic live preview of the Dynamic Island (both minimized pill and expanded card)
 * directly inside the app, accurately replicating island dimensions, hole-punch alignment,
 * dynamic styles, and fluid progress outlines.
 */
@Composable
fun ExpandedIslandPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
    previewMode: IslandPreviewMode = IslandPreviewMode.BOTH,
    showControls: Boolean = true,
    titleBar: Boolean = false,
    selectedElement: MusicPreviewElement? = null,
    onElementClick: ((MusicPreviewElement) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var showMinimizedPreview by remember { mutableStateOf(previewMode != IslandPreviewMode.EXPANDED) }
    var showExpandedPreview by remember { mutableStateOf(previewMode != IslandPreviewMode.MINIMIZED) }

    LaunchedEffect(previewMode) {
        showMinimizedPreview = previewMode != IslandPreviewMode.EXPANDED
        showExpandedPreview = previewMode != IslandPreviewMode.MINIMIZED
    }

    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()
    val showMinimizedProgressOutline by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val showExpandedProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val islandOutlineMode by OverlayPreferences.islandOutlineModeFlow.collectAsState()
    val showIslandOutline = ca.saboor.larpdot.service.resolveIslandOutlineVisible(
        islandOutlineMode,
        ca.saboor.larpdot.service.isPitchBlackPixel(MaterialTheme.colorScheme.background.toArgb()),
    )
    val leftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    val hasMinimizedTitle = leftSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE ||
        rightSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE
    val hasMinimizedVisualizer = leftSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER ||
        rightSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER
    val waveformBandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val waveformBarWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val waveformBarSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val isAnnouncing = isSongAnnouncement && showSongAnnouncement
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()

    val hasNotificationAccess = remember(context) { hasNotificationAccess(context) }

    val sampleTrack = remember {
        val sampleArt = DominantColorExtractor.createSampleArtwork("Starboy")
        MediaTrackInfo(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            albumArt = sampleArt,
            isPlaying = true,
            positionMs = 64000L,
            durationMs = 230000L,
            artworkColors = DominantColorExtractor.extractArtworkColors(sampleArt),
            isSimulated = false,
        )
    }

    // Always display realistic media so styles, shapes, blur, and waveforms are immediately visible
    val previewTrack = if (nowPlaying.hasMedia) nowPlaying else sampleTrack
    val livePositionMsState = rememberLiveMediaPositionMs(previewTrack, useLiveStream = nowPlaying.hasMedia)
    val previewProgressProvider: () -> Float = {
        if (previewTrack.durationMs > 0L) {
            (livePositionMsState.value.toFloat() / previewTrack.durationMs).coerceIn(0f, 1f)
        } else 0f
    }
    val pulseEntireIsland by OverlayPreferences.pulseEntireIslandEnabledFlow.collectAsState()
    val pulseAmount by OverlayPreferences.pulseAmountFlow.collectAsState()
    val islandFontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isBpmResolved by BpmDetector.isResolved.collectAsState()
    val bpmPulseState = rememberBpmPulse(
        bpm = if (isBpmResolved) currentBpm else (if (!nowPlaying.hasMedia) 120f else null),
        positionMsProvider = { livePositionMsState.value },
        isPlaying = previewTrack.isPlaying,
        enabled = pulseEntireIsland,
    )

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val compactCornerRadius = cameraGeometry.cornerRadius.dp
    val compactExtraDp = compactMediaBaseExtraDp(
        artStyle = minimizedStyle,
        thickness = compactPillThickness,
        showOutline = showMinimizedProgressOutline,
    )
    val visualizerWidthDp = compactVisualizerWidthDp(
        showVisualizer = hasMinimizedVisualizer,
        bandCount = waveformBandCount,
        barWidth = waveformBarWidth,
        barSpacing = waveformBarSpacing,
    )
    val mediaWings = configuredCompactMediaWings(
        mediaInfo = previewTrack,
        extraWidthDp = compactExtraDp.value,
        showTitle = hasMinimizedTitle,
        announcing = isAnnouncing,
        visualizerWidthDp = visualizerWidthDp,
        cameraDiameterDp = cutoutDiameterDp.value,
        cameraCenterXPx = cutoutInfo.centerX,
        landscape = false,
    )
    val targetCompactWidth = cutoutDiameterDp + mediaWings.widthDp.dp
    val animatedCompactWidth by animateDpAsState(
        targetValue = targetCompactWidth,
        animationSpec = LarpMotion.resize(),
        label = "preview_compact_width",
    )

    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()

    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val previewCardWidth = expandedMediaCardWidth(
        playerLayout = expandedPlayerLayout,
        screenWidthDp = screenWidthDp,
        horizontalMarginDp = 16.dp,
    )
    var previewHeight by remember { mutableStateOf(52.dp) }
    val previewHorizontalMarginDp = (screenWidthDp - previewCardWidth) / 2f

    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(previewCardWidth, previewHeight) / 2f)
    val containerShape = previewCardShape(expandedCornerRadiusDp, squircleEnabled, blurEnabled)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showControls && !hasNotificationAccess) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showMinimizedPreview) {
                FilledTonalButton(
                    onClick = { showMinimizedPreview = false },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Minimized")
                }
            } else {
                OutlinedButton(
                    onClick = { showMinimizedPreview = true },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Minimized")
                }
            }

            if (showExpandedPreview) {
                FilledTonalButton(
                    onClick = { showExpandedPreview = false },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Expanded")
                }
            } else {
                OutlinedButton(
                    onClick = { showExpandedPreview = true },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Expanded")
                }
            }
        }

        // Compact Pill Live Preview
        AnimatedVisibility(showMinimizedPreview, enter = LarpMotion.reveal, exit = LarpMotion.conceal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(animatedCompactWidth)
                        .height(compactPillThickness)
                        .graphicsLayer {
                            val pulse = bpmPulseState.value * pulseAmount
                            if (pulseEntireIsland && previewTrack.isPlaying && pulse > 0f) {
                                val scale = 1f + (0.035f * pulse)
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                        .then(
                            Modifier.islandFluidProgressBorder(
                                progressFraction = 0f,
                                cornerRadius = compactCornerRadius,
                                shape = RoundedCornerShape(compactCornerRadius),
                                strokeWidth = if (selectedElement == MusicPreviewElement.MINIMIZED_BACKGROUND) 1.5.dp else 0.75.dp,
                                trackColor = when {
                                    selectedElement == MusicPreviewElement.MINIMIZED_BACKGROUND -> MaterialTheme.colorScheme.primary
                                    showIslandOutline -> Color(0x30FFFFFF)
                                    else -> Color.Transparent
                                },
                                progressColor = Color.Transparent,
                            )
                        ),
                    shape = RoundedCornerShape(compactCornerRadius),
                    color = Color.Black,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactIslandContent(
                            mediaInfo = previewTrack,
                            cutoutDiameterDp = cutoutDiameterDp,
                            albumArtStyle = minimizedStyle,
                            showVisualizer = hasMinimizedVisualizer,
                            waveformBandCount = waveformBandCount,
                            isSongAnnouncement = isAnnouncing,
                            trailingWingFraction = mediaWings.trailingFraction,
                            pulseAmount = pulseAmount,
                            showProgressRing = showMinimizedProgressOutline,
                            progressProvider = previewProgressProvider,
                            onAlbumArtClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.MINIMIZED_ALBUM_ART) }
                            },
                            onRightWingClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.MINIMIZED_RIGHT_SIDE) }
                            },
                            onBackgroundClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.MINIMIZED_BACKGROUND) }
                            },
                            highlightAlbumArt = selectedElement == MusicPreviewElement.MINIMIZED_ALBUM_ART,
                            highlightRightWing = selectedElement == MusicPreviewElement.MINIMIZED_RIGHT_SIDE,
                        )
                        // Hardware camera cutout visualizer centered between wings
                        PreviewCameraCutoutDot(diameter = cutoutDiameterDp)
                    }
                }
            }
        }

        // Expanded Card Live Preview
        AnimatedVisibility(
            visible = showExpandedPreview,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            Surface(
                modifier = Modifier
                    .width(previewCardWidth)
                    .onSizeChanged { size -> previewHeight = with(density) { size.height.toDp() } }
                    .then(
                        Modifier.islandFluidProgressBorder(
                            progressFraction = 0f,
                            cornerRadius = expandedCornerRadiusDp,
                            shape = containerShape,
                            strokeWidth = if (selectedElement == MusicPreviewElement.EXPANDED_BACKGROUND) 1.5.dp else 0.75.dp,
                            trackColor = when {
                                selectedElement == MusicPreviewElement.EXPANDED_BACKGROUND -> MaterialTheme.colorScheme.primary
                                showIslandOutline -> Color(0x30FFFFFF)
                                else -> Color.Transparent
                            },
                            progressColor = Color.Transparent,
                        )
                    )
                    .animateContentSize(LarpMotion.resize())
                    .then(if (titleBar) Modifier.height(52.dp) else Modifier.heightIn(max = expandedMediaCardMaxHeight(screenHeightDp))),
                shape = containerShape,
                color = Color.Black.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    val punchHoleCenterXDp = (with(density) { cutoutInfo.centerX.toDp() } - previewHorizontalMarginDp)
                        .coerceIn(cutoutDiameterDp / 2f, previewCardWidth - (cutoutDiameterDp / 2f))
                    val punchHoleCenterYDp = 18.dp
                    ExpandedIslandContent(
                        mediaInfo = previewTrack,
                        cutoutDiameterDp = cutoutDiameterDp,
                        isExpanded = true,
                        onCollapse = { onElementClick?.invoke(MusicPreviewElement.EXPANDED_BACKGROUND) },
                        cardWidth = previewCardWidth,
                        cutoutCenterX = punchHoleCenterXDp,
                        cutoutCenterY = punchHoleCenterYDp,
                        topContentInset = punchHoleCenterYDp + cutoutDiameterDp / 2f,
                        waveformBandCount = waveformBandCount,
                        pulseAmount = pulseAmount,
                        fontStyle = islandFontStyle,
                        showExpandedProgressRing = showExpandedProgressOutline,
                        progressProvider = previewProgressProvider,
                        onAlbumArtClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.EXPANDED_ALBUM_ART) }
                        },
                        onTrackInfoClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.TRACK_INFO) }
                        },
                        onAudioOutputClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.AUDIO_OUTPUT) }
                        },
                        onVisualizerClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.VISUALIZER) }
                        },
                        onProgressBarClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.PROGRESS_BAR) }
                        },
                        onPlaybackControlsClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.PLAYBACK_CONTROLS) }
                        },
                        highlightAlbumArt = selectedElement == MusicPreviewElement.EXPANDED_ALBUM_ART,
                        highlightTrackInfo = selectedElement == MusicPreviewElement.TRACK_INFO,
                        highlightAudioOutput = selectedElement == MusicPreviewElement.AUDIO_OUTPUT,
                        highlightVisualizer = selectedElement == MusicPreviewElement.VISUALIZER,
                        highlightProgressBar = selectedElement == MusicPreviewElement.PROGRESS_BAR,
                        highlightPlaybackControls = selectedElement == MusicPreviewElement.PLAYBACK_CONTROLS,
                    )

                    Box(Modifier.matchParentSize()) {
                        PreviewCameraCutoutDot(
                            diameter = cutoutDiameterDp,
                            modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(
                                x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                                y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                            ),
                        )
                    }
                }
            }
            }
            if (onElementClick != null) {
                Text(
                    text = "Tap any element or empty space on the preview to customize",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

    }
