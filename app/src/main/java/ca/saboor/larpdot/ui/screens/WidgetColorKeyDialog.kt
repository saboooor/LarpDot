package ca.saboor.larpdot.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomIslandColor
import ca.saboor.larpdot.service.CustomIslandWidgets
import ca.saboor.larpdot.service.WidgetColorKey
import ca.saboor.larpdot.ui.components.ExternalScreenColorSession
import ca.saboor.larpdot.ui.components.HsvColor
import ca.saboor.larpdot.ui.components.LarpColorPickerContent
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.ScreenColorPickerOverlay
import ca.saboor.larpdot.ui.components.WidgetPreviewSampler
import ca.saboor.larpdot.ui.components.canPickFromOtherApps
import ca.saboor.larpdot.ui.components.captureScreenBitmap
import ca.saboor.larpdot.ui.components.parseRgbHex
import ca.saboor.larpdot.ui.components.toRgbHex
import ca.saboor.larpdot.ui.overlay.CustomIslandWidget
import ca.saboor.larpdot.ui.overlay.rememberAppDominantColor
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.ceil
import kotlin.math.roundToInt

private val colorPickerSaver = listSaver<HsvColor, Float>(
    save = { listOf(it.hue, it.saturation, it.value) },
    restore = { HsvColor(it[0], it[1], it[2]) },
)

@Composable
internal fun WidgetColorKeyDialog(
    widgetId: Int,
    current: WidgetColorKey?,
    onDismiss: () -> Unit,
    onApply: (WidgetColorKey?) -> Unit,
) {
    val activity = LocalActivity.current
    val initial = current ?: WidgetColorKey()
    var enabled by rememberSaveable { mutableStateOf(true) }
    var hex by rememberSaveable { mutableStateOf(initial.rgb.toRgbHex()) }
    var pickerColor by rememberSaveable(stateSaver = colorPickerSaver) { mutableStateOf(HsvColor.fromRgb(initial.rgb)) }
    var tolerance by rememberSaveable { mutableIntStateOf(initial.tolerancePercent) }
    var eyedropperMode by rememberSaveable { mutableStateOf(false) }
    var dragSample by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    var screenCaptureRequested by remember { mutableStateOf(false) }
    var screenCaptureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val sampler = remember(widgetId) { WidgetPreviewSampler() }
    val widgetPackage = remember(widgetId) { CustomIslandWidgets.info(widgetId)?.provider?.packageName }
    val widgetAppColor = rememberAppDominantColor(widgetPackage)

    DisposableEffect(sampler) {
        onDispose { sampler.releaseSnapshot() }
    }

    LaunchedEffect(screenCaptureRequested) {
        if (!screenCaptureRequested) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        captureScreenBitmap(activity) { bitmap ->
            screenCaptureRequested = false
            if (bitmap != null) {
                screenCaptureBitmap = bitmap
            }
        }
    }

    val rgb = hex.parseRgbHex()
    val filter = if (enabled) rgb?.let { WidgetColorKey(it, tolerance) } else null

    fun applySampledRgb(sampledRgb: Int) {
        hex = sampledRgb.toRgbHex()
        pickerColor = HsvColor.fromRgb(sampledRgb, fallbackHue = pickerColor.hue)
    }

    val activeScreenBitmap = screenCaptureBitmap
    if (activeScreenBitmap != null) {
        ScreenColorPickerOverlay(
            bitmap = activeScreenBitmap,
            initialRgb = rgb ?: pickerColor.toRgb(),
            onPickFromOtherApp = if (canPickFromOtherApps()) {
                {
                    screenCaptureBitmap = null
                    ExternalScreenColorSession.start(activity) { externalBitmap ->
                        if (externalBitmap != null) {
                            screenCaptureBitmap = externalBitmap
                        }
                    }
                }
            } else null,
            onConfirm = { pickedRgb ->
                applySampledRgb(pickedRgb)
                screenCaptureBitmap = null
            },
            onDismiss = { screenCaptureBitmap = null },
        )
        return
    }

    if (screenCaptureRequested) return

    val previewShape = RoundedCornerShape(12.dp)
    val previewModifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(previewShape)
        .then(
            if (enabled && eyedropperMode) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, previewShape)
            } else Modifier,
        )
        .pointerInput(enabled, sampler) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!sampler.prepareSnapshot()) return@awaitEachGesture
                try {
                    fun sampleAt(position: Offset) {
                        if (size.width == 0 || size.height == 0) return
                        val clamped = Offset(
                            position.x.coerceIn(0f, size.width.toFloat()),
                            position.y.coerceIn(0f, size.height.toFloat()),
                        )
                        val sampled = sampler.sampleRgb(clamped.x / size.width, clamped.y / size.height)
                        if (sampled != null) {
                            applySampledRgb(sampled)
                            dragSample = clamped to sampled
                        } else if (dragSample != null) {
                            dragSample = clamped to dragSample!!.second
                        }
                    }
                    down.consume()
                    sampleAt(down.position)
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        sampleAt(change.position)
                        change.consume()
                    } while (change.pressed)
                } finally {
                    sampler.releaseSnapshot()
                    dragSample = null
                    if (eyedropperMode) eyedropperMode = false
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color to Transparency") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(previewModifier) {
                    TransparencyGrid(Modifier.fillMaxSize())
                    CustomIslandWidget(
                        widgetId = widgetId,
                        interactive = false,
                        modifier = Modifier.fillMaxSize(),
                        colorKey = if (eyedropperMode) null else filter,
                        sampler = sampler,
                        updateProviderSize = false,
                    )
                    dragSample?.let { (point, sampledRgb) ->
                        Canvas(Modifier.fillMaxSize()) {
                            val ringRadius = 14.dp.toPx()
                            val dotRadius = 3.dp.toPx()
                            val swatchCenter = Offset(
                                point.x.coerceIn(ringRadius + 4.dp.toPx(), (size.width - ringRadius - 4.dp.toPx()).coerceAtLeast(ringRadius)),
                                (point.y - 26.dp.toPx()).coerceIn(ringRadius + 4.dp.toPx(), (size.height - ringRadius - 4.dp.toPx()).coerceAtLeast(ringRadius)),
                            )
                            drawCircle(Color.Black, dotRadius + 1.5.dp.toPx(), point)
                            drawCircle(Color.White, dotRadius, point)
                            drawCircle(Color(sampledRgb or 0xFF000000.toInt()), ringRadius, swatchCenter)
                            drawCircle(Color.White, ringRadius, swatchCenter, style = Stroke(2.5.dp.toPx()))
                            drawCircle(Color.Black, ringRadius + 1.5.dp.toPx(), swatchCenter, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            !enabled -> "Widget preview"
                            eyedropperMode -> "Showing original colors — tap or drag to pick"
                            else -> "Tap or drag preview to pick a color"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (enabled) {
                        FilledTonalIconToggleButton(
                            checked = eyedropperMode,
                            onCheckedChange = { eyedropperMode = it },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = LarpIcons.Colorize,
                                contentDescription = "Pick color from widget",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                LarpSwitchRow("Enable Filter", enabled, {
                    enabled = it
                    if (!it) eyedropperMode = false
                })
                if (enabled) {
                    LarpColorPickerContent(
                        selection = CustomIslandColor(
                            mode = CustomColorMode.CUSTOM,
                            rgb = rgb ?: pickerColor.toRgb(),
                        ),
                        onSelectionChange = { choice ->
                            pickerColor = HsvColor.fromRgb(choice.rgb, fallbackHue = pickerColor.hue)
                            hex = choice.rgb.toRgbHex()
                        },
                        supportsDynamicModes = false,
                        dominantColor = widgetAppColor,
                        dominantLabel = "App Color",
                        onPickFromScreen = { screenCaptureRequested = true },
                        hexText = hex,
                        onHexTextChange = { value ->
                            hex = value.take(7)
                            hex.parseRgbHex()?.let { parsed ->
                                pickerColor = HsvColor.fromRgb(parsed, fallbackHue = pickerColor.hue)
                            }
                        },
                        hsvOverride = pickerColor,
                        onHsvChange = { next ->
                            pickerColor = next
                            hex = next.toRgb().toRgbHex()
                        },
                    )
                    LarpSliderRow(
                        title = "Tolerance",
                        valueLabel = "$tolerance%",
                        value = tolerance.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { tolerance = it.roundToInt() },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(filter) }, enabled = !enabled || filter != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TransparencyGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF666666))
        val cell = 12.dp.toPx()
        val rows = ceil(size.height / cell).toInt()
        val columns = ceil(size.width / cell).toInt()
        for (row in 0 until rows) {
            for (column in (row % 2) until columns step 2) {
                drawRect(Color(0xFF808080), Offset(column * cell, row * cell), Size(cell, cell))
            }
        }
    }
}
