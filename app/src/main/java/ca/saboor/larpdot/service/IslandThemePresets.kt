package ca.saboor.larpdot.service

import android.content.Context
import ca.saboor.larpdot.flashlight.StrobeSpeed
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.ui.overlay.DEFAULT_ISLAND_PRIORITY_ORDER
import ca.saboor.larpdot.ui.overlay.IslandPriorityItem
import ca.saboor.larpdot.ui.overlay.SwipeDownSensitivity
import ca.saboor.larpdot.ui.overlay.normalizeIslandPriorityOrder

data class IslandBackupConfig(
    val bubbleStyle: OverlayPreferences.BubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
    val smallerBubbles: Boolean = false,
    val minimizedMaxWidthPercent: Float = 72f,
    val islandCornerRadius: Float = 36f,
    val islandSquircleEnabled: Boolean = true,
    val expandedBackgroundOpacity: Float = 1f,
    val backdropBlurEnabled: Boolean = false,
    val backdropBlurRadius: Float = 28f,
    val cameraCoverStyle: OverlayPreferences.CameraCoverStyle = OverlayPreferences.CameraCoverStyle.GRADIENT,
    val islandFontStyle: OverlayPreferences.IslandFontStyle = OverlayPreferences.IslandFontStyle.DEFAULT,
    val islandFontWeight: Int = 500,
    val minimizedAlbumArtStyle: OverlayPreferences.AlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
    val minimizedAlbumArtShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.CIRCLE,
    val minimizedAlbumArtRotation: Float = 0f,
    val minimizedNestedBlurredBg: Boolean = false,
    val expandedAlbumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    val showExpandedAlbumArt: Boolean = true,
    val expandedAlbumArtShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
    val expandedAlbumArtRotation: Float = 0f,
    val expandedPlayerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
    val showProgressSquiggles: Boolean = true,
    val progressBarAccentColor: Boolean = false,
    val progressTimestampPlacement: OverlayPreferences.ProgressTimestampPlacement =
        OverlayPreferences.ProgressTimestampPlacement.legacyDefault(expandedPlayerLayout),
    val playbackPlayPauseBg: Boolean = true,
    val playbackSkipBg: Boolean = false,
    val playbackCornerRadiusDp: Float = 22f,
    val playbackM3ButtonGroup: Boolean = false,
    val playbackPlayPauseAccent: Boolean = false,
    val trackInfoShowTitle: Boolean = true,
    val trackInfoShowArtist: Boolean = true,
    val trackInfoShowAlbum: Boolean = false,
    val trackInfoAlbumBesideArtist: Boolean = false,
    val trackInfoTitleAccent: Boolean = false,
    val showMinimizedTitle: Boolean = false,
    val showMinimizedVisualizer: Boolean = true,
    val showMinimizedProgressOutline: Boolean = true,
    val showExpandedProgressOutline: Boolean = true,
    val albumArtGlowEnabled: Boolean = true,
    val showMinimizedDominantGlow: Boolean = true,
    val showExpandedDominantGlow: Boolean = true,
    val expandedGlowLeft: Boolean = true,
    val expandedGlowRight: Boolean = true,
    val expandedGlowTop: Boolean = false,
    val expandedGlowBottom: Boolean = true,
    val bpmPulseEnabled: Boolean = true,
    val minimizedBpmPulseEnabled: Boolean = true,
    val expandedBpmPulseEnabled: Boolean = true,
    val pulseAmount: Float = 1f,
    val pulseEntireIslandEnabled: Boolean = false,
    val waveformBandCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT,
    val waveformBarWidth: Float = 2.5f,
    val waveformBarSpacing: Float = 1.5f,
    val showMinimizedFlashlightOutline: Boolean = true,
    val showExpandedFlashlightOutline: Boolean = true,
    val flashlightExpandedStyle: OverlayPreferences.FlashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
    val flashlightBeamBlur: Boolean = false,
    val flashlightBeamAccentColor: Boolean = true,
    val flashlightTapToToggle: Boolean = true,
    val flashlightAutoOffMinutes: Int = 0,
    val flashlightStrobeSpeed: StrobeSpeed = StrobeSpeed.NORMAL,
    val musicPauseTimeoutSeconds: Int = OverlayPreferences.DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS,
    val musicQuickSeekSeconds: Int = OverlayPreferences.DEFAULT_MUSIC_QUICK_SEEK_SECONDS,
    val tapToExpand: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val expandedAutoCollapseSeconds: Int = OverlayPreferences.DEFAULT_EXPANDED_AUTO_COLLAPSE_SECONDS,
    val holdToExpandDurationMs: Int = OverlayPreferences.DEFAULT_HOLD_TO_EXPAND_DURATION_MS,
    val swipeDownSensitivity: SwipeDownSensitivity = SwipeDownSensitivity.BALANCED,
    val hideWhenScreenOff: Boolean = true,
    val hideOnLockScreen: Boolean = false,
    val hideInFullscreen: Boolean = false,
    val hideInLandscape: Boolean = false,
    val hideInDnd: Boolean = false,
    val hideMusicWhenAppOpen: Boolean = true,
    val showSongAnnouncement: Boolean = true,
    val expandedShowTrackInfo: Boolean = true,
    val expandedShowProgress: Boolean = true,
    val expandedShowMainControls: Boolean = true,
    val expandedShowAppActions: Boolean = true,
    val expandedShowVisualizer: Boolean = true,
    val expandedShowBpm: Boolean = true,
    val visualizerMode: OverlayPreferences.VisualizerMode = OverlayPreferences.VisualizerMode.SIMULATED,
    val previewAudioSource: OverlayPreferences.PreviewAudioSource = OverlayPreferences.PreviewAudioSource.AUTO,
    val activitiesEnabled: Boolean = true,
    val activityProgress: Boolean = true,
    val activityNavigation: Boolean = true,
    val activityCalls: Boolean = true,
    val activityTimers: Boolean = true,
    val activityBatteryCharging: Boolean = true,
    val activityBatteryLow: Boolean = true,
    val activitySoundMode: Boolean = true,
    val activityDnd: Boolean = true,
    val activityVolumeHud: Boolean = false,
    val activityBluetooth: Boolean = true,
    val activityAudioOutput: Boolean = true,
    val activityScreenshotPocket: Boolean = true,
    val activityChargeLimitAlert: Boolean = false,
    val activityChargeLimitPercent: Int = 80,
    val activityPermanentCharging: Boolean = false,
    val activityPermanentLowBattery: Boolean = false,
    val activityPermanentSoundMode: Boolean = false,
    val activityPermanentDnd: Boolean = false,
    val activityPermanentBluetooth: Boolean = false,
    val activityPermanentAudioOutput: Boolean = false,
    val activityPermanentScreenshotPocket: Boolean = false,
    val activityExpandShortTerm: Boolean = false,
    val activityProgressOutline: Boolean = true,
    val islandPriorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
    val cutoutManualEnabled: Boolean? = null,
    val cutoutOffsetX: Float? = null,
    val cutoutOffsetY: Float? = null,
    val cutoutDiameterDp: Float? = null,
)

data class IslandThemePreset(
    val id: String,
    val name: String,
    val tagline: String,
    val style: IslandBackupConfig,
)

object IslandThemePresets {
    private const val BACKUP_VERSION = 4

    val builtInPresets: List<IslandThemePreset> = listOf(
        IslandThemePreset(
            id = "m3_expressive",
            name = "Material 3",
            tagline = "Squircle bubbles, rounded font & M3 controls",
            style = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                islandCornerRadius = 36f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.GRADIENT,
                islandFontStyle = OverlayPreferences.IslandFontStyle.ROUNDED,
                islandFontWeight = 500,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
                minimizedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.COOKIE,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
                expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
                expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE,
                showProgressSquiggles = true,
                progressBarAccentColor = true,
                progressTimestampPlacement = OverlayPreferences.ProgressTimestampPlacement.BELOW,
                playbackPlayPauseBg = true,
                playbackM3ButtonGroup = true,
                playbackPlayPauseAccent = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = true,
                flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
            ),
        ),
        IslandThemePreset(
            id = "classic_ios",
            name = "Classic Pill",
            tagline = "Clean black capsule, straight bar & side times",
            style = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.IPHONE,
                smallerBubbles = false,
                islandCornerRadius = 44f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.NONE,
                islandFontStyle = OverlayPreferences.IslandFontStyle.DEFAULT,
                islandFontWeight = 500,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
                minimizedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
                expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
                expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.IPHONE,
                showProgressSquiggles = false,
                progressBarAccentColor = false,
                progressTimestampPlacement = OverlayPreferences.ProgressTimestampPlacement.BESIDE,
                playbackPlayPauseBg = false,
                playbackSkipBg = false,
                playbackM3ButtonGroup = false,
                showMinimizedDominantGlow = false,
                showExpandedDominantGlow = false,
                minimizedBpmPulseEnabled = false,
                expandedBpmPulseEnabled = false,
                flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
            ),
        ),
        IslandThemePreset(
            id = "frosted_glass",
            name = "Frosted Glass",
            tagline = "Translucent blurred card with soft rounded corners",
            style = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                islandCornerRadius = 34f,
                islandSquircleEnabled = false,
                expandedBackgroundOpacity = 0.58f,
                backdropBlurEnabled = true,
                backdropBlurRadius = 34f,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.GRADIENT,
                islandFontStyle = OverlayPreferences.IslandFontStyle.DEFAULT,
                islandFontWeight = 500,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.BLENDED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
                expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
                expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
                showProgressSquiggles = true,
                progressBarAccentColor = true,
                progressTimestampPlacement = OverlayPreferences.ProgressTimestampPlacement.HIDDEN,
                playbackPlayPauseBg = true,
                playbackPlayPauseAccent = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = true,
                flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.SLIDER,
            ),
        ),
        IslandThemePreset(
            id = "vibrant_pulse",
            name = "Vibrant Pulse",
            tagline = "Full-bleed art, 4-side glow & beat-synced pulse",
            style = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                islandCornerRadius = 40f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.CANOPY,
                islandFontStyle = OverlayPreferences.IslandFontStyle.ROUNDED,
                islandFontWeight = 600,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
                expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.CLOVER,
                expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE,
                showProgressSquiggles = true,
                progressBarAccentColor = true,
                playbackPlayPauseBg = true,
                playbackPlayPauseAccent = true,
                playbackM3ButtonGroup = true,
                trackInfoTitleAccent = true,
                albumArtGlowEnabled = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = true,
                expandedGlowLeft = true,
                expandedGlowRight = true,
                expandedGlowTop = true,
                expandedGlowBottom = true,
                bpmPulseEnabled = true,
                minimizedBpmPulseEnabled = true,
                expandedBpmPulseEnabled = true,
                pulseAmount = 1.35f,
                pulseEntireIslandEnabled = true,
                waveformBandCount = 8,
                flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
                flashlightBeamBlur = true,
            ),
        ),
        IslandThemePreset(
            id = "minimal_matte",
            name = "Minimal Matte",
            tagline = "Compact bubbles, condensed type & zero glow",
            style = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.IPHONE,
                smallerBubbles = true,
                islandCornerRadius = 28f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.SWOOP,
                islandFontStyle = OverlayPreferences.IslandFontStyle.CONDENSED,
                islandFontWeight = 500,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.BASIC_FADED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
                expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
                expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
                showProgressSquiggles = false,
                progressBarAccentColor = false,
                progressTimestampPlacement = OverlayPreferences.ProgressTimestampPlacement.HIDDEN,
                playbackPlayPauseBg = true,
                playbackPlayPauseAccent = false,
                albumArtGlowEnabled = false,
                showMinimizedDominantGlow = false,
                showExpandedDominantGlow = false,
                bpmPulseEnabled = false,
                minimizedBpmPulseEnabled = false,
                expandedBpmPulseEnabled = false,
                pulseEntireIslandEnabled = false,
                flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.SLIDER,
            ),
        ),
    )

    /** Applies only visual theme fields, preserving cutout calibration, notification toggles, and priority order. */
    fun applyThemePreset(context: Context, preset: IslandThemePreset) {
        applyVisualAppearance(context, preset.style)
    }

    private fun applyVisualAppearance(context: Context, config: IslandBackupConfig) {
        OverlayPreferences.setBubbleStyle(context, config.bubbleStyle)
        OverlayPreferences.setSmallerBubblesEnabled(context, config.smallerBubbles)
        OverlayPreferences.setIslandCornerRadius(context, config.islandCornerRadius)
        OverlayPreferences.setIslandSquircleEnabled(context, config.islandSquircleEnabled)
        OverlayPreferences.setExpandedBackgroundOpacity(context, config.expandedBackgroundOpacity)
        OverlayPreferences.setBackdropBlurEnabled(context, config.backdropBlurEnabled)
        OverlayPreferences.setBackdropBlurRadius(context, config.backdropBlurRadius)
        OverlayPreferences.setCameraCoverStyle(context, config.cameraCoverStyle)
        OverlayPreferences.setIslandFontStyle(context, config.islandFontStyle)
        OverlayPreferences.setIslandFontWeight(context, config.islandFontWeight)
        OverlayPreferences.setMinimizedAlbumArtStyle(context, config.minimizedAlbumArtStyle)
        OverlayPreferences.setMinimizedAlbumArtShape(context, config.minimizedAlbumArtShape)
        OverlayPreferences.setMinimizedAlbumArtRotation(context, config.minimizedAlbumArtRotation)
        OverlayPreferences.setMinimizedNestedBlurredBgEnabled(context, config.minimizedNestedBlurredBg)
        OverlayPreferences.setExpandedAlbumArtStyle(context, config.expandedAlbumArtStyle)
        OverlayPreferences.setShowExpandedAlbumArtEnabled(context, config.showExpandedAlbumArt)
        OverlayPreferences.setExpandedAlbumArtShape(context, config.expandedAlbumArtShape)
        OverlayPreferences.setExpandedAlbumArtRotation(context, config.expandedAlbumArtRotation)
        OverlayPreferences.setExpandedPlayerLayout(context, config.expandedPlayerLayout)
        OverlayPreferences.setShowProgressSquigglesEnabled(context, config.showProgressSquiggles)
        OverlayPreferences.setProgressBarAccentColorEnabled(context, config.progressBarAccentColor)
        OverlayPreferences.setProgressTimestampPlacement(context, config.progressTimestampPlacement)
        OverlayPreferences.setPlaybackControlsStyle(
            context,
            OverlayPreferences.PlaybackControlsStyle(
                playPauseBackground = config.playbackPlayPauseBg,
                skipBackground = config.playbackSkipBg,
                cornerRadiusDp = config.playbackCornerRadiusDp,
                material3ButtonGroup = config.playbackM3ButtonGroup,
                playPauseAccentColor = config.playbackPlayPauseAccent,
            ),
        )
        OverlayPreferences.setTrackInfoStyle(
            context,
            OverlayPreferences.TrackInfoStyle(
                showTitle = config.trackInfoShowTitle,
                showArtist = config.trackInfoShowArtist,
                showAlbum = config.trackInfoShowAlbum,
                albumBesideArtist = config.trackInfoAlbumBesideArtist,
                titleAccentColor = config.trackInfoTitleAccent,
            ),
        )
        OverlayPreferences.setShowMinimizedTitleEnabled(context, config.showMinimizedTitle)
        OverlayPreferences.setShowMinimizedVisualizerEnabled(context, config.showMinimizedVisualizer)
        OverlayPreferences.setShowMinimizedProgressOutlineEnabled(context, config.showMinimizedProgressOutline)
        OverlayPreferences.setShowExpandedProgressOutlineEnabled(context, config.showExpandedProgressOutline)
        OverlayPreferences.setAlbumArtGlowEnabled(context, config.albumArtGlowEnabled)
        OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, config.showMinimizedDominantGlow)
        OverlayPreferences.setShowExpandedDominantColorGlowEnabled(context, config.showExpandedDominantGlow)
        OverlayPreferences.setExpandedGlowSides(
            context,
            OverlayPreferences.ExpandedGlowSides(
                left = config.expandedGlowLeft,
                right = config.expandedGlowRight,
                top = config.expandedGlowTop,
                bottom = config.expandedGlowBottom,
            ),
        )
        OverlayPreferences.setMinimizedBpmPulseEnabled(context, config.minimizedBpmPulseEnabled)
        OverlayPreferences.setExpandedBpmPulseEnabled(context, config.expandedBpmPulseEnabled)
        OverlayPreferences.setPulseAmount(context, config.pulseAmount)
        OverlayPreferences.setPulseEntireIslandEnabled(context, config.pulseEntireIslandEnabled)
        OverlayPreferences.setWaveformBandCount(context, config.waveformBandCount)
        OverlayPreferences.setWaveformBarWidth(context, config.waveformBarWidth)
        OverlayPreferences.setWaveformBarSpacing(context, config.waveformBarSpacing)
        OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, config.showMinimizedFlashlightOutline)
        OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, config.showExpandedFlashlightOutline)
        OverlayPreferences.setFlashlightExpandedStyle(context, config.flashlightExpandedStyle)
        OverlayPreferences.setFlashlightBeamBlurEnabled(context, config.flashlightBeamBlur)
        OverlayPreferences.setFlashlightBeamAccentColorEnabled(context, config.flashlightBeamAccentColor)
    }

    private val KNOWN_BACKUP_KEYS: Set<String> by lazy {
        parseFlatJsonObject(toJson(IslandBackupConfig()))?.keys.orEmpty() + setOf(
            "cutoutManualEnabled",
            "cutoutOffsetX",
            "cutoutOffsetY",
            "cutoutDiameterDp",
        )
    }

    fun toJson(config: IslandBackupConfig): String {
        val entries = buildList<Pair<String, String>> {
            add("version" to BACKUP_VERSION.toString())
            add("bubbleStyle" to quote(config.bubbleStyle.name))
            add("smallerBubbles" to config.smallerBubbles.toString())
            add("minimizedMaxWidthPercent" to config.minimizedMaxWidthPercent.toString())
            add("islandCornerRadius" to config.islandCornerRadius.toString())
            add("islandSquircleEnabled" to config.islandSquircleEnabled.toString())
            add("expandedBackgroundOpacity" to config.expandedBackgroundOpacity.toString())
            add("backdropBlurEnabled" to config.backdropBlurEnabled.toString())
            add("backdropBlurRadius" to config.backdropBlurRadius.toString())
            add("cameraCoverStyle" to quote(config.cameraCoverStyle.name))
            add("islandFontStyle" to quote(config.islandFontStyle.name))
            add("islandFontWeight" to config.islandFontWeight.toString())
            add("minimizedAlbumArtStyle" to quote(config.minimizedAlbumArtStyle.name))
            add("minimizedAlbumArtShape" to quote(config.minimizedAlbumArtShape.name))
            add("minimizedAlbumArtRotation" to config.minimizedAlbumArtRotation.toString())
            add("minimizedNestedBlurredBg" to config.minimizedNestedBlurredBg.toString())
            add("expandedAlbumArtStyle" to quote(config.expandedAlbumArtStyle.name))
            add("showExpandedAlbumArt" to config.showExpandedAlbumArt.toString())
            add("expandedAlbumArtShape" to quote(config.expandedAlbumArtShape.name))
            add("expandedAlbumArtRotation" to config.expandedAlbumArtRotation.toString())
            add("expandedPlayerLayout" to quote(config.expandedPlayerLayout.name))
            add("showProgressSquiggles" to config.showProgressSquiggles.toString())
            add("progressBarAccentColor" to config.progressBarAccentColor.toString())
            add("progressTimestampPlacement" to quote(config.progressTimestampPlacement.name))
            add("playbackPlayPauseBg" to config.playbackPlayPauseBg.toString())
            add("playbackSkipBg" to config.playbackSkipBg.toString())
            add("playbackCornerRadiusDp" to config.playbackCornerRadiusDp.toString())
            add("playbackM3ButtonGroup" to config.playbackM3ButtonGroup.toString())
            add("playbackPlayPauseAccent" to config.playbackPlayPauseAccent.toString())
            add("trackInfoShowTitle" to config.trackInfoShowTitle.toString())
            add("trackInfoShowArtist" to config.trackInfoShowArtist.toString())
            add("trackInfoShowAlbum" to config.trackInfoShowAlbum.toString())
            add("trackInfoAlbumBesideArtist" to config.trackInfoAlbumBesideArtist.toString())
            add("trackInfoTitleAccent" to config.trackInfoTitleAccent.toString())
            add("showMinimizedTitle" to config.showMinimizedTitle.toString())
            add("showMinimizedVisualizer" to config.showMinimizedVisualizer.toString())
            add("showMinimizedProgressOutline" to config.showMinimizedProgressOutline.toString())
            add("showExpandedProgressOutline" to config.showExpandedProgressOutline.toString())
            add("albumArtGlowEnabled" to config.albumArtGlowEnabled.toString())
            add("showMinimizedDominantGlow" to config.showMinimizedDominantGlow.toString())
            add("showExpandedDominantGlow" to config.showExpandedDominantGlow.toString())
            add("expandedGlowLeft" to config.expandedGlowLeft.toString())
            add("expandedGlowRight" to config.expandedGlowRight.toString())
            add("expandedGlowTop" to config.expandedGlowTop.toString())
            add("expandedGlowBottom" to config.expandedGlowBottom.toString())
            add("bpmPulseEnabled" to config.bpmPulseEnabled.toString())
            add("minimizedBpmPulseEnabled" to config.minimizedBpmPulseEnabled.toString())
            add("expandedBpmPulseEnabled" to config.expandedBpmPulseEnabled.toString())
            add("pulseAmount" to config.pulseAmount.toString())
            add("pulseEntireIslandEnabled" to config.pulseEntireIslandEnabled.toString())
            add("waveformBandCount" to config.waveformBandCount.toString())
            add("waveformBarWidth" to config.waveformBarWidth.toString())
            add("waveformBarSpacing" to config.waveformBarSpacing.toString())
            add("showMinimizedFlashlightOutline" to config.showMinimizedFlashlightOutline.toString())
            add("showExpandedFlashlightOutline" to config.showExpandedFlashlightOutline.toString())
            add("flashlightExpandedStyle" to quote(config.flashlightExpandedStyle.name))
            add("flashlightBeamBlur" to config.flashlightBeamBlur.toString())
            add("flashlightBeamAccentColor" to config.flashlightBeamAccentColor.toString())
            add("flashlightTapToToggle" to config.flashlightTapToToggle.toString())
            add("flashlightAutoOffMinutes" to config.flashlightAutoOffMinutes.toString())
            add("flashlightStrobeSpeed" to quote(config.flashlightStrobeSpeed.name))
            add("musicPauseTimeoutSeconds" to config.musicPauseTimeoutSeconds.toString())
            add("musicQuickSeekSeconds" to config.musicQuickSeekSeconds.toString())
            add("tapToExpand" to config.tapToExpand.toString())
            add("hapticsEnabled" to config.hapticsEnabled.toString())
            add("expandedAutoCollapseSeconds" to config.expandedAutoCollapseSeconds.toString())
            add("holdToExpandDurationMs" to config.holdToExpandDurationMs.toString())
            add("swipeDownSensitivity" to quote(config.swipeDownSensitivity.name))
            add("hideWhenScreenOff" to config.hideWhenScreenOff.toString())
            add("hideOnLockScreen" to config.hideOnLockScreen.toString())
            add("hideInFullscreen" to config.hideInFullscreen.toString())
            add("hideInLandscape" to config.hideInLandscape.toString())
            add("hideInDnd" to config.hideInDnd.toString())
            add("hideMusicWhenAppOpen" to config.hideMusicWhenAppOpen.toString())
            add("showSongAnnouncement" to config.showSongAnnouncement.toString())
            add("expandedShowTrackInfo" to config.expandedShowTrackInfo.toString())
            add("expandedShowProgress" to config.expandedShowProgress.toString())
            add("expandedShowMainControls" to config.expandedShowMainControls.toString())
            add("expandedShowAppActions" to config.expandedShowAppActions.toString())
            add("expandedShowVisualizer" to config.expandedShowVisualizer.toString())
            add("expandedShowBpm" to config.expandedShowBpm.toString())
            add("visualizerMode" to quote(config.visualizerMode.name))
            add("previewAudioSource" to quote(config.previewAudioSource.name))
            add("activitiesEnabled" to config.activitiesEnabled.toString())
            add("activityProgress" to config.activityProgress.toString())
            add("activityNavigation" to config.activityNavigation.toString())
            add("activityCalls" to config.activityCalls.toString())
            add("activityTimers" to config.activityTimers.toString())
            add("activityBatteryCharging" to config.activityBatteryCharging.toString())
            add("activityBatteryLow" to config.activityBatteryLow.toString())
            add("activitySoundMode" to config.activitySoundMode.toString())
            add("activityDnd" to config.activityDnd.toString())
            add("activityVolumeHud" to config.activityVolumeHud.toString())
            add("activityBluetooth" to config.activityBluetooth.toString())
            add("activityAudioOutput" to config.activityAudioOutput.toString())
            add("activityScreenshotPocket" to config.activityScreenshotPocket.toString())
            add("activityChargeLimitAlert" to config.activityChargeLimitAlert.toString())
            add("activityChargeLimitPercent" to config.activityChargeLimitPercent.toString())
            add("activityPermanentCharging" to config.activityPermanentCharging.toString())
            add("activityPermanentLowBattery" to config.activityPermanentLowBattery.toString())
            add("activityPermanentSoundMode" to config.activityPermanentSoundMode.toString())
            add("activityPermanentDnd" to config.activityPermanentDnd.toString())
            add("activityPermanentBluetooth" to config.activityPermanentBluetooth.toString())
            add("activityPermanentAudioOutput" to config.activityPermanentAudioOutput.toString())
            add("activityPermanentScreenshotPocket" to config.activityPermanentScreenshotPocket.toString())
            add("activityExpandShortTerm" to config.activityExpandShortTerm.toString())
            add("activityProgressOutline" to config.activityProgressOutline.toString())
            add("islandPriorityOrder" to quote(normalizeIslandPriorityOrder(config.islandPriorityOrder).joinToString(",") { it.name }))
            config.cutoutManualEnabled?.let { add("cutoutManualEnabled" to it.toString()) }
            config.cutoutOffsetX?.let { add("cutoutOffsetX" to it.toString()) }
            config.cutoutOffsetY?.let { add("cutoutOffsetY" to it.toString()) }
            config.cutoutDiameterDp?.let { add("cutoutDiameterDp" to it.toString()) }
        }
        return entries.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n}",
        ) { (key, value) -> "  \"$key\": $value" }
    }

    fun fromJson(raw: String): IslandBackupConfig? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val fields = parseFlatJsonObject(trimmed) ?: return null
        if (fields.keys.none { it in KNOWN_BACKUP_KEYS }) return null
        val defaults = IslandBackupConfig()
        val expandedPlayerLayout = enumOrDefault(fields["expandedPlayerLayout"], defaults.expandedPlayerLayout)
        val legacyBpmPulse = fields["bpmPulseEnabled"]?.toBooleanStrictOrNull() ?: defaults.bpmPulseEnabled
        val rawPriority = fields["islandPriorityOrder"]
        val parsedPriority = if (!rawPriority.isNullOrBlank()) {
            normalizeIslandPriorityOrder(
                rawPriority.split(",").mapNotNull(IslandPriorityItem::fromName),
            )
        } else {
            defaults.islandPriorityOrder
        }
        return IslandBackupConfig(
            bubbleStyle = enumOrDefault(fields["bubbleStyle"], defaults.bubbleStyle),
            smallerBubbles = fields["smallerBubbles"]?.toBooleanStrictOrNull() ?: defaults.smallerBubbles,
            minimizedMaxWidthPercent = (fields["minimizedMaxWidthPercent"]?.toFloatOrNull() ?: defaults.minimizedMaxWidthPercent).coerceIn(40f, 100f),
            islandCornerRadius = (fields["islandCornerRadius"]?.toFloatOrNull() ?: defaults.islandCornerRadius).coerceIn(0f, 100f),
            islandSquircleEnabled = fields["islandSquircleEnabled"]?.toBooleanStrictOrNull() ?: defaults.islandSquircleEnabled,
            expandedBackgroundOpacity = (fields["expandedBackgroundOpacity"]?.toFloatOrNull() ?: defaults.expandedBackgroundOpacity).coerceIn(0f, 1f),
            backdropBlurEnabled = fields["backdropBlurEnabled"]?.toBooleanStrictOrNull() ?: defaults.backdropBlurEnabled,
            backdropBlurRadius = (fields["backdropBlurRadius"]?.toFloatOrNull() ?: defaults.backdropBlurRadius).coerceIn(5f, 120f),
            cameraCoverStyle = enumOrDefault(fields["cameraCoverStyle"], defaults.cameraCoverStyle),
            islandFontStyle = enumOrDefault(fields["islandFontStyle"], defaults.islandFontStyle),
            islandFontWeight = (fields["islandFontWeight"]?.toIntOrNull() ?: defaults.islandFontWeight).coerceIn(100, 900),
            minimizedAlbumArtStyle = enumOrDefault(fields["minimizedAlbumArtStyle"], defaults.minimizedAlbumArtStyle),
            minimizedAlbumArtShape = enumOrDefault(fields["minimizedAlbumArtShape"], defaults.minimizedAlbumArtShape),
            minimizedAlbumArtRotation = (fields["minimizedAlbumArtRotation"]?.toFloatOrNull() ?: defaults.minimizedAlbumArtRotation).coerceIn(0f, 360f),
            minimizedNestedBlurredBg = fields["minimizedNestedBlurredBg"]?.toBooleanStrictOrNull() ?: defaults.minimizedNestedBlurredBg,
            expandedAlbumArtStyle = enumOrDefault(fields["expandedAlbumArtStyle"], defaults.expandedAlbumArtStyle),
            showExpandedAlbumArt = fields["showExpandedAlbumArt"]?.toBooleanStrictOrNull() ?: defaults.showExpandedAlbumArt,
            expandedAlbumArtShape = enumOrDefault(fields["expandedAlbumArtShape"], defaults.expandedAlbumArtShape),
            expandedAlbumArtRotation = (fields["expandedAlbumArtRotation"]?.toFloatOrNull() ?: defaults.expandedAlbumArtRotation).coerceIn(0f, 360f),
            expandedPlayerLayout = expandedPlayerLayout,
            showProgressSquiggles = fields["showProgressSquiggles"]?.toBooleanStrictOrNull() ?: defaults.showProgressSquiggles,
            progressBarAccentColor = fields["progressBarAccentColor"]?.toBooleanStrictOrNull() ?: defaults.progressBarAccentColor,
            progressTimestampPlacement = enumOrDefault(
                fields["progressTimestampPlacement"],
                OverlayPreferences.ProgressTimestampPlacement.legacyDefault(expandedPlayerLayout),
            ),
            playbackPlayPauseBg = fields["playbackPlayPauseBg"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseBg,
            playbackSkipBg = fields["playbackSkipBg"]?.toBooleanStrictOrNull() ?: defaults.playbackSkipBg,
            playbackCornerRadiusDp = (fields["playbackCornerRadiusDp"]?.toFloatOrNull() ?: defaults.playbackCornerRadiusDp).coerceIn(0f, 32f),
            playbackM3ButtonGroup = fields["playbackM3ButtonGroup"]?.toBooleanStrictOrNull() ?: defaults.playbackM3ButtonGroup,
            playbackPlayPauseAccent = fields["playbackPlayPauseAccent"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseAccent,
            trackInfoShowTitle = fields["trackInfoShowTitle"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowTitle,
            trackInfoShowArtist = fields["trackInfoShowArtist"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowArtist,
            trackInfoShowAlbum = fields["trackInfoShowAlbum"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowAlbum,
            trackInfoAlbumBesideArtist = fields["trackInfoAlbumBesideArtist"]?.toBooleanStrictOrNull() ?: defaults.trackInfoAlbumBesideArtist,
            trackInfoTitleAccent = fields["trackInfoTitleAccent"]?.toBooleanStrictOrNull() ?: defaults.trackInfoTitleAccent,
            showMinimizedTitle = fields["showMinimizedTitle"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedTitle,
            showMinimizedVisualizer = fields["showMinimizedVisualizer"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedVisualizer,
            showMinimizedProgressOutline = fields["showMinimizedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedProgressOutline,
            showExpandedProgressOutline = fields["showExpandedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedProgressOutline,
            albumArtGlowEnabled = fields["albumArtGlowEnabled"]?.toBooleanStrictOrNull() ?: defaults.albumArtGlowEnabled,
            showMinimizedDominantGlow = fields["showMinimizedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedDominantGlow,
            showExpandedDominantGlow = fields["showExpandedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showExpandedDominantGlow,
            expandedGlowLeft = fields["expandedGlowLeft"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowLeft,
            expandedGlowRight = fields["expandedGlowRight"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowRight,
            expandedGlowTop = fields["expandedGlowTop"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowTop,
            expandedGlowBottom = fields["expandedGlowBottom"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowBottom,
            bpmPulseEnabled = legacyBpmPulse,
            minimizedBpmPulseEnabled = fields["minimizedBpmPulseEnabled"]?.toBooleanStrictOrNull() ?: legacyBpmPulse,
            expandedBpmPulseEnabled = fields["expandedBpmPulseEnabled"]?.toBooleanStrictOrNull() ?: legacyBpmPulse,
            pulseAmount = (fields["pulseAmount"]?.toFloatOrNull() ?: defaults.pulseAmount).coerceIn(0.25f, 3.0f),
            pulseEntireIslandEnabled = fields["pulseEntireIslandEnabled"]?.toBooleanStrictOrNull() ?: defaults.pulseEntireIslandEnabled,
            waveformBandCount = (fields["waveformBandCount"]?.toIntOrNull() ?: defaults.waveformBandCount).coerceIn(0, 30),
            waveformBarWidth = (fields["waveformBarWidth"]?.toFloatOrNull() ?: defaults.waveformBarWidth).coerceIn(0.5f, 6f),
            waveformBarSpacing = (fields["waveformBarSpacing"]?.toFloatOrNull() ?: defaults.waveformBarSpacing).coerceIn(0f, 8f),
            showMinimizedFlashlightOutline = fields["showMinimizedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedFlashlightOutline,
            showExpandedFlashlightOutline = fields["showExpandedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedFlashlightOutline,
            flashlightExpandedStyle = enumOrDefault(fields["flashlightExpandedStyle"], defaults.flashlightExpandedStyle),
            flashlightBeamBlur = fields["flashlightBeamBlur"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamBlur,
            flashlightBeamAccentColor = fields["flashlightBeamAccentColor"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamAccentColor,
            flashlightTapToToggle = fields["flashlightTapToToggle"]?.toBooleanStrictOrNull() ?: defaults.flashlightTapToToggle,
            flashlightAutoOffMinutes = (fields["flashlightAutoOffMinutes"]?.toIntOrNull() ?: defaults.flashlightAutoOffMinutes).coerceIn(0, 60),
            flashlightStrobeSpeed = enumOrDefault(fields["flashlightStrobeSpeed"], defaults.flashlightStrobeSpeed),
            musicPauseTimeoutSeconds = (fields["musicPauseTimeoutSeconds"]?.toIntOrNull() ?: defaults.musicPauseTimeoutSeconds).coerceIn(0, 300),
            musicQuickSeekSeconds = (fields["musicQuickSeekSeconds"]?.toIntOrNull() ?: defaults.musicQuickSeekSeconds).coerceIn(5, 60),
            tapToExpand = fields["tapToExpand"]?.toBooleanStrictOrNull() ?: defaults.tapToExpand,
            hapticsEnabled = fields["hapticsEnabled"]?.toBooleanStrictOrNull() ?: defaults.hapticsEnabled,
            expandedAutoCollapseSeconds = (fields["expandedAutoCollapseSeconds"]?.toIntOrNull()
                ?: defaults.expandedAutoCollapseSeconds).coerceIn(0, 120),
            holdToExpandDurationMs = (fields["holdToExpandDurationMs"]?.toIntOrNull() ?: defaults.holdToExpandDurationMs)
                .coerceIn(OverlayPreferences.MIN_HOLD_TO_EXPAND_DURATION_MS, OverlayPreferences.MAX_HOLD_TO_EXPAND_DURATION_MS),
            swipeDownSensitivity = enumOrDefault(fields["swipeDownSensitivity"], defaults.swipeDownSensitivity),
            hideWhenScreenOff = fields["hideWhenScreenOff"]?.toBooleanStrictOrNull() ?: defaults.hideWhenScreenOff,
            hideOnLockScreen = fields["hideOnLockScreen"]?.toBooleanStrictOrNull() ?: defaults.hideOnLockScreen,
            hideInFullscreen = fields["hideInFullscreen"]?.toBooleanStrictOrNull() ?: defaults.hideInFullscreen,
            hideInLandscape = fields["hideInLandscape"]?.toBooleanStrictOrNull() ?: defaults.hideInLandscape,
            hideInDnd = fields["hideInDnd"]?.toBooleanStrictOrNull() ?: defaults.hideInDnd,
            hideMusicWhenAppOpen = fields["hideMusicWhenAppOpen"]?.toBooleanStrictOrNull() ?: defaults.hideMusicWhenAppOpen,
            showSongAnnouncement = fields["showSongAnnouncement"]?.toBooleanStrictOrNull() ?: defaults.showSongAnnouncement,
            expandedShowTrackInfo = fields["expandedShowTrackInfo"]?.toBooleanStrictOrNull() ?: defaults.expandedShowTrackInfo,
            expandedShowProgress = fields["expandedShowProgress"]?.toBooleanStrictOrNull() ?: defaults.expandedShowProgress,
            expandedShowMainControls = fields["expandedShowMainControls"]?.toBooleanStrictOrNull() ?: defaults.expandedShowMainControls,
            expandedShowAppActions = fields["expandedShowAppActions"]?.toBooleanStrictOrNull() ?: defaults.expandedShowAppActions,
            expandedShowVisualizer = fields["expandedShowVisualizer"]?.toBooleanStrictOrNull() ?: defaults.expandedShowVisualizer,
            expandedShowBpm = fields["expandedShowBpm"]?.toBooleanStrictOrNull() ?: defaults.expandedShowBpm,
            visualizerMode = enumOrDefault(fields["visualizerMode"], defaults.visualizerMode),
            previewAudioSource = enumOrDefault(fields["previewAudioSource"], defaults.previewAudioSource),
            activitiesEnabled = fields["activitiesEnabled"]?.toBooleanStrictOrNull() ?: defaults.activitiesEnabled,
            activityProgress = fields["activityProgress"]?.toBooleanStrictOrNull() ?: defaults.activityProgress,
            activityNavigation = fields["activityNavigation"]?.toBooleanStrictOrNull() ?: defaults.activityNavigation,
            activityCalls = fields["activityCalls"]?.toBooleanStrictOrNull() ?: defaults.activityCalls,
            activityTimers = fields["activityTimers"]?.toBooleanStrictOrNull() ?: defaults.activityTimers,
            activityBatteryCharging = fields["activityBatteryCharging"]?.toBooleanStrictOrNull() ?: defaults.activityBatteryCharging,
            activityBatteryLow = fields["activityBatteryLow"]?.toBooleanStrictOrNull() ?: defaults.activityBatteryLow,
            activitySoundMode = fields["activitySoundMode"]?.toBooleanStrictOrNull() ?: defaults.activitySoundMode,
            activityDnd = fields["activityDnd"]?.toBooleanStrictOrNull() ?: defaults.activityDnd,
            activityVolumeHud = fields["activityVolumeHud"]?.toBooleanStrictOrNull() ?: defaults.activityVolumeHud,
            activityBluetooth = fields["activityBluetooth"]?.toBooleanStrictOrNull() ?: defaults.activityBluetooth,
            activityAudioOutput = fields["activityAudioOutput"]?.toBooleanStrictOrNull() ?: defaults.activityAudioOutput,
            activityScreenshotPocket = fields["activityScreenshotPocket"]?.toBooleanStrictOrNull() ?: defaults.activityScreenshotPocket,
            activityChargeLimitAlert = fields["activityChargeLimitAlert"]?.toBooleanStrictOrNull() ?: defaults.activityChargeLimitAlert,
            activityChargeLimitPercent = (fields["activityChargeLimitPercent"]?.toIntOrNull() ?: defaults.activityChargeLimitPercent).coerceIn(70, 100),
            activityPermanentCharging = fields["activityPermanentCharging"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentCharging,
            activityPermanentLowBattery = fields["activityPermanentLowBattery"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentLowBattery,
            activityPermanentSoundMode = fields["activityPermanentSoundMode"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentSoundMode,
            activityPermanentDnd = fields["activityPermanentDnd"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentDnd,
            activityPermanentBluetooth = fields["activityPermanentBluetooth"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentBluetooth,
            activityPermanentAudioOutput = fields["activityPermanentAudioOutput"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentAudioOutput,
            activityPermanentScreenshotPocket = fields["activityPermanentScreenshotPocket"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentScreenshotPocket,
            activityExpandShortTerm = fields["activityExpandShortTerm"]?.toBooleanStrictOrNull() ?: defaults.activityExpandShortTerm,
            activityProgressOutline = fields["activityProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.activityProgressOutline,
            islandPriorityOrder = parsedPriority,
            cutoutManualEnabled = fields["cutoutManualEnabled"]?.toBooleanStrictOrNull(),
            cutoutOffsetX = fields["cutoutOffsetX"]?.toFloatOrNull(),
            cutoutOffsetY = fields["cutoutOffsetY"]?.toFloatOrNull(),
            cutoutDiameterDp = fields["cutoutDiameterDp"]?.toFloatOrNull(),
        )
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private val flatPairRegex = Regex(""""([^"]+)"\s*:\s*("([^"]*)"|true|false|-?\d+(?:\.\d+)?)""")

    private fun parseFlatJsonObject(raw: String): Map<String, String>? {
        val matches = flatPairRegex.findAll(raw).toList()
        if (matches.isEmpty()) return null
        return buildMap {
            for (match in matches) {
                val key = match.groupValues[1]
                val stringValue = match.groups[3]?.value
                val literalValue = match.groupValues[2]
                put(key, stringValue ?: literalValue)
            }
        }
    }

    fun exportFromPreferences(context: Context): String {
        OverlayPreferences.loadIslandAppearance(context)
        OverlayPreferences.initializeMusicSettings(context)
        val cutout = OverlayPreferences.getCutoutConfig(context)
        val playback = OverlayPreferences.getPlaybackControlsStyle(context)
        val trackInfo = OverlayPreferences.getTrackInfoStyle(context)
        val glowSides = OverlayPreferences.getExpandedGlowSides(context)
        val expandedVisibility = OverlayPreferences.getExpandedElementVisibility(context)
        val activities = OverlayPreferences.getNotificationActivitySettings(context)
        val config = IslandBackupConfig(
            bubbleStyle = OverlayPreferences.getBubbleStyle(context),
            smallerBubbles = OverlayPreferences.areSmallerBubblesEnabled(context),
            minimizedMaxWidthPercent = OverlayPreferences.minimizedMaxWidthPercent.value,
            islandCornerRadius = OverlayPreferences.islandCornerRadius.value,
            islandSquircleEnabled = OverlayPreferences.islandSquircleEnabled.value,
            expandedBackgroundOpacity = OverlayPreferences.expandedBackgroundOpacity.value,
            backdropBlurEnabled = OverlayPreferences.backdropBlurEnabled.value,
            backdropBlurRadius = OverlayPreferences.backdropBlurRadius.value,
            cameraCoverStyle = OverlayPreferences.getCameraCoverStyle(context),
            islandFontStyle = OverlayPreferences.getIslandFontStyle(context),
            islandFontWeight = OverlayPreferences.islandFontWeightFlow.value,
            minimizedAlbumArtStyle = OverlayPreferences.getMinimizedAlbumArtStyle(context),
            minimizedAlbumArtShape = OverlayPreferences.getMinimizedAlbumArtShape(context),
            minimizedAlbumArtRotation = OverlayPreferences.getMinimizedAlbumArtRotation(context),
            minimizedNestedBlurredBg = OverlayPreferences.isMinimizedNestedBlurredBgEnabled(context),
            expandedAlbumArtStyle = OverlayPreferences.getExpandedAlbumArtStyle(context),
            showExpandedAlbumArt = OverlayPreferences.isShowExpandedAlbumArtEnabled(context),
            expandedAlbumArtShape = OverlayPreferences.getExpandedAlbumArtShape(context),
            expandedAlbumArtRotation = OverlayPreferences.getExpandedAlbumArtRotation(context),
            expandedPlayerLayout = OverlayPreferences.getExpandedPlayerLayout(context),
            showProgressSquiggles = OverlayPreferences.isShowProgressSquigglesEnabled(context),
            progressBarAccentColor = OverlayPreferences.isProgressBarAccentColorEnabled(context),
            progressTimestampPlacement = OverlayPreferences.getProgressTimestampPlacement(context),
            playbackPlayPauseBg = playback.playPauseBackground,
            playbackSkipBg = playback.skipBackground,
            playbackCornerRadiusDp = playback.cornerRadiusDp,
            playbackM3ButtonGroup = playback.material3ButtonGroup,
            playbackPlayPauseAccent = playback.playPauseAccentColor,
            trackInfoShowTitle = trackInfo.showTitle,
            trackInfoShowArtist = trackInfo.showArtist,
            trackInfoShowAlbum = trackInfo.showAlbum,
            trackInfoAlbumBesideArtist = trackInfo.albumBesideArtist,
            trackInfoTitleAccent = trackInfo.titleAccentColor,
            showMinimizedTitle = OverlayPreferences.isShowMinimizedTitleEnabled(context),
            showMinimizedVisualizer = OverlayPreferences.isShowMinimizedVisualizerEnabled(context),
            showMinimizedProgressOutline = OverlayPreferences.isShowMinimizedProgressOutlineEnabled(context),
            showExpandedProgressOutline = OverlayPreferences.isShowExpandedProgressOutlineEnabled(context),
            albumArtGlowEnabled = OverlayPreferences.albumArtGlowFlow.value,
            showMinimizedDominantGlow = OverlayPreferences.isShowMinimizedDominantColorGlowEnabled(context),
            showExpandedDominantGlow = OverlayPreferences.isShowExpandedDominantColorGlowEnabled(context),
            expandedGlowLeft = glowSides.left,
            expandedGlowRight = glowSides.right,
            expandedGlowTop = glowSides.top,
            expandedGlowBottom = glowSides.bottom,
            bpmPulseEnabled = OverlayPreferences.isBpmPulseEnabled(context),
            minimizedBpmPulseEnabled = OverlayPreferences.isMinimizedBpmPulseEnabled(context),
            expandedBpmPulseEnabled = OverlayPreferences.isExpandedBpmPulseEnabled(context),
            pulseAmount = OverlayPreferences.getPulseAmount(context),
            pulseEntireIslandEnabled = OverlayPreferences.isPulseEntireIslandEnabled(context),
            waveformBandCount = OverlayPreferences.waveformBandCountFlow.value,
            waveformBarWidth = OverlayPreferences.waveformBarWidthFlow.value,
            waveformBarSpacing = OverlayPreferences.waveformBarSpacingFlow.value,
            showMinimizedFlashlightOutline = OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context),
            showExpandedFlashlightOutline = OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context),
            flashlightExpandedStyle = OverlayPreferences.getFlashlightExpandedStyle(context),
            flashlightBeamBlur = OverlayPreferences.isFlashlightBeamBlurEnabled(context),
            flashlightBeamAccentColor = OverlayPreferences.isFlashlightBeamAccentColorEnabled(context),
            flashlightTapToToggle = OverlayPreferences.isFlashlightTapToToggleEnabled(context),
            flashlightAutoOffMinutes = OverlayPreferences.getFlashlightAutoOffMinutes(context),
            flashlightStrobeSpeed = OverlayPreferences.getFlashlightStrobeSpeed(context),
            musicPauseTimeoutSeconds = OverlayPreferences.getMusicPauseTimeoutSeconds(context),
            musicQuickSeekSeconds = OverlayPreferences.getMusicQuickSeekSeconds(context),
            tapToExpand = OverlayPreferences.isTapToExpandEnabled(context),
            hapticsEnabled = OverlayPreferences.isHapticsEnabled(context),
            expandedAutoCollapseSeconds = OverlayPreferences.getExpandedAutoCollapseSeconds(context),
            holdToExpandDurationMs = OverlayPreferences.getHoldToExpandDurationMs(context),
            swipeDownSensitivity = OverlayPreferences.getSwipeDownSensitivity(context),
            hideWhenScreenOff = OverlayPreferences.isHideWhenScreenOffEnabled(context),
            hideOnLockScreen = OverlayPreferences.isHideOnLockScreenEnabled(context),
            hideInFullscreen = OverlayPreferences.isHideInFullscreenEnabled(context),
            hideInLandscape = OverlayPreferences.isHideInLandscapeEnabled(context),
            hideInDnd = OverlayPreferences.isHideInDndEnabled(context),
            hideMusicWhenAppOpen = OverlayPreferences.isHideMusicWhenAppOpenEnabled(context),
            showSongAnnouncement = OverlayPreferences.isShowSongAnnouncementEnabled(context),
            expandedShowTrackInfo = expandedVisibility.trackInfo,
            expandedShowProgress = expandedVisibility.progress,
            expandedShowMainControls = expandedVisibility.mainControls,
            expandedShowAppActions = expandedVisibility.appActions,
            expandedShowVisualizer = expandedVisibility.visualizer,
            expandedShowBpm = expandedVisibility.bpm,
            visualizerMode = OverlayPreferences.getVisualizerMode(context),
            previewAudioSource = OverlayPreferences.getPreviewAudioSource(context),
            activitiesEnabled = activities.enabled,
            activityProgress = activities.progress,
            activityNavigation = activities.navigation,
            activityCalls = activities.calls,
            activityTimers = activities.timers,
            activityBatteryCharging = activities.batteryCharging,
            activityBatteryLow = activities.batteryLow,
            activitySoundMode = activities.soundMode,
            activityDnd = activities.dnd,
            activityVolumeHud = activities.volumeHud,
            activityBluetooth = activities.bluetooth,
            activityAudioOutput = activities.audioOutput,
            activityScreenshotPocket = activities.screenshotPocket,
            activityChargeLimitAlert = activities.chargeLimitAlert,
            activityChargeLimitPercent = activities.chargeLimitPercent,
            activityPermanentCharging = activities.permanentCharging,
            activityPermanentLowBattery = activities.permanentLowBattery,
            activityPermanentSoundMode = activities.permanentSoundMode,
            activityPermanentDnd = activities.permanentDnd,
            activityPermanentBluetooth = activities.permanentBluetooth,
            activityPermanentAudioOutput = activities.permanentAudioOutput,
            activityPermanentScreenshotPocket = activities.permanentScreenshotPocket,
            activityExpandShortTerm = activities.expandShortTermAlerts,
            activityProgressOutline = activities.progressOutline,
            islandPriorityOrder = OverlayPreferences.getIslandPriorityOrder(context),
            cutoutManualEnabled = cutout.isManualEnabled,
            cutoutOffsetX = cutout.offsetX,
            cutoutOffsetY = cutout.offsetY,
            cutoutDiameterDp = cutout.customDiameterDp,
        )
        return toJson(config)
    }

    fun applyToPreferences(context: Context, config: IslandBackupConfig, includeCutout: Boolean = true) {
        applyVisualAppearance(context, config)
        OverlayPreferences.setMinimizedMaxWidthPercent(context, config.minimizedMaxWidthPercent)
        OverlayPreferences.setFlashlightTapToToggleEnabled(context, config.flashlightTapToToggle)
        OverlayPreferences.setFlashlightAutoOffMinutes(context, config.flashlightAutoOffMinutes)
        OverlayPreferences.setFlashlightStrobeSpeed(context, config.flashlightStrobeSpeed)
        OverlayPreferences.setMusicPauseTimeoutSeconds(context, config.musicPauseTimeoutSeconds)
        OverlayPreferences.setMusicQuickSeekSeconds(context, config.musicQuickSeekSeconds)
        OverlayPreferences.setTapToExpandEnabled(context, config.tapToExpand)
        OverlayPreferences.setHapticsEnabled(context, config.hapticsEnabled)
        OverlayPreferences.setExpandedAutoCollapseSeconds(context, config.expandedAutoCollapseSeconds)
        OverlayPreferences.setHoldToExpandDurationMs(context, config.holdToExpandDurationMs)
        OverlayPreferences.setSwipeDownSensitivity(context, config.swipeDownSensitivity)
        OverlayPreferences.setHideWhenScreenOffEnabled(context, config.hideWhenScreenOff)
        OverlayPreferences.setHideOnLockScreenEnabled(context, config.hideOnLockScreen)
        OverlayPreferences.setHideInFullscreenEnabled(context, config.hideInFullscreen)
        OverlayPreferences.setHideInLandscapeEnabled(context, config.hideInLandscape)
        OverlayPreferences.setHideInDndEnabled(context, config.hideInDnd)
        OverlayPreferences.setHideMusicWhenAppOpenEnabled(context, config.hideMusicWhenAppOpen)
        OverlayPreferences.setShowSongAnnouncementEnabled(context, config.showSongAnnouncement)
        OverlayPreferences.setExpandedElementVisibility(
            context,
            OverlayPreferences.ExpandedElementVisibility(
                trackInfo = config.expandedShowTrackInfo,
                progress = config.expandedShowProgress,
                mainControls = config.expandedShowMainControls,
                appActions = config.expandedShowAppActions,
                visualizer = config.expandedShowVisualizer,
                bpm = config.expandedShowBpm,
            ),
        )
        OverlayPreferences.setVisualizerMode(context, config.visualizerMode)
        OverlayPreferences.setPreviewAudioSource(context, config.previewAudioSource)
        OverlayPreferences.setNotificationActivitySettings(
            context,
            NotificationActivitySettings(
                enabled = config.activitiesEnabled,
                progress = config.activityProgress,
                navigation = config.activityNavigation,
                calls = config.activityCalls,
                timers = config.activityTimers,
                batteryCharging = config.activityBatteryCharging,
                batteryLow = config.activityBatteryLow,
                soundMode = config.activitySoundMode,
                dnd = config.activityDnd,
                volumeHud = config.activityVolumeHud,
                bluetooth = config.activityBluetooth,
                audioOutput = config.activityAudioOutput,
                screenshotPocket = config.activityScreenshotPocket,
                chargeLimitAlert = config.activityChargeLimitAlert,
                chargeLimitPercent = config.activityChargeLimitPercent,
                permanentCharging = config.activityPermanentCharging,
                permanentLowBattery = config.activityPermanentLowBattery,
                permanentSoundMode = config.activityPermanentSoundMode,
                permanentDnd = config.activityPermanentDnd,
                permanentBluetooth = config.activityPermanentBluetooth,
                permanentAudioOutput = config.activityPermanentAudioOutput,
                permanentScreenshotPocket = config.activityPermanentScreenshotPocket,
                expandShortTermAlerts = config.activityExpandShortTerm,
                progressOutline = config.activityProgressOutline,
            ),
        )
        OverlayPreferences.setIslandPriorityOrder(context, config.islandPriorityOrder)

        if (includeCutout && config.cutoutManualEnabled != null) {
            val current = OverlayPreferences.getCutoutConfig(context)
            OverlayPreferences.setCutoutConfig(
                context,
                current.copy(
                    isManualEnabled = config.cutoutManualEnabled,
                    offsetX = config.cutoutOffsetX ?: current.offsetX,
                    offsetY = config.cutoutOffsetY ?: current.offsetY,
                    customDiameterDp = config.cutoutDiameterDp ?: current.customDiameterDp,
                ),
            )
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        if (name.isNullOrBlank()) default else enumValues<T>().firstOrNull { it.name == name } ?: default
}
