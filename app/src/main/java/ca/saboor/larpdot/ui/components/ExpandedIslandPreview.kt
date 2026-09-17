package ca.saboor.larpdot.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactIslandContent
import ca.saboor.larpdot.ui.overlay.ExpandedIslandContent
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.squircleShape

/**
 * Authentic live preview of the Dynamic Island (both minimized pill and expanded card)
 * directly inside the app.
 */
@Composable
fun ExpandedIslandPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()
    val showProgressOutline by OverlayPreferences.showProgressOutlineFlow.collectAsState()

    val hasNotificationAccess = remember(context) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    }

    val cutoutDiameterDp = with(density) { (cutoutInfo.radiusPx * 2f).toDp() }.coerceIn(16.dp, 60.dp)

    val progressFraction = if (nowPlaying.durationMs > 0) {
        (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "preview_progress",
    )

    val squircleRadiusPx = with(density) { 32.dp.toPx() }
    val containerShape = squircleShape(squircleRadiusPx)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    .width(cutoutDiameterDp + 74.dp)
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black,
                shadowElevation = 4.dp,
            ) {
                CompactIslandContent(
                    mediaInfo = nowPlaying,
                    cutoutDiameterDp = cutoutDiameterDp,
                    onExpand = {},
                )
            }
        }

        // Expanded Card Live Preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .then(
                    if (showProgressOutline) {
                        Modifier.islandFluidProgressBorder(
                            progressFraction = animatedProgress,
                            cornerRadius = 32.dp,
                            shape = containerShape,
                            strokeWidth = 1.dp,
                            trackColor = Color(0x30FFFFFF),
                            progressColor = nowPlaying.dominantColor,
                        )
                    } else Modifier
                ),
            shape = containerShape,
            color = Color.Black,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center,
            ) {
                ExpandedIslandContent(
                    mediaInfo = nowPlaying,
                    cutoutDiameterDp = cutoutDiameterDp,
                    isExpanded = true,
                    onCollapse = { /* In-app preview */ },
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grant Music Access", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (nowPlaying.isSimulated) {
                            MediaPlaybackState.setSimulatedPlayback(false)
                        } else {
                            MediaPlaybackState.setSimulatedPlayback(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (nowPlaying.isSimulated) Icons.Default.Stop else Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (nowPlaying.isSimulated) "Stop Demo Track" else "Preview with Demo Track",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
