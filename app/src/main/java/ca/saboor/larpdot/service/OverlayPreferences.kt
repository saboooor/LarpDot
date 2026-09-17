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
    private const val KEY_MINIMIZED_ALBUM_ART_STYLE = "minimized_album_art_style"
    private const val KEY_EXPANDED_ALBUM_ART_STYLE = "expanded_album_art_style"
    private const val KEY_NESTED_ALBUM_ART_SHAPE = "nested_album_art_shape"
    private const val KEY_MINIMIZED_ALBUM_ART_SHAPE = "minimized_album_art_shape"
    private const val KEY_EXPANDED_ALBUM_ART_SHAPE = "expanded_album_art_shape"
    private const val KEY_SHOW_PROGRESS_OUTLINE = "show_progress_outline"

    enum class AlbumArtStyle(val label: String) {
        BASIC_FADED("Basic Faded"),
        BLENDED("Blended"),
        NESTED("Nested"),
        FULL_BACKGROUND("Full Background"),
    }

    enum class NestedAlbumArtShape(val label: String) {
        ROUNDED_SQUARE("Square"),
        CIRCLE("Circle"),
        SLANTED("Slanted"),
        ARCH("Arch"),
        FAN("Fan"),
        ARROW("Arrow"),
        SEMI_CIRCLE("Semi Circle"),
        OVAL("Oval"),
        PILL("Pill"),
        TRIANGLE("Triangle"),
        DIAMOND("Diamond"),
        CLAM_SHELL("Clam Shell"),
        PENTAGON("Pentagon"),
        GEM("Gem"),
        SUNNY("Sunny"),
        VERY_SUNNY("Very Sunny"),
        COOKIE("Cookie 4"),
        COOKIE_6("Cookie 6"),
        COOKIE_7("Cookie 7"),
        COOKIE_9("Cookie 9"),
        COOKIE_12("Cookie 12"),
        GHOSTISH("Ghost"),
        CLOVER("Clover 4"),
        CLOVER_8("Clover 8"),
        BURST("Burst"),
        SOFT_BURST("Soft Burst"),
        BOOM("Boom"),
        SOFT_BOOM("Soft Boom"),
        FLOWER("Flower"),
        PUFFY("Puffy"),
        PUFFY_DIAMOND("Puffy Dia."),
        PIXEL_CIRCLE("Pixel Circle"),
        PIXEL_TRIANGLE("Pixel Tri."),
        BUN("Bun"),
        HEART("Heart");

        companion object {
            fun fromName(name: String?): NestedAlbumArtShape {
                if (name == null) return ROUNDED_SQUARE
                return when (name) {
                    "COOKIE_4" -> COOKIE
                    "CLOVER_4" -> CLOVER
                    else -> try {
                        valueOf(name)
                    } catch (_: Exception) {
                        ROUNDED_SQUARE
                    }
                }
            }
        }
    }

    private val _isEnabledFlow = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    private val _showMinimizedTitleFlow = MutableStateFlow(true)
    val showMinimizedTitleFlow: StateFlow<Boolean> = _showMinimizedTitleFlow.asStateFlow()

    private val _tapToExpandFlow = MutableStateFlow(false)
    val tapToExpandFlow: StateFlow<Boolean> = _tapToExpandFlow.asStateFlow()

    private val _minimizedAlbumArtStyleFlow = MutableStateFlow(AlbumArtStyle.BLENDED)
    val minimizedAlbumArtStyleFlow: StateFlow<AlbumArtStyle> = _minimizedAlbumArtStyleFlow.asStateFlow()

    private val _expandedAlbumArtStyleFlow = MutableStateFlow(AlbumArtStyle.BLENDED)
    val expandedAlbumArtStyleFlow: StateFlow<AlbumArtStyle> = _expandedAlbumArtStyleFlow.asStateFlow()

    private val _minimizedAlbumArtShapeFlow = MutableStateFlow(NestedAlbumArtShape.ROUNDED_SQUARE)
    val minimizedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = _minimizedAlbumArtShapeFlow.asStateFlow()

    private val _expandedAlbumArtShapeFlow = MutableStateFlow(NestedAlbumArtShape.ROUNDED_SQUARE)
    val expandedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = _expandedAlbumArtShapeFlow.asStateFlow()

    private val _showProgressOutlineFlow = MutableStateFlow(true)
    val showProgressOutlineFlow: StateFlow<Boolean> = _showProgressOutlineFlow.asStateFlow()

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
    private var isMinimizedAlbumArtStyleInitialized = false
    private var isExpandedAlbumArtStyleInitialized = false
    private var isMinimizedAlbumArtShapeInitialized = false
    private var isExpandedAlbumArtShapeInitialized = false
    private var isShowProgressOutlineInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun parseAlbumArtStyle(styleName: String?): AlbumArtStyle {
        return when (styleName) {
            "BASIC_FADED" -> AlbumArtStyle.BASIC_FADED
            "NESTED", "NESTED_ROUNDED_SQUARE" -> AlbumArtStyle.NESTED
            "FULL_BACKGROUND" -> AlbumArtStyle.FULL_BACKGROUND
            else -> AlbumArtStyle.BLENDED
        }
    }

    private fun parseNestedAlbumArtShape(shapeName: String?): NestedAlbumArtShape {
        return NestedAlbumArtShape.fromName(shapeName)
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
            val manual = prefs.getBoolean(KEY_MANUAL_CUTOUT_ENABLED, false)
            val offX = prefs.getFloat(KEY_CUTOUT_OFFSET_X, 0f)
            val offY = prefs.getFloat(KEY_CUTOUT_OFFSET_Y, 0f)
            val diam = prefs.getFloat(KEY_CUTOUT_DIAMETER, 0f)
            _cutoutConfigFlow.value = CutoutConfig(manual, offX, offY, diam)
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

    fun getMinimizedAlbumArtStyle(context: Context): AlbumArtStyle {
        if (!isMinimizedAlbumArtStyleInitialized) {
            val prefs = getPrefs(context)
            val legacy = prefs.getString(KEY_ALBUM_ART_STYLE, null)
            val styleName = prefs.getString(KEY_MINIMIZED_ALBUM_ART_STYLE, legacy ?: AlbumArtStyle.BLENDED.name)
            val style = parseAlbumArtStyle(styleName)
            _minimizedAlbumArtStyleFlow.value = style
            isMinimizedAlbumArtStyleInitialized = true
        }
        return _minimizedAlbumArtStyleFlow.value
    }

    fun setMinimizedAlbumArtStyle(context: Context, style: AlbumArtStyle) {
        getPrefs(context).edit().putString(KEY_MINIMIZED_ALBUM_ART_STYLE, style.name).apply()
        _minimizedAlbumArtStyleFlow.value = style
        isMinimizedAlbumArtStyleInitialized = true
    }

    fun getExpandedAlbumArtStyle(context: Context): AlbumArtStyle {
        if (!isExpandedAlbumArtStyleInitialized) {
            val prefs = getPrefs(context)
            val legacy = prefs.getString(KEY_ALBUM_ART_STYLE, null)
            val styleName = prefs.getString(KEY_EXPANDED_ALBUM_ART_STYLE, legacy ?: AlbumArtStyle.BLENDED.name)
            val style = parseAlbumArtStyle(styleName)
            _expandedAlbumArtStyleFlow.value = style
            isExpandedAlbumArtStyleInitialized = true
        }
        return _expandedAlbumArtStyleFlow.value
    }

    fun setExpandedAlbumArtStyle(context: Context, style: AlbumArtStyle) {
        getPrefs(context).edit().putString(KEY_EXPANDED_ALBUM_ART_STYLE, style.name).apply()
        _expandedAlbumArtStyleFlow.value = style
        isExpandedAlbumArtStyleInitialized = true
    }

    fun getMinimizedAlbumArtShape(context: Context): NestedAlbumArtShape {
        if (!isMinimizedAlbumArtShapeInitialized) {
            val prefs = getPrefs(context)
            val legacy = prefs.getString(KEY_NESTED_ALBUM_ART_SHAPE, null)
            val shapeName = prefs.getString(KEY_MINIMIZED_ALBUM_ART_SHAPE, legacy ?: NestedAlbumArtShape.ROUNDED_SQUARE.name)
            val shape = parseNestedAlbumArtShape(shapeName)
            _minimizedAlbumArtShapeFlow.value = shape
            isMinimizedAlbumArtShapeInitialized = true
        }
        return _minimizedAlbumArtShapeFlow.value
    }

    fun setMinimizedAlbumArtShape(context: Context, shape: NestedAlbumArtShape) {
        getPrefs(context).edit().putString(KEY_MINIMIZED_ALBUM_ART_SHAPE, shape.name).apply()
        _minimizedAlbumArtShapeFlow.value = shape
        isMinimizedAlbumArtShapeInitialized = true
    }

    fun getExpandedAlbumArtShape(context: Context): NestedAlbumArtShape {
        if (!isExpandedAlbumArtShapeInitialized) {
            val prefs = getPrefs(context)
            val legacy = prefs.getString(KEY_NESTED_ALBUM_ART_SHAPE, null)
            val shapeName = prefs.getString(KEY_EXPANDED_ALBUM_ART_SHAPE, legacy ?: NestedAlbumArtShape.ROUNDED_SQUARE.name)
            val shape = parseNestedAlbumArtShape(shapeName)
            _expandedAlbumArtShapeFlow.value = shape
            isExpandedAlbumArtShapeInitialized = true
        }
        return _expandedAlbumArtShapeFlow.value
    }

    fun setExpandedAlbumArtShape(context: Context, shape: NestedAlbumArtShape) {
        getPrefs(context).edit().putString(KEY_EXPANDED_ALBUM_ART_SHAPE, shape.name).apply()
        _expandedAlbumArtShapeFlow.value = shape
        isExpandedAlbumArtShapeInitialized = true
    }

    fun isShowProgressOutlineEnabled(context: Context): Boolean {
        if (!isShowProgressOutlineInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_PROGRESS_OUTLINE, true)
            _showProgressOutlineFlow.value = enabled
            isShowProgressOutlineInitialized = true
        }
        return _showProgressOutlineFlow.value
    }

    fun setShowProgressOutlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_PROGRESS_OUTLINE, enabled).apply()
        _showProgressOutlineFlow.value = enabled
        isShowProgressOutlineInitialized = true
    }
}
