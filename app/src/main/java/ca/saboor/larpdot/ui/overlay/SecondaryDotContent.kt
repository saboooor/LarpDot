package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
internal fun SecondaryDotContent(
    type: IslandType,
    bubbleWidth: Dp,
    bubbleHeight: Dp,
    bubbleThickness: Dp,
    isMiniPill: Boolean,
    isLandscape: Boolean,
    beforeMain: Boolean,
    bubbleStyle: OverlayPreferences.BubbleStyle,
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
        val isLeftSource = customIsland != null &&
            (customIsland.left.icon != null || customIsland.left.image != null || !customIsland.left.text.isNullOrBlank())
        val slot = customIsland?.let { island ->
            if (isLeftSource) island.left
            else island.right.takeIf { it.icon != null || it.image != null || !it.text.isNullOrBlank() }
        }
        val wingAnchor = if (isLeftSource) iconAnchor else progressAnchor
        val size = Modifier.size(secondaryBubbleIconSize(bubbleThickness)).morphingElement(wingAnchor)
        val needsDominant = slot != null && (
            slot.iconColor.mode == CustomColorMode.DOMINANT || slot.textStyle.colorMode == CustomColorMode.DOMINANT
            )
        val sources = if (customIsland != null && (needsDominant || slot?.image != null || !slot?.text.isNullOrBlank())) {
            customIslandSources(customIsland)
        } else null
        val dominantColor = if (customIsland != null && needsDominant) {
            rememberCustomIslandDominantColor(customIsland, sources).first
        } else null
        val iconTint = if (slot != null) {
            resolveCustomIslandColor(slot.iconColor, MaterialTheme.colorScheme.primary, dominantColor)
        } else MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (customIsland != null && !slot?.text.isNullOrBlank() && sources != null) {
                val placeholderValues = rememberCustomIslandPlaceholderValues(customIsland, sources, preview = false)
                CustomWingText(
                    slot = slot,
                    placeholderValues = placeholderValues,
                    preview = false,
                    maxLines = 1,
                    dominantColor = dominantColor,
                    modifier = Modifier.padding(horizontal = 4.dp).morphingElement(wingAnchor),
                )
            } else if (customIsland != null && slot?.image != null && sources != null) {
                CustomIslandImageContent(
                    slot.image,
                    sources,
                    size,
                    compact = true,
                    preview = false,
                    appOverride = customIsland.tapAppPackage.takeIf { customIsland.tapAction == CustomIslandTapAction.OPEN_APP },
                    tint = if (slot.image == CustomIslandImage.NOTIFICATION_ICON) iconTint else null,
                )
            } else {
                Icon(
                    slot?.icon?.image ?: LarpIcons.Apps,
                    contentDescription = customIsland?.name,
                    tint = iconTint,
                    modifier = size,
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
            beforeMain = beforeMain,
            bubbleStyle = bubbleStyle,
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
    val showFlashlightRadial by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
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
    val outerPadding = iconInset
    val innerPadding = iconInset
    val contentShift = 0.dp
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
                    val isPercentage = actStatus.contains('%')
                    Text(
                        text = formatCompactStatus(actStatus),
                        color = Color.White,
                        fontSize = if (isPercentage) 10.sp else 11.sp,
                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
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
                    val isPercentage = flashlightStatus.contains('%')
                    Text(
                        text = formatCompactStatus(flashlightStatus),
                        color = flashlightAccent,
                        fontSize = if (isPercentage) 10.sp else 11.sp,
                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            IslandType.MEDIA, IslandType.CUSTOM -> Unit
        }
    }

    val trailingMiniPillPadding = if (hasMiniPillRing) miniPillRingInset else if (beforeMain) innerPadding else outerPadding

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        top = if (beforeMain) outerPadding else innerPadding,
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
                .offset(x = if (beforeMain) contentShift else -contentShift)
                .then(
                    if (isPillShaped) Modifier.padding(
                        start = if (beforeMain) outerPadding else innerPadding,
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
