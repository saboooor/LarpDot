package ca.saboor.larpdot.navigation

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    Home(
        label = "Home",
        icon = LarpIcons.Home,
    ),
    Appearance(
        label = "Appearance",
        icon = LarpIcons.Palette,
    ),
    Music(
        label = "Music",
        icon = LarpIcons.MusicNote,
    ),
    LiveActivities(
        label = "Activities",
        icon = LarpIcons.NotificationsActive,
    ),
    Flashlight(
        label = "Flashlight",
        icon = LarpIcons.FlashlightOn,
    ),
    Custom(
        label = "Custom",
        icon = LarpIcons.Apps,
    ),
}
