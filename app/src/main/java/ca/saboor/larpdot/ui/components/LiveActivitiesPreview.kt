package ca.saboor.larpdot.ui.components

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.notification.NotificationActivityAction
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactNotificationActivityContent
import ca.saboor.larpdot.ui.overlay.ExpandedNotificationActivityContent
import ca.saboor.larpdot.ui.overlay.activityRadialProgress
import ca.saboor.larpdot.ui.overlay.compactNavigationWings
import ca.saboor.larpdot.ui.overlay.compactRadialGaugeExtraDp
import ca.saboor.larpdot.ui.overlay.expandedActivitySize
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.rememberCompactActivityStatus
import ca.saboor.larpdot.ui.overlay.rememberCompactStatusExtraDp
import ca.saboor.larpdot.ui.overlay.rememberCompactStatusTextWidthDp
import ca.saboor.larpdot.ui.overlay.squircleShape
import ca.saboor.larpdot.ui.theme.LarpMotion

private enum class LiveActivityPreviewSample(val label: String) {
    NAVIGATION("Nav"),
    BLUETOOTH("BT"),
    CHARGING("Charge"),
    PROGRESS("Download"),
    TIMER("Timer"),
    CALL("Call"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveActivitiesPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var selectedSampleIndex by remember { mutableIntStateOf(0) }
    val selectedSample = LiveActivityPreviewSample.entries[selectedSampleIndex]

    val noopPendingIntent = remember(context) {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent("${context.packageName}.PREVIEW_NOOP").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
    val nowElapsed = remember { SystemClock.elapsedRealtime() }
    val sampleActivity = remember(selectedSample, nowElapsed, noopPendingIntent) {
        when (selectedSample) {
            LiveActivityPreviewSample.NAVIGATION -> NotificationActivityInfo(
                key = "preview_nav",
                packageName = "com.google.android.apps.maps",
                appName = "Maps",
                title = "Turn right on Main St",
                text = "15 min • 4.2 mi • 10:45 AM",
                kind = NotificationActivityKind.NAVIGATION,
                progress = 42,
                progressMax = 100,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = null,
                chronometerCountsDown = false,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = listOf(NotificationActivityAction("End trip", noopPendingIntent)),
            )
            LiveActivityPreviewSample.BLUETOOTH -> NotificationActivityInfo(
                key = "preview_bt",
                packageName = "android",
                appName = "System",
                title = "Pixel Buds Pro 2",
                text = "Earbuds • Connected",
                kind = NotificationActivityKind.BLUETOOTH,
                progress = null,
                progressMax = null,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = null,
                chronometerCountsDown = false,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = emptyList(),
                bluetoothDeviceType = "Earbuds",
                bluetoothBatteryPercent = 84,
            )
            LiveActivityPreviewSample.CHARGING -> NotificationActivityInfo(
                key = "preview_charging",
                packageName = "android",
                appName = "System",
                title = "Fast Charging",
                text = "76% • 27W • 18m left",
                kind = NotificationActivityKind.BATTERY_CHARGING,
                progress = 76,
                progressMax = 100,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = null,
                chronometerCountsDown = false,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = emptyList(),
            )
            LiveActivityPreviewSample.PROGRESS -> NotificationActivityInfo(
                key = "preview_progress",
                packageName = "com.android.providers.downloads",
                appName = "Downloads",
                title = "Downloading update",
                text = "68% • 142 MB of 210 MB",
                kind = NotificationActivityKind.PROGRESS,
                progress = 68,
                progressMax = 100,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = null,
                chronometerCountsDown = false,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = emptyList(),
            )
            LiveActivityPreviewSample.TIMER -> NotificationActivityInfo(
                key = "preview_timer",
                packageName = "com.google.android.deskclock",
                appName = "Clock",
                title = "Pasta Timer",
                text = "Counting down",
                kind = NotificationActivityKind.TIMER,
                progress = null,
                progressMax = null,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = nowElapsed + 4 * 60_000L + 32_000L,
                chronometerCountsDown = true,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = emptyList(),
            )
            LiveActivityPreviewSample.CALL -> NotificationActivityInfo(
                key = "preview_call",
                packageName = "com.google.android.dialer",
                appName = "Phone",
                title = "Alex Rivera",
                text = "On speaker",
                kind = NotificationActivityKind.CALL,
                progress = null,
                progressMax = null,
                progressIndeterminate = false,
                chronometerBaseElapsedRealtime = nowElapsed - 2 * 60_000L - 14_000L,
                chronometerCountsDown = false,
                postedAtMillis = 1L,
                contentIntent = null,
                actions = emptyList(),
            )
        }
    }

    val activitySettings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactThickness = cameraGeometry.thickness.dp
    val compactCornerRadius = cameraGeometry.cornerRadius.dp

    val compactStatus = rememberCompactActivityStatus(sampleActivity)
    val radialGaugeExtra = compactRadialGaugeExtraDp(compactThickness)
    val hasRadial = activityRadialProgress(sampleActivity, activitySettings.progressOutline, MaterialTheme.colorScheme.primary) != null
    val isNavigation = sampleActivity.kind == NotificationActivityKind.NAVIGATION
    val navMeasuredWidth = if (isNavigation && compactStatus.isNotEmpty()) {
        rememberCompactStatusTextWidthDp(compactStatus, compactFontSize = true)
    } else 0.dp
    val navWings = if (isNavigation) compactNavigationWings(navMeasuredWidth.value) else null
    val targetCompactExtra = if (navWings != null) {
        navWings.widthDp.dp
    } else {
        maxOf(
            if (hasRadial) radialGaugeExtra else 0.dp,
            if (compactStatus.isEmpty()) 56.dp else rememberCompactStatusExtraDp(compactStatus),
        )
    }
    val compactTrailingFraction = navWings?.trailingFraction ?: 0.5f
    val animatedCompactWidth by animateDpAsState(
        targetValue = cutoutDiameterDp + targetCompactExtra,
        animationSpec = LarpMotion.resize(),
        label = "activity_preview_compact_width",
    )

    val screenWidthDp = configuration.screenWidthDp.dp
    val maxExpandedWidth = (screenWidthDp - 32.dp).coerceAtLeast(180.dp)
    val expandedSize = expandedActivitySize(
        activity = sampleActivity,
        maxWidth = maxExpandedWidth,
        maxHeight = 260.dp,
        topInset = cutoutDiameterDp + 8.dp,
        cutoutDiameterDp = cutoutDiameterDp,
        spanIconLeftOfCutout = true,
    )
    val animatedCardWidth by animateDpAsState(
        targetValue = expandedSize.width,
        animationSpec = LarpMotion.resize(),
        label = "activity_preview_expanded_width",
    )
    val animatedCardHeight by animateDpAsState(
        targetValue = expandedSize.height,
        animationSpec = LarpMotion.resize(),
        label = "activity_preview_expanded_height",
    )
    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(animatedCardWidth, animatedCardHeight) / 2f)
    val expandedCornerRadiusPx = with(density) { expandedCornerRadiusDp.toPx() }
    val containerShape = if (!squircleEnabled || blurEnabled) {
        RoundedCornerShape(expandedCornerRadiusDp)
    } else {
        squircleShape(expandedCornerRadiusPx, 0.80f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            LiveActivityPreviewSample.entries.forEachIndexed { index, sample ->
                toggleableItem(
                    checked = selectedSampleIndex == index,
                    label = sample.label,
                    onCheckedChange = { selectedSampleIndex = index },
                    weight = 1f,
                )
            }
        }

        // Compact Pill Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(animatedCompactWidth)
                    .height(compactThickness)
                    .islandFluidProgressBorder(
                        progressFraction = 0f,
                        cornerRadius = compactCornerRadius,
                        shape = RoundedCornerShape(compactCornerRadius),
                        trackColor = Color(0x30FFFFFF),
                        progressColor = Color.Transparent,
                    ),
                shape = RoundedCornerShape(compactCornerRadius),
                color = Color.Black,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactNotificationActivityContent(
                        activity = sampleActivity,
                        cutoutDiameterDp = cutoutDiameterDp,
                        trailingWingFraction = compactTrailingFraction,
                    )
                    val leadingWidthDp = (animatedCompactWidth - cutoutDiameterDp) * (1f - compactTrailingFraction)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = leadingWidthDp)
                            .size(cutoutDiameterDp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0D0D))
                            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                    )
                }
            }
        }

        // Expanded Card Preview
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(animatedCardWidth)
                    .height(animatedCardHeight)
                    .islandFluidProgressBorder(
                        progressFraction = 0f,
                        cornerRadius = expandedCornerRadiusDp,
                        shape = containerShape,
                        trackColor = Color(0x30FFFFFF),
                        progressColor = Color.Transparent,
                    ),
                shape = containerShape,
                color = Color.Black.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ExpandedNotificationActivityContent(
                        activity = sampleActivity,
                        topContentInset = cutoutDiameterDp + 8.dp,
                        spanIconLeftOfCutout = true,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 6.dp)
                            .size(cutoutDiameterDp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D0D0D))
                            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                    )
                }
            }
        }
    }
}
