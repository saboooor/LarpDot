package ca.saboor.larpdot.service

import android.content.Context
import ca.saboor.larpdot.ui.overlay.DEFAULT_ISLAND_PRIORITY_ORDER
import ca.saboor.larpdot.ui.overlay.IslandPriorityItem
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
    val playbackPlayPauseBg: Boolean = true,
    val playbackSkipBg: Boolean = false,
    val playbackCornerRadiusDp: Float = 22f,
    val playbackM3ButtonGroup: Boolean = false,
    val playbackPlayPauseAccent: Boolean = false,
    val trackInfoShowTitle: Boolean = true,
    val trackInfoShowArtist: Boolean = true,
    val trackInfoShowAlbum: Boolean = false,
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
    val showDotRightSideInfo: Boolean = true,
    val showMinimizedFlashlightOutline: Boolean = true,
    val showExpandedFlashlightOutline: Boolean = true,
    val flashlightExpandedStyle: OverlayPreferences.FlashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.BEAM,
    val flashlightBeamBlur: Boolean = false,
    val flashlightBeamAccentColor: Boolean = true,
    val flashlightTapToToggle: Boolean = true,
    val islandPriorityOrder: List<IslandPriorityItem> = DEFAULT_ISLAND_PRIORITY_ORDER,
    val cutoutManualEnabled: Boolean? = null,
    val cutoutOffsetX: Float? = null,
    val cutoutOffsetY: Float? = null,
    val cutoutDiameterDp: Float? = null,
)

data class IslandPreset(
    val id: String,
    val name: String,
    val description: String,
    val config: IslandBackupConfig,
)

object IslandThemePresets {
    private const val BACKUP_VERSION = 2

    val builtInPresets: List<IslandPreset> = listOf(
        IslandPreset(
            id = "pixel_expressive",
            name = "Pixel Expressive",
            description = "Grouped bubbles, squircle card, soft canopy",
            config = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                minimizedMaxWidthPercent = 72f,
                islandCornerRadius = 38f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.CANOPY,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
                showMinimizedTitle = false,
                showMinimizedVisualizer = true,
                showMinimizedProgressOutline = true,
                showExpandedProgressOutline = true,
                albumArtGlowEnabled = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = true,
                bpmPulseEnabled = true,
                minimizedBpmPulseEnabled = true,
                expandedBpmPulseEnabled = true,
                pulseEntireIslandEnabled = false,
                showDotRightSideInfo = true,
            ),
        ),
        IslandPreset(
            id = "glass_blur",
            name = "Glass Blur",
            description = "Frosted translucent card with live blur",
            config = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                minimizedMaxWidthPercent = 76f,
                islandCornerRadius = 34f,
                islandSquircleEnabled = false,
                expandedBackgroundOpacity = 0.52f,
                backdropBlurEnabled = true,
                backdropBlurRadius = 32f,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.GRADIENT,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
                showMinimizedTitle = false,
                showMinimizedVisualizer = true,
                showMinimizedProgressOutline = true,
                showExpandedProgressOutline = true,
                albumArtGlowEnabled = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = false,
                bpmPulseEnabled = false,
                minimizedBpmPulseEnabled = false,
                expandedBpmPulseEnabled = false,
                pulseEntireIslandEnabled = false,
                showDotRightSideInfo = true,
            ),
        ),
        IslandPreset(
            id = "minimal_pill",
            name = "Minimal Pill",
            description = "Clean matte black, subtle dots, no glow",
            config = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.IPHONE,
                smallerBubbles = true,
                minimizedMaxWidthPercent = 60f,
                islandCornerRadius = 32f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.NONE,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.NESTED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.NONE,
                showMinimizedTitle = false,
                showMinimizedVisualizer = true,
                showMinimizedProgressOutline = false,
                showExpandedProgressOutline = false,
                albumArtGlowEnabled = false,
                showMinimizedDominantGlow = false,
                showExpandedDominantGlow = false,
                bpmPulseEnabled = false,
                minimizedBpmPulseEnabled = false,
                expandedBpmPulseEnabled = false,
                pulseEntireIslandEnabled = false,
                showDotRightSideInfo = false,
            ),
        ),
        IslandPreset(
            id = "music_studio",
            name = "Music Studio",
            description = "Blended artwork, BPM pulse, full-width pill",
            config = IslandBackupConfig(
                bubbleStyle = OverlayPreferences.BubbleStyle.MATERIAL_3,
                smallerBubbles = false,
                minimizedMaxWidthPercent = 92f,
                islandCornerRadius = 40f,
                islandSquircleEnabled = true,
                expandedBackgroundOpacity = 1f,
                backdropBlurEnabled = false,
                cameraCoverStyle = OverlayPreferences.CameraCoverStyle.SWOOP,
                minimizedAlbumArtStyle = OverlayPreferences.AlbumArtStyle.BLENDED,
                expandedAlbumArtStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
                showMinimizedTitle = true,
                showMinimizedVisualizer = true,
                showMinimizedProgressOutline = true,
                showExpandedProgressOutline = true,
                albumArtGlowEnabled = true,
                showMinimizedDominantGlow = true,
                showExpandedDominantGlow = true,
                bpmPulseEnabled = true,
                minimizedBpmPulseEnabled = true,
                expandedBpmPulseEnabled = true,
                pulseEntireIslandEnabled = true,
                showDotRightSideInfo = true,
            ),
        ),
    )

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
            add("playbackPlayPauseBg" to config.playbackPlayPauseBg.toString())
            add("playbackSkipBg" to config.playbackSkipBg.toString())
            add("playbackCornerRadiusDp" to config.playbackCornerRadiusDp.toString())
            add("playbackM3ButtonGroup" to config.playbackM3ButtonGroup.toString())
            add("playbackPlayPauseAccent" to config.playbackPlayPauseAccent.toString())
            add("trackInfoShowTitle" to config.trackInfoShowTitle.toString())
            add("trackInfoShowArtist" to config.trackInfoShowArtist.toString())
            add("trackInfoShowAlbum" to config.trackInfoShowAlbum.toString())
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
            add("showDotRightSideInfo" to config.showDotRightSideInfo.toString())
            add("showMinimizedFlashlightOutline" to config.showMinimizedFlashlightOutline.toString())
            add("showExpandedFlashlightOutline" to config.showExpandedFlashlightOutline.toString())
            add("flashlightExpandedStyle" to quote(config.flashlightExpandedStyle.name))
            add("flashlightBeamBlur" to config.flashlightBeamBlur.toString())
            add("flashlightBeamAccentColor" to config.flashlightBeamAccentColor.toString())
            add("flashlightTapToToggle" to config.flashlightTapToToggle.toString())
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
        val defaults = IslandBackupConfig()
        val legacyBpmPulse = fields["bpmPulseEnabled"]?.toBooleanStrictOrNull() ?: defaults.bpmPulseEnabled
        val rawPriority = fields["islandPriorityOrder"]
        val parsedPriority = if (!rawPriority.isNullOrBlank()) {
            normalizeIslandPriorityOrder(
                rawPriority.split(",").mapNotNull { token ->
                    IslandPriorityItem.entries.firstOrNull { it.name == token.trim() }
                },
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
            expandedPlayerLayout = enumOrDefault(fields["expandedPlayerLayout"], defaults.expandedPlayerLayout),
            showProgressSquiggles = fields["showProgressSquiggles"]?.toBooleanStrictOrNull() ?: defaults.showProgressSquiggles,
            progressBarAccentColor = fields["progressBarAccentColor"]?.toBooleanStrictOrNull() ?: defaults.progressBarAccentColor,
            playbackPlayPauseBg = fields["playbackPlayPauseBg"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseBg,
            playbackSkipBg = fields["playbackSkipBg"]?.toBooleanStrictOrNull() ?: defaults.playbackSkipBg,
            playbackCornerRadiusDp = (fields["playbackCornerRadiusDp"]?.toFloatOrNull() ?: defaults.playbackCornerRadiusDp).coerceIn(0f, 32f),
            playbackM3ButtonGroup = fields["playbackM3ButtonGroup"]?.toBooleanStrictOrNull() ?: defaults.playbackM3ButtonGroup,
            playbackPlayPauseAccent = fields["playbackPlayPauseAccent"]?.toBooleanStrictOrNull() ?: defaults.playbackPlayPauseAccent,
            trackInfoShowTitle = fields["trackInfoShowTitle"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowTitle,
            trackInfoShowArtist = fields["trackInfoShowArtist"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowArtist,
            trackInfoShowAlbum = fields["trackInfoShowAlbum"]?.toBooleanStrictOrNull() ?: defaults.trackInfoShowAlbum,
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
            waveformBarSpacing = (fields["waveformBarSpacing"]?.toFloatOrNull() ?: defaults.waveformBarSpacing).coerceIn(0.5f, 6f),
            showDotRightSideInfo = fields["showDotRightSideInfo"]?.toBooleanStrictOrNull() ?: defaults.showDotRightSideInfo,
            showMinimizedFlashlightOutline = fields["showMinimizedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedFlashlightOutline,
            showExpandedFlashlightOutline = fields["showExpandedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedFlashlightOutline,
            flashlightExpandedStyle = enumOrDefault(fields["flashlightExpandedStyle"], defaults.flashlightExpandedStyle),
            flashlightBeamBlur = fields["flashlightBeamBlur"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamBlur,
            flashlightBeamAccentColor = fields["flashlightBeamAccentColor"]?.toBooleanStrictOrNull() ?: defaults.flashlightBeamAccentColor,
            flashlightTapToToggle = fields["flashlightTapToToggle"]?.toBooleanStrictOrNull() ?: defaults.flashlightTapToToggle,
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
            playbackPlayPauseBg = playback.playPauseBackground,
            playbackSkipBg = playback.skipBackground,
            playbackCornerRadiusDp = playback.cornerRadiusDp,
            playbackM3ButtonGroup = playback.material3ButtonGroup,
            playbackPlayPauseAccent = playback.playPauseAccentColor,
            trackInfoShowTitle = trackInfo.showTitle,
            trackInfoShowArtist = trackInfo.showArtist,
            trackInfoShowAlbum = trackInfo.showAlbum,
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
            showDotRightSideInfo = OverlayPreferences.isShowDotRightSideInfoEnabled(context),
            showMinimizedFlashlightOutline = OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context),
            showExpandedFlashlightOutline = OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context),
            flashlightExpandedStyle = OverlayPreferences.getFlashlightExpandedStyle(context),
            flashlightBeamBlur = OverlayPreferences.isFlashlightBeamBlurEnabled(context),
            flashlightBeamAccentColor = OverlayPreferences.isFlashlightBeamAccentColorEnabled(context),
            flashlightTapToToggle = OverlayPreferences.isFlashlightTapToToggleEnabled(context),
            islandPriorityOrder = OverlayPreferences.getIslandPriorityOrder(context),
            cutoutManualEnabled = cutout.isManualEnabled,
            cutoutOffsetX = cutout.offsetX,
            cutoutOffsetY = cutout.offsetY,
            cutoutDiameterDp = cutout.customDiameterDp,
        )
        return toJson(config)
    }

    fun applyToPreferences(context: Context, config: IslandBackupConfig, includeCutout: Boolean = true) {
        OverlayPreferences.setBubbleStyle(context, config.bubbleStyle)
        OverlayPreferences.setSmallerBubblesEnabled(context, config.smallerBubbles)
        OverlayPreferences.setMinimizedMaxWidthPercent(context, config.minimizedMaxWidthPercent)
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
        OverlayPreferences.setShowDotRightSideInfoEnabled(context, config.showDotRightSideInfo)
        OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, config.showMinimizedFlashlightOutline)
        OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, config.showExpandedFlashlightOutline)
        OverlayPreferences.setFlashlightExpandedStyle(context, config.flashlightExpandedStyle)
        OverlayPreferences.setFlashlightBeamBlurEnabled(context, config.flashlightBeamBlur)
        OverlayPreferences.setFlashlightBeamAccentColorEnabled(context, config.flashlightBeamAccentColor)
        OverlayPreferences.setFlashlightTapToToggleEnabled(context, config.flashlightTapToToggle)
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
