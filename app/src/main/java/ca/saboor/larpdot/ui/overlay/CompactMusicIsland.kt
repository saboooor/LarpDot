package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import kotlin.math.roundToInt
import ca.saboor.larpdot.ui.theme.LarpMotion

@Composable
private fun AnnouncementTrackText(
    title: String,
    artist: String,
    accentColor: Color,
    titleFontSize: Float,
    artistFontSize: Float,
    fadeWidth: Dp,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarqueeText(
            text = title.ifBlank { "Playing Track" },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
                fontSize = titleFontSize.sp,
                fontFamily = fontFamily,
            ),
            textAlign = TextAlign.Center,
            fadeWidth = fadeWidth,
            initialDelayMillis = 600,
            repeatDelayMillis = 1000,
            velocity = 35.dp,
        )
        MarqueeText(
            text = artist.ifBlank { "Media Player" },
            color = accentColor.copy(alpha = 0.95f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = artistFontSize.sp,
                fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
                fontFamily = fontFamily,
            ),
            textAlign = TextAlign.Center,
            fadeWidth = fadeWidth,
            initialDelayMillis = 600,
            repeatDelayMillis = 1000,
            velocity = 35.dp,
        )
    }
}

@Composable
private fun CompactMusicNestedArtwork(
    bitmap: Bitmap,
    size: Dp,
    shape: Shape,
    artworkGlow: AlbumArtGlowState,
    artworkAnchor: ArtworkAnchor?,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = remember(bitmap) { bitmap.asImageBitmap() },
        contentDescription = null,
        modifier = modifier
            .size(size)
            .albumArtGlow(artworkGlow, artworkAnchor)
            .clip(shape)
            .artworkTile(
                artworkAnchor,
                ArtworkStyle(shape = shape),
            ),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun CompactMusicBasicFadedArtwork(
    bitmap: Bitmap,
    isLandscape: Boolean,
    artworkGlow: AlbumArtGlowState,
    artworkAnchor: ArtworkAnchor?,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = remember(bitmap) { bitmap.asImageBitmap() },
        contentDescription = null,
        modifier = modifier
            .albumArtGlow(artworkGlow, artworkAnchor)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = if (isLandscape) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White,
                                1.00f to Color.Transparent,
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.White,
                                1.00f to Color.Transparent,
                            ),
                        )
                    },
                    blendMode = BlendMode.DstIn,
                )
            }
            .artworkTile(
                artworkAnchor,
                ArtworkStyle(fadeVertically = isLandscape),
            ),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun CompactMusicBlendedArtwork(
    bitmap: Bitmap,
    isLandscape: Boolean,
    dotRadiusPx: Float,
    artworkGlow: AlbumArtGlowState,
    artworkAnchor: ArtworkAnchor?,
    extraBleedDp: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val side = minOf(imageBitmap.width, imageBitmap.height)
    val cropX = (imageBitmap.width - side) / 2
    val cropY = (imageBitmap.height - side) / 2
    val eighth = (side / 8).coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (extraBleedDp > 0.dp) {
                    Modifier.layout { measurable, constraints ->
                        val extraPx = extraBleedDp.roundToPx()
                        if (isLandscape) {
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                    maxHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                ),
                            )
                            layout(placeable.width.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
                                placeable.place(0, 0)
                            }
                        } else {
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                    maxWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                ),
                            )
                            layout(constraints.maxWidth.coerceAtLeast(0), placeable.height.coerceAtLeast(0)) {
                                placeable.place(0, 0)
                            }
                        }
                    }
                } else Modifier
            )
            .progressiveBlur(
                direction = if (isLandscape) 1 else 0,
                startFraction = 0.65f,
                maxBlurDp = 24.dp,
                dotRadiusPx = dotRadiusPx,
            )
            .artworkTile(artworkAnchor, firstSquare = true),
    ) {
        if (isLandscape) {
            val w = size.width
            val artH = w
            val refH = (size.height - artH).coerceAtLeast(0f)
            if (refH > 0.5f) {
                scale(scaleX = 1f, scaleY = -1f, pivot = Offset(w / 2f, artH + refH / 2f)) {
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset(cropX, cropY + side - eighth),
                        srcSize = IntSize(side, eighth),
                        dstOffset = IntOffset(0, artH.roundToInt()),
                        dstSize = IntSize(w.roundToInt(), refH.roundToInt()),
                    )
                }
            }
            drawBlendedAlbumArtGlow(artworkGlow, isLandscape = true, artworkAnchor)
            if (artworkAnchor?.isMoving() != true) drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(cropX, cropY),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(w.roundToInt(), artH.roundToInt()),
            )
        } else {
            val h = size.height
            val artW = h
            val refW = (size.width - artW).coerceAtLeast(0f)
            if (refW > 0.5f) {
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(artW + refW / 2f, h / 2f)) {
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset(cropX + side - eighth, cropY),
                        srcSize = IntSize(eighth, side),
                        dstOffset = IntOffset(artW.roundToInt(), 0),
                        dstSize = IntSize(refW.roundToInt(), h.roundToInt()),
                    )
                }
            }
            drawBlendedAlbumArtGlow(artworkGlow, isLandscape = false, artworkAnchor)
            if (artworkAnchor?.isMoving() != true) drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(cropX, cropY),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(artW.roundToInt(), h.roundToInt()),
            )
        }
    }
}

@Composable
private fun CompactMusicArtworkPlaceholder(
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    nestedShape: OverlayPreferences.NestedAlbumArtShape,
    nestedRotation: Float,
    pillThickness: Dp,
    minNestedSize: Dp = 16.dp,
    sizeOverride: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val materialYouPrimary = MaterialTheme.colorScheme.primary
    val placeholderSize = sizeOverride ?: if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
        (pillThickness - 8.dp).coerceIn(minNestedSize, 24.dp)
    } else {
        pillThickness.coerceIn(minNestedSize, 32.dp)
    }
    val placeholderShape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
        nestedAlbumArtShape(nestedShape, nestedRotation)
    } else {
        RoundedCornerShape(6.dp)
    }
    Box(
        modifier = modifier
            .size(placeholderSize)
            .clip(placeholderShape)
            .background(materialYouPrimary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LarpIcons.MusicNote,
            contentDescription = "Music artwork placeholder",
            tint = materialYouPrimary,
            modifier = Modifier.size(placeholderSize * 0.58f),
        )
    }
}

@Composable
internal fun SecondaryMusicDotContent(
    mediaInfo: MediaTrackInfo,
    bubbleWidth: Dp,
    bubbleHeight: Dp,
    bubbleThickness: Dp,
    isMiniPill: Boolean,
    isLandscape: Boolean,
    beforeMain: Boolean,
    bubbleStyle: OverlayPreferences.BubbleStyle,
    artworkAnchor: ArtworkAnchor? = null,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState().value,
    showProgressRing: Boolean = OverlayPreferences.showProgressOutlineFlow.collectAsState().value,
) {
    val artworkGlow = rememberAlbumArtGlow(mediaInfo)
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { (bubbleThickness / 2f).toPx() }
    val extraSpan = if (isLandscape) (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp) else (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
    val isPillShaped = isMiniPill && extraSpan > 4.dp
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val outerPadding = 8.dp
    val innerPadding = if (grouped) 6.dp else outerPadding
    val ringDiameter = compactBatteryRingDiameter(bubbleThickness)
    val hasMusicProgressRing = showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L
    val contentShift = if (grouped && !isPillShaped && !hasMusicProgressRing) 1.5.dp else 0.dp
    val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
    val nestedArtSize = (bubbleThickness - 12.dp).coerceIn(14.dp, 24.dp)
    val accentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val livePositionMs = rememberLiveMediaPositionMs(mediaInfo)
    val musicProgressProvider: () -> Float = {
        if (mediaInfo.durationMs > 0L) (livePositionMs.value.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f) else 0f
    }

    val trailingEqualizer = @Composable {
        EqualizerWaveform(
            mediaInfo = mediaInfo,
            maxHeightDp = 11.5f,
            barCount = 3,
            barWidth = 2.5.dp,
            barSpacing = 1.5.dp,
        )
    }

    val albumArt = mediaInfo.albumArt
    val isEdgeToEdgeArtwork = albumArt != null && albumArtStyle != OverlayPreferences.AlbumArtStyle.NESTED

    if (isEdgeToEdgeArtwork) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (albumArtStyle) {
                OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                    CompactMusicBasicFadedArtwork(
                        bitmap = albumArt,
                        isLandscape = isLandscape,
                        artworkGlow = artworkGlow,
                        artworkAnchor = artworkAnchor,
                        modifier = if (isLandscape) {
                            Modifier
                                .align(if (isPillShaped) Alignment.TopCenter else Alignment.Center)
                                .fillMaxWidth()
                                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                        } else {
                            Modifier
                                .align(if (isPillShaped) Alignment.CenterStart else Alignment.Center)
                                .fillMaxHeight()
                                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        },
                    )
                }
                OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                OverlayPreferences.AlbumArtStyle.BLENDED -> {
                    if (isPillShaped) {
                        CompactMusicBlendedArtwork(
                            bitmap = albumArt,
                            isLandscape = isLandscape,
                            dotRadiusPx = dotRadiusPx,
                            artworkGlow = artworkGlow,
                            artworkAnchor = artworkAnchor,
                            extraBleedDp = 0.dp,
                        )
                    } else {
                        Image(
                            bitmap = remember(albumArt) { albumArt.asImageBitmap() },
                            contentDescription = "Music Artwork",
                            modifier = Modifier
                                .fillMaxSize()
                                .artworkTile(artworkAnchor, ArtworkStyle(shape = CircleShape)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                OverlayPreferences.AlbumArtStyle.NESTED -> Unit
            }
            if (hasMusicProgressRing) {
                val dotArtworkShape = if (!isPillShaped && grouped) {
                    groupedIslandShape(bubbleThickness / 2f, !beforeMain, beforeMain, isLandscape)
                } else CircleShape
                Box(
                    modifier = Modifier
                        .align(
                            when {
                                !isPillShaped -> Alignment.Center
                                isLandscape -> Alignment.TopCenter
                                else -> Alignment.CenterStart
                            }
                        )
                        .size(bubbleThickness),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactMusicProgressRing(
                        progressProvider = musicProgressProvider,
                        accentColor = accentColor,
                        shape = dotArtworkShape,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp),
                    )
                }
            }
            if (isPillShaped) {
                Box(
                    modifier = Modifier
                        .align(if (isLandscape) Alignment.BottomCenter else Alignment.CenterEnd)
                        .padding(
                            bottom = if (isLandscape) (if (beforeMain) innerPadding else outerPadding) else 0.dp,
                            end = if (!isLandscape) (if (beforeMain) innerPadding else outerPadding) else 0.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingEqualizer()
                }
            }
        }
        return
    }

    val leadingContent = @Composable {
        val shapedArtSize = if (hasMusicProgressRing) ringDiameter else nestedArtSize
        val innerArtSize = if (hasMusicProgressRing) (shapedArtSize - 5.dp).coerceAtLeast(11.dp) else nestedArtSize
        val leadingShape = if (albumArt != null || albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
            artworkShape
        } else {
            RoundedCornerShape(6.dp)
        }
        val artworkNode = @Composable {
            if (albumArt != null) {
                CompactMusicNestedArtwork(
                    bitmap = albumArt,
                    size = innerArtSize,
                    shape = artworkShape,
                    artworkGlow = artworkGlow,
                    artworkAnchor = artworkAnchor,
                )
            } else {
                CompactMusicArtworkPlaceholder(
                    albumArtStyle = albumArtStyle,
                    nestedShape = nestedShape,
                    nestedRotation = nestedRotation,
                    pillThickness = bubbleThickness,
                    minNestedSize = if (hasMusicProgressRing) 11.dp else 14.dp,
                    sizeOverride = if (hasMusicProgressRing) innerArtSize else null,
                )
            }
        }
        if (hasMusicProgressRing) {
            Box(
                modifier = Modifier.size(shapedArtSize),
                contentAlignment = Alignment.Center,
            ) {
                artworkNode()
                CompactMusicProgressRing(
                    progressProvider = musicProgressProvider,
                    accentColor = accentColor,
                    shape = leadingShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            artworkNode()
        }
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        top = if (beforeMain) outerPadding else innerPadding,
                        bottom = if (beforeMain) innerPadding else outerPadding,
                    ) else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.scale(iconScale),
                contentAlignment = Alignment.Center,
            ) {
                leadingContent()
            }
            if (isPillShaped) {
                Box(contentAlignment = Alignment.Center) {
                    trailingEqualizer()
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        start = if (beforeMain) outerPadding else innerPadding,
                        end = if (beforeMain) innerPadding else outerPadding,
                    ) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.scale(iconScale),
                contentAlignment = Alignment.Center,
            ) {
                leadingContent()
            }
            if (isPillShaped) {
                Box(contentAlignment = Alignment.Center) {
                    trailingEqualizer()
                }
            }
        }
    }
}

@Composable
internal fun CompactIslandContent(
    mediaInfo: MediaTrackInfo,
    cutoutDiameterDp: Dp,
    modifier: Modifier = Modifier,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState().value,
    showDominantGlow: Boolean = OverlayPreferences.showMinimizedDominantColorGlowFlow.collectAsState().value,
    showMinimizedTitle: Boolean = OverlayPreferences.showMinimizedTitleFlow.collectAsState().value,
    showVisualizer: Boolean = OverlayPreferences.showMinimizedVisualizerFlow.collectAsState().value,
    waveformBandCount: Int = OverlayPreferences.waveformBandCountFlow.collectAsState().value,
    waveformBarWidth: Float = OverlayPreferences.waveformBarWidthFlow.collectAsState().value,
    waveformBarSpacing: Float = OverlayPreferences.waveformBarSpacingFlow.collectAsState().value,
    bpmPulseEnabled: Boolean = OverlayPreferences.minimizedBpmPulseEnabledFlow.collectAsState().value,
    pulseAmount: Float = OverlayPreferences.pulseAmountFlow.collectAsState().value,
    isSongAnnouncement: Boolean = false,
    trailingWingFraction: Float = 0.5f,
    artworkAnchor: ArtworkAnchor? = null,
    showProgressRing: Boolean = OverlayPreferences.showProgressOutlineFlow.collectAsState().value,
    progressProvider: (() -> Float)? = null,
    onAlbumArtClick: (() -> Unit)? = null,
    onVisualizerClick: (() -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    highlightAlbumArt: Boolean = false,
    highlightVisualizer: Boolean = false,
) {
    val artworkGlow = rememberAlbumArtGlow(mediaInfo)
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val materialYouPrimary = MaterialTheme.colorScheme.primary
    val accentColor = if (mediaInfo.albumArt == null) materialYouPrimary else mediaInfo.dominantColor
    val dotRadiusPx = with(density) { (cutoutDiameterDp / 2f).toPx() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 0.28f else 0.08f,
        animationSpec = LarpMotion.fade(360),
        label = "compact_glow_alpha",
    )
    val announcementAlpha by animateFloatAsState(
        targetValue = if (isSongAnnouncement) 1f else 0f,
        animationSpec = LarpMotion.fade(if (isSongAnnouncement) 160 else 180),
        label = "announcement_text_alpha",
    )
    val showAnnouncementText = isSongAnnouncement || announcementAlpha > 0.01f

    val currentBpm by BpmDetector.currentBpm.collectAsState()
    LaunchedEffect(mediaInfo.title, mediaInfo.artist) {
        if (mediaInfo.title.isNotBlank()) {
            BpmDetector.syncTrack(mediaInfo.title, mediaInfo.artist)
        }
    }
    val liveMediaPositionMs = rememberLiveMediaPositionMs(mediaInfo, useLiveStream = progressProvider == null)
    val musicProgressProvider: () -> Float = progressProvider ?: {
        if (mediaInfo.durationMs > 0L) (liveMediaPositionMs.value.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f) else 0f
    }
    val hasMusicProgressRing = showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L
    val compactRingDiameter = compactBatteryRingDiameter(cutoutDiameterDp)
    val bpmPulseState = rememberBpmPulse(
        bpm = currentBpm,
        positionMsProvider = { liveMediaPositionMs.value },
        isPlaying = mediaInfo.isPlaying,
        enabled = bpmPulseEnabled,
    )
    val glowAlphaProvider: () -> Float = {
        if (bpmPulseEnabled && mediaInfo.isPlaying) {
            0.16f + (0.22f * bpmPulseState.value * pulseAmount)
        } else {
            glowAlpha
        }
    }

    val containerModifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .then(if (onBackgroundClick != null) Modifier.clickable(onClick = onBackgroundClick) else Modifier)

    if (isLandscape) {
        Column(
            modifier = containerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompactMusicArtworkWing(
                mediaInfo = mediaInfo,
                isLandscape = true,
                albumArtStyle = albumArtStyle,
                nestedShape = nestedShape,
                nestedRotation = nestedRotation,
                artworkShape = artworkShape,
                artworkGlow = artworkGlow,
                artworkAnchor = artworkAnchor,
                cutoutDiameterDp = cutoutDiameterDp,
                dotRadiusPx = dotRadiusPx,
                compactRingDiameter = compactRingDiameter,
                hasProgressRing = hasMusicProgressRing,
                progressProvider = musicProgressProvider,
                accentColor = accentColor,
                showAnnouncementText = false,
                announcementAlpha = 0f,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            CompactMusicTrailingWing(
                mediaInfo = mediaInfo,
                isLandscape = true,
                albumArtStyle = albumArtStyle,
                cutoutDiameterDp = cutoutDiameterDp,
                accentColor = accentColor,
                showDominantGlow = showDominantGlow,
                glowAlphaProvider = glowAlphaProvider,
                showAnnouncementText = showAnnouncementText,
                announcementAlpha = announcementAlpha,
                showMinimizedTitle = showMinimizedTitle,
                showVisualizer = showVisualizer,
                waveformBandCount = waveformBandCount,
                waveformBarWidth = waveformBarWidth,
                waveformBarSpacing = waveformBarSpacing,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    } else {
        val leadingWingFraction = 1f - trailingWingFraction
        val albumArtWingInteraction = Modifier
            .then(if (onAlbumArtClick != null) Modifier.clickable(onClick = onAlbumArtClick) else Modifier)
            .then(
                if (highlightAlbumArt) Modifier.border(
                    1.5.dp,
                    materialYouPrimary,
                    RoundedCornerShape(topStart = cutoutDiameterDp / 2f, bottomStart = cutoutDiameterDp / 2f),
                ) else Modifier
            )
        val compactVisualizerVisible = showVisualizer && !(!showAnnouncementText && showMinimizedTitle && mediaInfo.title.isNotBlank())
        val visualizerWingInteraction = Modifier
            .then(
                if (onVisualizerClick != null && compactVisualizerVisible) {
                    Modifier.clickable(onClick = onVisualizerClick)
                } else Modifier
            )
            .then(
                if (highlightVisualizer && compactVisualizerVisible) {
                    Modifier.border(
                        1.5.dp,
                        materialYouPrimary,
                        RoundedCornerShape(topEnd = cutoutDiameterDp / 2f, bottomEnd = cutoutDiameterDp / 2f),
                    )
                } else Modifier
            )
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactMusicArtworkWing(
                mediaInfo = mediaInfo,
                isLandscape = false,
                albumArtStyle = albumArtStyle,
                nestedShape = nestedShape,
                nestedRotation = nestedRotation,
                artworkShape = artworkShape,
                artworkGlow = artworkGlow,
                artworkAnchor = artworkAnchor,
                cutoutDiameterDp = cutoutDiameterDp,
                dotRadiusPx = dotRadiusPx,
                compactRingDiameter = compactRingDiameter,
                hasProgressRing = hasMusicProgressRing,
                progressProvider = musicProgressProvider,
                accentColor = accentColor,
                showAnnouncementText = showAnnouncementText,
                announcementAlpha = announcementAlpha,
                modifier = Modifier
                    .weight(leadingWingFraction)
                    .fillMaxHeight()
                    .then(albumArtWingInteraction),
            )
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            CompactMusicTrailingWing(
                mediaInfo = mediaInfo,
                isLandscape = false,
                albumArtStyle = albumArtStyle,
                cutoutDiameterDp = cutoutDiameterDp,
                accentColor = accentColor,
                showDominantGlow = showDominantGlow,
                glowAlphaProvider = glowAlphaProvider,
                showAnnouncementText = showAnnouncementText,
                announcementAlpha = announcementAlpha,
                showMinimizedTitle = showMinimizedTitle,
                showVisualizer = showVisualizer,
                waveformBandCount = waveformBandCount,
                waveformBarWidth = waveformBarWidth,
                waveformBarSpacing = waveformBarSpacing,
                modifier = Modifier
                    .weight(trailingWingFraction)
                    .fillMaxHeight()
                    .then(visualizerWingInteraction),
            )
        }
    }
}

@Composable
private fun CompactMusicArtworkWing(
    mediaInfo: MediaTrackInfo,
    isLandscape: Boolean,
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    nestedShape: OverlayPreferences.NestedAlbumArtShape,
    nestedRotation: Float,
    artworkShape: Shape,
    artworkGlow: AlbumArtGlowState,
    artworkAnchor: ArtworkAnchor?,
    cutoutDiameterDp: Dp,
    dotRadiusPx: Float,
    compactRingDiameter: Dp,
    hasProgressRing: Boolean,
    progressProvider: () -> Float,
    accentColor: Color,
    showAnnouncementText: Boolean,
    announcementAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val isBlendedStyle = albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
        albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
    val albumArt = mediaInfo.albumArt
    if (albumArt == null) {
        val alignment = when {
            isLandscape -> if (isBlendedStyle) Alignment.Center else Alignment.BottomCenter
            showAnnouncementText || isBlendedStyle -> Alignment.Center
            else -> Alignment.CenterEnd
        }
        Box(modifier = modifier, contentAlignment = alignment) {
            if (!isLandscape && showAnnouncementText) {
                CompactMusicAnnouncementTitle(
                    title = mediaInfo.title,
                    alpha = announcementAlpha,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp),
                )
            } else {
                CompactMusicPlaceholderWithRing(
                    albumArtStyle = albumArtStyle,
                    nestedShape = nestedShape,
                    nestedRotation = nestedRotation,
                    artworkShape = artworkShape,
                    cutoutDiameterDp = cutoutDiameterDp,
                    ringDiameter = compactRingDiameter,
                    hasProgressRing = hasProgressRing,
                    progressProvider = progressProvider,
                    accentColor = accentColor,
                )
            }
        }
        return
    }

    when (albumArtStyle) {
        OverlayPreferences.AlbumArtStyle.NESTED -> {
            val albumArtSize = (cutoutDiameterDp - 12.dp).coerceIn(16.dp, 24.dp)
            val renderedArtSize = if (hasProgressRing) maxOf(albumArtSize, compactRingDiameter) else albumArtSize
            val innerArtSize = if (hasProgressRing) (renderedArtSize - 5.dp).coerceAtLeast(11.dp) else albumArtSize
            if (isLandscape) {
                Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
                    CompactMusicNestedArtworkWithRing(
                        bitmap = albumArt,
                        renderedSize = renderedArtSize,
                        innerSize = innerArtSize,
                        shape = artworkShape,
                        artworkGlow = artworkGlow,
                        artworkAnchor = artworkAnchor,
                        hasProgressRing = hasProgressRing,
                        progressProvider = progressProvider,
                        accentColor = accentColor,
                    )
                }
            } else {
                val artStartInset = ((cutoutDiameterDp - renderedArtSize) / 2f).coerceAtLeast(3.dp)
                Row(
                    modifier = modifier.padding(
                        start = if (showAnnouncementText) artStartInset else 0.dp,
                        end = if (showAnnouncementText) 2.dp else 0.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (showAnnouncementText) Arrangement.spacedBy(4.dp) else Arrangement.End,
                ) {
                    CompactMusicNestedArtworkWithRing(
                        bitmap = albumArt,
                        renderedSize = renderedArtSize,
                        innerSize = innerArtSize,
                        shape = artworkShape,
                        artworkGlow = artworkGlow,
                        artworkAnchor = artworkAnchor,
                        hasProgressRing = hasProgressRing,
                        progressProvider = progressProvider,
                        accentColor = accentColor,
                    )
                    if (showAnnouncementText) {
                        CompactMusicAnnouncementTitle(
                            title = mediaInfo.title,
                            alpha = announcementAlpha,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
            Box(
                modifier = modifier,
                contentAlignment = if (isLandscape) Alignment.BottomCenter else Alignment.Center,
            ) {
                val artContainerModifier = if (isLandscape) {
                    Modifier.fillMaxWidth().aspectRatio(1f, matchHeightConstraintsFirst = false)
                } else {
                    Modifier
                        .align(if (showAnnouncementText) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                }
                Box(modifier = artContainerModifier, contentAlignment = Alignment.Center) {
                    CompactMusicBasicFadedArtwork(
                        bitmap = albumArt,
                        isLandscape = isLandscape,
                        artworkGlow = artworkGlow,
                        artworkAnchor = artworkAnchor,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (hasProgressRing) {
                        CompactMusicProgressRing(
                            progressProvider = progressProvider,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxSize().padding(1.dp),
                        )
                    }
                }
                if (!isLandscape && showAnnouncementText) {
                    CompactMusicAnnouncementScrimAndTitle(mediaInfo.title, announcementAlpha)
                }
            }
        }
        OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
        OverlayPreferences.AlbumArtStyle.BLENDED -> {
            Box(
                modifier = modifier,
                contentAlignment = if (isLandscape) Alignment.TopCenter else Alignment.Center,
            ) {
                CompactMusicBlendedArtwork(
                    bitmap = albumArt,
                    isLandscape = isLandscape,
                    dotRadiusPx = dotRadiusPx,
                    artworkGlow = artworkGlow,
                    artworkAnchor = artworkAnchor,
                )
                if (hasProgressRing) {
                    Box(
                        modifier = Modifier
                            .align(if (isLandscape) Alignment.TopCenter else Alignment.CenterStart)
                            .size(cutoutDiameterDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactMusicProgressRing(
                            progressProvider = progressProvider,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxSize().padding(1.dp),
                        )
                    }
                }
                if (!isLandscape && showAnnouncementText) {
                    CompactMusicAnnouncementScrimAndTitle(mediaInfo.title, announcementAlpha)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CompactMusicAnnouncementScrimAndTitle(
    title: String,
    announcementAlpha: Float,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = announcementAlpha }
            .background(Color.Black.copy(alpha = 0.34f)),
    )
    CompactMusicAnnouncementTitle(
        title = title,
        alpha = announcementAlpha,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp),
    )
}

@Composable
private fun CompactMusicTrailingWing(
    mediaInfo: MediaTrackInfo,
    isLandscape: Boolean,
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    cutoutDiameterDp: Dp,
    accentColor: Color,
    showDominantGlow: Boolean,
    glowAlphaProvider: () -> Float,
    showAnnouncementText: Boolean,
    announcementAlpha: Float,
    showMinimizedTitle: Boolean,
    showVisualizer: Boolean,
    waveformBandCount: Int,
    waveformBarWidth: Float,
    waveformBarSpacing: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.compactDominantGlow(
            enabled = showDominantGlow,
            vertical = isLandscape,
            accentColor = accentColor,
            alphaProvider = glowAlphaProvider,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showAnnouncementText) Modifier.padding(horizontal = 4.dp, vertical = 4.dp) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (showAnnouncementText) {
                    Box(modifier = Modifier.graphicsLayer { alpha = announcementAlpha }) {
                        AnnouncementTrackText(
                            title = mediaInfo.title,
                            artist = mediaInfo.artist,
                            accentColor = accentColor,
                            titleFontSize = 10.5f,
                            artistFontSize = 9f,
                            fadeWidth = 6.dp,
                        )
                    }
                }
                if (showVisualizer) {
                    EqualizerWaveform(
                        mediaInfo = mediaInfo,
                        maxHeightDp = 13.5f,
                        barCount = waveformBandCount,
                        barWidth = waveformBarWidth.dp,
                        barSpacing = waveformBarSpacing.dp,
                    )
                }
            }
        } else {
            val equalizerShift = if (
                albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
            ) 0.dp else -(cutoutDiameterDp / if (waveformBandCount >= 6) 16f else 8f)
            val visualizerEndInset = (cutoutDiameterDp / 3f).coerceAtLeast(8.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showAnnouncementText) Modifier.padding(start = 2.dp, end = visualizerEndInset)
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showAnnouncementText) Arrangement.spacedBy(4.dp) else Arrangement.Center,
            ) {
                if (showAnnouncementText) {
                    MarqueeText(
                        text = mediaInfo.artist.ifBlank { "Media Player" },
                        color = accentColor.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = announcementAlpha },
                        textAlign = TextAlign.Center,
                        fadeWidth = 6.dp,
                        initialDelayMillis = 600,
                        repeatDelayMillis = 1000,
                        velocity = 35.dp,
                    )
                }
                if (!showAnnouncementText && showMinimizedTitle && mediaInfo.title.isNotBlank()) {
                    MarqueeText(
                        text = mediaInfo.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        fadeWidth = 6.dp,
                    )
                } else if (showVisualizer) {
                    EqualizerWaveform(
                        mediaInfo = mediaInfo,
                        maxHeightDp = 13.5f,
                        barCount = waveformBandCount,
                        barWidth = waveformBarWidth.dp,
                        barSpacing = waveformBarSpacing.dp,
                        modifier = Modifier.offset(x = if (showAnnouncementText) 0.dp else equalizerShift),
                    )
                }
            }
        }
    }
}

private fun Modifier.compactDominantGlow(
    enabled: Boolean,
    vertical: Boolean,
    accentColor: Color,
    alphaProvider: () -> Float,
): Modifier = if (!enabled) this else drawWithCache {
    val stops = listOf(Color.Transparent, accentColor)
    val gradient = if (vertical) Brush.verticalGradient(stops) else Brush.horizontalGradient(stops)
    onDrawBehind {
        drawRect(brush = gradient, alpha = alphaProvider())
    }
}

@Composable
private fun CompactMusicProgressRing(
    progressProvider: () -> Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
) {
    RadialProgressRing(
        progressProvider = progressProvider,
        color = accentColor,
        trackColor = Color.White.copy(alpha = 0.28f),
        strokeWidth = 2.dp,
        shape = shape,
        modifier = modifier,
    )
}

@Composable
private fun CompactMusicNestedArtworkWithRing(
    bitmap: Bitmap,
    renderedSize: Dp,
    innerSize: Dp,
    shape: Shape,
    artworkGlow: AlbumArtGlowState,
    artworkAnchor: ArtworkAnchor?,
    hasProgressRing: Boolean,
    progressProvider: () -> Float,
    accentColor: Color,
) {
    Box(
        modifier = Modifier.size(renderedSize),
        contentAlignment = Alignment.Center,
    ) {
        CompactMusicNestedArtwork(
            bitmap = bitmap,
            size = innerSize,
            shape = shape,
            artworkGlow = artworkGlow,
            artworkAnchor = artworkAnchor,
        )
        if (hasProgressRing) {
            CompactMusicProgressRing(
                progressProvider = progressProvider,
                accentColor = accentColor,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CompactMusicPlaceholderWithRing(
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    nestedShape: OverlayPreferences.NestedAlbumArtShape,
    nestedRotation: Float,
    artworkShape: Shape,
    cutoutDiameterDp: Dp,
    ringDiameter: Dp,
    hasProgressRing: Boolean,
    progressProvider: () -> Float,
    accentColor: Color,
) {
    Box(
        modifier = if (hasProgressRing) Modifier.size(ringDiameter) else Modifier,
        contentAlignment = Alignment.Center,
    ) {
        CompactMusicArtworkPlaceholder(
            albumArtStyle = albumArtStyle,
            nestedShape = nestedShape,
            nestedRotation = nestedRotation,
            pillThickness = cutoutDiameterDp,
            sizeOverride = if (hasProgressRing) (ringDiameter - 5.dp).coerceAtLeast(11.dp) else null,
        )
        if (hasProgressRing) {
            CompactMusicProgressRing(
                progressProvider = progressProvider,
                accentColor = accentColor,
                shape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) artworkShape else RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CompactMusicAnnouncementTitle(
    title: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    MarqueeText(
        text = title.ifBlank { "Playing Track" },
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
            fontSize = 11.5.sp,
        ),
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        textAlign = TextAlign.Center,
        fadeWidth = 6.dp,
        initialDelayMillis = 600,
        repeatDelayMillis = 1000,
        velocity = 35.dp,
    )
}

