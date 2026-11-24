package ca.saboor.larpdot.ui.screens

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.MusicPreviewElement
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.ExtractionState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MusicPreviewInspectorCard(
    element: MusicPreviewElement,
    onDismiss: () -> Unit,
    context: Context,
    minimizedStyle: OverlayPreferences.AlbumArtStyle,
    minimizedShape: OverlayPreferences.NestedAlbumArtShape,
    minimizedRotation: Float,
    minimizedNestedBlurredBg: Boolean,
    showMinimizedProgressOutline: Boolean,
    showExpandedAlbumArt: Boolean,
    expandedShape: OverlayPreferences.NestedAlbumArtShape,
    expandedRotation: Float,
    showExpandedProgressOutline: Boolean,
    albumArtGlow: Boolean,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    showProgressSquiggles: Boolean,
    progressBarAccentColor: Boolean,
    playbackControlsStyle: OverlayPreferences.PlaybackControlsStyle,
    trackInfoStyle: OverlayPreferences.TrackInfoStyle,
    expandedStyle: OverlayPreferences.ExpandedBackgroundStyle,
    showMinimizedDominantGlow: Boolean,
    minimizedBpmPulseEnabled: Boolean,
    showExpandedDominantGlow: Boolean,
    expandedGlowSides: OverlayPreferences.ExpandedGlowSides,
    expandedBpmPulseEnabled: Boolean,
    waveformBandCount: Int,
    waveformBarWidth: Float,
    waveformBarSpacing: Float,
    visualizerMode: OverlayPreferences.VisualizerMode,
    previewSource: OverlayPreferences.PreviewAudioSource,
    extractionState: ExtractionState,
    isPreviewPlaying: Boolean,
    previewPositionMs: Long,
    previewDurationMs: Long,
    currentBpm: Float?,
    isBpmResolved: Boolean,
    isBpmDetecting: Boolean,
    detectedBpmTrack: String?,
    detectedSource: String?,
    isLiveCapturing: Boolean,
    liveAmplitudes: FloatArray?,
    liveErrorMessage: String?,
    nowPlaying: MediaTrackInfo,
    mediaProjectionManager: MediaProjectionManager?,
    screenCaptureLauncher: ActivityResultLauncher<Intent>,
    onRequestAudioPreviewWarning: () -> Unit,
) {
    val (title, icon) = when (element) {
        MusicPreviewElement.MINIMIZED_ALBUM_ART -> "Minimized Album Art" to LarpIcons.PhotoSizeSelectSmall
        MusicPreviewElement.MINIMIZED_BACKGROUND -> "Minimized Background" to LarpIcons.Palette
        MusicPreviewElement.EXPANDED_ALBUM_ART -> "Expanded Album Art" to LarpIcons.Palette
        MusicPreviewElement.EXPANDED_BACKGROUND -> "Expanded Background" to LarpIcons.Palette
        MusicPreviewElement.VISUALIZER -> "Visualizer" to LarpIcons.GraphicEq
        MusicPreviewElement.PROGRESS_BAR -> "Progress Bar" to LarpIcons.Tune
        MusicPreviewElement.PLAYBACK_CONTROLS -> "Playback Controls" to LarpIcons.PlayArrow
        MusicPreviewElement.TRACK_INFO -> "Track Info" to LarpIcons.MusicNote
    }

    LarpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = LarpIcons.Close,
                        contentDescription = "Close $title settings",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when (element) {
                MusicPreviewElement.MINIMIZED_ALBUM_ART -> {
                    MinimizedAlbumArtSettingsContent(
                        context = context,
                        minimizedStyle = minimizedStyle,
                        minimizedShape = minimizedShape,
                        minimizedRotation = minimizedRotation,
                        minimizedNestedBlurredBg = minimizedNestedBlurredBg,
                        showMinimizedProgressOutline = showMinimizedProgressOutline,
                        albumArtGlow = albumArtGlow,
                    )
                }
                MusicPreviewElement.MINIMIZED_BACKGROUND -> {
                    MinimizedBackgroundSettingsContent(
                        context = context,
                        showMinimizedDominantGlow = showMinimizedDominantGlow,
                        minimizedBpmPulseEnabled = minimizedBpmPulseEnabled,
                    )
                }
                MusicPreviewElement.EXPANDED_ALBUM_ART -> {
                    ExpandedAlbumArtSettingsContent(
                        context = context,
                        showExpandedAlbumArt = showExpandedAlbumArt,
                        expandedShape = expandedShape,
                        expandedRotation = expandedRotation,
                        showExpandedProgressOutline = showExpandedProgressOutline,
                        albumArtGlow = albumArtGlow,
                    )
                }
                MusicPreviewElement.EXPANDED_BACKGROUND -> {
                    ExpandedBackgroundSettingsContent(
                        context = context,
                        expandedStyle = expandedStyle,
                        showExpandedDominantGlow = showExpandedDominantGlow,
                        expandedGlowSides = expandedGlowSides,
                        expandedBpmPulseEnabled = expandedBpmPulseEnabled,
                    )
                }
                MusicPreviewElement.VISUALIZER -> {
                    VisualizerSettingsContent(
                        context = context,
                        waveformBandCount = waveformBandCount,
                        waveformBarWidth = waveformBarWidth,
                        waveformBarSpacing = waveformBarSpacing,
                        visualizerMode = visualizerMode,
                        previewSource = previewSource,
                        extractionState = extractionState,
                        isPreviewPlaying = isPreviewPlaying,
                        previewPositionMs = previewPositionMs,
                        previewDurationMs = previewDurationMs,
                        currentBpm = currentBpm,
                        isBpmResolved = isBpmResolved,
                        isBpmDetecting = isBpmDetecting,
                        detectedBpmTrack = detectedBpmTrack,
                        detectedSource = detectedSource,
                        isLiveCapturing = isLiveCapturing,
                        liveAmplitudes = liveAmplitudes,
                        liveErrorMessage = liveErrorMessage,
                        nowPlaying = nowPlaying,
                        mediaProjectionManager = mediaProjectionManager,
                        screenCaptureLauncher = screenCaptureLauncher,
                        onRequestAudioPreviewWarning = onRequestAudioPreviewWarning,
                    )
                }
                MusicPreviewElement.PROGRESS_BAR -> {
                    ProgressBarSettingsContent(
                        context = context,
                        expandedElementVisibility = expandedElementVisibility,
                        showProgressSquiggles = showProgressSquiggles,
                        progressBarAccentColor = progressBarAccentColor,
                    )
                }
                MusicPreviewElement.PLAYBACK_CONTROLS -> {
                    PlaybackControlsSettingsContent(
                        context = context,
                        expandedElementVisibility = expandedElementVisibility,
                        style = playbackControlsStyle,
                    )
                }
                MusicPreviewElement.TRACK_INFO -> {
                    TrackInfoSettingsContent(
                        context = context,
                        expandedElementVisibility = expandedElementVisibility,
                        style = trackInfoStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimizedBackgroundSettingsContent(
    context: Context,
    showMinimizedDominantGlow: Boolean,
    minimizedBpmPulseEnabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dominant Color Glow", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ambient artwork tint on the minimized pill",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showMinimizedDominantGlow,
                onCheckedChange = {
                    OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, it)
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pulse with BPM", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pulse the minimized dominant glow to the track tempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = minimizedBpmPulseEnabled,
                onCheckedChange = {
                    OverlayPreferences.setMinimizedBpmPulseEnabled(context, it)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedBackgroundSettingsContent(
    context: Context,
    expandedStyle: OverlayPreferences.ExpandedBackgroundStyle,
    showExpandedDominantGlow: Boolean,
    expandedGlowSides: OverlayPreferences.ExpandedGlowSides,
    expandedBpmPulseEnabled: Boolean,
) {
    val expandedStyleRow1 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.NONE,
        OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED,
        OverlayPreferences.ExpandedBackgroundStyle.BLENDED,
    )
    val expandedStyleRow2 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND,
        OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = "Background Style",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
            )
            Text(
                text = when (expandedStyle) {
                    OverlayPreferences.ExpandedBackgroundStyle.NONE -> "Plain black island background"
                    OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED -> "Linear faded background card cover"
                    OverlayPreferences.ExpandedBackgroundStyle.BLENDED -> "Curved gradient blend with dominant backdrop"
                    OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND -> "Artwork fills the entire island card"
                    OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND -> "Blurred artwork fills the entire island card"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dominant Color Glow", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ambient colored edge glow adapted from artwork",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showExpandedDominantGlow,
                onCheckedChange = {
                    OverlayPreferences.setShowExpandedDominantColorGlowEnabled(context, it)
                },
            )
        }

        AnimatedVisibility(
            visible = showExpandedDominantGlow,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Glow Sides",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                ) {
                    toggleableItem(
                        checked = expandedGlowSides.left,
                        label = "Left",
                        onCheckedChange = {
                            OverlayPreferences.setExpandedGlowSides(
                                context,
                                expandedGlowSides.copy(left = it),
                            )
                        },
                        weight = 1f,
                    )
                    toggleableItem(
                        checked = expandedGlowSides.right,
                        label = "Right",
                        onCheckedChange = {
                            OverlayPreferences.setExpandedGlowSides(
                                context,
                                expandedGlowSides.copy(right = it),
                            )
                        },
                        weight = 1f,
                    )
                    toggleableItem(
                        checked = expandedGlowSides.bottom,
                        label = "Bottom",
                        onCheckedChange = {
                            OverlayPreferences.setExpandedGlowSides(
                                context,
                                expandedGlowSides.copy(bottom = it),
                            )
                        },
                        weight = 1f,
                    )
                    toggleableItem(
                        checked = expandedGlowSides.top,
                        label = "Top",
                        onCheckedChange = {
                            OverlayPreferences.setExpandedGlowSides(
                                context,
                                expandedGlowSides.copy(top = it),
                            )
                        },
                        weight = 1f,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pulse with BPM", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pulse the expanded glow and play button to the track tempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = expandedBpmPulseEnabled,
                onCheckedChange = {
                    OverlayPreferences.setExpandedBpmPulseEnabled(context, it)
                },
            )
        }
    }
}

@Composable
private fun TrackInfoSettingsContent(
    context: Context,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    style: OverlayPreferences.TrackInfoStyle,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Track Info", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display track metadata on the expanded card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = expandedElementVisibility.trackInfo,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setExpandedElementVisibility(
                        context,
                        expandedElementVisibility.copy(trackInfo = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Title", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display the song title",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.showTitle,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setTrackInfoStyle(context, style.copy(showTitle = enabled))
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Title Accent Color", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tint the song title with album art accent color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.titleAccentColor,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setTrackInfoStyle(context, style.copy(titleAccentColor = enabled))
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Artist", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display the artist name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.showArtist,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setTrackInfoStyle(context, style.copy(showArtist = enabled))
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Album", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display the album name when available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.showAlbum,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setTrackInfoStyle(context, style.copy(showAlbum = enabled))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaybackControlsSettingsContent(
    context: Context,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    style: OverlayPreferences.PlaybackControlsStyle,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Playback Controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display previous, play/pause, and next controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = expandedElementVisibility.mainControls,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setExpandedElementVisibility(
                        context,
                        expandedElementVisibility.copy(mainControls = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Play/Pause Background", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Show a filled background behind the play/pause button",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.playPauseBackground,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setPlaybackControlsStyle(
                        context,
                        style.copy(playPauseBackground = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Play/Pause Accent Color", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tint the play/pause button with album art accent color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.playPauseAccentColor,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setPlaybackControlsStyle(
                        context,
                        style.copy(playPauseAccentColor = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Skip/Previous Background", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Show a tonal background behind skip and previous buttons",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.skipBackground,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setPlaybackControlsStyle(
                        context,
                        style.copy(skipBackground = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Material 3 Button Group", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Connect buttons with tight spacing and inner seam corners",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = style.material3ButtonGroup,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setPlaybackControlsStyle(
                        context,
                        if (enabled) {
                            style.copy(
                                material3ButtonGroup = true,
                                playPauseBackground = true,
                                skipBackground = true,
                            )
                        } else {
                            style.copy(material3ButtonGroup = false)
                        },
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Corner Radius", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${style.cornerRadiusDp.roundToInt()} dp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val radiusSliderState = remember {
                SliderState(
                    value = style.cornerRadiusDp,
                    trackRange = 0f..32f,
                )
            }
            radiusSliderState.value = style.cornerRadiusDp
            Slider(
                state = radiusSliderState,
                onValueChange = { value ->
                    OverlayPreferences.setPlaybackControlsStyle(
                        context,
                        style.copy(cornerRadiusDp = value.roundToInt().toFloat()),
                    )
                },
            )
        }
    }
}

@Composable
private fun ProgressBarSettingsContent(
    context: Context,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    showProgressSquiggles: Boolean,
    progressBarAccentColor: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Progress Bar", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display the seekable scrubber on the expanded card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = expandedElementVisibility.progress,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setExpandedElementVisibility(
                        context,
                        expandedElementVisibility.copy(progress = enabled),
                    )
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Progress Squiggles", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Animate the progress bar with a wavy line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showProgressSquiggles,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setShowProgressSquigglesEnabled(context, enabled)
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tint the progress bar with album art accent color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = progressBarAccentColor,
                onCheckedChange = { enabled ->
                    OverlayPreferences.setProgressBarAccentColorEnabled(context, enabled)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MinimizedAlbumArtSettingsContent(
    context: Context,
    minimizedStyle: OverlayPreferences.AlbumArtStyle,
    minimizedShape: OverlayPreferences.NestedAlbumArtShape,
    minimizedRotation: Float,
    minimizedNestedBlurredBg: Boolean,
    showMinimizedProgressOutline: Boolean,
    albumArtGlow: Boolean,
) {
    val minimizedStyles = listOf(
        OverlayPreferences.AlbumArtStyle.BASIC_FADED,
        OverlayPreferences.AlbumArtStyle.BLENDED,
        OverlayPreferences.AlbumArtStyle.NESTED,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = "Minimized Style",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
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
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            OverlayPreferences.setMinimizedNestedBlurredBgEnabled(
                                context,
                                !minimizedNestedBlurredBg,
                            )
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Blurred Artwork Background",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                        )
                        Text(
                            text = "Add blurred background behind nested art that fades into the dot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = minimizedNestedBlurredBg,
                        onCheckedChange = {
                            OverlayPreferences.setMinimizedNestedBlurredBgEnabled(context, it)
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Radial Progress", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = showMinimizedProgressOutline,
                onCheckedChange = {
                    OverlayPreferences.setShowMinimizedProgressOutlineEnabled(context, it)
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Album Art Glow", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = albumArtGlow,
                onCheckedChange = {
                    OverlayPreferences.setAlbumArtGlowEnabled(context, it)
                },
            )
        }
    }
}

@Composable
private fun ExpandedAlbumArtSettingsContent(
    context: Context,
    showExpandedAlbumArt: Boolean,
    expandedShape: OverlayPreferences.NestedAlbumArtShape,
    expandedRotation: Float,
    showExpandedProgressOutline: Boolean,
    albumArtGlow: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Album Art", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Show the cover beside the track title",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showExpandedAlbumArt,
                onCheckedChange = {
                    OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it)
                },
            )
        }

        AnimatedVisibility(
            visible = showExpandedAlbumArt,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            ShapePickerSection(
                title = "Album Art Shape",
                subtitle = "expanded album art",
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Radial Progress", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = showExpandedProgressOutline,
                onCheckedChange = {
                    OverlayPreferences.setShowExpandedProgressOutlineEnabled(context, it)
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Album Art Glow", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = albumArtGlow,
                onCheckedChange = {
                    OverlayPreferences.setAlbumArtGlowEnabled(context, it)
                },
            )
        }
    }
}

