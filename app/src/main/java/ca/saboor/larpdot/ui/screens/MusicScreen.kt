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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ExpandedIslandPreview
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.overlay.nestedAlbumArtShape

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

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
        OverlayPreferences.isShowProgressOutlineEnabled(context)
        OverlayPreferences.getMinimizedAlbumArtStyle(context)
        OverlayPreferences.getExpandedAlbumArtStyle(context)
        OverlayPreferences.getMinimizedAlbumArtShape(context)
        OverlayPreferences.getExpandedAlbumArtShape(context)
    }

    val shapeEntries = OverlayPreferences.NestedAlbumArtShape.entries
    val shapeRow1 = shapeEntries.take(3)
    val shapeRow2 = shapeEntries.drop(3)

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
                        OverlayPreferences.AlbumArtStyle.entries.forEach { style ->
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column {
                                    Text(
                                        text = "Minimized Shape",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Material 3 shape for minimized thumbnail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Small Shape Row 1: Square, Circle, Cookie
                            ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            ) {
                                shapeRow1.forEach { shapeOption ->
                                    toggleableItem(
                                        checked = minimizedShape == shapeOption,
                                        label = shapeOption.label,
                                        onCheckedChange = {
                                            OverlayPreferences.setMinimizedAlbumArtShape(context, shapeOption)
                                        },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(nestedAlbumArtShape(shapeOption))
                                                    .background(
                                                        if (minimizedShape == shapeOption)
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.primary
                                                    ),
                                            )
                                        },
                                        weight = 1f,
                                    )
                                }
                            }

                            // Small Shape Row 2: Clover, Sunny, Heart
                            ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            ) {
                                shapeRow2.forEach { shapeOption ->
                                    toggleableItem(
                                        checked = minimizedShape == shapeOption,
                                        label = shapeOption.label,
                                        onCheckedChange = {
                                            OverlayPreferences.setMinimizedAlbumArtShape(context, shapeOption)
                                        },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(nestedAlbumArtShape(shapeOption))
                                                    .background(
                                                        if (minimizedShape == shapeOption)
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.primary
                                                    ),
                                            )
                                        },
                                        weight = 1f,
                                    )
                                }
                            }
                        }
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
                        OverlayPreferences.AlbumArtStyle.entries.forEach { style ->
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column {
                                    Text(
                                        text = "Expanded Shape",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Material 3 shape for expanded cover art",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Expanded Shape Row 1: Square, Circle, Cookie
                            ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            ) {
                                shapeRow1.forEach { shapeOption ->
                                    toggleableItem(
                                        checked = expandedShape == shapeOption,
                                        label = shapeOption.label,
                                        onCheckedChange = {
                                            OverlayPreferences.setExpandedAlbumArtShape(context, shapeOption)
                                        },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(nestedAlbumArtShape(shapeOption))
                                                    .background(
                                                        if (expandedShape == shapeOption)
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.primary
                                                    ),
                                            )
                                        },
                                        weight = 1f,
                                    )
                                }
                            }

                            // Expanded Shape Row 2: Clover, Sunny, Heart
                            ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            ) {
                                shapeRow2.forEach { shapeOption ->
                                    toggleableItem(
                                        checked = expandedShape == shapeOption,
                                        label = shapeOption.label,
                                        onCheckedChange = {
                                            OverlayPreferences.setExpandedAlbumArtShape(context, shapeOption)
                                        },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(nestedAlbumArtShape(shapeOption))
                                                    .background(
                                                        if (expandedShape == shapeOption)
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.primary
                                                    ),
                                            )
                                        },
                                        weight = 1f,
                                    )
                                }
                            }
                        }
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
                }
            }
        }
    }
}
