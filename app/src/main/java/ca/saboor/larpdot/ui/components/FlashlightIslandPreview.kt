package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.ui.overlay.CompactFlashlightContent
import ca.saboor.larpdot.ui.overlay.ExpandedFlashlightContent
import ca.saboor.larpdot.ui.overlay.FlashlightAmber
import ca.saboor.larpdot.ui.overlay.squircleShape

/**
 * Authentic live preview of the Flashlight Dynamic Island
 * (both compact pill and morphing expanded card) directly inside the app.
 */
@Composable
fun FlashlightIslandPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isSimulated by FlashlightController.isSimulated.collectAsState()

    val cutoutDiameterDp = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceIn(16.dp, 36.dp)
    val compactExtraDp = 48.dp
    val compactWidth = cutoutDiameterDp + compactExtraDp

    val displayRadiusDp = with(density) { cutoutInfo.displayCornerRadiusPx.toDp() }.coerceAtLeast(24.dp)
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    val topMarginDp = (cutoutCenterYDp - 18.dp).coerceAtLeast(8.dp)
    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)
    val expandedCornerRadiusPx = with(density) { expandedCornerRadiusDp.toPx() }
    val containerShape = squircleShape(expandedCornerRadiusPx, 0.80f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = "Live Island Preview",
            trailing = {
                StatusPill(
                    text = if (isFlashlightOn) "ILLUMINATING" else "IDLE",
                    active = isFlashlightOn,
                    pulse = isFlashlightOn,
                    activeColor = FlashlightAmber,
                )
            },
        )

        // Compact Pill Live Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val outlineAllowanceDp = 2.dp
            val compactHeight = cutoutDiameterDp + (outlineAllowanceDp * 2)
            val compactCornerRadius = compactHeight / 2f
            Surface(
                modifier = Modifier
                    .width(compactWidth)
                    .height(compactHeight)
                    .border(0.75.dp, Color(0x30FFFFFF), RoundedCornerShape(compactCornerRadius)),
                shape = RoundedCornerShape(compactCornerRadius),
                color = Color.Black,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactFlashlightContent(
                        cutoutDiameterDp = cutoutDiameterDp,
                        onExpand = {},
                        onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                    )
                    // Hardware camera cutout visualizer centered between wings
                    Box(
                        modifier = Modifier
                            .size((cutoutDiameterDp - 4.dp).coerceAtLeast(10.dp))
                            .clip(CircleShape)
                            .background(Color(0xFF0D0D0D))
                            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                    )
                }
            }
        }

        // Expanded Card Live Preview
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(220.dp)
                    .height(290.dp)
                    .border(0.75.dp, Color(0x30FFFFFF), containerShape),
                shape = containerShape,
                color = Color.Black,
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                ExpandedFlashlightContent(
                    cutoutDiameterDp = cutoutDiameterDp,
                    isExpanded = true,
                    onCollapse = { /* In-app preview */ },
                    cutoutInfo = cutoutInfo,
                    cardHorizontalMarginDp = 16.dp,
                )

                // Hardware camera cutout punch hole positioned accurately
                val screenWidthDp = configuration.screenWidthDp.dp
                val previewCardWidthDp = screenWidthDp - 32.dp
                val orientedCenterXDp = with(density) { cutoutInfo.centerX.toDp() } - 16.dp
                val punchHoleCenterXDp = orientedCenterXDp.coerceIn(
                    cutoutDiameterDp / 2f,
                    previewCardWidthDp - (cutoutDiameterDp / 2f),
                )
                val punchHoleCenterYDp = 18.dp

                Box(
                    modifier = Modifier
                        .offset(
                            x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                            y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                        )
                        .size((cutoutDiameterDp - 4.dp).coerceAtLeast(10.dp))
                        .clip(CircleShape)
                        .background(Color(0xFF0D0D0D))
                        .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                )
            }
        }
        }

        // Action Controls: Quick Toggle and Simulation Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { FlashlightController.toggleFlashlight() },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isFlashlightOn) "Turn Off" else "Turn On",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            OutlinedButton(
                onClick = {
                    FlashlightController.setSimulated(!isSimulated)
                },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSimulated) "Hardware Mode" else "Simulate Mode",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
