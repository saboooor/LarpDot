package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
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
    private const val KEY_MUSIC_PAUSE_TIMEOUT_SECONDS = "music_pause_timeout_seconds"
    const val DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS = 5
    private const val KEY_TAP_TO_EXPAND = "tap_to_expand"
    private const val KEY_HOLD_TO_EXPAND_DURATION_MS = "hold_to_expand_duration_ms"
    const val DEFAULT_HOLD_TO_EXPAND_DURATION_MS = 300
    const val MIN_HOLD_TO_EXPAND_DURATION_MS = 100
    const val MAX_HOLD_TO_EXPAND_DURATION_MS = 1000
    private const val KEY_BUBBLE_STYLE = "bubble_style"
    private const val KEY_SMALLER_BUBBLES = "smaller_bubbles"
    private const val KEY_ALBUM_ART_STYLE = "album_art_style"
    private const val KEY_MINIMIZED_ALBUM_ART_STYLE = "minimized_album_art_style"
    private const val KEY_EXPANDED_ALBUM_ART_STYLE = "expanded_album_art_style"
    private const val KEY_SHOW_EXPANDED_ALBUM_ART = "show_expanded_album_art"
    private const val KEY_ALBUM_ART_GLOW = "album_art_glow"
    private const val KEY_EXPANDED_PLAYER_LAYOUT = "expanded_player_layout"
    private const val KEY_SHOW_PROGRESS_SQUIGGLES = "show_progress_squiggles"
    private const val KEY_PROGRESS_BAR_ACCENT_COLOR = "progress_bar_accent_color"
    private const val KEY_PLAYBACK_PLAY_PAUSE_BG = "playback_play_pause_bg"
    private const val KEY_PLAYBACK_PLAY_PAUSE_ACCENT = "playback_play_pause_accent"
    private const val KEY_PLAYBACK_SKIP_BG = "playback_skip_bg"
    private const val KEY_PLAYBACK_CORNER_RADIUS = "playback_corner_radius"
    private const val KEY_PLAYBACK_M3_BUTTON_GROUP = "playback_m3_button_group"
    private const val KEY_TRACK_INFO_SHOW_TITLE = "track_info_show_title"
    private const val KEY_TRACK_INFO_SHOW_ARTIST = "track_info_show_artist"
    private const val KEY_TRACK_INFO_SHOW_ALBUM = "track_info_show_album"
    private const val KEY_TRACK_INFO_TITLE_ACCENT = "track_info_title_accent"
    private const val KEY_SHOW_MINIMIZED_VISUALIZER = "show_minimized_visualizer"
    private const val KEY_SHOW_EXPANDED_TRACK_INFO = "show_expanded_track_info"
    private const val KEY_SHOW_EXPANDED_PROGRESS = "show_expanded_progress"
    private const val KEY_SHOW_EXPANDED_MAIN_CONTROLS = "show_expanded_main_controls"
    private const val KEY_SHOW_EXPANDED_APP_ACTIONS = "show_expanded_app_actions"
    private const val KEY_SHOW_EXPANDED_VISUALIZER = "show_expanded_visualizer"
    private const val KEY_SHOW_EXPANDED_BPM = "show_expanded_bpm"
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
    private const val KEY_EXPANDED_GLOW_LEFT = "expanded_glow_left"
    private const val KEY_EXPANDED_GLOW_RIGHT = "expanded_glow_right"
    private const val KEY_EXPANDED_GLOW_BOTTOM = "expanded_glow_bottom"
    private const val KEY_EXPANDED_GLOW_TOP = "expanded_glow_top"
    private const val KEY_BPM_PULSE_ENABLED = "bpm_pulse_enabled"
    private const val KEY_MINIMIZED_BPM_PULSE_ENABLED = "minimized_bpm_pulse_enabled"
    private const val KEY_EXPANDED_BPM_PULSE_ENABLED = "expanded_bpm_pulse_enabled"
    private const val KEY_PULSE_ENTIRE_ISLAND_ENABLED = "pulse_entire_island_enabled"
    private const val KEY_PULSE_AMOUNT = "pulse_amount"
    const val DEFAULT_PULSE_AMOUNT = 1.0f
    private const val KEY_ISLAND_FONT_STYLE = "island_font_style"
    private const val KEY_MINIMIZED_NESTED_BLURRED_BG = "minimized_nested_blurred_bg"
    private const val KEY_HIDE_IN_FULLSCREEN = "hide_in_fullscreen"
    private const val KEY_HIDE_IN_LANDSCAPE = "hide_in_landscape"

    enum class IslandFontStyle(val label: String) {
        DEFAULT("Default"),
        SERIF("Serif"),
        MONOSPACE("Mono"),
        ROUNDED("Rounded"),
        CONDENSED("Condensed"),
        CURSIVE("Cursive"),
        CASUAL("Casual");

        val fontFamily: FontFamily
            get() = when (this) {
                DEFAULT -> FontFamily.Default
                SERIF -> FontFamily.Serif
                MONOSPACE -> FontFamily.Monospace
                ROUNDED -> FontFamily(createTypeface(islandFontWeightFlow.value))
                CONDENSED -> FontFamily(createTypeface(islandFontWeightFlow.value))
                CURSIVE -> FontFamily.Cursive
                CASUAL -> FontFamily(createTypeface(islandFontWeightFlow.value))
            }

        fun createTypeface(weight: Int = 400, italic: Boolean = false): Typeface {
            return try {
                val base = when (this) {
                    DEFAULT -> Typeface.DEFAULT
                    SERIF -> Typeface.SERIF
                    MONOSPACE -> Typeface.MONOSPACE
                    ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
                    CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
                    CASUAL -> Typeface.create("casual", Typeface.NORMAL)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(base, weight, italic)
                } else {
                    Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }
        }
    }
    private const val KEY_CAMERA_COVER_STYLE = "camera_cover_style"
    private const val KEY_SHOW_CAMERA_SWOOP = "show_camera_swoop"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_SHOW_DEBUG_DOT = "show_debug_dot"
    private const val KEY_HIDE_WHEN_SCREEN_OFF = "hide_when_screen_off"
    private const val KEY_HIDE_ON_LOCK_SCREEN = "hide_on_lock_screen"
    private const val KEY_HIDE_MUSIC_WHEN_APP_OPEN = "hide_music_when_app_open"
    private const val KEY_SHOW_DOT_RIGHT_SIDE_INFO = "show_dot_right_side_info"
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
        val bpm: Boolean = true,
    )

    data class ExpandedGlowSides(
        val left: Boolean = false,
        val right: Boolean = true,
        val bottom: Boolean = true,
        val top: Boolean = false,
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

    private val _holdToExpandDurationMsFlow = MutableStateFlow(DEFAULT_HOLD_TO_EXPAND_DURATION_MS)
    val holdToExpandDurationMsFlow: StateFlow<Int> = _holdToExpandDurationMsFlow.asStateFlow()

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

    private val _albumArtGlowFlow = MutableStateFlow(false)
    val albumArtGlowFlow: StateFlow<Boolean> = _albumArtGlowFlow.asStateFlow()

    private val _expandedPlayerLayoutFlow = MutableStateFlow(ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS)
    val expandedPlayerLayoutFlow: StateFlow<ExpandedPlayerLayout> = _expandedPlayerLayoutFlow.asStateFlow()

    data class PlaybackControlsStyle(
        val playPauseBackground: Boolean = true,
        val playPauseAccentColor: Boolean = false,
        val skipBackground: Boolean = false,
        val cornerRadiusDp: Float = 20f,
        val material3ButtonGroup: Boolean = false,
    )

    data class TrackInfoStyle(
        val showTitle: Boolean = true,
        val showArtist: Boolean = true,
        val showAlbum: Boolean = false,
        val titleAccentColor: Boolean = false,
    )

    private val _showProgressSquigglesFlow = MutableStateFlow(true)
    val showProgressSquigglesFlow: StateFlow<Boolean> = _showProgressSquigglesFlow.asStateFlow()

    private val _progressBarAccentColorFlow = MutableStateFlow(true)
    val progressBarAccentColorFlow: StateFlow<Boolean> = _progressBarAccentColorFlow.asStateFlow()

    private val _playbackControlsStyleFlow = MutableStateFlow(PlaybackControlsStyle())
    val playbackControlsStyleFlow: StateFlow<PlaybackControlsStyle> = _playbackControlsStyleFlow.asStateFlow()

    private val _trackInfoStyleFlow = MutableStateFlow(TrackInfoStyle())
    val trackInfoStyleFlow: StateFlow<TrackInfoStyle> = _trackInfoStyleFlow.asStateFlow()

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

    private val _expandedGlowSidesFlow = MutableStateFlow(ExpandedGlowSides())
    val expandedGlowSidesFlow: StateFlow<ExpandedGlowSides> = _expandedGlowSidesFlow.asStateFlow()

    private val _bpmPulseEnabledFlow = MutableStateFlow(true)
    val bpmPulseEnabledFlow: StateFlow<Boolean> = _bpmPulseEnabledFlow.asStateFlow()

    private val _minimizedBpmPulseEnabledFlow = MutableStateFlow(true)
    val minimizedBpmPulseEnabledFlow: StateFlow<Boolean> = _minimizedBpmPulseEnabledFlow.asStateFlow()

    private val _expandedBpmPulseEnabledFlow = MutableStateFlow(true)
    val expandedBpmPulseEnabledFlow: StateFlow<Boolean> = _expandedBpmPulseEnabledFlow.asStateFlow()

    private val _pulseEntireIslandEnabledFlow = MutableStateFlow(false)
    val pulseEntireIslandEnabledFlow: StateFlow<Boolean> = _pulseEntireIslandEnabledFlow.asStateFlow()

    private val _cameraCoverStyleFlow = MutableStateFlow(CameraCoverStyle.SWOOP)
    val cameraCoverStyleFlow: StateFlow<CameraCoverStyle> = _cameraCoverStyleFlow.asStateFlow()

    private val _showDotRightSideInfoFlow = MutableStateFlow(false)
    val showDotRightSideInfoFlow: StateFlow<Boolean> = _showDotRightSideInfoFlow.asStateFlow()

    val showFlashlightIslandFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showFlashlightIslandFlow
    val showMinimizedFlashlightOutlineFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showMinimizedFlashlightOutlineFlow
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showExpandedFlashlightOutlineFlow
    val flashlightTapToToggleFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightTapToToggleFlow
    val flashlightBoostFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBoostFlow

    private val _isDebugModeFlow = MutableStateFlow(false)
    val isDebugModeFlow: StateFlow<Boolean> = _isDebugModeFlow.asStateFlow()
    val showDebugDotFlow: StateFlow<Boolean> = _isDebugModeFlow.asStateFlow()

    private val _hideWhenScreenOffFlow = MutableStateFlow(true)
    val hideWhenScreenOffFlow: StateFlow<Boolean> = _hideWhenScreenOffFlow.asStateFlow()

    private val _hideOnLockScreenFlow = MutableStateFlow(false)
    val hideOnLockScreenFlow: StateFlow<Boolean> = _hideOnLockScreenFlow.asStateFlow()

    private val _hideInFullscreenFlow = MutableStateFlow(false)
    val hideInFullscreenFlow: StateFlow<Boolean> = _hideInFullscreenFlow.asStateFlow()

    private val _hideInLandscapeFlow = MutableStateFlow(false)
    val hideInLandscapeFlow: StateFlow<Boolean> = _hideInLandscapeFlow.asStateFlow()

    private val _hideMusicWhenAppOpenFlow = MutableStateFlow(true)
    val hideMusicWhenAppOpenFlow: StateFlow<Boolean> = _hideMusicWhenAppOpenFlow.asStateFlow()

    private val _musicPauseTimeoutSecondsFlow = MutableStateFlow(DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS)
    val musicPauseTimeoutSecondsFlow: StateFlow<Int> = _musicPauseTimeoutSecondsFlow.asStateFlow()
    private var isMusicPauseTimeoutInitialized = false

    val appBlacklistEnabledFlow: StateFlow<Boolean> get() = ActivityAndBlacklistPrefsStore.appBlacklistEnabledFlow
    val blacklistedPackagesFlow: StateFlow<Set<String>> get() = ActivityAndBlacklistPrefsStore.blacklistedPackagesFlow
    val notificationActivitySettingsFlow: StateFlow<NotificationActivitySettings>
        get() = ActivityAndBlacklistPrefsStore.notificationActivitySettingsFlow
    val islandPriorityOrderFlow: StateFlow<List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem>>
        get() = ActivityAndBlacklistPrefsStore.islandPriorityOrderFlow

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

    private val _islandFontStyleFlow = MutableStateFlow(IslandFontStyle.DEFAULT)
    val islandFontStyleFlow: StateFlow<IslandFontStyle> = _islandFontStyleFlow.asStateFlow()

    private val _pulseAmountFlow = MutableStateFlow(DEFAULT_PULSE_AMOUNT)
    val pulseAmountFlow: StateFlow<Float> = _pulseAmountFlow.asStateFlow()

    private val _minimizedNestedBlurredBgFlow = MutableStateFlow(false)
    val minimizedNestedBlurredBgFlow: StateFlow<Boolean> = _minimizedNestedBlurredBgFlow.asStateFlow()

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
    private var isHoldToExpandDurationInitialized = false
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
    private var isExpandedGlowSidesInitialized = false
    private var isBpmPulseEnabledInitialized = false
    private var isPulseEntireIslandInitialized = false
    private var isPulseAmountInitialized = false
    private var isIslandFontStyleInitialized = false
    private var isMinimizedNestedBlurredBgInitialized = false
    private var isCameraCoverStyleInitialized = false
    private var isShowDotRightSideInfoInitialized = false
    private var isDebugModeInitialized = false
    private var isHideWhenScreenOffInitialized = false
    private var isHideOnLockScreenInitialized = false
    private var isHideMusicWhenAppOpenInitialized = false
    private var isWaveformBandCountInitialized = false
    private var isWaveformBarWidthInitialized = false
    private var isWaveformBarSpacingInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _expandedBackgroundOpacity = MutableStateFlow(1f)
    val expandedBackgroundOpacity = _expandedBackgroundOpacity.asStateFlow()
    private val _backdropBlurEnabled = MutableStateFlow(false)
    val backdropBlurEnabled = _backdropBlurEnabled.asStateFlow()
    private val _backdropBlurRadius = MutableStateFlow(40f)
    val backdropBlurRadius = _backdropBlurRadius.asStateFlow()

    private val _minimizedMaxWidthPercent = MutableStateFlow(50f)
    val minimizedMaxWidthPercent = _minimizedMaxWidthPercent.asStateFlow()

    fun setMinimizedMaxWidthPercent(context: Context, value: Float) {
        _minimizedMaxWidthPercent.value = value.coerceIn(40f, 100f)
        getPrefs(context).edit().putFloat("minimized_max_width_percent", _minimizedMaxWidthPercent.value).apply()
    }

    private val _islandCornerRadius = MutableStateFlow(60f)
    val islandCornerRadius = _islandCornerRadius.asStateFlow()
    private val _islandSquircleEnabled = MutableStateFlow(true)
    val islandSquircleEnabled = _islandSquircleEnabled.asStateFlow()

    fun setIslandCornerRadius(context: Context, value: Float) {
        _islandCornerRadius.value = value.coerceIn(0f, 100f)
        getPrefs(context).edit().putFloat("island_corner_radius", _islandCornerRadius.value).apply()
    }

    fun setIslandSquircleEnabled(context: Context, enabled: Boolean) {
        _islandSquircleEnabled.value = enabled
        getPrefs(context).edit().putBoolean("island_squircle_enabled", enabled).apply()
    }

    fun loadIslandAppearance(context: Context) {
        val prefs = getPrefs(context)
        _minimizedMaxWidthPercent.value = prefs.getFloat("minimized_max_width_percent", 50f).coerceIn(40f, 100f)
        _islandCornerRadius.value = prefs.getFloat("island_corner_radius", 60f).coerceIn(0f, 100f)
        _islandSquircleEnabled.value = prefs.getBoolean("island_squircle_enabled", true)
        _expandedBackgroundOpacity.value = prefs.getFloat("expanded_background_opacity", 1f).coerceIn(0f, 1f)
        _backdropBlurEnabled.value = prefs.getBoolean("backdrop_blur_enabled", false)
        _backdropBlurRadius.value = prefs.getFloat("backdrop_blur_radius", 40f).coerceIn(5f, 120f)
        isTapToExpandEnabled(context)
        getHoldToExpandDurationMs(context)
    }

    fun setExpandedBackgroundOpacity(context: Context, value: Float) {
        _expandedBackgroundOpacity.value = value.coerceIn(0f, 1f)
        getPrefs(context).edit().putFloat("expanded_background_opacity", _expandedBackgroundOpacity.value).apply()
    }

    fun setBackdropBlurEnabled(context: Context, enabled: Boolean) {
        _backdropBlurEnabled.value = enabled
        getPrefs(context).edit().putBoolean("backdrop_blur_enabled", enabled).apply()
    }

    fun setBackdropBlurRadius(context: Context, value: Float) {
        _backdropBlurRadius.value = value.coerceIn(5f, 120f)
        getPrefs(context).edit().putFloat("backdrop_blur_radius", _backdropBlurRadius.value).apply()
    }

    fun initializeMusicSettings(context: Context) {
        loadIslandAppearance(context)
        isShowMinimizedProgressOutlineEnabled(context)
        isShowExpandedProgressOutlineEnabled(context)
        isShowMinimizedTitleEnabled(context)
        isShowSongAnnouncementEnabled(context)
        getMusicPauseTimeoutSeconds(context)
        _islandFontWeightFlow.value = getPrefs(context).getInt("island_font_weight", 400).coerceIn(100, 900)
        getFlashlightExpandedStyle(context)
        isFlashlightBeamBlurEnabled(context)
        isFlashlightBeamAccentColorEnabled(context)
        getIslandFontStyle(context)
        getPulseAmount(context)
        isMinimizedNestedBlurredBgEnabled(context)
        getMinimizedAlbumArtStyle(context)
        getExpandedAlbumArtStyle(context)
        isShowExpandedAlbumArtEnabled(context)
        _albumArtGlowFlow.value = getPrefs(context).getBoolean(KEY_ALBUM_ART_GLOW, false)
        getExpandedPlayerLayout(context)
        isShowProgressSquigglesEnabled(context)
        isProgressBarAccentColorEnabled(context)
        getPlaybackControlsStyle(context)
        getTrackInfoStyle(context)
        isShowMinimizedVisualizerEnabled(context)
        getExpandedElementVisibility(context)
        getMinimizedAlbumArtShape(context)
        getExpandedAlbumArtShape(context)
        getMinimizedAlbumArtRotation(context)
        getExpandedAlbumArtRotation(context)
        isShowMinimizedDominantColorGlowEnabled(context)
        isShowExpandedDominantColorGlowEnabled(context)
        getExpandedGlowSides(context)
        isBpmPulseEnabled(context)
        isMinimizedBpmPulseEnabled(context)
        isExpandedBpmPulseEnabled(context)
        isPulseEntireIslandEnabled(context)
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

    fun getHoldToExpandDurationMs(context: Context): Int {
        if (!isHoldToExpandDurationInitialized) {
            val duration = getPrefs(context)
                .getInt(KEY_HOLD_TO_EXPAND_DURATION_MS, DEFAULT_HOLD_TO_EXPAND_DURATION_MS)
                .coerceIn(MIN_HOLD_TO_EXPAND_DURATION_MS, MAX_HOLD_TO_EXPAND_DURATION_MS)
            _holdToExpandDurationMsFlow.value = duration
            isHoldToExpandDurationInitialized = true
        }
        return _holdToExpandDurationMsFlow.value
    }

    fun setHoldToExpandDurationMs(context: Context, durationMs: Int) {
        val clamped = durationMs.coerceIn(MIN_HOLD_TO_EXPAND_DURATION_MS, MAX_HOLD_TO_EXPAND_DURATION_MS)
        getPrefs(context).edit().putInt(KEY_HOLD_TO_EXPAND_DURATION_MS, clamped).apply()
        _holdToExpandDurationMsFlow.value = clamped
        isHoldToExpandDurationInitialized = true
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

    fun setAlbumArtGlowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALBUM_ART_GLOW, enabled).apply()
        _albumArtGlowFlow.value = enabled
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

    private var isProgressBarAccentColorInitialized = false

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

    private var isPlaybackControlsStyleInitialized = false
    private var isTrackInfoStyleInitialized = false

    fun isProgressBarAccentColorEnabled(context: Context): Boolean {
        if (!isProgressBarAccentColorInitialized) {
            _progressBarAccentColorFlow.value = getPrefs(context).getBoolean(KEY_PROGRESS_BAR_ACCENT_COLOR, true)
            isProgressBarAccentColorInitialized = true
        }
        return _progressBarAccentColorFlow.value
    }

    fun setProgressBarAccentColorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PROGRESS_BAR_ACCENT_COLOR, enabled).apply()
        _progressBarAccentColorFlow.value = enabled
        isProgressBarAccentColorInitialized = true
    }

    fun getPlaybackControlsStyle(context: Context): PlaybackControlsStyle {
        if (!isPlaybackControlsStyleInitialized) {
            val prefs = getPrefs(context)
            _playbackControlsStyleFlow.value = PlaybackControlsStyle(
                playPauseBackground = prefs.getBoolean(KEY_PLAYBACK_PLAY_PAUSE_BG, true),
                playPauseAccentColor = prefs.getBoolean(KEY_PLAYBACK_PLAY_PAUSE_ACCENT, false),
                skipBackground = prefs.getBoolean(KEY_PLAYBACK_SKIP_BG, false),
                cornerRadiusDp = prefs.getFloat(KEY_PLAYBACK_CORNER_RADIUS, 20f).coerceIn(0f, 32f),
                material3ButtonGroup = prefs.getBoolean(KEY_PLAYBACK_M3_BUTTON_GROUP, false),
            )
            isPlaybackControlsStyleInitialized = true
        }
        return _playbackControlsStyleFlow.value
    }

    fun setPlaybackControlsStyle(context: Context, style: PlaybackControlsStyle) {
        val clamped = style.copy(cornerRadiusDp = style.cornerRadiusDp.coerceIn(0f, 32f))
        getPrefs(context).edit()
            .putBoolean(KEY_PLAYBACK_PLAY_PAUSE_BG, clamped.playPauseBackground)
            .putBoolean(KEY_PLAYBACK_PLAY_PAUSE_ACCENT, clamped.playPauseAccentColor)
            .putBoolean(KEY_PLAYBACK_SKIP_BG, clamped.skipBackground)
            .putFloat(KEY_PLAYBACK_CORNER_RADIUS, clamped.cornerRadiusDp)
            .putBoolean(KEY_PLAYBACK_M3_BUTTON_GROUP, clamped.material3ButtonGroup)
            .apply()
        _playbackControlsStyleFlow.value = clamped
        isPlaybackControlsStyleInitialized = true
    }

    fun getTrackInfoStyle(context: Context): TrackInfoStyle {
        if (!isTrackInfoStyleInitialized) {
            val prefs = getPrefs(context)
            _trackInfoStyleFlow.value = TrackInfoStyle(
                showTitle = prefs.getBoolean(KEY_TRACK_INFO_SHOW_TITLE, true),
                showArtist = prefs.getBoolean(KEY_TRACK_INFO_SHOW_ARTIST, true),
                showAlbum = prefs.getBoolean(KEY_TRACK_INFO_SHOW_ALBUM, false),
                titleAccentColor = prefs.getBoolean(KEY_TRACK_INFO_TITLE_ACCENT, false),
            )
            isTrackInfoStyleInitialized = true
        }
        return _trackInfoStyleFlow.value
    }

    fun setTrackInfoStyle(context: Context, style: TrackInfoStyle) {
        getPrefs(context).edit()
            .putBoolean(KEY_TRACK_INFO_SHOW_TITLE, style.showTitle)
            .putBoolean(KEY_TRACK_INFO_SHOW_ARTIST, style.showArtist)
            .putBoolean(KEY_TRACK_INFO_SHOW_ALBUM, style.showAlbum)
            .putBoolean(KEY_TRACK_INFO_TITLE_ACCENT, style.titleAccentColor)
            .apply()
        _trackInfoStyleFlow.value = style
        isTrackInfoStyleInitialized = true
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
                bpm = prefs.getBoolean(KEY_SHOW_EXPANDED_BPM, true),
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
            .putBoolean(KEY_SHOW_EXPANDED_BPM, visibility.bpm)
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

    fun getExpandedGlowSides(context: Context): ExpandedGlowSides {
        if (!isExpandedGlowSidesInitialized) {
            val prefs = getPrefs(context)
            val sides = ExpandedGlowSides(
                left = prefs.getBoolean(KEY_EXPANDED_GLOW_LEFT, false),
                right = prefs.getBoolean(KEY_EXPANDED_GLOW_RIGHT, true),
                bottom = prefs.getBoolean(KEY_EXPANDED_GLOW_BOTTOM, true),
                top = prefs.getBoolean(KEY_EXPANDED_GLOW_TOP, false),
            )
            _expandedGlowSidesFlow.value = sides
            isExpandedGlowSidesInitialized = true
        }
        return _expandedGlowSidesFlow.value
    }

    fun setExpandedGlowSides(context: Context, sides: ExpandedGlowSides) {
        getPrefs(context).edit()
            .putBoolean(KEY_EXPANDED_GLOW_LEFT, sides.left)
            .putBoolean(KEY_EXPANDED_GLOW_RIGHT, sides.right)
            .putBoolean(KEY_EXPANDED_GLOW_BOTTOM, sides.bottom)
            .putBoolean(KEY_EXPANDED_GLOW_TOP, sides.top)
            .apply()
        _expandedGlowSidesFlow.value = sides
        isExpandedGlowSidesInitialized = true
    }

    private var isMinimizedBpmPulseInitialized = false
    private var isExpandedBpmPulseInitialized = false

    fun isBpmPulseEnabled(context: Context): Boolean {
        if (!isBpmPulseEnabledInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(KEY_BPM_PULSE_ENABLED, true)
            _bpmPulseEnabledFlow.value = enabled
            isBpmPulseEnabledInitialized = true
        }
        return _bpmPulseEnabledFlow.value
    }

    fun setBpmPulseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_BPM_PULSE_ENABLED, enabled)
            .putBoolean(KEY_MINIMIZED_BPM_PULSE_ENABLED, enabled)
            .putBoolean(KEY_EXPANDED_BPM_PULSE_ENABLED, enabled)
            .apply()
        _bpmPulseEnabledFlow.value = enabled
        _minimizedBpmPulseEnabledFlow.value = enabled
        _expandedBpmPulseEnabledFlow.value = enabled
        isBpmPulseEnabledInitialized = true
        isMinimizedBpmPulseInitialized = true
        isExpandedBpmPulseInitialized = true
    }

    fun isMinimizedBpmPulseEnabled(context: Context): Boolean {
        if (!isMinimizedBpmPulseInitialized) {
            val prefs = getPrefs(context)
            val fallback = prefs.getBoolean(KEY_BPM_PULSE_ENABLED, true)
            _minimizedBpmPulseEnabledFlow.value = prefs.getBoolean(KEY_MINIMIZED_BPM_PULSE_ENABLED, fallback)
            isMinimizedBpmPulseInitialized = true
        }
        return _minimizedBpmPulseEnabledFlow.value
    }

    fun setMinimizedBpmPulseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MINIMIZED_BPM_PULSE_ENABLED, enabled).apply()
        _minimizedBpmPulseEnabledFlow.value = enabled
        isMinimizedBpmPulseInitialized = true
    }

    fun isExpandedBpmPulseEnabled(context: Context): Boolean {
        if (!isExpandedBpmPulseInitialized) {
            val prefs = getPrefs(context)
            val fallback = prefs.getBoolean(KEY_BPM_PULSE_ENABLED, true)
            _expandedBpmPulseEnabledFlow.value = prefs.getBoolean(KEY_EXPANDED_BPM_PULSE_ENABLED, fallback)
            isExpandedBpmPulseInitialized = true
        }
        return _expandedBpmPulseEnabledFlow.value
    }

    fun setExpandedBpmPulseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_EXPANDED_BPM_PULSE_ENABLED, enabled).apply()
        _expandedBpmPulseEnabledFlow.value = enabled
        isExpandedBpmPulseInitialized = true
    }

    fun isPulseEntireIslandEnabled(context: Context): Boolean {
        if (!isPulseEntireIslandInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(KEY_PULSE_ENTIRE_ISLAND_ENABLED, false)
            _pulseEntireIslandEnabledFlow.value = enabled
            isPulseEntireIslandInitialized = true
        }
        return _pulseEntireIslandEnabledFlow.value
    }

    fun setPulseEntireIslandEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PULSE_ENTIRE_ISLAND_ENABLED, enabled).apply()
        _pulseEntireIslandEnabledFlow.value = enabled
        isPulseEntireIslandInitialized = true
    }

    fun getPulseAmount(context: Context): Float {
        if (!isPulseAmountInitialized) {
            val prefs = getPrefs(context)
            val amount = prefs.getFloat(KEY_PULSE_AMOUNT, DEFAULT_PULSE_AMOUNT)
            _pulseAmountFlow.value = amount
            isPulseAmountInitialized = true
        }
        return _pulseAmountFlow.value
    }

    fun setPulseAmount(context: Context, amount: Float) {
        getPrefs(context).edit().putFloat(KEY_PULSE_AMOUNT, amount).apply()
        _pulseAmountFlow.value = amount
        isPulseAmountInitialized = true
    }

    private val _islandFontWeightFlow = MutableStateFlow(400)
    val islandFontWeightFlow = _islandFontWeightFlow.asStateFlow()

    fun setIslandFontWeight(context: Context, weight: Int) {
        val value = weight.coerceIn(100, 900)
        getPrefs(context).edit().putInt("island_font_weight", value).apply()
        _islandFontWeightFlow.value = value
    }

    enum class FlashlightExpandedStyle(val label: String) {
        BEAM("Beam"), SLIDER("Slider")
    }
    val flashlightExpandedStyleFlow: StateFlow<FlashlightExpandedStyle> get() = FlashlightPrefsStore.flashlightExpandedStyleFlow
    val flashlightBeamBlurFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBeamBlurFlow
    val flashlightBeamAccentColorFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBeamAccentColorFlow

    fun getFlashlightExpandedStyle(context: Context): FlashlightExpandedStyle =
        FlashlightPrefsStore.getFlashlightExpandedStyle(getPrefs(context))

    fun setFlashlightExpandedStyle(context: Context, style: FlashlightExpandedStyle) =
        FlashlightPrefsStore.setFlashlightExpandedStyle(getPrefs(context), style)

    fun isFlashlightBeamBlurEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isFlashlightBeamBlurEnabled(getPrefs(context))

    fun setFlashlightBeamBlurEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setFlashlightBeamBlurEnabled(getPrefs(context), enabled)

    fun isFlashlightBeamAccentColorEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isFlashlightBeamAccentColorEnabled(getPrefs(context))

    fun setFlashlightBeamAccentColorEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setFlashlightBeamAccentColorEnabled(getPrefs(context), enabled)

    fun getIslandFontStyle(context: Context): IslandFontStyle {
        if (!isIslandFontStyleInitialized) {
            val prefs = getPrefs(context)
            val name = prefs.getString(KEY_ISLAND_FONT_STYLE, null)
            _islandFontStyleFlow.value = name?.let { saved ->
                IslandFontStyle.entries.firstOrNull { it.name == saved }
            } ?: IslandFontStyle.DEFAULT
            isIslandFontStyleInitialized = true
        }
        return _islandFontStyleFlow.value
    }

    fun setIslandFontStyle(context: Context, style: IslandFontStyle) {
        getPrefs(context).edit().putString(KEY_ISLAND_FONT_STYLE, style.name).apply()
        _islandFontStyleFlow.value = style
        isIslandFontStyleInitialized = true
    }

    fun isMinimizedNestedBlurredBgEnabled(context: Context): Boolean {
        if (!isMinimizedNestedBlurredBgInitialized) {
            val prefs = getPrefs(context)
            val enabled = prefs.getBoolean(KEY_MINIMIZED_NESTED_BLURRED_BG, false)
            _minimizedNestedBlurredBgFlow.value = enabled
            isMinimizedNestedBlurredBgInitialized = true
        }
        return _minimizedNestedBlurredBgFlow.value
    }

    fun setMinimizedNestedBlurredBgEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MINIMIZED_NESTED_BLURRED_BG, enabled).apply()
        _minimizedNestedBlurredBgFlow.value = enabled
        isMinimizedNestedBlurredBgInitialized = true
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

    fun isShowFlashlightIslandEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isShowFlashlightIslandEnabled(getPrefs(context))

    fun setShowFlashlightIslandEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setShowFlashlightIslandEnabled(getPrefs(context), enabled)

    fun isShowMinimizedFlashlightOutlineEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isShowMinimizedFlashlightOutlineEnabled(getPrefs(context))

    fun setShowMinimizedFlashlightOutlineEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setShowMinimizedFlashlightOutlineEnabled(getPrefs(context), enabled)

    fun isShowExpandedFlashlightOutlineEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isShowExpandedFlashlightOutlineEnabled(getPrefs(context))

    fun setShowExpandedFlashlightOutlineEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setShowExpandedFlashlightOutlineEnabled(getPrefs(context), enabled)

    fun isFlashlightTapToToggleEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isFlashlightTapToToggleEnabled(getPrefs(context))

    fun setFlashlightTapToToggleEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setFlashlightTapToToggleEnabled(getPrefs(context), enabled)

    fun isFlashlightBoostEnabled(context: Context): Boolean =
        FlashlightPrefsStore.isFlashlightBoostEnabled(getPrefs(context))

    fun setFlashlightBoostEnabled(context: Context, enabled: Boolean) =
        FlashlightPrefsStore.setFlashlightBoostEnabled(getPrefs(context), enabled)

    fun getBoostStrength(context: Context): Int = FlashlightPrefsStore.getBoostStrength(getPrefs(context))

    fun setBoostStrength(context: Context, strength: Int) =
        FlashlightPrefsStore.setBoostStrength(getPrefs(context), strength)

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

    private var isHideInFullscreenInitialized = false
    private var isHideInLandscapeInitialized = false

    fun isHideInFullscreenEnabled(context: Context): Boolean {
        if (!isHideInFullscreenInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_HIDE_IN_FULLSCREEN, false)
            _hideInFullscreenFlow.value = enabled
            isHideInFullscreenInitialized = true
        }
        return _hideInFullscreenFlow.value
    }

    fun setHideInFullscreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_IN_FULLSCREEN, enabled).apply()
        _hideInFullscreenFlow.value = enabled
        isHideInFullscreenInitialized = true
    }

    fun isHideInLandscapeEnabled(context: Context): Boolean {
        if (!isHideInLandscapeInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_HIDE_IN_LANDSCAPE, false)
            _hideInLandscapeFlow.value = enabled
            isHideInLandscapeInitialized = true
        }
        return _hideInLandscapeFlow.value
    }

    fun setHideInLandscapeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIDE_IN_LANDSCAPE, enabled).apply()
        _hideInLandscapeFlow.value = enabled
        isHideInLandscapeInitialized = true
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

    /** Seconds to keep the music island visible after pausing; 0 means keep until dismissed. */
    fun getMusicPauseTimeoutSeconds(context: Context): Int {
        if (!isMusicPauseTimeoutInitialized) {
            val saved = getPrefs(context)
                .getInt(KEY_MUSIC_PAUSE_TIMEOUT_SECONDS, DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS)
                .coerceIn(0, 300)
            _musicPauseTimeoutSecondsFlow.value = saved
            isMusicPauseTimeoutInitialized = true
        }
        return _musicPauseTimeoutSecondsFlow.value
    }

    fun setMusicPauseTimeoutSeconds(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(0, 300)
        getPrefs(context).edit().putInt(KEY_MUSIC_PAUSE_TIMEOUT_SECONDS, clamped).apply()
        _musicPauseTimeoutSecondsFlow.value = clamped
        isMusicPauseTimeoutInitialized = true
    }

    fun getNotificationActivitySettings(context: Context): NotificationActivitySettings =
        ActivityAndBlacklistPrefsStore.getNotificationActivitySettings(getPrefs(context))

    fun setNotificationActivitySettings(
        context: Context,
        settings: NotificationActivitySettings,
    ) = ActivityAndBlacklistPrefsStore.setNotificationActivitySettings(context, getPrefs(context), settings)

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

    fun isAppBlacklistEnabled(context: Context): Boolean =
        ActivityAndBlacklistPrefsStore.isAppBlacklistEnabled(getPrefs(context))

    fun setAppBlacklistEnabled(context: Context, enabled: Boolean) =
        ActivityAndBlacklistPrefsStore.setAppBlacklistEnabled(getPrefs(context), enabled)

    fun getBlacklistedPackages(context: Context): Set<String> =
        ActivityAndBlacklistPrefsStore.getBlacklistedPackages(getPrefs(context))

    fun setBlacklistedPackages(context: Context, packages: Set<String>) =
        ActivityAndBlacklistPrefsStore.setBlacklistedPackages(context, getPrefs(context), packages)

    fun addBlacklistedPackage(context: Context, packageName: String) =
        ActivityAndBlacklistPrefsStore.addBlacklistedPackage(context, getPrefs(context), packageName)

    fun removeBlacklistedPackage(context: Context, packageName: String) =
        ActivityAndBlacklistPrefsStore.removeBlacklistedPackage(context, getPrefs(context), packageName)

    fun toggleBlacklistedPackage(context: Context, packageName: String) =
        ActivityAndBlacklistPrefsStore.toggleBlacklistedPackage(context, getPrefs(context), packageName)

    fun clearBlacklistedPackages(context: Context) =
        ActivityAndBlacklistPrefsStore.clearBlacklistedPackages(context, getPrefs(context))

    fun getIslandPriorityOrder(context: Context): List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem> =
        ActivityAndBlacklistPrefsStore.getIslandPriorityOrder(getPrefs(context))

    fun setIslandPriorityOrder(
        context: Context,
        order: List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem>,
    ) = ActivityAndBlacklistPrefsStore.setIslandPriorityOrder(getPrefs(context), order)
}
