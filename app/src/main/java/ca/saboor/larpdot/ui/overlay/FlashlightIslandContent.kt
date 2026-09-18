package ca.saboor.larpdot.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import kotlin.math.roundToInt

val FlashlightWhite = Color.White
val FlashlightWhiteLight = Color.White
val FlashlightWhiteDim = Color(0xB3FFFFFF)

val FlashlightAmber = Color(0xFFFFB300)
val FlashlightAmberLight = Color(0xFFFFD54F)
val FlashlightAmberDark = Color(0xFFFF8F00)

/**
 * Minimized pill content when Flashlight is active:
 * Left Wing: Crisp white flashlight icon with subtle breathing pulse and clean padding.
 * Center: Hole punch clearance spacer.
 * Right Wing: Symmetrical clearance spacer keeping cutout strictly centered.
 */
@Composable
fun CompactFlashlightContent(
    cutoutDiameterDp: Dp,
    onExpand: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "compact_torch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Wing: Flashlight icon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.FlashlightOn,
                    contentDescription = "Flashlight Active",
                    tint = Color.White.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Hole punch clearance spacer
            Spacer(modifier = Modifier.height(cutoutDiameterDp))

            // Bottom Wing: Symmetrical balance spacer
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Wing: Flashlight icon with tight, clean padding
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.FlashlightOn,
                    contentDescription = "Flashlight Active",
                    tint = Color.White.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Hole punch clearance spacer
            Spacer(modifier = Modifier.width(cutoutDiameterDp))

            // Right Wing: Symmetrical balance spacer
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * Rich expanded island card for Flashlight control:
 * Top Row: Status badge & close action.
 * Middle: Large animated torch glyph, status readout, and M3 power switch.
 * Bottom: Real-time brightness slider (if supported by hardware/simulation) with -/+ nudges.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandedFlashlightContent(
    cutoutDiameterDp: Dp,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    cutoutInfo: CutoutInfo? = null,
    cardHorizontalMarginDp: Dp = 14.dp,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val isStrengthSupported by FlashlightController.isStrengthSupported.collectAsState()
    val isPixelLightActive by FlashlightController.isPixelLightActive.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "expanded_torch_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "expanded_glow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.30f to Color.Transparent,
                        1.00f to Color.White.copy(alpha = 0.08f),
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCollapse() })
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Row 1: Header with Chip and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Amber active status pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isFlashlightOn) FlashlightAmber else Color.Gray),
                        )
                        Text(
                            text = if (isPixelLightActive) "PIXELLIGHT BOOST" else if (isFlashlightOn) "FLASHLIGHT ON" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = if (isFlashlightOn) FlashlightAmberLight else Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                // Hole punch clearance spacer
                Spacer(modifier = Modifier.width(cutoutDiameterDp))

                // Close / collapse button
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Row 2: Large Icon, Info, and Primary Power Toggle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Radiant torch icon glyph
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        FlashlightAmberLight.copy(alpha = if (isFlashlightOn) pulseGlow else 0.1f),
                                        FlashlightAmber.copy(alpha = if (isFlashlightOn) pulseGlow * 0.5f else 0.05f),
                                        Color.Transparent,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isFlashlightOn) FlashlightAmberLight else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Column {
                        Text(
                            text = "Flashlight",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isFlashlightOn) {
                                if (isPixelLightActive) {
                                    "PixelLight Boost: $torchStrength / $maxStrength"
                                } else if (isStrengthSupported && maxStrength > 1) {
                                    "Brightness: ${(torchStrength.toFloat() / maxStrength * 100).roundToInt()}%"
                                } else {
                                    "Illuminating \u2022 Tap to turn off"
                                }
                            } else {
                                "Turned off"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }

                // Tactile M3 Power Toggle Pill
                Surface(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        FlashlightController.toggleFlashlight()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFlashlightOn) FlashlightAmber else Color(0x33FFFFFF),
                    modifier = Modifier
                        .width(64.dp)
                        .height(46.dp),
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (isFlashlightOn) "Turn Off" else "Turn On",
                            tint = if (isFlashlightOn) Color(0xFF1B1A1E) else Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            // Row 3: Brightness / Strength Slider or Info Bar
            if (isStrengthSupported && maxStrength > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (torchStrength > 1) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                FlashlightController.setStrength(torchStrength - 1)
                            }
                        },
                        modifier = Modifier.size(34.dp),
                        enabled = torchStrength > 1,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease Brightness",
                            tint = if (torchStrength > 1) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Slider(
                        value = torchStrength.toFloat(),
                        onValueChange = { newLevel ->
                            FlashlightController.setStrength(newLevel.roundToInt())
                        },
                        onValueChangeFinished = {
                            FlashlightController.flushStrength(torchStrength)
                        },
                        valueRange = 1f..maxStrength.toFloat(),
                        steps = if (maxStrength > 30) 0 else (maxStrength - 2),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = FlashlightAmber,
                            activeTrackColor = FlashlightAmber,
                            inactiveTrackColor = FlashlightAmber.copy(alpha = 0.24f),
                        ),
                    )

                    IconButton(
                        onClick = {
                            if (torchStrength < maxStrength) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                FlashlightController.setStrength(torchStrength + 1)
                            }
                        },
                        modifier = Modifier.size(34.dp),
                        enabled = torchStrength < maxStrength,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase Brightness",
                            tint = if (torchStrength < maxStrength) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = FlashlightAmberLight,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isFlashlightOn) "Hardware torch active at full brightness" else "Tap power button to activate flashlight",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
