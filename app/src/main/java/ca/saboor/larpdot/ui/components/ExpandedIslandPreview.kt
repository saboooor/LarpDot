package ca.saboor.larpdot.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.DominantColorExtractor
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactIslandContent
import ca.saboor.larpdot.ui.overlay.ExpandedIslandContent
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.squircleShape

/**
 * Authentic live preview of the Dynamic Island (both minimized pill and expanded card)
 * directly inside the app, accurately replicating island dimensions, hole-punch alignment,
 * dynamic styles, and fluid progress outlines.
 */
@Composable
fun ExpandedIslandPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()
    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()

    val hasNotificationAccess = remember(context) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    }

    val sampleTrack = remember {
        val sampleArt = DominantColorExtractor.createSampleArtwork("Starboy")
        MediaTrackInfo(
            title = "Starboy",
            artist = "The Weeknd",
            albumArt = sampleArt,
            isPlaying = true,
            positionMs = 64000L,
            durationMs = 230000L,
            dominantColor = DominantColorExtractor.extractDominantColor(sampleArt),
            isSimulated = true,
        )
    }

    // Always display realistic media so styles, shapes, blur, and waveforms are immediately visible
    val previewTrack = if (nowPlaying.hasMedia) nowPlaying else sampleTrack

    val cutoutDiameterDp = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceIn(20.dp, 32.dp)
    val compactExtraDp = if (minimizedStyle == OverlayPreferences.AlbumArtStyle.BLENDED) 108.dp else 72.dp
    val compactWidth = cutoutDiameterDp + compactExtraDp

    val progressFraction = if (previewTrack.durationMs > 0) {
        (previewTrack.positionMs.toFloat() / previewTrack.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "preview_progress",
    )

    // Concentric expanded corner radius matching actual ExpandedIslandOverlay
    val displayRadiusDp = with(density) { cutoutInfo.displayCornerRadiusPx.toDp() }.coerceAtLeast(24.dp)
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    val topMarginDp = (cutoutCenterYDp - 18.dp).coerceAtLeast(8.dp)
    val concentricCornerRadiusDp = (displayRadiusDp - topMarginDp).coerceAtLeast(16.dp)
    val expandedCornerRadiusDp = concentricCornerRadiusDp.coerceAtLeast(60.dp)
    val expandedCornerRadiusPx = with(density) { expandedCornerRadiusDp.toPx() }
    val containerShape = squircleShape(expandedCornerRadiusPx, 0.80f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = "Live Island Preview")

        // Compact Pill Live Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(compactWidth)
                    .height(36.dp)
                    .then(
                        if (showProgressOutline) {
                            Modifier.islandFluidProgressBorder(
                                progressFraction = animatedProgress,
                                cornerRadius = 18.dp,
                                shape = RoundedCornerShape(18.dp),
                                strokeWidth = 0.75.dp,
                                trackColor = Color(0x30FFFFFF),
                                progressColor = previewTrack.dominantColor,
                            )
                        } else Modifier
                    ),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactIslandContent(
                        mediaInfo = previewTrack,
                        cutoutDiameterDp = cutoutDiameterDp,
                        onExpand = {},
                    )
                    // Hardware camera cutout visualizer centered between wings
                    Box(
                        modifier = Modifier
                            .size((cutoutDiameterDp - 4.dp).coerceAtLeast(10.dp))
                            .clip(CircleShape)
                            .background(Color(0xFF0D0D0D))
                            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                    )
                }
            }
        }

        // Expanded Card Live Preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .then(
                    if (showProgressOutline) {
                        Modifier.islandFluidProgressBorder(
                            progressFraction = animatedProgress,
                            cornerRadius = expandedCornerRadiusDp,
                            shape = containerShape,
                            strokeWidth = 0.75.dp,
                            trackColor = Color(0x30FFFFFF),
                            progressColor = previewTrack.dominantColor,
                        )
                    } else Modifier
                ),
            shape = containerShape,
            color = Color.Black,
            shadowElevation = 14.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                ExpandedIslandContent(
                    mediaInfo = previewTrack,
                    cutoutDiameterDp = cutoutDiameterDp,
                    isExpanded = true,
                    onCollapse = { /* In-app preview */ },
                    cutoutInfo = cutoutInfo,
                    cardHorizontalMarginDp = 16.dp,
                )

                // Hardware camera cutout punch hole positioned accurately over the organic scoop shader
                val screenWidthDp = configuration.screenWidthDp.dp
                val previewCardWidthDp = screenWidthDp - 32.dp
                val orientedCenterXDp = with(density) { cutoutInfo.centerX.toDp() } - 16.dp
                val punchHoleCenterXDp = orientedCenterXDp.coerceIn(
                    cutoutDiameterDp / 2f,
                    previewCardWidthDp - (cutoutDiameterDp / 2f),
                )
                val punchHoleCenterYDp = 18.dp

                Box(
                    modifier = Modifier
                        .offset(
                            x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                            y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                        )
                        .size((cutoutDiameterDp - 4.dp).coerceAtLeast(10.dp))
                        .clip(CircleShape)
                        .background(Color(0xFF0D0D0D))
                        .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
                )
            }
        }

        // Action / Permission Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hasNotificationAccess) {
                FilledTonalButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grant Access", style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedButton(
                onClick = {
                    if (nowPlaying.isSimulated) {
                        MediaPlaybackState.setSimulatedPlayback(false)
                    } else {
                        MediaPlaybackState.setSimulatedPlayback(true)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (nowPlaying.isSimulated) Icons.Default.Stop else Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (nowPlaying.isSimulated) "Stop Demo" else "Simulate Playback",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
