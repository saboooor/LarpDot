package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayPreferences {
    private const val PREFS_NAME = "larpdot_overlay_prefs"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_MANUAL_CUTOUT_ENABLED = "manual_cutout_enabled"
    private const val KEY_CUTOUT_OFFSET_X = "cutout_offset_x"
    private const val KEY_CUTOUT_OFFSET_Y = "cutout_offset_y"
    private const val KEY_CUTOUT_DIAMETER = "cutout_diameter"
    private const val KEY_SHOW_MINIMIZED_TITLE = "show_minimized_title"
    private const val KEY_TAP_TO_EXPAND = "tap_to_expand"
    private const val KEY_ALBUM_ART_STYLE = "album_art_style"

    enum class AlbumArtStyle(val label: String) {
        BASIC_FADED("Basic Faded"),
        BLENDED("Blended"),
        NESTED_ROUNDED_SQUARE("Nested Square"),
    }

    private val _isEnabledFlow = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    private val _showMinimizedTitleFlow = MutableStateFlow(true)
    val showMinimizedTitleFlow: StateFlow<Boolean> = _showMinimizedTitleFlow.asStateFlow()

    private val _tapToExpandFlow = MutableStateFlow(false)
    val tapToExpandFlow: StateFlow<Boolean> = _tapToExpandFlow.asStateFlow()

    private val _albumArtStyleFlow = MutableStateFlow(AlbumArtStyle.BLENDED)
    val albumArtStyleFlow: StateFlow<AlbumArtStyle> = _albumArtStyleFlow.asStateFlow()

    data class CutoutConfig(
        val isManualEnabled: Boolean = false,
        val offsetX: Float = 0f, // in dp
        val offsetY: Float = 0f, // in dp
        val customDiameterDp: Float = 0f, // in dp (0f = auto-detect)
    )

    private val _cutoutConfigFlow = MutableStateFlow(CutoutConfig())
    val cutoutConfigFlow: StateFlow<CutoutConfig> = _cutoutConfigFlow.asStateFlow()

    private var isInitialized = false
    private var isCutoutInitialized = false
    private var isTitlePrefInitialized = false
    private var isTapToExpandInitialized = false
    private var isAlbumArtStyleInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOverlayEnabled(context: Context): Boolean {
        if (!isInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_OVERLAY_ENABLED, false)
            _isEnabledFlow.value = enabled
            isInitialized = true
        }
        return _isEnabledFlow.value
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _isEnabledFlow.value = enabled
        isInitialized = true
    }

    fun getCutoutConfig(context: Context): CutoutConfig {
        if (!isCutoutInitialized) {
            val prefs = getPrefs(context)
            val isManual = prefs.getBoolean(KEY_MANUAL_CUTOUT_ENABLED, false)
            val offX = prefs.getFloat(KEY_CUTOUT_OFFSET_X, 0f)
            val offY = prefs.getFloat(KEY_CUTOUT_OFFSET_Y, 0f)
            val diameter = prefs.getFloat(KEY_CUTOUT_DIAMETER, 0f)
            val config = CutoutConfig(
                isManualEnabled = isManual,
                offsetX = offX,
                offsetY = offY,
                customDiameterDp = diameter,
            )
            _cutoutConfigFlow.value = config
            isCutoutInitialized = true
        }
        return _cutoutConfigFlow.value
    }

    fun setCutoutConfig(context: Context, config: CutoutConfig) {
        getPrefs(context).edit()
            .putBoolean(KEY_MANUAL_CUTOUT_ENABLED, config.isManualEnabled)
            .putFloat(KEY_CUTOUT_OFFSET_X, config.offsetX)
            .putFloat(KEY_CUTOUT_OFFSET_Y, config.offsetY)
            .putFloat(KEY_CUTOUT_DIAMETER, config.customDiameterDp)
            .apply()
        _cutoutConfigFlow.value = config
        isCutoutInitialized = true
    }

    fun isShowMinimizedTitleEnabled(context: Context): Boolean {
        if (!isTitlePrefInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_MINIMIZED_TITLE, true)
            _showMinimizedTitleFlow.value = enabled
            isTitlePrefInitialized = true
        }
        return _showMinimizedTitleFlow.value
    }

    fun setShowMinimizedTitleEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MINIMIZED_TITLE, enabled).apply()
        _showMinimizedTitleFlow.value = enabled
        isTitlePrefInitialized = true
    }

    fun isTapToExpandEnabled(context: Context): Boolean {
        if (!isTapToExpandInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_TAP_TO_EXPAND, false)
            _tapToExpandFlow.value = enabled
            isTapToExpandInitialized = true
        }
        return _tapToExpandFlow.value
    }

    fun setTapToExpandEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TAP_TO_EXPAND, enabled).apply()
        _tapToExpandFlow.value = enabled
        isTapToExpandInitialized = true
    }

    fun getAlbumArtStyle(context: Context): AlbumArtStyle {
        if (!isAlbumArtStyleInitialized) {
            val styleName = getPrefs(context).getString(KEY_ALBUM_ART_STYLE, AlbumArtStyle.BLENDED.name)
            val style = try {
                AlbumArtStyle.valueOf(styleName ?: AlbumArtStyle.BLENDED.name)
            } catch (_: Exception) {
                AlbumArtStyle.BLENDED
            }
            _albumArtStyleFlow.value = style
            isAlbumArtStyleInitialized = true
        }
        return _albumArtStyleFlow.value
    }

    fun setAlbumArtStyle(context: Context, style: AlbumArtStyle) {
        getPrefs(context).edit().putString(KEY_ALBUM_ART_STYLE, style.name).apply()
        _albumArtStyleFlow.value = style
        isAlbumArtStyleInitialized = true
    }
}
