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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import ca.saboor.larpdot.service.QuickTimerManager
import ca.saboor.larpdot.service.QuickTimerMode
import ca.saboor.larpdot.service.QuickTimerState
import ca.saboor.larpdot.service.ScreenshotPocketManager
import ca.saboor.larpdot.service.SystemEventManager
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.roundToInt

private val LEADING_BATTERY_PERCENT_REGEX = Regex("""^\d+%(?:\s+remaining|\s+reached)?\s*•\s*""", RegexOption.IGNORE_CASE)
private val PURE_PERCENT_REGEX = Regex("""^\d+%$""")

private val earbudTitleMinWidth = 56.dp

private fun expandedActivityGaugeDiameter(spanIconLeftOfCutout: Boolean): Dp =
    if (spanIconLeftOfCutout) 44.dp else 42.dp

private fun earbudCardChrome(spanIconLeftOfCutout: Boolean): Dp =
    if (spanIconLeftOfCutout) 116.dp else 90.dp

private fun shouldStackEarbudGauges(cardWidth: Dp, spanIconLeftOfCutout: Boolean): Boolean {
    val diameter = expandedActivityGaugeDiameter(spanIconLeftOfCutout)
    return cardWidth < earbudCardChrome(spanIconLeftOfCutout) +
        expandedEarbudPairSize(diameter, stacked = false).width + earbudTitleMinWidth
}

internal fun activitySubtitle(activity: NotificationActivityInfo): String {
    if (activity.kind == NotificationActivityKind.BLUETOOTH) {
        val isDisconnected = activity.text?.contains("Disconnected", ignoreCase = true) == true
        if (isDisconnected) return "Disconnected"
        val deviceType = activity.bluetoothDeviceType
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("Bluetooth", ignoreCase = true) &&
                    !it.equals("Earbuds", ignoreCase = true) &&
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

private data class ExpandedActivityLayoutModel(
    val subtitle: String,
    val isNavigation: Boolean,
    val radialProgress: Pair<Int, Color>?,
    val hasRadialGauge: Boolean,
    val navigationEta: String?,
    val inlineAction: NotificationActivityAction?,
    val footerActions: List<NotificationActivityAction>,
    val hasNavRightContent: Boolean,
    val isScreenshot: Boolean,
    val isVolume: Boolean,
    val isSoundMode: Boolean,
    val isQuickTimer: Boolean,
    val showProgressBar: Boolean,
    val hasFooter: Boolean,
) {
    val bottomPadding: Dp get() = if (isNavigation) 14.dp else 22.dp
}

private fun buildExpandedActivityLayoutModel(
    activity: NotificationActivityInfo,
    progressOutlineEnabled: Boolean,
    primaryColor: Color,
): ExpandedActivityLayoutModel {
    val isNavigation = activity.kind == NotificationActivityKind.NAVIGATION
    val isVolume = activity.kind == NotificationActivityKind.VOLUME
    val isSoundMode = activity.kind == NotificationActivityKind.SOUND_MODE
    val isQuickTimer = activity.key == QuickTimerManager.ACTIVITY_KEY
    val radialProgress = activityRadialProgress(activity, progressOutlineEnabled, primaryColor)
    val hasRadialGauge = radialProgress != null
    val navigationEta = if (isNavigation) compactStatusFallback(activity).takeIf { it.isNotBlank() } else null
    val inlineAction = inlineNavigationAction(activity)
    val footerActions = if (inlineAction != null) {
        activity.actions.filterNot { it == inlineAction }.take(3)
    } else {
        activity.actions.take(3)
    }
    val isScreenshot = activity.kind == NotificationActivityKind.SCREENSHOT &&
        (activity.mediaUri != null || !activity.heldText.isNullOrBlank())
    val showProgressBar = !isNavigation && !isVolume && activity.progressFraction != null && !hasRadialGauge
    val hasFooter = showProgressBar || isScreenshot || isVolume || isSoundMode || isQuickTimer || footerActions.isNotEmpty()
    return ExpandedActivityLayoutModel(
        subtitle = activitySubtitle(activity),
        isNavigation = isNavigation,
        radialProgress = radialProgress,
        hasRadialGauge = hasRadialGauge,
        navigationEta = navigationEta,
        inlineAction = inlineAction,
        footerActions = footerActions,
        hasNavRightContent = navigationEta != null || inlineAction != null,
        isScreenshot = isScreenshot,
        isVolume = isVolume,
        isSoundMode = isSoundMode,
        isQuickTimer = isQuickTimer,
        showProgressBar = showProgressBar,
        hasFooter = hasFooter,
    )
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
    val model = buildExpandedActivityLayoutModel(activity, activitySettings.progressOutline, primaryColor)
    val titleStyle = activityTextStyle(true)
    val subtitleStyle = activityTextStyle(false)
    val buttonStyle = MaterialTheme.typography.labelLarge
    val etaStyle = subtitleStyle.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val hasEarbudLevels = activity.pairedEarbudBatteryLevels != null
    val stackEarbudGauges = hasEarbudLevels && shouldStackEarbudGauges(maxWidth, spanIconLeftOfCutout)
    val gaugeDiameter = expandedActivityGaugeDiameter(spanIconLeftOfCutout)
    val earbudLevelsSize = if (hasEarbudLevels) expandedEarbudPairSize(gaugeDiameter, stackEarbudGauges) else DpSize.Zero
    return with(density) {
        val titleNatural = measurer.measure(activity.title, titleStyle, softWrap = false).size.width.toDp()
        val subtitleNatural = if (model.subtitle.isNotEmpty()) {
            measurer.measure(model.subtitle, subtitleStyle, softWrap = false).size.width.toDp()
        } else 0.dp
        val etaWidth = model.navigationEta?.let {
            measurer.measure(it, etaStyle, softWrap = false).size.width.toDp()
        } ?: 0.dp
        val inlineButtonWidth = model.inlineAction?.let {
            maxOf(64.dp, measurer.measure(it.title, buttonStyle, softWrap = false).size.width.toDp() + 32.dp)
        } ?: 0.dp
        val navRightWidth = maxOf(etaWidth, inlineButtonWidth)
        val etaHeight = model.navigationEta?.let {
            measurer.measure(it, etaStyle, maxLines = 1).size.height.toDp()
        } ?: 0.dp
        val actionHeight = if (model.inlineAction != null) {
            maxOf(48.dp, measurer.measure(model.inlineAction.title, buttonStyle, maxLines = 1).size.height.toDp() + 12.dp)
        } else 0.dp
        val navRightHeight = etaHeight + actionHeight +
            (if (etaHeight > 0.dp && actionHeight > 0.dp) 6.dp else 0.dp) +
            (if (spanIconLeftOfCutout && model.hasNavRightContent) 16.dp else 0.dp)
        val buttonWidths = model.footerActions.map {
            maxOf(64.dp, measurer.measure(it.title, buttonStyle, softWrap = false).size.width.toDp() + 48.dp)
        }
        val actionsWidth = buttonWidths.fold(0.dp) { sum, width -> sum + width } + (8.dp * (buttonWidths.size - 1).coerceAtLeast(0))
        val horizontalChrome = when {
            hasEarbudLevels -> earbudCardChrome(spanIconLeftOfCutout) + earbudLevelsSize.width
            spanIconLeftOfCutout && model.hasRadialGauge -> 160.dp
            spanIconLeftOfCutout && navRightWidth > 0.dp -> 116.dp + navRightWidth
            spanIconLeftOfCutout -> 100.dp
            model.hasRadialGauge -> 134.dp
            navRightWidth > 0.dp -> 92.dp + navRightWidth
            else -> 78.dp
        }
        val minCardWidth = if (spanIconLeftOfCutout) {
            maxOf(184.dp, 152.dp + cutoutDiameterDp)
        } else {
            160.dp
        }
        val rightSlotHeight = maxOf(navRightHeight, earbudLevelsSize.height)
        val textNaturalWidth = maxOf(titleNatural, subtitleNatural, if (hasEarbudLevels) earbudTitleMinWidth else 0.dp)
        val width = maxOf(minCardWidth, textNaturalWidth + horizontalChrome, actionsWidth + 44.dp).coerceAtMost(maxWidth)
        val textWidthPx = (width - horizontalChrome).coerceAtLeast(1.dp).roundToPx()
        val titleMaxLines = if (model.isNavigation) 3 else 2
        val titleHeight = measurer.measure(
            activity.title,
            titleStyle,
            maxLines = titleMaxLines,
            constraints = Constraints(maxWidth = textWidthPx),
        ).size.height.toDp()
        val subtitleHeight = if (model.subtitle.isNotEmpty()) {
            measurer.measure(
                model.subtitle,
                subtitleStyle,
                maxLines = 4,
                constraints = Constraints(maxWidth = textWidthPx),
            ).size.height.toDp()
        } else 0.dp
        val textHeight = titleHeight + subtitleHeight
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
        val previewHeight = if (model.isScreenshot && activity.appIcon != null) 118.dp else 0.dp
        val pocketActionHeight = if (model.isScreenshot) 56.dp else 0.dp
        val volumeSectionHeight = if (model.isVolume) 56.dp else 0.dp
        val soundModeSectionHeight = if (model.isSoundMode) 54.dp else 0.dp
        val quickTimerSectionHeight = if (model.isQuickTimer) 56.dp else 0.dp
        val bottomPadding = model.bottomPadding
        val headerBlockHeight = if (spanIconLeftOfCutout) {
            if (model.hasFooter) {
                maxOf(48.dp, rightSlotHeight, topInset + textHeight) + bottomPadding
            } else {
                maxOf(64.dp, rightSlotHeight, topInset + textHeight + bottomPadding)
            }
        } else {
            (if (topInset > 0.dp) topInset else 16.dp) + maxOf(
                if (model.hasRadialGauge) 44.dp else 24.dp,
                rightSlotHeight,
                textHeight,
            ) + bottomPadding
        }
        val height = headerBlockHeight +
            (if (model.showProgressBar) 20.dp else 0.dp) +
            previewHeight + pocketActionHeight + volumeSectionHeight + soundModeSectionHeight + quickTimerSectionHeight +
            (if (rows > 0) 14.dp + 48.dp * rows + 8.dp * (rows - 1) else 0.dp)
        val effectiveWidth = if (model.isScreenshot || model.isVolume || model.isSoundMode || model.isQuickTimer) {
            maxOf(width, 280.dp).coerceAtMost(maxWidth)
        } else {
            width
        }
        DpSize(effectiveWidth, height.coerceAtMost(maxHeight))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedNotificationActivityContent(
    activity: NotificationActivityInfo,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit = {},
    topContentInset: Dp = 0.dp,
    spanIconLeftOfCutout: Boolean = false,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
) {
    val context = LocalContext.current
    val fontStyle by OverlayPreferences.islandFontStyleFlow.collectAsState()
    val fontWeight = FontWeight(OverlayPreferences.islandFontWeightFlow.collectAsState().value)
    val openApp = {
        when {
            activity.kind == NotificationActivityKind.SCREENSHOT && activity.mediaUri != null -> {
                ScreenshotPocketManager.openItem(context, activity.mediaUri, activity.mediaMimeType)
            }
            activity.kind == NotificationActivityKind.SCREENSHOT && !activity.heldText.isNullOrBlank() -> {
                val url = ScreenshotPocketManager.extractFirstHttpUrl(activity.heldText)
                if (url != null) ScreenshotPocketManager.openUrl(context, url)
                else ScreenshotPocketManager.copyText(context, activity.heldText)
            }
            activity.kind == NotificationActivityKind.AUDIO_OUTPUT || activity.kind == NotificationActivityKind.BLUETOOTH -> {
                AudioOutputTracker.openOutputSwitcher(context)
            }
            activity.kind == NotificationActivityKind.DND -> {
                SystemEventManager.openDoNotDisturbSettings(context)
            }
            activity.kind == NotificationActivityKind.VOLUME -> {
                SystemEventManager.openSoundSettings(context)
            }
            else -> NotificationActivityState.open(context, activity)
        }
        onCollapse()
    }

    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val model = buildExpandedActivityLayoutModel(activity, activitySettings.progressOutline, primaryColor)
    val startPadding = if (spanIconLeftOfCutout) 18.dp else 22.dp
    val hasTrailingContent = model.hasRadialGauge || model.hasNavRightContent || activity.pairedEarbudBatteryLevels != null
    val endPadding = if (spanIconLeftOfCutout) (if (hasTrailingContent) 18.dp else 20.dp) else 22.dp
    val bottomPadding = model.bottomPadding
    val columnTopPadding = when {
        spanIconLeftOfCutout -> 0.dp
        topContentInset > 0.dp -> topContentInset
        else -> 16.dp
    }
    val columnBottomPadding = if (spanIconLeftOfCutout && !model.hasFooter) 0.dp else bottomPadding

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
        ExpandedActivityHeaderRow(
            activity = activity,
            model = model,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            primaryColor = primaryColor,
            topContentInset = topContentInset,
            bottomPadding = bottomPadding,
            spanIconLeftOfCutout = spanIconLeftOfCutout,
            stackEarbudGauges = activity.pairedEarbudBatteryLevels != null &&
                shouldStackEarbudGauges(cardWidth, spanIconLeftOfCutout),
            iconAnchor = iconAnchor,
            progressAnchor = progressAnchor,
        )

        if (model.showProgressBar) activity.progressFraction?.let { fraction ->
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

        if (model.isScreenshot) {
            ExpandedScreenshotSection(
                activity = activity,
                onOpen = openApp,
                onCollapse = onCollapse,
            )
        }

        if (model.isVolume) {
            ExpandedVolumeSection(activity = activity)
        }

        if (model.isSoundMode) {
            ExpandedSoundModeSection(activity = activity)
        }

        if (model.isQuickTimer) {
            ExpandedQuickTimerSection(onCollapse = onCollapse)
        }

        if (model.footerActions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                model.footerActions.forEach { action ->
                    Button(modifier = Modifier.heightIn(min = 48.dp), onClick = { NotificationActivityState.perform(action) }) {
                        Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedActivityHeaderRow(
    activity: NotificationActivityInfo,
    model: ExpandedActivityLayoutModel,
    fontStyle: OverlayPreferences.IslandFontStyle,
    fontWeight: FontWeight,
    primaryColor: Color,
    topContentInset: Dp,
    bottomPadding: Dp,
    spanIconLeftOfCutout: Boolean,
    stackEarbudGauges: Boolean,
    iconAnchor: IslandElementAnchor?,
    progressAnchor: IslandElementAnchor?,
) {
    val rowModifier = if (spanIconLeftOfCutout) {
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (!model.hasFooter) Modifier.heightIn(min = 64.dp) else Modifier)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(if (spanIconLeftOfCutout) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val earbudLevels = activity.pairedEarbudBatteryLevels
        val appIconAnchor = if (earbudLevels == null) iconAnchor else null
        if (spanIconLeftOfCutout) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                ActivityIcon(
                    activity = activity,
                    modifier = Modifier.size(44.dp).morphingElement(appIconAnchor),
                    iconShape = RoundedCornerShape(12.dp),
                )
            }
        } else {
            ActivityIcon(
                activity = activity,
                modifier = Modifier.size(24.dp).morphingElement(appIconAnchor),
                iconShape = RoundedCornerShape(6.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (spanIconLeftOfCutout) {
                        Modifier.padding(
                            top = topContentInset,
                            bottom = if (model.hasFooter) 0.dp else bottomPadding,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                text = activity.title,
                color = Color.White,
                style = activityTextStyle(true),
                fontWeight = fontWeight,
                fontFamily = fontStyle.fontFamily,
                maxLines = if (model.isNavigation) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.subtitle.isNotEmpty()) {
                Text(
                    text = model.subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    style = activityTextStyle(false),
                    fontFamily = fontStyle.fontFamily,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val gaugeDiameter = expandedActivityGaugeDiameter(spanIconLeftOfCutout)
        if (earbudLevels != null) {
            ExpandedEarbudBatteryContent(
                levels = earbudLevels,
                diameter = gaugeDiameter,
                stacked = stackEarbudGauges,
                leftAnchor = iconAnchor,
                rightAnchor = progressAnchor,
                modifier = Modifier.padding(start = 2.dp, end = if (spanIconLeftOfCutout) 2.dp else 0.dp),
            )
        } else if (model.radialProgress != null) {
            val (percent, ringColor) = model.radialProgress
            if (spanIconLeftOfCutout) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpandedActivityGauge(
                        percent = percent,
                        color = ringColor,
                        diameter = gaugeDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
                    )
                }
            } else {
                Spacer(Modifier.width(2.dp))
                ExpandedActivityGauge(
                    percent = percent,
                    color = ringColor,
                    diameter = gaugeDiameter,
                    modifier = Modifier.morphingElement(progressAnchor),
                )
            }
        } else if (model.hasNavRightContent) {
            ExpandedNavigationTrailingSlot(
                navigationEta = model.navigationEta,
                inlineAction = model.inlineAction,
                primaryColor = primaryColor,
                fontStyle = fontStyle,
                modifier = if (spanIconLeftOfCutout) {
                    Modifier.fillMaxHeight().padding(vertical = 8.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun ExpandedNavigationTrailingSlot(
    navigationEta: String?,
    inlineAction: NotificationActivityAction?,
    primaryColor: Color,
    fontStyle: OverlayPreferences.IslandFontStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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

@Composable
private fun ExpandedScreenshotSection(
    activity: NotificationActivityInfo,
    onOpen: () -> Unit,
    onCollapse: () -> Unit,
) {
    val context = LocalContext.current
    val mediaUri = activity.mediaUri
    val heldText = activity.heldText?.takeIf { it.isNotBlank() }
    if (mediaUri == null && heldText != null) {
        val url = remember(heldText) { ScreenshotPocketManager.extractFirstHttpUrl(heldText) }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    ScreenshotPocketManager.copyText(context, heldText)
                    onCollapse()
                },
                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
            ) {
                Icon(LarpIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (url != null) {
                FilledTonalButton(
                    onClick = {
                        ScreenshotPocketManager.openUrl(context, url)
                        onCollapse()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = {
                        ScreenshotPocketManager.shareText(context, heldText)
                        onCollapse()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        ScreenshotPocketManager.shareText(context, heldText)
                        onCollapse()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalButton(
                onClick = {
                    ScreenshotPocketManager.dismissHeldItem(activity.key)
                    onCollapse()
                },
                modifier = Modifier.heightIn(min = 42.dp),
            ) {
                Icon(LarpIcons.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
            }
        }
        return
    }
    if (mediaUri == null) return
    val previewBitmap = remember(activity.appIcon) { activity.appIcon?.asImageBitmap() }
    if (previewBitmap != null) {
        Spacer(Modifier.height(12.dp))
        Image(
            bitmap = previewBitmap,
            contentDescription = activity.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen),
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
                ScreenshotPocketManager.shareItem(context, mediaUri, activity.mediaMimeType)
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
                ScreenshotPocketManager.copyItem(context, mediaUri)
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
                ScreenshotPocketManager.deleteItem(context, activity.key, mediaUri)
                onCollapse()
            },
            modifier = Modifier.heightIn(min = 42.dp),
        ) {
            Icon(LarpIcons.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedVolumeSection(activity: NotificationActivityInfo) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val reportedPercent = (activity.progress ?: 0).coerceIn(0, 100)
    val sliderState = remember { SliderState(value = reportedPercent.toFloat(), trackRange = 0f..100f) }
    sliderState.value = reportedPercent.toFloat()
    val isMuted = reportedPercent <= 0

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalButton(
            onClick = {
                haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                SystemEventManager.toggleMediaMute(context)
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Icon(
                imageVector = if (isMuted) LarpIcons.VolumeOff else LarpIcons.VolumeUp,
                contentDescription = if (isMuted) "Unmute media" else "Mute media",
                modifier = Modifier.size(18.dp),
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { raw ->
                val snapped = raw.roundToInt().coerceIn(0, 100)
                sliderState.value = snapped.toFloat()
                SystemEventManager.setMediaVolumePercent(context, snapped)
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
    }
}

@Composable
private fun ExpandedSoundModeSection(activity: NotificationActivityInfo) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activeMode = parseSoundModeFromTitle(activity.title)
    val choices = listOf(
        Triple(android.media.AudioManager.RINGER_MODE_NORMAL, "Ring", LarpIcons.VolumeUp),
        Triple(android.media.AudioManager.RINGER_MODE_VIBRATE, "Vibrate", LarpIcons.Vibration),
        Triple(android.media.AudioManager.RINGER_MODE_SILENT, "Silent", LarpIcons.VolumeOff),
    )
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choices.forEach { (mode, label, icon) ->
            val selected = activeMode == mode
            val onClick = {
                haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                SystemEventManager.setRingerMode(context, mode)
                Unit
            }
            if (selected) {
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun parseSoundModeFromTitle(title: String): Int = when {
    title.contains("Silent", ignoreCase = true) -> android.media.AudioManager.RINGER_MODE_SILENT
    title.contains("Vibrate", ignoreCase = true) -> android.media.AudioManager.RINGER_MODE_VIBRATE
    else -> android.media.AudioManager.RINGER_MODE_NORMAL
}

@Composable
private fun ExpandedQuickTimerSection(onCollapse: () -> Unit) {
    val timerState by QuickTimerManager.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    if (timerState is QuickTimerState.Idle) return

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val state = timerState) {
            is QuickTimerState.Running -> {
                Button(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                        QuickTimerManager.togglePause()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pause", maxLines = 1)
                }
                if (state.mode == QuickTimerMode.COUNTDOWN) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                            QuickTimerManager.addOneMinute()
                        },
                        modifier = Modifier.heightIn(min = 42.dp),
                    ) {
                        Text("+1m", maxLines = 1)
                    }
                }
                FilledTonalButton(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.LongPress)
                        QuickTimerManager.stop()
                        onCollapse()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Stop, contentDescription = "Stop timer", modifier = Modifier.size(18.dp))
                }
            }
            is QuickTimerState.Paused -> {
                Button(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                        QuickTimerManager.togglePause()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Resume", maxLines = 1)
                }
                if (state.mode == QuickTimerMode.COUNTDOWN) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                            QuickTimerManager.addOneMinute()
                        },
                        modifier = Modifier.heightIn(min = 42.dp),
                    ) {
                        Text("+1m", maxLines = 1)
                    }
                }
                FilledTonalButton(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.LongPress)
                        QuickTimerManager.stop()
                        onCollapse()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Stop, contentDescription = "Stop timer", modifier = Modifier.size(18.dp))
                }
            }
            is QuickTimerState.Finished -> {
                Button(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                        QuickTimerManager.restart()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restart", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                        QuickTimerManager.addOneMinute()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Text("+1m", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        haptic.performIslandHaptic(HapticFeedbackType.LongPress)
                        QuickTimerManager.stop()
                        onCollapse()
                    },
                    modifier = Modifier.heightIn(min = 42.dp),
                ) {
                    Icon(LarpIcons.Close, contentDescription = "Dismiss timer", modifier = Modifier.size(18.dp))
                }
            }
            QuickTimerState.Idle -> Unit
        }
    }
}
