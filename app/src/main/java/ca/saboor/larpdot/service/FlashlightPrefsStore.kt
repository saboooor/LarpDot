package ca.saboor.larpdot.service

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object FlashlightPrefsStore {
    private const val KEY_SHOW_FLASHLIGHT_ISLAND = "show_flashlight_island"
    private const val KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE = "show_minimized_flashlight_outline"
    private const val KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE = "show_expanded_flashlight_outline"
    private const val KEY_FLASHLIGHT_TAP_TO_TOGGLE = "flashlight_tap_to_toggle"
    private const val KEY_FLASHLIGHT_BOOST = "flashlight_boost_enabled"
    private const val KEY_BOOST_STRENGTH = "flashlight_boost_strength"
    private const val KEY_FLASHLIGHT_EXPANDED_STYLE = "flashlight_expanded_style"
    private const val KEY_FLASHLIGHT_BEAM_BLUR = "flashlight_beam_blur"
    private const val KEY_FLASHLIGHT_BEAM_ACCENT_COLOR = "flashlight_beam_accent_color"

    private val _showFlashlightIslandFlow = MutableStateFlow(true)
    val showFlashlightIslandFlow: StateFlow<Boolean> = _showFlashlightIslandFlow.asStateFlow()

    private val _showMinimizedFlashlightOutlineFlow = MutableStateFlow(true)
    val showMinimizedFlashlightOutlineFlow: StateFlow<Boolean> = _showMinimizedFlashlightOutlineFlow.asStateFlow()

    private val _showExpandedFlashlightOutlineFlow = MutableStateFlow(true)
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> = _showExpandedFlashlightOutlineFlow.asStateFlow()

    private val _flashlightTapToToggleFlow = MutableStateFlow(true)
    val flashlightTapToToggleFlow: StateFlow<Boolean> = _flashlightTapToToggleFlow.asStateFlow()

    private val _flashlightBoostFlow = MutableStateFlow(false)
    val flashlightBoostFlow: StateFlow<Boolean> = _flashlightBoostFlow.asStateFlow()

    private val _flashlightExpandedStyleFlow = MutableStateFlow(OverlayPreferences.FlashlightExpandedStyle.BEAM)
    val flashlightExpandedStyleFlow: StateFlow<OverlayPreferences.FlashlightExpandedStyle> =
        _flashlightExpandedStyleFlow.asStateFlow()

    private val _flashlightBeamBlurFlow = MutableStateFlow(false)
    val flashlightBeamBlurFlow: StateFlow<Boolean> = _flashlightBeamBlurFlow.asStateFlow()

    private val _flashlightBeamAccentColorFlow = MutableStateFlow(true)
    val flashlightBeamAccentColorFlow: StateFlow<Boolean> = _flashlightBeamAccentColorFlow.asStateFlow()

    private var isShowFlashlightIslandInitialized = false
    private var isShowMinimizedFlashlightOutlineInitialized = false
    private var isShowExpandedFlashlightOutlineInitialized = false
    private var isFlashlightTapToToggleInitialized = false
    private var isFlashlightBoostInitialized = false

    fun getFlashlightExpandedStyle(prefs: SharedPreferences): OverlayPreferences.FlashlightExpandedStyle {
        val saved = prefs.getString(KEY_FLASHLIGHT_EXPANDED_STYLE, null)
        if (saved == "BLURRED_BEAM") {
            if (!prefs.contains(KEY_FLASHLIGHT_BEAM_BLUR)) {
                prefs.edit().putBoolean(KEY_FLASHLIGHT_BEAM_BLUR, true).apply()
                _flashlightBeamBlurFlow.value = true
            }
            prefs.edit().putString(KEY_FLASHLIGHT_EXPANDED_STYLE, OverlayPreferences.FlashlightExpandedStyle.BEAM.name).apply()
            _flashlightExpandedStyleFlow.value = OverlayPreferences.FlashlightExpandedStyle.BEAM
            return OverlayPreferences.FlashlightExpandedStyle.BEAM
        }
        _flashlightExpandedStyleFlow.value = OverlayPreferences.FlashlightExpandedStyle.entries
            .firstOrNull { it.name == saved }
            ?: OverlayPreferences.FlashlightExpandedStyle.BEAM
        return _flashlightExpandedStyleFlow.value
    }

    fun setFlashlightExpandedStyle(prefs: SharedPreferences, style: OverlayPreferences.FlashlightExpandedStyle) {
        prefs.edit().putString(KEY_FLASHLIGHT_EXPANDED_STYLE, style.name).apply()
        _flashlightExpandedStyleFlow.value = style
    }

    fun isFlashlightBeamBlurEnabled(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(KEY_FLASHLIGHT_BEAM_BLUR, false)
        _flashlightBeamBlurFlow.value = enabled
        return enabled
    }

    fun setFlashlightBeamBlurEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLASHLIGHT_BEAM_BLUR, enabled).apply()
        _flashlightBeamBlurFlow.value = enabled
    }

    fun isFlashlightBeamAccentColorEnabled(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(KEY_FLASHLIGHT_BEAM_ACCENT_COLOR, true)
        _flashlightBeamAccentColorFlow.value = enabled
        return enabled
    }

    fun setFlashlightBeamAccentColorEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLASHLIGHT_BEAM_ACCENT_COLOR, enabled).apply()
        _flashlightBeamAccentColorFlow.value = enabled
    }

    fun isShowFlashlightIslandEnabled(prefs: SharedPreferences): Boolean {
        if (!isShowFlashlightIslandInitialized) {
            _showFlashlightIslandFlow.value = prefs.getBoolean(KEY_SHOW_FLASHLIGHT_ISLAND, true)
            isShowFlashlightIslandInitialized = true
        }
        return _showFlashlightIslandFlow.value
    }

    fun setShowFlashlightIslandEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FLASHLIGHT_ISLAND, enabled).apply()
        _showFlashlightIslandFlow.value = enabled
        isShowFlashlightIslandInitialized = true
    }

    fun isShowMinimizedFlashlightOutlineEnabled(prefs: SharedPreferences): Boolean {
        if (!isShowMinimizedFlashlightOutlineInitialized) {
            _showMinimizedFlashlightOutlineFlow.value = prefs.getBoolean(KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE, true)
            isShowMinimizedFlashlightOutlineInitialized = true
        }
        return _showMinimizedFlashlightOutlineFlow.value
    }

    fun setShowMinimizedFlashlightOutlineEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE, enabled).apply()
        _showMinimizedFlashlightOutlineFlow.value = enabled
        isShowMinimizedFlashlightOutlineInitialized = true
    }

    fun isShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences): Boolean {
        if (!isShowExpandedFlashlightOutlineInitialized) {
            _showExpandedFlashlightOutlineFlow.value = prefs.getBoolean(KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE, true)
            isShowExpandedFlashlightOutlineInitialized = true
        }
        return _showExpandedFlashlightOutlineFlow.value
    }

    fun setShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE, enabled).apply()
        _showExpandedFlashlightOutlineFlow.value = enabled
        isShowExpandedFlashlightOutlineInitialized = true
    }

    fun isFlashlightTapToToggleEnabled(prefs: SharedPreferences): Boolean {
        if (!isFlashlightTapToToggleInitialized) {
            _flashlightTapToToggleFlow.value = prefs.getBoolean(KEY_FLASHLIGHT_TAP_TO_TOGGLE, true)
            isFlashlightTapToToggleInitialized = true
        }
        return _flashlightTapToToggleFlow.value
    }

    fun setFlashlightTapToToggleEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLASHLIGHT_TAP_TO_TOGGLE, enabled).apply()
        _flashlightTapToToggleFlow.value = enabled
        isFlashlightTapToToggleInitialized = true
    }

    fun isFlashlightBoostEnabled(prefs: SharedPreferences): Boolean {
        if (!isFlashlightBoostInitialized) {
            _flashlightBoostFlow.value = prefs.getBoolean(KEY_FLASHLIGHT_BOOST, false)
            isFlashlightBoostInitialized = true
        }
        return _flashlightBoostFlow.value
    }

    fun setFlashlightBoostEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLASHLIGHT_BOOST, enabled).apply()
        _flashlightBoostFlow.value = enabled
        isFlashlightBoostInitialized = true
    }

    fun getBoostStrength(prefs: SharedPreferences): Int = prefs.getInt(KEY_BOOST_STRENGTH, 1)

    fun setBoostStrength(prefs: SharedPreferences, strength: Int) {
        prefs.edit().putInt(KEY_BOOST_STRENGTH, strength).apply()
    }
}
