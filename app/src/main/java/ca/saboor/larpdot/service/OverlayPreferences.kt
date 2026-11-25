package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import ca.saboor.larpdot.notification.NotificationActivitySettings
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
    private const val KEY_PROGRESS_TIMESTAMP_PLACEMENT = "progress_timestamp_placement"
    private const val KEY_PLAYBACK_PLAY_PAUSE_BG = "playback_play_pause_bg"
    private const val KEY_PLAYBACK_PLAY_PAUSE_ACCENT = "playback_play_pause_accent"
    private const val KEY_PLAYBACK_SKIP_BG = "playback_skip_bg"
    private const val KEY_PLAYBACK_CORNER_RADIUS = "playback_corner_radius"
    private const val KEY_PLAYBACK_M3_BUTTON_GROUP = "playback_m3_button_group"
    private const val KEY_TRACK_INFO_SHOW_TITLE = "track_info_show_title"
    private const val KEY_TRACK_INFO_SHOW_ARTIST = "track_info_show_artist"
    private const val KEY_TRACK_INFO_SHOW_ALBUM = "track_info_show_album"
    private const val KEY_TRACK_INFO_ALBUM_BESIDE_ARTIST = "track_info_album_beside_artist"
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
    private const val KEY_ISLAND_FONT_WEIGHT = "island_font_weight"
    private const val KEY_MINIMIZED_NESTED_BLURRED_BG = "minimized_nested_blurred_bg"
    private const val KEY_HIDE_IN_FULLSCREEN = "hide_in_fullscreen"
    private const val KEY_HIDE_IN_LANDSCAPE = "hide_in_landscape"
    private const val KEY_CAMERA_COVER_STYLE = "camera_cover_style"
    private const val KEY_SHOW_CAMERA_SWOOP = "show_camera_swoop"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_SHOW_DEBUG_DOT = "show_debug_dot"
    private const val KEY_HIDE_WHEN_SCREEN_OFF = "hide_when_screen_off"
    private const val KEY_HIDE_ON_LOCK_SCREEN = "hide_on_lock_screen"
    private const val KEY_HIDE_MUSIC_WHEN_APP_OPEN = "hide_music_when_app_open"
    private const val KEY_WAVEFORM_BAND_COUNT = "waveform_band_count"
    const val DEFAULT_WAVEFORM_BAND_COUNT = 5
    private const val KEY_WAVEFORM_BAR_WIDTH = "waveform_bar_width"
    const val DEFAULT_WAVEFORM_BAR_WIDTH = 2.2f
    private const val KEY_WAVEFORM_BAR_SPACING = "waveform_bar_spacing"
    const val DEFAULT_WAVEFORM_BAR_SPACING = 2.0f
    private const val KEY_HQ_VISUALIZER_ENABLED = "hq_visualizer_enabled"
    const val KEY_VISUALIZER_MODE = "visualizer_mode"
    const val KEY_PREVIEW_AUDIO_SOURCE = "preview_audio_source"

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

    enum class ProgressTimestampPlacement(val label: String) {
        HIDDEN("Hidden"),
        BELOW("Below"),
        BESIDE("Beside");

        companion object {
            internal fun legacyDefault(layout: ExpandedPlayerLayout): ProgressTimestampPlacement =
                if (layout == ExpandedPlayerLayout.IPHONE) BESIDE else HIDDEN
        }
    }

    enum class FlashlightExpandedStyle(val label: String) {
        BEAM("Beam"),
        SLIDER("Slider"),
    }

    enum class VisualizerMode(val key: String, val title: String, val description: String) {
        SIMULATED("simulated", "Simulated", "Smooth flowing wave animation"),
        BPM("bpm", "BPM Beat Sync", "Rhythmic pulse locked to track tempo (zero CPU impact)"),
        DEVICE_AUDIO("device_audio", "Device Audio", "Live internal audio captured directly from phone"),
        AUDIO_PREVIEW("audio_preview", "Preview Audio", "Decodes 30s audio offline to extract frequency bands");

        companion object {
            fun fromKey(key: String?): VisualizerMode = entries.firstOrNull { it.key == key } ?: SIMULATED
        }
    }

    enum class PreviewAudioSource(val key: String, val title: String, val subtitle: String) {
        AUTO("auto", "Auto", "Tries Deezer first, falls back to iTunes"),
        DEEZER("deezer", "Deezer", "Queries Deezer 30s preview catalog"),
        ITUNES("itunes", "iTunes", "Queries iTunes Search API"),
        DEVICE("device", "Device (Live)", "Captures internal audio directly from phone");

        companion object {
            fun fromKey(key: String?): PreviewAudioSource = entries.firstOrNull { it.key == key } ?: AUTO
        }
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
        val albumBesideArtist: Boolean = false,
    )

    data class CutoutConfig(
        val isManualEnabled: Boolean = false,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val customDiameterDp: Float = 0f,
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

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun parseAlbumArtStyle(styleName: String?): AlbumArtStyle = when (styleName) {
        "BASIC_FADED" -> AlbumArtStyle.BASIC_FADED
        "NESTED", "NESTED_ROUNDED_SQUARE" -> AlbumArtStyle.NESTED
        "FULL_BACKGROUND" -> AlbumArtStyle.FULL_BACKGROUND
        else -> AlbumArtStyle.BLENDED
    }

    private fun parseExpandedBackgroundStyle(styleName: String?): ExpandedBackgroundStyle = when (styleName) {
        "NONE", "NESTED", "NESTED_ROUNDED_SQUARE" -> ExpandedBackgroundStyle.NONE
        "BASIC_FADED" -> ExpandedBackgroundStyle.BASIC_FADED
        "FULL_BACKGROUND" -> ExpandedBackgroundStyle.FULL_BACKGROUND
        "BLURRED_FULL_BACKGROUND" -> ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND
        else -> ExpandedBackgroundStyle.BLENDED
    }

    // ---- Scalar / Enum PrefState instances ----
    private val overlayEnabledPref = BooleanPref(KEY_OVERLAY_ENABLED, false)
    private val tapToExpandPref = BooleanPref(KEY_TAP_TO_EXPAND, false)
    private val holdToExpandDurationMsPref = IntPref(
        KEY_HOLD_TO_EXPAND_DURATION_MS,
        DEFAULT_HOLD_TO_EXPAND_DURATION_MS,
        MIN_HOLD_TO_EXPAND_DURATION_MS,
        MAX_HOLD_TO_EXPAND_DURATION_MS,
    )
    private val bubbleStylePref = EnumPref(
        KEY_BUBBLE_STYLE,
        BubbleStyle.IPHONE,
        parse = { saved -> BubbleStyle.entries.firstOrNull { it.name == saved } ?: BubbleStyle.IPHONE },
    )
    private val smallerBubblesPref = BooleanPref(KEY_SMALLER_BUBBLES, false)
    private val showMinimizedTitlePref = BooleanPref(KEY_SHOW_MINIMIZED_TITLE, false)
    private val showSongAnnouncementPref = BooleanPref(KEY_SHOW_SONG_ANNOUNCEMENT, true)
    private val minimizedAlbumArtStylePref = EnumPref(
        KEY_MINIMIZED_ALBUM_ART_STYLE,
        AlbumArtStyle.BLENDED,
        parse = ::parseAlbumArtStyle,
        fallbackKey = KEY_ALBUM_ART_STYLE,
    )
    private val expandedAlbumArtStylePref = EnumPref(
        KEY_EXPANDED_ALBUM_ART_STYLE,
        ExpandedBackgroundStyle.BLENDED,
        parse = ::parseExpandedBackgroundStyle,
        fallbackKey = KEY_ALBUM_ART_STYLE,
    )
    private val showExpandedAlbumArtPref = BooleanPref(KEY_SHOW_EXPANDED_ALBUM_ART, true)
    private val albumArtGlowPref = BooleanPref(KEY_ALBUM_ART_GLOW, false)
    private val expandedPlayerLayoutPref = EnumPref(
        KEY_EXPANDED_PLAYER_LAYOUT,
        ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
        parse = { saved -> ExpandedPlayerLayout.entries.firstOrNull { it.name == saved } ?: ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS },
    )
    private val showProgressSquigglesPref = BooleanPref(KEY_SHOW_PROGRESS_SQUIGGLES, true)
    private val progressBarAccentColorPref = BooleanPref(KEY_PROGRESS_BAR_ACCENT_COLOR, true)
    private val progressTimestampPlacementPref = EnumPref(
        KEY_PROGRESS_TIMESTAMP_PLACEMENT,
        ProgressTimestampPlacement.HIDDEN,
        parse = { saved -> ProgressTimestampPlacement.entries.firstOrNull { it.name == saved } ?: ProgressTimestampPlacement.HIDDEN },
    )
    private val showMinimizedVisualizerPref = BooleanPref(KEY_SHOW_MINIMIZED_VISUALIZER, true)
    private val minimizedAlbumArtShapePref = EnumPref(
        KEY_MINIMIZED_ALBUM_ART_SHAPE,
        NestedAlbumArtShape.ROUNDED_SQUARE,
        parse = NestedAlbumArtShape::fromName,
        fallbackKey = KEY_NESTED_ALBUM_ART_SHAPE,
    )
    private val expandedAlbumArtShapePref = EnumPref(
        KEY_EXPANDED_ALBUM_ART_SHAPE,
        NestedAlbumArtShape.ROUNDED_SQUARE,
        parse = NestedAlbumArtShape::fromName,
        fallbackKey = KEY_NESTED_ALBUM_ART_SHAPE,
    )
    private val showMinimizedProgressOutlinePref = BooleanPref(
        KEY_SHOW_MINIMIZED_PROGRESS_OUTLINE,
        true,
        fallbackKey = KEY_SHOW_PROGRESS_OUTLINE,
    )
    private val showExpandedProgressOutlinePref = BooleanPref(
        KEY_SHOW_EXPANDED_PROGRESS_OUTLINE,
        true,
        fallbackKey = KEY_SHOW_PROGRESS_OUTLINE,
    )
    private val minimizedAlbumArtRotationPref = FloatPref(KEY_MINIMIZED_ALBUM_ART_ROTATION, 0f)
    private val expandedAlbumArtRotationPref = FloatPref(KEY_EXPANDED_ALBUM_ART_ROTATION, 0f)
    private val showMinimizedDominantColorGlowPref = BooleanPref(
        KEY_SHOW_MINIMIZED_DOMINANT_COLOR_GLOW,
        true,
        fallbackKey = KEY_SHOW_DOMINANT_COLOR_GLOW,
    )
    private val showExpandedDominantColorGlowPref = BooleanPref(
        KEY_SHOW_EXPANDED_DOMINANT_COLOR_GLOW,
        true,
        fallbackKey = KEY_SHOW_DOMINANT_COLOR_GLOW,
    )
    private val bpmPulseEnabledPref = BooleanPref(KEY_BPM_PULSE_ENABLED, true)
    private val minimizedBpmPulseEnabledPref = BooleanPref(
        KEY_MINIMIZED_BPM_PULSE_ENABLED,
        true,
        fallbackKey = KEY_BPM_PULSE_ENABLED,
    )
    private val expandedBpmPulseEnabledPref = BooleanPref(
        KEY_EXPANDED_BPM_PULSE_ENABLED,
        true,
        fallbackKey = KEY_BPM_PULSE_ENABLED,
    )
    private val pulseEntireIslandEnabledPref = BooleanPref(KEY_PULSE_ENTIRE_ISLAND_ENABLED, false)
    private val pulseAmountPref = FloatPref(KEY_PULSE_AMOUNT, DEFAULT_PULSE_AMOUNT)
    private val islandFontStylePref = EnumPref(
        KEY_ISLAND_FONT_STYLE,
        IslandFontStyle.DEFAULT,
        parse = { saved -> IslandFontStyle.entries.firstOrNull { it.name == saved } ?: IslandFontStyle.DEFAULT },
    )
    private val islandFontWeightPref = IntPref(KEY_ISLAND_FONT_WEIGHT, 400, 100, 900)
    private val minimizedNestedBlurredBgPref = BooleanPref(KEY_MINIMIZED_NESTED_BLURRED_BG, false)
    private val isDebugModePref = BooleanPref(KEY_DEBUG_MODE, false, fallbackKey = KEY_SHOW_DEBUG_DOT)
    private val hideWhenScreenOffPref = BooleanPref(KEY_HIDE_WHEN_SCREEN_OFF, true)
    private val hideOnLockScreenPref = BooleanPref(KEY_HIDE_ON_LOCK_SCREEN, false)
    private val hideInFullscreenPref = BooleanPref(KEY_HIDE_IN_FULLSCREEN, false)
    private val hideInLandscapePref = BooleanPref(KEY_HIDE_IN_LANDSCAPE, false)
    private val hideMusicWhenAppOpenPref = BooleanPref(KEY_HIDE_MUSIC_WHEN_APP_OPEN, true)
    private val musicPauseTimeoutSecondsPref = IntPref(
        KEY_MUSIC_PAUSE_TIMEOUT_SECONDS,
        DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS,
        0,
        300,
    )
    private val waveformBandCountPref = IntPref(KEY_WAVEFORM_BAND_COUNT, DEFAULT_WAVEFORM_BAND_COUNT)
    private val waveformBarWidthPref = FloatPref(KEY_WAVEFORM_BAR_WIDTH, DEFAULT_WAVEFORM_BAR_WIDTH)
    private val waveformBarSpacingPref = FloatPref(KEY_WAVEFORM_BAR_SPACING, DEFAULT_WAVEFORM_BAR_SPACING)
    private val previewAudioSourcePref = EnumPref(
        KEY_PREVIEW_AUDIO_SOURCE,
        PreviewAudioSource.AUTO,
        parse = PreviewAudioSource::fromKey,
        serialize = { it.key },
    )
    private val expandedBackgroundOpacityPref = FloatPref("expanded_background_opacity", 1f, 0f, 1f)
    private val backdropBlurEnabledPref = BooleanPref("backdrop_blur_enabled", false)
    private val backdropBlurRadiusPref = FloatPref("backdrop_blur_radius", 40f, 5f, 120f)
    private val minimizedMaxWidthPercentPref = FloatPref("minimized_max_width_percent", 50f, 40f, 100f)
    private val islandCornerRadiusPref = FloatPref("island_corner_radius", 60f, 0f, 100f)
    private val islandSquircleEnabledPref = BooleanPref("island_squircle_enabled", true)

    // ---- Public StateFlows ----
    val isEnabledFlow: StateFlow<Boolean> = overlayEnabledPref.flow
    val tapToExpandFlow: StateFlow<Boolean> = tapToExpandPref.flow
    val holdToExpandDurationMsFlow: StateFlow<Int> = holdToExpandDurationMsPref.flow
    val bubbleStyleFlow: StateFlow<BubbleStyle> = bubbleStylePref.flow
    val smallerBubblesFlow: StateFlow<Boolean> = smallerBubblesPref.flow
    val showMinimizedTitleFlow: StateFlow<Boolean> = showMinimizedTitlePref.flow
    val showSongAnnouncementFlow: StateFlow<Boolean> = showSongAnnouncementPref.flow
    val minimizedAlbumArtStyleFlow: StateFlow<AlbumArtStyle> = minimizedAlbumArtStylePref.flow
    val expandedAlbumArtStyleFlow: StateFlow<ExpandedBackgroundStyle> = expandedAlbumArtStylePref.flow
    val showExpandedAlbumArtFlow: StateFlow<Boolean> = showExpandedAlbumArtPref.flow
    val albumArtGlowFlow: StateFlow<Boolean> = albumArtGlowPref.flow
    val expandedPlayerLayoutFlow: StateFlow<ExpandedPlayerLayout> = expandedPlayerLayoutPref.flow
    val showProgressSquigglesFlow: StateFlow<Boolean> = showProgressSquigglesPref.flow
    val progressBarAccentColorFlow: StateFlow<Boolean> = progressBarAccentColorPref.flow
    val progressTimestampPlacementFlow: StateFlow<ProgressTimestampPlacement> = progressTimestampPlacementPref.flow
    val showMinimizedVisualizerFlow: StateFlow<Boolean> = showMinimizedVisualizerPref.flow
    val minimizedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = minimizedAlbumArtShapePref.flow
    val expandedAlbumArtShapeFlow: StateFlow<NestedAlbumArtShape> = expandedAlbumArtShapePref.flow
    val showMinimizedProgressOutlineFlow: StateFlow<Boolean> = showMinimizedProgressOutlinePref.flow
    val showProgressOutlineFlow: StateFlow<Boolean> get() = showMinimizedProgressOutlinePref.flow
    val showExpandedProgressOutlineFlow: StateFlow<Boolean> = showExpandedProgressOutlinePref.flow
    val minimizedAlbumArtRotationFlow: StateFlow<Float> = minimizedAlbumArtRotationPref.flow
    val expandedAlbumArtRotationFlow: StateFlow<Float> = expandedAlbumArtRotationPref.flow
    val showMinimizedDominantColorGlowFlow: StateFlow<Boolean> = showMinimizedDominantColorGlowPref.flow
    val showExpandedDominantColorGlowFlow: StateFlow<Boolean> = showExpandedDominantColorGlowPref.flow
    val minimizedBpmPulseEnabledFlow: StateFlow<Boolean> = minimizedBpmPulseEnabledPref.flow
    val expandedBpmPulseEnabledFlow: StateFlow<Boolean> = expandedBpmPulseEnabledPref.flow
    val pulseEntireIslandEnabledFlow: StateFlow<Boolean> = pulseEntireIslandEnabledPref.flow
    val pulseAmountFlow: StateFlow<Float> = pulseAmountPref.flow
    val islandFontStyleFlow: StateFlow<IslandFontStyle> = islandFontStylePref.flow
    val islandFontWeightFlow: StateFlow<Int> = islandFontWeightPref.flow
    val minimizedNestedBlurredBgFlow: StateFlow<Boolean> = minimizedNestedBlurredBgPref.flow
    val isDebugModeFlow: StateFlow<Boolean> = isDebugModePref.flow
    val hideWhenScreenOffFlow: StateFlow<Boolean> = hideWhenScreenOffPref.flow
    val hideOnLockScreenFlow: StateFlow<Boolean> = hideOnLockScreenPref.flow
    val hideInFullscreenFlow: StateFlow<Boolean> = hideInFullscreenPref.flow
    val hideInLandscapeFlow: StateFlow<Boolean> = hideInLandscapePref.flow
    val hideMusicWhenAppOpenFlow: StateFlow<Boolean> = hideMusicWhenAppOpenPref.flow
    val musicPauseTimeoutSecondsFlow: StateFlow<Int> = musicPauseTimeoutSecondsPref.flow
    val waveformBandCountFlow: StateFlow<Int> = waveformBandCountPref.flow
    val waveformBarWidthFlow: StateFlow<Float> = waveformBarWidthPref.flow
    val waveformBarSpacingFlow: StateFlow<Float> = waveformBarSpacingPref.flow
    val previewAudioSourceFlow: StateFlow<PreviewAudioSource> = previewAudioSourcePref.flow
    val expandedBackgroundOpacity: StateFlow<Float> = expandedBackgroundOpacityPref.flow
    val backdropBlurEnabled: StateFlow<Boolean> = backdropBlurEnabledPref.flow
    val backdropBlurRadius: StateFlow<Float> = backdropBlurRadiusPref.flow
    val minimizedMaxWidthPercent: StateFlow<Float> = minimizedMaxWidthPercentPref.flow
    val islandCornerRadius: StateFlow<Float> = islandCornerRadiusPref.flow
    val islandSquircleEnabled: StateFlow<Boolean> = islandSquircleEnabledPref.flow

    // ---- Compound / Delegated StateFlows ----
    private val _playbackControlsStyleFlow = MutableStateFlow(PlaybackControlsStyle())
    val playbackControlsStyleFlow: StateFlow<PlaybackControlsStyle> = _playbackControlsStyleFlow.asStateFlow()
    private var isPlaybackControlsStyleInitialized = false

    private val _trackInfoStyleFlow = MutableStateFlow(TrackInfoStyle())
    val trackInfoStyleFlow: StateFlow<TrackInfoStyle> = _trackInfoStyleFlow.asStateFlow()
    private var isTrackInfoStyleInitialized = false

    private val _expandedElementVisibilityFlow = MutableStateFlow(ExpandedElementVisibility())
    val expandedElementVisibilityFlow: StateFlow<ExpandedElementVisibility> = _expandedElementVisibilityFlow.asStateFlow()
    private var isExpandedElementVisibilityInitialized = false

    private val _expandedGlowSidesFlow = MutableStateFlow(ExpandedGlowSides())
    val expandedGlowSidesFlow: StateFlow<ExpandedGlowSides> = _expandedGlowSidesFlow.asStateFlow()
    private var isExpandedGlowSidesInitialized = false

    private val _cutoutConfigFlow = MutableStateFlow(CutoutConfig())
    val cutoutConfigFlow: StateFlow<CutoutConfig> = _cutoutConfigFlow.asStateFlow()
    private var isCutoutInitialized = false

    private val _cameraCoverStyleFlow = MutableStateFlow(CameraCoverStyle.SWOOP)
    val cameraCoverStyleFlow: StateFlow<CameraCoverStyle> = _cameraCoverStyleFlow.asStateFlow()
    private var isCameraCoverStyleInitialized = false

    private val _visualizerModeFlow = MutableStateFlow(VisualizerMode.SIMULATED)
    val visualizerModeFlow: StateFlow<VisualizerMode> = _visualizerModeFlow.asStateFlow()
    private var isVisualizerModeInitialized = false

    val showFlashlightIslandFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showFlashlightIslandFlow
    val showMinimizedFlashlightOutlineFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showMinimizedFlashlightOutlineFlow
    val showExpandedFlashlightOutlineFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.showExpandedFlashlightOutlineFlow
    val flashlightTapToToggleFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightTapToToggleFlow
    val flashlightBoostFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBoostFlow
    val flashlightExpandedStyleFlow: StateFlow<FlashlightExpandedStyle> get() = FlashlightPrefsStore.flashlightExpandedStyleFlow
    val flashlightBeamBlurFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBeamBlurFlow
    val flashlightBeamAccentColorFlow: StateFlow<Boolean> get() = FlashlightPrefsStore.flashlightBeamAccentColorFlow

    val appBlacklistEnabledFlow: StateFlow<Boolean> get() = ActivityAndBlacklistPrefsStore.appBlacklistEnabledFlow
    val blacklistedPackagesFlow: StateFlow<Set<String>> get() = ActivityAndBlacklistPrefsStore.blacklistedPackagesFlow
    val notificationActivitySettingsFlow: StateFlow<NotificationActivitySettings>
        get() = ActivityAndBlacklistPrefsStore.notificationActivitySettingsFlow
    val islandPriorityOrderFlow: StateFlow<List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem>>
        get() = ActivityAndBlacklistPrefsStore.islandPriorityOrderFlow

    // ---- Batch loaders ----
    fun loadIslandAppearance(context: Context) {
        val prefs = getPrefs(context)
        minimizedMaxWidthPercentPref.reload(prefs)
        islandCornerRadiusPref.reload(prefs)
        islandSquircleEnabledPref.reload(prefs)
        expandedBackgroundOpacityPref.reload(prefs)
        backdropBlurEnabledPref.reload(prefs)
        backdropBlurRadiusPref.reload(prefs)
        isTapToExpandEnabled(context)
        getHoldToExpandDurationMs(context)
    }

    fun initializeMusicSettings(context: Context) {
        val prefs = getPrefs(context)
        loadIslandAppearance(context)
        isShowMinimizedProgressOutlineEnabled(context)
        isShowExpandedProgressOutlineEnabled(context)
        isShowMinimizedTitleEnabled(context)
        isShowSongAnnouncementEnabled(context)
        getMusicPauseTimeoutSeconds(context)
        islandFontWeightPref.reload(prefs)
        getFlashlightExpandedStyle(context)
        isFlashlightBeamBlurEnabled(context)
        isFlashlightBeamAccentColorEnabled(context)
        getIslandFontStyle(context)
        getPulseAmount(context)
        isMinimizedNestedBlurredBgEnabled(context)
        getMinimizedAlbumArtStyle(context)
        getExpandedAlbumArtStyle(context)
        isShowExpandedAlbumArtEnabled(context)
        albumArtGlowPref.reload(prefs)
        getExpandedPlayerLayout(context)
        isShowProgressSquigglesEnabled(context)
        isProgressBarAccentColorEnabled(context)
        getProgressTimestampPlacement(context)
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
        getVisualizerMode(context)
        getPreviewAudioSource(context)
    }

    // ---- Simple Preference Accessors ----
    fun isOverlayEnabled(context: Context): Boolean = overlayEnabledPref.get(getPrefs(context))
    fun setOverlayEnabled(context: Context, enabled: Boolean) = overlayEnabledPref.set(getPrefs(context), enabled)

    fun isTapToExpandEnabled(context: Context): Boolean = tapToExpandPref.get(getPrefs(context))
    fun setTapToExpandEnabled(context: Context, enabled: Boolean) = tapToExpandPref.set(getPrefs(context), enabled)

    fun getHoldToExpandDurationMs(context: Context): Int = holdToExpandDurationMsPref.get(getPrefs(context))
    fun setHoldToExpandDurationMs(context: Context, durationMs: Int) = holdToExpandDurationMsPref.set(getPrefs(context), durationMs)

    fun getBubbleStyle(context: Context): BubbleStyle = bubbleStylePref.get(getPrefs(context))
    fun setBubbleStyle(context: Context, style: BubbleStyle) = bubbleStylePref.set(getPrefs(context), style)

    fun areSmallerBubblesEnabled(context: Context): Boolean = smallerBubblesPref.get(getPrefs(context))
    fun setSmallerBubblesEnabled(context: Context, enabled: Boolean) = smallerBubblesPref.set(getPrefs(context), enabled)

    fun isShowMinimizedTitleEnabled(context: Context): Boolean = showMinimizedTitlePref.get(getPrefs(context))
    fun setShowMinimizedTitleEnabled(context: Context, enabled: Boolean) = showMinimizedTitlePref.set(getPrefs(context), enabled)

    fun isShowSongAnnouncementEnabled(context: Context): Boolean = showSongAnnouncementPref.get(getPrefs(context))
    fun setShowSongAnnouncementEnabled(context: Context, enabled: Boolean) = showSongAnnouncementPref.set(getPrefs(context), enabled)

    fun getMinimizedAlbumArtStyle(context: Context): AlbumArtStyle = minimizedAlbumArtStylePref.get(getPrefs(context))
    fun setMinimizedAlbumArtStyle(context: Context, style: AlbumArtStyle) = minimizedAlbumArtStylePref.set(getPrefs(context), style)

    fun getExpandedAlbumArtStyle(context: Context): ExpandedBackgroundStyle = expandedAlbumArtStylePref.get(getPrefs(context))
    fun setExpandedAlbumArtStyle(context: Context, style: ExpandedBackgroundStyle) = expandedAlbumArtStylePref.set(getPrefs(context), style)

    fun isShowExpandedAlbumArtEnabled(context: Context): Boolean = showExpandedAlbumArtPref.get(getPrefs(context))
    fun setShowExpandedAlbumArtEnabled(context: Context, enabled: Boolean) = showExpandedAlbumArtPref.set(getPrefs(context), enabled)

    fun setAlbumArtGlowEnabled(context: Context, enabled: Boolean) = albumArtGlowPref.set(getPrefs(context), enabled)

    fun getExpandedPlayerLayout(context: Context): ExpandedPlayerLayout = expandedPlayerLayoutPref.get(getPrefs(context))
    fun setExpandedPlayerLayout(context: Context, layout: ExpandedPlayerLayout) = expandedPlayerLayoutPref.set(getPrefs(context), layout)

    fun isShowProgressSquigglesEnabled(context: Context): Boolean = showProgressSquigglesPref.get(getPrefs(context))
    fun setShowProgressSquigglesEnabled(context: Context, enabled: Boolean) = showProgressSquigglesPref.set(getPrefs(context), enabled)

    fun isProgressBarAccentColorEnabled(context: Context): Boolean = progressBarAccentColorPref.get(getPrefs(context))
    fun setProgressBarAccentColorEnabled(context: Context, enabled: Boolean) = progressBarAccentColorPref.set(getPrefs(context), enabled)

    fun getProgressTimestampPlacement(context: Context): ProgressTimestampPlacement {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_PROGRESS_TIMESTAMP_PLACEMENT)) {
            val placement = ProgressTimestampPlacement.legacyDefault(getExpandedPlayerLayout(context))
            progressTimestampPlacementPref.set(prefs, placement)
            return placement
        }
        return progressTimestampPlacementPref.get(prefs)
    }

    fun setProgressTimestampPlacement(context: Context, placement: ProgressTimestampPlacement) =
        progressTimestampPlacementPref.set(getPrefs(context), placement)

    fun isShowMinimizedVisualizerEnabled(context: Context): Boolean = showMinimizedVisualizerPref.get(getPrefs(context))
    fun setShowMinimizedVisualizerEnabled(context: Context, enabled: Boolean) = showMinimizedVisualizerPref.set(getPrefs(context), enabled)

    fun getMinimizedAlbumArtShape(context: Context): NestedAlbumArtShape = minimizedAlbumArtShapePref.get(getPrefs(context))
    fun setMinimizedAlbumArtShape(context: Context, shape: NestedAlbumArtShape) = minimizedAlbumArtShapePref.set(getPrefs(context), shape)

    fun getExpandedAlbumArtShape(context: Context): NestedAlbumArtShape = expandedAlbumArtShapePref.get(getPrefs(context))
    fun setExpandedAlbumArtShape(context: Context, shape: NestedAlbumArtShape) = expandedAlbumArtShapePref.set(getPrefs(context), shape)

    fun isShowMinimizedProgressOutlineEnabled(context: Context): Boolean = showMinimizedProgressOutlinePref.get(getPrefs(context))
    fun setShowMinimizedProgressOutlineEnabled(context: Context, enabled: Boolean) = showMinimizedProgressOutlinePref.set(getPrefs(context), enabled)

    fun isShowExpandedProgressOutlineEnabled(context: Context): Boolean = showExpandedProgressOutlinePref.get(getPrefs(context))
    fun setShowExpandedProgressOutlineEnabled(context: Context, enabled: Boolean) = showExpandedProgressOutlinePref.set(getPrefs(context), enabled)

    fun getMinimizedAlbumArtRotation(context: Context): Float = minimizedAlbumArtRotationPref.get(getPrefs(context))
    fun setMinimizedAlbumArtRotation(context: Context, degrees: Float) = minimizedAlbumArtRotationPref.set(getPrefs(context), degrees)

    fun getExpandedAlbumArtRotation(context: Context): Float = expandedAlbumArtRotationPref.get(getPrefs(context))
    fun setExpandedAlbumArtRotation(context: Context, degrees: Float) = expandedAlbumArtRotationPref.set(getPrefs(context), degrees)

    fun isShowMinimizedDominantColorGlowEnabled(context: Context): Boolean = showMinimizedDominantColorGlowPref.get(getPrefs(context))
    fun setShowMinimizedDominantColorGlowEnabled(context: Context, enabled: Boolean) = showMinimizedDominantColorGlowPref.set(getPrefs(context), enabled)

    fun isShowExpandedDominantColorGlowEnabled(context: Context): Boolean = showExpandedDominantColorGlowPref.get(getPrefs(context))
    fun setShowExpandedDominantColorGlowEnabled(context: Context, enabled: Boolean) = showExpandedDominantColorGlowPref.set(getPrefs(context), enabled)

    fun isBpmPulseEnabled(context: Context): Boolean = bpmPulseEnabledPref.get(getPrefs(context))

    fun isMinimizedBpmPulseEnabled(context: Context): Boolean = minimizedBpmPulseEnabledPref.get(getPrefs(context))
    fun setMinimizedBpmPulseEnabled(context: Context, enabled: Boolean) = minimizedBpmPulseEnabledPref.set(getPrefs(context), enabled)

    fun isExpandedBpmPulseEnabled(context: Context): Boolean = expandedBpmPulseEnabledPref.get(getPrefs(context))
    fun setExpandedBpmPulseEnabled(context: Context, enabled: Boolean) = expandedBpmPulseEnabledPref.set(getPrefs(context), enabled)

    fun isPulseEntireIslandEnabled(context: Context): Boolean = pulseEntireIslandEnabledPref.get(getPrefs(context))
    fun setPulseEntireIslandEnabled(context: Context, enabled: Boolean) = pulseEntireIslandEnabledPref.set(getPrefs(context), enabled)

    fun getPulseAmount(context: Context): Float = pulseAmountPref.get(getPrefs(context))
    fun setPulseAmount(context: Context, amount: Float) = pulseAmountPref.set(getPrefs(context), amount)

    fun getIslandFontStyle(context: Context): IslandFontStyle = islandFontStylePref.get(getPrefs(context))
    fun setIslandFontStyle(context: Context, style: IslandFontStyle) = islandFontStylePref.set(getPrefs(context), style)

    fun setIslandFontWeight(context: Context, weight: Int) = islandFontWeightPref.set(getPrefs(context), weight)

    fun isMinimizedNestedBlurredBgEnabled(context: Context): Boolean = minimizedNestedBlurredBgPref.get(getPrefs(context))
    fun setMinimizedNestedBlurredBgEnabled(context: Context, enabled: Boolean) = minimizedNestedBlurredBgPref.set(getPrefs(context), enabled)

    fun isDebugModeEnabled(context: Context): Boolean = isDebugModePref.get(getPrefs(context))
    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_DEBUG_MODE, enabled)
            .putBoolean(KEY_SHOW_DEBUG_DOT, enabled)
            .apply()
        isDebugModePref.forceSet(enabled)
    }

    fun isHideWhenScreenOffEnabled(context: Context): Boolean = hideWhenScreenOffPref.get(getPrefs(context))
    fun setHideWhenScreenOffEnabled(context: Context, enabled: Boolean) = hideWhenScreenOffPref.set(getPrefs(context), enabled)

    fun isHideOnLockScreenEnabled(context: Context): Boolean = hideOnLockScreenPref.get(getPrefs(context))
    fun setHideOnLockScreenEnabled(context: Context, enabled: Boolean) = hideOnLockScreenPref.set(getPrefs(context), enabled)

    fun isHideInFullscreenEnabled(context: Context): Boolean = hideInFullscreenPref.get(getPrefs(context))
    fun setHideInFullscreenEnabled(context: Context, enabled: Boolean) = hideInFullscreenPref.set(getPrefs(context), enabled)

    fun isHideInLandscapeEnabled(context: Context): Boolean = hideInLandscapePref.get(getPrefs(context))
    fun setHideInLandscapeEnabled(context: Context, enabled: Boolean) = hideInLandscapePref.set(getPrefs(context), enabled)

    fun isHideMusicWhenAppOpenEnabled(context: Context): Boolean = hideMusicWhenAppOpenPref.get(getPrefs(context))
    fun setHideMusicWhenAppOpenEnabled(context: Context, enabled: Boolean) = hideMusicWhenAppOpenPref.set(getPrefs(context), enabled)

    /** Seconds to keep the music island visible after pausing; 0 means keep until dismissed. */
    fun getMusicPauseTimeoutSeconds(context: Context): Int = musicPauseTimeoutSecondsPref.get(getPrefs(context))
    fun setMusicPauseTimeoutSeconds(context: Context, seconds: Int) = musicPauseTimeoutSecondsPref.set(getPrefs(context), seconds)

    fun getWaveformBandCount(context: Context): Int = waveformBandCountPref.get(getPrefs(context))
    fun setWaveformBandCount(context: Context, count: Int) = waveformBandCountPref.set(getPrefs(context), count)

    fun getWaveformBarWidth(context: Context): Float = waveformBarWidthPref.get(getPrefs(context))
    fun setWaveformBarWidth(context: Context, widthDp: Float) = waveformBarWidthPref.set(getPrefs(context), widthDp)

    fun getWaveformBarSpacing(context: Context): Float = waveformBarSpacingPref.get(getPrefs(context))
    fun setWaveformBarSpacing(context: Context, spacingDp: Float) = waveformBarSpacingPref.set(getPrefs(context), spacingDp)

    fun getPreviewAudioSource(context: Context): PreviewAudioSource = previewAudioSourcePref.get(getPrefs(context))
    fun setPreviewAudioSource(context: Context, source: PreviewAudioSource) = previewAudioSourcePref.set(getPrefs(context), source)

    fun setMinimizedMaxWidthPercent(context: Context, value: Float) = minimizedMaxWidthPercentPref.set(getPrefs(context), value)
    fun setIslandCornerRadius(context: Context, value: Float) = islandCornerRadiusPref.set(getPrefs(context), value)
    fun setIslandSquircleEnabled(context: Context, enabled: Boolean) = islandSquircleEnabledPref.set(getPrefs(context), enabled)
    fun setExpandedBackgroundOpacity(context: Context, value: Float) = expandedBackgroundOpacityPref.set(getPrefs(context), value)
    fun setBackdropBlurEnabled(context: Context, enabled: Boolean) = backdropBlurEnabledPref.set(getPrefs(context), enabled)
    fun setBackdropBlurRadius(context: Context, value: Float) = backdropBlurRadiusPref.set(getPrefs(context), value)

    // ---- Compound / Migration Preference Accessors ----
    fun getCutoutConfig(context: Context): CutoutConfig {
        if (!isCutoutInitialized) {
            val prefs = getPrefs(context)
            _cutoutConfigFlow.value = CutoutConfig(
                isManualEnabled = prefs.getBoolean(KEY_MANUAL_CUTOUT_ENABLED, false),
                offsetX = prefs.getFloat(KEY_CUTOUT_OFFSET_X, 0f),
                offsetY = prefs.getFloat(KEY_CUTOUT_OFFSET_Y, 0f),
                customDiameterDp = prefs.getFloat(KEY_CUTOUT_DIAMETER, 0f),
            )
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
                albumBesideArtist = prefs.getBoolean(KEY_TRACK_INFO_ALBUM_BESIDE_ARTIST, false),
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
            .putBoolean(KEY_TRACK_INFO_ALBUM_BESIDE_ARTIST, style.albumBesideArtist)
            .putBoolean(KEY_TRACK_INFO_TITLE_ACCENT, style.titleAccentColor)
            .apply()
        _trackInfoStyleFlow.value = style
        isTrackInfoStyleInitialized = true
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

    fun getExpandedGlowSides(context: Context): ExpandedGlowSides {
        if (!isExpandedGlowSidesInitialized) {
            val prefs = getPrefs(context)
            _expandedGlowSidesFlow.value = ExpandedGlowSides(
                left = prefs.getBoolean(KEY_EXPANDED_GLOW_LEFT, false),
                right = prefs.getBoolean(KEY_EXPANDED_GLOW_RIGHT, true),
                bottom = prefs.getBoolean(KEY_EXPANDED_GLOW_BOTTOM, true),
                top = prefs.getBoolean(KEY_EXPANDED_GLOW_TOP, false),
            )
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
        isVisualizerModeInitialized = true
    }

    // ---- Delegated Store Accessors (Flashlight, Blacklist, Live Activities, Priority) ----
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

    fun getNotificationActivitySettings(context: Context): NotificationActivitySettings =
        ActivityAndBlacklistPrefsStore.getNotificationActivitySettings(getPrefs(context))
    fun setNotificationActivitySettings(context: Context, settings: NotificationActivitySettings) =
        ActivityAndBlacklistPrefsStore.setNotificationActivitySettings(context, getPrefs(context), settings)

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
    fun setIslandPriorityOrder(context: Context, order: List<ca.saboor.larpdot.ui.overlay.IslandPriorityItem>) =
        ActivityAndBlacklistPrefsStore.setIslandPriorityOrder(getPrefs(context), order)
}
