package ca.saboor.larpdot.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.flashlight.FlashlightMode
import kotlin.math.roundToInt

internal fun formatFlashlightTileSubtitle(
    isAvailable: Boolean,
    isOn: Boolean,
    mode: FlashlightMode,
    isBoostActive: Boolean,
    isStrengthSupported: Boolean,
    strength: Int,
    maxStrength: Int,
): String {
    if (!isAvailable) return "Unavailable"
    if (!isOn) return "Off"
    if (mode != FlashlightMode.STEADY) return mode.label
    if (isStrengthSupported && maxStrength > 1) {
        val percent = ((strength.coerceIn(1, maxStrength).toFloat() / maxStrength.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(1, 100)
        return if (isBoostActive) "Boost • $percent%" else "$percent%"
    }
    return if (isBoostActive) "Boost" else "On"
}

/** Quick Settings tile for toggling LarpDot's flashlight with live strength, boost, and mode status. */
class FlashlightTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        FlashlightController.init(applicationContext)
        syncTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        FlashlightController.init(applicationContext)
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        FlashlightController.init(applicationContext)
        if (!FlashlightController.isAvailable.value) {
            syncTile()
            return
        }
        FlashlightController.toggleFlashlight()
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val available = FlashlightController.isAvailable.value
        val isOn = FlashlightController.isFlashlightOn.value
        tile.state = when {
            !available -> Tile.STATE_UNAVAILABLE
            isOn -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = formatFlashlightTileSubtitle(
                isAvailable = available,
                isOn = isOn,
                mode = FlashlightController.flashlightMode.value,
                isBoostActive = FlashlightController.isBoostActive.value,
                isStrengthSupported = FlashlightController.isStrengthSupported.value,
                strength = FlashlightController.torchStrength.value,
                maxStrength = FlashlightController.maxStrength.value,
            )
        }
        tile.updateTile()
    }
}
