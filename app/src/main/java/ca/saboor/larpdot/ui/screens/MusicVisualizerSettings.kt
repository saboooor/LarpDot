package ca.saboor.larpdot.ui.screens

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.ExtractionState
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import kotlin.math.roundToInt

@Composable
internal fun VisualizerSettingsContent(
    context: Context,
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
    val requestLiveAudioCapture: () -> Unit = {
        mediaProjectionManager?.createScreenCaptureIntent()?.let(screenCaptureLauncher::launch)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LarpSliderRow(
            title = "Waveform Bands",
            subtitle = "Number of animated equalizer frequency bars (default: 5)",
            valueLabel = when (waveformBandCount) {
                0 -> "Off"
                OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT -> "5 (Default)"
                else -> "$waveformBandCount Bands"
            },
            value = waveformBandCount.toFloat(),
            valueRange = 0f..30f,
            steps = 29,
            onValueChange = { OverlayPreferences.setWaveformBandCount(context, it.roundToInt()) },
        )

        LarpSliderRow(
            title = "Bar Width",
            valueLabel = "${"%.1f".format(waveformBarWidth)} dp",
            value = waveformBarWidth,
            valueRange = 0.5f..6f,
            onValueChange = { OverlayPreferences.setWaveformBarWidth(context, (it * 10f).roundToInt() / 10f) },
        )

        LarpSliderRow(
            title = "Bar Spacing",
            valueLabel = "${"%.1f".format(waveformBarSpacing)} dp",
            value = waveformBarSpacing,
            valueRange = 0f..8f,
            onValueChange = { OverlayPreferences.setWaveformBarSpacing(context, (it * 10f).roundToInt() / 10f) },
        )

        LarpDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (visualizerMode) {
                    OverlayPreferences.VisualizerMode.SIMULATED -> LarpIcons.GraphicEq
                    OverlayPreferences.VisualizerMode.BPM -> LarpIcons.Speed
                    OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> LarpIcons.GraphicEq
                    OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> LarpIcons.Audiotrack
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(text = "Visualizer Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (visualizerMode) {
                        OverlayPreferences.VisualizerMode.SIMULATED -> "Sinusoidal wave oscillation"
                        OverlayPreferences.VisualizerMode.BPM -> "BPM tempo-locked rhythm section (0% CPU)"
                        OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> "Real-time internal playback stream directly from phone (0ms latency)"
                        OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> "30s preview dynamic frequency bands"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LarpConnectedButtons(
            options = OverlayPreferences.VisualizerMode.entries.map { mode ->
                LarpButtonOption(
                    checked = visualizerMode == mode,
                    label = when (mode) {
                        OverlayPreferences.VisualizerMode.SIMULATED -> "Simulated"
                        OverlayPreferences.VisualizerMode.BPM -> "BPM"
                        OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> "Phone Audio"
                        OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> "Preview"
                    },
                    onCheckedChange = {
                        when (mode) {
                            OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> {
                                if (visualizerMode != mode) onRequestAudioPreviewWarning()
                            }
                            OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> {
                                if (!isLiveCapturing) {
                                    requestLiveAudioCapture()
                                } else {
                                    OverlayPreferences.setVisualizerMode(context, mode)
                                    PreviewAudioPlayer.stop()
                                }
                            }
                            else -> {
                                OverlayPreferences.setVisualizerMode(context, mode)
                                PreviewAudioPlayer.stop()
                                LiveAudioVisualizer.stop(context)
                            }
                        }
                    },
                )
            },
        )

        AnimatedVisibility(
            visible = visualizerMode == OverlayPreferences.VisualizerMode.BPM,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            BpmVisualizerStatusCard(
                currentBpm = currentBpm,
                isBpmResolved = isBpmResolved,
                isBpmDetecting = isBpmDetecting,
                detectedBpmTrack = detectedBpmTrack,
                detectedSource = detectedSource,
            )
        }

        AnimatedVisibility(
            visible = visualizerMode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            DeviceAudioCaptureCard(
                context = context,
                isLiveCapturing = isLiveCapturing,
                liveAmplitudes = liveAmplitudes,
                liveErrorMessage = liveErrorMessage,
                waveformBandCount = waveformBandCount,
                onStartCapture = requestLiveAudioCapture,
            )
        }

        AnimatedVisibility(
            visible = visualizerMode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW,
            enter = LarpMotion.reveal,
            exit = LarpMotion.conceal,
        ) {
            AudioPreviewStatusCard(
                context = context,
                previewSource = previewSource,
                extractionState = extractionState,
                isPreviewPlaying = isPreviewPlaying,
                previewPositionMs = previewPositionMs,
                previewDurationMs = previewDurationMs,
                isLiveCapturing = isLiveCapturing,
                nowPlaying = nowPlaying,
                waveformBandCount = waveformBandCount,
                onRequestLiveCapture = requestLiveAudioCapture,
            )
        }
    }
}

@Composable
private fun VisualizerModeInfoCard(
    verticalSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

@Composable
private fun BpmVisualizerStatusCard(
    currentBpm: Float?,
    isBpmResolved: Boolean,
    isBpmDetecting: Boolean,
    detectedBpmTrack: String?,
    detectedSource: String?,
) {
    VisualizerModeInfoCard(verticalSpacing = 8.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LarpIcons.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBpmResolved && currentBpm != null) {
                        "Tempo Beat Sync (${currentBpm.toInt()} BPM)"
                    } else {
                        "Tempo Beat Sync (No BPM)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when {
                        isBpmDetecting -> "Detecting track tempo..."
                        isBpmResolved && detectedSource != null -> "${detectedBpmTrack ?: "Track"} • Verified via $detectedSource"
                        isBpmResolved && currentBpm != null -> "${detectedBpmTrack ?: "Track"} • ${currentBpm.toInt()} BPM"
                        else -> "BPM could not be resolved (pulsing inactive)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBpmDetecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            text = "Synthesizes kick drums on bass bars, snares on center bars, and hi-hats on treble bars in lockstep with the song tempo. Smooth, lightweight, and zero battery drain.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceAudioCaptureCard(
    context: Context,
    isLiveCapturing: Boolean,
    liveAmplitudes: FloatArray?,
    liveErrorMessage: String?,
    waveformBandCount: Int,
    onStartCapture: () -> Unit,
) {
    VisualizerModeInfoCard(verticalSpacing = 10.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LarpIcons.GraphicEq,
                contentDescription = null,
                tint = if (isLiveCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Internal Playback Capture (Digital Loopback)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (isLiveCapturing) "Direct internal audio stream active • 0ms latency"
                    else (liveErrorMessage ?: "Screen/Audio recording permission required"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (liveErrorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLiveCapturing) {
                LiveTrackVisualizer(
                    amplitudes = liveAmplitudes,
                    isPlaying = true,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(width = 44.dp, height = 24.dp),
                    barCount = waveformBandCount,
                )
            }
        }

        if (!isLiveCapturing) {
            Button(onClick = onStartCapture, modifier = Modifier.fillMaxWidth()) {
                Icon(LarpIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Grant Permission & Start Live Capture", modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Button(
                onClick = {
                    LiveAudioVisualizer.stop(context)
                    OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.SIMULATED)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(LarpIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Stop Live Capture", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Captures internal digital audio playback (Android 10+) using the system screen/audio recording method. Directly streams from Spotify, YouTube Music, Apple Music, or any app with zero mic noise, zero 30s limit, and 0ms latency.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AudioPreviewStatusCard(
    context: Context,
    previewSource: OverlayPreferences.PreviewAudioSource,
    extractionState: ExtractionState,
    isPreviewPlaying: Boolean,
    previewPositionMs: Long,
    previewDurationMs: Long,
    isLiveCapturing: Boolean,
    nowPlaying: MediaTrackInfo,
    waveformBandCount: Int,
    onRequestLiveCapture: () -> Unit,
) {
    VisualizerModeInfoCard(verticalSpacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Preview Audio Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LarpConnectedButtons(
                options = OverlayPreferences.PreviewAudioSource.entries.map { source ->
                    LarpButtonOption(
                        checked = previewSource == source,
                        label = source.title,
                        onCheckedChange = {
                            if (source == OverlayPreferences.PreviewAudioSource.DEVICE) {
                                if (!isLiveCapturing) {
                                    onRequestLiveCapture()
                                } else {
                                    OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.DEVICE_AUDIO)
                                    PreviewAudioPlayer.stop()
                                }
                            } else {
                                OverlayPreferences.setPreviewAudioSource(context, source)
                                PreviewAudioPlayer.stop()
                                val curTitle = if (nowPlaying.hasMedia) nowPlaying.title else "Starboy"
                                val curArtist = if (nowPlaying.hasMedia) nowPlaying.artist else "The Weeknd"
                                AudioPreviewExtractor.syncTrack(context, curTitle, curArtist, true, waveformBandCount, source)
                            }
                        },
                    )
                },
            )
            Text(
                text = previewSource.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LarpDivider()

        when (extractionState) {
            is ExtractionState.Idle -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LarpIcons.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Waiting for song playback to analyze audio...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ExtractionState.Processing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Processing: ${extractionState.trackTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Downloading 30s preview & decomposing ${extractionState.bandCount} frequency bands offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            is ExtractionState.Success -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LarpIcons.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Successfully Processed: ${extractionState.trackTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Synced from ${extractionState.sourceName} • ${extractionState.frameCount} frames • ${extractionState.bandCount} acoustic bands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val audioSource = extractionState.audioFilePath ?: extractionState.previewUrl
                if (audioSource != null) {
                    LarpDivider()
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
                            FilledTonalIconButton(
                                onClick = { PreviewAudioPlayer.togglePlayback(context, audioSource) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) LarpIcons.Pause else LarpIcons.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = if (isPreviewPlaying) "Playing 30s Audio Preview" else "Play 30s iTunes Preview",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${formatPreviewTime(previewPositionMs)} / ${formatPreviewTime(previewDurationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = LarpIcons.VolumeUp,
                            contentDescription = null,
                            tint = if (isPreviewPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            if (previewDurationMs > 0) (previewPositionMs.toFloat() / previewDurationMs).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
            is ExtractionState.Failed -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = LarpIcons.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Could not process: ${extractionState.trackTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${extractionState.reason} • Falling back to simulated waveform",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatPreviewTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
