package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.notification.NotificationActivityState
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
    private const val KEY_SHOW_SONG_ANNOUNCEMENT = "show_song_announcement"
    private const val KEY_TAP_TO_EXPAND = "tap_to_expand"
    private const val KEY_BUBBLE_STYLE = "bubble_style"
    private const val KEY_SMALLER_BUBBLES = "smaller_bubbles"
    private const val KEY_ALBUM_ART_STYLE = "album_art_style"
    private const val KEY_MINIMIZED_ALBUM_ART_STYLE = "minimized_album_art_style"
    private const val KEY_EXPANDED_ALBUM_ART_STYLE = "expanded_album_art_style"
    private const val KEY_SHOW_EXPANDED_ALBUM_ART = "show_expanded_album_art"
    private const val KEY_EXPANDED_PLAYER_LAYOUT = "expanded_player_layout"
    private const val KEY_SHOW_PROGRESS_SQUIGGLES = "show_progress_squiggles"
    private const val KEY_SHOW_MINIMIZED_VISUALIZER = "show_minimized_visualizer"
    private const val KEY_SHOW_EXPANDED_TRACK_INFO = "show_expanded_track_info"
    private const val KEY_SHOW_EXPANDED_PROGRESS = "show_expanded_progress"
    private const val KEY_SHOW_EXPANDED_MAIN_CONTROLS = "show_expanded_main_controls"
    private const val KEY_SHOW_EXPANDED_APP_ACTIONS = "show_expanded_app_actions"
    private const val KEY_SHOW_EXPANDED_VISUALIZER = "show_expanded_visualizer"
    private const val KEY_NESTED_ALBUM_ART_SHAPE = "nested_album_art_shape"
    private const val KEY_MINIMIZED_ALBUM_ART_SHAPE = "minimized_album_art_shape"
    private const val KEY_EXPANDED_ALBUM_ART_SHAPE = "expanded_album_art_shape"
    private const val KEY_SHOW_PROGRESS_OUTLINE = "show_progress_outline"
    private const val KEY_SHOW_MINIMIZED_PROGRESS_OUTLINE = "show_minimized_progress_outline"
    private const val KEY_SHOW_EXPANDED_PROGRESS_OUTLINE = "show_expanded_progress_outline"
    private const val KEY_MINIMIZED_ALBUM_ART_ROTATION = "minimized_album_art_rotation"
    private const val KEY_EXPANDED_ALBUM_ART_ROTATION = "expanded_album_art_rotation"
    private const val KEY_SHOW_DOMINANT_COLOR_GLOW = "show_dominant_color_glow"
    private const val KEY_SHOW_MINIMIZED_DOMINANT_COLOR_GLOW = "show_minimized_dominant_color_glow"
    private const val KEY_SHOW_EXPANDED_DOMINANT_COLOR_GLOW = "show_expanded_dominant_color_glow"
    private const val KEY_CAMERA_COVER_STYLE = "camera_cover_style"
    private const val KEY_SHOW_CAMERA_SWOOP = "show_camera_swoop"
    private const val KEY_SHOW_FLASHLIGHT_ISLAND = "show_flashlight_island"
    private const val KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE = "show_minimized_flashlight_outline"
    private const val KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE = "show_expanded_flashlight_outline"
    private const val KEY_FLASHLIGHT_TAP_TO_TOGGLE = "flashlight_tap_to_toggle"
    private const val KEY_USE_PIXELLIGHT = "use_pixellight"
    private const val KEY_PIXELLIGHT_MAX_STRENGTH = "pixellight_max_strength"
    private const val KEY_PIXELLIGHT_SAVED_STRENGTH = "pixellight_saved_strength"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_SHOW_DEBUG_DOT = "show_debug_dot"
    private const val KEY_HIDE_WHEN_SCREEN_OFF = "hide_when_screen_off"
    private const val KEY_HIDE_ON_LOCK_SCREEN = "hide_on_lock_screen"
    private const val KEY_HIDE_MUSIC_WHEN_APP_OPEN = "hide_music_when_app_open"
    private const val KEY_APP_BLACKLIST_ENABLED = "app_blacklist_enabled"
    private const val KEY_APP_BLACKLIST = "app_blacklist"
    private const val KEY_SHOW_DOT_RIGHT_SIDE_INFO = "show_dot_right_side_info"
    private const val KEY_NOTIFICATION_ACTIVITIES_ENABLED = "notification_activities_enabled"
    private const val KEY_NOTIFICATION_PROGRESS_ENABLED = "notification_progress_enabled"
    private const val KEY_NOTIFICATION_NAVIGATION_ENABLED = "notification_navigation_enabled"
    private const val KEY_NOTIFICATION_CALLS_ENABLED = "notification_calls_enabled"
    private const val KEY_NOTIFICATION_TIMERS_ENABLED = "notification_timers_enabled"
    private const val KEY_WAVEFORM_BAND_COUNT = "waveform_band_count"
    const val DEFAULT_WAVEFORM_BAND_COUNT = 5
    private const val KEY_WAVEFORM_BAR_WIDTH = "waveform_bar_width"
    const val DEFAULT_WAVEFORM_BAR_WIDTH = 2.2f   // dp
    private const val KEY_WAVEFORM_BAR_SPACING = "waveform_bar_spacing"
    const val DEFAULT_WAVEFORM_BAR_SPACING = 2.0f // dp
    private const val KEY_HQ_VISUALIZER_ENABLED = "hq_visualizer_enabled"
    const val KEY_VISUALIZER_MODE = "visualizer_mode"
    const val KEY_PREVIEW_AUDIO_SOURCE = "preview_audio_source"

    enum class CameraCoverStyle(val label: String, val description: String) {
        NONE("None", "Leave the camera cutout uncovered"),
        SWOOP("Swoop", "A wide, curved fade from the top edge"),
        GRADIENT("Gradient", "Black at the camera, fading smoothly to transparent at the bottom of the island"),
        CANOPY("Canopy", "A broad curved cover with a long, soft fade below the camera"),
    }

    enum class BubbleStyle(val label: String) {
        IPHONE("iPhone"),
        MATERIAL_3("Material 3"),
    }

    enum class AlbumArtStyle(val label: String) {
        BASIC_FADED("Basic Faded"),
        BLENDED("Blended"),
        NESTED("Nested"),
        FULL_BACKGROUND("Full Background"),
    }

    enum class ExpandedBackgroundStyle(val label: String) {
        NONE("None"),
        BASIC_FADED("Basic Faded"),
        BLENDED("Blended"),
        FULL_BACKGROUND("Full Background"),
        BLURRED_FULL_BACKGROUND("Blurred Full"),
    }

    enum class ExpandedPlayerLayout(val label: String) {
        ANDROID_MEDIA_CONTROLS("Android Media Controls"),
        MATERIAL_3_EXPRESSIVE("Material 3 Expressive"),
        IPHONE("iPhone"),
    }

    data class ExpandedElementVisibility(
        val trackInfo: Boolean = true,
        val progress: Boolean = true,
        val mainControls: Boolean = true,
        val appActions: Boolean = true,
        val visualizer: Boolean = true,
    )

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

    private val _tapToExpandFlow = MutableStateFlow(false)
    val tapToExpandFlow: StateFlow<Boolean> = _tapToExpandFlow.asStateFlow()

    private val _bubbleStyleFlow = MutableStateFlow(BubbleStyle.IPHONE)
    val bubbleStyleFlow: StateFlow<BubbleStyle> = _bubbleStyleFlow.asStateFlow()

    private val _smallerBubblesFlow = MutableStateFlow(false)
    val smallerBubblesFlow: StateFlow<Boolean> = _smallerBubblesFlow.asStateFlow()

    private val _showMinimizedTitleFlow = MutableStateFlow(false)
    val showMinimizedTitleFlow: StateFlow<Boolean> = _showMinimizedTitleFlow.asStateFlow()

    private val _showSongAnnouncementFlow = MutableStateFlow(true)
    val showSongAnnouncementFlow: StateFlow<Boolean> = _showSongAnnouncementFlow.asStateFlow()

    private val _minimizedAlbumArtStyleFlow = MutableStateFlow(AlbumArtStyle.BLENDED)
    val minimizedAlbumArtStyleFlow: StateFlow<AlbumArtStyle> = _minimizedAlbumArtStyleFlow.asStateFlow()

    private val _expandedAlbumArtStyleFlow = MutableStateFlow(ExpandedBackgroundStyle.BLENDED)
    val expandedAlbumArtStyleFlow: StateFlow<ExpandedBackgroundStyle> = _expandedAlbumArtStyleFlow.asStateFlow()

    private val _showExpandedAlbumArtFlow = MutableStateFlow(true)
    val showExpandedAlbumArtFlow: StateFlow<Boolean> = _showExpandedAlbumArtFlow.asStateFlow()

    private val _expandedPlayerLayoutFlow = MutableStateFlow(ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS)
    val expandedPlayerLayoutFlow: StateFlow<ExpandedPlayerLayout> = _expandedPlayerLayoutFlow.asStateFlow()

    private val _showProgressSquigglesFlow = MutableStateFlow(true)
    val showProgressSquigglesFlow: StateFlow<Boolean> = _showProgressSquigglesFlow.asStateFlow()

    private val _showMinimizedVisualizerFlow = MutableStateFlow(true)
    val showMinimizedVisualizerFlow: StateFlow<Boolean> = _showMinimizedVisualizerFlow.asStateFlow()

    private val _expandedElementVisibilityFlow = MutableStateFlow(ExpandedElementVisibility())
    val expandedElementVisibilityFlow: StateFlow<ExpandedElementVisibility> = _expandedElementVisibilityFlow.asStateFlow()

    private val _minimizedAlbumArtShapeFlow = MutableStateFlow(NestedAlbumArtShape.ROUNDED_SQUARE)
    val minimizedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = _minimizedAlbumArtShapeFlow.asStateFlow()

    private val _expandedAlbumArtShapeFlow = MutableStateFlow(NestedAlbumArtShape.ROUNDED_SQUARE)
    val expandedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = _expandedAlbumArtShapeFlow.asStateFlow()

    private val _showMinimizedProgressOutlineFlow = MutableStateFlow(true)
    val showMinimizedProgressOutlineFlow: StateFlow<Boolean> = _showMinimizedProgressOutlineFlow.asStateFlow()
    val showProgressOutlineFlow: StateFlow<Boolean> get() = _showMinimizedProgressOutlineFlow.asStateFlow()

    private val _showExpandedProgressOutlineFlow = MutableStateFlow(true)
    val showExpandedProgressOutlineFlow: StateFlow<Boolean> = _showExpandedProgressOutlineFlow.asStateFlow()

    private val _minimizedAlbumArtRotationFlow = MutableStateFlow(0f)
    val minimizedAlbumArtRotationFlow: StateFlow<Float> = _minimizedAlbumArtRotationFlow.asStateFlow()

    private val _expandedAlbumArtRotationFlow = MutableStateFlow(0f)
    val expandedAlbumArtRotationFlow: StateFlow<Float> = _expandedAlbumArtRotationFlow.asStateFlow()

    private val _showMinimizedDominantColorGlowFlow = MutableStateFlow(true)
    val showMinimizedDominantColorGlowFlow: StateFlow<Boolean> = _showMinimizedDominantColorGlowFlow.asStateFlow()

    private val _showExpandedDominantColorGlowFlow = MutableStateFlow(true)
    val showExpandedDominantColorGlowFlow: StateFlow<Boolean> = _showExpandedDominantColorGlowFlow.asStateFlow()

    private val _cameraCoverStyleFlow = MutableStateFlow(CameraCoverStyle.SWOOP)
    val cameraCoverStyleFlow: StateFlow<CameraCoverStyle> = _cameraCoverStyleFlow.asStateFlow()

    private val _showFlashlightIslandFlow = MutableStateFlow(true)
    private val _showDotRightSideInfoFlow = MutableStateFlow(false)
    val showDotRightSideInfoFlow: StateFlow<Boolean> = _showDotRightSideInfoFlow.asStateFlow()

    val showFlashlightIslandFlow: StateFlow<Boolean> = _showFlashlightIslandFlow.asStateFlow()

    private val _showMinimizedFlashlightOutlineFlow = MutableStateFlow(true)
    val showMinimizedFlashlightOutlineFlow: StateFlow<Boolean> = _showMinimizedFlashlightOutlineFlow.asStateFlow()
    private val _showExpandedFlashlightOutlineFlow = MutableStateFlow(true)
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> = _showExpandedFlashlightOutlineFlow.asStateFlow()

    private val _flashlightTapToToggleFlow = MutableStateFlow(true)
    val flashlightTapToToggleFlow: StateFlow<Boolean> = _flashlightTapToToggleFlow.asStateFlow()

    private val _usePixelLightFlow = MutableStateFlow(true)
    val usePixelLightFlow: StateFlow<Boolean> = _usePixelLightFlow.asStateFlow()

    private val _isDebugModeFlow = MutableStateFlow(false)
    val isDebugModeFlow: StateFlow<Boolean> = _isDebugModeFlow.asStateFlow()
    val showDebugDotFlow: StateFlow<Boolean> = _isDebugModeFlow.asStateFlow()

    private val _hideWhenScreenOffFlow = MutableStateFlow(true)
    val hideWhenScreenOffFlow: StateFlow<Boolean> = _hideWhenScreenOffFlow.asStateFlow()

    private val _hideOnLockScreenFlow = MutableStateFlow(false)
    val hideOnLockScreenFlow: StateFlow<Boolean> = _hideOnLockScreenFlow.asStateFlow()

    private val _hideMusicWhenAppOpenFlow = MutableStateFlow(true)
    val hideMusicWhenAppOpenFlow: StateFlow<Boolean> = _hideMusicWhenAppOpenFlow.asStateFlow()

    private val _appBlacklistEnabledFlow = MutableStateFlow(true)
    val appBlacklistEnabledFlow: StateFlow<Boolean> = _appBlacklistEnabledFlow.asStateFlow()

    private val _blacklistedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedPackagesFlow: StateFlow<Set<String>> = _blacklistedPackagesFlow.asStateFlow()

    private val _notificationActivitySettingsFlow = MutableStateFlow(NotificationActivitySettings())
    val notificationActivitySettingsFlow: StateFlow<NotificationActivitySettings> =
        _notificationActivitySettingsFlow.asStateFlow()

    private val _waveformBandCountFlow = MutableStateFlow(DEFAULT_WAVEFORM_BAND_COUNT)
    val waveformBandCountFlow: StateFlow<Int> = _waveformBandCountFlow.asStateFlow()

    private val _waveformBarWidthFlow = MutableStateFlow(DEFAULT_WAVEFORM_BAR_WIDTH)
    val waveformBarWidthFlow: StateFlow<Float> = _waveformBarWidthFlow.asStateFlow()

    private val _waveformBarSpacingFlow = MutableStateFlow(DEFAULT_WAVEFORM_BAR_SPACING)
    val waveformBarSpacingFlow: StateFlow<Float> = _waveformBarSpacingFlow.asStateFlow()

    private val _hqVisualizerEnabledFlow = MutableStateFlow(false)
    val hqVisualizerEnabledFlow: StateFlow<Boolean> = _hqVisualizerEnabledFlow.asStateFlow()

    private val _visualizerModeFlow = MutableStateFlow(VisualizerMode.SIMULATED)
    val visualizerModeFlow: StateFlow<VisualizerMode> = _visualizerModeFlow.asStateFlow()

    private val _previewAudioSourceFlow = MutableStateFlow(PreviewAudioSource.AUTO)
    val previewAudioSourceFlow: StateFlow<PreviewAudioSource> = _previewAudioSourceFlow.asStateFlow()

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
    private var isSongAnnouncementInitialized = false
    private var isTapToExpandInitialized = false
    private var isBubbleStyleInitialized = false
    private var isSmallerBubblesInitialized = false
    private var isMinimizedAlbumArtStyleInitialized = false
    private var isExpandedAlbumArtStyleInitialized = false
    private var isShowExpandedAlbumArtInitialized = false
    private var isExpandedPlayerLayoutInitialized = false
    private var isShowProgressSquigglesInitialized = false
    private var isShowMinimizedVisualizerInitialized = false
    private var isExpandedElementVisibilityInitialized = false
    private var isMinimizedAlbumArtShapeInitialized = false
    private var isExpandedAlbumArtShapeInitialized = false
    private var isShowMinimizedProgressOutlineInitialized = false
    private var isShowExpandedProgressOutlineInitialized = false
    private var isMinimizedAlbumArtRotationInitialized = false
    private var isExpandedAlbumArtRotationInitialized = false
    private var isShowMinimizedDominantColorGlowInitialized = false
    private var isShowExpandedDominantColorGlowInitialized = false
    private var isCameraCoverStyleInitialized = false
    private var isShowFlashlightIslandInitialized = false
    private var isShowMinimizedFlashlightOutlineInitialized = false
    private var isShowExpandedFlashlightOutlineInitialized = false
    private var isShowDotRightSideInfoInitialized = false
    private var isFlashlightTapToToggleInitialized = false
    private var isUsePixelLightInitialized = false
    private var isDebugModeInitialized = false
    private var isHideWhenScreenOffInitialized = false
    private var isHideOnLockScreenInitialized = false
    private var isHideMusicWhenAppOpenInitialized = false
    private var isNotificationActivitySettingsInitialized = false
    private var isWaveformBandCountInitialized = false
    private var isWaveformBarWidthInitialized = false
    private var isWaveformBarSpacingInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initializeMusicSettings(context: Context) {
        isShowMinimizedProgressOutlineEnabled(context)
        isShowExpandedProgressOutlineEnabled(context)
        isHideMusicWhenAppOpenEnabled(context)
        isShowMinimizedTitleEnabled(context)
        isShowSongAnnouncementEnabled(context)
        getMinimizedAlbumArtStyle(context)
        getExpandedAlbumArtStyle(context)
        isShowExpandedAlbumArtEnabled(context)
        getExpandedPlayerLayout(context)
        isShowProgressSquigglesEnabled(context)
        isShowMinimizedVisualizerEnabled(context)
        getExpandedElementVisibility(context)
        getMinimizedAlbumArtShape(context)
        getExpandedAlbumArtShape(context)
        getMinimizedAlbumArtRotation(context)
        getExpandedAlbumArtRotation(context)
        isShowMinimizedDominantColorGlowEnabled(context)
        isShowExpandedDominantColorGlowEnabled(context)
        getCameraCoverStyle(context)
        getWaveformBandCount(context)
        getWaveformBarWidth(context)
        getWaveformBarSpacing(context)
        isHqVisualizerEnabled(context)
        getVisualizerMode(context)
        getPreviewAudioSource(context)
    }

    private fun parseAlbumArtStyle(styleName: String?): AlbumArtStyle {
        return when (styleName) {
            "BASIC_FADED" -> AlbumArtStyle.BASIC_FADED
            "NESTED", "NESTED_ROUNDED_SQUARE" -> AlbumArtStyle.NESTED
            "FULL_BACKGROUND" -> AlbumArtStyle.FULL_BACKGROUND
            else -> AlbumArtStyle.BLENDED
        }
    }

    private fun parseExpandedBackgroundStyle(styleName: String?): ExpandedBackgroundStyle {
        return when (styleName) {
            "NONE", "NESTED", "NESTED_ROUNDED_SQUARE" -> ExpandedBackgroundStyle.NONE
            "BASIC_FADED" -> ExpandedBackgroundStyle.BASIC_FADED
            "FULL_BACKGROUND" -> ExpandedBackgroundStyle.FULL_BACKGROUND
            "BLURRED_FULL_BACKGROUND" -> ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND
            else -> ExpandedBackgroundStyle.BLENDED
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

    fun isTapToExpandEnabled(context: Context): Boolean {
        if (!isTapToExpandInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_TAP_TO_EXPAND, false)
            _tapToExpandFlow.value = enabled
            isTapToExpandInitialized = true
        }
        return _tapToExpandFlow.value
    }

    fun getBubbleStyle(context: Context): BubbleStyle {
        if (!isBubbleStyleInitialized) {
            val saved = getPrefs(context).getString(KEY_BUBBLE_STYLE, null)
            _bubbleStyleFlow.value = BubbleStyle.entries.firstOrNull { it.name == saved } ?: BubbleStyle.IPHONE
            isBubbleStyleInitialized = true
        }
        return _bubbleStyleFlow.value
    }

    fun setBubbleStyle(context: Context, style: BubbleStyle) {
        getPrefs(context).edit().putString(KEY_BUBBLE_STYLE, style.name).apply()
        _bubbleStyleFlow.value = style
        isBubbleStyleInitialized = true
    }

    fun areSmallerBubblesEnabled(context: Context): Boolean {
        if (!isSmallerBubblesInitialized) {
            _smallerBubblesFlow.value = getPrefs(context).getBoolean(KEY_SMALLER_BUBBLES, false)
            isSmallerBubblesInitialized = true
        }
        return _smallerBubblesFlow.value
    }

    fun setSmallerBubblesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SMALLER_BUBBLES, enabled).apply()
        _smallerBubblesFlow.value = enabled
        isSmallerBubblesInitialized = true
    }

    fun setTapToExpandEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TAP_TO_EXPAND, enabled).apply()
        _tapToExpandFlow.value = enabled
        isTapToExpandInitialized = true
    }

    fun isShowMinimizedTitleEnabled(context: Context): Boolean {
        if (!isTitlePrefInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_MINIMIZED_TITLE, false)
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

    fun isShowSongAnnouncementEnabled(context: Context): Boolean {
        if (!isSongAnnouncementInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_SONG_ANNOUNCEMENT, true)
            _showSongAnnouncementFlow.value = enabled
            isSongAnnouncementInitialized = true
        }
        return _showSongAnnouncementFlow.value
    }

    fun setShowSongAnnouncementEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_SONG_ANNOUNCEMENT, enabled).apply()
        _showSongAnnouncementFlow.value = enabled
        isSongAnnouncementInitialized = true
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

    fun getExpandedAlbumArtStyle(context: Context): ExpandedBackgroundStyle {
        if (!isExpandedAlbumArtStyleInitialized) {
            val prefs = getPrefs(context)
            val legacy = prefs.getString(KEY_ALBUM_ART_STYLE, null)
            val styleName = prefs.getString(KEY_EXPANDED_ALBUM_ART_STYLE, legacy ?: ExpandedBackgroundStyle.BLENDED.name)
            val style = parseExpandedBackgroundStyle(styleName)
            _expandedAlbumArtStyleFlow.value = style
            isExpandedAlbumArtStyleInitialized = true
        }
        return _expandedAlbumArtStyleFlow.value
    }

    fun setExpandedAlbumArtStyle(context: Context, style: ExpandedBackgroundStyle) {
        getPrefs(context).edit().putString(KEY_EXPANDED_ALBUM_ART_STYLE, style.name).apply()
        _expandedAlbumArtStyleFlow.value = style
        isExpandedAlbumArtStyleInitialized = true
    }

    fun isShowExpandedAlbumArtEnabled(context: Context): Boolean {
        if (!isShowExpandedAlbumArtInitialized) {
            _showExpandedAlbumArtFlow.value = getPrefs(context).getBoolean(KEY_SHOW_EXPANDED_ALBUM_ART, true)
            isShowExpandedAlbumArtInitialized = true
        }
        return _showExpandedAlbumArtFlow.value
    }

    fun setShowExpandedAlbumArtEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EXPANDED_ALBUM_ART, enabled).apply()
        _showExpandedAlbumArtFlow.value = enabled
        isShowExpandedAlbumArtInitialized = true
    }

    fun getExpandedPlayerLayout(context: Context): ExpandedPlayerLayout {
        if (!isExpandedPlayerLayoutInitialized) {
            val stored = getPrefs(context).getString(
                KEY_EXPANDED_PLAYER_LAYOUT,
                ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS.name,
            )
            _expandedPlayerLayoutFlow.value = runCatching {
                ExpandedPlayerLayout.valueOf(stored.orEmpty())
            }.getOrDefault(ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS)
            isExpandedPlayerLayoutInitialized = true
        }
        return _expandedPlayerLayoutFlow.value
    }

    fun setExpandedPlayerLayout(context: Context, layout: ExpandedPlayerLayout) {
        getPrefs(context).edit().putString(KEY_EXPANDED_PLAYER_LAYOUT, layout.name).apply()
        _expandedPlayerLayoutFlow.value = layout
        isExpandedPlayerLayoutInitialized = true
    }

    fun isShowProgressSquigglesEnabled(context: Context): Boolean {
        if (!isShowProgressSquigglesInitialized) {
            _showProgressSquigglesFlow.value = getPrefs(context).getBoolean(KEY_SHOW_PROGRESS_SQUIGGLES, true)
            isShowProgressSquigglesInitialized = true
        }
        return _showProgressSquigglesFlow.value
    }

    fun setShowProgressSquigglesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_PROGRESS_SQUIGGLES, enabled).apply()
        _showProgressSquigglesFlow.value = enabled
        isShowProgressSquigglesInitialized = true
    }

    fun isShowMinimizedVisualizerEnabled(context: Context): Boolean {
        if (!isShowMinimizedVisualizerInitialized) {
            _showMinimizedVisualizerFlow.value = getPrefs(context).getBoolean(KEY_SHOW_MINIMIZED_VISUALIZER, true)
            isShowMinimizedVisualizerInitialized = true
        }
        return _showMinimizedVisualizerFlow.value
    }

    fun setShowMinimizedVisualizerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MINIMIZED_VISUALIZER, enabled).apply()
        _showMinimizedVisualizerFlow.value = enabled
        isShowMinimizedVisualizerInitialized = true
    }

    fun getExpandedElementVisibility(context: Context): ExpandedElementVisibility {
        if (!isExpandedElementVisibilityInitialized) {
            val prefs = getPrefs(context)
            _expandedElementVisibilityFlow.value = ExpandedElementVisibility(
                trackInfo = prefs.getBoolean(KEY_SHOW_EXPANDED_TRACK_INFO, true),
                progress = prefs.getBoolean(KEY_SHOW_EXPANDED_PROGRESS, true),
                mainControls = prefs.getBoolean(KEY_SHOW_EXPANDED_MAIN_CONTROLS, true),
                appActions = prefs.getBoolean(KEY_SHOW_EXPANDED_APP_ACTIONS, true),
                visualizer = prefs.getBoolean(KEY_SHOW_EXPANDED_VISUALIZER, true),
            )
            isExpandedElementVisibilityInitialized = true
        }
        return _expandedElementVisibilityFlow.value
    }

    fun setExpandedElementVisibility(context: Context, visibility: ExpandedElementVisibility) {
        getPrefs(context).edit()
            .putBoolean(KEY_SHOW_EXPANDED_TRACK_INFO, visibility.trackInfo)
            .putBoolean(KEY_SHOW_EXPANDED_PROGRESS, visibility.progress)
            .putBoolean(KEY_SHOW_EXPANDED_MAIN_CONTROLS, visibility.mainControls)
            .putBoolean(KEY_SHOW_EXPANDED_APP_ACTIONS, visibility.appActions)
            .putBoolean(KEY_SHOW_EXPANDED_VISUALIZER, visibility.visualizer)
            .apply()
        _expandedElementVisibilityFlow.value = visibility
        isExpandedElementVisibilityInitialized = true
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

    fun isShowMinimizedProgressOutlineEnabled(context: Context): Boolean {
        if (!isShowMinimizedProgressOutlineInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(
                KEY_SHOW_MINIMIZED_PROGRESS_OUTLINE,
                prefs.getBoolean(KEY_SHOW_PROGRESS_OUTLINE, true),
            )
            _showMinimizedProgressOutlineFlow.value = enabled
            isShowMinimizedProgressOutlineInitialized = true
        }
        return _showMinimizedProgressOutlineFlow.value
    }

    fun setShowMinimizedProgressOutlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MINIMIZED_PROGRESS_OUTLINE, enabled).apply()
        _showMinimizedProgressOutlineFlow.value = enabled
        isShowMinimizedProgressOutlineInitialized = true
    }

    fun isShowExpandedProgressOutlineEnabled(context: Context): Boolean {
        if (!isShowExpandedProgressOutlineInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(
                KEY_SHOW_EXPANDED_PROGRESS_OUTLINE,
                prefs.getBoolean(KEY_SHOW_PROGRESS_OUTLINE, true),
            )
            _showExpandedProgressOutlineFlow.value = enabled
            isShowExpandedProgressOutlineInitialized = true
        }
        return _showExpandedProgressOutlineFlow.value
    }

    fun setShowExpandedProgressOutlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EXPANDED_PROGRESS_OUTLINE, enabled).apply()
        _showExpandedProgressOutlineFlow.value = enabled
        isShowExpandedProgressOutlineInitialized = true
    }

    fun getMinimizedAlbumArtRotation(context: Context): Float {
        if (!isMinimizedAlbumArtRotationInitialized) {
            val rotation = getPrefs(context).getFloat(KEY_MINIMIZED_ALBUM_ART_ROTATION, 0f)
            _minimizedAlbumArtRotationFlow.value = rotation
            isMinimizedAlbumArtRotationInitialized = true
        }
        return _minimizedAlbumArtRotationFlow.value
    }

    fun setMinimizedAlbumArtRotation(context: Context, degrees: Float) {
        getPrefs(context).edit().putFloat(KEY_MINIMIZED_ALBUM_ART_ROTATION, degrees).apply()
        _minimizedAlbumArtRotationFlow.value = degrees
        isMinimizedAlbumArtRotationInitialized = true
    }

    fun getExpandedAlbumArtRotation(context: Context): Float {
        if (!isExpandedAlbumArtRotationInitialized) {
            val rotation = getPrefs(context).getFloat(KEY_EXPANDED_ALBUM_ART_ROTATION, 0f)
            _expandedAlbumArtRotationFlow.value = rotation
            isExpandedAlbumArtRotationInitialized = true
        }
        return _expandedAlbumArtRotationFlow.value
    }

    fun setExpandedAlbumArtRotation(context: Context, degrees: Float) {
        getPrefs(context).edit().putFloat(KEY_EXPANDED_ALBUM_ART_ROTATION, degrees).apply()
        _expandedAlbumArtRotationFlow.value = degrees
        isExpandedAlbumArtRotationInitialized = true
    }

    fun isShowMinimizedDominantColorGlowEnabled(context: Context): Boolean {
        if (!isShowMinimizedDominantColorGlowInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(
                KEY_SHOW_MINIMIZED_DOMINANT_COLOR_GLOW,
                prefs.getBoolean(KEY_SHOW_DOMINANT_COLOR_GLOW, true),
            )
            _showMinimizedDominantColorGlowFlow.value = enabled
            isShowMinimizedDominantColorGlowInitialized = true
        }
        return _showMinimizedDominantColorGlowFlow.value
    }

    fun setShowMinimizedDominantColorGlowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MINIMIZED_DOMINANT_COLOR_GLOW, enabled).apply()
        _showMinimizedDominantColorGlowFlow.value = enabled
        isShowMinimizedDominantColorGlowInitialized = true
    }

    fun isShowExpandedDominantColorGlowEnabled(context: Context): Boolean {
        if (!isShowExpandedDominantColorGlowInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(
                KEY_SHOW_EXPANDED_DOMINANT_COLOR_GLOW,
                prefs.getBoolean(KEY_SHOW_DOMINANT_COLOR_GLOW, true),
            )
            _showExpandedDominantColorGlowFlow.value = enabled
            isShowExpandedDominantColorGlowInitialized = true
        }
        return _showExpandedDominantColorGlowFlow.value
    }

    fun setShowExpandedDominantColorGlowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EXPANDED_DOMINANT_COLOR_GLOW, enabled).apply()
        _showExpandedDominantColorGlowFlow.value = enabled
        isShowExpandedDominantColorGlowInitialized = true
    }

    fun getCameraCoverStyle(context: Context): CameraCoverStyle {
        if (!isCameraCoverStyleInitialized) {
            val prefs = getPrefs(context)
            val fallback = if (prefs.getBoolean(KEY_SHOW_CAMERA_SWOOP, true)) {
                CameraCoverStyle.SWOOP
            } else CameraCoverStyle.NONE
            _cameraCoverStyleFlow.value = prefs.getString(KEY_CAMERA_COVER_STYLE, null)?.let { saved ->
                when (saved) {
                    "HALO", "CURTAIN" -> CameraCoverStyle.GRADIENT
                    "CAPSULE" -> CameraCoverStyle.CANOPY
                    else -> CameraCoverStyle.entries.firstOrNull { it.name == saved }
                }
            } ?: fallback
            isCameraCoverStyleInitialized = true
        }
        return _cameraCoverStyleFlow.value
    }

    fun setCameraCoverStyle(context: Context, style: CameraCoverStyle) {
        getPrefs(context).edit().putString(KEY_CAMERA_COVER_STYLE, style.name).apply()
        _cameraCoverStyleFlow.value = style
        isCameraCoverStyleInitialized = true
    }

    fun isShowDotRightSideInfoEnabled(context: Context): Boolean {
        if (!isShowDotRightSideInfoInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_DOT_RIGHT_SIDE_INFO, false)
            _showDotRightSideInfoFlow.value = enabled
            isShowDotRightSideInfoInitialized = true
        }
        return _showDotRightSideInfoFlow.value
    }

    fun setShowDotRightSideInfoEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_DOT_RIGHT_SIDE_INFO, enabled).apply()
        _showDotRightSideInfoFlow.value = enabled
        isShowDotRightSideInfoInitialized = true
    }

    fun isShowFlashlightIslandEnabled(context: Context): Boolean {
        if (!isShowFlashlightIslandInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_SHOW_FLASHLIGHT_ISLAND, true)
            _showFlashlightIslandFlow.value = enabled
            isShowFlashlightIslandInitialized = true
        }
        return _showFlashlightIslandFlow.value
    }

    fun setShowFlashlightIslandEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_FLASHLIGHT_ISLAND, enabled).apply()
        _showFlashlightIslandFlow.value = enabled
        isShowFlashlightIslandInitialized = true
    }

    fun isShowMinimizedFlashlightOutlineEnabled(context: Context): Boolean {
        if (!isShowMinimizedFlashlightOutlineInitialized) {
            _showMinimizedFlashlightOutlineFlow.value = getPrefs(context).getBoolean(KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE, true)
            isShowMinimizedFlashlightOutlineInitialized = true
        }
        return _showMinimizedFlashlightOutlineFlow.value
    }

    fun setShowMinimizedFlashlightOutlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_MINIMIZED_FLASHLIGHT_OUTLINE, enabled).apply()
        _showMinimizedFlashlightOutlineFlow.value = enabled
        isShowMinimizedFlashlightOutlineInitialized = true
    }

    fun isShowExpandedFlashlightOutlineEnabled(context: Context): Boolean {
        if (!isShowExpandedFlashlightOutlineInitialized) {
            _showExpandedFlashlightOutlineFlow.value = getPrefs(context).getBoolean(KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE, true)
            isShowExpandedFlashlightOutlineInitialized = true
        }
        return _showExpandedFlashlightOutlineFlow.value
    }

    fun setShowExpandedFlashlightOutlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_EXPANDED_FLASHLIGHT_OUTLINE, enabled).apply()
        _showExpandedFlashlightOutlineFlow.value = enabled
        isShowExpandedFlashlightOutlineInitialized = true
    }

    fun isFlashlightTapToToggleEnabled(context: Context): Boolean {
        if (!isFlashlightTapToToggleInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_FLASHLIGHT_TAP_TO_TOGGLE, true)
            _flashlightTapToToggleFlow.value = enabled
            isFlashlightTapToToggleInitialized = true
        }
        return _flashlightTapToToggleFlow.value
    }

    fun setFlashlightTapToToggleEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FLASHLIGHT_TAP_TO_TOGGLE, enabled).apply()
        _flashlightTapToToggleFlow.value = enabled
        isFlashlightTapToToggleInitialized = true
    }

    fun isUsePixelLightEnabled(context: Context): Boolean {
        if (!isUsePixelLightInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_USE_PIXELLIGHT, true)
            _usePixelLightFlow.value = enabled
            isUsePixelLightInitialized = true
        }
        return _usePixelLightFlow.value
    }

    fun setUsePixelLightEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_PIXELLIGHT, enabled).apply()
        _usePixelLightFlow.value = enabled
        isUsePixelLightInitialized = true
    }

    fun getLastKnownPixelLightMax(context: Context?): Int {
        if (context == null) return 127
        val saved = getPrefs(context).getInt(KEY_PIXELLIGHT_MAX_STRENGTH, 127)
        return if (saved <= 36) 127 else saved
    }

    fun setLastKnownPixelLightMax(context: Context?, max: Int) {
        if (context == null || max <= 1) return
        getPrefs(context).edit().putInt(KEY_PIXELLIGHT_MAX_STRENGTH, max).apply()
    }

    fun getLastKnownPixelLightStrength(context: Context?): Int {
        if (context == null) return 127
        val saved = getPrefs(context).getInt(KEY_PIXELLIGHT_SAVED_STRENGTH, 127)
        return if (saved <= 36) 127 else saved
    }

    fun setLastKnownPixelLightStrength(context: Context?, strength: Int) {
        if (context == null || strength <= 0) return
        getPrefs(context).edit().putInt(KEY_PIXELLIGHT_SAVED_STRENGTH, strength).apply()
    }

    fun isDebugModeEnabled(context: Context): Boolean {
        if (!isDebugModeInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(KEY_DEBUG_MODE, prefs.getBoolean(KEY_SHOW_DEBUG_DOT, false))
            _isDebugModeFlow.value = enabled
            isDebugModeInitialized = true
        }
        return _isDebugModeFlow.value
    }

    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_DEBUG_MODE, enabled)
            .putBoolean(KEY_SHOW_DEBUG_DOT, enabled)
            .apply()
        _isDebugModeFlow.value = enabled
        isDebugModeInitialized = true
    }

    fun isShowDebugDotEnabled(context: Context): Boolean = isDebugModeEnabled(context)
    fun setShowDebugDotEnabled(context: Context, enabled: Boolean) = setDebugModeEnabled(context, enabled)

    fun isHideWhenScreenOffEnabled(context: Context): Boolean {
        if (!isHideWhenScreenOffInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_HIDE_WHEN_SCREEN_OFF, true)
            _hideWhenScreenOffFlow.value = enabled
            isHideWhenScreenOffInitialized = true
        }
        return _hideWhenScreenOffFlow.value
    }

    fun setHideWhenScreenOffEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_WHEN_SCREEN_OFF, enabled).apply()
        _hideWhenScreenOffFlow.value = enabled
        isHideWhenScreenOffInitialized = true
    }

    fun isHideOnLockScreenEnabled(context: Context): Boolean {
        if (!isHideOnLockScreenInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_HIDE_ON_LOCK_SCREEN, false)
            _hideOnLockScreenFlow.value = enabled
            isHideOnLockScreenInitialized = true
        }
        return _hideOnLockScreenFlow.value
    }

    fun setHideOnLockScreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_ON_LOCK_SCREEN, enabled).apply()
        _hideOnLockScreenFlow.value = enabled
        isHideOnLockScreenInitialized = true
    }

    fun isHideMusicWhenAppOpenEnabled(context: Context): Boolean {
        if (!isHideMusicWhenAppOpenInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_HIDE_MUSIC_WHEN_APP_OPEN, true)
            _hideMusicWhenAppOpenFlow.value = enabled
            isHideMusicWhenAppOpenInitialized = true
        }
        return _hideMusicWhenAppOpenFlow.value
    }

    fun setHideMusicWhenAppOpenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_MUSIC_WHEN_APP_OPEN, enabled).apply()
        _hideMusicWhenAppOpenFlow.value = enabled
        isHideMusicWhenAppOpenInitialized = true
    }

    fun getNotificationActivitySettings(context: Context): NotificationActivitySettings {
        if (!isNotificationActivitySettingsInitialized) {
            val prefs = getPrefs(context)
            _notificationActivitySettingsFlow.value = NotificationActivitySettings(
                enabled = prefs.getBoolean(KEY_NOTIFICATION_ACTIVITIES_ENABLED, true),
                progress = prefs.getBoolean(KEY_NOTIFICATION_PROGRESS_ENABLED, true),
                navigation = prefs.getBoolean(KEY_NOTIFICATION_NAVIGATION_ENABLED, true),
                calls = prefs.getBoolean(KEY_NOTIFICATION_CALLS_ENABLED, true),
                timers = prefs.getBoolean(KEY_NOTIFICATION_TIMERS_ENABLED, true),
            )
            isNotificationActivitySettingsInitialized = true
        }
        return _notificationActivitySettingsFlow.value.also(NotificationActivityState::configure)
    }

    fun setNotificationActivitySettings(
        context: Context,
        settings: NotificationActivitySettings,
    ) {
        getPrefs(context).edit()
            .putBoolean(KEY_NOTIFICATION_ACTIVITIES_ENABLED, settings.enabled)
            .putBoolean(KEY_NOTIFICATION_PROGRESS_ENABLED, settings.progress)
            .putBoolean(KEY_NOTIFICATION_NAVIGATION_ENABLED, settings.navigation)
            .putBoolean(KEY_NOTIFICATION_CALLS_ENABLED, settings.calls)
            .putBoolean(KEY_NOTIFICATION_TIMERS_ENABLED, settings.timers)
            .apply()
        _notificationActivitySettingsFlow.value = settings
        isNotificationActivitySettingsInitialized = true
        NotificationActivityState.configure(settings)
    }

    fun getWaveformBandCount(context: Context): Int {
        if (!isWaveformBandCountInitialized) {
            val count = getPrefs(context).getInt(KEY_WAVEFORM_BAND_COUNT, DEFAULT_WAVEFORM_BAND_COUNT)
            _waveformBandCountFlow.value = count
            isWaveformBandCountInitialized = true
        }
        return _waveformBandCountFlow.value
    }

    fun setWaveformBandCount(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_WAVEFORM_BAND_COUNT, count).apply()
        _waveformBandCountFlow.value = count
        isWaveformBandCountInitialized = true
    }

    fun getWaveformBarWidth(context: Context): Float {
        if (!isWaveformBarWidthInitialized) {
            val w = getPrefs(context).getFloat(KEY_WAVEFORM_BAR_WIDTH, DEFAULT_WAVEFORM_BAR_WIDTH)
            _waveformBarWidthFlow.value = w
            isWaveformBarWidthInitialized = true
        }
        return _waveformBarWidthFlow.value
    }

    fun setWaveformBarWidth(context: Context, widthDp: Float) {
        getPrefs(context).edit().putFloat(KEY_WAVEFORM_BAR_WIDTH, widthDp).apply()
        _waveformBarWidthFlow.value = widthDp
        isWaveformBarWidthInitialized = true
    }

    fun getWaveformBarSpacing(context: Context): Float {
        if (!isWaveformBarSpacingInitialized) {
            val s = getPrefs(context).getFloat(KEY_WAVEFORM_BAR_SPACING, DEFAULT_WAVEFORM_BAR_SPACING)
            _waveformBarSpacingFlow.value = s
            isWaveformBarSpacingInitialized = true
        }
        return _waveformBarSpacingFlow.value
    }

    fun setWaveformBarSpacing(context: Context, spacingDp: Float) {
        getPrefs(context).edit().putFloat(KEY_WAVEFORM_BAR_SPACING, spacingDp).apply()
        _waveformBarSpacingFlow.value = spacingDp
        isWaveformBarSpacingInitialized = true
    }

    enum class VisualizerMode(val key: String, val title: String, val description: String) {
        SIMULATED("simulated", "Simulated", "Smooth flowing wave animation"),
        BPM("bpm", "BPM Beat Sync", "Rhythmic pulse locked to track tempo (zero CPU impact)"),
        DEVICE_AUDIO("device_audio", "Device Audio", "Live internal audio captured directly from phone"),
        AUDIO_PREVIEW("audio_preview", "Preview Audio", "Decodes 30s audio offline to extract frequency bands");

        companion object {
            fun fromKey(key: String?): VisualizerMode {
                return entries.firstOrNull { it.key == key } ?: SIMULATED
            }
        }
    }

    private var isVisualizerModeInitialized = false

    fun getVisualizerMode(context: Context): VisualizerMode {
        if (!isVisualizerModeInitialized) {
            val prefs = getPrefs(context)
            val modeStr = prefs.getString(KEY_VISUALIZER_MODE, null)
            val mode = if (modeStr != null) {
                VisualizerMode.fromKey(modeStr)
            } else if (prefs.getBoolean(KEY_HQ_VISUALIZER_ENABLED, false)) {
                VisualizerMode.AUDIO_PREVIEW
            } else {
                VisualizerMode.SIMULATED
            }
            _visualizerModeFlow.value = mode
            _hqVisualizerEnabledFlow.value = (mode == VisualizerMode.AUDIO_PREVIEW)
            isVisualizerModeInitialized = true
        }
        return _visualizerModeFlow.value
    }

    fun setVisualizerMode(context: Context, mode: VisualizerMode) {
        getPrefs(context).edit()
            .putString(KEY_VISUALIZER_MODE, mode.key)
            .putBoolean(KEY_HQ_VISUALIZER_ENABLED, mode == VisualizerMode.AUDIO_PREVIEW)
            .apply()
        _visualizerModeFlow.value = mode
        _hqVisualizerEnabledFlow.value = (mode == VisualizerMode.AUDIO_PREVIEW)
        isVisualizerModeInitialized = true
    }

    fun isHqVisualizerEnabled(context: Context): Boolean {
        return getVisualizerMode(context) == VisualizerMode.AUDIO_PREVIEW
    }

    fun setHqVisualizerEnabled(context: Context, enabled: Boolean) {
        setVisualizerMode(context, if (enabled) VisualizerMode.AUDIO_PREVIEW else VisualizerMode.SIMULATED)
    }

    enum class PreviewAudioSource(val key: String, val title: String, val subtitle: String) {
        AUTO("auto", "Auto", "Tries Deezer first, falls back to iTunes"),
        DEEZER("deezer", "Deezer", "Queries Deezer 30s preview catalog"),
        ITUNES("itunes", "iTunes", "Queries iTunes Search API"),
        DEVICE("device", "Device (Live)", "Captures internal audio directly from phone");

        companion object {
            fun fromKey(key: String?): PreviewAudioSource {
                return entries.firstOrNull { it.key == key } ?: AUTO
            }
        }
    }

    private var isPreviewAudioSourceInitialized = false

    fun getPreviewAudioSource(context: Context): PreviewAudioSource {
        if (!isPreviewAudioSourceInitialized) {
            val key = getPrefs(context).getString(KEY_PREVIEW_AUDIO_SOURCE, null)
            _previewAudioSourceFlow.value = PreviewAudioSource.fromKey(key)
            isPreviewAudioSourceInitialized = true
        }
        return _previewAudioSourceFlow.value
    }

    fun setPreviewAudioSource(context: Context, source: PreviewAudioSource) {
        getPrefs(context).edit().putString(KEY_PREVIEW_AUDIO_SOURCE, source.key).apply()
        _previewAudioSourceFlow.value = source
        isPreviewAudioSourceInitialized = true
    }

    private var isAppBlacklistInitialized = false

    private fun ensureAppBlacklistInitialized(context: Context) {
        if (!isAppBlacklistInitialized) {
            val prefs = getPrefs(context)
            _appBlacklistEnabledFlow.value = prefs.getBoolean(KEY_APP_BLACKLIST_ENABLED, true)
            val saved = prefs.getStringSet(KEY_APP_BLACKLIST, emptySet())?.toSet() ?: emptySet()
            _blacklistedPackagesFlow.value = saved
            isAppBlacklistInitialized = true
        }
    }

    fun isAppBlacklistEnabled(context: Context): Boolean {
        ensureAppBlacklistInitialized(context)
        return _appBlacklistEnabledFlow.value
    }

    fun setAppBlacklistEnabled(context: Context, enabled: Boolean) {
        ensureAppBlacklistInitialized(context)
        getPrefs(context).edit().putBoolean(KEY_APP_BLACKLIST_ENABLED, enabled).apply()
        _appBlacklistEnabledFlow.value = enabled
    }

    fun getBlacklistedPackages(context: Context): Set<String> {
        ensureAppBlacklistInitialized(context)
        return _blacklistedPackagesFlow.value
    }

    fun setBlacklistedPackages(context: Context, packages: Set<String>) {
        ensureAppBlacklistInitialized(context)
        val filtered = packages.filter { it.isNotBlank() && it != context.packageName }.toSet()
        getPrefs(context).edit().putStringSet(KEY_APP_BLACKLIST, HashSet(filtered)).apply()
        _blacklistedPackagesFlow.value = filtered
    }

    fun addBlacklistedPackage(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        ensureAppBlacklistInitialized(context)
        setBlacklistedPackages(context, _blacklistedPackagesFlow.value + packageName)
    }

    fun removeBlacklistedPackage(context: Context, packageName: String) {
        ensureAppBlacklistInitialized(context)
        setBlacklistedPackages(context, _blacklistedPackagesFlow.value - packageName)
    }

    fun toggleBlacklistedPackage(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        ensureAppBlacklistInitialized(context)
        if (_blacklistedPackagesFlow.value.contains(packageName)) {
            removeBlacklistedPackage(context, packageName)
        } else {
            addBlacklistedPackage(context, packageName)
        }
    }

    fun clearBlacklistedPackages(context: Context) {
        setBlacklistedPackages(context, emptySet())
    }
}
