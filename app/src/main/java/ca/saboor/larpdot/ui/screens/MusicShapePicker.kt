package ca.saboor.larpdot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.nestedAlbumArtShape
import kotlin.math.roundToInt

private enum class ShapeCategory(val label: String) {
    ALL("All (35)"),
    GEOMETRIC("Geometric"),
    COOKIES("Cookies"),
    STARS("Stars"),
    EXPRESSIVE("Expressive"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShapePickerSection(
    title: String,
    subtitle: String,
    selectedShape: OverlayPreferences.NestedAlbumArtShape,
    rotationDegrees: Float,
    onShapeSelected: (OverlayPreferences.NestedAlbumArtShape) -> Unit,
    onRotationChanged: (Float) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(ShapeCategory.ALL) }

    val geometricShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
            OverlayPreferences.NestedAlbumArtShape.CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.SEMI_CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.OVAL,
            OverlayPreferences.NestedAlbumArtShape.PILL,
            OverlayPreferences.NestedAlbumArtShape.SLANTED,
            OverlayPreferences.NestedAlbumArtShape.TRIANGLE,
            OverlayPreferences.NestedAlbumArtShape.DIAMOND,
            OverlayPreferences.NestedAlbumArtShape.PENTAGON,
            OverlayPreferences.NestedAlbumArtShape.GEM,
        )
    }

    val cookieShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.COOKIE,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_6,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_7,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_9,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_12,
            OverlayPreferences.NestedAlbumArtShape.CLOVER,
            OverlayPreferences.NestedAlbumArtShape.CLOVER_8,
        )
    }

    val starShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.SUNNY,
            OverlayPreferences.NestedAlbumArtShape.VERY_SUNNY,
            OverlayPreferences.NestedAlbumArtShape.BURST,
            OverlayPreferences.NestedAlbumArtShape.SOFT_BURST,
            OverlayPreferences.NestedAlbumArtShape.BOOM,
            OverlayPreferences.NestedAlbumArtShape.SOFT_BOOM,
        )
    }

    val expressiveShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.HEART,
            OverlayPreferences.NestedAlbumArtShape.FLOWER,
            OverlayPreferences.NestedAlbumArtShape.GHOSTISH,
            OverlayPreferences.NestedAlbumArtShape.BUN,
            OverlayPreferences.NestedAlbumArtShape.PUFFY,
            OverlayPreferences.NestedAlbumArtShape.PUFFY_DIAMOND,
            OverlayPreferences.NestedAlbumArtShape.PIXEL_CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.PIXEL_TRIANGLE,
            OverlayPreferences.NestedAlbumArtShape.ARCH,
            OverlayPreferences.NestedAlbumArtShape.FAN,
            OverlayPreferences.NestedAlbumArtShape.ARROW,
            OverlayPreferences.NestedAlbumArtShape.CLAM_SHELL,
        )
    }

    val displayedShapes = when (selectedCategory) {
        ShapeCategory.ALL -> OverlayPreferences.NestedAlbumArtShape.entries
        ShapeCategory.GEOMETRIC -> geometricShapes
        ShapeCategory.COOKIES -> cookieShapes
        ShapeCategory.STARS -> starShapes
        ShapeCategory.EXPRESSIVE -> expressiveShapes
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(nestedAlbumArtShape(selectedShape, rotationDegrees))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Active: ${selectedShape.label} ($subtitle)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Category Filter ButtonGroups
        val catRow1 = listOf(ShapeCategory.ALL, ShapeCategory.GEOMETRIC, ShapeCategory.COOKIES)
        val catRow2 = listOf(ShapeCategory.STARS, ShapeCategory.EXPRESSIVE)

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            catRow1.forEach { category ->
                toggleableItem(
                    checked = selectedCategory == category,
                    label = category.label,
                    onCheckedChange = { selectedCategory = category },
                    weight = 1f,
                )
            }
        }

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            catRow2.forEach { category ->
                toggleableItem(
                    checked = selectedCategory == category,
                    label = category.label,
                    onCheckedChange = { selectedCategory = category },
                    weight = 1f,
                )
            }
        }

        // Horizontal list of shapes ensuring shapes are never squished or distorted
        val shapeListState = rememberLazyListState()

        LaunchedEffect(selectedShape, selectedCategory) {
            val index = displayedShapes.indexOf(selectedShape)
            if (index >= 0) {
                shapeListState.animateScrollToItem(index)
            }
        }

        LazyRow(
            state = shapeListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        ) {
            items(displayedShapes, key = { it.name }) { shapeOption ->
                val isSelected = selectedShape == shapeOption
                Surface(
                    selected = isSelected,
                    onClick = { onShapeSelected(shapeOption) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(54.dp)
                        .semantics { contentDescription = shapeOption.label },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(nestedAlbumArtShape(shapeOption, rotationDegrees))
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Rotation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Shape Rotation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${rotationDegrees.roundToInt()}°",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Quick Rotation Presets ButtonGroup
        val rotationPresets = listOf(0f, 45f, 90f, 180f, 270f)
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            rotationPresets.forEach { preset ->
                val isSelected = (((rotationDegrees % 360f) + 360f) % 360f).roundToInt() == preset.toInt()
                toggleableItem(
                    checked = isSelected,
                    label = "${preset.toInt()}°",
                    onCheckedChange = { onRotationChanged(preset) },
                    weight = 1f,
                )
            }
        }

        // Rotation Slider with Minus & Plus buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    val next = (((rotationDegrees - 15f) % 360f) + 360f) % 360f
                    onRotationChanged(next.roundToInt().toFloat())
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Rotate Counter-Clockwise",
                    modifier = Modifier.size(18.dp),
                )
            }

            val currentRotation = (((rotationDegrees % 360f) + 360f) % 360f)
            val rotationSliderState = remember {
                SliderState(
                    value = currentRotation,
                    trackRange = 0f..360f,
                )
            }
            rotationSliderState.value = currentRotation
            Slider(
                state = rotationSliderState,
                onValueChange = { onRotationChanged(it.roundToInt().toFloat()) },
                modifier = Modifier.weight(1f),
            )

            FilledTonalIconButton(
                onClick = {
                    val next = (rotationDegrees + 15f) % 360f
                    onRotationChanged(next.roundToInt().toFloat())
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Rotate Clockwise",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
