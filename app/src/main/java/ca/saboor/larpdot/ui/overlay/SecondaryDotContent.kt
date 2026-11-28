package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandTapAction
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.resolveCustomIslandColor
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
internal fun secondaryStatusStyle(status: String) = MaterialTheme.typography.labelSmall.copy(
    fontSize = if (status.contains('%')) 11.5.sp else 12.5.sp,
    fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
)

@Composable
internal fun secondaryStatusTextWidth(
    type: IslandType,
    status: String?,
    activity: NotificationActivityInfo?,
): Float {
    if (status.isNullOrBlank()) return 0f
    when (type) {
        IslandType.FLASHLIGHT -> {
            val left by OverlayPreferences.minimizedFlashlightLeftSideFlow.collectAsState()
            val right by OverlayPreferences.minimizedFlashlightRightSideFlow.collectAsState()
            val radial = OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS
            if (left == radial || right == radial) return 0f
        }
        IslandType.NOTIFICATION_ACTIVITY -> {
            val settings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
            if (activityRadialProgress(activity, settings.progressOutline, MaterialTheme.colorScheme.primary) != null) return 0f
        }
        else -> return 0f
    }
    val style = secondaryStatusStyle(status)
    val sample = formatCompactStatus(status.map { if (it.isDigit()) '8' else it }.joinToString(""))
    val width = rememberTextMeasurer().measure(sample, style, maxLines = 1, softWrap = false).size.width
    return width / LocalDensity.current.density + 2f
}

@Composable
internal fun SecondaryDotContent(
    type: IslandType,
    bubbleWidth: Dp,
    bubbleHeight: Dp,
    bubbleThickness: Dp,
    isMiniPill: Boolean,
    isLandscape: Boolean,
    notificationActivity: NotificationActivityInfo?,
    mediaInfo: MediaTrackInfo,
    notificationActivityStatus: String?,
    flashlightStatus: String,
    artworkAnchor: ArtworkAnchor? = null,
    visualizerAnchor: IslandElementAnchor? = null,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
    customIsland: CustomIsland? = null,
) {
    if (type == IslandType.CUSTOM) {
        val bubbleIconSize = secondaryBubbleIconSize(bubbleThickness)
        val extraSpan = if (isLandscape) {
            (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp)
        } else {
            (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
        }
        val showMiniPillWings = isMiniPill &&
            extraSpan > 4.dp &&
            customIsland != null &&
            customIsland.left.hasContent &&
            customIsland.right.hasContent
        val isLeftSource = customIsland != null && customIsland.left.hasContent
        val dotSlot = customIsland?.let { island ->
            if (isLeftSource) island.left else island.right.takeIf { it.hasContent }
        }
        val visibleSlots = if (showMiniPillWings) {
            listOf(customIsland.left, customIsland.right)
        } else {
            listOfNotNull(dotSlot)
        }
        val needsDominant = visibleSlots.any { slot ->
            slot.iconColor.mode == CustomColorMode.DOMINANT || slot.textStyle.colorMode == CustomColorMode.DOMINANT
        }
        val needsSources = needsDominant || visibleSlots.any { slot ->
            slot.image != null || !slot.text.isNullOrBlank()
        }
        val sources = if (customIsland != null && needsSources) {
            customIslandSources(customIsland)
        } else null
        val dominantColor = if (customIsland != null && needsDominant) {
            rememberCustomIslandDominantColor(customIsland, sources).first
        } else null
        val placeholderValues = if (customIsland != null && sources != null && visibleSlots.any { !it.text.isNullOrBlank() }) {
            rememberCustomIslandPlaceholderValues(customIsland, sources, preview = false)
        } else emptyMap()
        val appOverride = customIsland?.tapAppPackage.takeIf { customIsland?.tapAction == CustomIslandTapAction.OPEN_APP }

        if (showMiniPillWings) {
            val iconInset = ((bubbleThickness - bubbleIconSize) / 2f).coerceAtLeast(4.dp)
            if (isLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = iconInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CustomIslandWing(
                            slot = customIsland.left,
                            iconSize = bubbleIconSize,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            anchor = iconAnchor,
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        CustomIslandWing(
                            slot = customIsland.right,
                            iconSize = bubbleIconSize,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            anchor = progressAnchor,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = iconInset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CustomIslandWing(
                            slot = customIsland.left,
                            iconSize = bubbleIconSize,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            anchor = iconAnchor,
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        CustomIslandWing(
                            slot = customIsland.right,
                            iconSize = bubbleIconSize,
                            sources = sources,
                            placeholderValues = placeholderValues,
                            appOverride = appOverride,
                            dominantColor = dominantColor,
                            anchor = progressAnchor,
                        )
                    }
                }
            }
            return
        }

        val wingAnchor = if (isLeftSource) iconAnchor else progressAnchor
        val iconTint = if (dotSlot != null) {
            resolveCustomIslandColor(dotSlot.iconColor, MaterialTheme.colorScheme.primary, dominantColor)
        } else MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (customIsland != null && dotSlot != null) {
                CustomIslandWing(
                    slot = dotSlot,
                    iconSize = bubbleIconSize,
                    sources = sources,
                    placeholderValues = placeholderValues,
                    appOverride = appOverride,
                    dominantColor = dominantColor,
                    anchor = wingAnchor,
                )
            } else {
                Icon(
                    LarpIcons.Apps,
                    contentDescription = customIsland?.name,
                    tint = iconTint,
                    modifier = Modifier.size(bubbleIconSize).morphingElement(wingAnchor),
                )
            }
        }
        return
    }
    if (type == IslandType.MEDIA) {
        SecondaryMusicDotContent(
            mediaInfo = mediaInfo,
            bubbleWidth = bubbleWidth,
            bubbleHeight = bubbleHeight,
            bubbleThickness = bubbleThickness,
            isMiniPill = isMiniPill,
            isLandscape = isLandscape,
            artworkAnchor = artworkAnchor,
            visualizerAnchor = visualizerAnchor,
        )
        return
    }
    val earbudLevels = notificationActivity?.pairedEarbudBatteryLevels
    if (type == IslandType.NOTIFICATION_ACTIVITY && isMiniPill && earbudLevels != null) {
        CompactEarbudBatteryContent(
            levels = earbudLevels,
            thickness = bubbleThickness,
            centerGap = secondaryEarbudRingGap,
            isLandscape = isLandscape,
            leftAnchor = iconAnchor,
            rightAnchor = progressAnchor,
        )
        return
    }
    val flashlightAccent = FlashlightWhite
    val flashlightLeftSide by OverlayPreferences.minimizedFlashlightLeftSideFlow.collectAsState()
    val flashlightRightSide by OverlayPreferences.minimizedFlashlightRightSideFlow.collectAsState()
    val showFlashlightRadial = flashlightLeftSide == OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS ||
        flashlightRightSide == OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxTorchStrength by FlashlightController.maxStrength.collectAsState()
    val flashlightFraction = getFlashlightStrengthFraction(torchStrength, maxTorchStrength)
    val flashlightPercentLabel = flashlightStatus.removeSuffix("%").trim()
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val bubbleIconSize = secondaryBubbleIconSize(bubbleThickness)
    val extraSpan = if (isLandscape) (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp) else (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
    val isPillShaped = isMiniPill && extraSpan > 4.dp
    val iconInset = ((bubbleThickness - bubbleIconSize) / 2f).coerceAtLeast(4.dp)
    val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
    val activityRadial = if (type == IslandType.NOTIFICATION_ACTIVITY) {
        if (isPillShaped) {
            activityRadialProgress(notificationActivity, activitySettings.progressOutline, primaryColor)
        } else {
            secondaryDotRadialProgress(notificationActivity, activitySettings.progressOutline, primaryColor)
        }
    } else null
    val hasActivityDotRing = !isPillShaped && activityRadial != null && notificationActivity != null
    val hasFlashlightDotRing = !isPillShaped && type == IslandType.FLASHLIGHT && showFlashlightRadial
    val hasDotRing = hasActivityDotRing || hasFlashlightDotRing
    val hasMiniPillRing = isPillShaped && (
        (type == IslandType.NOTIFICATION_ACTIVITY && activityRadial != null) ||
            (type == IslandType.FLASHLIGHT && showFlashlightRadial && flashlightPercentLabel.isNotEmpty())
        )
    val dotRingDiameter = compactBatteryRingDiameter(bubbleThickness)
    val miniPillRingDiameter = dotRingDiameter
    val miniPillRingInset = compactEndInset(bubbleThickness, miniPillRingDiameter)
    val leadingSlotSize = if (hasDotRing) dotRingDiameter else bubbleIconSize
    val leftContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                if (notificationActivity != null) {
                    if (!isPillShaped && activityRadial != null) {
                        val (percent, ringColor) = activityRadial
                        RadialProgressIcon(
                            progress = percent / 100f,
                            color = ringColor,
                            diameter = dotRingDiameter,
                            ringModifier = Modifier.morphingElement(progressAnchor),
                        ) { iconSize ->
                            ActivityIcon(
                                activity = notificationActivity,
                                modifier = Modifier.size(iconSize).morphingElement(iconAnchor),
                            )
                        }
                    } else {
                        ActivityIcon(
                            activity = notificationActivity,
                            modifier = Modifier.size(bubbleIconSize).morphingElement(iconAnchor),
                        )
                    }
                } else {
                    Icon(
                        imageVector = LarpIcons.Notifications,
                        contentDescription = "Ongoing activity",
                        tint = primaryColor,
                        modifier = Modifier.size(bubbleIconSize),
                    )
                }
            }
            IslandType.FLASHLIGHT -> {
                if (hasFlashlightDotRing) {
                    RadialProgressIcon(
                        progress = flashlightFraction,
                        color = flashlightAccent,
                        diameter = dotRingDiameter,
                        ringModifier = Modifier.morphingElement(progressAnchor),
                    ) { iconSize ->
                        CompactFlashlightIcon(size = iconSize, iconAnchor = iconAnchor)
                    }
                } else {
                    CompactFlashlightIcon(size = bubbleIconSize, iconAnchor = iconAnchor)
                }
            }
            IslandType.MEDIA, IslandType.CUSTOM -> Unit
        }
    }

    val rightContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                if (activityRadial != null && notificationActivity != null) {
                    val (percent, ringColor) = activityRadial
                    RadialProgressGauge(
                        progress = percent / 100f,
                        text = percent.toString(),
                        color = ringColor,
                        diameter = miniPillRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
                    )
                } else {
                    val actStatus = notificationActivityStatus ?: ""
                    Text(
                        text = formatCompactStatus(actStatus),
                        color = Color.White,
                        style = secondaryStatusStyle(actStatus),
                        modifier = if (isLandscape) Modifier.verticalActivityLabel() else Modifier,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            IslandType.FLASHLIGHT -> {
                if (showFlashlightRadial && flashlightPercentLabel.isNotEmpty()) {
                    RadialProgressGauge(
                        progress = flashlightFraction,
                        text = flashlightPercentLabel,
                        color = FlashlightWhite,
                        diameter = miniPillRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
                    )
                } else {
                    Text(
                        text = formatCompactStatus(flashlightStatus),
                        color = flashlightAccent,
                        style = secondaryStatusStyle(flashlightStatus),
                        modifier = if (isLandscape) Modifier.verticalActivityLabel() else Modifier,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            IslandType.MEDIA, IslandType.CUSTOM -> Unit
        }
    }

    val trailingMiniPillPadding = if (hasMiniPillRing) miniPillRingInset else iconInset

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPillShaped) Modifier.padding(
                        top = iconInset,
                        bottom = trailingMiniPillPadding,
                    ) else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(leadingSlotSize)
                    .scale(iconScale),
                contentAlignment = Alignment.Center,
            ) {
                leftContent()
            }
            if (isPillShaped) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPillShaped) Modifier.padding(
                        start = iconInset,
                        end = trailingMiniPillPadding,
                    ) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isPillShaped) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(leadingSlotSize)
                    .scale(iconScale),
                contentAlignment = Alignment.Center,
            ) {
                leftContent()
            }
            if (isPillShaped) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    rightContent()
                }
            }
        }
    }
}
