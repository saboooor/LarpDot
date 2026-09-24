package ca.saboor.larpdot.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    Home(
        label = "Home",
        icon = Icons.Default.Home,
    ),
    Music(
        label = "Music",
        icon = Icons.Default.MusicNote,
    ),
    LiveActivities(
        label = "Activities",
        icon = Icons.Default.NotificationsActive,
    ),
    Flashlight(
        label = "Flashlight",
        icon = Icons.Default.FlashlightOn,
    ),
}
