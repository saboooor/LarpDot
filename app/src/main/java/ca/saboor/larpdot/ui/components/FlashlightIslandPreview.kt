package ca.saboor.larpdot.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactFlashlightContent
import ca.saboor.larpdot.ui.overlay.getFlashlightStrengthFraction
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.rememberCompactFlashlightStatus
import ca.saboor.larpdot.ui.overlay.rememberCompactStatusExtraDp
import ca.saboor.larpdot.ui.overlay.ExpandedFlashlightContent
import ca.saboor.larpdot.ui.overlay.squircleShape
import ca.saboor.larpdot.ui.theme.LarpMotion

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
    var showMinimizedPreview by remember { mutableStateOf(true) }
    var showExpandedPreview by remember { mutableStateOf(true) }

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isSimulated by FlashlightController.isSimulated.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val showMinimizedOutline by OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState()
    val showExpandedOutline by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val strengthFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxStrength) else 0f

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val flashlightStatus = rememberCompactFlashlightStatus()
    val compactExtraDp = rememberCompactStatusExtraDp(flashlightStatus)
    val compactWidth = cutoutDiameterDp + compactExtraDp

    val screenWidthDp = configuration.screenWidthDp.dp
    val wideFlashlight = expandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER
    val previewCardWidth = if (wideFlashlight) screenWidthDp - 32.dp else minOf(screenWidthDp - 96.dp, 220.dp)
    val previewCardHeight = if (wideFlashlight) cutoutDiameterDp + 64.dp else 290.dp
    val previewHorizontalMarginDp = (screenWidthDp - previewCardWidth) / 2f

    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(previewCardWidth, previewCardHeight) / 2f)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showMinimizedPreview) {
                FilledTonalButton(
                    onClick = { showMinimizedPreview = false },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Minimized")
                }
            } else {
                OutlinedButton(
                    onClick = { showMinimizedPreview = true },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Minimized")
                }
            }

            if (showExpandedPreview) {
                FilledTonalButton(
                    onClick = { showExpandedPreview = false },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Expanded")
                }
            } else {
                OutlinedButton(
                    onClick = { showExpandedPreview = true },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Text("Expanded")
                }
            }
        }

        // Compact Pill Live Preview
        AnimatedVisibility(showMinimizedPreview, enter = LarpMotion.reveal, exit = LarpMotion.conceal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The camera spans the full thickness; padding belongs to the wing content.
                val compactHeight = cutoutDiameterDp
                val compactCornerRadius = compactHeight / 2f
                Surface(
                    modifier = Modifier
                        .width(compactWidth)
                        .height(compactHeight)
                        .islandFluidProgressBorder(
                            progressFraction = if (showMinimizedOutline) strengthFraction else 0f,
                            cornerRadius = compactCornerRadius,
                            shape = RoundedCornerShape(compactCornerRadius),
                            trackColor = if (showMinimizedOutline) Color(0x30FFFFFF) else Color.Transparent,
                            progressColor = Color.White,
                        ),
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
                            status = flashlightStatus,
                        )
                        // Hardware camera cutout visualizer centered between wings
                        Box(
                            modifier = Modifier
                                .size(cutoutDiameterDp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0D0D))
                                .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                        )
                    }
                }
            }
        }

        // Expanded Card Live Preview
        AnimatedVisibility(showExpandedPreview, enter = LarpMotion.reveal, exit = LarpMotion.conceal) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(previewCardWidth)
                        .height(previewCardHeight)
                        .islandFluidProgressBorder(
                            progressFraction = if (showExpandedOutline) strengthFraction else 0f,
                            cornerRadius = expandedCornerRadiusDp,
                            shape = containerShape,
                            trackColor = if (showExpandedOutline) Color(0x30FFFFFF) else Color.Transparent,
                            progressColor = Color.White,
                        ),
                    shape = containerShape,
                    color = Color.Black.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
                    shadowElevation = 14.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        ExpandedFlashlightContent(
                            cutoutDiameterDp = cutoutDiameterDp,
                            isExpanded = true,
                            onCollapse = { /* In-app preview */ },
                            cutoutInfo = cutoutInfo,
                            cardHorizontalMarginDp = previewHorizontalMarginDp,
                        )

                        // Hardware camera cutout punch hole positioned accurately
                        val orientedCenterXDp = with(density) { cutoutInfo.centerX.toDp() } - previewHorizontalMarginDp
                        val punchHoleCenterXDp = orientedCenterXDp.coerceIn(
                            cutoutDiameterDp / 2f,
                            previewCardWidth - (cutoutDiameterDp / 2f),
                        )
                        val punchHoleCenterYDp = 18.dp

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                                    y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                                )
                                .size(cutoutDiameterDp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0D0D))
                                .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                        )
                    }
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
