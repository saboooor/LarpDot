package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

internal fun batteryPercentOf(activity: NotificationActivityInfo?): Int? {
    if (activity == null) return null
    return when (activity.kind) {
        NotificationActivityKind.BATTERY_CHARGING,
        NotificationActivityKind.BATTERY_LOW,
        -> activity.progress?.takeIf { it in 0..100 }
            ?: activity.progressFraction?.let { (it * 100f).roundToInt().coerceIn(0, 100) }
        NotificationActivityKind.BLUETOOTH -> {
            val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
            if (disconnected || activity.pairedEarbudBatteryLevels != null) null
            else activity.earbudBatteryLevels?.let { it.left ?: it.right }
                ?: activity.bluetoothBatteryPercent?.takeIf { it in 0..100 }
        }
        else -> null
    }
}

internal fun batteryGaugeColor(
    kind: NotificationActivityKind,
    percent: Int,
    primaryColor: Color,
): Color = when {
    kind == NotificationActivityKind.BATTERY_CHARGING -> Color(0xFF4CAF50)
    kind == NotificationActivityKind.BATTERY_LOW || percent <= 20 -> Color(0xFFFF5252)
    else -> primaryColor
}

internal fun compactBatteryRingDiameter(thickness: Dp): Dp =
    (thickness - 6.dp).coerceIn(18.dp, 22.dp).coerceAtMost((thickness - 2.dp).coerceAtLeast(0.dp))

internal fun compactRadialGaugeExtraDp(thickness: Dp): Dp {
    val ringDiameter = compactBatteryRingDiameter(thickness)
    val ringInset = compactEndInset(thickness, ringDiameter)
    return (ringDiameter + ringInset) * 2
}

internal fun activityRadialProgress(
    activity: NotificationActivityInfo?,
    progressRingEnabled: Boolean,
    primaryColor: Color,
): Pair<Int, Color>? {
    if (activity == null) return null
    if (activity.kind == NotificationActivityKind.NAVIGATION) return null
    if (activity.kind == NotificationActivityKind.VOLUME) {
        val percent = activity.progress?.coerceIn(0, 100)
            ?: activity.progressFraction?.let { (it * 100f).roundToInt().coerceIn(0, 100) }
            ?: return null
        val color = if (percent <= 0) Color(0xFFFF5252) else primaryColor
        return percent to color
    }
    val battery = batteryPercentOf(activity)
    if (battery != null) {
        return battery to batteryGaugeColor(activity.kind, battery, primaryColor)
    }
    if (!progressRingEnabled) return null
    val fraction = activity.progressFraction ?: return null
    val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
    return percent to activityIconTint(activity.kind, primaryColor)
}

/**
 * Radial ring for a collapsed secondary dot: matches [activityRadialProgress], and falls back
 * to the main earbud battery level when paired earbuds are collapsed into a single circular dot.
 */
internal fun secondaryDotRadialProgress(
    activity: NotificationActivityInfo?,
    progressRingEnabled: Boolean,
    primaryColor: Color,
): Pair<Int, Color>? {
    activityRadialProgress(activity, progressRingEnabled, primaryColor)?.let { return it }
    if (activity?.kind != NotificationActivityKind.BLUETOOTH) return null
    val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
    if (disconnected) return null
    val paired = activity.pairedEarbudBatteryLevels ?: return null
    val percent = activity.bluetoothBatteryPercent?.takeIf { it in 0..100 }
        ?: minOf(paired.left!!, paired.right!!)
    return percent to batteryGaugeColor(activity.kind, percent, primaryColor)
}

internal data class ShapeProgressStart(
    val startIndex: Int,
    val isClockwise: Boolean,
)

/**
 * Finds the 12 o'clock (top-center) sample index and screen-space winding direction
 * for an arbitrary closed shape contour so progress always starts at 12 o'clock and sweeps clockwise.
 */
internal fun findShapeTopStartAndWinding(
    samples: List<Offset>,
    center: Offset,
): ShapeProgressStart {
    if (samples.isEmpty()) return ShapeProgressStart(startIndex = 0, isClockwise = true)
    val n = samples.size
    var signedArea2 = 0f
    var bestIndex = 0
    var bestAngleDiff = Double.MAX_VALUE
    val topAngle = -PI / 2.0
    for (i in 0 until n) {
        val a = samples[i]
        val b = samples[(i + 1) % n]
        signedArea2 += (a.x * b.y - b.x * a.y)
        val angle = atan2((a.y - center.y).toDouble(), (a.x - center.x).toDouble())
        var diff = angle - topAngle
        while (diff > PI) diff -= 2.0 * PI
        while (diff < -PI) diff += 2.0 * PI
        val absDiff = abs(diff)
        if (absDiff < bestAngleDiff) {
            bestAngleDiff = absDiff
            bestIndex = i
        }
    }
    return ShapeProgressStart(
        startIndex = bestIndex,
        isClockwise = signedArea2 >= 0f,
    )
}

@Composable
internal fun RadialProgressRing(
    progressProvider: () -> Float,
    color: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    trackColor: Color = color.copy(alpha = 0.24f),
    shape: Shape = CircleShape,
) {
    if (shape == CircleShape) {
        Canvas(modifier = modifier) {
            val strokePx = strokeWidth.toPx()
            val arcSide = (minOf(size.width, size.height) - strokePx).coerceAtLeast(0f)
            val topLeft = Offset(
                (size.width - arcSide) / 2f,
                (size.height - arcSide) / 2f,
            )
            val arcSize = Size(arcSide, arcSide)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            val fraction = progressProvider().coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = fraction * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        return
    }

    Box(
        modifier = modifier.drawWithCache {
            val strokePx = strokeWidth.toPx()
            val halfStroke = strokePx / 2f
            val innerWidth = (size.width - strokePx).coerceAtLeast(0f)
            val innerHeight = (size.height - strokePx).coerceAtLeast(0f)
            val valid = innerWidth > 0.5f && innerHeight > 0.5f
            val innerSize = Size(innerWidth, innerHeight)
            val outline = if (valid) shape.createOutline(innerSize, layoutDirection, this) else null
            val shapePath = when (outline) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                null -> null
            }
            val pathMeasure = PathMeasure()
            var totalLength = 0f
            var startDistance = 0f
            var isClockwise = true
            if (shapePath != null) {
                pathMeasure.setPath(shapePath, forceClosed = true)
                totalLength = pathMeasure.length
                if (totalLength > 0f) {
                    val sampleCount = 128
                    val samples = List(sampleCount) { idx ->
                        pathMeasure.getPosition(totalLength * idx / sampleCount)
                    }
                    val startInfo = findShapeTopStartAndWinding(
                        samples = samples,
                        center = Offset(innerWidth / 2f, innerHeight / 2f),
                    )
                    startDistance = totalLength * startInfo.startIndex / sampleCount
                    isClockwise = startInfo.isClockwise
                }
            }
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val progressSegment = Path()

            onDrawBehind {
                if (shapePath == null || totalLength <= 0f) return@onDrawBehind
                translate(left = halfStroke, top = halfStroke) {
                    drawPath(
                        path = shapePath,
                        color = trackColor,
                        style = stroke,
                    )
                    val fraction = progressProvider().coerceIn(0f, 1f)
                    if (fraction > 0.002f) {
                        if (fraction >= 0.999f) {
                            drawPath(
                                path = shapePath,
                                color = color,
                                style = stroke,
                            )
                        } else {
                            progressSegment.rewind()
                            val progressDistance = totalLength * fraction
                            if (isClockwise) {
                                val endDistance = startDistance + progressDistance
                                pathMeasure.getSegment(
                                    startDistance,
                                    endDistance.coerceAtMost(totalLength),
                                    progressSegment,
                                    startWithMoveTo = true,
                                )
                                if (endDistance > totalLength) {
                                    pathMeasure.getSegment(
                                        0f,
                                        endDistance - totalLength,
                                        progressSegment,
                                        startWithMoveTo = false,
                                    )
                                }
                            } else {
                                val rawStart = startDistance - progressDistance
                                if (rawStart >= 0f) {
                                    pathMeasure.getSegment(
                                        rawStart,
                                        startDistance,
                                        progressSegment,
                                        startWithMoveTo = true,
                                    )
                                } else {
                                    pathMeasure.getSegment(
                                        totalLength + rawStart,
                                        totalLength,
                                        progressSegment,
                                        startWithMoveTo = true,
                                    )
                                    if (startDistance > 0f) {
                                        pathMeasure.getSegment(
                                            0f,
                                            startDistance,
                                            progressSegment,
                                            startWithMoveTo = false,
                                        )
                                    }
                                }
                            }
                            drawPath(
                                path = progressSegment,
                                color = color,
                                style = stroke,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun RadialProgressRing(
    progress: Float,
    color: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    trackColor: Color = color.copy(alpha = 0.24f),
    shape: Shape = CircleShape,
) {
    val animatedFractionState = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = LarpMotion.fade(280),
        label = "radial_progress_ring",
    )
    RadialProgressRing(
        progressProvider = { animatedFractionState.value },
        color = color,
        strokeWidth = strokeWidth,
        modifier = modifier,
        trackColor = trackColor,
        shape = shape,
    )
}

@Composable
internal fun RadialProgressIcon(
    progress: Float?,
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    ringModifier: Modifier = Modifier,
    icon: @Composable (Dp) -> Unit,
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        icon((diameter - 8.dp).coerceIn(10.dp, 13.dp))
        if (progress != null) {
            RadialProgressRing(progress, color, 2.dp, Modifier.fillMaxSize().then(ringModifier))
        }
    }
}

@Composable
internal fun ExpandedActivityGauge(
    percent: Int?,
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    RadialProgressGauge(
        progress = (percent ?: 0) / 100f,
        text = percent?.toString() ?: "—",
        color = color,
        diameter = diameter,
        strokeWidth = 3.5.dp,
        showPercentSymbol = percent != null,
        modifier = modifier,
    )
}

@Composable
internal fun RadialProgressGauge(
    progress: Float,
    text: String,
    color: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
    showPercentSymbol: Boolean = diameter >= 30.dp,
) {
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val hasThreeOrMoreDigits = text.length >= 3
    val maxFontSize = if (diameter >= 30.dp) 18.sp else 13.sp
    val labelPadding = strokeWidth + if (hasThreeOrMoreDigits) 1.5.dp else 0.5.dp

    Box(
        modifier = modifier
            .size(diameter)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        RadialProgressRing(
            progress = progress,
            color = color,
            strokeWidth = strokeWidth,
            modifier = Modifier.fillMaxSize(),
        )
        val label = remember(text, showPercentSymbol) {
            if (showPercentSymbol) formatCompactStatus("$text%") else AnnotatedString(text)
        }
        Text(
            text = label,
            color = color,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 4.sp,
                maxFontSize = if (hasThreeOrMoreDigits) maxFontSize * 0.8f else maxFontSize,
                stepSize = 0.5.sp,
            ),
            modifier = Modifier.padding(horizontal = labelPadding, vertical = strokeWidth),
            fontWeight = fontWeight,
            fontFamily = fontStyle.fontFamily,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            maxLines = 1,
            softWrap = false,
        )
    }
}
