package ca.saboor.larpdot.ui.overlay

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

private val DOWNLOAD_KEYWORDS_REGEX = Regex(
    """\b(downloads?|downloading|downloaded)\b""",
    RegexOption.IGNORE_CASE,
)
private val PERCENT_CHAR_REGEX = Regex("%")
private val PERCENT_VALUE_REGEX = Regex("""(\d{1,3})\s*%""")
private val COMPOUND_ETA_REGEX = Regex("""\b(\d{1,2}\s*(?:hr|h)\s*\d{1,2}\s*(?:min|m))\b""", RegexOption.IGNORE_CASE)
private val SHORT_TIME_UNIT_REGEX = Regex("""\b(\d{1,2}\s*(?:min|mins|hr|hrs))\b""", RegexOption.IGNORE_CASE)
private val SHORT_DISTANCE_REGEX = Regex("""\b(?<!\.)(\d+(?:\.\d+)?\s*(?:mi|miles|km))\b""", RegexOption.IGNORE_CASE)
private val CLOCK_TIME_REGEX = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""")
private val NAV_COMPOUND_DURATION_REGEX = Regex("""\b(\d+)\s*(?:hrs?|hours?)\s*(\d+)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
private val NAV_MINUTES_REGEX = Regex("""\b(\d+)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
private val NAV_HOURS_REGEX = Regex("""\b(\d+)\s*(?:hrs?|hours?)\b""", RegexOption.IGNORE_CASE)
private val NAV_MILES_REGEX = Regex("""\b(\d+(?:\.\d+)?)\s*miles?\b""", RegexOption.IGNORE_CASE)
private val NAV_DISTANCE_SPACE_REGEX = Regex("""\b(\d+(?:\.\d+)?)\s*(mi|km)\b""", RegexOption.IGNORE_CASE)

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
    modifier: Modifier = Modifier,
    iconShape: Shape = RoundedCornerShape(4.dp),
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val maneuver = activity.maneuverIcon
    val appIcon = activity.appIcon

    when {
        activity.kind == NotificationActivityKind.NAVIGATION && maneuver != null -> {
            Image(
                bitmap = remember(maneuver) { maneuver.asImageBitmap() },
                contentDescription = activity.title.ifBlank { activity.appName },
                modifier = modifier.clip(iconShape),
                contentScale = ContentScale.Fit,
            )
        }
        activity.kind == NotificationActivityKind.SCREENSHOT && appIcon != null -> {
            Image(
                bitmap = remember(appIcon) { appIcon.asImageBitmap() },
                contentDescription = activity.title,
                modifier = modifier.clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        activity.kind == NotificationActivityKind.PROGRESS && !isCertainDownload(activity) && appIcon != null -> {
            Image(
                bitmap = remember(appIcon) { appIcon.asImageBitmap() },
                contentDescription = activity.appName,
                modifier = modifier.clip(iconShape),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            val tint = when (activity.kind) {
                NotificationActivityKind.SOUND_MODE -> when {
                    activity.title.contains("Silent", ignoreCase = true) -> Color(0xFFFF5252)
                    activity.title.contains("Vibrate", ignoreCase = true) -> Color(0xFFFFA000)
                    else -> Color(0xFF4CAF50)
                }
                NotificationActivityKind.DND -> {
                    if (activity.title.contains("Off", ignoreCase = true)) Color(0xFF9E9E9E)
                    else Color(0xFF9C7CF8)
                }
                NotificationActivityKind.VOLUME -> {
                    if ((activity.progress ?: 1) <= 0) Color(0xFFFF5252) else primaryColor
                }
                NotificationActivityKind.BLUETOOTH -> {
                    val isDisconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
                    if (isDisconnected) primaryColor.copy(alpha = 0.45f) else primaryColor
                }
                else -> activityIconTint(activity.kind, primaryColor)
            }
            val description = when (activity.kind) {
                NotificationActivityKind.BLUETOOTH,
                NotificationActivityKind.AUDIO_OUTPUT,
                NotificationActivityKind.SCREENSHOT,
                NotificationActivityKind.DND,
                NotificationActivityKind.VOLUME,
                -> activity.title
                else -> activity.appName
            }
            Icon(
                imageVector = activityIcon(activity),
                contentDescription = description,
                tint = tint,
                modifier = modifier,
            )
        }
    }
}

/**
 * Returns true if the activity has status content to display on the right wing of the minimized island
 * (such as a progress percentage, chronometer countdown, or active call duration).
 */
fun hasCompactStatusContent(activity: NotificationActivityInfo?): Boolean {
    if (activity == null) return false
    if (activity.pairedEarbudBatteryLevels != null) return true
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
        for (match in PERCENT_CHAR_REGEX.findAll(status)) {
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

private val compactActivityIconSize = 18.dp
private val compactActivityOuterPadding = 8.dp
private val compactActivityIconWing = compactActivityIconSize + compactActivityOuterPadding

/** Size both wings to whichever needs more space, including the padding used by the renderer. */
fun calculateActivityCompactExtraDp(statusTextWidthDp: Float): Float =
    maxOf(compactActivityIconWing.value, statusTextWidthDp + compactActivityOuterPadding.value) * 2f

/** Give the icon and ETA the same space around the camera, including the ETA's padding. */
internal fun compactNavigationExtraDp(statusTextWidthDp: Float): Float =
    (statusTextWidthDp + compactActivityOuterPadding.value).coerceIn(compactActivityIconWing.value, 68f) * 2f

@Composable
internal fun rememberCompactActivityExtraDp(
    activity: NotificationActivityInfo?,
    status: String?,
    cutoutDiameter: Dp,
    progressOutline: Boolean,
): Dp {
    val isNavigation = activity?.kind == NotificationActivityKind.NAVIGATION
    val hasGauge = activityRadialProgress(activity, progressOutline, MaterialTheme.colorScheme.primary) != null
    val extra = when {
        activity?.pairedEarbudBatteryLevels != null -> compactRadialGaugeExtraDp(cutoutDiameter)
        hasGauge -> maxOf(compactActivityIconWing * 2f, compactRadialGaugeExtraDp(cutoutDiameter))
        else -> {
            val textWidth = rememberCompactStatusTextWidthDp(status, compactFontSize = isNavigation).value
            if (isNavigation) compactNavigationExtraDp(textWidth).dp else calculateActivityCompactExtraDp(textWidth).dp
        }
    }
    val maxExtra = (LocalConfiguration.current.screenWidthDp.dp - cutoutDiameter - 32.dp)
        .coerceAtLeast(compactActivityIconWing * 2f)
    return extra.coerceAtMost(maxExtra)
}

@Composable
private fun compactActivityStatusStyle(status: String, compactFontSize: Boolean): TextStyle {
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeightValue by OverlayPreferences.islandFontWeightFlow.collectAsState()
    return MaterialTheme.typography.labelSmall.copy(
        fontSize = if (compactFontSize || status.contains('%')) 10.5.sp else 11.5.sp,
        fontFamily = fontStyle.fontFamily,
        fontWeight = FontWeight(fontWeightValue),
    )
}

@Composable
internal fun rememberCompactStatusTextWidthDp(
    status: String?,
    compactFontSize: Boolean = false,
): Dp {
    if (status.isNullOrEmpty()) return 0.dp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = compactActivityStatusStyle(status, compactFontSize)
    val normalizedStatus = remember(status) {
        status.map { if (it.isDigit()) '0' else it }.joinToString("")
    }
    return remember(normalizedStatus, density, style, textMeasurer) {
        val formatted = formatCompactStatus(normalizedStatus)
        val result = textMeasurer.measure(
            text = formatted,
            style = style,
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

@Composable
internal fun CompactNotificationActivityContent(
    activity: NotificationActivityInfo,
    cutoutDiameterDp: Dp,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    trailingWingFraction: Float = 0.5f,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
) {
    activity.pairedEarbudBatteryLevels?.let { levels ->
        CompactEarbudBatteryContent(
            levels = levels,
            thickness = cutoutDiameterDp,
            centerGap = cutoutDiameterDp,
            isLandscape = isLandscape,
            modifier = modifier,
            leftAnchor = iconAnchor,
            rightAnchor = progressAnchor,
        )
        return
    }
    val status = rememberCompactActivityStatus(activity)
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val radialProgress = activityRadialProgress(activity, activitySettings.progressOutline, primaryColor)

    val icon = @Composable {
        ActivityIcon(
            activity = activity,
            modifier = Modifier.size(compactActivityIconSize).morphingElement(iconAnchor),
        )
    }

    val isPercentage = status.contains('%')
    val isNavigation = activity.kind == NotificationActivityKind.NAVIGATION
    val statusStyle = compactActivityStatusStyle(status, compactFontSize = isNavigation)
    val formattedStatus = remember(status) { formatCompactStatus(status) }

    val summary = @Composable {
        Text(
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            text = formattedStatus,
            color = Color.White,
            style = statusStyle,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }

    val compactRingDiameter = compactBatteryRingDiameter(cutoutDiameterDp)
    val compactRingInset = compactEndInset(cutoutDiameterDp, compactRingDiameter)

    if (isLandscape) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = compactActivityOuterPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = if (radialProgress != null) compactRingInset else compactActivityOuterPadding),
                contentAlignment = if (radialProgress != null) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    RadialProgressGauge(
                        progress = percent / 100f,
                        text = percent.toString(),
                        color = ringColor,
                        diameter = compactRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
                    )
                } else if (status.isNotEmpty()) {
                    Text(
                        text = formattedStatus,
                        color = Color.White,
                        style = statusStyle.copy(fontSize = if (isPercentage) 9.5.sp else 10.sp),
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
                    .padding(start = compactActivityOuterPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(trailingWeight)
                    .fillMaxHeight()
                    .padding(end = if (radialProgress != null) compactRingInset else compactActivityOuterPadding),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (radialProgress != null) {
                    val (percent, ringColor) = radialProgress
                    RadialProgressGauge(
                        progress = percent / 100f,
                        text = percent.toString(),
                        color = ringColor,
                        diameter = compactRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
                    )
                } else if (status.isNotEmpty()) {
                    summary()
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
    NotificationActivityKind.DND -> LarpIcons.DoNotDisturbOn
    NotificationActivityKind.VOLUME -> LarpIcons.VolumeUp
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
    NotificationActivityKind.DND -> LarpIcons.DoNotDisturbOn
    NotificationActivityKind.VOLUME -> if ((activity.progress ?: 1) <= 0) LarpIcons.VolumeOff else LarpIcons.VolumeUp
    NotificationActivityKind.BLUETOOTH -> bluetoothDeviceIcon(activity)
    NotificationActivityKind.AUDIO_OUTPUT -> audioOutputActivityIcon(activity)
    NotificationActivityKind.SCREENSHOT -> LarpIcons.Screenshot
}

internal fun bluetoothDeviceIcon(activity: NotificationActivityInfo): ImageVector {
    val type = activity.bluetoothDeviceType.orEmpty().lowercase()
    val name = activity.title.lowercase()
    val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
    val levels = activity.earbudBatteryLevels
    val matched = when {
        !disconnected && levels?.left != null && levels.right == null -> LarpIcons.LeftEarbud
        !disconnected && levels?.right != null && levels.left == null -> LarpIcons.RightEarbud
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
    NotificationActivityKind.DND -> Color(0xFF9C7CF8)
    NotificationActivityKind.VOLUME,
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
        .replace(NAV_COMPOUND_DURATION_REGEX, "$1h$2m")
        .replace(NAV_MINUTES_REGEX, "$1m")
        .replace(NAV_HOURS_REGEX, "$1h")
        .replace(NAV_MILES_REGEX, "$1mi")
        .replace(NAV_DISTANCE_SPACE_REGEX, "$1$2")
        .trim()
}

internal fun compactStatusFallback(activity: NotificationActivityInfo): String {
    if (activity.kind == NotificationActivityKind.SOUND_MODE || activity.kind == NotificationActivityKind.DND) return ""
    if (activity.kind == NotificationActivityKind.BLUETOOTH) {
        return batteryPercentOf(activity)?.let { "$it%" }.orEmpty()
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
        NotificationActivityKind.DND -> ""
        NotificationActivityKind.VOLUME -> ""
        NotificationActivityKind.BLUETOOTH -> ""
        NotificationActivityKind.AUDIO_OUTPUT -> activity.title
        NotificationActivityKind.SCREENSHOT -> "Share"
    }
}

private fun findPercentage(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val match = PERCENT_VALUE_REGEX.find(text) ?: return null
    val value = match.groupValues[1].toIntOrNull() ?: return null
    return if (value in 0..100) "$value%" else null
}

private fun findShortEta(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val compoundMatch = COMPOUND_ETA_REGEX.find(text)
    if (compoundMatch != null) return compoundMatch.value.trim()

    val timeMatch = SHORT_TIME_UNIT_REGEX.find(text)
    if (timeMatch != null) return timeMatch.value.trim()

    val distanceMatch = SHORT_DISTANCE_REGEX.find(text)
    if (distanceMatch != null) return distanceMatch.value.trim()

    return null
}

private fun findShortTime(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val match = CLOCK_TIME_REGEX.find(text) ?: return null
    return match.value
}
