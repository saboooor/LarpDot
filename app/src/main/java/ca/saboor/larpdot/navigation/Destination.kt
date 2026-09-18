package ca.saboor.larpdot.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
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
    Flashlight(
        label = "Flashlight",
        icon = Icons.Default.FlashlightOn,
    ),
}
