package ca.saboor.larpdot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val expandedStyle by OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState()
    val nestedShape by OverlayPreferences.nestedAlbumArtShapeFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
        OverlayPreferences.getMinimizedAlbumArtStyle(context)
        OverlayPreferences.getExpandedAlbumArtStyle(context)
        OverlayPreferences.getNestedAlbumArtShape(context)
    }

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

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OverlayPreferences.AlbumArtStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = minimizedStyle == style,
                                onClick = {
                                    OverlayPreferences.setMinimizedAlbumArtStyle(context, style)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = OverlayPreferences.AlbumArtStyle.entries.size,
                                ),
                                label = {
                                    Text(
                                        text = style.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
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

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OverlayPreferences.AlbumArtStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = expandedStyle == style,
                                onClick = {
                                    OverlayPreferences.setExpandedAlbumArtStyle(context, style)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = OverlayPreferences.AlbumArtStyle.entries.size,
                                ),
                                label = {
                                    Text(
                                        text = style.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Material 3 Shapes for Nested Art
        item {
            SectionHeader(title = "Nested Artwork Shape")
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
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Material 3 Shape",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Expressive shape applied when Nested style is selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val shapeEntries = OverlayPreferences.NestedAlbumArtShape.entries
                    val row1 = shapeEntries.take(3)
                    val row2 = shapeEntries.drop(3)

                    // Row 1: Square, Circle, Cookie
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row1.forEachIndexed { index, shapeOption ->
                            SegmentedButton(
                                selected = nestedShape == shapeOption,
                                onClick = {
                                    OverlayPreferences.setNestedAlbumArtShape(context, shapeOption)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = row1.size,
                                ),
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(nestedAlbumArtShape(shapeOption))
                                            .background(
                                                if (nestedShape == shapeOption)
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                else
                                                    MaterialTheme.colorScheme.primary
                                            ),
                                    )
                                },
                                label = {
                                    Text(
                                        text = shapeOption.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }

                    // Row 2: Clover, Sunny, Heart
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row2.forEachIndexed { index, shapeOption ->
                            SegmentedButton(
                                selected = nestedShape == shapeOption,
                                onClick = {
                                    OverlayPreferences.setNestedAlbumArtShape(context, shapeOption)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = row2.size,
                                ),
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(nestedAlbumArtShape(shapeOption))
                                            .background(
                                                if (nestedShape == shapeOption)
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                else
                                                    MaterialTheme.colorScheme.primary
                                            ),
                                    )
                                },
                                label = {
                                    Text(
                                        text = shapeOption.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Display Options Card
        item {
            SectionHeader(title = "Display Options")
            LarpCard {
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
            }
        }
    }
}
