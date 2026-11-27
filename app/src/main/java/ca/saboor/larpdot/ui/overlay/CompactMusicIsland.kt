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
import androidx.compose.ui.graphics.drawscope.translate
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
    isTrailing: Boolean = false,
) {
    val stops = if (isTrailing) {
        arrayOf(0.00f to Color.Transparent, 1.00f to Color.White)
    } else {
        arrayOf(0.00f to Color.White, 1.00f to Color.Transparent)
    }
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
                        Brush.verticalGradient(colorStops = stops)
                    } else {
                        Brush.horizontalGradient(colorStops = stops)
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
    modifier: Modifier = Modifier,
    extraBleedDp: Dp = 6.dp,
    isTrailing: Boolean = false,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val side = minOf(imageBitmap.width, imageBitmap.height)
    val cropX = (imageBitmap.width - side) / 2
    val cropY = (imageBitmap.height - side) / 2
    val eighth = (side / 8).coerceAtLeast(1)
    val blurDirection = when {
        isLandscape && isTrailing -> 5
        isLandscape -> 1
        isTrailing -> 4
        else -> 0
    }

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
                                placeable.place(0, if (isTrailing) -extraPx else 0)
                            }
                        } else {
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                    maxWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                ),
                            )
                            layout(constraints.maxWidth.coerceAtLeast(0), placeable.height.coerceAtLeast(0)) {
                                placeable.place(if (isTrailing) -extraPx else 0, 0)
                            }
                        }
                    }
                } else Modifier
            )
            .progressiveBlur(
                direction = blurDirection,
                startFraction = 0.65f,
                maxBlurDp = 24.dp,
                dotRadiusPx = dotRadiusPx,
            )
            .artworkTile(artworkAnchor, firstSquare = !isTrailing),
    ) {
        if (isLandscape) {
            val w = size.width
            val artH = w
            val refH = (size.height - artH).coerceAtLeast(0f)
            if (isTrailing) {
                if (refH > 0.5f) {
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(w / 2f, refH / 2f)) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(cropX, cropY),
                            srcSize = IntSize(side, eighth),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(w.roundToInt(), refH.roundToInt()),
                        )
                    }
                }
                translate(top = refH) {
                    drawBlendedAlbumArtGlow(artworkGlow, isLandscape = true, artworkAnchor)
                }
                if (artworkAnchor?.isMoving() != true) drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(cropX, cropY),
                    srcSize = IntSize(side, side),
                    dstOffset = IntOffset(0, refH.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), artH.roundToInt()),
                )
            } else {
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
            }
        } else {
            val h = size.height
            val artW = h
            val refW = (size.width - artW).coerceAtLeast(0f)
            if (isTrailing) {
                if (refW > 0.5f) {
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(refW / 2f, h / 2f)) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(cropX, cropY),
                            srcSize = IntSize(eighth, side),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(refW.roundToInt(), h.roundToInt()),
                        )
                    }
                }
                translate(left = refW) {
                    drawBlendedAlbumArtGlow(artworkGlow, isLandscape = false, artworkAnchor)
                }
                if (artworkAnchor?.isMoving() != true) drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(cropX, cropY),
                    srcSize = IntSize(side, side),
                    dstOffset = IntOffset(refW.roundToInt(), 0),
                    dstSize = IntSize(artW.roundToInt(), h.roundToInt()),
                )
            } else {
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
}

internal fun compactNestedArtworkSize(thickness: Dp, minimum: Dp = 16.dp): Dp =
    (thickness - 12.dp).coerceIn(minimum, 24.dp).coerceAtMost(thickness)

@Composable
private fun CompactMusicArtworkPlaceholder(
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    nestedShape: OverlayPreferences.NestedAlbumArtShape,
    nestedRotation: Float,
    placeholderSize: Dp,
    modifier: Modifier = Modifier,
) {
    val materialYouPrimary = MaterialTheme.colorScheme.primary
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
    visualizerAnchor: IslandElementAnchor? = null,
    albumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState().value,
    showProgressRing: Boolean = OverlayPreferences.showProgressOutlineFlow.collectAsState().value,
) {
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { (bubbleThickness / 2f).toPx() }
    val extraSpan = if (isLandscape) (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp) else (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
    val isPillShaped = isMiniPill && extraSpan > 4.dp
    val usesShapedGlow = !isPillShaped || albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED
    val artworkGlow = rememberAlbumArtGlow(
        media = mediaInfo,
        shape = if (usesShapedGlow) artworkShape else null,
        shapeKey = if (usesShapedGlow) AlbumArtGlowShapeKey.of(nestedShape, nestedRotation) else null,
    )
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val outerPadding = 8.dp
    val innerPadding = if (grouped) 6.dp else outerPadding
    val ringDiameter = compactBatteryRingDiameter(bubbleThickness)
    val hasMusicProgressRing = showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L
    val hasCircularArtwork = nestedShape == OverlayPreferences.NestedAlbumArtShape.CIRCLE &&
        (mediaInfo.albumArt != null || albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED)
    val contentShift = if (grouped && !isPillShaped && !hasMusicProgressRing && !hasCircularArtwork) 1.5.dp else 0.dp
    val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
    val nestedArtSize = compactNestedArtworkSize(bubbleThickness, minimum = 14.dp)
    val leadingSize = if (hasMusicProgressRing) ringDiameter else nestedArtSize
    val leadingInset = compactEndInset(bubbleThickness, leadingSize)
    val accentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val livePositionMs = rememberLiveMediaPositionMs(mediaInfo)
    val musicProgressProvider: () -> Float = {
        if (mediaInfo.durationMs > 0L) (livePositionMs.value.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f) else 0f
    }

    val leftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    val bandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val barWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val barSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val trailingSide = secondaryMusicTrailingSide(leftSide, rightSide)
    val trailingInfo = compactMusicRightInfoForSide(trailingSide, mediaInfo.title.isNotBlank())
    val trailingTimestamp = timestampForMusicSide(trailingSide)
    val trailingContent = @Composable {
        CompactMusicRightInfoContent(
            trailingInfo, mediaInfo, livePositionMs, trailingTimestamp, isLandscape, bandCount, barWidth, barSpacing,
            visualizerAnchor, maxHeightDp = 11.5f,
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
                            top = if (isLandscape) bubbleThickness else 0.dp,
                            start = if (!isLandscape) bubbleThickness else 0.dp,
                            bottom = if (isLandscape) (if (beforeMain) innerPadding else outerPadding) else 0.dp,
                            end = if (!isLandscape) (if (beforeMain) innerPadding else outerPadding) else 0.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingContent()
                }
            }
        }
        return
    }

    val leadingContent = @Composable {
        val innerArtSize = if (hasMusicProgressRing) leadingSize - 5.dp else nestedArtSize
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
                    placeholderSize = innerArtSize,
                )
            }
        }
        if (hasMusicProgressRing) {
            Box(
                modifier = Modifier.size(leadingSize),
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
                        top = leadingInset,
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
                    trailingContent()
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
                        start = leadingInset,
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
                    trailingContent()
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
    visualizerAnchor: IslandElementAnchor? = null,
    showProgressRing: Boolean = OverlayPreferences.showProgressOutlineFlow.collectAsState().value,
    progressProvider: (() -> Float)? = null,
    onAlbumArtClick: (() -> Unit)? = null,
    onRightWingClick: (() -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    highlightAlbumArt: Boolean = false,
    highlightRightWing: Boolean = false,
) {
    val artworkShape = nestedAlbumArtShape(nestedShape, nestedRotation)
    val usesShapedGlow = albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED
    val artworkGlow = rememberAlbumArtGlow(
        media = mediaInfo,
        shape = if (usesShapedGlow) artworkShape else null,
        shapeKey = if (usesShapedGlow) AlbumArtGlowShapeKey.of(nestedShape, nestedRotation) else null,
    )
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

    val leftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    val leftHasArt = leftSide == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART
    val rightHasArt = rightSide == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART
    val leftHasViz = leftSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER
    val rightHasViz = rightSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER
    val leftArtworkAnchor = if (leftHasArt) artworkAnchor else null
    val rightArtworkAnchor = if (rightHasArt && !leftHasArt) artworkAnchor else null
    val rightVisualizerAnchor = if (rightHasViz) visualizerAnchor else null
    val leftVisualizerAnchor = if (leftHasViz && !rightHasViz) visualizerAnchor else null

    val containerModifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .then(if (onBackgroundClick != null) Modifier.clickable(onClick = onBackgroundClick) else Modifier)

    val leftWingInteraction = Modifier
        .then(if (onAlbumArtClick != null) Modifier.clickable(onClick = onAlbumArtClick) else Modifier)
        .then(
            if (highlightAlbumArt) Modifier.border(
                1.5.dp,
                materialYouPrimary,
                if (isLandscape) {
                    RoundedCornerShape(topStart = cutoutDiameterDp / 2f, topEnd = cutoutDiameterDp / 2f)
                } else {
                    RoundedCornerShape(topStart = cutoutDiameterDp / 2f, bottomStart = cutoutDiameterDp / 2f)
                },
            ) else Modifier
        )

    val rightWingInteraction = Modifier
        .then(if (onRightWingClick != null) Modifier.clickable(onClick = onRightWingClick) else Modifier)
        .then(
            if (highlightRightWing) Modifier.border(
                1.5.dp,
                materialYouPrimary,
                if (isLandscape) {
                    RoundedCornerShape(bottomStart = cutoutDiameterDp / 2f, bottomEnd = cutoutDiameterDp / 2f)
                } else {
                    RoundedCornerShape(topEnd = cutoutDiameterDp / 2f, bottomEnd = cutoutDiameterDp / 2f)
                },
            ) else Modifier
        )

    @Composable
    fun RenderMusicWing(
        sideContent: OverlayPreferences.MinimizedMusicSideContent,
        isTrailing: Boolean,
        wingModifier: Modifier,
        wingArtworkAnchor: ArtworkAnchor?,
        wingVisualizerAnchor: IslandElementAnchor?,
    ) {
        if (sideContent == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART) {
            CompactMusicArtworkWing(
                mediaInfo = mediaInfo,
                isLandscape = isLandscape,
                isTrailing = isTrailing,
                albumArtStyle = albumArtStyle,
                nestedShape = nestedShape,
                nestedRotation = nestedRotation,
                artworkShape = artworkShape,
                artworkGlow = artworkGlow,
                artworkAnchor = wingArtworkAnchor,
                cutoutDiameterDp = cutoutDiameterDp,
                dotRadiusPx = dotRadiusPx,
                compactRingDiameter = compactRingDiameter,
                hasProgressRing = hasMusicProgressRing,
                progressProvider = musicProgressProvider,
                accentColor = accentColor,
                showAnnouncementText = !isLandscape && showAnnouncementText && !isTrailing,
                announcementAlpha = if (!isLandscape && !isTrailing) announcementAlpha else 0f,
                modifier = wingModifier,
            )
        } else {
            CompactMusicSideInfoWing(
                sideContent = sideContent,
                isLeading = !isTrailing,
                mediaInfo = mediaInfo,
                isLandscape = isLandscape,
                cutoutDiameterDp = cutoutDiameterDp,
                accentColor = accentColor,
                showDominantGlow = showDominantGlow,
                glowAlphaProvider = glowAlphaProvider,
                showAnnouncementText = showAnnouncementText,
                announcementAlpha = announcementAlpha,
                livePositionMs = liveMediaPositionMs,
                waveformBandCount = waveformBandCount,
                waveformBarWidth = waveformBarWidth,
                waveformBarSpacing = waveformBarSpacing,
                visualizerAnchor = wingVisualizerAnchor,
                modifier = wingModifier,
            )
        }
    }

    if (isLandscape) {
        Column(
            modifier = containerModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderMusicWing(
                sideContent = leftSide,
                isTrailing = false,
                wingModifier = Modifier.weight(1f).fillMaxWidth().then(leftWingInteraction),
                wingArtworkAnchor = leftArtworkAnchor,
                wingVisualizerAnchor = leftVisualizerAnchor,
            )
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            RenderMusicWing(
                sideContent = rightSide,
                isTrailing = true,
                wingModifier = Modifier.weight(1f).fillMaxWidth().then(rightWingInteraction),
                wingArtworkAnchor = rightArtworkAnchor,
                wingVisualizerAnchor = rightVisualizerAnchor,
            )
        }
    } else {
        val leadingWingFraction = 1f - trailingWingFraction
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RenderMusicWing(
                sideContent = leftSide,
                isTrailing = false,
                wingModifier = Modifier
                    .weight(leadingWingFraction)
                    .fillMaxHeight()
                    .then(leftWingInteraction),
                wingArtworkAnchor = leftArtworkAnchor,
                wingVisualizerAnchor = leftVisualizerAnchor,
            )
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            RenderMusicWing(
                sideContent = rightSide,
                isTrailing = true,
                wingModifier = Modifier
                    .weight(trailingWingFraction)
                    .fillMaxHeight()
                    .then(rightWingInteraction),
                wingArtworkAnchor = rightArtworkAnchor,
                wingVisualizerAnchor = rightVisualizerAnchor,
            )
        }
    }
}

@Composable
private fun CompactMusicArtworkWing(
    mediaInfo: MediaTrackInfo,
    isLandscape: Boolean,
    isTrailing: Boolean,
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
        val isNested = albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED
        val placeholderSize = if (isNested) {
            compactNestedArtworkSize(cutoutDiameterDp)
        } else {
            cutoutDiameterDp.coerceIn(16.dp, 32.dp)
        }
        val renderedSize = if (hasProgressRing) compactRingDiameter else placeholderSize
        val artInset = if (isNested) compactEndInset(cutoutDiameterDp, renderedSize) else 0.dp
        val alignment = when {
            isLandscape && isNested -> if (isTrailing) Alignment.BottomCenter else Alignment.TopCenter
            isNested && !showAnnouncementText -> if (isTrailing) Alignment.CenterEnd else Alignment.CenterStart
            isLandscape -> when {
                isBlendedStyle -> Alignment.Center
                isTrailing -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
            showAnnouncementText || isBlendedStyle -> Alignment.Center
            isTrailing -> Alignment.CenterStart
            else -> Alignment.CenterEnd
        }
        Box(
            modifier = modifier.padding(
                start = if (!isLandscape && !isTrailing && !showAnnouncementText) artInset else 0.dp,
                end = if (!isLandscape && isTrailing && !showAnnouncementText) artInset else 0.dp,
                top = if (isLandscape && !isTrailing) artInset else 0.dp,
                bottom = if (isLandscape && isTrailing) artInset else 0.dp,
            ),
            contentAlignment = alignment,
        ) {
            if (!isLandscape && showAnnouncementText) {
                CompactMusicAnnouncementTitle(
                    title = mediaInfo.title,
                    alpha = announcementAlpha,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                )
            } else {
                CompactMusicPlaceholderWithRing(
                    albumArtStyle = albumArtStyle,
                    nestedShape = nestedShape,
                    nestedRotation = nestedRotation,
                    artworkShape = artworkShape,
                    renderedSize = renderedSize,
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
            val albumArtSize = compactNestedArtworkSize(cutoutDiameterDp)
            val renderedArtSize = if (hasProgressRing) maxOf(albumArtSize, compactRingDiameter) else albumArtSize
            val innerArtSize = if (hasProgressRing) (renderedArtSize - 5.dp).coerceAtLeast(11.dp) else albumArtSize
            val artInset = compactEndInset(cutoutDiameterDp, renderedArtSize)
            if (isLandscape) {
                Box(
                    modifier = modifier.padding(
                        top = if (!isTrailing) artInset else 0.dp,
                        bottom = if (isTrailing) artInset else 0.dp,
                    ),
                    contentAlignment = if (isTrailing) Alignment.BottomCenter else Alignment.TopCenter,
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
                }
            } else if (isTrailing) {
                Row(
                    modifier = modifier.padding(end = artInset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
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
                }
            } else {
                Row(
                    modifier = modifier.padding(start = artInset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                contentAlignment = when {
                    isLandscape && isTrailing -> Alignment.BottomCenter
                    isLandscape && hasProgressRing -> Alignment.TopCenter
                    isLandscape -> Alignment.BottomCenter
                    else -> Alignment.Center
                },
            ) {
                val artContainerModifier = if (isLandscape) {
                    Modifier.fillMaxWidth().aspectRatio(1f, matchHeightConstraintsFirst = false)
                } else {
                    Modifier
                        .align(
                            when {
                                isTrailing -> Alignment.CenterEnd
                                showAnnouncementText || hasProgressRing -> Alignment.CenterStart
                                else -> Alignment.CenterEnd
                            }
                        )
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
                        isTrailing = isTrailing,
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
                contentAlignment = when {
                    isLandscape && isTrailing -> Alignment.BottomCenter
                    isLandscape -> Alignment.TopCenter
                    else -> Alignment.Center
                },
            ) {
                CompactMusicBlendedArtwork(
                    bitmap = albumArt,
                    isLandscape = isLandscape,
                    dotRadiusPx = dotRadiusPx,
                    artworkGlow = artworkGlow,
                    artworkAnchor = artworkAnchor,
                    isTrailing = isTrailing,
                )
                if (hasProgressRing) {
                    Box(
                        modifier = Modifier
                            .align(
                                when {
                                    isLandscape && isTrailing -> Alignment.BottomCenter
                                    isLandscape -> Alignment.TopCenter
                                    isTrailing -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                }
                            )
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
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
    )
}

@Composable
private fun CompactMusicSideInfoWing(
    sideContent: OverlayPreferences.MinimizedMusicSideContent,
    isLeading: Boolean,
    mediaInfo: MediaTrackInfo,
    isLandscape: Boolean,
    cutoutDiameterDp: Dp,
    accentColor: Color,
    showDominantGlow: Boolean,
    glowAlphaProvider: () -> Float,
    showAnnouncementText: Boolean,
    announcementAlpha: Float,
    livePositionMs: androidx.compose.runtime.State<Long>,
    waveformBandCount: Int,
    waveformBarWidth: Float,
    waveformBarSpacing: Float,
    visualizerAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
) {
    val info = if (showAnnouncementText && !isLandscape) {
        if (sideContent == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER) CompactMusicRightInfo.VISUALIZER
        else CompactMusicRightInfo.NONE
    } else {
        compactMusicRightInfoForSide(sideContent, !isLandscape && mediaInfo.title.isNotBlank())
    }
    val timestamp = timestampForMusicSide(sideContent)
    val slotContent = @Composable {
        CompactMusicRightInfoContent(
            info, mediaInfo, livePositionMs, timestamp, isLandscape, waveformBandCount,
            waveformBarWidth, waveformBarSpacing, visualizerAnchor,
        )
    }
    Box(
        modifier = modifier.compactDominantGlow(
            enabled = showDominantGlow,
            vertical = isLandscape,
            reverse = isLeading,
            accentColor = accentColor,
            alphaProvider = glowAlphaProvider,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showAnnouncementText && !isLeading) Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (showAnnouncementText && !isLeading) {
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
                slotContent()
            }
        } else {
            val edgeInset = if (showAnnouncementText) (cutoutDiameterDp / 3f).coerceAtLeast(8.dp) else 8.dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isLeading) edgeInset else 0.dp,
                        end = if (!isLeading) edgeInset else 0.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = when {
                    showAnnouncementText -> Arrangement.spacedBy(4.dp)
                    isLeading -> Arrangement.Start
                    else -> Arrangement.End
                },
            ) {
                if (isLeading) {
                    slotContent()
                    if (showAnnouncementText) {
                        CompactMusicAnnouncementTitle(
                            title = mediaInfo.title,
                            alpha = announcementAlpha,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    if (showAnnouncementText) {
                        MarqueeText(
                            text = mediaInfo.artist.ifBlank { "Media Player" },
                            color = accentColor.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = announcementAlpha },
                            textAlign = TextAlign.Start,
                            fadeWidth = 6.dp,
                            initialDelayMillis = 600,
                            repeatDelayMillis = 1000,
                            velocity = 35.dp,
                        )
                    }
                    slotContent()
                }
            }
        }
    }
}

internal enum class CompactMusicRightInfo { TITLE, TIMESTAMP, VISUALIZER, NONE }

internal fun secondaryMusicTrailingSide(
    leftSide: OverlayPreferences.MinimizedMusicSideContent,
    rightSide: OverlayPreferences.MinimizedMusicSideContent,
): OverlayPreferences.MinimizedMusicSideContent = when {
    rightSide != OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART -> rightSide
    leftSide != OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART -> leftSide
    else -> OverlayPreferences.MinimizedMusicSideContent.VISUALIZER
}

internal fun timestampForMusicSide(
    side: OverlayPreferences.MinimizedMusicSideContent,
): OverlayPreferences.MinimizedMusicTimestamp = when (side) {
    OverlayPreferences.MinimizedMusicSideContent.TIME_LEFT -> OverlayPreferences.MinimizedMusicTimestamp.REMAINING
    OverlayPreferences.MinimizedMusicSideContent.TIME_ELAPSED -> OverlayPreferences.MinimizedMusicTimestamp.ELAPSED
    else -> OverlayPreferences.MinimizedMusicTimestamp.NONE
}

internal fun compactMusicRightInfoForSide(
    side: OverlayPreferences.MinimizedMusicSideContent,
    hasTitle: Boolean,
): CompactMusicRightInfo = when (side) {
    OverlayPreferences.MinimizedMusicSideContent.TIME_LEFT,
    OverlayPreferences.MinimizedMusicSideContent.TIME_ELAPSED,
    -> CompactMusicRightInfo.TIMESTAMP
    OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE ->
        if (hasTitle) CompactMusicRightInfo.TITLE else CompactMusicRightInfo.NONE
    OverlayPreferences.MinimizedMusicSideContent.VISUALIZER -> CompactMusicRightInfo.VISUALIZER
    OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART -> CompactMusicRightInfo.NONE
}

internal fun compactMusicRightInfo(
    timestamp: OverlayPreferences.MinimizedMusicTimestamp,
    showTitle: Boolean,
    showVisualizer: Boolean,
): CompactMusicRightInfo = when {
    timestamp != OverlayPreferences.MinimizedMusicTimestamp.NONE -> CompactMusicRightInfo.TIMESTAMP
    showTitle -> CompactMusicRightInfo.TITLE
    showVisualizer -> CompactMusicRightInfo.VISUALIZER
    else -> CompactMusicRightInfo.NONE
}

@Composable
private fun CompactMusicRightInfoContent(
    info: CompactMusicRightInfo,
    mediaInfo: MediaTrackInfo,
    position: androidx.compose.runtime.State<Long>,
    timestamp: OverlayPreferences.MinimizedMusicTimestamp,
    isLandscape: Boolean,
    bandCount: Int,
    barWidth: Float,
    barSpacing: Float,
    visualizerAnchor: IslandElementAnchor?,
    maxHeightDp: Float = 13.5f,
) {
    val labelModifier = if (isLandscape) Modifier.verticalActivityLabel() else Modifier
    when (info) {
        CompactMusicRightInfo.TITLE -> MarqueeText(
            text = mediaInfo.title,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = labelModifier.fillMaxWidth(),
            fadeWidth = 6.dp,
        )
        CompactMusicRightInfo.TIMESTAMP -> MediaTimeLabel(
            positionState = position,
            format = { time ->
                if (timestamp == OverlayPreferences.MinimizedMusicTimestamp.REMAINING) {
                    formatRemainingTime(time, mediaInfo.durationMs)
                } else formatTime(time)
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = labelModifier,
        )
        CompactMusicRightInfo.VISUALIZER -> FittedEqualizerWaveform(
            mediaInfo, maxHeightDp, bandCount, barWidth, barSpacing, visualizerAnchor,
        )
        CompactMusicRightInfo.NONE -> Unit
    }
}

private fun Modifier.compactDominantGlow(
    enabled: Boolean,
    vertical: Boolean,
    reverse: Boolean = false,
    accentColor: Color,
    alphaProvider: () -> Float,
): Modifier = if (!enabled) this else drawWithCache {
    val stops = if (reverse) listOf(accentColor, Color.Transparent) else listOf(Color.Transparent, accentColor)
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
    renderedSize: Dp,
    hasProgressRing: Boolean,
    progressProvider: () -> Float,
    accentColor: Color,
) {
    Box(
        modifier = Modifier.size(renderedSize),
        contentAlignment = Alignment.Center,
    ) {
        CompactMusicArtworkPlaceholder(
            albumArtStyle = albumArtStyle,
            nestedShape = nestedShape,
            nestedRotation = nestedRotation,
            placeholderSize = if (hasProgressRing) renderedSize - 5.dp else renderedSize,
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
        textAlign = TextAlign.End,
        fadeWidth = 6.dp,
        initialDelayMillis = 600,
        repeatDelayMillis = 1000,
        velocity = 35.dp,
    )
}

