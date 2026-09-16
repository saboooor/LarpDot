package ca.saboor.larpdot.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.media.MediaPlaybackState

/**
 * Authentic Android native media player notification card,
 * copied and adapted from app.rippin.android.components.MediaPlayer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaPlayer(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasNotificationAccess = remember(context) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    }

    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()
    val albumArt = nowPlaying.albumArt
    val progress = if (nowPlaying.durationMs > 0) {
        (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1E222A)
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Android Media Notification Background: Album Art with subtle dark scrims
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = "Album art background",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                // Horizontal scrim to keep left-side titles and controls legible
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Black.copy(alpha = 0.58f),
                                    Color.Black.copy(alpha = 0.25f)
                                )
                            )
                        )
                )
                // Vertical gradient scrim for bottom scrubber/buttons
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF243242),
                                    Color(0xFF1A1F29),
                                    Color(0xFF12151B)
                                )
                            )
                        )
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasNotificationAccess) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Grant Notification Access for Music", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Row 1: Header (Music Note Icon & "This phone" Output Switcher Chip)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Media",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(20.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick simulation toggle chip
                        Surface(
                            onClick = {
                                MediaPlaybackState.setSimulatedPlayback(!nowPlaying.isSimulated)
                            },
                            shape = CircleShape,
                            color = if (nowPlaying.isSimulated) nowPlaying.dominantColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (nowPlaying.isSimulated) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (nowPlaying.isSimulated) nowPlaying.dominantColor else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (nowPlaying.isSimulated) "Simulating" else "Simulate",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (nowPlaying.isSimulated) nowPlaying.dominantColor else Color.White
                                )
                            }
                        }

                        // "This phone" output switcher chip
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "This phone",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Row 2: Track Title & Artist (Left) + Play/Pause Button (Right)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = nowPlaying.title.ifEmpty { "No Media Playing" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = nowPlaying.artist.ifEmpty { "Play music in Spotify or tap Simulate" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Native Android 13/14 rounded square Play/Pause button
                    Surface(
                        onClick = {
                            if (!nowPlaying.hasMedia && !nowPlaying.isSimulated) {
                                MediaPlaybackState.setSimulatedPlayback(true)
                            } else {
                                MediaPlaybackState.togglePlayPause()
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF1B1A1E),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Row 3: Scrubber Line with Controls (Skip Previous, Wavy Indicator, Skip Next, Star, Shuffle)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { MediaPlaybackState.skipPrevious() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(32.dp)
                            .pointerInput(nowPlaying.durationMs) {
                                detectTapGestures { offset ->
                                    if (nowPlaying.durationMs > 0) {
                                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                        MediaPlaybackState.seekTo((fraction * nowPlaying.durationMs).toLong())
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.32f),
                        )
                    }

                    IconButton(
                        onClick = { MediaPlaybackState.skipNext() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next track",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}
