package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityAction
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.ui.theme.LarpMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.roundToInt

private val DOWNLOAD_KEYWORDS_REGEX = Regex(
    """\b(downloads?|downloading|downloaded)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Checks whether an activity is certainly a download rather than a generic progress notification.
 * If true, the download icon is shown. If false, the app icon is displayed instead.
 */
internal fun isCertainDownload(activity: NotificationActivityInfo): Boolean {
    if (activity.kind != NotificationActivityKind.PROGRESS) return false

    val pkg = activity.packageName.lowercase()
    if (pkg.contains("download") ||
        pkg == "com.android.providers.downloads" ||
        pkg == "com.google.android.providers.downloads"
    ) {
        return true
    }

    if (DOWNLOAD_KEYWORDS_REGEX.containsMatchIn(activity.title)) {
        return true
    }
    if (activity.text != null && DOWNLOAD_KEYWORDS_REGEX.containsMatchIn(activity.text)) {
        return true
    }

    return false
}

@Composable
fun ActivityIcon(
    activity: NotificationActivityInfo,
    modifier: Modifier = Modifier.size(18.dp),
    iconShape: Shape = RoundedCornerShape(4.dp),
) {
    val isDownload = isCertainDownload(activity)
    val appIcon = activity.appIcon
    val primaryColor = MaterialTheme.colorScheme.primary

    val iconModifier = modifier

    when (activity.kind) {
        NotificationActivityKind.CALL -> {
            Icon(
                imageVector = LarpIcons.Call,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.TIMER -> {
            Icon(
                imageVector = LarpIcons.Timer,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.NAVIGATION -> {
            Icon(
                imageVector = LarpIcons.Navigation,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.BATTERY_CHARGING -> {
            Icon(
                imageVector = LarpIcons.BatteryChargingFull,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.BATTERY_LOW -> {
            Icon(
                imageVector = LarpIcons.BatteryAlert,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.SOUND_MODE -> {
            val icon = activityIcon(activity)
            val tint = when {
                activity.title.contains("Silent", ignoreCase = true) -> Color(0xFFFF5252)
                activity.title.contains("Vibrate", ignoreCase = true) -> Color(0xFFFFA000)
                else -> Color(0xFF4CAF50)
            }
            Icon(
                imageVector = icon,
                contentDescription = activity.appName,
                tint = tint,
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.BLUETOOTH -> {
            val isDisconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
            Icon(
                imageVector = activityIcon(activity),
                contentDescription = activity.title,
                tint = if (isDisconnected) primaryColor.copy(alpha = 0.45f) else primaryColor,
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.AUDIO_OUTPUT -> {
            Icon(
                imageVector = activityIcon(activity),
                contentDescription = activity.title,
                tint = primaryColor,
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.SCREENSHOT -> {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = activity.title,
                    modifier = iconModifier.clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = LarpIcons.Screenshot,
                    contentDescription = activity.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = iconModifier,
                )
            }
        }
        NotificationActivityKind.PROGRESS -> {
            if (isDownload) {
                Icon(
                    imageVector = LarpIcons.Download,
                    contentDescription = activity.appName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = iconModifier,
                )
            } else if (appIcon != null) {
                Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = activity.appName,
                    modifier = iconModifier.clip(iconShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = LarpIcons.Notifications,
                    contentDescription = activity.appName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = iconModifier,
                )
            }
        }
    }
}

/**
 * Returns true if the activity has status content to display on the right wing of the minimized island
 * (such as a progress percentage, chronometer countdown, or active call duration).
 *
 * Activities without right-wing content (like indeterminate loading with no progress percentage,
 * or navigation activities which are lower priority than two-sided music) return false so that the
 * island manager can demote them to a secondary dot, allowing two-sided media (music) to occupy the main pill.
 */
fun hasCompactStatusContent(activity: NotificationActivityInfo?): Boolean {
    if (activity == null) return false
    if (activity.kind == NotificationActivityKind.NAVIGATION) {
        return compactStatusFallback(activity).isNotBlank()
    }
    if (activity.progressFraction != null) return true
    if (activity.chronometerBaseElapsedRealtime != null) return true
    return compactStatusFallback(activity).isNotBlank()
}

/**
 * Formats compact status text, styling '%' with a smaller font size so it does not overpower digits.
 */
fun formatCompactStatus(status: String): AnnotatedString {
    if (!status.contains('%')) {
        return AnnotatedString(status)
    }
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in Regex("%").findAll(status)) {
            append(status.substring(lastIndex, match.range.first))
            withStyle(SpanStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium)) {
                append("%")
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < status.length) {
            append(status.substring(lastIndex))
        }
    }
}

/**
 * Calculates the extra width in dp needed for the compact island based on the measured status text width.
 * Status text widens the island dynamically instead of truncating.
 * The extra width is split symmetrically into left and right wings around the central punch hole.
 */
fun calculateActivityCompactExtraDp(statusTextWidthDp: Float): Float {
    val minWingWidthDp = 28f
    val wingPaddingDp = 6f
    val requiredWingWidthDp = if (statusTextWidthDp <= 0f) {
        minWingWidthDp
    } else {
        maxOf(minWingWidthDp, statusTextWidthDp + wingPaddingDp)
    }
    return requiredWingWidthDp * 2f
}

internal data class CompactActivityWings(
    val leadingDp: Float,
    val trailingDp: Float,
) {
    val widthDp: Float get() = leadingDp + trailingDp
    val trailingFraction: Float get() = if (widthDp > 0f) trailingDp / widthDp else 0.5f
}

/**
 * Fits the compact navigation pill tightly to its icon on the left (28dp) and ETA text on the right
 * (text width + 10dp horizontal padding + 2dp breathing room), keeping the camera cutout anchored.
 */
internal fun compactNavigationWings(
    statusTextWidthDp: Float,
    maxTrailingDp: Float = 68f,
): CompactActivityWings {
    val leadingDp = 28f
    val trailingDp = if (statusTextWidthDp <= 0f) {
        leadingDp
    } else {
        maxOf(leadingDp, statusTextWidthDp + 12f).coerceAtMost(maxOf(leadingDp, maxTrailingDp))
    }
    return CompactActivityWings(leadingDp = leadingDp, trailingDp = trailingDp)
}

@Composable
internal fun rememberCompactStatusTextWidthDp(
    status: String?,
    compactFontSize: Boolean = false,
): Dp {
    if (status.isNullOrEmpty()) return 0.dp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeightValue by OverlayPreferences.islandFontWeightFlow.collectAsState()
    val normalizedStatus = remember(status) {
        status.map { if (it.isDigit()) '0' else it }.joinToString("")
    }
    val configuredStyle = MaterialTheme.typography.labelSmall
    return remember(normalizedStatus, density, configuredStyle, fontStyle, fontWeightValue, compactFontSize) {
        val formatted = formatCompactStatus(normalizedStatus)
        val result = textMeasurer.measure(
            text = formatted,
            style = configuredStyle.copy(
                fontSize = if (compactFontSize || normalizedStatus.contains('%')) 10.5.sp else 11.5.sp,
                fontFamily = fontStyle.fontFamily,
                fontWeight = FontWeight(fontWeightValue),
            ),
            softWrap = false,
        )
        with(density) { result.size.width.toDp() }
    }
}

/**
 * Measures status text to calculate dynamic extra width for compact islands
 * (used by live activities, flashlight percentage, etc.).
 */
@Composable
fun rememberCompactStatusExtraDp(
    status: String?,
): Dp {
    if (status.isNullOrEmpty()) return 80.dp
    val measuredTextWidthDp = rememberCompactStatusTextWidthDp(status)
    return calculateActivityCompactExtraDp(measuredTextWidthDp.value).dp
}

internal fun batteryPercentOf(activity: NotificationActivityInfo?): Int? {
    if (activity == null) return null
    return when (activity.kind) {
        NotificationActivityKind.BATTERY_CHARGING,
        NotificationActivityKind.BATTERY_LOW,
        -> activity.progress?.takeIf { it in 0..100 }
            ?: activity.progressFraction?.let { (it * 100f).roundToInt().coerceIn(0, 100) }
        NotificationActivityKind.BLUETOOTH -> {
            val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
            if (disconnected) null else activity.bluetoothBatteryPercent?.takeIf { it in 0..100 }
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
    (thickness - 6.dp).coerceIn(18.dp, 22.dp)

internal fun compactRadialGaugeExtraDp(thickness: Dp): Dp {
    val ringDiameter = compactBatteryRingDiameter(thickness)
    val ringInset = ((thickness - ringDiameter) / 2f).coerceAtLeast(3.dp)
    return (ringDiameter + ringInset + 2.dp) * 2
}

internal fun activityRadialProgress(
    activity: NotificationActivityInfo?,
    progressRingEnabled: Boolean,
    primaryColor: Color,
): Pair<Int, Color>? {
    if (activity == null) return null
    if (activity.kind == NotificationActivityKind.NAVIGATION) return null
    val battery = batteryPercentOf(activity)
    if (battery != null) {
        return battery to batteryGaugeColor(activity.kind, battery, primaryColor)
    }
    if (!progressRingEnabled) return null
    val fraction = activity.progressFraction ?: return null
    val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
    return percent to activityIconTint(activity.kind, primaryColor)
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
    val animatedFraction by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = LarpMotion.fade(280),
        label = "radial_progress_ring",
    )
    RadialProgressRing(
        progressProvider = { animatedFraction },
        color = color,
        strokeWidth = strokeWidth,
        modifier = modifier,
        trackColor = trackColor,
        shape = shape,
    )
}

@Composable
internal fun RadialProgressGauge(
    progress: Float,
    text: String,
    color: Color,
    diameter: Dp,
    strokeWidth: Dp,
    fontSize: TextUnit,
    showPercentSymbol: Boolean = diameter >= 30.dp,
    modifier: Modifier = Modifier,
) {
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)

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
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontStyle.fontFamily,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun BatteryRadialRing(
    percent: Int,
    color: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    RadialProgressRing(
        progress = percent.coerceIn(0, 100) / 100f,
        color = color,
        strokeWidth = strokeWidth,
        modifier = modifier,
    )
}

@Composable
internal fun BatteryRadialGauge(
    percent: Int,
    color: Color,
    diameter: Dp,
    strokeWidth: Dp,
    fontSize: TextUnit,
    showPercentSymbol: Boolean = diameter >= 30.dp,
    modifier: Modifier = Modifier,
) {
    val clamped = percent.coerceIn(0, 100)
    RadialProgressGauge(
        progress = clamped / 100f,
        text = "$clamped",
        color = color,
        diameter = diameter,
        strokeWidth = strokeWidth,
        fontSize = fontSize,
        showPercentSymbol = showPercentSymbol,
        modifier = modifier,
    )
}

@Composable
fun rememberActivityCompactExtraDp(
    activity: NotificationActivityInfo?,
    status: String?,
): Dp {
    if (activity == null) return 80.dp
    if (batteryPercentOf(activity) != null) return 64.dp
    if (status.isNullOrEmpty()) return 48.dp
    return rememberCompactStatusExtraDp(status)
}

@Composable
internal fun CompactNotificationActivityContent(
    activity: NotificationActivityInfo,
    cutoutDiameterDp: Dp,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    trailingWingFraction: Float = 0.5f,
) {
    val status = rememberCompactActivityStatus(activity)
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val radialProgress = activityRadialProgress(activity, activitySettings.progressOutline, primaryColor)

    val icon = @Composable {
        ActivityIcon(
            activity = activity,
            modifier = Modifier.size(18.dp),
        )
    }

    val isPercentage = status.contains('%')
    val isNavigation = activity.kind == NotificationActivityKind.NAVIGATION
    val formattedStatus = remember(status) { formatCompactStatus(status) }

    val summary = @Composable {
        Text(
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            text = formattedStatus,
            color = Color.White,
            fontSize = if (isPercentage || isNavigation) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
            fontFamily = fontStyle.fontFamily,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }

    val compactRingDiameter = compactBatteryRingDiameter(cutoutDiameterDp)
    val compactRingInset = ((cutoutDiameterDp - compactRingDiameter) / 2f).coerceAtLeast(3.dp)

    // Centered layout around the camera cutout:
    // Symmetrical left/right (or top/bottom in landscape) wings flank a cutout clearance spacer
    // of exactly cutoutDiameterDp. The wings dynamically expand to fit wider text without truncating.
    // The icon and status text share equal outer-edge insets from the rounded pill caps.
    if (isLandscape) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = if (radialProgress != null) compactRingInset else 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    BatteryRadialGauge(
                        percent = percent,
                        color = ringColor,
                        diameter = compactRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (percent >= 100) 6.5.sp else 7.5.sp,
                        showPercentSymbol = false,
                    )
                } else if (status.isNotEmpty()) {
                    Text(
                        text = formattedStatus,
                        color = Color.White,
                        fontSize = if (isPercentage) 9.5.sp else 10.sp,
                        fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
                        fontFamily = fontStyle.fontFamily,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.verticalActivityLabel(),
                    )
                }
            }
        }
    } else {
        val leadingWeight = (1f - trailingWingFraction).coerceAtLeast(0.001f)
        val trailingWeight = trailingWingFraction.coerceAtLeast(0.001f)
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(leadingWeight)
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(trailingWeight)
                    .fillMaxHeight()
                    .padding(start = 2.dp, end = if (radialProgress != null) compactRingInset else 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    BatteryRadialGauge(
                        percent = percent,
                        color = ringColor,
                        diameter = compactRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (percent >= 100) 7.sp else 8.sp,
                        showPercentSymbol = false,
                    )
                } else if (status.isNotEmpty()) {
                    summary()
                }
            }
        }
    }
}

private val LEADING_BATTERY_PERCENT_REGEX = Regex("""^\d+%(?:\s+remaining|\s+reached)?\s*•\s*""", RegexOption.IGNORE_CASE)

internal fun activitySubtitle(activity: NotificationActivityInfo): String {
    if (activity.kind == NotificationActivityKind.BLUETOOTH) {
        val isDisconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
        if (isDisconnected) return "Disconnected"
        val deviceType = activity.bluetoothDeviceType
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("Bluetooth", ignoreCase = true) &&
                    !it.equals("Audio", ignoreCase = true)
            }
        return deviceType.orEmpty()
    }
    val raw = activity.text?.takeIf { it.isNotBlank() } ?: activity.appName
    if (batteryPercentOf(activity) != null) {
        val stripped = raw.replace(LEADING_BATTERY_PERCENT_REGEX, "").trim()
        if (stripped.matches(Regex("""^\d+%$"""))) return ""
        return stripped
    }
    return raw
}

@Composable
internal fun activityTextStyle(title: Boolean): TextStyle {
    val family by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val weight by OverlayPreferences.islandFontWeightFlow.collectAsState()
    return MaterialTheme.typography.bodyMedium.copy(
        fontFamily = family.fontFamily, fontWeight = FontWeight(weight),
        fontSize = if (title) 20.sp else 12.sp,
        lineHeight = if (title) 25.sp else 17.sp,
    )
}

internal fun shouldSpanActivityIconLeftOfCutout(
    isLandscape: Boolean,
    topInsetDp: Float,
    cutoutCenterXDp: Float,
    cutoutDiameterDp: Float,
): Boolean {
    if (isLandscape || topInsetDp <= 0f) return false
    val cutoutLeftOnScreen = cutoutCenterXDp - (cutoutDiameterDp / 2f)
    return cutoutLeftOnScreen >= 88f
}

internal fun inlineNavigationAction(activity: NotificationActivityInfo): NotificationActivityAction? {
    if (activity.kind != NotificationActivityKind.NAVIGATION || activity.actions.isEmpty()) return null
    return activity.actions.firstOrNull { action ->
        val t = action.title
        t.contains("end", ignoreCase = true) ||
            t.contains("exit", ignoreCase = true) ||
            t.contains("stop", ignoreCase = true)
    } ?: activity.actions.first()
}

/** Measure the same text styles and wrapping limits used by the activity card. */
@Composable
internal fun expandedActivitySize(
    activity: NotificationActivityInfo?,
    maxWidth: Dp,
    maxHeight: Dp,
    topInset: Dp,
    cutoutDiameterDp: Dp = 26.dp,
    spanIconLeftOfCutout: Boolean = false,
): DpSize {
    if (activity == null) return DpSize(maxWidth, 80.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val titleStyle = activityTextStyle(true)
    val subtitleStyle = activityTextStyle(false)
    val buttonStyle = MaterialTheme.typography.labelLarge
    val etaStyle = subtitleStyle.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    return with(density) {
        val subtitle = activitySubtitle(activity)
        val isNavigation = activity.kind == NotificationActivityKind.NAVIGATION
        val radialProgress = activityRadialProgress(activity, activitySettings.progressOutline, primaryColor)
        val hasRadialGauge = radialProgress != null
        val navigationEta = if (isNavigation) compactStatusFallback(activity).takeIf { it.isNotBlank() } else null
        val inlineAction = inlineNavigationAction(activity)
        val footerActions = if (inlineAction != null) {
            activity.actions.filterNot { it == inlineAction }.take(3)
        } else {
            activity.actions.take(3)
        }
        val titleNatural = measurer.measure(activity.title, titleStyle, softWrap = false).size.width.toDp()
        val subtitleNatural = if (subtitle.isNotEmpty()) {
            measurer.measure(subtitle, subtitleStyle, softWrap = false).size.width.toDp()
        } else 0.dp
        val etaWidth = if (navigationEta != null) {
            measurer.measure(navigationEta, etaStyle, softWrap = false).size.width.toDp()
        } else 0.dp
        val inlineButtonWidth = if (inlineAction != null) {
            maxOf(64.dp, measurer.measure(inlineAction.title, buttonStyle, softWrap = false).size.width.toDp() + 32.dp)
        } else 0.dp
        val navRightWidth = maxOf(etaWidth, inlineButtonWidth)
        val navRightHeight = if (navRightWidth > 0.dp) {
            (if (navigationEta != null) 20.dp else 0.dp) +
                (if (inlineAction != null) 38.dp else 0.dp) +
                (if (navigationEta != null && inlineAction != null) 6.dp else 0.dp) +
                24.dp
        } else 0.dp
        val buttonWidths = footerActions.map {
            maxOf(64.dp, measurer.measure(it.title, buttonStyle, softWrap = false).size.width.toDp() + 48.dp)
        }
        val actionsWidth = buttonWidths.fold(0.dp) { sum, width -> sum + width } + (8.dp * (buttonWidths.size - 1).coerceAtLeast(0))
        val horizontalChrome = when {
            spanIconLeftOfCutout && hasRadialGauge -> 160.dp
            spanIconLeftOfCutout && navRightWidth > 0.dp -> 116.dp + navRightWidth
            spanIconLeftOfCutout -> 100.dp
            hasRadialGauge -> 134.dp
            navRightWidth > 0.dp -> 92.dp + navRightWidth
            else -> 78.dp
        }
        val minCardWidth = if (spanIconLeftOfCutout) {
            maxOf(184.dp, 152.dp + cutoutDiameterDp)
        } else {
            160.dp
        }
        val width = maxOf(minCardWidth, maxOf(titleNatural, subtitleNatural) + horizontalChrome, actionsWidth + 44.dp).coerceAtMost(maxWidth)
        val textWidthPx = (width - horizontalChrome).coerceAtLeast(1.dp).roundToPx()
        val titleMaxLines = if (isNavigation) 3 else 2
        val titleHeight = measurer.measure(activity.title, titleStyle, maxLines = titleMaxLines,
            constraints = Constraints(maxWidth = textWidthPx)).size.height.toDp()
        val subtitleHeight = if (subtitle.isNotEmpty()) {
            measurer.measure(subtitle, subtitleStyle, maxLines = 4,
                constraints = Constraints(maxWidth = textWidthPx)).size.height.toDp()
        } else 0.dp
        val textHeight = titleHeight + subtitleHeight + (if (isNavigation) 4.dp else 0.dp)
        var rows = if (buttonWidths.isEmpty()) 0 else 1
        var rowWidth = 0.dp
        buttonWidths.forEach { button ->
            val next = if (rowWidth == 0.dp) button else rowWidth + 8.dp + button
            if (rowWidth > 0.dp && next > width - 44.dp) { rows++; rowWidth = button }
            else rowWidth = next
        }
        val isScreenshot = activity.kind == NotificationActivityKind.SCREENSHOT && activity.mediaUri != null
        val showProgressBar = !isNavigation && activity.progressFraction != null && !hasRadialGauge
        val previewHeight = if (isScreenshot && activity.appIcon != null) 118.dp else 0.dp
        val pocketActionHeight = if (isScreenshot) 56.dp else 0.dp
        val hasFooter = showProgressBar || isScreenshot || rows > 0
        val bottomPadding = 22.dp
        val headerBlockHeight = if (spanIconLeftOfCutout) {
            if (hasFooter) {
                maxOf(48.dp, navRightHeight, topInset + textHeight) + bottomPadding
            } else {
                maxOf(64.dp, navRightHeight, topInset + textHeight + bottomPadding)
            }
        } else {
            (if (topInset > 0.dp) topInset else 16.dp) + maxOf(
                if (hasRadialGauge) 44.dp else 24.dp,
                if (navRightWidth > 0.dp) navRightHeight - 16.dp else 0.dp,
                textHeight,
            ) + bottomPadding
        }
        val height = headerBlockHeight +
            (if (showProgressBar) 20.dp else 0.dp) +
            previewHeight + pocketActionHeight +
            (if (rows > 0) 14.dp + 48.dp * rows + 8.dp * (rows - 1) else 0.dp)
        val effectiveWidth = if (isScreenshot) maxOf(width, 280.dp).coerceAtMost(maxWidth) else width
        DpSize(effectiveWidth, height.coerceAtMost(maxHeight))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedNotificationActivityContent(
    activity: NotificationActivityInfo,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
    topContentInset: Dp = 0.dp,
    spanIconLeftOfCutout: Boolean = false,
) {
    val context = LocalContext.current
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val openApp = {
        when {
            activity.kind == NotificationActivityKind.SCREENSHOT && activity.mediaUri != null -> {
                ScreenshotPocketManager.openItem(context, activity.mediaUri, activity.mediaMimeType)
            }
            activity.kind == NotificationActivityKind.AUDIO_OUTPUT || activity.kind == NotificationActivityKind.BLUETOOTH -> {
                AudioOutputTracker.openOutputSwitcher(context)
            }
            else -> NotificationActivityState.open(context, activity)
        }
        onCollapse()
    }

    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val isNavigation = activity.kind == NotificationActivityKind.NAVIGATION
    val radialProgress = activityRadialProgress(activity, activitySettings.progressOutline, primaryColor)
    val hasRadialGauge = radialProgress != null
    val navigationEta = if (isNavigation) compactStatusFallback(activity).takeIf { it.isNotBlank() } else null
    val inlineAction = inlineNavigationAction(activity)
    val footerActions = if (inlineAction != null) {
        activity.actions.filterNot { it == inlineAction }.take(3)
    } else {
        activity.actions.take(3)
    }
    val hasNavRightContent = navigationEta != null || inlineAction != null
    val startPadding = if (spanIconLeftOfCutout) 18.dp else 22.dp
    val endPadding = if (spanIconLeftOfCutout) (if (hasRadialGauge || hasNavRightContent) 18.dp else 20.dp) else 22.dp
    val bottomPadding = 22.dp
    val isScreenshot = activity.kind == NotificationActivityKind.SCREENSHOT && activity.mediaUri != null
    val showProgressBar = !isNavigation && activity.progressFraction != null && !hasRadialGauge
    val hasFooter = showProgressBar || isScreenshot || footerActions.isNotEmpty()
    val columnTopPadding = when {
        spanIconLeftOfCutout -> 0.dp
        topContentInset > 0.dp -> topContentInset
        else -> 16.dp
    }
    val columnBottomPadding = if (spanIconLeftOfCutout && !hasFooter) 0.dp else bottomPadding
    val subtitle = activitySubtitle(activity)

    val navRightSlot: (@Composable (Modifier) -> Unit)? = if (hasNavRightContent) {
        { slotModifier ->
            Column(
                modifier = slotModifier,
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                if (navigationEta != null) {
                    Text(
                        text = navigationEta,
                        color = primaryColor,
                        style = activityTextStyle(false).copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        fontFamily = fontStyle.fontFamily,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (inlineAction != null) {
                    Button(
                        onClick = { NotificationActivityState.perform(inlineAction) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 38.dp),
                    ) {
                        Text(inlineAction.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    } else null

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState, enabled = scrollState.maxValue > 0)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = openApp,
            )
            .padding(start = startPadding, end = endPadding, top = columnTopPadding, bottom = columnBottomPadding),
    ) {
        if (spanIconLeftOfCutout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(if (!hasFooter) Modifier.heightIn(min = 64.dp) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    ActivityIcon(
                        activity = activity,
                        modifier = Modifier.size(44.dp),
                        iconShape = RoundedCornerShape(12.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            top = topContentInset,
                            bottom = if (hasFooter) 0.dp else bottomPadding,
                        ),
                ) {
                    Text(
                        text = activity.title,
                        color = Color.White,
                        style = activityTextStyle(true),
                        fontWeight = fontWeight,
                        fontFamily = fontStyle.fontFamily,
                        maxLines = if (isNavigation) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            style = activityTextStyle(false),
                            fontFamily = fontStyle.fontFamily,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BatteryRadialGauge(
                            percent = percent,
                            color = ringColor,
                            diameter = 44.dp,
                            strokeWidth = 3.5.dp,
                            fontSize = if (percent >= 100) 10.sp else 11.5.sp,
                            showPercentSymbol = true,
                        )
                    }
                } else if (navRightSlot != null) {
                    navRightSlot(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActivityIcon(
                    activity = activity,
                    modifier = Modifier.size(24.dp),
                    iconShape = RoundedCornerShape(6.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title,
                        color = Color.White,
                        style = activityTextStyle(true),
                        fontWeight = fontWeight,
                        fontFamily = fontStyle.fontFamily,
                        maxLines = if (isNavigation) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            style = activityTextStyle(false),
                            fontFamily = fontStyle.fontFamily,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    Spacer(Modifier.width(2.dp))
                    BatteryRadialGauge(
                        percent = percent,
                        color = ringColor,
                        diameter = 42.dp,
                        strokeWidth = 3.5.dp,
                        fontSize = if (percent >= 100) 9.5.sp else 11.sp,
                        showPercentSymbol = true,
                    )
                } else if (navRightSlot != null) {
                    navRightSlot(Modifier)
                }
            }
        }

        if (showProgressBar) activity.progressFraction?.let { fraction ->
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }

        if (activity.kind == NotificationActivityKind.SCREENSHOT && activity.mediaUri != null) {
            if (activity.appIcon != null) {
                Spacer(Modifier.height(12.dp))
                Image(
                    bitmap = activity.appIcon.asImageBitmap(),
                    contentDescription = activity.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { openApp() },
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        ScreenshotPocketManager.shareItem(context, activity.mediaUri, activity.mediaMimeType)
                        onCollapse()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = {
                        ScreenshotPocketManager.copyItem(context, activity.mediaUri)
                        onCollapse()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = {
                        ScreenshotPocketManager.deleteItem(context, activity.key, activity.mediaUri)
                        onCollapse()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (footerActions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                footerActions.forEach { action ->
                    Button(modifier = Modifier.heightIn(min = 48.dp), onClick = { NotificationActivityState.perform(action) }) {
                        Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal fun activityIcon(kind: NotificationActivityKind): ImageVector = when (kind) {
    NotificationActivityKind.PROGRESS -> LarpIcons.Download
    NotificationActivityKind.NAVIGATION -> LarpIcons.Navigation
    NotificationActivityKind.CALL -> LarpIcons.Call
    NotificationActivityKind.TIMER -> LarpIcons.Timer
    NotificationActivityKind.BATTERY_CHARGING -> LarpIcons.BatteryChargingFull
    NotificationActivityKind.BATTERY_LOW -> LarpIcons.BatteryAlert
    NotificationActivityKind.SOUND_MODE -> LarpIcons.VolumeUp
    NotificationActivityKind.BLUETOOTH -> LarpIcons.BluetoothConnected
    NotificationActivityKind.AUDIO_OUTPUT -> LarpIcons.Headphones
    NotificationActivityKind.SCREENSHOT -> LarpIcons.Screenshot
}

internal fun activityIcon(activity: NotificationActivityInfo): ImageVector = when (activity.kind) {
    NotificationActivityKind.PROGRESS -> if (isCertainDownload(activity)) LarpIcons.Download else LarpIcons.Notifications
    NotificationActivityKind.NAVIGATION -> LarpIcons.Navigation
    NotificationActivityKind.CALL -> LarpIcons.Call
    NotificationActivityKind.TIMER -> LarpIcons.Timer
    NotificationActivityKind.BATTERY_CHARGING -> LarpIcons.BatteryChargingFull
    NotificationActivityKind.BATTERY_LOW -> LarpIcons.BatteryAlert
    NotificationActivityKind.SOUND_MODE -> when {
        activity.title.contains("Silent", ignoreCase = true) || activity.text?.contains("Silent", ignoreCase = true) == true -> LarpIcons.VolumeOff
        activity.title.contains("Vibrate", ignoreCase = true) || activity.text?.contains("Vibrate", ignoreCase = true) == true -> LarpIcons.Vibration
        else -> LarpIcons.VolumeUp
    }
    NotificationActivityKind.BLUETOOTH -> bluetoothDeviceIcon(activity)
    NotificationActivityKind.AUDIO_OUTPUT -> audioOutputActivityIcon(activity)
    NotificationActivityKind.SCREENSHOT -> LarpIcons.Screenshot
}

internal fun bluetoothDeviceIcon(activity: NotificationActivityInfo): ImageVector {
    val type = activity.bluetoothDeviceType.orEmpty().lowercase()
    val name = activity.title.lowercase()
    val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
    val matched = when {
        type.contains("earbud") ||
            name.contains("buds") || name.contains("airpods") || name.contains("earpod") ||
            name.contains("freebuds") || name.contains("wf-") || name.contains("ear (") -> LarpIcons.Earbuds
        type.contains("headphone") ||
            name.contains("headphone") || name.contains("wh-") || name.contains("quietcomfort") ||
            name.contains("airpods max") || name.contains("beats studio") -> LarpIcons.Headphones
        type.contains("headset") || name.contains("headset") -> LarpIcons.HeadsetMic
        type.contains("watch") || type.contains("wearable") ||
            name.contains("watch") || name.contains("fitbit") || name.contains("garmin") || name.contains("band") -> LarpIcons.Watch
        type.contains("speaker") ||
            name.contains("speaker") || name.contains("jbl") || name.contains("soundlink") ||
            name.contains("sonos") || name.contains("boom") || name.contains("homepod") -> LarpIcons.Speaker
        type.contains("car") ||
            name.contains("car") || name.contains("auto") || name.contains("tesla") ||
            name.contains("honda") || name.contains("toyota") || name.contains("ford") || name.contains("bmw") -> LarpIcons.DirectionsCar
        type.contains("controller") || type.contains("gamepad") ||
            name.contains("controller") || name.contains("dualsense") || name.contains("dualshock") ||
            name.contains("xbox") || name.contains("joy-con") || name.contains("8bitdo") -> LarpIcons.SportsEsports
        type.contains("keyboard") || name.contains("keyboard") || name.contains("keychron") -> LarpIcons.Keyboard
        type.contains("mouse") || name.contains("mouse") || name.contains("trackpad") -> LarpIcons.Mouse
        type.contains("computer") || name.contains("macbook") || name.contains("laptop") -> LarpIcons.Laptop
        type.contains("phone") -> LarpIcons.PhoneAndroid
        type.contains("audio") -> LarpIcons.BluetoothAudio
        else -> null
    }
    return matched ?: if (disconnected) LarpIcons.BluetoothDisabled else LarpIcons.BluetoothConnected
}

private fun audioOutputActivityIcon(activity: NotificationActivityInfo): ImageVector {
    val type = activity.bluetoothDeviceType.orEmpty().lowercase()
    val name = activity.title.lowercase()
    return when {
        name == "this phone" || type.contains("switched from") -> LarpIcons.PhoneAndroid
        type.contains("usb") -> LarpIcons.Usb
        type.contains("car") -> LarpIcons.DirectionsCar
        type.contains("wired") -> LarpIcons.Headphones
        else -> bluetoothDeviceIcon(activity)
    }
}

internal fun activityIconTint(kind: NotificationActivityKind, fallback: Color): Color = when (kind) {
    NotificationActivityKind.CALL -> Color(0xFF4CAF50)
    NotificationActivityKind.BATTERY_CHARGING -> Color(0xFF4CAF50)
    NotificationActivityKind.BATTERY_LOW -> Color(0xFFFF5252)
    NotificationActivityKind.SOUND_MODE -> Color(0xFFFFA000)
    NotificationActivityKind.TIMER,
    NotificationActivityKind.NAVIGATION,
    NotificationActivityKind.BLUETOOTH,
    NotificationActivityKind.AUDIO_OUTPUT,
    NotificationActivityKind.SCREENSHOT,
    NotificationActivityKind.PROGRESS,
    -> fallback
}

@Composable
private fun rememberChronometerText(activity: NotificationActivityInfo): String {
    var now by remember(activity.key) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(activity.key, activity.chronometerBaseElapsedRealtime) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val base = activity.chronometerBaseElapsedRealtime ?: now
    val millis = if (activity.chronometerCountsDown) base - now else now - base
    val seconds = millis.absoluteValue / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%d:%02d".format(minutes, remainingSeconds)
}

internal fun Modifier.verticalActivityLabel(): Modifier = this
    .graphicsLayer { rotationZ = 90f }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ))
        layout(placeable.height, placeable.width) {
            placeable.placeRelative(
                x = -(placeable.width - placeable.height) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }

@Composable
internal fun rememberCompactActivityStatus(activity: NotificationActivityInfo): String {
    if (activity.concurrentStatuses.isNotEmpty()) return activity.concurrentStatuses.joinToString(" • ")
    val chronometer = activity.chronometerBaseElapsedRealtime?.let { rememberChronometerText(activity) }
    return remember(activity, chronometer) {
        val raw = chronometer ?: compactStatusFallback(activity)
        formatCompactActivityDisplayStatus(activity.kind, raw)
    }
}

internal fun formatCompactActivityDisplayStatus(
    kind: NotificationActivityKind,
    status: String,
): String {
    if (kind != NotificationActivityKind.NAVIGATION || status.isBlank()) return status
    return status
        .replace(Regex("""\b(\d+)\s*(?:hrs?|hours?)\s*(\d+)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE), "$1h$2m")
        .replace(Regex("""\b(\d+)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE), "$1m")
        .replace(Regex("""\b(\d+)\s*(?:hrs?|hours?)\b""", RegexOption.IGNORE_CASE), "$1h")
        .replace(Regex("""\b(\d+(?:\.\d+)?)\s*miles?\b""", RegexOption.IGNORE_CASE), "$1mi")
        .replace(Regex("""\b(\d+(?:\.\d+)?)\s*(mi|km)\b""", RegexOption.IGNORE_CASE), "$1$2")
        .trim()
}

internal fun compactStatusFallback(activity: NotificationActivityInfo): String {
    if (activity.kind == NotificationActivityKind.SOUND_MODE) return ""
    if (activity.kind == NotificationActivityKind.BLUETOOTH) {
        val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
        if (disconnected) return ""
        return activity.bluetoothBatteryPercent?.let { "$it%" } ?: ""
    }
    // For navigation: NEVER put percentage on the right.
    if (activity.kind == NotificationActivityKind.NAVIGATION) {
        val shortEta = findShortEta(activity.title) ?: findShortEta(activity.text)
        if (shortEta != null) return shortEta

        val shortTime = findShortTime(activity.title) ?: findShortTime(activity.text)
        if (shortTime != null) return shortTime

        return ""
    }

    activity.progressFraction?.let { return "${(it * 100).toInt()}%" }

    findPercentage(activity.title)?.let { return it }
    findPercentage(activity.text)?.let { return it }

    val shortEta = findShortEta(activity.title) ?: findShortEta(activity.text)
    if (shortEta != null) return shortEta

    val shortTime = findShortTime(activity.title) ?: findShortTime(activity.text)
    if (shortTime != null) return shortTime

    return when (activity.kind) {
        NotificationActivityKind.CALL -> "Call"
        NotificationActivityKind.NAVIGATION -> ""
        NotificationActivityKind.TIMER -> "Timer"
        NotificationActivityKind.PROGRESS -> ""
        NotificationActivityKind.BATTERY_CHARGING -> "Charging"
        NotificationActivityKind.BATTERY_LOW -> "Low"
        NotificationActivityKind.SOUND_MODE -> when {
            activity.title.contains("Silent", ignoreCase = true) || activity.text?.contains("Silent", ignoreCase = true) == true -> "Silent"
            activity.title.contains("Vibrate", ignoreCase = true) || activity.text?.contains("Vibrate", ignoreCase = true) == true -> "Vibrate"
            else -> "Ring"
        }
        NotificationActivityKind.BLUETOOTH -> ""
        NotificationActivityKind.AUDIO_OUTPUT -> activity.title
        NotificationActivityKind.SCREENSHOT -> "Share"
    }
}

private fun findPercentage(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val match = Regex("""(\d{1,3})\s*%""").find(text) ?: return null
    val value = match.groupValues[1].toIntOrNull() ?: return null
    return if (value in 0..100) "$value%" else null
}

private fun findShortEta(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val compoundMatch = Regex("""\b(\d{1,2}\s*(?:hr|h)\s*\d{1,2}\s*(?:min|m))\b""", RegexOption.IGNORE_CASE).find(text)
    if (compoundMatch != null) return compoundMatch.value.trim()

    val timeMatch = Regex("""\b(\d{1,2}\s*(?:min|mins|hr|hrs))\b""", RegexOption.IGNORE_CASE).find(text)
    if (timeMatch != null) return timeMatch.value.trim()

    val distanceMatch = Regex("""\b(?<!\.)(\d+(?:\.\d+)?\s*(?:mi|miles|km))\b""", RegexOption.IGNORE_CASE).find(text)
    if (distanceMatch != null) return distanceMatch.value.trim()

    return null
}

private fun findShortTime(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val match = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""").find(text) ?: return null
    return match.value
}
