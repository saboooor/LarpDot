package ca.saboor.larpdot.service

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.StateFlow

internal object FlashlightPrefsStore {
    private const val KEY_BOOST_STRENGTH = "flashlight_boost_strength"
    private const val KEY_FLASHLIGHT_EXPANDED_STYLE = "flashlight_expanded_style"
    private const val KEY_FLASHLIGHT_BEAM_BLUR = "flashlight_beam_blur"
    private const val KEY_MINIMIZED_LEFT_SIDE = "minimized_flashlight_left_side"
    private const val KEY_MINIMIZED_RIGHT_SIDE = "minimized_flashlight_right_side"

    private val showFlashlightIsland = BooleanPref("show_flashlight_island", true)
    private val minimizedLeftSide = EnumPref(
        KEY_MINIMIZED_LEFT_SIDE,
        OverlayPreferences.MinimizedFlashlightSideContent.ICON,
        parse = { saved ->
            OverlayPreferences.MinimizedFlashlightSideContent.entries.firstOrNull { it.name == saved }
                ?: OverlayPreferences.MinimizedFlashlightSideContent.ICON
        },
    )
    private val minimizedRightSide = EnumPref(
        KEY_MINIMIZED_RIGHT_SIDE,
        OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS,
        parse = { saved ->
            OverlayPreferences.MinimizedFlashlightSideContent.entries.firstOrNull { it.name == saved }
                ?: OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS
        },
    )
    private val showExpandedOutline = BooleanPref("show_expanded_flashlight_outline", true)
    private val boost = BooleanPref("flashlight_boost_enabled", false)
    private val beamBlur = BooleanPref(KEY_FLASHLIGHT_BEAM_BLUR, false)
    private val beamAccentColor = BooleanPref("flashlight_beam_accent_color", true)
    private val expandedStyle = EnumPref(
        KEY_FLASHLIGHT_EXPANDED_STYLE,
        OverlayPreferences.FlashlightExpandedStyle.BEAM,
        parse = { saved ->
            OverlayPreferences.FlashlightExpandedStyle.entries.firstOrNull { it.name == saved }
                ?: OverlayPreferences.FlashlightExpandedStyle.BEAM
        },
    )

    val flashlightExpandedStyleFlow: StateFlow<OverlayPreferences.FlashlightExpandedStyle> get() = expandedStyle.flow
    val showFlashlightIslandFlow: StateFlow<Boolean> get() = showFlashlightIsland.flow
    val minimizedLeftSideFlow: StateFlow<OverlayPreferences.MinimizedFlashlightSideContent> get() = minimizedLeftSide.flow
    val minimizedRightSideFlow: StateFlow<OverlayPreferences.MinimizedFlashlightSideContent> get() = minimizedRightSide.flow
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> get() = showExpandedOutline.flow
    val flashlightBoostFlow: StateFlow<Boolean> get() = boost.flow
    val flashlightBeamBlurFlow: StateFlow<Boolean> get() = beamBlur.flow
    val flashlightBeamAccentColorFlow: StateFlow<Boolean> get() = beamAccentColor.flow

    fun getFlashlightExpandedStyle(prefs: SharedPreferences): OverlayPreferences.FlashlightExpandedStyle =
        expandedStyle.get(prefs)

    fun setFlashlightExpandedStyle(prefs: SharedPreferences, style: OverlayPreferences.FlashlightExpandedStyle) =
        expandedStyle.set(prefs, style)

    fun isFlashlightBeamBlurEnabled(prefs: SharedPreferences): Boolean = beamBlur.get(prefs)
    fun setFlashlightBeamBlurEnabled(prefs: SharedPreferences, enabled: Boolean) = beamBlur.set(prefs, enabled)

    fun isFlashlightBeamAccentColorEnabled(prefs: SharedPreferences): Boolean = beamAccentColor.get(prefs)
    fun setFlashlightBeamAccentColorEnabled(prefs: SharedPreferences, enabled: Boolean) = beamAccentColor.set(prefs, enabled)

    fun isShowFlashlightIslandEnabled(prefs: SharedPreferences): Boolean = showFlashlightIsland.get(prefs)
    fun setShowFlashlightIslandEnabled(prefs: SharedPreferences, enabled: Boolean) = showFlashlightIsland.set(prefs, enabled)

    fun getMinimizedLeftSide(prefs: SharedPreferences): OverlayPreferences.MinimizedFlashlightSideContent =
        minimizedLeftSide.get(prefs)

    fun setMinimizedLeftSide(prefs: SharedPreferences, value: OverlayPreferences.MinimizedFlashlightSideContent) =
        minimizedLeftSide.set(prefs, value)

    fun getMinimizedRightSide(prefs: SharedPreferences): OverlayPreferences.MinimizedFlashlightSideContent =
        minimizedRightSide.get(prefs)

    fun setMinimizedRightSide(prefs: SharedPreferences, value: OverlayPreferences.MinimizedFlashlightSideContent) =
        minimizedRightSide.set(prefs, value)

    fun isShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences): Boolean = showExpandedOutline.get(prefs)
    fun setShowExpandedFlashlightOutlineEnabled(prefs: SharedPreferences, enabled: Boolean) = showExpandedOutline.set(prefs, enabled)

    fun isFlashlightBoostEnabled(prefs: SharedPreferences): Boolean = boost.get(prefs)
    fun setFlashlightBoostEnabled(prefs: SharedPreferences, enabled: Boolean) = boost.set(prefs, enabled)

    fun getBoostStrength(prefs: SharedPreferences): Int = prefs.getInt(KEY_BOOST_STRENGTH, 1)
    fun setBoostStrength(prefs: SharedPreferences, strength: Int) {
        prefs.edit { putInt(KEY_BOOST_STRENGTH, strength) }
    }
}
