package ca.saboor.larpdot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.ExpandedIslandPreview
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader

@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val albumArtStyle by OverlayPreferences.albumArtStyleFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
        OverlayPreferences.getAlbumArtStyle(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Live Island Preview
        item {
            ExpandedIslandPreview(cutoutInfo = cutoutInfo)
        }

        // Album Art Style Card
        item {
            SectionHeader(title = "Album Art Style")
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
                                text = "Artwork Presentation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (albumArtStyle) {
                                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> "Linear fade into island edge"
                                    OverlayPreferences.AlbumArtStyle.BLENDED -> "Smooth gradient blend with ambient glow"
                                    OverlayPreferences.AlbumArtStyle.NESTED_ROUNDED_SQUARE -> "Crisp rounded square cover nested in island"
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
                                selected = albumArtStyle == style,
                                onClick = {
                                    OverlayPreferences.setAlbumArtStyle(context, style)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = OverlayPreferences.AlbumArtStyle.entries.size,
                                ),
                                label = {
                                    Text(
                                        text = when (style) {
                                            OverlayPreferences.AlbumArtStyle.BASIC_FADED -> "Basic Faded"
                                            OverlayPreferences.AlbumArtStyle.BLENDED -> "Blended"
                                            OverlayPreferences.AlbumArtStyle.NESTED_ROUNDED_SQUARE -> "Nested Square"
                                        },
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
