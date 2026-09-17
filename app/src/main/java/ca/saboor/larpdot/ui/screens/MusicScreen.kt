package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ExpandedIslandPreview
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
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
private fun ShapePickerSection(
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

            Slider(
                value = (((rotationDegrees % 360f) + 360f) % 360f),
                onValueChange = { onRotationChanged(it.roundToInt().toFloat()) },
                valueRange = 0f..360f,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val expandedStyle by OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState()
    val minimizedShape by OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState()
    val expandedShape by OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState()
    val minimizedRotation by OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState()
    val expandedRotation by OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState()
    val showDominantGlow by OverlayPreferences.showDominantColorGlowFlow.collectAsState()
    val showCameraSwoop by OverlayPreferences.showCameraSwoopFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
        OverlayPreferences.isShowProgressOutlineEnabled(context)
        OverlayPreferences.getMinimizedAlbumArtStyle(context)
        OverlayPreferences.getExpandedAlbumArtStyle(context)
        OverlayPreferences.getMinimizedAlbumArtShape(context)
        OverlayPreferences.getExpandedAlbumArtShape(context)
        OverlayPreferences.getMinimizedAlbumArtRotation(context)
        OverlayPreferences.getExpandedAlbumArtRotation(context)
        OverlayPreferences.isShowDominantColorGlowEnabled(context)
        OverlayPreferences.isShowCameraSwoopEnabled(context)
    }

    val minimizedStyles = listOf(
        OverlayPreferences.AlbumArtStyle.BASIC_FADED,
        OverlayPreferences.AlbumArtStyle.BLENDED,
        OverlayPreferences.AlbumArtStyle.NESTED,
    )
    val expandedStyleRow1 = listOf(
        OverlayPreferences.AlbumArtStyle.BASIC_FADED,
        OverlayPreferences.AlbumArtStyle.BLENDED,
    )
    val expandedStyleRow2 = listOf(
        OverlayPreferences.AlbumArtStyle.NESTED,
        OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Live Island Preview (both Minimized Pill and Expanded Card)
        item {
            ExpandedIslandPreview(cutoutInfo = cutoutInfo)
        }

        // Minimized Island Presentation
        item {
            SectionHeader(title = "Minimized Island Art")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoSizeSelectSmall,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Minimized Style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (minimizedStyle) {
                                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> "Linear edge fade into capsule pill"
                                    OverlayPreferences.AlbumArtStyle.BLENDED -> "Smooth gradient blend with ambient glow"
                                    OverlayPreferences.AlbumArtStyle.NESTED -> "Crisp nested album art thumbnail"
                                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND -> "Artwork fills the pill wing"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        minimizedStyles.forEach { style ->
                            toggleableItem(
                                checked = minimizedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setMinimizedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = minimizedStyle == OverlayPreferences.AlbumArtStyle.NESTED,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShapePickerSection(
                            title = "Minimized Shape",
                            subtitle = "minimized island",
                            selectedShape = minimizedShape,
                            rotationDegrees = minimizedRotation,
                            onShapeSelected = {
                                OverlayPreferences.setMinimizedAlbumArtShape(context, it)
                            },
                            onRotationChanged = {
                                OverlayPreferences.setMinimizedAlbumArtRotation(context, it)
                            },
                        )
                    }
                }
            }
        }

        // Expanded Island Presentation
        item {
            SectionHeader(title = "Expanded Island Art")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Expanded Style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (expandedStyle) {
                                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> "Linear faded background card cover"
                                    OverlayPreferences.AlbumArtStyle.BLENDED -> "Curved gradient blend with dominant backdrop"
                                    OverlayPreferences.AlbumArtStyle.NESTED -> "Prominent nested cover beside track title"
                                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND -> "Artwork fills the entire island card like Android media player"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        expandedStyleRow1.forEach { style ->
                            toggleableItem(
                                checked = expandedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        expandedStyleRow2.forEach { style ->
                            toggleableItem(
                                checked = expandedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = expandedStyle == OverlayPreferences.AlbumArtStyle.NESTED,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShapePickerSection(
                            title = "Expanded Shape",
                            subtitle = "expanded card",
                            selectedShape = expandedShape,
                            rotationDegrees = expandedRotation,
                            onShapeSelected = {
                                OverlayPreferences.setExpandedAlbumArtShape(context, it)
                            },
                            onRotationChanged = {
                                OverlayPreferences.setExpandedAlbumArtRotation(context, it)
                            },
                        )
                    }
                }
            }
        }

        // Display Options Card
        item {
            SectionHeader(title = "Display Options")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Show Song Title
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
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Show Song Title",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Display track title above minimized island",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = showTitle,
                            onCheckedChange = { isChecked ->
                                OverlayPreferences.setShowMinimizedTitleEnabled(context, isChecked)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Progress Outline
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
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Progress Outline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Draw fluid perimeter progress ring around island",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = showProgressOutline,
                            onCheckedChange = { isChecked ->
                                OverlayPreferences.setShowProgressOutlineEnabled(context, isChecked)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Dominant Color Glow
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
                            Icon(
                                imageVector = Icons.Default.Flare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Dominant Color Glow",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Ambient dominant color glow on the right wing and card backdrop",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = showDominantGlow,
                            onCheckedChange = { isChecked ->
                                OverlayPreferences.setShowDominantColorGlowEnabled(context, isChecked)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Camera Swoop Cutout Cover
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
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Camera Swoop Cover",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Smooth organic swoop shader over camera cutout on expanded card",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = showCameraSwoop,
                            onCheckedChange = { isChecked ->
                                OverlayPreferences.setShowCameraSwoopEnabled(context, isChecked)
                            },
                        )
                    }
                }
            }
        }
    }
}
