package ca.saboor.larpdot.ui.screens

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.IslandPreviewInspectorHeader
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.MusicPreviewElement
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.ExtractionState
import kotlin.math.roundToInt

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
            IslandPreviewInspectorHeader(title, icon, onDismiss)

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
        LarpSwitchRow(
            title = "Dominant Color Glow",
            subtitle = "Ambient artwork tint on the minimized pill",
            checked = showMinimizedDominantGlow,
            onCheckedChange = { OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Pulse with BPM",
            subtitle = "Pulse the minimized dominant glow to the track tempo",
            checked = minimizedBpmPulseEnabled,
            onCheckedChange = { OverlayPreferences.setMinimizedBpmPulseEnabled(context, it) },
        )
    }
}

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

        LarpConnectedButtons(
            options = expandedStyleRow1.map { style ->
                LarpButtonOption(
                    checked = expandedStyle == style,
                    label = style.label,
                    onCheckedChange = { OverlayPreferences.setExpandedAlbumArtStyle(context, style) },
                )
            },
        )

        LarpConnectedButtons(
            options = expandedStyleRow2.map { style ->
                LarpButtonOption(
                    checked = expandedStyle == style,
                    label = style.label,
                    onCheckedChange = { OverlayPreferences.setExpandedAlbumArtStyle(context, style) },
                )
            },
        )

        LarpDivider()

        LarpSwitchRow(
            title = "Dominant Color Glow",
            subtitle = "Ambient colored edge glow adapted from artwork",
            checked = showExpandedDominantGlow,
            onCheckedChange = { OverlayPreferences.setShowExpandedDominantColorGlowEnabled(context, it) },
        )

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
                LarpConnectedButtons(
                    options = listOf(
                        LarpButtonOption(
                            checked = expandedGlowSides.left,
                            label = "Left",
                            onCheckedChange = {
                                OverlayPreferences.setExpandedGlowSides(context, expandedGlowSides.copy(left = it))
                            },
                        ),
                        LarpButtonOption(
                            checked = expandedGlowSides.right,
                            label = "Right",
                            onCheckedChange = {
                                OverlayPreferences.setExpandedGlowSides(context, expandedGlowSides.copy(right = it))
                            },
                        ),
                        LarpButtonOption(
                            checked = expandedGlowSides.bottom,
                            label = "Bottom",
                            onCheckedChange = {
                                OverlayPreferences.setExpandedGlowSides(context, expandedGlowSides.copy(bottom = it))
                            },
                        ),
                        LarpButtonOption(
                            checked = expandedGlowSides.top,
                            label = "Top",
                            onCheckedChange = {
                                OverlayPreferences.setExpandedGlowSides(context, expandedGlowSides.copy(top = it))
                            },
                        ),
                    ),
                    singleSelection = false,
                )
            }
        }

        LarpDivider()

        LarpSwitchRow(
            title = "Pulse with BPM",
            subtitle = "Pulse the expanded glow and play button to the track tempo",
            checked = expandedBpmPulseEnabled,
            onCheckedChange = { OverlayPreferences.setExpandedBpmPulseEnabled(context, it) },
        )
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
        LarpSwitchRow(
            title = "Show Track Info",
            subtitle = "Display track metadata on the expanded card",
            checked = expandedElementVisibility.trackInfo,
            onCheckedChange = { enabled ->
                OverlayPreferences.setExpandedElementVisibility(context, expandedElementVisibility.copy(trackInfo = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Show Title",
            subtitle = "Display the song title",
            checked = style.showTitle,
            onCheckedChange = { enabled ->
                OverlayPreferences.setTrackInfoStyle(context, style.copy(showTitle = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Title Accent Color",
            subtitle = "Tint the song title with album art accent color",
            checked = style.titleAccentColor,
            onCheckedChange = { enabled ->
                OverlayPreferences.setTrackInfoStyle(context, style.copy(titleAccentColor = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Show Artist",
            subtitle = "Display the artist name",
            checked = style.showArtist,
            onCheckedChange = { enabled ->
                OverlayPreferences.setTrackInfoStyle(context, style.copy(showArtist = enabled))
            },
        )
        LarpDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Show Album", style = MaterialTheme.typography.titleMedium)
            val options = listOf(
                Triple("Off", LarpIcons.Close, style.copy(showAlbum = false)),
                Triple("Below", LarpIcons.KeyboardArrowDown, style.copy(showAlbum = true, albumBesideArtist = false)),
                Triple("Beside", LarpIcons.MoreHoriz, style.copy(showAlbum = true, albumBesideArtist = true)),
            )
            LarpConnectedButtons(
                options = options.map { (label, icon, option) ->
                    LarpButtonOption(
                        label = label,
                        checked = style == option,
                        icon = icon,
                        onCheckedChange = { OverlayPreferences.setTrackInfoStyle(context, option) },
                    )
                },
            )
        }
    }
}

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
        LarpSwitchRow(
            title = "Show Playback Controls",
            subtitle = "Display previous, play/pause, and next controls",
            checked = expandedElementVisibility.mainControls,
            onCheckedChange = { enabled ->
                OverlayPreferences.setExpandedElementVisibility(context, expandedElementVisibility.copy(mainControls = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Play/Pause Background",
            subtitle = "Show a filled background behind the play/pause button",
            checked = style.playPauseBackground,
            onCheckedChange = { enabled ->
                OverlayPreferences.setPlaybackControlsStyle(context, style.copy(playPauseBackground = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Play/Pause Accent Color",
            subtitle = "Tint the play/pause button with album art accent color",
            checked = style.playPauseAccentColor,
            onCheckedChange = { enabled ->
                OverlayPreferences.setPlaybackControlsStyle(context, style.copy(playPauseAccentColor = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Skip/Previous Background",
            subtitle = "Show a tonal background behind skip and previous buttons",
            checked = style.skipBackground,
            onCheckedChange = { enabled ->
                OverlayPreferences.setPlaybackControlsStyle(context, style.copy(skipBackground = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Material 3 Button Group",
            subtitle = "Connect buttons with tight spacing and inner seam corners",
            checked = style.material3ButtonGroup,
            onCheckedChange = { enabled ->
                OverlayPreferences.setPlaybackControlsStyle(
                    context,
                    if (enabled) {
                        style.copy(material3ButtonGroup = true, playPauseBackground = true, skipBackground = true)
                    } else {
                        style.copy(material3ButtonGroup = false)
                    },
                )
            },
        )
        LarpDivider()
        LarpSliderRow(
            title = "Corner Radius",
            valueLabel = "${style.cornerRadiusDp.roundToInt()} dp",
            value = style.cornerRadiusDp,
            valueRange = 0f..32f,
            onValueChange = { value ->
                OverlayPreferences.setPlaybackControlsStyle(context, style.copy(cornerRadiusDp = value.roundToInt().toFloat()))
            },
        )
    }
}

@Composable
private fun ProgressBarSettingsContent(
    context: Context,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    showProgressSquiggles: Boolean,
    progressBarAccentColor: Boolean,
) {
    val timestampPlacement by OverlayPreferences.progressTimestampPlacementFlow.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LarpSwitchRow(
            title = "Show Progress Bar",
            subtitle = "Display the seekable scrubber on the expanded card",
            checked = expandedElementVisibility.progress,
            onCheckedChange = { enabled ->
                OverlayPreferences.setExpandedElementVisibility(context, expandedElementVisibility.copy(progress = enabled))
            },
        )
        LarpDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Timestamps", style = MaterialTheme.typography.titleMedium)
            LarpConnectedButtons(
                options = OverlayPreferences.ProgressTimestampPlacement.entries.map { placement ->
                    LarpButtonOption(
                        label = placement.label,
                        checked = timestampPlacement == placement,
                        icon = when (placement) {
                            OverlayPreferences.ProgressTimestampPlacement.HIDDEN -> LarpIcons.Close
                            OverlayPreferences.ProgressTimestampPlacement.BELOW -> LarpIcons.KeyboardArrowDown
                            OverlayPreferences.ProgressTimestampPlacement.BESIDE -> LarpIcons.MoreHoriz
                        },
                        onCheckedChange = { OverlayPreferences.setProgressTimestampPlacement(context, placement) },
                    )
                },
            )
        }
        LarpDivider()
        LarpSwitchRow(
            title = "Progress Squiggles",
            subtitle = "Animate the progress bar with a wavy line",
            checked = showProgressSquiggles,
            onCheckedChange = { OverlayPreferences.setShowProgressSquigglesEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Accent Color",
            subtitle = "Tint the progress bar with album art accent color",
            checked = progressBarAccentColor,
            onCheckedChange = { OverlayPreferences.setProgressBarAccentColorEnabled(context, it) },
        )
    }
}

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

        LarpConnectedButtons(
            options = minimizedStyles.map { style ->
                LarpButtonOption(
                    checked = minimizedStyle == style,
                    label = style.label,
                    onCheckedChange = { OverlayPreferences.setMinimizedAlbumArtStyle(context, style) },
                )
            },
        )

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
                    onShapeSelected = { OverlayPreferences.setMinimizedAlbumArtShape(context, it) },
                    onRotationChanged = { OverlayPreferences.setMinimizedAlbumArtRotation(context, it) },
                )
                LarpDivider()
                LarpSwitchRow(
                    title = "Blurred Artwork Background",
                    subtitle = "Add blurred background behind nested art that fades into the dot",
                    checked = minimizedNestedBlurredBg,
                    onCheckedChange = { OverlayPreferences.setMinimizedNestedBlurredBgEnabled(context, it) },
                )
            }
        }

        LarpDivider()
        LarpSwitchRow(
            title = "Radial Progress",
            checked = showMinimizedProgressOutline,
            onCheckedChange = { OverlayPreferences.setShowMinimizedProgressOutlineEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Album Art Glow",
            checked = albumArtGlow,
            onCheckedChange = { OverlayPreferences.setAlbumArtGlowEnabled(context, it) },
        )
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
        LarpSwitchRow(
            title = "Show Album Art",
            subtitle = "Show the cover beside the track title",
            checked = showExpandedAlbumArt,
            onCheckedChange = { OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it) },
        )

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
                onShapeSelected = { OverlayPreferences.setExpandedAlbumArtShape(context, it) },
                onRotationChanged = { OverlayPreferences.setExpandedAlbumArtRotation(context, it) },
            )
        }

        LarpDivider()
        LarpSwitchRow(
            title = "Radial Progress",
            checked = showExpandedProgressOutline,
            onCheckedChange = { OverlayPreferences.setShowExpandedProgressOutlineEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Album Art Glow",
            checked = albumArtGlow,
            onCheckedChange = { OverlayPreferences.setAlbumArtGlowEnabled(context, it) },
        )
    }
}
