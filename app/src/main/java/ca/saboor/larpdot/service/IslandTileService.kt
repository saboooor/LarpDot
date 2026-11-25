package ca.saboor.larpdot.service

import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

internal fun formatIslandTileSubtitle(
    hasPermission: Boolean,
    enabled: Boolean,
    pausedByDnd: Boolean,
): String = when {
    !hasPermission -> "Permission needed"
    !enabled -> "Hidden"
    pausedByDnd -> "Paused (DND)"
    else -> "Active"
}

/** Quick Settings tile to toggle the Dynamic Island overlay on or off without opening the app. */
class IslandTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        syncTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val nextEnabled = !OverlayPreferences.isOverlayEnabled(this)
        OverlayPreferences.setOverlayEnabled(this, nextEnabled)
        if (nextEnabled && !DotAccessibilityService.isServiceConnected.value && Settings.canDrawOverlays(this)) {
            DotOverlayService.start(this)
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val enabled = OverlayPreferences.isOverlayEnabled(this)
        val hasPermission = DotAccessibilityService.isServiceConnected.value || Settings.canDrawOverlays(this)
        val pausedByDnd = enabled &&
            OverlayPreferences.isHideInDndEnabled(this) &&
            SystemEventManager.isDoNotDisturbActive.value
        tile.state = when {
            !hasPermission -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = formatIslandTileSubtitle(
                hasPermission = hasPermission,
                enabled = enabled,
                pausedByDnd = pausedByDnd,
            )
        }
        tile.updateTile()
    }
}
