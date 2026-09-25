package ca.saboor.larpdot.ui.overlay

import android.graphics.RenderEffect
import android.graphics.Typeface
import android.text.TextPaint
import ca.saboor.larpdot.service.OverlayPreferences
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

/** The small corners face the adjacent bubble, as in a Material 3 button group. */
internal fun groupedIslandShape(
    radius: Dp,
    before: Boolean,
    after: Boolean,
    isLandscape: Boolean,
): RoundedCornerShape {
    val inner = (radius * 0.3f).coerceAtMost(6.dp)
    return if (isLandscape) RoundedCornerShape(
        topStart = if (before) inner else radius,
        topEnd = if (before) inner else radius,
        bottomEnd = if (after) inner else radius,
        bottomStart = if (after) inner else radius,
    ) else RoundedCornerShape(
        topStart = if (before) inner else radius,
        topEnd = if (after) inner else radius,
        bottomEnd = if (after) inner else radius,
        bottomStart = if (before) inner else radius,
    )
}

/**
 * Authentic cubic-bezier spring and ease curves extracted directly from com.pryshedko.mtisland
 * (zl0.java, jc1.java, f92.java).
 */
val MtIslandEnterEasing = CubicBezierEasing(0.25f, 0.9f, 0.35f, 1.02f) // Soft overshoot bounce
val MtIslandExitEasing = CubicBezierEasing(0.42f, 0.0f, 0.12f, 1.0f)  // Apple-style snap collapse
val MtIslandDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
val MtIslandStandard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

enum class IslandType {
    MEDIA,
    FLASHLIGHT,
    NOTIFICATION_ACTIVITY,
}

fun secondaryBubbleThicknessDp(mainThicknessDp: Float, smaller: Boolean): Float =
    if (smaller) mainThicknessDp * 0.8f else mainThicknessDp

fun secondaryItemWidthDp(
    type: IslandType,
    thicknessDp: Float,
    isMiniPill: Boolean,
    hasMedia: Boolean,
    flashlightStatus: String,
    activityStatus: String?,
): Float {
    if (!isMiniPill) return thicknessDp
    return when (type) {
        IslandType.MEDIA -> {
            if (hasMedia) thicknessDp + 28f else thicknessDp
        }
        IslandType.FLASHLIGHT -> {
            if (flashlightStatus.isNotBlank()) thicknessDp + 24f else thicknessDp
        }
        IslandType.NOTIFICATION_ACTIVITY -> {
            if (!activityStatus.isNullOrBlank()) {
                val extra = if (activityStatus.length > 4) 34f else 24f
                thicknessDp + extra
            } else {
                thicknessDp
            }
        }
    }
}

internal fun squircleShape(
    radiusPx: Float,
    curvatureFactor: Float = 0.8f,
    topLeftRadiusPx: Float = radiusPx,
    topRightRadiusPx: Float = radiusPx,
    bottomRightRadiusPx: Float = radiusPx,
    bottomLeftRadiusPx: Float = radiusPx,
) = GenericShape { size, _ ->
    val radius = radiusPx.coerceAtMost(minOf(size.width, size.height) / 2f)
    val topLeft = topLeftRadiusPx.coerceIn(0f, radius)
    val topRight = topRightRadiusPx.coerceIn(0f, radius)
    val bottomRight = bottomRightRadiusPx.coerceIn(0f, radius)
    val bottomLeft = bottomLeftRadiusPx.coerceIn(0f, radius)

    moveTo(topLeft, 0f)
    lineTo(size.width - topRight, 0f)
    cubicTo(
        size.width - topRight + topRight * curvatureFactor,
        0f,
        size.width,
        topRight - topRight * curvatureFactor,
        size.width,
        topRight,
    )
    lineTo(size.width, size.height - bottomRight)
    cubicTo(
        size.width,
        size.height - bottomRight + bottomRight * curvatureFactor,
        size.width - bottomRight + bottomRight * curvatureFactor,
        size.height,
        size.width - bottomRight,
        size.height,
    )
    lineTo(bottomLeft, size.height)
    cubicTo(
        bottomLeft - bottomLeft * curvatureFactor,
        size.height,
        0f,
        size.height - bottomLeft + bottomLeft * curvatureFactor,
        0f,
        size.height - bottomLeft,
    )
    lineTo(0f, topLeft)
    cubicTo(
        0f,
        topLeft - topLeft * curvatureFactor,
        topLeft - topLeft * curvatureFactor,
        0f,
        topLeft,
        0f,
    )
    close()
}

/**
 * Sweeps song playback progress along the perimeter of the fluid morphing capsule/card.
 * Accurately traces from 12 o'clock (top center above the camera cutout) clockwise,
 * dynamically matching the container's animated width, height, and corner radius.
 */
fun Modifier.islandFluidProgressBorder(
    progressFraction: Float,
    cornerRadius: Dp,
    shape: Shape,
    strokeWidth: Dp = 0.75.dp,
    trackColor: Color = Color(0x30FFFFFF),
    progressColor: Color = Color(0xFFA5C8FF),
): Modifier = this.drawWithContent {
    drawContent()

    val strokePx = strokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val w = size.width
    val h = size.height
    // A newly appearing secondary dot can be smaller than the outline for a frame.
    // Its content may draw, but the border has no valid path until it grows.
    if (w <= strokePx || h <= strokePx) return@drawWithContent
    val rPx = (cornerRadius.toPx() - halfStroke).coerceIn(0f, (minOf(w, h) / 2f) - halfStroke)

    val left = halfStroke
    val top = halfStroke
    val right = w - halfStroke
    val bottom = h - halfStroke

    val outline = shape.createOutline(size, layoutDirection, this)

    // 1. Ambient hairline outline follows the surface shape, including the expanded squircle.
    when (outline) {
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = trackColor,
            style = Stroke(width = strokePx),
        )
        is Outline.Rounded -> drawPath(
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = trackColor,
            style = Stroke(width = strokePx),
        )
        is Outline.Rectangle -> drawRect(
            color = trackColor,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height),
            style = Stroke(width = strokePx),
        )
    }

    // 2. Sweeping active playback progress arc
    if (progressFraction > 0.002f) {
        if (outline is Outline.Generic) {
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(outline.path, forceClosed = false)
            val progressSegment = Path()
            val squircleRadius = cornerRadius.toPx()
                .coerceAtMost(minOf(w, h) / 2f)
            val startDistance = ((w / 2f) - squircleRadius)
                .coerceIn(0f, pathMeasure.length)
            val progressDistance = pathMeasure.length * progressFraction.coerceIn(0f, 1f)
            val endDistance = startDistance + progressDistance
            pathMeasure.getSegment(
                startDistance,
                endDistance.coerceAtMost(pathMeasure.length),
                progressSegment,
                startWithMoveTo = true,
            )
            if (endDistance > pathMeasure.length) {
                pathMeasure.getSegment(
                    0f,
                    endDistance - pathMeasure.length,
                    progressSegment,
                    startWithMoveTo = false,
                )
            }
            drawPath(
                path = progressSegment,
                color = progressColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        } else {
        val centerX = w / 2f
        val path = Path().apply {
            moveTo(centerX, top)
            lineTo(right - rPx, top)
            arcTo(
                rect = Rect(right - 2 * rPx, top, right, top + 2 * rPx),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(right, bottom - rPx)
            arcTo(
                rect = Rect(right - 2 * rPx, bottom - 2 * rPx, right, bottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(left + rPx, bottom)
            arcTo(
                rect = Rect(left, bottom - 2 * rPx, left + 2 * rPx, bottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(left, top + rPx)
            arcTo(
                rect = Rect(left, top, left + 2 * rPx, top + 2 * rPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(centerX, top)
        }

        val pathMeasure = PathMeasure()
        pathMeasure.setPath(path, forceClosed = true)
        val progressLength = pathMeasure.length * progressFraction.coerceIn(0f, 1f)

        val progressSegment = Path()
        pathMeasure.getSegment(0f, progressLength, progressSegment, startWithMoveTo = true)

        drawPath(
            path = progressSegment,
            color = progressColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        }
    }
}

@Language("AGSL")
internal const val PROGRESSIVE_BLUR_SHADER = """
    uniform shader composable;
    uniform float2 size;
    uniform float direction; // 0 = L->R (curved arc), 1 = T->B (curved arc), 2 = B->T, 3 = 2D (expanded island leak)
    uniform float maxBlur;
    uniform float startFraction;
    uniform float uTopSeam;
    uniform float uRightSeam;
    uniform float uDotRadius;

    const float GOLDEN_ANGLE = 2.39996323;

    float gaussian(float r, float sigma) {
        return exp(-0.5 * (r * r) / (sigma * sigma));
    }

    half4 main(float2 coord) {
        float progress = 0.0;
        float fadeFactor = 1.0;

        if (direction < 0.5) {
            // ONLY blur and fade on the RIGHT side towards the dot:
            // The left perimeter and body of the album art remain 100% unblurred and solid.
            // The right boundary curves outward in the center like a circular dome: )
            float yc = size.y * 0.5;
            float dy = coord.y - yc;
            float rCurve = size.y * 0.85;
            float curveOffset = rCurve - sqrt(max(rCurve * rCurve - dy * dy, 0.0));
            float xEff = coord.x + curveOffset;

            float blurStart = size.x * 0.40;
            float blurEnd = size.x * 0.90;
            float blurT = clamp((xEff - blurStart) / max(blurEnd - blurStart, 1.0), 0.0, 1.0);
            progress = smoothstep(0.0, 1.0, blurT);

            float fadeStart = size.x * 0.50;
            float fadeEnd = size.x * 0.96;
            float fadeT = clamp((xEff - fadeStart) / max(fadeEnd - fadeStart, 1.0), 0.0, 1.0);
            fadeFactor = 1.0 - smoothstep(0.0, 1.0, fadeT);
        } else if (direction < 1.5) {
            // Landscape: ONLY blur and fade on the BOTTOM side towards the dot
            float xc = size.x * 0.5;
            float dx = coord.x - xc;
            float rCurve = size.x * 0.85;
            float curveOffset = rCurve - sqrt(max(rCurve * rCurve - dx * dx, 0.0));
            float yEff = coord.y + curveOffset;

            float blurStart = size.y * 0.40;
            float blurEnd = size.y * 0.90;
            float blurT = clamp((yEff - blurStart) / max(blurEnd - blurStart, 1.0), 0.0, 1.0);
            progress = smoothstep(0.0, 1.0, blurT);

            float fadeStart = size.y * 0.50;
            float fadeEnd = size.y * 0.96;
            float fadeT = clamp((yEff - fadeStart) / max(fadeEnd - fadeStart, 1.0), 0.0, 1.0);
            fadeFactor = 1.0 - smoothstep(0.0, 1.0, fadeT);
        } else if (direction < 2.5) {
            // Vertical: bottom (0) -> top (1) with startFraction leak
            float t = 1.0 - (coord.y / size.y);
            progress = clamp((t - startFraction) / max(1.0 - startFraction, 0.001), 0.0, 1.0);
        } else {
            // Direction 3: 2D leak for expanded island
            float leakTop = 20.0;
            float topBound = uTopSeam + leakTop;
            float progTop = clamp((topBound - coord.y) / max(topBound, 1.0), 0.0, 1.0);

            float leakRight = 24.0;
            float rightBound = uRightSeam - leakRight;
            float progRight = clamp((coord.x - rightBound) / max(size.x - rightBound, 1.0), 0.0, 1.0);
            progress = max(progTop, progRight);
        }

        // If completely faded out past the fade edge, return transparent black immediately
        if (fadeFactor <= 0.001) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        float blurFactor = smoothstep(0.0, 1.0, progress);
        float currentRadius = blurFactor * maxBlur;

        if (currentRadius < 0.5) {
            return composable.eval(coord) * fadeFactor;
        }

        half4 accumColor = half4(0.0);
        float accumWeight = 0.0;
        float sigma = max(currentRadius * 0.45, 0.8);

        float centerWeight = gaussian(0.0, sigma);
        accumColor += composable.eval(coord) * centerWeight;
        accumWeight += centerWeight;

        const int SAMPLES = 24;
        for (int i = 0; i < SAMPLES; ++i) {
            float fi = float(i);
            float r = sqrt((fi + 0.5) / float(SAMPLES)) * currentRadius;
            float theta = fi * GOLDEN_ANGLE;
            float2 offset = float2(cos(theta), sin(theta)) * r;
            float weight = gaussian(r, sigma);

            accumColor += composable.eval(coord + offset) * weight;
            accumWeight += weight;
        }

        // The fade factor is applied AFTER blur accumulation, ensuring the blur is fully faded
        return (accumColor / accumWeight) * fadeFactor;
    }

"""

@Language("AGSL")
internal const val ORGANIC_SCOOP_FADE_SHADER = """
    uniform float2 uSize;
    uniform float2 uDotCenter;
    uniform float uDotRadius;
    uniform float uScoopDepth;
    uniform float uFadeWidth;
    uniform float uHalfWidth;

    float scoopY(float x) {
        float dist = abs(x - uDotCenter.x);
        float t = clamp(1.0 - (dist / uHalfWidth), 0.0, 1.0);
        
        // Perfectly symmetrical smooth sine bump
        float bump = sin(t * 1.5707963);
        bump = pow(max(bump, 0.0), 1.5);
        return bump * uScoopDepth;
    }

    half4 main(float2 coord) {
        // Guaranteed solid black over the physical camera cutout
        if (length(coord - uDotCenter) <= uDotRadius) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        float sy = scoopY(coord.x);
        float diff = coord.y - sy;
        
        if (diff <= 0.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }
        
        if (diff >= uFadeWidth) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }
        
        float alpha = smoothstep(uFadeWidth, 0.0, diff);
        return half4(0.0, 0.0, 0.0, alpha);
    }

"""

@Composable
internal fun Modifier.progressiveBlur(
    direction: Int, // 0 = L->R, 1 = T->B, 2 = B->T, 3 = 2D leak
    maxBlurDp: Dp = 16.dp,
    startFraction: Float = 0f,
    topSeamPx: Float = 0f,
    rightSeamPx: Float = 0f,
    dotRadiusPx: Float = 0f,
): Modifier {
    val density = LocalDensity.current
    val maxBlurPx = with(density) { maxBlurDp.toPx() }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(PROGRESSIVE_BLUR_SHADER) }
        return this.graphicsLayer {
            if (size.width > 0f && size.height > 0f) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("direction", direction.toFloat())
                shader.setFloatUniform("maxBlur", maxBlurPx)
                shader.setFloatUniform("startFraction", startFraction)
                shader.setFloatUniform("uTopSeam", topSeamPx)
                shader.setFloatUniform("uRightSeam", rightSeamPx)
                shader.setFloatUniform("uDotRadius", dotRadiusPx)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable")
                    .asComposeRenderEffect()
            }
        }
    } else {
        return this
    }
}


/**
 * Calculates the dynamic horizontal expansion (in dp) for a song announcement.
 * When both title and artist texts are short, the island expands only as much as needed
 * to display them comfortably without excessive empty black space. When texts are longer,
 * it smoothly expands up to the standard 210dp cap.
 */
fun calculateAnnouncementExtraDp(
    title: String,
    artist: String,
    cutoutDiameterDp: Float,
    albumArtStyle: OverlayPreferences.AlbumArtStyle,
    density: Float,
): Float {
    val safeDensity = if (density > 0f) density else 1f
    val effectiveTitle = title.ifBlank { "Playing Track" }
    val effectiveArtist = artist.ifBlank { "Media Player" }

    val (titleWidthDp, artistWidthDp) = try {
        val titleTypeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, 600, false)
        } else {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val titlePaint = TextPaint().apply {
            textSize = 11.5f * safeDensity
            typeface = titleTypeface
        }
        val tW = titlePaint.measureText(effectiveTitle) / safeDensity

        val artistPaint = TextPaint().apply {
            textSize = 11.5f * safeDensity
            typeface = Typeface.DEFAULT
        }
        val aW = artistPaint.measureText(effectiveArtist) / safeDensity
        Pair(tW, aW)
    } catch (_: Throwable) {
        // Robust fallback for JVM unit tests where android.jar methods are stubbed
        Pair(effectiveTitle.length * 6.2f, effectiveArtist.length * 5.6f)
    }

    val leftNonTextDp = if (albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED) {
        val nestedArtSizeDp = (cutoutDiameterDp - 8f).coerceIn(18f, 26f)
        // start 3dp (outer) + nested art + spacing 3dp + end 2dp (inner) = 8dp + art
        nestedArtSizeDp + 8f
    } else {
        // start 6dp (outer) + end 2dp (inner) = 8dp
        8f
    }

    // Right wing: start 2dp (inner) + end 6dp (outer) = 8dp
    val rightNonTextDp = 8f

    val leftNeededDp = leftNonTextDp + titleWidthDp
    val rightNeededDp = rightNonTextDp + artistWidthDp

    // Symmetrical wings around the physical cutout
    val wingWidthDp = maxOf(leftNeededDp, rightNeededDp)
    val calculatedExtraDp = wingWidthDp * 2f

    // Ultra-compact: as tight as possible around the text, capped at 210dp
    return calculatedExtraDp.coerceIn(24f, 210f)
}
