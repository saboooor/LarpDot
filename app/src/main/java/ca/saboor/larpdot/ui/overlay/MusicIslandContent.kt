package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextAlign
import ca.saboor.larpdot.ui.components.MarqueeText
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.AudioOutputChip
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmChip
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.BpmVisualizer
import ca.saboor.larpdot.visualizer.rememberBpmPulse
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import ca.saboor.larpdot.visualizer.TrackVisualizer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ca.saboor.larpdot.ui.theme.LarpMotion

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
        size: androidx.compose.ui.geometry.Size,
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
                    RadialProgressRing(
                        progressProvider = musicProgressProvider,
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        strokeWidth = 2.dp,
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
                RadialProgressRing(
                    progressProvider = musicProgressProvider,
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.28f),
                    strokeWidth = 2.dp,
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
    onExpand: () -> Unit,
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
    fontStyle: OverlayPreferences.IslandFontStyle = OverlayPreferences.islandFontStyleFlow.collectAsState().value,
    minimizedNestedBlurredBg: Boolean = OverlayPreferences.minimizedNestedBlurredBgFlow.collectAsState().value,
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
    val fadeRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + cutoutDiameterDp).toPx() }
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

    if (isLandscape) {
        // Landscape Mode: Vertical Dynamic Island Pill
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(if (onBackgroundClick != null) Modifier.clickable(onClick = onBackgroundClick) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Wing: Album art based on chosen AlbumArtStyle
            if (mediaInfo.albumArt != null) {
                when (albumArtStyle) {
                    OverlayPreferences.AlbumArtStyle.NESTED -> {
                        // The camera spans the full thickness; padding belongs to the wing content.
                        val compactPillThickness = cutoutDiameterDp
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        val renderedArtSize = if (hasMusicProgressRing) maxOf(albumArtSize, compactRingDiameter) else albumArtSize
                        val innerArtSize = if (hasMusicProgressRing) (renderedArtSize - 5.dp).coerceAtLeast(11.dp) else albumArtSize
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier.size(renderedArtSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactMusicNestedArtwork(
                                    bitmap = mediaInfo.albumArt,
                                    size = innerArtSize,
                                    shape = artworkShape,
                                    artworkGlow = artworkGlow,
                                    artworkAnchor = artworkAnchor,
                                )
                                if (hasMusicProgressRing) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        shape = artworkShape,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = false),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactMusicBasicFadedArtwork(
                                    bitmap = mediaInfo.albumArt,
                                    isLandscape = true,
                                    artworkGlow = artworkGlow,
                                    artworkAnchor = artworkAnchor,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (hasMusicProgressRing) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                    )
                                }
                            }
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                    OverlayPreferences.AlbumArtStyle.BLENDED -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            CompactMusicBlendedArtwork(
                                bitmap = mediaInfo.albumArt,
                                isLandscape = true,
                                dotRadiusPx = dotRadiusPx,
                                artworkGlow = artworkGlow,
                                artworkAnchor = artworkAnchor,
                            )
                            if (hasMusicProgressRing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .size(cutoutDiameterDp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = if (
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
                    ) Alignment.Center else Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = if (hasMusicProgressRing) Modifier.size(compactRingDiameter) else Modifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactMusicArtworkPlaceholder(
                            albumArtStyle = albumArtStyle,
                            nestedShape = nestedShape,
                            nestedRotation = nestedRotation,
                            pillThickness = cutoutDiameterDp,
                            sizeOverride = if (hasMusicProgressRing) (compactRingDiameter - 5.dp).coerceAtLeast(11.dp) else null,
                        )
                        if (hasMusicProgressRing) {
                            RadialProgressRing(
                                progressProvider = musicProgressProvider,
                                color = accentColor,
                                trackColor = Color.White.copy(alpha = 0.28f),
                                strokeWidth = 2.dp,
                                shape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) artworkShape else RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
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
                            Modifier.drawWithCache {
                                val gradient = Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        accentColor,
                                    )
                                )
                                onDrawBehind {
                                    val alpha = if (bpmPulseEnabled && mediaInfo.isPlaying) {
                                        0.16f + (0.22f * bpmPulseState.value * pulseAmount)
                                    } else {
                                        glowAlpha
                                    }
                                    drawRect(brush = gradient, alpha = alpha)
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
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
            }
        }
    } else {
        // Portrait Mode: Horizontal Dynamic Island Pill
        val leadingWingFraction = 1f - trailingWingFraction
        val albumArtWingInteraction = Modifier
            .then(if (onAlbumArtClick != null) Modifier.clickable(onClick = onAlbumArtClick) else Modifier)
            .then(
                if (highlightAlbumArt) Modifier.border(1.5.dp, materialYouPrimary, RoundedCornerShape(topStart = cutoutDiameterDp / 2f, bottomStart = cutoutDiameterDp / 2f))
                else Modifier
            )
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(if (onBackgroundClick != null) Modifier.clickable(onClick = onBackgroundClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Wing: Album art based on chosen AlbumArtStyle, with announcement title inserted in front
            if (mediaInfo.albumArt != null) {
                when (albumArtStyle) {
                    OverlayPreferences.AlbumArtStyle.NESTED -> {
                        // The camera spans the full thickness; padding belongs to the wing content.
                        val compactPillThickness = cutoutDiameterDp
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        val renderedArtSize = if (hasMusicProgressRing) maxOf(albumArtSize, compactRingDiameter) else albumArtSize
                        val innerArtSize = if (hasMusicProgressRing) (renderedArtSize - 5.dp).coerceAtLeast(11.dp) else albumArtSize
                        val artStartInset = ((compactPillThickness - renderedArtSize) / 2f).coerceAtLeast(3.dp)
                        Row(
                            modifier = Modifier
                                .weight(leadingWingFraction)
                                .fillMaxHeight()
                                .then(albumArtWingInteraction)
                                .padding(
                                    start = if (showAnnouncementText) artStartInset else 0.dp,
                                    end = if (showAnnouncementText) 2.dp else 0.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (showAnnouncementText) Arrangement.spacedBy(4.dp) else Arrangement.End,
                        ) {
                            Box(
                                modifier = Modifier.size(renderedArtSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactMusicNestedArtwork(
                                    bitmap = mediaInfo.albumArt,
                                    size = innerArtSize,
                                    shape = artworkShape,
                                    artworkGlow = artworkGlow,
                                    artworkAnchor = artworkAnchor,
                                )
                                if (hasMusicProgressRing) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        shape = artworkShape,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            if (showAnnouncementText) {
                                MarqueeText(
                                    text = mediaInfo.title.ifBlank { "Playing Track" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                                        fontSize = 11.5.sp,
                                    ),
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
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                        Box(
                            modifier = Modifier
                                .weight(leadingWingFraction)
                                .fillMaxHeight()
                                .then(albumArtWingInteraction),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(if (showAnnouncementText) Alignment.CenterStart else Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactMusicBasicFadedArtwork(
                                    bitmap = mediaInfo.albumArt,
                                    isLandscape = false,
                                    artworkGlow = artworkGlow,
                                    artworkAnchor = artworkAnchor,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (hasMusicProgressRing) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                    )
                                }
                            }
                            if (showAnnouncementText) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = announcementAlpha }
                                        .background(Color.Black.copy(alpha = 0.34f)),
                                )
                                MarqueeText(
                                    text = mediaInfo.title.ifBlank { "Playing Track" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                                        fontSize = 11.5.sp,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 6.dp, end = 2.dp)
                                        .graphicsLayer { alpha = announcementAlpha },
                                    textAlign = TextAlign.Center,
                                    fadeWidth = 6.dp,
                                    initialDelayMillis = 600,
                                    repeatDelayMillis = 1000,
                                    velocity = 35.dp,
                                )
                            }
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                    OverlayPreferences.AlbumArtStyle.BLENDED -> {
                        Box(
                            modifier = Modifier
                                .weight(leadingWingFraction)
                                .fillMaxHeight()
                                .then(albumArtWingInteraction),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompactMusicBlendedArtwork(
                                bitmap = mediaInfo.albumArt,
                                isLandscape = false,
                                dotRadiusPx = dotRadiusPx,
                                artworkGlow = artworkGlow,
                                artworkAnchor = artworkAnchor,
                            )
                            if (hasMusicProgressRing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .size(cutoutDiameterDp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RadialProgressRing(
                                        progressProvider = musicProgressProvider,
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.28f),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                    )
                                }
                            }
                            if (showAnnouncementText) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = announcementAlpha }
                                        .background(Color.Black.copy(alpha = 0.34f)),
                                )
                                MarqueeText(
                                    text = mediaInfo.title.ifBlank { "Playing Track" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                                        fontSize = 11.5.sp,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 6.dp, end = 2.dp)
                                        .graphicsLayer { alpha = announcementAlpha },
                                    textAlign = TextAlign.Center,
                                    fadeWidth = 6.dp,
                                    initialDelayMillis = 600,
                                    repeatDelayMillis = 1000,
                                    velocity = 35.dp,
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(leadingWingFraction)
                        .fillMaxHeight()
                        .then(albumArtWingInteraction),
                    contentAlignment = if (
                        showAnnouncementText ||
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
                    ) Alignment.Center else Alignment.CenterEnd,
                ) {
                    if (showAnnouncementText) {
                        MarqueeText(
                            text = mediaInfo.title.ifBlank { "Playing Track" },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                                fontSize = 11.5.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 2.dp)
                                .graphicsLayer { alpha = announcementAlpha },
                            textAlign = TextAlign.Center,
                            fadeWidth = 6.dp,
                            initialDelayMillis = 600,
                            repeatDelayMillis = 1000,
                            velocity = 35.dp,
                        )
                    } else {
                        Box(
                            modifier = if (hasMusicProgressRing) Modifier.size(compactRingDiameter) else Modifier,
                            contentAlignment = Alignment.Center,
                        ) {
                            CompactMusicArtworkPlaceholder(
                                albumArtStyle = albumArtStyle,
                                nestedShape = nestedShape,
                                nestedRotation = nestedRotation,
                                pillThickness = cutoutDiameterDp,
                                sizeOverride = if (hasMusicProgressRing) (compactRingDiameter - 5.dp).coerceAtLeast(11.dp) else null,
                            )
                            if (hasMusicProgressRing) {
                                RadialProgressRing(
                                    progressProvider = musicProgressProvider,
                                    color = accentColor,
                                    trackColor = Color.White.copy(alpha = 0.28f),
                                    strokeWidth = 2.dp,
                                    shape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) artworkShape else RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.width(cutoutDiameterDp))

            // Right Wing: Equalizer with ambient glow extending from the dot to the right edge.
            // Compact styles (NESTED, BASIC_FADED, no-art) have a narrow right wing (~30dp) with a
            // ~14dp rounded corner on the right — shift the waveform left by cornerRadius/2 so it
            // sits optically centered in the visible straight area rather than close to the edge.
            // The camera spans the full thickness; padding belongs to the wing content.
            val compactPillThickness = cutoutDiameterDp
            val equalizerShift = if (
                albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
            ) 0.dp else -(compactPillThickness / if (waveformBandCount >= 6) 16f else 8f)
            val compactVisualizerVisible = showVisualizer && !(!showAnnouncementText && showMinimizedTitle && mediaInfo.title.isNotBlank())
            Box(
                modifier = Modifier
                    .weight(trailingWingFraction)
                    .fillMaxHeight()
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
                    .then(
                        if (showDominantGlow) {
                            Modifier.drawWithCache {
                                val gradient = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        accentColor,
                                    )
                                )
                                onDrawBehind {
                                    val alpha = if (bpmPulseEnabled && mediaInfo.isPlaying) {
                                        0.16f + (0.22f * bpmPulseState.value * pulseAmount)
                                    } else {
                                        glowAlpha
                                    }
                                    drawRect(brush = gradient, alpha = alpha)
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val visualizerEndInset = (compactPillThickness / 3f).coerceAtLeast(8.dp)
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
}

private fun loadMediaSessionActionIcon(
    context: Context,
    packageName: String?,
    resourceId: Int,
): Bitmap? {
    if (packageName == null || resourceId == 0) return null
    return runCatching {
        val packageContext = context.createPackageContext(packageName, 0)
        val drawable = packageContext.resources.getDrawable(resourceId, packageContext.theme)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(128) ?: 64
            val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(128) ?: 64
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = AndroidCanvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }
    }.getOrNull()
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
                        val playIconTint = if (hasPlayBg) {
                            Color(0xFF1B1A1E)
                        } else if (usePlayAccent) {
                            accentColor
                        } else Color.White
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

internal fun openPlayerApp(context: Context, track: MediaTrackInfo) {
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

@Composable
private fun MediaTimeLabel(
    positionState: androidx.compose.runtime.State<Long>,
    format: (Long) -> String,
    style: androidx.compose.ui.text.TextStyle,
) {
    Text(
        text = format(positionState.value),
        style = style,
        color = Color.White.copy(alpha = 0.55f),
    )
}

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

internal fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val remaining = (durationMs - positionMs).coerceAtLeast(0)
    val totalSec = remaining / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("-%d:%02d", min, sec)
}

@Composable
private fun PlaybackIcon(playing: Boolean, tint: Color, size: Dp) {
    Crossfade(playing, animationSpec = LarpMotion.fade(145), label = "play_pause") { isPlaying ->
        Icon(
            imageVector = if (isPlaying) LarpIcons.Pause else LarpIcons.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedMusicProgressBar(
    durationMs: Long,
    isPlaying: Boolean,
    activeFractionProvider: () -> Float,
    accentColor: Color,
    useAccentColor: Boolean,
    showSquiggles: Boolean,
    onDragStart: (Float) -> Unit,
    onDragChange: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onProgressBarClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (useAccentColor) accentColor else Color.White
    val trackColor = if (useAccentColor) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .height(28.dp)
            .then(
                if (highlight) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            )
            .then(
                if (onProgressBarClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onProgressBarClick)
                } else {
                    Modifier
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                if (durationMs > 0) {
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    MediaPlaybackState.seekTo((fraction * durationMs).toLong())
                                }
                            }
                        }
                        .pointerInput(durationMs) {
                            var latestFraction = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    latestFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    onDragStart(latestFraction)
                                },
                                onDragEnd = { onDragEnd(latestFraction) },
                                onDragCancel = onDragCancel,
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    latestFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    onDragChange(latestFraction)
                                },
                            )
                        }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = activeFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = activeColor,
            trackColor = trackColor,
            amplitude = { if (showSquiggles) if (isPlaying) 0.5f else 0.12f else 0f },
            wavelength = 24.dp,
        )
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
private fun ExpandedPlaybackControlsRow(
    isPlaying: Boolean,
    accentColor: Color,
    style: OverlayPreferences.PlaybackControlsStyle,
    skipWidth: Dp,
    skipHeight: Dp,
    skipIconSize: Dp,
    playWidth: Dp,
    playHeight: Dp,
    playIconSize: Dp,
    defaultSpacing: Dp,
    bpmPulseScale: () -> Float,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val outerRadius = style.cornerRadiusDp.dp
    val innerRadius = (style.cornerRadiusDp * 0.35f).coerceIn(3f, 8f).dp
    val isGroup = style.material3ButtonGroup
    val prevShape = if (isGroup) {
        RoundedCornerShape(topStart = outerRadius, bottomStart = outerRadius, topEnd = innerRadius, bottomEnd = innerRadius)
    } else RoundedCornerShape(outerRadius)
    val playShape = RoundedCornerShape(if (isGroup) innerRadius else outerRadius)
    val nextShape = if (isGroup) {
        RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = outerRadius, bottomEnd = outerRadius)
    } else RoundedCornerShape(outerRadius)
    val skipBgColor = if (style.skipBackground) Color.White.copy(alpha = 0.14f) else Color.Transparent
    val playBgColor = if (style.playPauseBackground) {
        if (style.playPauseAccentColor) accentColor else Color.White.copy(alpha = 0.94f)
    } else Color.Transparent
    val playTint = if (style.playPauseBackground) {
        Color(0xFF1B1A1E)
    } else if (style.playPauseAccentColor) {
        accentColor
    } else Color.White

    Row(
        modifier = modifier
            .then(
                if (highlight) Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(outerRadius + 4.dp))
                    .padding(3.dp)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isGroup) 3.dp else defaultSpacing),
    ) {
        Surface(
            onClick = onPrevious,
            modifier = Modifier.size(width = skipWidth, height = skipHeight),
            shape = prevShape,
            color = skipBgColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = LarpIcons.SkipPrevious,
                    contentDescription = "Previous track",
                    tint = Color.White,
                    modifier = Modifier.size(skipIconSize),
                )
            }
        }
        Surface(
            onClick = onPlayPause,
            modifier = Modifier
                .size(width = playWidth, height = playHeight)
                .graphicsLayer {
                    val scale = bpmPulseScale()
                    scaleX = scale
                    scaleY = scale
                },
            shape = playShape,
            color = playBgColor,
            shadowElevation = if (style.playPauseBackground) 2.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlaybackIcon(
                    playing = isPlaying,
                    tint = playTint,
                    size = playIconSize,
                )
            }
        }
        Surface(
            onClick = onNext,
            modifier = Modifier.size(width = skipWidth, height = skipHeight),
            shape = nextShape,
            color = skipBgColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = LarpIcons.SkipNext,
                    contentDescription = "Next track",
                    tint = Color.White,
                    modifier = Modifier.size(skipIconSize),
                )
            }
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
