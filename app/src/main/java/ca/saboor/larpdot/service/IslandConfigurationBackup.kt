package ca.saboor.larpdot.service

import android.content.Context
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.ui.overlay.DEFAULT_ISLAND_PRIORITY_ORDER
import ca.saboor.larpdot.ui.overlay.IslandPriorityItem
import ca.saboor.larpdot.ui.overlay.SwipeDownSensitivity
import ca.saboor.larpdot.ui.overlay.normalizeIslandPriorityOrder

data class IslandBackupConfig(
    val islandOutlineMode: OverlayPreferences.IslandOutlineMode = OverlayPreferences.IslandOutlineMode.ON,
    val smallerBubblesMode: OverlayPreferences.SmallerBubblesMode = OverlayPreferences.SmallerBubblesMode.OFF,
    val islandSpacingDp: Float = OverlayPreferences.DEFAULT_ISLAND_SPACING_DP,
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
    val expandedAlbumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    val showExpandedAlbumArt: Boolean = true,
    val expandedAlbumArtShape: OverlayPreferences.NestedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
    val expandedAlbumArtRotation: Float = 0f,
    val audioOutputStyle: OverlayPreferences.AudioOutputStyle = OverlayPreferences.AudioOutputStyle.CHIP,
    val expandedPlayerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
    val showProgressSquiggles: Boolean = true,
    val progressBarAccentColor: Boolean = false,
    val progressTimestampPlacement: OverlayPreferences.ProgressTimestampPlacement =
        OverlayPreferences.ProgressTimestampPlacement.HIDDEN,
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
    val minimizedMusicLeftSide: OverlayPreferences.MinimizedMusicSideContent = OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART,
    val minimizedMusicRightSide: OverlayPreferences.MinimizedMusicSideContent = OverlayPreferences.MinimizedMusicSideContent.VISUALIZER,
    val showMinimizedProgressOutline: Boolean = true,
    val showExpandedProgressOutline: Boolean = true,
    val albumArtGlowEnabled: Boolean = true,
    val showMinimizedDominantGlow: Boolean = true,
    val showExpandedDominantGlow: Boolean = true,
    val expandedGlowLeft: Boolean = true,
    val expandedGlowRight: Boolean = true,
    val expandedGlowTop: Boolean = false,
    val expandedGlowBottom: Boolean = true,
    val minimizedBpmPulseEnabled: Boolean = true,
    val expandedBpmPulseEnabled: Boolean = true,
    val pulseAmount: Float = 1f,
    val pulseEntireIslandEnabled: Boolean = false,
    val waveformBandCount: Int = OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT,
    val waveformBarWidth: Float = 2.5f,
    val waveformBarSpacing: Float = 1.5f,
    val minimizedFlashlightLeftSide: OverlayPreferences.MinimizedFlashlightSideContent = OverlayPreferences.MinimizedFlashlightSideContent.ICON,
    val minimizedFlashlightRightSide: OverlayPreferences.MinimizedFlashlightSideContent = OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS,
    val showExpandedFlashlightOutline: Boolean = true,
    val flashlightExpandedStyle: OverlayPreferences.FlashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
    val flashlightBeamBlur: Boolean = false,
    val flashlightBeamAccentColor: Boolean = true,
    val musicPauseTimeoutSeconds: Int = OverlayPreferences.DEFAULT_MUSIC_PAUSE_TIMEOUT_SECONDS,
    val musicQuickSeekSeconds: Int = OverlayPreferences.DEFAULT_MUSIC_QUICK_SEEK_SECONDS,
    val tapToExpand: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val expandedAutoCollapseSeconds: Int = OverlayPreferences.DEFAULT_EXPANDED_AUTO_COLLAPSE_SECONDS,
    val holdToExpandDurationMs: Int = OverlayPreferences.DEFAULT_HOLD_TO_EXPAND_DURATION_MS,
    val swipeDownSensitivity: SwipeDownSensitivity = SwipeDownSensitivity.BALANCED,
    val animationSpeed: OverlayPreferences.AnimationSpeed = OverlayPreferences.AnimationSpeed.DEFAULT,
    val hideWhenScreenOff: Boolean = true,
    val hideOnLockScreen: Boolean = false,
    val hideInFullscreen: Boolean = false,
    val hideInLandscape: Boolean = false,
    val hideInDnd: Boolean = false,
    val hideInRelatedApp: Boolean = true,
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
    val activityLiveUpdates: Boolean = true,
    val activityProgress: Boolean = true,
    val activityNavigation: Boolean = true,
    val activityCalls: Boolean = true,
    val activityTimers: Boolean = true,
    val activityBatteryCharging: Boolean = true,
    val activityBatteryLow: Boolean = true,
    val activitySoundMode: Boolean = true,
    val activityDnd: Boolean = true,
    val activityBluetooth: Boolean = true,
    val activityAudioOutput: Boolean = true,
    val activityChargeLimitAlert: Boolean = false,
    val activityChargeLimitPercent: Int = 80,
    val activityLowBatteryThresholdPercent: Int = 15,
    val activityPermanentCharging: Boolean = false,
    val activityPermanentLowBattery: Boolean = false,
    val activityPermanentSoundMode: Boolean = false,
    val activityPermanentDnd: Boolean = false,
    val activityPermanentBluetooth: Boolean = false,
    val activityPermanentAudioOutput: Boolean = false,
    val activityAutoExpandLiveUpdates: Boolean = false,
    val activityAutoExpandLiveUpdatesOnChange: Boolean = false,
    val activityAutoExpandProgress: Boolean = false,
    val activityAutoExpandNavigation: Boolean = false,
    val activityAutoExpandCalls: Boolean = false,
    val activityAutoExpandTimers: Boolean = false,
    val activityAutoExpandCharging: Boolean = false,
    val activityAutoExpandLowBattery: Boolean = false,
    val activityAutoExpandChargeLimit: Boolean = false,
    val activityAutoExpandSoundMode: Boolean = false,
    val activityAutoExpandDnd: Boolean = false,
    val activityAutoExpandBluetooth: Boolean = false,
    val activityAutoExpandAudioOutput: Boolean = false,
    val activityProgressOutline: Boolean = true,
    val activityHideStatusBarLiveUpdates: Boolean = false,
    val activityMinimizeStatusBarLiveUpdates: Boolean = false,
    val activityUseAppColorForIcons: Boolean = false,
    val islandPriorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
    val cutoutManualEnabled: Boolean? = null,
    val cutoutOffsetX: Float? = null,
    val cutoutOffsetY: Float? = null,
    val cutoutDiameterDp: Float? = null,
)

object IslandConfigurationBackup {
    private const val BACKUP_VERSION = 1

    private fun applyVisualAppearance(context: Context, config: IslandBackupConfig) {
        OverlayPreferences.setIslandOutlineMode(context, config.islandOutlineMode)
        OverlayPreferences.setSmallerBubblesMode(context, config.smallerBubblesMode)
        OverlayPreferences.setIslandSpacingDp(context, config.islandSpacingDp)
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
        OverlayPreferences.setExpandedAlbumArtStyle(context, config.expandedAlbumArtStyle)
        OverlayPreferences.setShowExpandedAlbumArtEnabled(context, config.showExpandedAlbumArt)
        OverlayPreferences.setExpandedAlbumArtShape(context, config.expandedAlbumArtShape)
        OverlayPreferences.setExpandedAlbumArtRotation(context, config.expandedAlbumArtRotation)
        OverlayPreferences.setExpandedPlayerLayout(context, config.expandedPlayerLayout)
        OverlayPreferences.setAudioOutputStyle(context, config.audioOutputStyle)
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
        OverlayPreferences.setMinimizedMusicLeftSide(context, config.minimizedMusicLeftSide)
        OverlayPreferences.setMinimizedMusicRightSide(context, config.minimizedMusicRightSide)
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
        OverlayPreferences.setMinimizedFlashlightLeftSide(context, config.minimizedFlashlightLeftSide)
        OverlayPreferences.setMinimizedFlashlightRightSide(context, config.minimizedFlashlightRightSide)
        OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, config.showExpandedFlashlightOutline)
        OverlayPreferences.setFlashlightExpandedStyle(context, config.flashlightExpandedStyle)
        OverlayPreferences.setFlashlightBeamBlurEnabled(context, config.flashlightBeamBlur)
        OverlayPreferences.setFlashlightBeamAccentColorEnabled(context, config.flashlightBeamAccentColor)
    }

    private val KNOWN_BACKUP_KEYS: Set<String> by lazy {
        parseBackupFields(toJson(IslandBackupConfig()))?.keys.orEmpty() + setOf(
            "cutoutManualEnabled",
            "cutoutOffsetX",
            "cutoutOffsetY",
            "cutoutDiameterDp",
        )
    }

    fun toJson(config: IslandBackupConfig): String {
        val entries = buildList<Pair<String, String>> {
            add("version" to BACKUP_VERSION.toString())
            add("islandOutlineMode" to quote(config.islandOutlineMode.name))
            add("smallerBubblesMode" to quote(config.smallerBubblesMode.name))
            add("islandSpacingDp" to config.islandSpacingDp.toString())
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
            add("expandedAlbumArtStyle" to quote(config.expandedAlbumArtStyle.name))
            add("showExpandedAlbumArt" to config.showExpandedAlbumArt.toString())
            add("expandedAlbumArtShape" to quote(config.expandedAlbumArtShape.name))
            add("expandedAlbumArtRotation" to config.expandedAlbumArtRotation.toString())
            add("expandedPlayerLayout" to quote(config.expandedPlayerLayout.name))
            add("audioOutputStyle" to quote(config.audioOutputStyle.name))
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
            add("minimizedMusicLeftSide" to quote(config.minimizedMusicLeftSide.name))
            add("minimizedMusicRightSide" to quote(config.minimizedMusicRightSide.name))
            add("showMinimizedProgressOutline" to config.showMinimizedProgressOutline.toString())
            add("showExpandedProgressOutline" to config.showExpandedProgressOutline.toString())
            add("albumArtGlowEnabled" to config.albumArtGlowEnabled.toString())
            add("showMinimizedDominantGlow" to config.showMinimizedDominantGlow.toString())
            add("showExpandedDominantGlow" to config.showExpandedDominantGlow.toString())
            add("expandedGlowLeft" to config.expandedGlowLeft.toString())
            add("expandedGlowRight" to config.expandedGlowRight.toString())
            add("expandedGlowTop" to config.expandedGlowTop.toString())
            add("expandedGlowBottom" to config.expandedGlowBottom.toString())
            add("minimizedBpmPulseEnabled" to config.minimizedBpmPulseEnabled.toString())
            add("expandedBpmPulseEnabled" to config.expandedBpmPulseEnabled.toString())
            add("pulseAmount" to config.pulseAmount.toString())
            add("pulseEntireIslandEnabled" to config.pulseEntireIslandEnabled.toString())
            add("waveformBandCount" to config.waveformBandCount.toString())
            add("waveformBarWidth" to config.waveformBarWidth.toString())
            add("waveformBarSpacing" to config.waveformBarSpacing.toString())
            add("minimizedFlashlightLeftSide" to quote(config.minimizedFlashlightLeftSide.name))
            add("minimizedFlashlightRightSide" to quote(config.minimizedFlashlightRightSide.name))
            add("showExpandedFlashlightOutline" to config.showExpandedFlashlightOutline.toString())
            add("flashlightExpandedStyle" to quote(config.flashlightExpandedStyle.name))
            add("flashlightBeamBlur" to config.flashlightBeamBlur.toString())
            add("flashlightBeamAccentColor" to config.flashlightBeamAccentColor.toString())
            add("musicPauseTimeoutSeconds" to config.musicPauseTimeoutSeconds.toString())
            add("musicQuickSeekSeconds" to config.musicQuickSeekSeconds.toString())
            add("tapToExpand" to config.tapToExpand.toString())
            add("hapticsEnabled" to config.hapticsEnabled.toString())
            add("expandedAutoCollapseSeconds" to config.expandedAutoCollapseSeconds.toString())
            add("holdToExpandDurationMs" to config.holdToExpandDurationMs.toString())
            add("swipeDownSensitivity" to quote(config.swipeDownSensitivity.name))
            add("animationSpeed" to quote(config.animationSpeed.name))
            add("hideWhenScreenOff" to config.hideWhenScreenOff.toString())
            add("hideOnLockScreen" to config.hideOnLockScreen.toString())
            add("hideInFullscreen" to config.hideInFullscreen.toString())
            add("hideInLandscape" to config.hideInLandscape.toString())
            add("hideInDnd" to config.hideInDnd.toString())
            add("hideInRelatedApp" to config.hideInRelatedApp.toString())
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
            add("activityLiveUpdates" to config.activityLiveUpdates.toString())
            add("activityProgress" to config.activityProgress.toString())
            add("activityNavigation" to config.activityNavigation.toString())
            add("activityCalls" to config.activityCalls.toString())
            add("activityTimers" to config.activityTimers.toString())
            add("activityBatteryCharging" to config.activityBatteryCharging.toString())
            add("activityBatteryLow" to config.activityBatteryLow.toString())
            add("activitySoundMode" to config.activitySoundMode.toString())
            add("activityDnd" to config.activityDnd.toString())
            add("activityBluetooth" to config.activityBluetooth.toString())
            add("activityAudioOutput" to config.activityAudioOutput.toString())
            add("activityChargeLimitAlert" to config.activityChargeLimitAlert.toString())
            add("activityChargeLimitPercent" to config.activityChargeLimitPercent.toString())
            add("activityLowBatteryThresholdPercent" to config.activityLowBatteryThresholdPercent.toString())
            add("activityPermanentCharging" to config.activityPermanentCharging.toString())
            add("activityPermanentLowBattery" to config.activityPermanentLowBattery.toString())
            add("activityPermanentSoundMode" to config.activityPermanentSoundMode.toString())
            add("activityPermanentDnd" to config.activityPermanentDnd.toString())
            add("activityPermanentBluetooth" to config.activityPermanentBluetooth.toString())
            add("activityPermanentAudioOutput" to config.activityPermanentAudioOutput.toString())
            add("activityAutoExpandLiveUpdates" to config.activityAutoExpandLiveUpdates.toString())
            add("activityAutoExpandLiveUpdatesOnChange" to config.activityAutoExpandLiveUpdatesOnChange.toString())
            add("activityAutoExpandProgress" to config.activityAutoExpandProgress.toString())
            add("activityAutoExpandNavigation" to config.activityAutoExpandNavigation.toString())
            add("activityAutoExpandCalls" to config.activityAutoExpandCalls.toString())
            add("activityAutoExpandTimers" to config.activityAutoExpandTimers.toString())
            add("activityAutoExpandCharging" to config.activityAutoExpandCharging.toString())
            add("activityAutoExpandLowBattery" to config.activityAutoExpandLowBattery.toString())
            add("activityAutoExpandChargeLimit" to config.activityAutoExpandChargeLimit.toString())
            add("activityAutoExpandSoundMode" to config.activityAutoExpandSoundMode.toString())
            add("activityAutoExpandDnd" to config.activityAutoExpandDnd.toString())
            add("activityAutoExpandBluetooth" to config.activityAutoExpandBluetooth.toString())
            add("activityAutoExpandAudioOutput" to config.activityAutoExpandAudioOutput.toString())
            add("activityProgressOutline" to config.activityProgressOutline.toString())
            add("activityHideStatusBarLiveUpdates" to config.activityHideStatusBarLiveUpdates.toString())
            add("activityMinimizeStatusBarLiveUpdates" to config.activityMinimizeStatusBarLiveUpdates.toString())
            add("activityUseAppColorForIcons" to config.activityUseAppColorForIcons.toString())
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
        val fields = parseBackupFields(raw) ?: return null
        fun finiteFloat(key: String): Float? = fields[key]?.toFloatOrNull()?.takeIf(Float::isFinite)
        if (fields.keys.none { it in KNOWN_BACKUP_KEYS }) return null
        val defaults = IslandBackupConfig()
        val rawPriority = fields["islandPriorityOrder"]
        val parsedPriority = if (!rawPriority.isNullOrBlank()) {
            normalizeIslandPriorityOrder(
                rawPriority.split(",").mapNotNull(IslandPriorityItem::fromName),
            )
        } else {
            defaults.islandPriorityOrder
        }
        return IslandBackupConfig(
            islandOutlineMode = enumOrDefault(fields["islandOutlineMode"], defaults.islandOutlineMode),
            smallerBubblesMode = enumOrDefault(fields["smallerBubblesMode"], defaults.smallerBubblesMode),
            islandSpacingDp = (finiteFloat("islandSpacingDp") ?: defaults.islandSpacingDp).coerceIn(0f, 24f),
            minimizedMaxWidthPercent = (finiteFloat("minimizedMaxWidthPercent") ?: defaults.minimizedMaxWidthPercent).coerceIn(40f, 100f),
            islandCornerRadius = (finiteFloat("islandCornerRadius") ?: defaults.islandCornerRadius).coerceIn(0f, 100f),
            islandSquircleEnabled = fields["islandSquircleEnabled"]?.toBooleanStrictOrNull() ?: defaults.islandSquircleEnabled,
            expandedBackgroundOpacity = (finiteFloat("expandedBackgroundOpacity") ?: defaults.expandedBackgroundOpacity).coerceIn(0f, 1f),
            backdropBlurEnabled = fields["backdropBlurEnabled"]?.toBooleanStrictOrNull() ?: defaults.backdropBlurEnabled,
            backdropBlurRadius = (finiteFloat("backdropBlurRadius") ?: defaults.backdropBlurRadius).coerceIn(5f, 120f),
            cameraCoverStyle = enumOrDefault(fields["cameraCoverStyle"], defaults.cameraCoverStyle),
            islandFontStyle = enumOrDefault(fields["islandFontStyle"], defaults.islandFontStyle),
            islandFontWeight = (fields["islandFontWeight"]?.toIntOrNull() ?: defaults.islandFontWeight).coerceIn(100, 900),
            minimizedAlbumArtStyle = enumOrDefault(fields["minimizedAlbumArtStyle"], defaults.minimizedAlbumArtStyle),
            minimizedAlbumArtShape = enumOrDefault(fields["minimizedAlbumArtShape"], defaults.minimizedAlbumArtShape),
            minimizedAlbumArtRotation = (finiteFloat("minimizedAlbumArtRotation") ?: defaults.minimizedAlbumArtRotation).coerceIn(0f, 360f),
            expandedAlbumArtStyle = enumOrDefault(fields["expandedAlbumArtStyle"], defaults.expandedAlbumArtStyle),
            showExpandedAlbumArt = fields["showExpandedAlbumArt"]?.toBooleanStrictOrNull() ?: defaults.showExpandedAlbumArt,
            expandedAlbumArtShape = enumOrDefault(fields["expandedAlbumArtShape"], defaults.expandedAlbumArtShape),
            expandedAlbumArtRotation = (finiteFloat("expandedAlbumArtRotation") ?: defaults.expandedAlbumArtRotation).coerceIn(0f, 360f),
            expandedPlayerLayout = enumOrDefault(fields["expandedPlayerLayout"], defaults.expandedPlayerLayout),
            audioOutputStyle = enumOrDefault(fields["audioOutputStyle"], defaults.audioOutputStyle),
            showProgressSquiggles = fields["showProgressSquiggles"]?.toBooleanStrictOrNull() ?: defaults.showProgressSquiggles,
            progressBarAccentColor = fields["progressBarAccentColor"]?.toBooleanStrictOrNull() ?: defaults.progressBarAccentColor,
            progressTimestampPlacement = enumOrDefault(fields["progressTimestampPlacement"], defaults.progressTimestampPlacement),
            playbackPlayPauseBg = fields["playbackPlayPauseBg"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseBg,
            playbackSkipBg = fields["playbackSkipBg"]?.toBooleanStrictOrNull() ?: defaults.playbackSkipBg,
            playbackCornerRadiusDp = (finiteFloat("playbackCornerRadiusDp") ?: defaults.playbackCornerRadiusDp).coerceIn(0f, 32f),
            playbackM3ButtonGroup = fields["playbackM3ButtonGroup"]?.toBooleanStrictOrNull() ?: defaults.playbackM3ButtonGroup,
            playbackPlayPauseAccent = fields["playbackPlayPauseAccent"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseAccent,
            trackInfoShowTitle = fields["trackInfoShowTitle"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowTitle,
            trackInfoShowArtist = fields["trackInfoShowArtist"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowArtist,
            trackInfoShowAlbum = fields["trackInfoShowAlbum"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowAlbum,
            trackInfoAlbumBesideArtist = fields["trackInfoAlbumBesideArtist"]?.toBooleanStrictOrNull() ?: defaults.trackInfoAlbumBesideArtist,
            trackInfoTitleAccent = fields["trackInfoTitleAccent"]?.toBooleanStrictOrNull() ?: defaults.trackInfoTitleAccent,
            minimizedMusicLeftSide = enumOrDefault(fields["minimizedMusicLeftSide"], defaults.minimizedMusicLeftSide),
            minimizedMusicRightSide = enumOrDefault(fields["minimizedMusicRightSide"], defaults.minimizedMusicRightSide),
            showMinimizedProgressOutline = fields["showMinimizedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedProgressOutline,
            showExpandedProgressOutline = fields["showExpandedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedProgressOutline,
            albumArtGlowEnabled = fields["albumArtGlowEnabled"]?.toBooleanStrictOrNull() ?: defaults.albumArtGlowEnabled,
            showMinimizedDominantGlow = fields["showMinimizedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedDominantGlow,
            showExpandedDominantGlow = fields["showExpandedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showExpandedDominantGlow,
            expandedGlowLeft = fields["expandedGlowLeft"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowLeft,
            expandedGlowRight = fields["expandedGlowRight"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowRight,
            expandedGlowTop = fields["expandedGlowTop"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowTop,
            expandedGlowBottom = fields["expandedGlowBottom"]?.toBooleanStrictOrNull() ?: defaults.expandedGlowBottom,
            minimizedBpmPulseEnabled = fields["minimizedBpmPulseEnabled"]?.toBooleanStrictOrNull() ?: defaults.minimizedBpmPulseEnabled,
            expandedBpmPulseEnabled = fields["expandedBpmPulseEnabled"]?.toBooleanStrictOrNull() ?: defaults.expandedBpmPulseEnabled,
            pulseAmount = (finiteFloat("pulseAmount") ?: defaults.pulseAmount).coerceIn(0.25f, 3.0f),
            pulseEntireIslandEnabled = fields["pulseEntireIslandEnabled"]?.toBooleanStrictOrNull() ?: defaults.pulseEntireIslandEnabled,
            waveformBandCount = (fields["waveformBandCount"]?.toIntOrNull() ?: defaults.waveformBandCount).coerceIn(0, 30),
            waveformBarWidth = (finiteFloat("waveformBarWidth") ?: defaults.waveformBarWidth).coerceIn(0.5f, 6f),
            waveformBarSpacing = (finiteFloat("waveformBarSpacing") ?: defaults.waveformBarSpacing).coerceIn(0f, 8f),
            minimizedFlashlightLeftSide = enumOrDefault(fields["minimizedFlashlightLeftSide"], defaults.minimizedFlashlightLeftSide),
            minimizedFlashlightRightSide = enumOrDefault(fields["minimizedFlashlightRightSide"], defaults.minimizedFlashlightRightSide),
            showExpandedFlashlightOutline = fields["showExpandedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedFlashlightOutline,
            flashlightExpandedStyle = enumOrDefault(fields["flashlightExpandedStyle"], defaults.flashlightExpandedStyle),
            flashlightBeamBlur = fields["flashlightBeamBlur"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamBlur,
            flashlightBeamAccentColor = fields["flashlightBeamAccentColor"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamAccentColor,
            musicPauseTimeoutSeconds = (fields["musicPauseTimeoutSeconds"]?.toIntOrNull() ?: defaults.musicPauseTimeoutSeconds).coerceIn(0, 300),
            musicQuickSeekSeconds = (fields["musicQuickSeekSeconds"]?.toIntOrNull() ?: defaults.musicQuickSeekSeconds).coerceIn(5, 60),
            tapToExpand = fields["tapToExpand"]?.toBooleanStrictOrNull() ?: defaults.tapToExpand,
            hapticsEnabled = fields["hapticsEnabled"]?.toBooleanStrictOrNull() ?: defaults.hapticsEnabled,
            expandedAutoCollapseSeconds = (fields["expandedAutoCollapseSeconds"]?.toIntOrNull()
                ?: defaults.expandedAutoCollapseSeconds).coerceIn(0, 120),
            holdToExpandDurationMs = (fields["holdToExpandDurationMs"]?.toIntOrNull() ?: defaults.holdToExpandDurationMs)
                .coerceIn(OverlayPreferences.MIN_HOLD_TO_EXPAND_DURATION_MS, OverlayPreferences.MAX_HOLD_TO_EXPAND_DURATION_MS),
            swipeDownSensitivity = enumOrDefault(fields["swipeDownSensitivity"], defaults.swipeDownSensitivity),
            animationSpeed = enumOrDefault(fields["animationSpeed"], defaults.animationSpeed),
            hideWhenScreenOff = fields["hideWhenScreenOff"]?.toBooleanStrictOrNull() ?: defaults.hideWhenScreenOff,
            hideOnLockScreen = fields["hideOnLockScreen"]?.toBooleanStrictOrNull() ?: defaults.hideOnLockScreen,
            hideInFullscreen = fields["hideInFullscreen"]?.toBooleanStrictOrNull() ?: defaults.hideInFullscreen,
            hideInLandscape = fields["hideInLandscape"]?.toBooleanStrictOrNull() ?: defaults.hideInLandscape,
            hideInDnd = fields["hideInDnd"]?.toBooleanStrictOrNull() ?: defaults.hideInDnd,
            hideInRelatedApp = fields["hideInRelatedApp"]?.toBooleanStrictOrNull() ?: defaults.hideInRelatedApp,
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
            activityLiveUpdates = fields["activityLiveUpdates"]?.toBooleanStrictOrNull() ?: defaults.activityLiveUpdates,
            activityProgress = fields["activityProgress"]?.toBooleanStrictOrNull() ?: defaults.activityProgress,
            activityNavigation = fields["activityNavigation"]?.toBooleanStrictOrNull() ?: defaults.activityNavigation,
            activityCalls = fields["activityCalls"]?.toBooleanStrictOrNull() ?: defaults.activityCalls,
            activityTimers = fields["activityTimers"]?.toBooleanStrictOrNull() ?: defaults.activityTimers,
            activityBatteryCharging = fields["activityBatteryCharging"]?.toBooleanStrictOrNull() ?: defaults.activityBatteryCharging,
            activityBatteryLow = fields["activityBatteryLow"]?.toBooleanStrictOrNull() ?: defaults.activityBatteryLow,
            activitySoundMode = fields["activitySoundMode"]?.toBooleanStrictOrNull() ?: defaults.activitySoundMode,
            activityDnd = fields["activityDnd"]?.toBooleanStrictOrNull() ?: defaults.activityDnd,
            activityBluetooth = fields["activityBluetooth"]?.toBooleanStrictOrNull() ?: defaults.activityBluetooth,
            activityAudioOutput = fields["activityAudioOutput"]?.toBooleanStrictOrNull() ?: defaults.activityAudioOutput,
            activityChargeLimitAlert = fields["activityChargeLimitAlert"]?.toBooleanStrictOrNull() ?: defaults.activityChargeLimitAlert,
            activityChargeLimitPercent = (fields["activityChargeLimitPercent"]?.toIntOrNull() ?: defaults.activityChargeLimitPercent).coerceIn(70, 100),
            activityLowBatteryThresholdPercent = (fields["activityLowBatteryThresholdPercent"]?.toIntOrNull()
                ?: defaults.activityLowBatteryThresholdPercent).coerceIn(10, 30),
            activityPermanentCharging = fields["activityPermanentCharging"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentCharging,
            activityPermanentLowBattery = fields["activityPermanentLowBattery"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentLowBattery,
            activityPermanentSoundMode = fields["activityPermanentSoundMode"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentSoundMode,
            activityPermanentDnd = fields["activityPermanentDnd"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentDnd,
            activityPermanentBluetooth = fields["activityPermanentBluetooth"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentBluetooth,
            activityPermanentAudioOutput = fields["activityPermanentAudioOutput"]?.toBooleanStrictOrNull() ?: defaults.activityPermanentAudioOutput,
            activityAutoExpandLiveUpdates = fields["activityAutoExpandLiveUpdates"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandLiveUpdates,
            activityAutoExpandLiveUpdatesOnChange = fields["activityAutoExpandLiveUpdatesOnChange"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandLiveUpdatesOnChange,
            activityAutoExpandProgress = fields["activityAutoExpandProgress"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandProgress,
            activityAutoExpandNavigation = fields["activityAutoExpandNavigation"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandNavigation,
            activityAutoExpandCalls = fields["activityAutoExpandCalls"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandCalls,
            activityAutoExpandTimers = fields["activityAutoExpandTimers"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandTimers,
            activityAutoExpandCharging = fields["activityAutoExpandCharging"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandCharging,
            activityAutoExpandLowBattery = fields["activityAutoExpandLowBattery"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandLowBattery,
            activityAutoExpandChargeLimit = fields["activityAutoExpandChargeLimit"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandChargeLimit,
            activityAutoExpandSoundMode = fields["activityAutoExpandSoundMode"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandSoundMode,
            activityAutoExpandDnd = fields["activityAutoExpandDnd"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandDnd,
            activityAutoExpandBluetooth = fields["activityAutoExpandBluetooth"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandBluetooth,
            activityAutoExpandAudioOutput = fields["activityAutoExpandAudioOutput"]?.toBooleanStrictOrNull() ?: defaults.activityAutoExpandAudioOutput,
            activityProgressOutline = fields["activityProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.activityProgressOutline,
            activityHideStatusBarLiveUpdates = fields["activityHideStatusBarLiveUpdates"]?.toBooleanStrictOrNull()
                ?: defaults.activityHideStatusBarLiveUpdates,
            activityMinimizeStatusBarLiveUpdates = fields["activityMinimizeStatusBarLiveUpdates"]?.toBooleanStrictOrNull()
                ?: defaults.activityMinimizeStatusBarLiveUpdates,
            activityUseAppColorForIcons = fields["activityUseAppColorForIcons"]?.toBooleanStrictOrNull()
                ?: defaults.activityUseAppColorForIcons,
            islandPriorityOrder = parsedPriority,
            cutoutManualEnabled = fields["cutoutManualEnabled"]?.toBooleanStrictOrNull(),
            cutoutOffsetX = finiteFloat("cutoutOffsetX"),
            cutoutOffsetY = finiteFloat("cutoutOffsetY"),
            cutoutDiameterDp = finiteFloat("cutoutDiameterDp"),
        )
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun parseBackupFields(raw: String): Map<String, String>? = runCatching {
        JsonReader(StringReader(raw)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.beginObject()
            val fields = buildMap {
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> put(key, reader.nextString())
                        JsonToken.BOOLEAN -> put(key, reader.nextBoolean().toString())
                        else -> {
                            reader.skipValue()
                            remove(key)
                        }
                    }
                }
            }
            reader.endObject()
            check(reader.peek() == JsonToken.END_DOCUMENT)
            fields
        }
    }.getOrNull()

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
            islandOutlineMode = OverlayPreferences.getIslandOutlineMode(context),
            smallerBubblesMode = OverlayPreferences.getSmallerBubblesMode(context),
            islandSpacingDp = OverlayPreferences.getIslandSpacingDp(context),
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
            expandedAlbumArtStyle = OverlayPreferences.getExpandedAlbumArtStyle(context),
            showExpandedAlbumArt = OverlayPreferences.isShowExpandedAlbumArtEnabled(context),
            expandedAlbumArtShape = OverlayPreferences.getExpandedAlbumArtShape(context),
            expandedAlbumArtRotation = OverlayPreferences.getExpandedAlbumArtRotation(context),
            expandedPlayerLayout = OverlayPreferences.getExpandedPlayerLayout(context),
            audioOutputStyle = OverlayPreferences.getAudioOutputStyle(context),
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
            minimizedMusicLeftSide = OverlayPreferences.getMinimizedMusicLeftSide(context),
            minimizedMusicRightSide = OverlayPreferences.getMinimizedMusicRightSide(context),
            showMinimizedProgressOutline = OverlayPreferences.isShowMinimizedProgressOutlineEnabled(context),
            showExpandedProgressOutline = OverlayPreferences.isShowExpandedProgressOutlineEnabled(context),
            albumArtGlowEnabled = OverlayPreferences.albumArtGlowFlow.value,
            showMinimizedDominantGlow = OverlayPreferences.isShowMinimizedDominantColorGlowEnabled(context),
            showExpandedDominantGlow = OverlayPreferences.isShowExpandedDominantColorGlowEnabled(context),
            expandedGlowLeft = glowSides.left,
            expandedGlowRight = glowSides.right,
            expandedGlowTop = glowSides.top,
            expandedGlowBottom = glowSides.bottom,
            minimizedBpmPulseEnabled = OverlayPreferences.isMinimizedBpmPulseEnabled(context),
            expandedBpmPulseEnabled = OverlayPreferences.isExpandedBpmPulseEnabled(context),
            pulseAmount = OverlayPreferences.getPulseAmount(context),
            pulseEntireIslandEnabled = OverlayPreferences.isPulseEntireIslandEnabled(context),
            waveformBandCount = OverlayPreferences.waveformBandCountFlow.value,
            waveformBarWidth = OverlayPreferences.waveformBarWidthFlow.value,
            waveformBarSpacing = OverlayPreferences.waveformBarSpacingFlow.value,
            minimizedFlashlightLeftSide = OverlayPreferences.getMinimizedFlashlightLeftSide(context),
            minimizedFlashlightRightSide = OverlayPreferences.getMinimizedFlashlightRightSide(context),
            showExpandedFlashlightOutline = OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context),
            flashlightExpandedStyle = OverlayPreferences.getFlashlightExpandedStyle(context),
            flashlightBeamBlur = OverlayPreferences.isFlashlightBeamBlurEnabled(context),
            flashlightBeamAccentColor = OverlayPreferences.isFlashlightBeamAccentColorEnabled(context),
            musicPauseTimeoutSeconds = OverlayPreferences.getMusicPauseTimeoutSeconds(context),
            musicQuickSeekSeconds = OverlayPreferences.getMusicQuickSeekSeconds(context),
            tapToExpand = OverlayPreferences.isTapToExpandEnabled(context),
            hapticsEnabled = OverlayPreferences.isHapticsEnabled(context),
            expandedAutoCollapseSeconds = OverlayPreferences.getExpandedAutoCollapseSeconds(context),
            holdToExpandDurationMs = OverlayPreferences.getHoldToExpandDurationMs(context),
            swipeDownSensitivity = OverlayPreferences.getSwipeDownSensitivity(context),
            animationSpeed = OverlayPreferences.getAnimationSpeed(context),
            hideWhenScreenOff = OverlayPreferences.isHideWhenScreenOffEnabled(context),
            hideOnLockScreen = OverlayPreferences.isHideOnLockScreenEnabled(context),
            hideInFullscreen = OverlayPreferences.isHideInFullscreenEnabled(context),
            hideInLandscape = OverlayPreferences.isHideInLandscapeEnabled(context),
            hideInDnd = OverlayPreferences.isHideInDndEnabled(context),
            hideInRelatedApp = OverlayPreferences.isHideInRelatedAppEnabled(context),
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
            activityLiveUpdates = activities.liveUpdates,
            activityProgress = activities.progress,
            activityNavigation = activities.navigation,
            activityCalls = activities.calls,
            activityTimers = activities.timers,
            activityBatteryCharging = activities.batteryCharging,
            activityBatteryLow = activities.batteryLow,
            activitySoundMode = activities.soundMode,
            activityDnd = activities.dnd,
            activityBluetooth = activities.bluetooth,
            activityAudioOutput = activities.audioOutput,
            activityChargeLimitAlert = activities.chargeLimitAlert,
            activityChargeLimitPercent = activities.chargeLimitPercent,
            activityLowBatteryThresholdPercent = activities.lowBatteryThresholdPercent,
            activityPermanentCharging = activities.permanentCharging,
            activityPermanentLowBattery = activities.permanentLowBattery,
            activityPermanentSoundMode = activities.permanentSoundMode,
            activityPermanentDnd = activities.permanentDnd,
            activityPermanentBluetooth = activities.permanentBluetooth,
            activityPermanentAudioOutput = activities.permanentAudioOutput,
            activityAutoExpandLiveUpdates = activities.autoExpandLiveUpdates,
            activityAutoExpandLiveUpdatesOnChange = activities.autoExpandLiveUpdatesOnChange,
            activityAutoExpandProgress = activities.autoExpandProgress,
            activityAutoExpandNavigation = activities.autoExpandNavigation,
            activityAutoExpandCalls = activities.autoExpandCalls,
            activityAutoExpandTimers = activities.autoExpandTimers,
            activityAutoExpandCharging = activities.autoExpandCharging,
            activityAutoExpandLowBattery = activities.autoExpandLowBattery,
            activityAutoExpandChargeLimit = activities.autoExpandChargeLimit,
            activityAutoExpandSoundMode = activities.autoExpandSoundMode,
            activityAutoExpandDnd = activities.autoExpandDnd,
            activityAutoExpandBluetooth = activities.autoExpandBluetooth,
            activityAutoExpandAudioOutput = activities.autoExpandAudioOutput,
            activityProgressOutline = activities.progressOutline,
            activityHideStatusBarLiveUpdates = activities.hideStatusBarLiveUpdates,
            activityMinimizeStatusBarLiveUpdates = activities.minimizeStatusBarLiveUpdates,
            activityUseAppColorForIcons = activities.useAppColorForIcons,
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
        OverlayPreferences.setMusicPauseTimeoutSeconds(context, config.musicPauseTimeoutSeconds)
        OverlayPreferences.setMusicQuickSeekSeconds(context, config.musicQuickSeekSeconds)
        OverlayPreferences.setTapToExpandEnabled(context, config.tapToExpand)
        OverlayPreferences.setHapticsEnabled(context, config.hapticsEnabled)
        OverlayPreferences.setExpandedAutoCollapseSeconds(context, config.expandedAutoCollapseSeconds)
        OverlayPreferences.setHoldToExpandDurationMs(context, config.holdToExpandDurationMs)
        OverlayPreferences.setSwipeDownSensitivity(context, config.swipeDownSensitivity)
        OverlayPreferences.setAnimationSpeed(context, config.animationSpeed)
        OverlayPreferences.setHideWhenScreenOffEnabled(context, config.hideWhenScreenOff)
        OverlayPreferences.setHideOnLockScreenEnabled(context, config.hideOnLockScreen)
        OverlayPreferences.setHideInFullscreenEnabled(context, config.hideInFullscreen)
        OverlayPreferences.setHideInLandscapeEnabled(context, config.hideInLandscape)
        OverlayPreferences.setHideInDndEnabled(context, config.hideInDnd)
        OverlayPreferences.setHideInRelatedAppEnabled(context, config.hideInRelatedApp)
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
                liveUpdates = config.activityLiveUpdates,
                progress = config.activityProgress,
                navigation = config.activityNavigation,
                calls = config.activityCalls,
                timers = config.activityTimers,
                batteryCharging = config.activityBatteryCharging,
                batteryLow = config.activityBatteryLow,
                soundMode = config.activitySoundMode,
                dnd = config.activityDnd,
                bluetooth = config.activityBluetooth,
                audioOutput = config.activityAudioOutput,
                chargeLimitAlert = config.activityChargeLimitAlert,
                chargeLimitPercent = config.activityChargeLimitPercent,
                lowBatteryThresholdPercent = config.activityLowBatteryThresholdPercent,
                permanentCharging = config.activityPermanentCharging,
                permanentLowBattery = config.activityPermanentLowBattery,
                permanentSoundMode = config.activityPermanentSoundMode,
                permanentDnd = config.activityPermanentDnd,
                permanentBluetooth = config.activityPermanentBluetooth,
                permanentAudioOutput = config.activityPermanentAudioOutput,
                autoExpandLiveUpdates = config.activityAutoExpandLiveUpdates,
                autoExpandLiveUpdatesOnChange = config.activityAutoExpandLiveUpdatesOnChange,
                autoExpandProgress = config.activityAutoExpandProgress,
                autoExpandNavigation = config.activityAutoExpandNavigation,
                autoExpandCalls = config.activityAutoExpandCalls,
                autoExpandTimers = config.activityAutoExpandTimers,
                autoExpandCharging = config.activityAutoExpandCharging,
                autoExpandLowBattery = config.activityAutoExpandLowBattery,
                autoExpandChargeLimit = config.activityAutoExpandChargeLimit,
                autoExpandSoundMode = config.activityAutoExpandSoundMode,
                autoExpandDnd = config.activityAutoExpandDnd,
                autoExpandBluetooth = config.activityAutoExpandBluetooth,
                autoExpandAudioOutput = config.activityAutoExpandAudioOutput,
                progressOutline = config.activityProgressOutline,
                hideStatusBarLiveUpdates = config.activityHideStatusBarLiveUpdates,
                minimizeStatusBarLiveUpdates = config.activityMinimizeStatusBarLiveUpdates,
                useAppColorForIcons = config.activityUseAppColorForIcons,
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
