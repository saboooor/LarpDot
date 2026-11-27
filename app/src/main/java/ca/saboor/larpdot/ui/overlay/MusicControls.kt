package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons
import androidx.compose.animation.Crossfade
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion

/**
 * Resolves a Material 3 Expressive shape for nested album art presentation.
 */
data class RotatedShape(
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


internal fun loadMediaSessionActionIcon(
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
            createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = AndroidCanvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }
    }.getOrNull()
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
internal fun MediaTimeLabel(
    positionState: androidx.compose.runtime.State<Long>,
    format: (Long) -> String,
    style: androidx.compose.ui.text.TextStyle,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = format(positionState.value),
        style = style,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        softWrap = false,
        modifier = modifier.then(if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
        } else {
            Modifier
        }),
    )
}

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, min, sec)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", min, sec)
    }
}

internal fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    return "-${formatTime(remaining)}"
}

internal fun formatTotalDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    return formatTime(durationMs)
}

@Composable
internal fun PlaybackIcon(playing: Boolean, tint: Color, size: Dp) {
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
internal fun ExpandedMusicProgressBar(
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
    modifier: Modifier = Modifier,
    onProgressBarClick: (() -> Unit)? = null,
    highlight: Boolean = false,
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


internal fun playPauseIconTint(
    hasBackground: Boolean,
    useAccentColor: Boolean,
    accentColor: Color,
): Color = when {
    hasBackground && useAccentColor -> if (accentColor.luminance() > 0.42f) Color(0xFF1B1A1E) else Color.White
    hasBackground -> Color(0xFF1B1A1E)
    useAccentColor -> accentColor
    else -> Color.White
}

internal data class PlaybackButtonVisuals(
    val outerRadius: Dp,
    val prevShape: RoundedCornerShape,
    val playShape: RoundedCornerShape,
    val nextShape: RoundedCornerShape,
    val skipBgColor: Color,
    val playBgColor: Color,
    val playTint: Color,
)

internal fun resolvePlaybackButtonVisuals(
    style: OverlayPreferences.PlaybackControlsStyle,
    accentColor: Color,
): PlaybackButtonVisuals {
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
    val playTint = playPauseIconTint(
        hasBackground = style.playPauseBackground,
        useAccentColor = style.playPauseAccentColor,
        accentColor = accentColor,
    )
    return PlaybackButtonVisuals(
        outerRadius = outerRadius,
        prevShape = prevShape,
        playShape = playShape,
        nextShape = nextShape,
        skipBgColor = skipBgColor,
        playBgColor = playBgColor,
        playTint = playTint,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExpandedPlaybackControlsRow(
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
    modifier: Modifier = Modifier,
    onLongPressPrevious: (() -> Unit)? = null,
    onLongPressNext: (() -> Unit)? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
) {
    val visuals = resolvePlaybackButtonVisuals(style, accentColor)
    val isGroup = style.material3ButtonGroup

    Row(
        modifier = modifier
            .then(
                if (highlight) Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(visuals.outerRadius + 4.dp))
                    .padding(3.dp)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isGroup) 3.dp else defaultSpacing),
    ) {
        Surface(
            modifier = Modifier
                .size(width = skipWidth, height = skipHeight)
                .clip(visuals.prevShape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onPrevious,
                    onLongClick = onLongPressPrevious,
                ),
            shape = visuals.prevShape,
            color = visuals.skipBgColor,
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
            enabled = enabled,
            modifier = Modifier
                .size(width = playWidth, height = playHeight)
                .graphicsLayer {
                    val scale = bpmPulseScale()
                    scaleX = scale
                    scaleY = scale
                },
            shape = visuals.playShape,
            color = visuals.playBgColor,
            shadowElevation = if (style.playPauseBackground) 2.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlaybackIcon(
                    playing = isPlaying,
                    tint = visuals.playTint,
                    size = playIconSize,
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(width = skipWidth, height = skipHeight)
                .clip(visuals.nextShape)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onNext,
                    onLongClick = onLongPressNext,
                ),
            shape = visuals.nextShape,
            color = visuals.skipBgColor,
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

