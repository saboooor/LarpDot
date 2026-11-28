package ca.saboor.larpdot.ui.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.OverlayPreferences

private fun compactTimestampWidthSample(durationMs: Long, timestamp: OverlayPreferences.MinimizedMusicTimestamp): String {
    val time = if (durationMs > 0L) formatTime(durationMs) else "88:88:88"
    val prefix = if (timestamp == OverlayPreferences.MinimizedMusicTimestamp.REMAINING) "-" else ""
    return prefix + time.map { if (it.isDigit()) '8' else it }.joinToString("")
}

@Composable
internal fun secondaryMusicRightInfoExtent(
    mediaInfo: MediaTrackInfo,
    timestamp: OverlayPreferences.MinimizedMusicTimestamp,
    showTitle: Boolean,
    visualizerWidth: Dp,
    isLandscape: Boolean,
): Dp {
    val info = compactMusicRightInfo(timestamp, showTitle && mediaInfo.title.isNotBlank(), visualizerWidth > 0.dp)
    if (info == CompactMusicRightInfo.NONE) return 0.dp
    if (info == CompactMusicRightInfo.VISUALIZER) return if (isLandscape) 12.dp else visualizerWidth.coerceAtMost(96.dp)

    val sample = if (info == CompactMusicRightInfo.TIMESTAMP) {
        compactTimestampWidthSample(mediaInfo.durationMs, timestamp)
    } else mediaInfo.title
    val textMeasurer = rememberTextMeasurer()
    val size = textMeasurer.measure(sample, MaterialTheme.typography.labelMedium, softWrap = false, maxLines = 1).size
    return with(LocalDensity.current) { size.width.toDp().coerceAtMost(96.dp) }
}

@Composable
internal fun configuredCompactMediaWings(
    mediaInfo: MediaTrackInfo,
    extraWidthDp: Float,
    showTitle: Boolean,
    announcing: Boolean,
    visualizerWidthDp: Float,
    cameraDiameterDp: Float,
    cameraCenterXPx: Float,
    landscape: Boolean,
    leadingBubbleExtentDp: Float = 0f,
    trailingBubbleExtentDp: Float = 0f,
    hasRightWingContent: Boolean = true,
    hasRightBubble: Boolean = false,
): CompactMediaWings {
    val albumArtStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val showProgressRing by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val leftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val hasTitleSide = leftSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE ||
        rightSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE
    val hasArtSide = leftSide == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART ||
        rightSide == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART
    val hasVisualizerSide = leftSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER ||
        rightSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER

    fun sideTimestampWidth(side: OverlayPreferences.MinimizedMusicSideContent): Float {
        val ts = timestampForMusicSide(side)
        if (ts == OverlayPreferences.MinimizedMusicTimestamp.NONE) return 0f
        return textMeasurer.measure(
            AnnotatedString(compactTimestampWidthSample(mediaInfo.durationMs, ts)),
            style = labelStyle, softWrap = false, maxLines = 1,
        ).size.width / density + 8f
    }

    fun textWidth(text: String): Float {
        val textStyle = if (announcing) labelStyle.copy(
            fontSize = 13.sp,
            fontWeight = labelStyle.fontWeight,
        ) else labelStyle
        return textMeasurer.measure(
            AnnotatedString(text), style = textStyle, softWrap = false, maxLines = 1,
        ).size.width / density
    }

    val requested = if (!landscape && announcing) {
        val thickness = cameraDiameterDp.dp
        val nestedArtworkSize = compactNestedArtworkSize(thickness)
        val renderedArtworkSize = if (showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L) {
            maxOf(nestedArtworkSize, compactBatteryRingDiameter(thickness))
        } else {
            nestedArtworkSize
        }
        val baseExtra = when {
            !hasArtSide -> compactMediaBaseExtraDp(OverlayPreferences.AlbumArtStyle.NESTED, thickness, false).value
            albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED -> cameraDiameterDp + renderedArtworkSize.value
            else -> compactMediaBaseExtraDp(albumArtStyle, thickness, showProgressRing).value
        }
        compactTextMediaWings(
            baseExtraDp = baseExtra,
            titleWidthDp = textWidth(mediaInfo.title.ifBlank { "Playing Track" }),
            artistWidthDp = textWidth(mediaInfo.artist.ifBlank { "Media Player" }),
            announcing = true,
            announcementArtWidthDp = if (leftSide == OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART &&
                albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED && mediaInfo.albumArt != null
            ) {
                val startInset = compactEndInset(thickness, renderedArtworkSize)
                (renderedArtworkSize + startInset + 4.dp).value
            } else 0f,
            announcementVisualizerWidthDp = if (rightSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER && visualizerWidthDp > 0f) {
                visualizerWidthDp + 4f
            } else 0f,
            announcementEndInsetDp = (cameraDiameterDp / 3f).coerceAtLeast(8f),
        )
    } else if (!landscape && (hasTitleSide || showTitle)) {
        val thickness = cameraDiameterDp.dp
        val nestedArtworkSize = compactNestedArtworkSize(thickness)
        val renderedArtworkSize = if (showProgressRing && mediaInfo.hasMedia && mediaInfo.durationMs > 0L) {
            maxOf(nestedArtworkSize, compactBatteryRingDiameter(thickness))
        } else {
            nestedArtworkSize
        }
        val baseExtra = when {
            !hasArtSide -> maxOf(extraWidthDp, sideTimestampWidth(leftSide) * 2f, sideTimestampWidth(rightSide) * 2f)
            albumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED -> cameraDiameterDp + renderedArtworkSize.value
            else -> compactMediaBaseExtraDp(albumArtStyle, thickness, showProgressRing).value
        }
        val baseWings = compactTextMediaWings(
            baseExtraDp = baseExtra,
            titleWidthDp = textWidth(mediaInfo.title),
            artistWidthDp = 0f,
            announcing = false,
        )
        val extraWing = maxOf(
            sideTimestampWidth(leftSide),
            sideTimestampWidth(rightSide),
            if (hasVisualizerSide && visualizerWidthDp > 0f) visualizerWidthDp + 8f else 0f,
        )
        CompactMediaWings(
            maxOf(baseWings.leadingDp, extraWing),
            maxOf(baseWings.trailingDp, extraWing),
        )
    } else {
        val timeExtraWidth = maxOf(sideTimestampWidth(leftSide), sideTimestampWidth(rightSide)) * 2f
        val artExtra = if (hasArtSide) extraWidthDp else maxOf(48f, timeExtraWidth)
        compactMediaWings(maxOf(artExtra, timeExtraWidth), if (hasVisualizerSide) visualizerWidthDp else 0f)
    }
    if (landscape) return requested
    val adjusted = collapseEmptyTrailingWing(requested, hasRightWingContent, hasRightBubble)
    return rememberFittedCompactWings(
        adjusted, cameraDiameterDp, cameraCenterXPx,
        leadingBubbleExtentDp, trailingBubbleExtentDp,
        symmetric = !announcing && (hasRightWingContent || !hasRightBubble),
    )
}

@Composable
internal fun rememberFittedCompactWings(
    requested: CompactMediaWings,
    cameraDiameterDp: Float,
    cameraCenterXPx: Float,
    leadingBubbleExtentDp: Float = 0f,
    trailingBubbleExtentDp: Float = 0f,
    symmetric: Boolean = true,
): CompactMediaWings {
    val percent by OverlayPreferences.minimizedMaxWidthPercent.collectAsState()
    val density = LocalDensity.current.density
    val screenWidth = LocalView.current.width.takeIf { it > 0 }?.let { it / density }
        ?: LocalConfiguration.current.screenWidthDp.toFloat()
    return fitCompactMediaWings(
        requested, cameraDiameterDp, screenWidth, cameraCenterXPx / density, percent,
        leadingBubbleExtentDp, trailingBubbleExtentDp, symmetric,
    )
}
