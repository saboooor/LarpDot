package ca.saboor.larpdot.ui.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal const val BLUR_CORE_SIZE = 32
internal const val BLUR_PADDING = 14
internal const val BLUR_BUFFER_SIZE = BLUR_CORE_SIZE + (BLUR_PADDING * 2)
internal const val BLUR_RADIUS = 4
internal const val BLUR_PASSES = 3
private const val GLOW_SATURATION_BOOST = 1.15f
private const val GLOW_CORE_OVERSCAN = 1.08f
private const val GLOW_INTENSITY_ALPHA = 0.90f
private const val GLOW_ALPHA_EPSILON = 0.004f
private const val BLUR_CACHE_MAX_ENTRIES = 8

@Immutable
internal data class AlbumArtGlowState(
    val blurredArtwork: ImageBitmap? = null,
    val alpha: Float = 0f,
) {
    val isVisible: Boolean get() = blurredArtwork != null && alpha > GLOW_ALPHA_EPSILON

    companion object {
        val Empty = AlbumArtGlowState()
    }
}

private class CachedBlurredArt(
    val sourceRef: WeakReference<Bitmap>,
    val generationId: Int,
    val blurred: ImageBitmap,
)

private val blurredArtworkCacheLock = Any()
private val blurredArtworkCache = ArrayDeque<CachedBlurredArt>(BLUR_CACHE_MAX_ENTRIES)

@Composable
internal fun rememberAlbumArtGlow(media: MediaTrackInfo): AlbumArtGlowState {
    val enabled by OverlayPreferences.albumArtGlowFlow.collectAsState()
    val albumArt = media.albumArt
    val targetAlpha = if (enabled && albumArt != null) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = LarpMotion.fade(),
        label = "album_art_glow_alpha",
    )
    val shouldRender = enabled || alpha > GLOW_ALPHA_EPSILON
    val cachedHit = remember(albumArt, shouldRender) {
        if (albumArt != null && shouldRender) getCachedBlurredArtwork(albumArt) else null
    }
    var asyncBlurred by remember(albumArt, shouldRender) { mutableStateOf(cachedHit) }
    LaunchedEffect(albumArt, shouldRender) {
        if (albumArt != null && shouldRender && asyncBlurred == null) {
            asyncBlurred = withContext(Dispatchers.Default) {
                getOrCreateBlurredArtwork(albumArt)
            }
        }
    }
    val blurred = cachedHit ?: asyncBlurred
    val retainedHolder = remember { arrayOfNulls<ImageBitmap>(1) }
    if (blurred != null) {
        retainedHolder[0] = blurred
    } else if (!shouldRender) {
        retainedHolder[0] = null
    }
    return AlbumArtGlowState(
        blurredArtwork = blurred ?: retainedHolder[0],
        alpha = alpha,
    )
}

internal fun Modifier.albumArtGlow(glow: AlbumArtGlowState, anchor: ArtworkAnchor?): Modifier {
    if (!glow.isVisible) return this
    return drawWithContent {
        if (anchor?.isMoving() != true) {
            drawBlurredArtworkHalo(glow, size, opacity = 1f)
        }
        drawContent()
    }
}

internal fun DrawScope.drawMovingAlbumArtGlow(
    glow: AlbumArtGlowState,
    tileSize: Size,
    opacity: Float,
) {
    if (!glow.isVisible || opacity <= 0f) return
    drawBlurredArtworkHalo(glow, tileSize, opacity = opacity)
}

/** Edge-to-edge artwork radiates its blurred cover colors softly across the seam into the reflection. */
internal fun DrawScope.drawBlendedAlbumArtGlow(
    glow: AlbumArtGlowState,
    isLandscape: Boolean,
    anchor: ArtworkAnchor?,
) {
    if (!glow.isVisible || anchor?.isMoving() == true) return
    val side = if (isLandscape) size.width else size.height
    if (side <= 0f) return
    drawBlurredArtworkHalo(
        glow = glow,
        tileSize = Size(side, side),
        opacity = 0.85f,
    )
}

private fun DrawScope.drawBlurredArtworkHalo(
    glow: AlbumArtGlowState,
    tileSize: Size,
    opacity: Float,
) {
    val blurred = glow.blurredArtwork ?: return
    val effectiveAlpha = (glow.alpha * opacity * GLOW_INTENSITY_ALPHA).coerceIn(0f, 1f)
    if (effectiveAlpha <= 0f || tileSize.width <= 0f || tileSize.height <= 0f) return

    val scaleFactor = GLOW_CORE_OVERSCAN * (BLUR_BUFFER_SIZE.toFloat() / BLUR_CORE_SIZE.toFloat())
    val dstWidth = (tileSize.width * scaleFactor).roundToInt().coerceAtLeast(1)
    val dstHeight = (tileSize.height * scaleFactor).roundToInt().coerceAtLeast(1)
    val dstLeft = ((tileSize.width - dstWidth) / 2f).roundToInt()
    val dstTop = ((tileSize.height - dstHeight) / 2f).roundToInt()

    drawImage(
        image = blurred,
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(dstWidth, dstHeight),
        alpha = effectiveAlpha,
        filterQuality = FilterQuality.High,
    )
}

private fun getCachedBlurredArtwork(source: Bitmap): ImageBitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    val generationId = source.generationId
    synchronized(blurredArtworkCacheLock) {
        val iterator = blurredArtworkCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cachedSource = entry.sourceRef.get()
            if (cachedSource == null || cachedSource.isRecycled) {
                iterator.remove()
            } else if (cachedSource === source && entry.generationId == generationId) {
                return entry.blurred
            }
        }
    }
    return null
}

private fun getOrCreateBlurredArtwork(source: Bitmap): ImageBitmap? {
    getCachedBlurredArtwork(source)?.let { return it }
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    val generationId = source.generationId

    val created = createBlurredArtworkBitmap(source)?.asImageBitmap() ?: return null
    synchronized(blurredArtworkCacheLock) {
        if (blurredArtworkCache.size >= BLUR_CACHE_MAX_ENTRIES) {
            blurredArtworkCache.removeFirst()
        }
        blurredArtworkCache.addLast(CachedBlurredArt(WeakReference(source), generationId, created))
    }
    return created
}

private fun createBlurredArtworkBitmap(source: Bitmap): Bitmap? {
    val softwareCopy = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        null
    }
    val readableSource = softwareCopy ?: source
    try {
        val cropSide = minOf(readableSource.width, readableSource.height)
        if (cropSide <= 0) return null
        val cropLeft = (readableSource.width - cropSide) / 2
        val cropTop = (readableSource.height - cropSide) / 2

        val buffer = createBitmap(BLUR_BUFFER_SIZE, BLUR_BUFFER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            readableSource,
            Rect(cropLeft, cropTop, cropLeft + cropSide, cropTop + cropSide),
            Rect(
                BLUR_PADDING,
                BLUR_PADDING,
                BLUR_PADDING + BLUR_CORE_SIZE,
                BLUR_PADDING + BLUR_CORE_SIZE,
            ),
            paint,
        )

        val pixels = IntArray(BLUR_BUFFER_SIZE * BLUR_BUFFER_SIZE)
        buffer.getPixels(pixels, 0, BLUR_BUFFER_SIZE, 0, 0, BLUR_BUFFER_SIZE, BLUR_BUFFER_SIZE)
        blurArtworkPixels(
            pixels = pixels,
            width = BLUR_BUFFER_SIZE,
            height = BLUR_BUFFER_SIZE,
            radius = BLUR_RADIUS,
            passes = BLUR_PASSES,
            saturationBoost = GLOW_SATURATION_BOOST,
        )
        buffer.setPixels(pixels, 0, BLUR_BUFFER_SIZE, 0, 0, BLUR_BUFFER_SIZE, BLUR_BUFFER_SIZE)
        return buffer
    } finally {
        softwareCopy?.recycle()
    }
}

/**
 * Separable multi-pass box blur in premultiplied-alpha float space (approximating a Gaussian blur).
 * Out-of-bounds samples are treated as transparent zero so padded artwork fades smoothly to 0 alpha
 * with no hard rectangular edges and no dark halo fringing.
 */
internal fun blurArtworkPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    passes: Int = BLUR_PASSES,
    saturationBoost: Float = GLOW_SATURATION_BOOST,
) {
    if (width <= 0 || height <= 0 || radius <= 0 || passes <= 0) return
    val size = width * height
    if (pixels.size < size) return

    val a = FloatArray(size)
    val r = FloatArray(size)
    val g = FloatArray(size)
    val b = FloatArray(size)

    for (i in 0 until size) {
        val argb = pixels[i]
        val af = ((argb ushr 24) and 0xFF) / 255f
        a[i] = af
        r[i] = ((argb ushr 16) and 0xFF) * af
        g[i] = ((argb ushr 8) and 0xFF) * af
        b[i] = (argb and 0xFF) * af
    }

    val tmpA = FloatArray(size)
    val tmpR = FloatArray(size)
    val tmpG = FloatArray(size)
    val tmpB = FloatArray(size)
    val invWindow = 1f / (2 * radius + 1)

    repeat(passes) {
        // Horizontal pass: src -> tmp
        for (y in 0 until height) {
            val row = y * width
            var sumA = 0f
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f
            val initEnd = minOf(radius, width - 1)
            for (x in 0..initEnd) {
                val idx = row + x
                sumA += a[idx]
                sumR += r[idx]
                sumG += g[idx]
                sumB += b[idx]
            }
            for (x in 0 until width) {
                val dst = row + x
                tmpA[dst] = sumA * invWindow
                tmpR[dst] = sumR * invWindow
                tmpG[dst] = sumG * invWindow
                tmpB[dst] = sumB * invWindow

                val removeX = x - radius
                if (removeX >= 0) {
                    val idx = row + removeX
                    sumA -= a[idx]
                    sumR -= r[idx]
                    sumG -= g[idx]
                    sumB -= b[idx]
                }
                val addX = x + radius + 1
                if (addX < width) {
                    val idx = row + addX
                    sumA += a[idx]
                    sumR += r[idx]
                    sumG += g[idx]
                    sumB += b[idx]
                }
            }
        }

        // Vertical pass: tmp -> src
        for (x in 0 until width) {
            var sumA = 0f
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f
            val initEnd = minOf(radius, height - 1)
            for (y in 0..initEnd) {
                val idx = y * width + x
                sumA += tmpA[idx]
                sumR += tmpR[idx]
                sumG += tmpG[idx]
                sumB += tmpB[idx]
            }
            for (y in 0 until height) {
                val dst = y * width + x
                a[dst] = sumA * invWindow
                r[dst] = sumR * invWindow
                g[dst] = sumG * invWindow
                b[dst] = sumB * invWindow

                val removeY = y - radius
                if (removeY >= 0) {
                    val idx = removeY * width + x
                    sumA -= tmpA[idx]
                    sumR -= tmpR[idx]
                    sumG -= tmpG[idx]
                    sumB -= tmpB[idx]
                }
                val addY = y + radius + 1
                if (addY < height) {
                    val idx = addY * width + x
                    sumA += tmpA[idx]
                    sumR += tmpR[idx]
                    sumG += tmpG[idx]
                    sumB += tmpB[idx]
                }
            }
        }
    }

    for (i in 0 until size) {
        val alpha = a[i].coerceIn(0f, 1f)
        if (alpha <= 0.002f) {
            pixels[i] = 0
            continue
        }
        var ur = (r[i] / alpha).coerceIn(0f, 255f)
        var ug = (g[i] / alpha).coerceIn(0f, 255f)
        var ub = (b[i] / alpha).coerceIn(0f, 255f)
        if (saturationBoost != 1f) {
            val luma = 0.2126f * ur + 0.7152f * ug + 0.0722f * ub
            ur = (luma + (ur - luma) * saturationBoost).coerceIn(0f, 255f)
            ug = (luma + (ug - luma) * saturationBoost).coerceIn(0f, 255f)
            ub = (luma + (ub - luma) * saturationBoost).coerceIn(0f, 255f)
        }
        val ai = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val ri = ur.roundToInt().coerceIn(0, 255)
        val gi = ug.roundToInt().coerceIn(0, 255)
        val bi = ub.roundToInt().coerceIn(0, 255)
        pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
