package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.Crossfade
import androidx.compose.ui.text.style.TextAlign
import ca.saboor.larpdot.ui.components.MarqueeText
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
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
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.BpmVisualizer
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import ca.saboor.larpdot.visualizer.TrackVisualizer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarqueeText(
            text = title.ifBlank { "Playing Track" },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = titleFontSize.sp,
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = artistFontSize.sp),
            textAlign = TextAlign.Center,
            fadeWidth = fadeWidth,
            initialDelayMillis = 600,
            repeatDelayMillis = 1000,
            velocity = 35.dp,
        )
    }
}

@Composable
private fun AnnouncementAlbumArt(
    mediaInfo: MediaTrackInfo,
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    nestedShape: OverlayPreferences.NestedAlbumArtShape,
    nestedRotation: Float,
    cutoutDiameterDp: Dp,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val albumArt = mediaInfo.albumArt ?: return
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { (cutoutDiameterDp / 2f).toPx() }
    when (albumArtStyle) {
        OverlayPreferences.AlbumArtStyle.NESTED -> Image(
            bitmap = albumArt.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size((cutoutDiameterDp - 8.dp).coerceIn(18.dp, 26.dp))
                .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
            contentScale = ContentScale.Crop,
        )

        OverlayPreferences.AlbumArtStyle.BASIC_FADED -> Image(
            bitmap = albumArt.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .then(
                    if (isLandscape) Modifier.fillMaxWidth().aspectRatio(1f)
                    else Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = if (isLandscape) {
                            Brush.verticalGradient(listOf(Color.White, Color.Transparent))
                        } else {
                            Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
                        },
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentScale = ContentScale.Crop,
        )

        OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
        OverlayPreferences.AlbumArtStyle.BLENDED -> {
            val bitmap = albumArt.asImageBitmap()
            val side = minOf(bitmap.width, bitmap.height)
            val cropX = (bitmap.width - side) / 2
            val cropY = (bitmap.height - side) / 2
            // Use a wider source slice to reduce distortion in the mirrored extension.
            val reflectionSlice = side / 4
            Canvas(
                modifier = modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val extraPx = 6.dp.roundToPx()
                        val placeable = if (isLandscape) {
                            measurable.measure(
                                constraints.copy(
                                    minHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                    maxHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                )
                            )
                        } else {
                            measurable.measure(
                                constraints.copy(
                                    minWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                    maxWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                )
                            )
                        }
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(0, 0)
                        }
                    }
                    .progressiveBlur(
                        direction = if (isLandscape) 1 else 0,
                        startFraction = 0.65f,
                        maxBlurDp = 24.dp,
                        dotRadiusPx = dotRadiusPx,
                    ),
            ) {
                if (isLandscape) {
                    val artHeight = size.width
                    val reflectionHeight = (size.height - artHeight).coerceAtLeast(0f)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(cropX, cropY),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), artHeight.roundToInt()),
                    )
                    scale(
                        scaleX = 1f,
                        scaleY = -1f,
                        pivot = Offset(size.width / 2f, artHeight + reflectionHeight / 2f),
                    ) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX, cropY + side - reflectionSlice),
                            srcSize = IntSize(side, reflectionSlice),
                            dstOffset = IntOffset(0, artHeight.roundToInt()),
                            dstSize = IntSize(size.width.roundToInt(), reflectionHeight.roundToInt()),
                        )
                    }
                } else {
                    val artWidth = size.height
                    val reflectionWidth = (size.width - artWidth).coerceAtLeast(0f)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(cropX, cropY),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(artWidth.roundToInt(), size.height.roundToInt()),
                    )
                    scale(
                        scaleX = -1f,
                        scaleY = 1f,
                        pivot = Offset(artWidth + reflectionWidth / 2f, size.height / 2f),
                    ) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(cropX + side - reflectionSlice, cropY),
                            srcSize = IntSize(reflectionSlice, side),
                            dstOffset = IntOffset(artWidth.roundToInt(), 0),
                            dstSize = IntSize(reflectionWidth.roundToInt(), size.height.roundToInt()),
                        )
                    }
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
    isSongAnnouncement: Boolean = false,
    trailingWingFraction: Float = 0.5f,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val materialYouPrimary = MaterialTheme.colorScheme.primary
    val accentColor = if (mediaInfo.albumArt == null) materialYouPrimary else mediaInfo.dominantColor
    val dotRadiusPx = with(density) { (cutoutDiameterDp / 2f).toPx() }
    val fadeRadiusPx = with(density) { ((cutoutDiameterDp / 2f) + cutoutDiameterDp).toPx() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 0.28f else 0.08f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "compact_glow_alpha",
    )

    Crossfade(
        targetState = isSongAnnouncement,
        animationSpec = tween(durationMillis = 280),
        label = "song_announcement_crossfade",
    ) { announcing ->
        if (announcing) {
            if (isLandscape) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnnouncementAlbumArt(
                            mediaInfo = mediaInfo,
                            albumArtStyle = albumArtStyle,
                            nestedShape = nestedShape,
                            nestedRotation = nestedRotation,
                            cutoutDiameterDp = cutoutDiameterDp,
                            isLandscape = true,
                        )
                    }

                    Spacer(modifier = Modifier.height(cutoutDiameterDp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
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
            } else {
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f - trailingWingFraction)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 3.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                AnnouncementAlbumArt(
                                    mediaInfo = mediaInfo,
                                    albumArtStyle = albumArtStyle,
                                    nestedShape = nestedShape,
                                    nestedRotation = nestedRotation,
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    isLandscape = false,
                                )
                                MarqueeText(
                                    text = mediaInfo.title.ifBlank { "Playing Track" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fadeWidth = 6.dp,
                                    initialDelayMillis = 600,
                                    repeatDelayMillis = 1000,
                                    velocity = 35.dp,
                                )
                            }
                        } else {
                            AnnouncementAlbumArt(
                                mediaInfo = mediaInfo,
                                albumArtStyle = albumArtStyle,
                                nestedShape = nestedShape,
                                nestedRotation = nestedRotation,
                                cutoutDiameterDp = cutoutDiameterDp,
                                isLandscape = false,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.34f)),
                            )
                            MarqueeText(
                                text = mediaInfo.title.ifBlank { "Playing Track" },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, end = 2.dp),
                                textAlign = TextAlign.Center,
                                fadeWidth = 6.dp,
                                initialDelayMillis = 600,
                                repeatDelayMillis = 1000,
                                velocity = 35.dp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(cutoutDiameterDp))

                    Box(
                        modifier = Modifier
                            .weight(trailingWingFraction)
                            .fillMaxHeight()
                            .then(
                                if (showDominantGlow) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                accentColor.copy(alpha = glowAlpha),
                                            )
                                        )
                                    )
                                } else Modifier
                            )
                            .padding(start = 2.dp, end = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MarqueeText(
                            text = mediaInfo.artist.ifBlank { "Media Player" },
                            color = accentColor.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            textAlign = TextAlign.Center,
                            fadeWidth = 6.dp,
                            initialDelayMillis = 600,
                            repeatDelayMillis = 1000,
                            velocity = 35.dp,
                        )
                    }
                }
            }
        } else {
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
                        val outlineAllowanceDp = 2.dp
                        val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(albumArtSize)
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
                            contentAlignment = Alignment.BottomCenter,
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
                                    .layout { measurable, constraints ->
                                        val extraPx = 6.dp.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                                maxHeight = (constraints.maxHeight + extraPx).coerceAtLeast(0),
                                            )
                                        )
                                        layout(placeable.width.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
                                            placeable.place(0, 0)
                                        }
                                    }
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
                val placeholderSize = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    (cutoutDiameterDp - 8.dp).coerceIn(16.dp, 24.dp)
                } else {
                    cutoutDiameterDp.coerceIn(20.dp, 32.dp)
                }
                val placeholderShape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    nestedAlbumArtShape(nestedShape, nestedRotation)
                } else {
                    RoundedCornerShape(6.dp)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = if (
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
                    ) Alignment.Center else Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .size(placeholderSize)
                            .clip(placeholderShape)
                            .background(materialYouPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Music artwork placeholder",
                            tint = materialYouPrimary,
                            modifier = Modifier.size(placeholderSize * 0.58f),
                        )
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
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = glowAlpha),
                                    )
                                )
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showVisualizer) {
                    EqualizerWaveform(
                        isPlaying = mediaInfo.isPlaying,
                        maxHeightDp = 13.5f,
                        accentColor = accentColor,
                        barCount = waveformBandCount,
                        barWidth = waveformBarWidth.dp,
                        barSpacing = waveformBarSpacing.dp,
                        currentPositionMs = mediaInfo.positionMs,
                    )
                }
            }
        }
    } else {
        // Portrait Mode: Horizontal Dynamic Island Pill
        val leadingWingFraction = 1f - trailingWingFraction
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
                        val outlineAllowanceDp = 2.dp
                        val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
                        val albumArtSize = (compactPillThickness - 12.dp).coerceIn(16.dp, 24.dp)
                        Box(
                            modifier = Modifier
                                .weight(leadingWingFraction)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Image(
                                bitmap = mediaInfo.albumArt.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(albumArtSize)
                                    .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> {
                        Box(
                            modifier = Modifier
                                .weight(leadingWingFraction)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd,
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
                                .weight(leadingWingFraction)
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
                                    .layout { measurable, constraints ->
                                        val extraPx = 6.dp.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                minWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                                maxWidth = (constraints.maxWidth + extraPx).coerceAtLeast(0),
                                            )
                                        )
                                        layout(constraints.maxWidth.coerceAtLeast(0), placeable.height.coerceAtLeast(0)) {
                                            placeable.place(0, 0)
                                        }
                                    }
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
                val placeholderSize = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    (cutoutDiameterDp - 8.dp).coerceIn(16.dp, 24.dp)
                } else {
                    cutoutDiameterDp.coerceIn(20.dp, 32.dp)
                }
                val placeholderShape = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
                    nestedAlbumArtShape(nestedShape, nestedRotation)
                } else {
                    RoundedCornerShape(6.dp)
                }
                Box(
                    modifier = Modifier.weight(leadingWingFraction).fillMaxHeight(),
                    contentAlignment = if (
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                        albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
                    ) Alignment.Center else Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(placeholderSize)
                            .clip(placeholderShape)
                            .background(materialYouPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Music artwork placeholder",
                            tint = materialYouPrimary,
                            modifier = Modifier.size(placeholderSize * 0.58f),
                        )
                    }
                }
            }
            // Center: Symmetrical clearance spacer hugging the hole punch camera
            Spacer(modifier = Modifier.width(cutoutDiameterDp))

            // Right Wing: Equalizer with ambient glow extending from the dot to the right edge.
            // Compact styles (NESTED, BASIC_FADED, no-art) have a narrow right wing (~30dp) with a
            // ~14dp rounded corner on the right — shift the waveform left by cornerRadius/2 so it
            // sits optically centered in the visible straight area rather than close to the edge.
            val outlineAllowanceDp = 2.dp
            val compactPillThickness = cutoutDiameterDp + (outlineAllowanceDp * 2)
            val equalizerShift = if (
                albumArtStyle == OverlayPreferences.AlbumArtStyle.BLENDED ||
                albumArtStyle == OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND
            ) 0.dp else -(compactPillThickness / if (waveformBandCount >= 6) 16f else 8f)
            Box(
                modifier = Modifier
                    .weight(trailingWingFraction)
                    .fillMaxHeight()
                    .then(
                        if (showDominantGlow) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = glowAlpha),
                                    )
                                )
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showMinimizedTitle && mediaInfo.title.isNotBlank()) {
                    MarqueeText(
                        text = mediaInfo.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        fadeWidth = 6.dp,
                    )
                } else if (showVisualizer) {
                    EqualizerWaveform(
                        isPlaying = mediaInfo.isPlaying,
                        maxHeightDp = 13.5f,
                        accentColor = accentColor,
                        barCount = waveformBandCount,
                        barWidth = waveformBarWidth.dp,
                        barSpacing = waveformBarSpacing.dp,
                        modifier = Modifier.offset(x = equalizerShift),
                        currentPositionMs = mediaInfo.positionMs,
                    )
                }
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
    albumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState().value,
    showAlbumArt: Boolean = OverlayPreferences.showExpandedAlbumArtFlow.collectAsState().value,
    playerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.expandedPlayerLayoutFlow.collectAsState().value,
    showProgressSquiggles: Boolean = OverlayPreferences.showProgressSquigglesFlow.collectAsState().value,
    elementVisibility: OverlayPreferences.ExpandedElementVisibility = OverlayPreferences.expandedElementVisibilityFlow.collectAsState().value,
    nestedShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState().value,
    nestedRotation: Float = OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState().value,
    showDominantGlow: Boolean = OverlayPreferences.showExpandedDominantColorGlowFlow.collectAsState().value,
    cameraCoverStyle: OverlayPreferences.CameraCoverStyle = OverlayPreferences.cameraCoverStyleFlow.collectAsState().value,
    waveformBandCount: Int = OverlayPreferences.waveformBandCountFlow.collectAsState().value,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val accentColor = if (mediaInfo.albumArt == null) MaterialTheme.colorScheme.primary else mediaInfo.dominantColor
    val albumArtPlaybackScale by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 1f else 0.86f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "album_art_playback_scale",
    )
    val albumArtPlaybackAlpha by animateFloatAsState(
        targetValue = if (mediaInfo.isPlaying) 1f else 0.55f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "album_art_playback_alpha",
    )

    val progressFraction = if (mediaInfo.durationMs > 0) {
        (mediaInfo.positionMs.toFloat() / mediaInfo.durationMs).coerceIn(0f, 1f)
    } else 0f

    // Interactive scrub state
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else progressFraction

    val cardDragOffsetAnim = androidx.compose.animation.core.Animatable(0f)
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
                if (albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND ||
                    albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND
                ) {
                    onDrawBehind { /* Full background image rendered inside content */ }
                } else {
                    val dominantTint = if (showDominantGlow) {
                        accentColor.copy(alpha = 0.25f)
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
        } else if (albumArtStyle == OverlayPreferences.ExpandedBackgroundStyle.BLENDED && mediaInfo.albumArt != null) {
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
        CameraCover(
            style = cameraCoverStyle,
            centerX = dotCenterXDp,
            centerY = dotCenterYDp,
            diameter = cutoutDiameterDp,
        )

        if (playerLayout == OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS) Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Row 1: Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Spacer(Modifier.height(32.dp))
            }

            // Row 2: Track Title & Artist (Left) + Visualizer stacked above Play/Pause Button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showAlbumArt && mediaInfo.albumArt != null) {
                    Image(
                        bitmap = mediaInfo.albumArt.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = albumArtPlaybackScale
                                scaleY = albumArtPlaybackScale
                                alpha = albumArtPlaybackAlpha
                            }
                            .clip(nestedAlbumArtShape(nestedShape, nestedRotation)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                }

                if (elementVisibility.trackInfo) Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                        .clickable { openPlayerApp(context, mediaInfo) },
                ) {
                    MarqueeText(
                        text = mediaInfo.title.ifEmpty { "No Media Playing" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    MarqueeText(
                        text = mediaInfo.artist.ifEmpty { "" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = Color.White.copy(alpha = 0.80f),
                    )
                }

                // Controls Column: 5-bar visualizer in a fixed container stacked directly above the Play/Pause button
                if (elementVisibility.visualizer || elementVisibility.mainControls) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerWaveform(
                            isPlaying = mediaInfo.isPlaying,
                            maxHeightDp = 20f,
                            accentColor = accentColor,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 3.2.dp else 4.dp,
                            barSpacing = if (waveformBandCount > 5) 2.4.dp else 3.dp,
                            minHeight = 4.dp,
                            barCornerRadius = 2.dp,
                            currentPositionMs = mediaInfo.positionMs,
                        )
                    }
                    if (elementVisibility.visualizer && elementVisibility.mainControls) Spacer(Modifier.height(24.dp))
                    // Native Android 13/14 M3 wide pill Play/Pause button
                    if (elementVisibility.mainControls) Surface(
                        onClick = { MediaPlaybackState.togglePlayPause() },
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
            if (elementVisibility.progress || elementVisibility.mainControls || elementVisibility.appActions) Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (elementVisibility.mainControls) IconButton(
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

                if (elementVisibility.progress) Box(
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
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.32f),
                        amplitude = { if (showProgressSquiggles) if (mediaInfo.isPlaying) 0.5f else 0.15f else 0f },
                        wavelength = 28.dp,
                    )
                }

                if (elementVisibility.mainControls) IconButton(
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
                    .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                if (showAlbumArt && mediaInfo.albumArt != null) {
                    Image(
                        bitmap = mediaInfo.albumArt.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer {
                                scaleX = albumArtPlaybackScale
                                scaleY = albumArtPlaybackScale
                                alpha = albumArtPlaybackAlpha
                            }
                            .clip(nestedAlbumArtShape(nestedShape, nestedRotation))
                            .clickable { openPlayerApp(context, mediaInfo) },
                        contentScale = ContentScale.Crop,
                    )
                }

                if (elementVisibility.trackInfo || elementVisibility.visualizer) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openPlayerApp(context, mediaInfo) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (elementVisibility.trackInfo) Column(modifier = Modifier.weight(1f)) {
                        MarqueeText(
                            text = mediaInfo.title.ifEmpty { "No Media Playing" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(Modifier.height(2.dp))
                        MarqueeText(
                            text = mediaInfo.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Start,
                        )
                    }
                    if (elementVisibility.trackInfo && elementVisibility.visualizer) Spacer(Modifier.width(14.dp))
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 20.dp)
                            .padding(end = 14.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        EqualizerWaveform(
                            isPlaying = mediaInfo.isPlaying,
                            maxHeightDp = 20f,
                            accentColor = accentColor,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 2.8.dp else 3.5.dp,
                            barSpacing = if (waveformBandCount > 5) 2.dp else 2.5.dp,
                            minHeight = 3.dp,
                            currentPositionMs = mediaInfo.positionMs,
                        )
                    }
                }

                if (elementVisibility.progress) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
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
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LinearWavyProgressIndicator(
                        progress = { activeFraction },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        amplitude = { if (showProgressSquiggles) if (mediaInfo.isPlaying) 0.55f else 0.12f else 0f },
                        wavelength = 24.dp,
                    )
                }

                if (elementVisibility.mainControls) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Surface(
                        onClick = { MediaPlaybackState.skipPrevious() },
                        modifier = Modifier.size(width = 60.dp, height = 50.dp),
                        shape = RoundedCornerShape(
                            topStart = 25.dp,
                            bottomStart = 25.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp,
                        ),
                        color = Color.White.copy(alpha = 0.14f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                "Previous track",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = { MediaPlaybackState.togglePlayPause() },
                        modifier = Modifier.size(width = 68.dp, height = 56.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor,
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(31.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = { MediaPlaybackState.skipNext() },
                        modifier = Modifier.size(width = 60.dp, height = 50.dp),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 25.dp,
                            bottomEnd = 25.dp,
                        ),
                        color = Color.White.copy(alpha = 0.14f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SkipNext,
                                "Next track",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

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
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (showAlbumArt && mediaInfo.albumArt != null) {
                        Image(
                            bitmap = mediaInfo.albumArt.asImageBitmap(),
                            contentDescription = "Album art",
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    scaleX = albumArtPlaybackScale
                                    scaleY = albumArtPlaybackScale
                                    alpha = albumArtPlaybackAlpha
                                }
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (elementVisibility.trackInfo) Column(
                        modifier = Modifier.weight(1f).clickable { openPlayerApp(context, mediaInfo) },
                    ) {
                        MarqueeText(
                            text = mediaInfo.title.ifEmpty { "No Media Playing" },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(3.dp))
                        MarqueeText(
                            text = mediaInfo.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                    if (elementVisibility.trackInfo && elementVisibility.visualizer) Spacer(Modifier.width(12.dp))
                    if (elementVisibility.visualizer) Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 20.dp)
                            .padding(end = 14.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        EqualizerWaveform(
                            isPlaying = mediaInfo.isPlaying,
                            maxHeightDp = 20f,
                            accentColor = accentColor,
                            barCount = waveformBandCount,
                            barWidth = if (waveformBandCount > 5) 2.8.dp else 3.5.dp,
                            barSpacing = if (waveformBandCount > 5) 2.dp else 2.5.dp,
                            minHeight = 3.dp,
                            currentPositionMs = mediaInfo.positionMs,
                        )
                    }
                }

                if (elementVisibility.progress) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatTime(mediaInfo.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .pointerInput(mediaInfo.durationMs) {
                                detectTapGestures { offset ->
                                    if (mediaInfo.durationMs > 0) {
                                        MediaPlaybackState.seekTo(
                                            ((offset.x / size.width).coerceIn(0f, 1f) * mediaInfo.durationMs).toLong()
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { activeFraction },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            amplitude = {
                                if (showProgressSquiggles) {
                                    if (mediaInfo.isPlaying) 0.45f else 0.1f
                                } else {
                                    0f
                                }
                            },
                            wavelength = 24.dp,
                        )
                    }
                    Text(
                        text = formatRemainingTime(mediaInfo.positionMs, mediaInfo.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }

                if (elementVisibility.mainControls || elementVisibility.appActions) Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (elementVisibility.mainControls) Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = { MediaPlaybackState.skipPrevious() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Previous track", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        IconButton(
                            onClick = { MediaPlaybackState.togglePlayPause() },
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                imageVector = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        IconButton(onClick = { MediaPlaybackState.skipNext() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.SkipNext, "Next track", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                    }

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
