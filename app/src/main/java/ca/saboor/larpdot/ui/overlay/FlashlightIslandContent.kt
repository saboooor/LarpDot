package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.service.OverlayPreferences
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import kotlin.math.abs
import kotlin.math.roundToInt

val FlashlightWhite = Color.White
val FlashlightWhiteLight = Color.White
val FlashlightWhiteDim = Color(0xB3FFFFFF)

@Composable
internal fun flashlightAccentColor(): Color {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context).primary
        else Color(0xFFF2B8B5)
    }
}

val FlashlightAmber = Color(0xFFFFB300)
val FlashlightAmberLight = Color(0xFFFFD54F)
val FlashlightAmberDark = Color(0xFFFF8F00)

/** Percentages use the standard torch maximum as 100%; boosted levels may exceed it. */
fun getFlashlightPercentText(torchStrength: Int, maxStrength: Int, standardMaxStrength: Int = maxStrength): String {
    val level = torchStrength.coerceIn(1, maxStrength.coerceAtLeast(1))
    val percent = (level.toFloat() / standardMaxStrength.coerceAtLeast(1) * 100).roundToInt().coerceAtLeast(1)
    return "$percent%"
}

/** Strength as a perimeter fraction; binary flashlights show a full outline when on. */
fun getFlashlightStrengthFraction(torchStrength: Int, maxStrength: Int): Float =
    if (maxStrength > 1) torchStrength.coerceIn(1, maxStrength).toFloat() / maxStrength else 1f

@Composable
fun rememberCompactFlashlightStatus(): String {
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val standardMaximum by FlashlightController.standardMaxStrength.collectAsState()
    return remember(torchStrength, maxStrength, standardMaximum) {
        getFlashlightPercentText(torchStrength, maxStrength, standardMaximum)
    }
}

/**
 * Minimized pill content when Flashlight is active:
 * Symmetrically aligned capsule keeping the camera cutout centered.
 */
@Composable
fun CompactFlashlightContent(
    cutoutDiameterDp: Dp,
    onExpand: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    status: String = rememberCompactFlashlightStatus(),
) {
    val accentColor = flashlightAccentColor()
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

    val icon = @Composable {
        Icon(
            imageVector = Icons.Default.FlashlightOn,
            contentDescription = "Flashlight Active",
            tint = accentColor.copy(alpha = pulseAlpha),
            modifier = Modifier.size(18.dp),
        )
    }

    val isPercentage = status.contains('%')
    val formattedStatus = remember(status) { formatCompactStatus(status) }

    val summary = @Composable {
        Text(
            text = formattedStatus,
            color = accentColor,
            fontSize = if (isPercentage) 10.5.sp else 11.5.sp,
            fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (status.isNotEmpty()) {
                    Text(
                        text = formattedStatus,
                        color = accentColor,
                        fontSize = if (isPercentage) 9.5.sp else 10.sp,
                        fontWeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontWeight,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.verticalActivityLabel(),
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (status.isNotEmpty()) {
                    summary()
                }
            }
        }
    }
}

/**
 * Taller and narrower Pixel-inspired Dynamic Island Flashlight Card:
 * - Background provided by the island surface.
 * - Standard Material Flashlight icon.
 * - Dynamic colors extracted directly from system wallpaper via Material You (dynamicDarkColorScheme).
 * - Upward-projecting light beam cone originating from the Material Flashlight icon.
 * - Horizontal pill slider bar at the top of the beam with continuous vertical drag control.
 * - Tapping on the expanded island toggles / turns off the flashlight.
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
    topContentInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()

    val currentMaxStrength by rememberUpdatedState(maxStrength)
    val currentIsFlashlightOn by rememberUpdatedState(isFlashlightOn)

    // Dynamic Material You system accent colors
    val accentColor = flashlightAccentColor()

    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    if (expandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER) {
        Row(
            modifier = modifier.fillMaxSize().padding(
                start = 16.dp, end = 16.dp,
                top = maxOf(cutoutDiameterDp, topContentInset) + 8.dp, bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { FlashlightController.toggleFlashlight() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = if (isFlashlightOn) "Turn off flashlight" else "Turn on flashlight",
                    tint = if (isFlashlightOn) accentColor else Color.Gray,
                    modifier = Modifier.size(28.dp),
                )
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = torchStrength.toFloat().coerceIn(1f, maxStrength.coerceAtLeast(2).toFloat()),
                onValueChange = { FlashlightController.setStrength(it.roundToInt().coerceIn(1, maxStrength.coerceAtLeast(1))) },
                onValueChangeFinished = { FlashlightController.flushStrength(FlashlightController.torchStrength.value) },
                valueRange = 1f..maxStrength.coerceAtLeast(2).toFloat(),
                enabled = isFlashlightOn && maxStrength > 1,
            )
        }
        return
    }
    // Whether user is actively dragging the slider
    var isDragging by remember { mutableStateOf(false) }

    // Normalized intensity fraction (0.05f to 1.0f)
    var intensityFraction by remember {
        val initial = if (maxStrength > 1) {
            (torchStrength.toFloat() / maxStrength).coerceIn(0.05f, 1.0f)
        } else {
            1.0f
        }
        mutableFloatStateOf(initial)
    }

    // Only sync from external torchStrength when user is NOT actively dragging
    LaunchedEffect(torchStrength, maxStrength) {
        if (!isDragging && maxStrength > 1) {
            intensityFraction = (torchStrength.toFloat() / maxStrength).coerceIn(0.05f, 1.0f)
        }
    }

    // Smooth transition for on/off toggle (cone fades in/out)
    val onOffProgress by animateFloatAsState(
        targetValue = if (isFlashlightOn) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "anim_on_off",
    )

    var lastHapticStep by remember { mutableIntStateOf((intensityFraction * 4).toInt()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        FlashlightController.toggleFlashlight()
                    }
                )
            },
    ) {
        // Interactive Light Cone, Slider Bar & Material Flashlight Icon Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = maxOf((cutoutDiameterDp - 4.dp).coerceAtLeast(16.dp), topContentInset),
                    bottom = 16.dp,
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            if (!currentIsFlashlightOn) {
                                FlashlightController.turnOn()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val finalStrength = (intensityFraction * currentMaxStrength).roundToInt().coerceIn(1, currentMaxStrength)
                            FlashlightController.flushStrength(finalStrength)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            // Vertical drag: Dragging UP decreases Y -> increases brightness
                            val deltaFraction = -dragAmount.y / 150f
                            val newFraction = (intensityFraction + deltaFraction).coerceIn(0.05f, 1.0f)
                            if (abs(newFraction - intensityFraction) > 0.005f) {
                                val currentStep = (newFraction * 4).toInt()
                                if (currentStep != lastHapticStep || newFraction >= 0.99f || newFraction <= 0.06f) {
                                    lastHapticStep = currentStep
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                intensityFraction = newFraction
                                val newLevel = (newFraction * currentMaxStrength).roundToInt().coerceIn(1, currentMaxStrength)
                                FlashlightController.setStrength(newLevel)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Light Cone Canvas and Horizontal Slider Bar
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val centerX = size.width / 2f
                val canvasHeight = size.height

                // Material Flashlight icon height at bottom (~36dp + 4dp padding)
                val iconAreaHeight = 44.dp.toPx()
                val beamOriginY = canvasHeight - iconAreaHeight
                val beamBaseWidth = 16.dp.toPx()

                // Vertical Travel Range for the horizontal slider bar
                val maxTravelY = 6.dp.toPx() // Topmost position
                val minTravelY = beamOriginY - 16.dp.toPx() // Lowest position

                val currentSliderY = minTravelY - ((minTravelY - maxTravelY) * intensityFraction.coerceIn(0.05f, 1.0f))

                // Width of top horizontal slider bar & beam based on height
                val travelProgress = ((minTravelY - currentSliderY) / (minTravelY - maxTravelY)).coerceIn(0f, 1f)
                val topBeamWidth = (42.dp.toPx()) + (68.dp.toPx() * travelProgress)
                val barHeight = 6.dp.toPx()

                // 1. Upward Projecting Light Cone
                val beamPath = Path().apply {
                    moveTo(centerX - beamBaseWidth / 2f, beamOriginY)
                    lineTo(centerX - topBeamWidth / 2f, currentSliderY)
                    lineTo(centerX + topBeamWidth / 2f, currentSliderY)
                    lineTo(centerX + beamBaseWidth / 2f, beamOriginY)
                    close()
                }

                if (isFlashlightOn && onOffProgress > 0.02f) {
                    if (expandedStyle == OverlayPreferences.FlashlightExpandedStyle.BLURRED_BEAM) {
                        // Approximate a Gaussian blur in horizontal bands: the light is sharp
                        // at its source and diffuses progressively toward the wide end.
                        val beamHeight = (beamOriginY - currentSliderY).coerceAtLeast(1f)
                        val bands = 24
                        for (band in 0 until bands) {
                            val progress = (band + 0.5f) / bands
                            val radius = 16.dp.toPx() * (1f - progress) * (1f - progress)
                            clipRect(top = currentSliderY + beamHeight * band / bands,
                                bottom = currentSliderY + beamHeight * (band + 1) / bands + 0.5f) {
                                for (sample in -6..6) {
                                    val weight = kotlin.math.exp(-sample * sample / 8f) / 5.008f
                                    translate(left = sample * radius / 3f) {
                                        drawPath(
                                            path = beamPath,
                                            brush = Brush.verticalGradient(
                                                listOf(accentColor.copy(alpha = 0.85f * onOffProgress * weight),
                                                    accentColor.copy(alpha = 0.12f * onOffProgress * weight)),
                                                startY = currentSliderY, endY = beamOriginY,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    drawPath(
                        path = beamPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.85f * onOffProgress),
                                accentColor.copy(alpha = 0.45f * onOffProgress),
                                accentColor.copy(alpha = 0.12f * onOffProgress),
                            ),
                            startY = currentSliderY,
                            endY = beamOriginY,
                        ),
                    )
                    }
                } else {
                    // Subtle translucent guide when unlit
                    drawPath(
                        path = beamPath,
                        color = Color.White.copy(alpha = 0.04f),
                    )
                }

                // 2. Horizontal Slider Bar (Pill Handle at top of beam)
                val sliderColor = if (isFlashlightOn) accentColor else Color.White.copy(alpha = 0.35f)
                drawRoundRect(
                    color = sliderColor,
                    topLeft = Offset(centerX - topBeamWidth / 2f, currentSliderY - barHeight / 2f),
                    size = Size(topBeamWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
                )
            }

            // Regular Material Flashlight Icon at bottom of beam
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    FlashlightController.toggleFlashlight()
                },
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = if (isFlashlightOn) "Turn Off" else "Turn On",
                    tint = if (isFlashlightOn) accentColor else Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
