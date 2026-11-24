package ca.saboor.larpdot.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
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
import ca.saboor.larpdot.ui.overlay.compactRadialGaugeExtraDp
import ca.saboor.larpdot.ui.overlay.ExpandedIslandContent
import ca.saboor.larpdot.ui.overlay.configuredCompactMediaWings
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.squircleShape
import ca.saboor.larpdot.ui.theme.LarpMotion

enum class IslandPreviewMode {
    MINIMIZED,
    EXPANDED,
    BOTH,
}

enum class MusicPreviewElement {
    MINIMIZED_ALBUM_ART,
    MINIMIZED_BACKGROUND,
    EXPANDED_ALBUM_ART,
    EXPANDED_BACKGROUND,
    TRACK_INFO,
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
    val livePositionMs by MediaPlaybackState.positionMs.collectAsState()
    val showMinimizedProgressOutline by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val showExpandedProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val waveformBandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val waveformBarWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val waveformBarSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showMinimizedVisualizer by OverlayPreferences.showMinimizedVisualizerFlow.collectAsState()
    val isSongAnnouncement by MediaPlaybackState.isSongAnnouncementActive.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val isAnnouncing = isSongAnnouncement && showSongAnnouncement
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()

    val hasNotificationAccess = remember(context) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    }

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
    val previewPositionMs = if (nowPlaying.hasMedia) livePositionMs else sampleTrack.positionMs
    val pulseEntireIsland by OverlayPreferences.pulseEntireIslandEnabledFlow.collectAsState()
    val pulseAmount by OverlayPreferences.pulseAmountFlow.collectAsState()
    val islandFontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isBpmResolved by BpmDetector.isResolved.collectAsState()
    val bpmPulseState = rememberBpmPulse(
        bpm = if (isBpmResolved) currentBpm else (if (!nowPlaying.hasMedia) 120f else null),
        currentPositionMs = previewPositionMs,
        isPlaying = previewTrack.isPlaying,
        enabled = pulseEntireIsland,
    )

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val compactCornerRadius = cameraGeometry.cornerRadius.dp
    val nestedArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
    val compactExtraDp = when (minimizedStyle) {
        OverlayPreferences.AlbumArtStyle.BLENDED -> compactPillThickness * 3
        OverlayPreferences.AlbumArtStyle.NESTED -> maxOf(
            compactPillThickness + nestedArtSize,
            if (showMinimizedProgressOutline) compactRadialGaugeExtraDp(compactPillThickness) else 0.dp,
        )
        else -> 60.dp
    }
    val visualizerWidthDp = if (showMinimizedVisualizer) {
        waveformBandCount * waveformBarWidth + (waveformBandCount - 1).coerceAtLeast(0) * waveformBarSpacing
    } else 0f
    val mediaWings = configuredCompactMediaWings(
        mediaInfo = previewTrack,
        extraWidthDp = compactExtraDp.value,
        hasTrailingBubble = false,
        showTitle = showMinimizedTitle,
        announcing = isAnnouncing,
        visualizerWidthDp = visualizerWidthDp,
        cameraDiameterDp = cutoutDiameterDp.value,
        cameraCenterXPx = cutoutInfo.centerX,
        bubbleThicknessDp = compactPillThickness.value,
        hasLeadingBubble = false,
        landscape = false,
    )
    val targetCompactWidth = cutoutDiameterDp + mediaWings.widthDp.dp
    val animatedCompactWidth by animateDpAsState(
        targetValue = targetCompactWidth,
        animationSpec = LarpMotion.resize(),
        label = "preview_compact_width",
    )

    val progressFraction = if (previewTrack.durationMs > 0) {
        (previewPositionMs.toFloat() / previewTrack.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = LarpMotion.timing(durationMillis = 350, easing = LinearEasing),
        label = "preview_progress",
    )

    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val usesVerticalMediaLayout = expandedPlayerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE
    val hasExtraMediaButtons = expandedElementVisibility.appActions && previewTrack.sessionActions.any {
        it.iconResourceId != 0 && it.iconPackageName != null
    }

    val screenWidthDp = configuration.screenWidthDp.dp
    val previewCardWidth = if (usesVerticalMediaLayout) {
        minOf(screenWidthDp - 88.dp, 248.dp)
    } else {
        screenWidthDp - 32.dp
    }
    val previewHeight = if (titleBar) {
        52.dp
    } else if (usesVerticalMediaLayout) {
        val hasAlbumArt = showExpandedAlbumArt && previewTrack.albumArt != null
        val materialHeight = when {
            hasAlbumArt && hasExtraMediaButtons -> 430.dp
            hasAlbumArt -> 350.dp
            hasExtraMediaButtons -> 290.dp
            else -> 210.dp
        }
        materialHeight
    } else {
        220.dp
    }
    val previewHorizontalMarginDp = (screenWidthDp - previewCardWidth) / 2f

    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(previewCardWidth, previewHeight) / 2f)
    val expandedCornerRadiusPx = with(density) { expandedCornerRadiusDp.toPx() }
    val containerShape = if (!squircleEnabled || blurEnabled) {
        RoundedCornerShape(expandedCornerRadiusDp)
    } else {
        squircleShape(expandedCornerRadiusPx, 0.80f)
    }

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
                                trackColor = if (selectedElement == MusicPreviewElement.MINIMIZED_BACKGROUND) {
                                    MaterialTheme.colorScheme.primary
                                } else Color(0x30FFFFFF),
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
                            onExpand = {},
                            albumArtStyle = minimizedStyle,
                            showVisualizer = showMinimizedVisualizer,
                            waveformBandCount = waveformBandCount,
                            isSongAnnouncement = isAnnouncing,
                            trailingWingFraction = mediaWings.trailingFraction,
                            pulseAmount = pulseAmount,
                            fontStyle = islandFontStyle,
                            showProgressRing = showMinimizedProgressOutline,
                            progressProvider = { animatedProgress },
                            onAlbumArtClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.MINIMIZED_ALBUM_ART) }
                            },
                            onVisualizerClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.VISUALIZER) }
                            },
                            onBackgroundClick = onElementClick?.let { callback ->
                                { callback(MusicPreviewElement.MINIMIZED_BACKGROUND) }
                            },
                            highlightAlbumArt = selectedElement == MusicPreviewElement.MINIMIZED_ALBUM_ART,
                            highlightVisualizer = selectedElement == MusicPreviewElement.VISUALIZER,
                        )
                        // Hardware camera cutout visualizer centered between wings
                        Box(
                            modifier = Modifier
                                .size(cutoutDiameterDp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0D0D))
                                .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                        )
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
                    .height(previewHeight)
                    .then(
                        Modifier.islandFluidProgressBorder(
                            progressFraction = 0f,
                            cornerRadius = expandedCornerRadiusDp,
                            shape = containerShape,
                            strokeWidth = if (selectedElement == MusicPreviewElement.EXPANDED_BACKGROUND) 1.5.dp else 0.75.dp,
                            trackColor = if (selectedElement == MusicPreviewElement.EXPANDED_BACKGROUND) {
                                MaterialTheme.colorScheme.primary
                            } else Color(0x30FFFFFF),
                            progressColor = Color.Transparent,
                        )
                    ),
                shape = containerShape,
                color = Color.Black.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ExpandedIslandContent(
                        mediaInfo = previewTrack,
                        cutoutDiameterDp = cutoutDiameterDp,
                        isExpanded = true,
                        onCollapse = { onElementClick?.invoke(MusicPreviewElement.EXPANDED_BACKGROUND) },
                        cutoutInfo = cutoutInfo,
                        cardHorizontalMarginDp = previewHorizontalMarginDp,
                        waveformBandCount = waveformBandCount,
                        pulseAmount = pulseAmount,
                        fontStyle = islandFontStyle,
                        showExpandedProgressRing = showExpandedProgressOutline,
                        progressProvider = { animatedProgress },
                        onAlbumArtClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.EXPANDED_ALBUM_ART) }
                        },
                        onTrackInfoClick = onElementClick?.let { callback ->
                            { callback(MusicPreviewElement.TRACK_INFO) }
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
                        highlightVisualizer = selectedElement == MusicPreviewElement.VISUALIZER,
                        highlightProgressBar = selectedElement == MusicPreviewElement.PROGRESS_BAR,
                        highlightPlaybackControls = selectedElement == MusicPreviewElement.PLAYBACK_CONTROLS,
                    )

                    // Hardware camera cutout punch hole positioned accurately over the organic scoop shader
                    val previewCardWidthDp = previewCardWidth
                    val orientedCenterXDp = with(density) { cutoutInfo.centerX.toDp() } - previewHorizontalMarginDp
                    val punchHoleCenterXDp = orientedCenterXDp.coerceIn(
                        cutoutDiameterDp / 2f,
                        previewCardWidthDp - (cutoutDiameterDp / 2f),
                    )
                    val punchHoleCenterYDp = 18.dp

                    Box(
                        modifier = Modifier
                            .offset(
                                x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                                y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                            )
                            .size(cutoutDiameterDp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0D0D))
                            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                    )
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
