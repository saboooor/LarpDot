package ca.saboor.larpdot.ui.screens

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
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
            valueLabel = if (waveformBandCount == 0) "Off" else "$waveformBandCount",
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

        Text(text = "Visualizer Mode", style = MaterialTheme.typography.titleMedium)

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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
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
    @Suppress("UNUSED_PARAMETER") detectedSource: String?,
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
                        "${currentBpm.toInt()} BPM"
                    } else {
                        "No BPM detected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when {
                        isBpmDetecting -> "Detecting tempo…"
                        isBpmResolved && currentBpm != null -> detectedBpmTrack ?: "Current track"
                        else -> "Play a song to detect tempo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBpmDetecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
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
                    text = "Device Audio Capture",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (isLiveCapturing) "Active"
                    else (liveErrorMessage ?: "Requires screen capture permission"),
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
                Text("Start Capture", modifier = Modifier.padding(start = 8.dp))
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
                Text("Stop Capture", modifier = Modifier.padding(start = 8.dp))
            }
        }
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
    onRequestLiveCapture: () -> Unit,
) {
    VisualizerModeInfoCard(verticalSpacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Preview Source",
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
                            }
                        },
                    )
                },
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
                        text = "Play a song to analyze",
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
                            text = extractionState.trackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Analyzing audio…",
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = extractionState.trackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = extractionState.sourceName,
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
                                    text = "Audio Preview",
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
                            text = extractionState.trackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = extractionState.reason,
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
