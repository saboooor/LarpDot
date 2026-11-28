package ca.saboor.larpdot.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomIslandColor
import ca.saboor.larpdot.ui.theme.LarpIcons
import kotlin.math.roundToInt

internal fun Int.toRgbHex(): String = (this and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

internal fun String.parseRgbHex(): Int? {
    val digits = trim().removePrefix("#")
    return digits.takeIf { it.length == 6 && it.all { ch -> ch.digitToIntOrNull(16) != null } }?.toIntOrNull(16)
}

internal fun Color.toRgbInt(): Int = toArgb() and 0xFFFFFF

internal val defaultColorPresetRgbs: List<Int> = listOf(
    0xFFFFFF,
    0xB0BEC5,
    0x000000,
    0xEF5350,
    0xFF7043,
    0xFFA726,
    0xFFEE58,
    0x66BB6A,
    0x26A69A,
    0x29B6F6,
    0x42A5F5,
    0x5C6BC0,
    0xAB47BC,
    0xEC407A,
)

internal fun resolveCustomIslandColor(
    colorChoice: CustomIslandColor,
    materialYouColor: Color,
    dominantColor: Color?,
): Color = when (colorChoice.mode) {
    CustomColorMode.MATERIAL_YOU -> materialYouColor
    CustomColorMode.DOMINANT -> dominantColor ?: materialYouColor
    CustomColorMode.CUSTOM -> Color(colorChoice.rgb or 0xFF000000.toInt())
}

internal data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toRgb(): Int = AndroidColor.HSVToColor(floatArrayOf(hue % 360f, saturation, value)) and 0xFFFFFF

    companion object {
        fun fromRgb(rgb: Int, fallbackHue: Float = 0f): HsvColor {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(rgb or 0xFF000000.toInt(), hsv)
            return HsvColor(if (hsv[1] == 0f) fallbackHue else hsv[0], hsv[1], hsv[2])
        }
    }
}

private val hueSpectrum = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

@Composable
internal fun ColorPicker(color: HsvColor, onColorChange: (HsvColor) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(128.dp).clip(shape)
                .pickAt { x, y -> onColorChange(color.copy(saturation = x, value = 1f - y)) }
                .semantics {
                    contentDescription = "Saturation and brightness"
                    stateDescription = "${(color.saturation * 100).roundToInt()}% saturation, " +
                        "${(color.value * 100).roundToInt()}% brightness"
                    customActions = listOf(
                        CustomAccessibilityAction("Increase saturation") {
                            onColorChange(color.copy(saturation = (color.saturation + 0.05f).coerceAtMost(1f)))
                            true
                        },
                        CustomAccessibilityAction("Decrease saturation") {
                            onColorChange(color.copy(saturation = (color.saturation - 0.05f).coerceAtLeast(0f)))
                            true
                        },
                        CustomAccessibilityAction("Increase brightness") {
                            onColorChange(color.copy(value = (color.value + 0.05f).coerceAtMost(1f)))
                            true
                        },
                        CustomAccessibilityAction("Decrease brightness") {
                            onColorChange(color.copy(value = (color.value - 0.05f).coerceAtLeast(0f)))
                            true
                        },
                    )
                },
        ) {
            val hue = Color.hsv(color.hue % 360f, 1f, 1f)
            drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawPickerHandle(Offset(color.saturation * size.width, (1f - color.value) * size.height))
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .pickAt { x, _ -> onColorChange(color.copy(hue = x * 360f)) }
                .semantics {
                    contentDescription = "Hue"
                    progressBarRangeInfo = ProgressBarRangeInfo(color.hue, 0f..360f)
                    setProgress {
                        onColorChange(color.copy(hue = it.coerceIn(0f, 360f)))
                        true
                    }
                },
        ) {
            val trackHeight = 20.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(hueSpectrum),
                topLeft = Offset(0f, (size.height - trackHeight) / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawPickerHandle(Offset(color.hue / 360f * size.width, size.height / 2f))
        }
    }
}

@Composable
internal fun LarpColorPickerContent(
    selection: CustomIslandColor,
    onSelectionChange: (CustomIslandColor) -> Unit,
    supportsDynamicModes: Boolean = true,
    dominantColor: Color? = null,
    dominantLabel: String = "App Color",
    onPickFromScreen: (() -> Unit)? = null,
    hexText: String? = null,
    onHexTextChange: ((String) -> Unit)? = null,
    hsvOverride: HsvColor? = null,
    onHsvChange: ((HsvColor) -> Unit)? = null,
) {
    val materialPrimary = MaterialTheme.colorScheme.primary
    val materialSecondary = MaterialTheme.colorScheme.secondary
    val materialTertiary = MaterialTheme.colorScheme.tertiary
    val resolvedColor = resolveCustomIslandColor(selection, materialPrimary, dominantColor)
    val resolvedRgb = resolvedColor.toRgbInt()

    var internalHsv by remember { mutableStateOf(HsvColor.fromRgb(resolvedRgb)) }
    val activeHsv = hsvOverride ?: internalHsv
    var internalHex by remember { mutableStateOf(resolvedRgb.toRgbHex()) }
    val activeHex = hexText ?: internalHex

    LaunchedEffect(resolvedRgb, selection.mode) {
        if (hsvOverride == null && internalHsv.toRgb() != resolvedRgb) {
            internalHsv = HsvColor.fromRgb(resolvedRgb, fallbackHue = internalHsv.hue)
        }
        if (hexText == null && internalHex.parseRgbHex() != resolvedRgb) {
            internalHex = resolvedRgb.toRgbHex()
        }
    }

    fun selectFixedRgb(rgb: Int) {
        val clamped = rgb and 0xFFFFFF
        val nextHsv = HsvColor.fromRgb(clamped, fallbackHue = activeHsv.hue)
        if (onHsvChange != null) onHsvChange(nextHsv) else internalHsv = nextHsv
        if (onHexTextChange != null) onHexTextChange(clamped.toRgbHex()) else internalHex = clamped.toRgbHex()
        onSelectionChange(CustomIslandColor(mode = CustomColorMode.CUSTOM, rgb = clamped))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Contextual & Dynamic Presets (Material You, Dominant/App Color, Pick from screen)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorOptionChip(
                label = "Material You",
                swatchColor = materialPrimary,
                selected = if (supportsDynamicModes) {
                    selection.mode == CustomColorMode.MATERIAL_YOU
                } else {
                    resolvedRgb == materialPrimary.toRgbInt()
                },
                onClick = {
                    if (supportsDynamicModes) {
                        onSelectionChange(selection.copy(mode = CustomColorMode.MATERIAL_YOU))
                    } else {
                        selectFixedRgb(materialPrimary.toRgbInt())
                    }
                },
            )
            if (dominantColor != null) {
                ColorOptionChip(
                    label = dominantLabel,
                    swatchColor = dominantColor,
                    selected = if (supportsDynamicModes) {
                        selection.mode == CustomColorMode.DOMINANT
                    } else {
                        resolvedRgb == dominantColor.toRgbInt()
                    },
                    onClick = {
                        if (supportsDynamicModes) {
                            onSelectionChange(selection.copy(mode = CustomColorMode.DOMINANT))
                        } else {
                            selectFixedRgb(dominantColor.toRgbInt())
                        }
                    },
                )
            }
            if (onPickFromScreen != null) {
                Surface(
                    onClick = onPickFromScreen,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = LarpIcons.Colorize,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Pick from screen", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Preset color swatches (including Material You secondary/tertiary + standard palette)
        val presets = remember(materialPrimary, materialSecondary, materialTertiary, dominantColor) {
            buildList {
                add(materialPrimary.toRgbInt())
                add(materialSecondary.toRgbInt())
                add(materialTertiary.toRgbInt())
                dominantColor?.let { add(it.toRgbInt()) }
                addAll(defaultColorPresetRgbs)
            }.distinct()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { presetRgb ->
                val isSelected = (!supportsDynamicModes || selection.mode == CustomColorMode.CUSTOM) &&
                    resolvedRgb == presetRgb
                val swatchColor = Color(presetRgb or 0xFF000000.toInt())
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickable(role = Role.RadioButton) { selectFixedRgb(presetRgb) }
                        .semantics { contentDescription = "Preset color #${presetRgb.toRgbHex()}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = LarpIcons.Check,
                            contentDescription = null,
                            tint = if (swatchColor.luminance() > 0.45f) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // HSV picker
        ColorPicker(activeHsv) { nextHsv ->
            val nextRgb = nextHsv.toRgb()
            if (onHsvChange != null) onHsvChange(nextHsv) else internalHsv = nextHsv
            if (onHexTextChange != null) onHexTextChange(nextRgb.toRgbHex()) else internalHex = nextRgb.toRgbHex()
            onSelectionChange(CustomIslandColor(mode = CustomColorMode.CUSTOM, rgb = nextRgb))
        }

        // Hex input
        OutlinedTextField(
            value = activeHex,
            onValueChange = { raw ->
                val trimmed = raw.take(7)
                if (onHexTextChange != null) onHexTextChange(trimmed) else internalHex = trimmed
                trimmed.parseRgbHex()?.let { parsed ->
                    val nextHsv = HsvColor.fromRgb(parsed, fallbackHue = activeHsv.hue)
                    if (onHsvChange != null) onHsvChange(nextHsv) else internalHsv = nextHsv
                    onSelectionChange(CustomIslandColor(mode = CustomColorMode.CUSTOM, rgb = parsed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hex") },
            placeholder = { Text("FFFFFF") },
            singleLine = true,
            leadingIcon = {
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier
                        .size(20.dp)
                        .background(resolvedColor, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                )
            },
        )
    }
}

@Composable
private fun ColorOptionChip(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun LarpColorPickerDialog(
    title: String,
    initial: CustomIslandColor,
    supportsDynamicModes: Boolean = true,
    dominantColor: Color? = null,
    dominantLabel: String = "App Color",
    onDismiss: () -> Unit,
    onConfirm: (CustomIslandColor) -> Unit,
) {
    val activity = LocalActivity.current
    val materialPrimary = MaterialTheme.colorScheme.primary
    var draft by remember(initial) { mutableStateOf(initial) }
    var captureRequested by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(captureRequested) {
        if (!captureRequested) return@LaunchedEffect
        // Let the dialog leave the window before taking the screenshot.
        withFrameNanos { }
        withFrameNanos { }
        captureScreenBitmap(activity) { bitmap ->
            captureRequested = false
            if (bitmap != null) {
                capturedBitmap = bitmap
            }
        }
    }

    val activeScreenCapture = capturedBitmap
    if (activeScreenCapture != null) {
        val currentRgb = resolveCustomIslandColor(draft, materialPrimary, dominantColor).toRgbInt()
        ScreenColorPickerOverlay(
            bitmap = activeScreenCapture,
            initialRgb = currentRgb,
            onPickFromOtherApp = if (canPickFromOtherApps()) {
                {
                    capturedBitmap = null
                    ExternalScreenColorSession.start(activity) { externalBitmap ->
                        if (externalBitmap != null) {
                            capturedBitmap = externalBitmap
                        }
                    }
                }
            } else null,
            onConfirm = { pickedRgb ->
                draft = CustomIslandColor(mode = CustomColorMode.CUSTOM, rgb = pickedRgb)
                capturedBitmap = null
            },
            onDismiss = { capturedBitmap = null },
        )
        return
    }

    if (captureRequested) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LarpColorPickerContent(
                    selection = draft,
                    onSelectionChange = { draft = it },
                    supportsDynamicModes = supportsDynamicModes,
                    dominantColor = dominantColor,
                    dominantLabel = dominantLabel,
                    onPickFromScreen = { captureRequested = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun LarpColorPickerButton(
    label: String,
    selection: CustomIslandColor,
    onSelectionChange: (CustomIslandColor) -> Unit,
    modifier: Modifier = Modifier,
    supportsDynamicModes: Boolean = true,
    dominantColor: Color? = null,
    dominantLabel: String = "App Color",
) {
    var open by remember { mutableStateOf(false) }
    val resolvedColor = resolveCustomIslandColor(selection, MaterialTheme.colorScheme.primary, dominantColor)
    val summary = when {
        supportsDynamicModes && selection.mode == CustomColorMode.MATERIAL_YOU -> "Material You"
        supportsDynamicModes && selection.mode == CustomColorMode.DOMINANT -> dominantLabel
        else -> "#${resolvedColor.toRgbInt().toRgbHex()}"
    }

    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shape = CircleShape
            Box(
                Modifier
                    .size(18.dp)
                    .background(resolvedColor, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            Text("$label · $summary")
        }
    }

    if (open) {
        LarpColorPickerDialog(
            title = label,
            initial = selection,
            supportsDynamicModes = supportsDynamicModes,
            dominantColor = dominantColor,
            dominantLabel = dominantLabel,
            onDismiss = { open = false },
            onConfirm = { chosen ->
                onSelectionChange(chosen)
                open = false
            },
        )
    }
}

@Composable
private fun Modifier.pickAt(onPick: (Float, Float) -> Unit): Modifier {
    val pick = rememberUpdatedState(onPick)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            fun select(position: Offset) {
                if (size.width == 0 || size.height == 0) return
                pick.value(
                    (position.x / size.width).coerceIn(0f, 1f),
                    (position.y / size.height).coerceIn(0f, 1f),
                )
            }
            down.consume()
            select(down.position)
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                select(change.position)
                change.consume()
            } while (change.pressed)
        }
    }
}

private fun DrawScope.drawPickerHandle(position: Offset) {
    val radius = 7.dp.toPx()
    val center = Offset(
        position.x.coerceIn(minOf(radius, size.width / 2f), maxOf(size.width - radius, size.width / 2f)),
        position.y.coerceIn(minOf(radius, size.height / 2f), maxOf(size.height - radius, size.height / 2f)),
    )
    drawCircle(Color.Black, radius, center)
    drawCircle(Color.White, radius - 1.dp.toPx(), center)
    drawCircle(Color.Black, radius - 3.dp.toPx(), center)
}
