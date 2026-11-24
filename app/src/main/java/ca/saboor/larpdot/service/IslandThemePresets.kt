package ca.saboor.larpdot.service

import android.content.Context

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
    val expandedAlbumArtStyle: OverlayPreferences.ExpandedBackgroundStyle = OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    val expandedPlayerLayout: OverlayPreferences.ExpandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS,
    val showMinimizedTitle: Boolean = false,
    val showMinimizedVisualizer: Boolean = true,
    val showMinimizedProgressOutline: Boolean = true,
    val showExpandedProgressOutline: Boolean = true,
    val albumArtGlowEnabled: Boolean = true,
    val showMinimizedDominantGlow: Boolean = true,
    val showExpandedDominantGlow: Boolean = true,
    val bpmPulseEnabled: Boolean = true,
    val pulseEntireIslandEnabled: Boolean = false,
    val showDotRightSideInfo: Boolean = true,
    val showMinimizedFlashlightOutline: Boolean = true,
    val showExpandedFlashlightOutline: Boolean = true,
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
    private const val BACKUP_VERSION = 1

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
            add("expandedAlbumArtStyle" to quote(config.expandedAlbumArtStyle.name))
            add("expandedPlayerLayout" to quote(config.expandedPlayerLayout.name))
            add("showMinimizedTitle" to config.showMinimizedTitle.toString())
            add("showMinimizedVisualizer" to config.showMinimizedVisualizer.toString())
            add("showMinimizedProgressOutline" to config.showMinimizedProgressOutline.toString())
            add("showExpandedProgressOutline" to config.showExpandedProgressOutline.toString())
            add("albumArtGlowEnabled" to config.albumArtGlowEnabled.toString())
            add("showMinimizedDominantGlow" to config.showMinimizedDominantGlow.toString())
            add("showExpandedDominantGlow" to config.showExpandedDominantGlow.toString())
            add("bpmPulseEnabled" to config.bpmPulseEnabled.toString())
            add("pulseEntireIslandEnabled" to config.pulseEntireIslandEnabled.toString())
            add("showDotRightSideInfo" to config.showDotRightSideInfo.toString())
            add("showMinimizedFlashlightOutline" to config.showMinimizedFlashlightOutline.toString())
            add("showExpandedFlashlightOutline" to config.showExpandedFlashlightOutline.toString())
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
            expandedAlbumArtStyle = enumOrDefault(fields["expandedAlbumArtStyle"], defaults.expandedAlbumArtStyle),
            expandedPlayerLayout = enumOrDefault(fields["expandedPlayerLayout"], defaults.expandedPlayerLayout),
            showMinimizedTitle = fields["showMinimizedTitle"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedTitle,
            showMinimizedVisualizer = fields["showMinimizedVisualizer"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedVisualizer,
            showMinimizedProgressOutline = fields["showMinimizedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedProgressOutline,
            showExpandedProgressOutline = fields["showExpandedProgressOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedProgressOutline,
            albumArtGlowEnabled = fields["albumArtGlowEnabled"]?.toBooleanStrictOrNull() ?: defaults.albumArtGlowEnabled,
            showMinimizedDominantGlow = fields["showMinimizedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedDominantGlow,
            showExpandedDominantGlow = fields["showExpandedDominantGlow"]?.toBooleanStrictOrNull() ?: defaults.showExpandedDominantGlow,
            bpmPulseEnabled = fields["bpmPulseEnabled"]?.toBooleanStrictOrNull() ?: defaults.bpmPulseEnabled,
            pulseEntireIslandEnabled = fields["pulseEntireIslandEnabled"]?.toBooleanStrictOrNull() ?: defaults.pulseEntireIslandEnabled,
            showDotRightSideInfo = fields["showDotRightSideInfo"]?.toBooleanStrictOrNull() ?: defaults.showDotRightSideInfo,
            showMinimizedFlashlightOutline = fields["showMinimizedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showMinimizedFlashlightOutline,
            showExpandedFlashlightOutline = fields["showExpandedFlashlightOutline"]?.toBooleanStrictOrNull() ?: defaults.showExpandedFlashlightOutline,
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
            expandedAlbumArtStyle = OverlayPreferences.getExpandedAlbumArtStyle(context),
            expandedPlayerLayout = OverlayPreferences.getExpandedPlayerLayout(context),
            showMinimizedTitle = OverlayPreferences.isShowMinimizedTitleEnabled(context),
            showMinimizedVisualizer = OverlayPreferences.isShowMinimizedVisualizerEnabled(context),
            showMinimizedProgressOutline = OverlayPreferences.isShowMinimizedProgressOutlineEnabled(context),
            showExpandedProgressOutline = OverlayPreferences.isShowExpandedProgressOutlineEnabled(context),
            albumArtGlowEnabled = OverlayPreferences.albumArtGlowFlow.value,
            showMinimizedDominantGlow = OverlayPreferences.isShowMinimizedDominantColorGlowEnabled(context),
            showExpandedDominantGlow = OverlayPreferences.isShowExpandedDominantColorGlowEnabled(context),
            bpmPulseEnabled = OverlayPreferences.isBpmPulseEnabled(context),
            pulseEntireIslandEnabled = OverlayPreferences.isPulseEntireIslandEnabled(context),
            showDotRightSideInfo = OverlayPreferences.isShowDotRightSideInfoEnabled(context),
            showMinimizedFlashlightOutline = OverlayPreferences.isShowMinimizedFlashlightOutlineEnabled(context),
            showExpandedFlashlightOutline = OverlayPreferences.isShowExpandedFlashlightOutlineEnabled(context),
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
        OverlayPreferences.setExpandedAlbumArtStyle(context, config.expandedAlbumArtStyle)
        OverlayPreferences.setExpandedPlayerLayout(context, config.expandedPlayerLayout)
        OverlayPreferences.setShowMinimizedTitleEnabled(context, config.showMinimizedTitle)
        OverlayPreferences.setShowMinimizedVisualizerEnabled(context, config.showMinimizedVisualizer)
        OverlayPreferences.setShowMinimizedProgressOutlineEnabled(context, config.showMinimizedProgressOutline)
        OverlayPreferences.setShowExpandedProgressOutlineEnabled(context, config.showExpandedProgressOutline)
        OverlayPreferences.setAlbumArtGlowEnabled(context, config.albumArtGlowEnabled)
        OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, config.showMinimizedDominantGlow)
        OverlayPreferences.setShowExpandedDominantColorGlowEnabled(context, config.showExpandedDominantGlow)
        OverlayPreferences.setBpmPulseEnabled(context, config.bpmPulseEnabled)
        OverlayPreferences.setPulseEntireIslandEnabled(context, config.pulseEntireIslandEnabled)
        OverlayPreferences.setShowDotRightSideInfoEnabled(context, config.showDotRightSideInfo)
        OverlayPreferences.setShowMinimizedFlashlightOutlineEnabled(context, config.showMinimizedFlashlightOutline)
        OverlayPreferences.setShowExpandedFlashlightOutlineEnabled(context, config.showExpandedFlashlightOutline)

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
