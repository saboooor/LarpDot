package ca.saboor.larpdot.service

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object FlashlightPrefsStore {
    private const val KEY_BOOST_STRENGTH = "flashlight_boost_strength"
    private const val KEY_FLASHLIGHT_EXPANDED_STYLE = "flashlight_expanded_style"
    private const val KEY_FLASHLIGHT_BEAM_BLUR = "flashlight_beam_blur"

    private val showFlashlightIsland = BooleanPref("show_flashlight_island", true)
    private val showMinimizedOutline = BooleanPref("show_minimized_flashlight_outline", true)
    private val showExpandedOutline = BooleanPref("show_expanded_flashlight_outline", true)
    private val tapToToggle = BooleanPref("flashlight_tap_to_toggle", true)
    private val boost = BooleanPref("flashlight_boost_enabled", false)
    private val beamBlur = BooleanPref(KEY_FLASHLIGHT_BEAM_BLUR, false)
    private val beamAccentColor = BooleanPref("flashlight_beam_accent_color", true)
    private val autoOffMinutes = IntPref("flashlight_auto_off_minutes", 0, 0, 60)

    private val _flashlightExpandedStyleFlow = MutableStateFlow(OverlayPreferences.FlashlightExpandedStyle.BEAM)
    val flashlightExpandedStyleFlow: StateFlow<OverlayPreferences.FlashlightExpandedStyle> =
        _flashlightExpandedStyleFlow.asStateFlow()

    val showFlashlightIslandFlow: StateFlow<Boolean> get() = showFlashlightIsland.flow
    val showMinimizedFlashlightOutlineFlow: StateFlow<Boolean> get() = showMinimizedOutline.flow
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> get() = showExpandedOutline.flow
    val flashlightTapToToggleFlow: StateFlow<Boolean> get() = tapToToggle.flow
    val flashlightBoostFlow: StateFlow<Boolean> get() = boost.flow
    val flashlightBeamBlurFlow: StateFlow<Boolean> get() = beamBlur.flow
    val flashlightBeamAccentColorFlow: StateFlow<Boolean> get() = beamAccentColor.flow
    val flashlightAutoOffMinutesFlow: StateFlow<Int> get() = autoOffMinutes.flow

    fun getFlashlightExpandedStyle(prefs: SharedPreferences): OverlayPreferences.FlashlightExpandedStyle {
        val saved = prefs.getString(KEY_FLASHLIGHT_EXPANDED_STYLE, null)
        if (saved == "BLURRED_BEAM") {
            if (!prefs.contains(KEY_FLASHLIGHT_BEAM_BLUR)) {
                beamBlur.set(prefs, true)
            }
            setFlashlightExpandedStyle(prefs, OverlayPreferences.FlashlightExpandedStyle.BEAM)
            return OverlayPreferences.FlashlightExpandedStyle.BEAM
        }
        val resolved = OverlayPreferences.FlashlightExpandedStyle.entries
            .firstOrNull { it.name == saved }
            ?: OverlayPreferences.FlashlightExpandedStyle.BEAM
        _flashlightExpandedStyleFlow.value = resolved
        return resolved
    }

    fun setFlashlightExpandedStyle(prefs: SharedPreferences, style: OverlayPreferences.FlashlightExpandedStyle) {
        prefs.edit().putString(KEY_FLASHLIGHT_EXPANDED_STYLE, style.name).apply()
        _flashlightExpandedStyleFlow.value = style
    }

    fun isFlashlightBeamBlurEnabled(prefs: SharedPreferences): Boolean = beamBlur.get(prefs)
    fun setFlashlightBeamBlurEnabled(prefs: SharedPreferences, enabled: Boolean) = beamBlur.set(prefs, enabled)

    fun isFlashlightBeamAccentColorEnabled(prefs: SharedPreferences): Boolean = beamAccentColor.get(prefs)
    fun setFlashlightBeamAccentColorEnabled(prefs: SharedPreferences, enabled: Boolean) = beamAccentColor.set(prefs, enabled)

    fun isShowFlashlightIslandEnabled(prefs: SharedPreferences): Boolean = showFlashlightIsland.get(prefs)
    fun setShowFlashlightIslandEnabled(prefs: SharedPreferences, enabled: Boolean) = showFlashlightIsland.set(prefs, enabled)

    fun isShowMinimizedFlashlightOutlineEnabled(prefs: SharedPreferences): Boolean = showMinimizedOutline.get(prefs)
    fun setShowMinimizedFlashlightOutlineEnabled(prefs: SharedPreferences, enabled: Boolean) = showMinimizedOutline.set(prefs, enabled)

    fun isShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences): Boolean = showExpandedOutline.get(prefs)
    fun setShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences, enabled: Boolean) = showExpandedOutline.set(prefs, enabled)

    fun isFlashlightTapToToggleEnabled(prefs: SharedPreferences): Boolean = tapToToggle.get(prefs)
    fun setFlashlightTapToToggleEnabled(prefs: SharedPreferences, enabled: Boolean) = tapToToggle.set(prefs, enabled)

    fun isFlashlightBoostEnabled(prefs: SharedPreferences): Boolean = boost.get(prefs)
    fun setFlashlightBoostEnabled(prefs: SharedPreferences, enabled: Boolean) = boost.set(prefs, enabled)

    fun getBoostStrength(prefs: SharedPreferences): Int = prefs.getInt(KEY_BOOST_STRENGTH, 1)
    fun setBoostStrength(prefs: SharedPreferences, strength: Int) {
        prefs.edit().putInt(KEY_BOOST_STRENGTH, strength).apply()
    }

    fun getAutoOffMinutes(prefs: SharedPreferences): Int = autoOffMinutes.get(prefs)
    fun setAutoOffMinutes(prefs: SharedPreferences, minutes: Int) = autoOffMinutes.set(prefs, minutes)
}
