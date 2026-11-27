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
    mediaProjectionManager: MediaProjectionManager?,
    screenCaptureLauncher: ActivityResultLauncher<Intent>,
    onRequestAudioPreviewWarning: () -> Unit,
) {
    val minimizedLeftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val minimizedRightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    fun sideIcon(side: OverlayPreferences.MinimizedMusicSideContent) = when (side) {
        OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART -> LarpIcons.PhotoSizeSelectSmall
        OverlayPreferences.MinimizedMusicSideContent.VISUALIZER -> LarpIcons.GraphicEq
        OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE -> LarpIcons.MusicNote
        OverlayPreferences.MinimizedMusicSideContent.TIME_LEFT,
        OverlayPreferences.MinimizedMusicSideContent.TIME_ELAPSED,
        -> LarpIcons.Timer
    }
    val (title, icon) = when (element) {
        MusicPreviewElement.MINIMIZED_ALBUM_ART -> "Minimized Left Side" to sideIcon(minimizedLeftSide)
        MusicPreviewElement.MINIMIZED_BACKGROUND -> "Minimized Background" to LarpIcons.Palette
        MusicPreviewElement.EXPANDED_ALBUM_ART -> "Expanded Album Art" to LarpIcons.Palette
        MusicPreviewElement.EXPANDED_BACKGROUND -> "Expanded Background" to LarpIcons.Palette
        MusicPreviewElement.MINIMIZED_RIGHT_SIDE -> "Minimized Right Side" to sideIcon(minimizedRightSide)
        MusicPreviewElement.VISUALIZER -> "Visualizer" to LarpIcons.GraphicEq
        MusicPreviewElement.PROGRESS_BAR -> "Progress Bar" to LarpIcons.Tune
        MusicPreviewElement.PLAYBACK_CONTROLS -> "Playback Controls" to LarpIcons.PlayArrow
        MusicPreviewElement.AUDIO_OUTPUT -> "Playing On" to LarpIcons.Speaker
        MusicPreviewElement.TRACK_INFO -> "Track Info" to LarpIcons.MusicNote
    }

    LarpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IslandPreviewInspectorHeader(title, icon, onDismiss)

            when (element) {
                MusicPreviewElement.MINIMIZED_ALBUM_ART, MusicPreviewElement.MINIMIZED_RIGHT_SIDE -> {
                    val isLeft = element == MusicPreviewElement.MINIMIZED_ALBUM_ART
                    val selectedSide = if (isLeft) minimizedLeftSide else minimizedRightSide
                    LarpConnectedButtons(
                        options = OverlayPreferences.MinimizedMusicSideContent.entries.map { option ->
                            LarpButtonOption(option.label, checked = selectedSide == option) {
                                if (isLeft) OverlayPreferences.setMinimizedMusicLeftSide(context, option)
                                else OverlayPreferences.setMinimizedMusicRightSide(context, option)
                            }
                        },
                        maxButtonsPerRow = 3,
                    )
                    when (selectedSide) {
                        OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART -> {
                            MinimizedAlbumArtSettingsContent(
                                context = context,
                                minimizedStyle = minimizedStyle,
                                minimizedShape = minimizedShape,
                                minimizedRotation = minimizedRotation,
                                showMinimizedProgressOutline = showMinimizedProgressOutline,
                                albumArtGlow = albumArtGlow,
                            )
                        }
                        OverlayPreferences.MinimizedMusicSideContent.VISUALIZER -> {
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
                                mediaProjectionManager = mediaProjectionManager,
                                screenCaptureLauncher = screenCaptureLauncher,
                                onRequestAudioPreviewWarning = onRequestAudioPreviewWarning,
                            )
                        }
                        else -> Unit
                    }
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
                MusicPreviewElement.AUDIO_OUTPUT -> {
                    val style by OverlayPreferences.audioOutputStyleFlow.collectAsState()
                    LarpConnectedButtons(OverlayPreferences.AudioOutputStyle.entries.map { option ->
                        LarpButtonOption(option.label, checked = style == option) {
                            OverlayPreferences.setAudioOutputStyle(context, option)
                        }
                    })
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
            checked = showMinimizedDominantGlow,
            onCheckedChange = { OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Pulse with BPM",
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
        Text(
            text = "Background Style",
            style = MaterialTheme.typography.titleMedium,
        )

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
            checked = expandedElementVisibility.trackInfo,
            onCheckedChange = { enabled ->
                OverlayPreferences.setExpandedElementVisibility(context, expandedElementVisibility.copy(trackInfo = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Show Title",
            checked = style.showTitle,
            onCheckedChange = { enabled ->
                OverlayPreferences.setTrackInfoStyle(context, style.copy(showTitle = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Title Accent Color",
            checked = style.titleAccentColor,
            onCheckedChange = { enabled ->
                OverlayPreferences.setTrackInfoStyle(context, style.copy(titleAccentColor = enabled))
            },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Show Artist",
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
            checked = expandedElementVisibility.mainControls,
            onCheckedChange = { enabled ->
                OverlayPreferences.setExpandedElementVisibility(context, expandedElementVisibility.copy(mainControls = enabled))
            },
        )
        LarpDivider()
        PlaybackButtonStyleSettings(style) { OverlayPreferences.setPlaybackControlsStyle(context, it) }
    }
}

@Composable
internal fun PlaybackButtonStyleSettings(
    style: OverlayPreferences.PlaybackControlsStyle,
    forPlayback: Boolean = true,
    onChange: (OverlayPreferences.PlaybackControlsStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (forPlayback) {
            LarpSwitchRow(
                title = "Play/Pause Background",
                checked = style.playPauseBackground,
                onCheckedChange = { onChange(style.copy(playPauseBackground = it)) },
            )
            LarpSwitchRow(
                title = "Play/Pause Accent Color",
                checked = style.playPauseAccentColor,
                onCheckedChange = { onChange(style.copy(playPauseAccentColor = it)) },
            )
        }
        LarpSwitchRow(
            title = if (forPlayback) "Skip/Previous Background" else "Button Background",
            checked = style.skipBackground,
            onCheckedChange = { onChange(style.copy(skipBackground = it)) },
        )
        LarpSwitchRow(
            title = "Material 3 Button Group",
            checked = style.material3ButtonGroup,
            onCheckedChange = { enabled ->
                onChange(if (enabled) {
                    style.copy(material3ButtonGroup = true, playPauseBackground = true, skipBackground = true)
                } else style.copy(material3ButtonGroup = false))
            },
        )
        LarpSliderRow(
            title = "Corner Radius",
            valueLabel = "${style.cornerRadiusDp.roundToInt()} dp",
            value = style.cornerRadiusDp,
            valueRange = 0f..32f,
            onValueChange = { onChange(style.copy(cornerRadiusDp = it.roundToInt().toFloat())) },
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
            checked = showProgressSquiggles,
            onCheckedChange = { OverlayPreferences.setShowProgressSquigglesEnabled(context, it) },
        )
        LarpDivider()
        LarpSwitchRow(
            title = "Accent Color",
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
        Text(
            text = "Style",
            style = MaterialTheme.typography.titleMedium,
        )

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
            ShapePickerSection(
                title = "Shape",
                selectedShape = minimizedShape,
                rotationDegrees = minimizedRotation,
                onShapeSelected = { OverlayPreferences.setMinimizedAlbumArtShape(context, it) },
                onRotationChanged = { OverlayPreferences.setMinimizedAlbumArtRotation(context, it) },
            )
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
            checked = showExpandedAlbumArt,
            onCheckedChange = { OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it) },
        )

        AnimatedVisibility(
            visible = showExpandedAlbumArt,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            ShapePickerSection(
                title = "Shape",
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
