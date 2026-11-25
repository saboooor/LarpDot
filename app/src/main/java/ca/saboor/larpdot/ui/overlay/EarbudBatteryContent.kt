package ca.saboor.larpdot.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.notification.EarbudBatteryLevels
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.ui.theme.LarpIcons

private val earbudPairGap = 8.dp
internal val secondaryEarbudRingGap = 2.dp

internal fun expandedEarbudPairSize(diameter: Dp, stacked: Boolean): DpSize =
    if (stacked) DpSize(diameter, diameter * 2 + earbudPairGap)
    else DpSize(diameter * 2 + earbudPairGap, diameter)

@Composable
internal fun CompactEarbudBatteryContent(
    levels: EarbudBatteryLevels,
    thickness: Dp,
    centerGap: Dp,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    leftAnchor: IslandElementAnchor? = null,
    rightAnchor: IslandElementAnchor? = null,
) {
    val diameter = compactBatteryRingDiameter(thickness)
    val inset = compactEndInset(thickness, diameter)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .then(if (isLandscape) Modifier.verticalActivityLabel() else Modifier)
                .fillMaxSize()
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                CompactEarbudBattery(levels.left, left = true, diameter = diameter, modifier = Modifier.morphingElement(leftAnchor))
            }
            Spacer(Modifier.width(centerGap))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                CompactEarbudBattery(levels.right, left = false, diameter = diameter, modifier = Modifier.morphingElement(rightAnchor))
            }
        }
    }
}

@Composable
private fun CompactEarbudBattery(percent: Int?, left: Boolean, diameter: Dp, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    RadialProgressIcon(
        progress = percent?.div(100f),
        color = percent?.let { batteryGaugeColor(NotificationActivityKind.BLUETOOTH, it, primary) } ?: primary,
        diameter = diameter,
        modifier = modifier.earbudBatterySemantics(left, percent),
    ) { iconSize ->
        EarbudIcon(left, iconSize)
    }
}

@Composable
internal fun ExpandedEarbudBatteryContent(
    levels: EarbudBatteryLevels,
    diameter: Dp,
    stacked: Boolean,
    leftAnchor: IslandElementAnchor?,
    rightAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
) {
    val gauges = @Composable {
        ExpandedEarbudBattery(levels.left, left = true, diameter = diameter, modifier = Modifier.morphingElement(leftAnchor))
        ExpandedEarbudBattery(levels.right, left = false, diameter = diameter, modifier = Modifier.morphingElement(rightAnchor))
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (stacked) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(earbudPairGap)) { gauges() }
        } else {
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(earbudPairGap)) { gauges() }
        }
    }
}

@Composable
private fun ExpandedEarbudBattery(percent: Int?, left: Boolean, diameter: Dp, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    ExpandedActivityGauge(
        percent = percent,
        color = percent?.let { batteryGaugeColor(NotificationActivityKind.BLUETOOTH, it, primary) } ?: primary,
        diameter = diameter,
        modifier = modifier.earbudBatterySemantics(left, percent),
    )
}

@Composable
private fun EarbudIcon(left: Boolean, size: Dp) {
    Icon(
        imageVector = if (left) LarpIcons.LeftEarbud else LarpIcons.RightEarbud,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size),
    )
}

private fun Modifier.earbudBatterySemantics(left: Boolean, percent: Int?): Modifier =
    clearAndSetSemantics {
        val side = if (left) "Left" else "Right"
        contentDescription = if (percent == null) "$side earbud battery unavailable" else "$side earbud battery $percent percent"
    }
