package ca.saboor.larpdot.ui.overlay

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.OverlayPreferences
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

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
    modifier: Modifier = Modifier,
    iconShape: Shape = RoundedCornerShape(4.dp),
) {
    val isDownload = isCertainDownload(activity)
    val appIcon = activity.appIcon
    val primaryColor = MaterialTheme.colorScheme.primary

    val iconModifier = modifier.size(18.dp)

    when (activity.kind) {
        NotificationActivityKind.CALL -> {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.TIMER -> {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.NAVIGATION -> {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = activity.appName,
                tint = activityIconTint(activity.kind, primaryColor),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.BATTERY_CHARGING -> {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = activity.appName,
                tint = Color(0xFF4CAF50),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.BATTERY_LOW -> {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = activity.appName,
                tint = Color(0xFFFF5252),
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
                imageVector = if (isDisconnected) Icons.Default.BluetoothDisabled else Icons.Default.BluetoothConnected,
                contentDescription = activity.appName,
                tint = if (isDisconnected) Color.White.copy(alpha = 0.6f) else Color(0xFF29B6F6),
                modifier = iconModifier,
            )
        }
        NotificationActivityKind.PROGRESS -> {
            if (isDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
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
                    imageVector = Icons.Default.Notifications,
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

/**
 * Measures status text to calculate dynamic extra width for compact islands
 * (used by live activities, flashlight percentage, etc.).
 */
@Composable
fun rememberCompactStatusExtraDp(
    status: String?,
): Dp {
    if (status.isNullOrEmpty()) return 80.dp

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Digits 0-9 have slightly variable widths in proportional fonts.
    // Replace all digits with '0' for measurement so the island width does not jitter
    // per-second during countdowns or percentages (e.g. 51% -> 52%).
    val normalizedStatus = remember(status) {
        status.map { if (it.isDigit()) '0' else it }.joinToString("")
    }

    val configuredStyle = MaterialTheme.typography.labelSmall
    val measuredTextWidthDp = remember(normalizedStatus, density, configuredStyle) {
        val formatted = formatCompactStatus(normalizedStatus)
        val result = textMeasurer.measure(
            text = formatted,
            style = configuredStyle.copy(
                fontSize = if (normalizedStatus.contains('%')) 10.5.sp else 11.5.sp,
            ),
            softWrap = false,
        )
        with(density) { result.size.width.toDp() }
    }

    return calculateActivityCompactExtraDp(measuredTextWidthDp.value).dp
}

@Composable
fun rememberActivityCompactExtraDp(
    activity: NotificationActivityInfo?,
    status: String?,
): Dp {
    if (activity == null) return 80.dp
    if (status.isNullOrEmpty()) return 48.dp
    return rememberCompactStatusExtraDp(status)
}

@Composable
internal fun CompactNotificationActivityContent(
    activity: NotificationActivityInfo,
    cutoutDiameterDp: Dp,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    val status = rememberCompactActivityStatus(activity)
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()

    val icon = @Composable {
        ActivityIcon(
            activity = activity,
            modifier = Modifier.size(18.dp),
        )
    }

    val isPercentage = status.contains('%')
    val formattedStatus = remember(status) { formatCompactStatus(status) }

    val summary = @Composable {
        Text(
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            text = formattedStatus,
            color = Color.White,
            fontSize = if (isPercentage) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
            fontFamily = fontStyle.fontFamily,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }

    // Centered layout around the camera cutout:
    // Symmetrical left/right (or top/bottom in landscape) wings flank a cutout clearance spacer
    // of exactly cutoutDiameterDp. The wings dynamically expand to fit wider text without truncating.
    // The icon is pinned to the outer edge (left-aligned) while status text sits at the trailing edge.
    if (isLandscape) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (status.isNotEmpty()) {
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
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (status.isNotEmpty()) {
                    summary()
                }
            }
        }
    }
}

internal fun activitySubtitle(activity: NotificationActivityInfo): String = listOfNotNull(
    activity.text?.takeIf { it.isNotBlank() } ?: activity.appName,
    if (activity.kind == NotificationActivityKind.BLUETOOTH)
        activity.bluetoothBatteryPercent?.let { "Battery: $it%" } ?: "Battery unavailable" else null,
).joinToString(" • ")

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

/** Measure the same text styles and wrapping limits used by the activity card. */
@Composable
internal fun expandedActivitySize(activity: NotificationActivityInfo?, maxWidth: Dp, maxHeight: Dp, topInset: Dp): DpSize {
    if (activity == null) return DpSize(maxWidth, 80.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = activityTextStyle(true)
    val subtitleStyle = activityTextStyle(false)
    val buttonStyle = MaterialTheme.typography.labelLarge
    return with(density) {
        val titleNatural = measurer.measure(activity.title, titleStyle, softWrap = false).size.width.toDp()
        val subtitleNatural = measurer.measure(activitySubtitle(activity), subtitleStyle, softWrap = false).size.width.toDp()
        val buttonWidths = activity.actions.take(3).map {
            maxOf(64.dp, measurer.measure(it.title, buttonStyle, softWrap = false).size.width.toDp() + 48.dp)
        }
        val actionsWidth = buttonWidths.fold(0.dp) { sum, width -> sum + width } + (8.dp * (buttonWidths.size - 1).coerceAtLeast(0))
        val width = maxOf(160.dp, maxOf(titleNatural, subtitleNatural) + 78.dp, actionsWidth + 44.dp).coerceAtMost(maxWidth)
        val textWidthPx = (width - 78.dp).coerceAtLeast(1.dp).roundToPx()
        val titleHeight = measurer.measure(activity.title, titleStyle, maxLines = 2,
            constraints = Constraints(maxWidth = textWidthPx)).size.height.toDp()
        val subtitleHeight = measurer.measure(activitySubtitle(activity), subtitleStyle, maxLines = 4,
            constraints = Constraints(maxWidth = textWidthPx)).size.height.toDp()
        var rows = if (buttonWidths.isEmpty()) 0 else 1
        var rowWidth = 0.dp
        buttonWidths.forEach { button ->
            val next = if (rowWidth == 0.dp) button else rowWidth + 8.dp + button
            if (rowWidth > 0.dp && next > width - 44.dp) { rows++; rowWidth = button }
            else rowWidth = next
        }
        val height = maxOf(18.dp, topInset) + 18.dp + maxOf(24.dp, titleHeight + subtitleHeight) +
            (if (activity.progressFraction != null) 20.dp else 0.dp) +
            (if (rows > 0) 14.dp + 48.dp * rows + 8.dp * (rows - 1) else 0.dp)
        DpSize(width, height.coerceAtMost(maxHeight))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedNotificationActivityContent(
    activity: NotificationActivityInfo,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
    topContentInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val openApp = {
        NotificationActivityState.open(context, activity)
        onCollapse()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = openApp,
            )
            .padding(start = 22.dp, end = 22.dp, top = maxOf(18.dp, topContentInset), bottom = 18.dp),
    ) {
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
                    fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value),
                    fontFamily = fontStyle.fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = activitySubtitle(activity)
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

        activity.progressFraction?.let { fraction ->
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

        if (activity.actions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                activity.actions.take(3).forEach { action ->
                    Button(modifier = Modifier.heightIn(min = 48.dp), onClick = { NotificationActivityState.perform(action) }) {
                        Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal fun activityIcon(kind: NotificationActivityKind): ImageVector = when (kind) {
    NotificationActivityKind.PROGRESS -> Icons.Default.Download
    NotificationActivityKind.NAVIGATION -> Icons.Default.Navigation
    NotificationActivityKind.CALL -> Icons.Default.Call
    NotificationActivityKind.TIMER -> Icons.Default.Timer
    NotificationActivityKind.BATTERY_CHARGING -> Icons.Default.BatteryChargingFull
    NotificationActivityKind.BATTERY_LOW -> Icons.Default.BatteryAlert
    NotificationActivityKind.SOUND_MODE -> Icons.Default.VolumeUp
    NotificationActivityKind.BLUETOOTH -> Icons.Default.BluetoothConnected
}

internal fun activityIcon(activity: NotificationActivityInfo): ImageVector = when (activity.kind) {
    NotificationActivityKind.PROGRESS -> if (isCertainDownload(activity)) Icons.Default.Download else Icons.Default.Notifications
    NotificationActivityKind.NAVIGATION -> Icons.Default.Navigation
    NotificationActivityKind.CALL -> Icons.Default.Call
    NotificationActivityKind.TIMER -> Icons.Default.Timer
    NotificationActivityKind.BATTERY_CHARGING -> Icons.Default.BatteryChargingFull
    NotificationActivityKind.BATTERY_LOW -> Icons.Default.BatteryAlert
    NotificationActivityKind.SOUND_MODE -> when {
        activity.title.contains("Silent", ignoreCase = true) || activity.text?.contains("Silent", ignoreCase = true) == true -> Icons.Default.VolumeOff
        activity.title.contains("Vibrate", ignoreCase = true) || activity.text?.contains("Vibrate", ignoreCase = true) == true -> Icons.Default.Vibration
        else -> Icons.Default.VolumeUp
    }
    NotificationActivityKind.BLUETOOTH -> when {
        activity.text?.contains("Disconnected", ignoreCase = true) == true -> Icons.Default.BluetoothDisabled
        else -> Icons.Default.BluetoothConnected
    }
}

internal fun activityIconTint(kind: NotificationActivityKind, fallback: Color): Color = when (kind) {
    NotificationActivityKind.CALL -> Color(0xFF4CAF50)
    NotificationActivityKind.TIMER -> Color(0xFFFF9800)
    NotificationActivityKind.NAVIGATION -> Color(0xFF42A5F5)
    NotificationActivityKind.BATTERY_CHARGING -> Color(0xFF4CAF50)
    NotificationActivityKind.BATTERY_LOW -> Color(0xFFFF5252)
    NotificationActivityKind.SOUND_MODE -> Color(0xFFFF9800)
    NotificationActivityKind.BLUETOOTH -> Color(0xFF29B6F6)
    NotificationActivityKind.PROGRESS -> fallback
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
        chronometer ?: compactStatusFallback(activity)
    }
}

internal fun compactStatusFallback(activity: NotificationActivityInfo): String {
    if (activity.kind == NotificationActivityKind.SOUND_MODE) return ""
    if (activity.kind == NotificationActivityKind.BLUETOOTH && activity.bluetoothDeviceType != null) {
        val disconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
        return if (disconnected) "${activity.bluetoothDeviceType} • Disconnected"
            else listOfNotNull(activity.bluetoothDeviceType, activity.bluetoothBatteryPercent?.let { "$it%" }).joinToString(" • ")
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
        NotificationActivityKind.BLUETOOTH -> when {
            activity.text?.contains("Disconnected", ignoreCase = true) == true -> "Disconnected"
            else -> "Connected"
        }
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
