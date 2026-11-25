package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Shader
import ca.saboor.larpdot.service.OverlayPreferences
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.flashlight.FlashlightMode
import ca.saboor.larpdot.flashlight.formatAutoOffRemaining
import ca.saboor.larpdot.flashlight.nextFlashlightMode
import kotlin.math.abs
import kotlin.math.roundToInt
import ca.saboor.larpdot.ui.theme.LarpMotion

val FlashlightWhite = Color.White
val FlashlightWhiteLight = Color.White
val FlashlightWhiteDim = Color(0xB3FFFFFF)

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
    val mode by FlashlightController.flashlightMode.collectAsState()
    return remember(torchStrength, maxStrength, standardMaximum, mode) {
        if (mode != FlashlightMode.STEADY) mode.label
        else getFlashlightPercentText(torchStrength, maxStrength, standardMaximum)
    }
}

@Composable
internal fun CompactFlashlightIcon(size: Dp, iconAnchor: IslandElementAnchor? = null) {
    Icon(
        imageVector = LarpIcons.FlashlightOn,
        contentDescription = "Flashlight Active",
        tint = FlashlightWhite,
        modifier = Modifier
            .size(size)
            .morphingElement(iconAnchor)
            .drawWithCache {
                val radius = (this.size.minDimension * 0.85f).coerceAtLeast(1f)
                val glow = Brush.radialGradient(
                    0f to FlashlightWhite.copy(alpha = 0.55f),
                    0.45f to FlashlightWhite.copy(alpha = 0.2f),
                    1f to FlashlightWhite.copy(alpha = 0f),
                    radius = radius,
                )
                onDrawBehind {
                    drawCircle(brush = glow, radius = radius)
                }
            },
    )
}

/**
 * Minimized pill content when Flashlight is active:
 * Symmetrically aligned capsule keeping the camera cutout centered.
 */
@Composable
internal fun CompactFlashlightContent(
    cutoutDiameterDp: Dp,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {},
    onFlashlightToggle: () -> Unit = {},
    isLandscape: Boolean = false,
    status: String = rememberCompactFlashlightStatus(),
    showRadialProgress: Boolean = OverlayPreferences.showMinimizedFlashlightOutlineFlow.collectAsState().value,
    onIconClick: (() -> Unit)? = null,
    onRadialClick: (() -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    highlightIcon: Boolean = false,
    highlightRadial: Boolean = false,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
) {
    val accentColor = FlashlightWhite
    val highlightColor = MaterialTheme.colorScheme.primary
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val strengthFraction = getFlashlightStrengthFraction(torchStrength, maxStrength)
    val percentLabel = status.removeSuffix("%").trim()
    val showGauge = showRadialProgress && percentLabel.isNotEmpty()
    val compactRingDiameter = compactBatteryRingDiameter(cutoutDiameterDp)
    val compactRingInset = compactEndInset(cutoutDiameterDp, compactRingDiameter)


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
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                CompactFlashlightIcon(size = 18.dp, iconAnchor = iconAnchor)
            }
            Spacer(modifier = Modifier.height(cutoutDiameterDp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = if (showGauge) compactRingInset else 8.dp),
                contentAlignment = if (showGauge) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                if (showGauge) {
                    RadialProgressGauge(
                        progress = strengthFraction,
                        text = percentLabel,
                        color = FlashlightWhite,
                        diameter = compactRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
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
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CompactFlashlightIcon(size = 18.dp, iconAnchor = iconAnchor)
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
                    .padding(end = if (showGauge) compactRingInset else 8.dp),
                contentAlignment = if (showGauge) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (showGauge) {
                    RadialProgressGauge(
                        progress = strengthFraction,
                        text = percentLabel,
                        color = FlashlightWhite,
                        diameter = compactRingDiameter,
                        modifier = Modifier.morphingElement(progressAnchor),
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
 * - Upward-projecting light beam cone originating from the Material Flashlight icon.
 * - Horizontal pill slider bar at the top of the beam with continuous vertical drag control.
 * - Tapping on the expanded island toggles / turns off the flashlight.
 */
@Composable
internal fun ExpandedFlashlightContent(
    modifier: Modifier = Modifier,
    topContentInset: Dp = 0.dp,
    onBeamClick: (() -> Unit)? = null,
    iconAnchor: IslandElementAnchor? = null,
    progressAnchor: IslandElementAnchor? = null,
) {
    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val torchStrength by FlashlightController.torchStrength.collectAsState()
    val maxStrength by FlashlightController.maxStrength.collectAsState()
    val accentColor = FlashlightWhite
    val showExpandedRadial by OverlayPreferences.showExpandedFlashlightOutlineFlow.collectAsState()
    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val autoOffRemainingMs by FlashlightController.autoOffRemainingMs.collectAsState()
    val flashlightMode by FlashlightController.flashlightMode.collectAsState()

    if (expandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER) {
        ExpandedFlashlightSliderLayout(
            isFlashlightOn = isFlashlightOn,
            torchStrength = torchStrength,
            maxStrength = maxStrength,
            accentColor = accentColor,
            showExpandedRadial = showExpandedRadial,
            topContentInset = topContentInset,
            autoOffRemainingMs = autoOffRemainingMs,
            flashlightMode = flashlightMode,
            iconAnchor = iconAnchor,
            progressAnchor = progressAnchor,
            modifier = modifier,
        )
    } else {
        ExpandedFlashlightBeamLayout(
            isFlashlightOn = isFlashlightOn,
            torchStrength = torchStrength,
            maxStrength = maxStrength,
            accentColor = accentColor,
            showExpandedRadial = showExpandedRadial,
            topContentInset = topContentInset,
            autoOffRemainingMs = autoOffRemainingMs,
            flashlightMode = flashlightMode,
            onBeamClick = onBeamClick,
            iconAnchor = iconAnchor,
            progressAnchor = progressAnchor,
            modifier = modifier,
        )
    }
}

@Composable
private fun FlashlightModeChip(
    mode: FlashlightMode,
    isFlashlightOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isFlashlightOn && mode != FlashlightMode.STEADY
    Box(
        modifier = modifier
            .background(
                color = if (active) FlashlightWhite.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mode.label,
            color = if (active) FlashlightWhite else FlashlightWhiteDim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun FlashlightTorchToggleButton(
    isFlashlightOn: Boolean,
    accentColor: Color,
    inactiveTint: Color,
    showExpandedRadial: Boolean,
    progress: Float,
    buttonSize: Dp,
    iconSize: Dp,
    ringSize: Dp,
    contentDescription: String,
    onClick: () -> Unit,
    iconAnchor: IslandElementAnchor?,
    progressAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isFlashlightOn) LarpIcons.FlashlightOn else LarpIcons.FlashlightOff,
                contentDescription = contentDescription,
                tint = if (isFlashlightOn) accentColor else inactiveTint,
                modifier = Modifier.size(if (showExpandedRadial) 22.dp else iconSize).morphingElement(iconAnchor),
            )
            if (showExpandedRadial) {
                RadialProgressRing(
                    progress = progress,
                    color = accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(ringSize).morphingElement(progressAnchor),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedFlashlightSliderLayout(
    isFlashlightOn: Boolean,
    torchStrength: Int,
    maxStrength: Int,
    accentColor: Color,
    showExpandedRadial: Boolean,
    topContentInset: Dp,
    autoOffRemainingMs: Long?,
    flashlightMode: FlashlightMode,
    iconAnchor: IslandElementAnchor?,
    progressAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val strengthFraction = if (isFlashlightOn) getFlashlightStrengthFraction(torchStrength, maxStrength) else 0f
    Row(
        modifier = modifier.fillMaxSize().padding(
            start = 16.dp, end = 16.dp,
            top = if (topContentInset > 0.dp) topContentInset else 8.dp, bottom = 8.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlashlightTorchToggleButton(
            isFlashlightOn = isFlashlightOn,
            accentColor = accentColor,
            inactiveTint = Color.Gray,
            showExpandedRadial = showExpandedRadial,
            progress = strengthFraction,
            buttonSize = 48.dp,
            iconSize = 28.dp,
            ringSize = 44.dp,
            contentDescription = if (isFlashlightOn) "Turn off flashlight" else "Turn on flashlight",
            onClick = { FlashlightController.toggleFlashlight() },
            iconAnchor = iconAnchor,
            progressAnchor = progressAnchor,
        )
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
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.25f),
            ),
        )
        FlashlightModeChip(
            mode = flashlightMode,
            isFlashlightOn = isFlashlightOn,
            onClick = {
                hapticFeedback.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                FlashlightController.setMode(nextFlashlightMode(flashlightMode))
            },
        )
        if (isFlashlightOn && autoOffRemainingMs != null) {
            Text(
                text = formatAutoOffRemaining(autoOffRemainingMs),
                color = FlashlightWhiteDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ExpandedFlashlightBeamLayout(
    isFlashlightOn: Boolean,
    torchStrength: Int,
    maxStrength: Int,
    accentColor: Color,
    showExpandedRadial: Boolean,
    topContentInset: Dp,
    autoOffRemainingMs: Long?,
    flashlightMode: FlashlightMode,
    onBeamClick: (() -> Unit)?,
    iconAnchor: IslandElementAnchor?,
    progressAnchor: IslandElementAnchor?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val currentMaxStrength by rememberUpdatedState(maxStrength)
    val currentIsFlashlightOn by rememberUpdatedState(isFlashlightOn)
    val beamBlur by OverlayPreferences.flashlightBeamBlurFlow.collectAsState()
    val beamColor = accentColor

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
                            hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
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
                                    hapticFeedback.performIslandHaptic(HapticFeedbackType.TextHandleMove)
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
            val composeBeamPath = remember { Path() }
            val nativeBeamPath = remember { AndroidPath() }
            val nativeCorePath = remember { AndroidPath() }
            val outerBlurFilter = remember(density) {
                BlurMaskFilter(with(density) { 14.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
            val coreBlurFilter = remember(density) {
                BlurMaskFilter(with(density) { 4.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
            // Cache blur LinearGradient shaders across identical draw passes
            val shaderCacheKey = remember { FloatArray(4) { Float.NaN } }

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
                composeBeamPath.rewind()
                composeBeamPath.moveTo(centerX - beamBaseWidth / 2f, beamOriginY)
                composeBeamPath.lineTo(centerX - topBeamWidth / 2f, currentSliderY)
                composeBeamPath.lineTo(centerX + topBeamWidth / 2f, currentSliderY)
                composeBeamPath.lineTo(centerX + beamBaseWidth / 2f, beamOriginY)
                composeBeamPath.close()

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

                        val beamArgb = beamColor.toArgb().toFloat()
                        if (shaderCacheKey[0] != currentSliderY ||
                            shaderCacheKey[1] != beamOriginY ||
                            shaderCacheKey[2] != onOffProgress ||
                            shaderCacheKey[3] != beamArgb
                        ) {
                            shaderCacheKey[0] = currentSliderY
                            shaderCacheKey[1] = beamOriginY
                            shaderCacheKey[2] = onOffProgress
                            shaderCacheKey[3] = beamArgb
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
                        }

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(nativeBeamPath, outerBlurPaint)
                            canvas.nativeCanvas.drawPath(nativeCorePath, coreBlurPaint)
                        }
                    } else {
                        drawPath(
                            path = composeBeamPath,
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
                        path = composeBeamPath,
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

            FlashlightModeChip(
                mode = flashlightMode,
                isFlashlightOn = isFlashlightOn,
                onClick = {
                    hapticFeedback.performIslandHaptic(HapticFeedbackType.TextHandleMove)
                    FlashlightController.setMode(nextFlashlightMode(flashlightMode))
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 10.dp),
            )

            if (isFlashlightOn && autoOffRemainingMs != null) {
                Text(
                    text = formatAutoOffRemaining(autoOffRemainingMs),
                    color = FlashlightWhiteDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp),
                )
            }

            // Regular Material Flashlight Icon at bottom of beam
            FlashlightTorchToggleButton(
                isFlashlightOn = isFlashlightOn,
                accentColor = accentColor,
                inactiveTint = Color.White.copy(alpha = 0.40f),
                showExpandedRadial = showExpandedRadial,
                progress = if (isFlashlightOn) intensityFraction else 0f,
                buttonSize = 44.dp,
                iconSize = 32.dp,
                ringSize = 42.dp,
                contentDescription = if (isFlashlightOn) "Turn Off" else "Turn On",
                onClick = {
                    hapticFeedback.performIslandHaptic(HapticFeedbackType.LongPress)
                    FlashlightController.toggleFlashlight()
                },
                modifier = Modifier.padding(bottom = 2.dp),
                iconAnchor = iconAnchor,
                progressAnchor = progressAnchor,
            )
        }
    }
}
