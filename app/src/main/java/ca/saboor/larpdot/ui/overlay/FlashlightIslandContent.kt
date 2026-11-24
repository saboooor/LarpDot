package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Shader
import ca.saboor.larpdot.service.OverlayPreferences
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
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
import ca.saboor.larpdot.ui.theme.LarpMotion

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
fun getFlashlightPercentValue(torchStrength: Int, maxStrength: Int, standardMaxStrength: Int = maxStrength): Int {
    val level = torchStrength.coerceIn(1, maxStrength.coerceAtLeast(1))
    return (level.toFloat() / standardMaxStrength.coerceAtLeast(1) * 100).roundToInt().coerceAtLeast(1)
}

/** Percentages use the standard torch maximum as 100%; boosted levels may exceed it. */
fun getFlashlightPercentText(torchStrength: Int, maxStrength: Int, standardMaxStrength: Int = maxStrength): String =
    "${getFlashlightPercentValue(torchStrength, maxStrength, standardMaxStrength)}%"

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
    showRadialProgress: Boolean = OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState().value,
    onIconClick: (() -> Unit)? = null,
    onRadialClick: (() -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    highlightIcon: Boolean = false,
    highlightRadial: Boolean = false,
) {
    val accentColor = flashlightAccentColor()
    val highlightColor = MaterialTheme.colorScheme.primary
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val strengthFraction = getFlashlightStrengthFraction(torchStrength, maxStrength)
    val percentLabel = status.removeSuffix("%").trim()
    val showGauge = showRadialProgress && percentLabel.isNotEmpty()
    val compactRingDiameter = compactBatteryRingDiameter(cutoutDiameterDp)
    val compactRingInset = ((cutoutDiameterDp - compactRingDiameter) / 2f).coerceAtLeast(3.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "compact_torch_pulse")
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = LarpMotion.timing(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val icon = @Composable {
        Icon(
            imageVector = LarpIcons.FlashlightOn,
            contentDescription = "Flashlight Active",
            tint = accentColor,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { alpha = pulseAlphaState.value },
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
                    .padding(top = 8.dp, bottom = 2.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = if (showGauge) compactRingInset else 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (showGauge) {
                    RadialProgressGauge(
                        progress = strengthFraction,
                        text = percentLabel,
                        color = accentColor,
                        diameter = compactRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (percentLabel.length >= 3) 6.5.sp else 7.5.sp,
                        showPercentSymbol = false,
                    )
                } else if (status.isNotEmpty()) {
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
                .background(Color.Black)
                .then(if (onBackgroundClick != null) Modifier.clickable(onClick = onBackgroundClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val startHighlightShape = RoundedCornerShape(
                topStart = cutoutDiameterDp / 2f,
                bottomStart = cutoutDiameterDp / 2f,
            )
            val endHighlightShape = RoundedCornerShape(
                topEnd = cutoutDiameterDp / 2f,
                bottomEnd = cutoutDiameterDp / 2f,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (onIconClick != null) Modifier.clickable(onClick = onIconClick) else Modifier)
                    .then(
                        if (highlightIcon) Modifier.border(1.5.dp, highlightColor, startHighlightShape)
                        else Modifier
                    )
                    .padding(start = 8.dp, end = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (onRadialClick != null) Modifier.clickable(onClick = onRadialClick) else Modifier)
                    .then(
                        if (highlightRadial) Modifier.border(1.5.dp, highlightColor, endHighlightShape)
                        else Modifier
                    )
                    .padding(start = 2.dp, end = if (showGauge) compactRingInset else 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (showGauge) {
                    RadialProgressGauge(
                        progress = strengthFraction,
                        text = percentLabel,
                        color = accentColor,
                        diameter = compactRingDiameter,
                        strokeWidth = 2.dp,
                        fontSize = if (percentLabel.length >= 3) 7.sp else 8.sp,
                        showPercentSymbol = false,
                    )
                } else if (status.isNotEmpty()) {
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
    onBeamClick: (() -> Unit)? = null,
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
    val beamBlur by OverlayPreferences.flashlightBeamBlurFlow.collectAsState()
    val beamAccentColor by OverlayPreferences.flashlightBeamAccentColorFlow.collectAsState()
    val beamColor = if (beamAccentColor) accentColor else Color.White
    val showExpandedRadial by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val strengthFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxStrength) else 0f

    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    if (expandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER) {
        Row(
            modifier = modifier.fillMaxSize().padding(
                start = 16.dp, end = 16.dp,
                top = if (topContentInset > 0.dp) topContentInset else 8.dp, bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { FlashlightController.toggleFlashlight() }, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFlashlightOn) LarpIcons.FlashlightOn else LarpIcons.FlashlightOff,
                        contentDescription = if (isFlashlightOn) "Turn off flashlight" else "Turn on flashlight",
                        tint = if (isFlashlightOn) accentColor else Color.Gray,
                        modifier = Modifier.size(if (showExpandedRadial) 22.dp else 28.dp),
                    )
                    if (showExpandedRadial) {
                        RadialProgressRing(
                            progress = strengthFraction,
                            color = accentColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            val sliderMax = maxStrength.coerceAtLeast(2).toFloat()
            val sliderValue = torchStrength.toFloat().coerceIn(1f, sliderMax)
            val sliderState = remember(sliderMax) {
                SliderState(value = sliderValue, trackRange = 1f..sliderMax)
            }
            sliderState.value = sliderValue
            Slider(
                state = sliderState,
                modifier = Modifier.weight(1f),
                onValueChange = { FlashlightController.setStrength(it.roundToInt().coerceIn(1, maxStrength.coerceAtLeast(1))) },
                onValueChangeFinished = { FlashlightController.flushStrength(FlashlightController.torchStrength.value) },
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
        animationSpec = LarpMotion.fade(if (isFlashlightOn) 260 else 200),
        label = "anim_on_off",
    )

    var lastHapticStep by remember { mutableIntStateOf((intensityFraction * 4).toInt()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onBeamClick) {
                detectTapGestures(
                    onTap = {
                        if (onBeamClick != null) {
                            onBeamClick()
                        } else {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            FlashlightController.toggleFlashlight()
                        }
                    }
                )
            },
    ) {
        // Interactive Light Cone, Slider Bar & Material Flashlight Icon Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (topContentInset > 0.dp) topContentInset else 16.dp,
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
            val outerBlurPaint = remember {
                AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.FILL
                }
            }
            val coreBlurPaint = remember {
                AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    style = AndroidPaint.Style.FILL
                }
            }
            val nativeBeamPath = remember { AndroidPath() }
            val nativeCorePath = remember { AndroidPath() }
            val outerBlurFilter = remember(density) {
                BlurMaskFilter(with(density) { 14.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
            val coreBlurFilter = remember(density) {
                BlurMaskFilter(with(density) { 4.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }

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

                if (onOffProgress > 0.002f) {
                    if (beamBlur) {
                        nativeBeamPath.rewind()
                        nativeBeamPath.moveTo(centerX - beamBaseWidth / 2f, beamOriginY)
                        nativeBeamPath.lineTo(centerX - topBeamWidth / 2f, currentSliderY)
                        nativeBeamPath.lineTo(centerX + topBeamWidth / 2f, currentSliderY)
                        nativeBeamPath.lineTo(centerX + beamBaseWidth / 2f, beamOriginY)
                        nativeBeamPath.close()

                        val coreTopWidth = topBeamWidth * 0.62f
                        nativeCorePath.rewind()
                        nativeCorePath.moveTo(centerX - beamBaseWidth * 0.45f, beamOriginY)
                        nativeCorePath.lineTo(centerX - coreTopWidth / 2f, currentSliderY)
                        nativeCorePath.lineTo(centerX + coreTopWidth / 2f, currentSliderY)
                        nativeCorePath.lineTo(centerX + beamBaseWidth * 0.45f, beamOriginY)
                        nativeCorePath.close()

                        outerBlurPaint.maskFilter = outerBlurFilter
                        outerBlurPaint.shader = LinearGradient(
                            0f, currentSliderY, 0f, beamOriginY,
                            beamColor.copy(alpha = 0.72f * onOffProgress).toArgb(),
                            beamColor.copy(alpha = 0.10f * onOffProgress).toArgb(),
                            Shader.TileMode.CLAMP,
                        )
                        coreBlurPaint.maskFilter = coreBlurFilter
                        coreBlurPaint.shader = LinearGradient(
                            0f, currentSliderY, 0f, beamOriginY,
                            beamColor.copy(alpha = 0.34f * onOffProgress).toArgb(),
                            beamColor.copy(alpha = 0.22f * onOffProgress).toArgb(),
                            Shader.TileMode.CLAMP,
                        )

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(nativeBeamPath, outerBlurPaint)
                            canvas.nativeCanvas.drawPath(nativeCorePath, coreBlurPaint)
                        }
                    } else {
                    drawPath(
                        path = beamPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                beamColor.copy(alpha = 0.85f * onOffProgress),
                                beamColor.copy(alpha = 0.45f * onOffProgress),
                                beamColor.copy(alpha = 0.12f * onOffProgress),
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
                val sliderColor = if (isFlashlightOn) beamColor else Color.White.copy(alpha = 0.35f)
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
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFlashlightOn) LarpIcons.FlashlightOn else LarpIcons.FlashlightOff,
                        contentDescription = if (isFlashlightOn) "Turn Off" else "Turn On",
                        tint = if (isFlashlightOn) accentColor else Color.White.copy(alpha = 0.40f),
                        modifier = Modifier.size(if (showExpandedRadial) 22.dp else 32.dp),
                    )
                    if (showExpandedRadial) {
                        RadialProgressRing(
                            progress = if (isFlashlightOn) intensityFraction else 0f,
                            color = accentColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
            }
        }
    }
}
