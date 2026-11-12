package ca.saboor.larpdot.flashlight

/** 100% is the standard torch API's maximum, even when the slider exposes boost levels. */
internal data class FlashlightBrightnessRange(val standardMaximum: Int, val extendedMaximum: Int) {
    fun needsCamera(level: Int): Boolean = extendedMaximum > standardMaximum && level > standardMaximum
}
