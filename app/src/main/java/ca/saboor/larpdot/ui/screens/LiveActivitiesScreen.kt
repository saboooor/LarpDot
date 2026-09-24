package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader

@Composable
fun LiveActivitiesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by OverlayPreferences.notificationActivitySettingsFlow.collectAsState()
    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()

    LaunchedEffect(Unit) {
        OverlayPreferences.getNotificationActivitySettings(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeader(title = "Live Activities")
            LarpCard {
                ActivitySettingRow(
                    title = "Show Live Activities",
                    subtitle = "Turn ongoing notifications into island activities",
                    icon = Icons.Default.NotificationsActive,
                    checked = settings.enabled,
                    onCheckedChange = {
                        OverlayPreferences.setNotificationActivitySettings(
                            context,
                            settings.copy(enabled = it),
                        )
                    },
                )

                AnimatedVisibility(
                    visible = settings.enabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ActivitySettingRow(
                            title = "Progress",
                            subtitle = "Downloads, uploads, installs, and other progress",
                            icon = Icons.Default.Download,
                            checked = settings.progress,
                            onCheckedChange = {
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(progress = it),
                                )
                            },
                        )
                        ActivitySettingRow(
                            title = "Navigation",
                            subtitle = "Ongoing turn-by-turn navigation",
                            icon = Icons.Default.Navigation,
                            checked = settings.navigation,
                            onCheckedChange = {
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(navigation = it),
                                )
                            },
                        )
                        ActivitySettingRow(
                            title = "Calls",
                            subtitle = "Active call status and controls",
                            icon = Icons.Default.Call,
                            checked = settings.calls,
                            onCheckedChange = {
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(calls = it),
                                )
                            },
                        )
                        ActivitySettingRow(
                            title = "Timers",
                            subtitle = "Ongoing notification chronometers",
                            icon = Icons.Default.Timer,
                            checked = settings.timers,
                            onCheckedChange = {
                                OverlayPreferences.setNotificationActivitySettings(
                                    context,
                                    settings.copy(timers = it),
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Appearance")
            LarpCard {
                ActivitySettingRow(
                    title = "Show Right Side Info on Dots",
                    subtitle = "Show secondary details alongside the icon, making dots into mini pills",
                    icon = Icons.Default.MoreHoriz,
                    checked = showDotRightSideInfo,
                    onCheckedChange = {
                        OverlayPreferences.setShowDotRightSideInfoEnabled(context, it)
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivitySettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
