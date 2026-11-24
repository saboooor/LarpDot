package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.ScreenStateTracker
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion

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
) {
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
        )
        return
    }
    val flashlightAccent = flashlightAccentColor()
    val showFlashlightRadial by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxTorchStrength by FlashlightController.maxStrength.collectAsState()
    val flashlightFraction = getFlashlightStrengthFraction(torchStrength, maxTorchStrength)
    val flashlightPercentLabel = flashlightStatus.removeSuffix("%").trim()
    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val bubbleIconSize = (bubbleThickness - 8.dp).coerceIn(12.dp, 20.dp)
    val extraSpan = if (isLandscape) (bubbleHeight - bubbleWidth).coerceAtLeast(0.dp) else (bubbleWidth - bubbleHeight).coerceAtLeast(0.dp)
    val isPillShaped = isMiniPill && extraSpan > 4.dp
    val iconInset = ((bubbleThickness - bubbleIconSize) / 2f).coerceAtLeast(4.dp)
    val outerPadding = iconInset
    val innerPadding = iconInset
    val contentShift = 0.dp
    val iconScale = (minOf(bubbleWidth, bubbleHeight) / bubbleThickness).coerceIn(0f, 1f)
    val isScreenOn by ScreenStateTracker.isScreenOn.collectAsState()
    val pulseAlphaState = if (type == IslandType.FLASHLIGHT && isScreenOn) {
        val infiniteTransition = rememberInfiniteTransition(label = "dot_torch_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = LarpMotion.timing(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
    } else null
    val activityRadial = if (type == IslandType.NOTIFICATION_ACTIVITY) {
        activityRadialProgress(notificationActivity, activitySettings.progressOutline, primaryColor)
    } else null
    val hasActivityDotRing = !isPillShaped && activityRadial != null && notificationActivity != null
    val hasFlashlightDotRing = !isPillShaped && type == IslandType.FLASHLIGHT && showFlashlightRadial
    val hasDotRing = hasActivityDotRing || hasFlashlightDotRing
    val hasMiniPillRing = isPillShaped && (
        (type == IslandType.NOTIFICATION_ACTIVITY && activityRadial != null) ||
            (type == IslandType.FLASHLIGHT && showFlashlightRadial && flashlightPercentLabel.isNotEmpty())
        )
    val dotRingDiameter = compactBatteryRingDiameter(bubbleThickness)
    val dotRingIconSize = (dotRingDiameter - 8.dp).coerceIn(10.dp, 13.dp)
    val miniPillRingDiameter = dotRingDiameter
    val miniPillRingInset = ((bubbleThickness - miniPillRingDiameter) / 2f).coerceAtLeast(3.dp)
    val leadingSlotSize = if (hasDotRing) dotRingDiameter else bubbleIconSize
    val leftContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                if (notificationActivity != null) {
                    if (!isPillShaped && activityRadial != null) {
                        val (percent, ringColor) = activityRadial
                        Box(
                            modifier = Modifier.size(dotRingDiameter),
                            contentAlignment = Alignment.Center,
                        ) {
                            ActivityIcon(
                                activity = notificationActivity,
                                modifier = Modifier.size(dotRingIconSize),
                            )
                            BatteryRadialRing(
                                percent = percent,
                                color = ringColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        ActivityIcon(
                            activity = notificationActivity,
                            modifier = Modifier.size(bubbleIconSize),
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
                    Box(
                        modifier = Modifier.size(dotRingDiameter),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = LarpIcons.FlashlightOn,
                            contentDescription = "Flashlight Active",
                            tint = flashlightAccent,
                            modifier = Modifier
                                .size(dotRingIconSize)
                                .graphicsLayer { alpha = pulseAlphaState?.value ?: 1f },
                        )
                        RadialProgressRing(
                            progress = flashlightFraction,
                            color = flashlightAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Icon(
                        imageVector = LarpIcons.FlashlightOn,
                        contentDescription = "Flashlight Active",
                        tint = flashlightAccent,
                        modifier = Modifier
                            .size(bubbleIconSize)
                            .graphicsLayer { alpha = pulseAlphaState?.value ?: 1f },
                    )
                }
            }
            IslandType.MEDIA -> Unit
        }
    }

    val rightContent = @Composable {
        when (type) {
            IslandType.NOTIFICATION_ACTIVITY -> {
                if (activityRadial != null && notificationActivity != null) {
                    val (percent, ringColor) = activityRadial
                    BatteryRadialGauge(
                        percent = percent,
                        color = ringColor,
                        diameter = miniPillRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (percent >= 100) 6.5.sp else 7.5.sp,
                        showPercentSymbol = false,
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
                        color = flashlightAccent,
                        diameter = miniPillRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (flashlightPercentLabel.length >= 3) 6.5.sp else 7.5.sp,
                        showPercentSymbol = false,
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
            IslandType.MEDIA -> Unit
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
