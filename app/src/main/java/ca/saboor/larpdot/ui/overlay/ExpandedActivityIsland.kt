package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityAction
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.service.AudioOutputTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.ui.theme.LarpIcons

private val LEADING_BATTERY_PERCENT_REGEX = Regex("""^\d+%(?:\s+remaining|\s+reached)?\s*•\s*""", RegexOption.IGNORE_CASE)
private val PURE_PERCENT_REGEX = Regex("""^\d+%$""")

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
        if (PURE_PERCENT_REGEX.matches(stripped)) return ""
        return stripped
    }
    return raw
}

@Composable
internal fun activityTextStyle(title: Boolean): TextStyle {
    val family by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val weight by OverlayPreferences.islandFontWeightFlow.collectAsState()
    return MaterialTheme.typography.bodyMedium.copy(
        fontFamily = family.fontFamily,
        fontWeight = FontWeight(weight),
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
        val titleHeight = measurer.measure(
            activity.title,
            titleStyle,
            maxLines = titleMaxLines,
            constraints = Constraints(maxWidth = textWidthPx),
        ).size.height.toDp()
        val subtitleHeight = if (subtitle.isNotEmpty()) {
            measurer.measure(
                subtitle,
                subtitleStyle,
                maxLines = 4,
                constraints = Constraints(maxWidth = textWidthPx),
            ).size.height.toDp()
        } else 0.dp
        val textHeight = titleHeight + subtitleHeight + (if (isNavigation) 4.dp else 0.dp)
        var rows = if (buttonWidths.isEmpty()) 0 else 1
        var rowWidth = 0.dp
        buttonWidths.forEach { button ->
            val next = if (rowWidth == 0.dp) button else rowWidth + 8.dp + button
            if (rowWidth > 0.dp && next > width - 44.dp) {
                rows++
                rowWidth = button
            } else {
                rowWidth = next
            }
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
