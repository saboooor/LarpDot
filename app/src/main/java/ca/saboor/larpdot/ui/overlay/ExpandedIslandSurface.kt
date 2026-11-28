package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandTapAction
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.service.runTapAction
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.ui.theme.motionBlur
import ca.saboor.larpdot.ui.theme.motionFraction
import ca.saboor.larpdot.ui.theme.rememberMotionBlur
import kotlinx.coroutines.launch

private const val ISLAND_MORPH_DURATION_MS = 360
private const val ISLAND_SHELL_MORPH_DURATION_MS = 515
private const val ISLAND_OVERSHOOT_PEAK = 0.87f
private val islandMorphCurve = CubicBezierEasing(0.34f, 0f, 0.48f, 1.24f)
private val islandMorphEasing = Easing { fraction ->
    val peak = ISLAND_OVERSHOOT_PEAK * ISLAND_MORPH_DURATION_MS / ISLAND_SHELL_MORPH_DURATION_MS
    val curveFraction = if (fraction <= peak) {
        fraction * ISLAND_OVERSHOOT_PEAK / peak
    } else {
        lerp(ISLAND_OVERSHOOT_PEAK, 1f, (fraction - peak) / (1f - peak))
    }
    islandMorphCurve.transform(curveFraction)
}
private val minimizeStretchEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private fun islandMinimizeStretch(speedMultiplier: Float = 1f) = keyframes {
    val scale = speedMultiplier.coerceIn(0.5f, 2.0f)
    durationMillis = (525 * scale).toInt().coerceAtLeast(180)
    0f at (50 * scale).toInt().coerceAtLeast(16) using minimizeStretchEasing
    0.12f at (325 * scale).toInt().coerceAtLeast(90) using minimizeStretchEasing
}

// Reach zero with zero velocity while matching the original slope outside the eased range.
private fun easeMorphDistance(distance: Float, range: Float): Float {
    val fraction = (distance / range).coerceAtMost(1f)
    return distance * fraction * (2f - fraction)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IslandSurfaceOverlay(
    cutoutInfo: CutoutInfo,
    mediaInfo: MediaTrackInfo,
    notificationActivity: NotificationActivityInfo?,
    expandedType: IslandType,
    fromTinyDot: Boolean,
    isExpanded: Boolean,
    sourceFrame: CompactIslandFrame,
    returnFrame: CompactIslandFrame?,
    onCollapse: () -> Unit,
    onFirstFrameDrawn: () -> Unit,
    onExitFinished: () -> Unit,
    compactPressScale: Float,
    compactStretch: Float,
    modifier: Modifier = Modifier,
    onExpandedBoundsChanged: (Rect) -> Unit = {},
    customIsland: CustomIsland? = null,
    interactiveProgress: Float? = null,
) {
    val statusArrivalOffset = rememberStatusArrivalOffset(notificationActivity, !isExpanded && expandedType == IslandType.NOTIFICATION_ACTIVITY)
    val morphProgress = remember { Animatable(0f) }
    val contentProgress = remember { Animatable(0f) }
    val minimizeStretch = remember { Animatable(0f) }
    var collapseFrom by remember { mutableStateOf<Float?>(null) }
    val isInteractive = interactiveProgress != null
    val currentInteractiveProgressState = rememberUpdatedState(interactiveProgress)
    val rawMorphProgress by remember {
        derivedStateOf { currentInteractiveProgressState.value ?: morphProgress.value }
    }
    val expansion by remember {
        derivedStateOf {
            val progress = rawMorphProgress.coerceIn(0f, 1f)
            if (progress <= 0.5f) easeMorphDistance(progress, 0.1f)
            else 1f - easeMorphDistance(1f - progress, 0.1f)
        }
    }
    val contentExpansion by remember {
        derivedStateOf { (currentInteractiveProgressState.value ?: contentProgress.value).coerceIn(0f, 1f) }
    }

    LaunchedEffect(isInteractive) {
        if (isInteractive) {
            collapseFrom = null
            minimizeStretch.snapTo(0f)
            withFrameNanos { }
            onFirstFrameDrawn()
        }
    }

    LaunchedEffect(interactiveProgress) {
        val pull = interactiveProgress ?: return@LaunchedEffect
        morphProgress.snapTo(pull)
        contentProgress.snapTo(pull.coerceIn(0f, 1f))
    }

    LaunchedEffect(isExpanded, isInteractive) {
        if (isInteractive) return@LaunchedEffect
        if (isExpanded) {
            collapseFrom = null
            minimizeStretch.snapTo(0f)
            // Lay out the shared elements before handing the compact surface to the morph.
            withFrameNanos { }
            onFirstFrameDrawn()
        } else {
            collapseFrom = expansion
        }
        val speedMultiplier = OverlayPreferences.animationSpeedFlow.value.durationMultiplier
        val target = if (isExpanded) 1f else 0f
        val currentClamped = morphProgress.value.coerceIn(0f, 1f)
        val remaining = (if (isExpanded) 1f - currentClamped else currentClamped).coerceIn(0.28f, 1f)
        val contentDuration = (ISLAND_MORPH_DURATION_MS * remaining).toInt().coerceAtLeast(140)
        val shellDuration = LarpMotion.scaleIslandDuration(
            baseMillis = (ISLAND_SHELL_MORPH_DURATION_MS * remaining).toInt().coerceAtLeast(180),
            multiplier = speedMultiplier,
        )
        val contentAnimation = launch {
            contentProgress.animateTo(
                targetValue = target,
                animationSpec = LarpMotion.islandVisibility(durationMillis = contentDuration),
            )
        }
        val stretchAnimation = if (!isExpanded && currentClamped > 0.6f) launch {
            minimizeStretch.animateTo(0f, animationSpec = islandMinimizeStretch(speedMultiplier))
        } else null
        morphProgress.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = shellDuration, easing = islandMorphEasing),
        )
        contentAnimation.join()
        stretchAnimation?.join()
        if (!isExpanded) onExitFinished()
    }

    val handoff = collapseFrom?.let { start ->
        if (start <= 0f) 1f else motionFraction(start - expansion, 0f, start)
    } ?: 0f
    val compactFrame = returnFrame?.let { sourceFrame.toward(it, handoff) } ?: sourceFrame
    val startBounds = compactFrame.bounds

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val drawView = LocalView.current
    val drawWindowWidthPx = drawView.width.takeIf { it > 0 }?.toFloat()
        ?: with(density) { screenWidthDp.toPx() }

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val islandOutlineMode by OverlayPreferences.islandOutlineModeFlow.collectAsState()
    val isBackgroundPitchBlack by ca.saboor.larpdot.service.IslandBackdropLuminanceTracker.isBackgroundPitchBlack.collectAsState()
    val outlineVisibility by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ca.saboor.larpdot.service.resolveIslandOutlineVisible(islandOutlineMode, isBackgroundPitchBlack)) 1f else 0f,
        animationSpec = LarpMotion.fade(220),
        label = "expanded_outline_visibility",
    )
    val smallerBubblesMode by OverlayPreferences.smallerBubblesModeFlow.collectAsState()
    val secondaryBubbleThickness = secondaryBubbleThicknessDp(
        compactPillThickness.value,
        compactFrame.smallerSecondary || smallerBubblesMode == OverlayPreferences.SmallerBubblesMode.ALWAYS,
    ).dp
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    // The expanded card starts 14dp below the screen top; the camera dot itself is the top inset.
    val expandedCutoutClearanceDp = if (isLandscape) 0.dp else
        (cutoutCenterYDp + cutoutDiameterDp / 2f - 14.dp).coerceAtLeast(0.dp)

    val musicLeftSide by OverlayPreferences.minimizedMusicLeftSideFlow.collectAsState()
    val musicRightSide by OverlayPreferences.minimizedMusicRightSideFlow.collectAsState()
    val showMinimizedTitle = musicLeftSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE ||
        musicRightSide == OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE
    val notificationActivityStatus = notificationActivity?.let { rememberCompactActivityStatus(it) }
    val flashlightStatus = rememberCompactFlashlightStatus()
    val announcementActive = compactFrame.announcing
    val presentations = rememberIslandPresentations(
        mediaInfo, notificationActivity, notificationActivityStatus, flashlightStatus,
        getFlashlightStrengthFraction(
            FlashlightController.torchStrength.collectAsState().value,
            FlashlightController.maxStrength.collectAsState().value,
        ),
        compactPillThickness, cutoutDiameterDp, announcementActive,
    )
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val timestampPlacement by OverlayPreferences.progressTimestampPlacementFlow.collectAsState()

    val topMarginDp = (cutoutCenterYDp - compactPillThickness / 2f).coerceAtLeast(0.dp)
    val drawWindowWidthDp = with(density) { drawWindowWidthPx.toDp() }
    val cutoutCenterXDp = with(density) { cutoutInfo.centerX.toDp() }
    val horizontalMarginDp = if (isLandscape) {
        islandSideMargin(cutoutCenterXDp.value, drawWindowWidthDp.value, cutoutDiameterDp.value / 2f).dp
    } else topMarginDp.coerceAtLeast(14.dp)
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()
    val flashlightExpandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val wideFlashlight = flashlightExpandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER
    val spanActivityIconLeftOfCutout = shouldSpanActivityIconLeftOfCutout(
        isLandscape = isLandscape,
        topInsetDp = expandedCutoutClearanceDp.value,
        cutoutCenterXDp = cutoutCenterXDp.value,
        cutoutDiameterDp = cutoutDiameterDp.value,
    )
    val activitySize = expandedActivitySize(
        notificationActivity,
        maxWidth = (if (isLandscape) drawWindowWidthDp - horizontalMarginDp - 14.dp else screenWidthDp - 32.dp).coerceAtLeast(80.dp),
        maxHeight = (screenHeightDp - 36.dp).coerceAtLeast(80.dp),
        topInset = expandedCutoutClearanceDp,
        cutoutDiameterDp = cutoutDiameterDp,
        spanIconLeftOfCutout = spanActivityIconLeftOfCutout,
    )
    val mediaCardWidth = expandedMediaCardWidth(
        playerLayout = expandedPlayerLayout,
        screenWidthDp = screenWidthDp,
        horizontalMarginDp = horizontalMarginDp,
        drawWindowWidthDp = drawWindowWidthDp,
        isLandscape = isLandscape,
    )
    val cardWidth = when (expandedType) {
        IslandType.CUSTOM -> minOf(
            (customIsland?.main?.width ?: 300).dp + (customIsland?.main?.paddingX ?: 8).dp * 2,
            (if (isLandscape) drawWindowWidthDp - horizontalMarginDp - 14.dp else screenWidthDp - 28.dp)
                .coerceAtLeast(1.dp),
        )
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            if (isLandscape) minOf(drawWindowWidthDp - horizontalMarginDp - 14.dp, 420.dp)
            else screenWidthDp - (horizontalMarginDp * 2)
        } else minOf(screenWidthDp - 96.dp, 220.dp)
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.width
        IslandType.MEDIA -> mediaCardWidth
    }
    var measuredMediaHeight by remember { mutableStateOf<Dp?>(null) }
    val maxMediaHeight = expandedMediaCardMaxHeight(screenHeightDp, isLandscape)
    var measuredCustomHeight by remember(customIsland?.id) { mutableStateOf<Dp?>(null) }
    val maxCustomHeight = (minOf(screenHeightDp, with(density) { drawView.height.toDp() }
        .takeIf { it > 0.dp } ?: 460.dp) - 28.dp).coerceAtLeast(1.dp)
    val cardHeight = when (expandedType) {
        IslandType.CUSTOM -> measuredCustomHeight ?: with(density) { sourceFrame.bounds.height.toDp() }
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            (if (expandedCutoutClearanceDp > 0.dp) expandedCutoutClearanceDp else 8.dp) + 56.dp
        } else if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.height
        IslandType.MEDIA -> measuredMediaHeight ?: with(density) { sourceFrame.bounds.height.toDp() }
    }

    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(cardWidth, cardHeight) / 2f)

    val showMinimizedVisualizer = musicLeftSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER ||
        musicRightSide == OverlayPreferences.MinimizedMusicSideContent.VISUALIZER
    val visualizerMotion = if (expandedType == IslandType.MEDIA && expandedElementVisibility.visualizer &&
        (if (fromTinyDot) compactFrame.isMiniPill else showMinimizedVisualizer)
    ) {
        rememberIslandElementMotion { expansion }
    } else null
    val customWingLinks = if (expandedType == IslandType.CUSTOM) {
        resolveCustomWingMorphLinks(customIsland, fromSecondaryBubble = fromTinyDot)
    } else CustomWingMorphLinks()
    val hasActivityMotion = expandedType == IslandType.FLASHLIGHT || expandedType == IslandType.NOTIFICATION_ACTIVITY
    val iconMotion = if (hasActivityMotion || customWingLinks.leftCompactTarget != null) {
        rememberIslandElementMotion { expansion }
    } else null
    val progressMotion = if (hasActivityMotion || customWingLinks.rightCompactTarget != null) {
        rememberIslandElementMotion { expansion }
    } else null
    val elementMotions = listOfNotNull(progressMotion, iconMotion, visualizerMotion)
    val startWidth = with(density) { startBounds.width.toDp() }
    val startHeight = with(density) { startBounds.height.toDp() }
    val startOffsetX = with(density) { (startBounds.center.x - drawWindowWidthPx / 2f).toDp() }
    val startOffsetY = with(density) { startBounds.top.toDp() } - 14.dp
    val startCorner = minOf(startWidth, startHeight) / 2f

    val artwork = mediaInfo.albumArt?.takeIf { expandedType == IslandType.MEDIA && showExpandedAlbumArt }
    val artworkMotion = remember(fromTinyDot, artwork != null) {
        if (artwork != null) IslandArtworkMotion { expansion } else null
    }
    val expandedWidth by animateDpAsState(cardWidth, LarpMotion.resize(), label = "expanded_width")
    val awaitingHeightMeasurement = (expandedType == IslandType.MEDIA && measuredMediaHeight == null) ||
        (expandedType == IslandType.CUSTOM && measuredCustomHeight == null)
    val expandedHeight = if (awaitingHeightMeasurement) {
        cardHeight
    } else {
        animateDpAsState(cardHeight, LarpMotion.resize(), label = "expanded_height").value
    }
    val currentWidth = androidx.compose.ui.unit.lerp(startWidth, expandedWidth, expansion)
    val currentHeight = androidx.compose.ui.unit.lerp(startHeight, expandedHeight, expansion)
    // Expand inward from the camera's edge, keeping the panel clear of the hole punch.
    val panelPlacement = islandPanelPlacement(
        drawWindowWidthDp.value, screenHeightDp.value, cutoutCenterXDp.value,
        cutoutCenterYDp.value, cutoutDiameterDp.value / 2f, expandedWidth.value, expandedHeight.value, isLandscape,
    )
    val expandedOffsetX = panelPlacement.offsetX.dp
    val expandedOffsetY = panelPlacement.top.dp - 14.dp
    val elementPath = with(density) {
        IslandElementPath(
            compactShellCenter = startBounds.center,
            expandedShellCenter = Offset(
                drawWindowWidthPx / 2f + expandedOffsetX.toPx(),
                (panelPlacement.top.dp + expandedHeight / 2f).toPx(),
            ),
            cameraCenter = Offset(cutoutInfo.centerX, cutoutInfo.centerY),
            cameraRadius = cutoutDiameterDp.toPx() / 2f,
            cameraClearance = cutoutDiameterDp.toPx() / 2f + 2.dp.toPx(),
            isLandscape = isLandscape,
            fromSecondaryBubble = fromTinyDot,
        )
    }
    val currentOffsetX = androidx.compose.ui.unit.lerp(startOffsetX, expandedOffsetX, expansion)
    val currentOffsetY = androidx.compose.ui.unit.lerp(startOffsetY, expandedOffsetY, expansion)
    val currentCornerRadius = androidx.compose.ui.unit.lerp(startCorner, expandedCornerRadiusDp, expansion)
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val useRoundedCorners = !squircleEnabled || blurEnabled
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val blurAvailable = blurEnabled && rememberBackdropBlurStatus() == BackdropBlurStatus.AVAILABLE
    val blurReveal = motionFraction(expansion, 0.12f, 0.88f)
    val useBackdropBlur = blurAvailable && blurReveal > 0f
    // Native backdrop blur can only follow evenly rounded corners.
    val containerShape = if (useRoundedCorners) {
        RoundedCornerShape(currentCornerRadius)
    } else squircleShape(
        radiusPx = with(density) { currentCornerRadius.toPx() },
        curvatureFactor = 0.5522848f + 0.2477152f * expansion,
    )

    val compactAlpha = 1f - motionFraction(contentExpansion, 0.04f, 0.54f)
    val expandedAlpha = motionFraction(contentExpansion, 0.23f, 0.85f)
    val expandedContentScale = 0.96f + (0.04f * contentExpansion)
    val expandedContentOffsetY = 10.dp * (1f - contentExpansion)
    val contentBlur = rememberMotionBlur()

    val surfacePress = rememberIslandPressFeedback(expandedType, isExpanded)

    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val backgroundAlpha = islandBackgroundAlpha(backgroundOpacity, motionFraction(expansion, 0.08f, 0.94f))

    val currentElevation = androidx.compose.ui.unit.lerp(4.dp, 12.dp, expansion)

    val targetExpandedBounds = remember(
        drawWindowWidthPx, screenHeightDp, cutoutCenterXDp, cutoutCenterYDp,
        cutoutDiameterDp, cardWidth, cardHeight, isLandscape, density,
    ) {
        val placement = islandPanelPlacement(
            drawWindowWidthDp.value, screenHeightDp.value, cutoutCenterXDp.value,
            cutoutCenterYDp.value, cutoutDiameterDp.value / 2f, cardWidth.value, cardHeight.value, isLandscape,
        )
        with(density) {
            val wPx = cardWidth.toPx()
            val hPx = cardHeight.toPx()
            val left = (drawWindowWidthPx - wPx) / 2f + placement.offsetX.dp.toPx()
            val top = placement.top.dp.toPx()
            Rect(left, top, left + wPx, top + hPx)
        }
    }
    LaunchedEffect(isExpanded, targetExpandedBounds) {
        if (isExpanded) onExpandedBoundsChanged(targetExpandedBounds)
    }

    val allowVerticalSwipeCollapse = isExpanded && !(expandedType == IslandType.FLASHLIGHT && !wideFlashlight)
    val context = LocalContext.current
    val expandedTapIsland = customIsland?.takeIf {
        isExpanded && expandedType == IslandType.CUSTOM &&
            it.tapAction != CustomIslandTapAction.EXPAND && it.tapAction != CustomIslandTapAction.NONE &&
            (!it.isSwipeDownOnly || it.tapAction == CustomIslandTapAction.OPEN_APP)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .absoluteOffset(x = currentOffsetX, y = currentOffsetY)
                .size(width = currentWidth, height = currentHeight)
                .then(surfacePress.modifier)
                .expandedIslandSwipeToCollapse(
                    identity = expandedType,
                    enabled = allowVerticalSwipeCollapse,
                    onCollapse = onCollapse,
                )
                .graphicsLayer {
                    translationX = statusArrivalOffset
                    val pressCarry = collapseFrom?.let { start ->
                        if (start <= 0f) 0f else (1f - start) * (expansion / start)
                    } ?: (1f - expansion)
                    val pressScale = maxOf(surfacePress.scale, 1f + (compactPressScale - 1f) * pressCarry)
                    val stretch = compactStretch * pressCarry
                    val rawProgress = rawMorphProgress
                    val overshoot = (rawProgress - rawProgress.coerceIn(0f, 1f)).coerceIn(-0.035f, 0.14f)
                    val positiveOvershoot = overshoot.coerceAtLeast(0f)
                    val shellScaleX = pressScale * (1f + easeMorphDistance(positiveOvershoot.coerceAtMost(0.035f), 0.01f) * 0.6f)
                    val shellScaleY = pressScale * (1f + easeMorphDistance(positiveOvershoot, 0.02f) * 0.55f)
                    val upwardStretch = minimizeStretch.value
                    val horizontalSqueeze = upwardStretch * 0.5f
                    val swipeScaleX = 1f + kotlin.math.abs(stretch) * if (fromTinyDot) 0.22f else 0.18f
                    scaleX = shellScaleX * swipeScaleX * (1f - horizontalSqueeze)
                    val upwardTravel = startHeight.toPx() * upwardStretch
                    scaleY = shellScaleY + upwardTravel / size.height.coerceAtLeast(1f)
                    translationY = -upwardTravel / 2f
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        positiveOvershoot > 0f && !isLandscape -> TransformOrigin(0.5f, 0f)
                        positiveOvershoot > 0f -> TransformOrigin(if (cutoutInfo.centerX < drawWindowWidthPx / 2f) 0f else 1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    alpha = when {
                        isExpanded || returnFrame != null -> 1f
                        isInteractive -> (expansion * 4f).coerceIn(0f, 1f)
                        else -> expansion
                    }
                }
                .islandOutline(containerShape, currentCornerRadius, outlineVisibility, enabled = outlineVisibility > 0.001f)
                .shadow(currentElevation, containerShape, clip = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clip(containerShape).then(
                    if (expandedTapIsland != null) Modifier.clickable(onClickLabel = expandedTapIsland.tapAction.label) {
                        expandedTapIsland.runTapAction(context) {}
                    } else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (useBackdropBlur) {
                    IslandBackdropBlur(
                        radiusPx = with(density) { blurRadius.dp.toPx() } * blurReveal,
                        cornerRadiusPx = with(density) { currentCornerRadius.toPx() },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = backgroundAlpha)))
                val keepCompactElements = elementMotions.isNotEmpty() && (!isExpanded || expansion < 1f)
                val keepExpandedElements = elementMotions.isNotEmpty() && (isExpanded || expansion > 0f)
                if (compactAlpha > 0.01f || (artworkMotion != null && !isExpanded) || keepCompactElements) {
                    Box(
                        modifier = Modifier
                            .requiredSize(startWidth, startHeight)
                            .graphicsLayer {
                                // Keep shared elements recordable even when their host has faded.
                                alpha = if (elementMotions.any { it.isMoving }) compactAlpha.coerceAtLeast(0.01f) else compactAlpha
                                renderEffect = contentBlur.motionBlur(1f - compactAlpha)
                            }
                            .artworkContainer(artworkMotion?.compact)
                            .elementMotionContainer(elementMotions.map { it.compact }),
                    ) {
                        if (fromTinyDot) {
                            SecondaryDotContent(
                                type = expandedType,
                                bubbleWidth = startWidth,
                                bubbleHeight = startHeight,
                                bubbleThickness = secondaryBubbleThickness,
                                isMiniPill = compactFrame.isMiniPill,
                                isLandscape = isLandscape,
                                notificationActivity = notificationActivity,
                                mediaInfo = mediaInfo,
                                notificationActivityStatus = notificationActivityStatus,
                                flashlightStatus = flashlightStatus,
                                artworkAnchor = artworkMotion?.compact,
                                visualizerAnchor = visualizerMotion?.compact,
                                iconAnchor = iconMotion?.compact,
                                progressAnchor = progressMotion?.compact,
                                customIsland = customIsland,
                            )
                        } else {
                            IslandCompactContent(
                                model = IslandModel(expandedType, {}, presentation = presentations.getValue(expandedType)),
                                media = mediaInfo, activity = notificationActivity,
                                diameter = cutoutDiameterDp, landscape = isLandscape,
                                showTitle = showMinimizedTitle,
                                announcing = announcementActive, trailingWingFraction = compactFrame.trailingFraction,
                                artworkAnchor = artworkMotion?.compact,
                                visualizerAnchor = visualizerMotion?.compact,
                                iconAnchor = iconMotion?.compact,
                                progressAnchor = progressMotion?.compact,
                                customIsland = customIsland,
                                onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                            )
                        }
                    }
                }

                val hasContentDrivenHeight = expandedType == IslandType.MEDIA || expandedType == IslandType.CUSTOM
                if (expandedAlpha > 0.01f || (hasContentDrivenHeight && isExpanded) ||
                    (expandedType == IslandType.CUSTOM && isInteractive) || keepExpandedElements
                ) {
                    val expandedContentSize = if (hasContentDrivenHeight) {
                        Modifier.requiredWidth(expandedWidth)
                            .wrapContentHeight(unbounded = true)
                            .heightIn(max = if (expandedType == IslandType.CUSTOM) maxCustomHeight else maxMediaHeight)
                    } else Modifier.requiredSize(expandedWidth, expandedHeight)
                    Box(
                        modifier = expandedContentSize
                            .graphicsLayer {
                                alpha = if (elementMotions.any { it.isMoving }) expandedAlpha.coerceAtLeast(0.01f) else expandedAlpha
                                renderEffect = contentBlur.motionBlur(1f - expandedAlpha)
                                scaleX = expandedContentScale
                                scaleY = expandedContentScale
                                translationY = with(density) { expandedContentOffsetY.toPx() }
                            }
                            .artworkContainer(artworkMotion?.expanded)
                            .elementMotionContainer(elementMotions.map { it.expanded }),
                    ) {
                        if (expandedType != IslandType.MEDIA && expandedType != IslandType.CUSTOM) CameraCover(
                            style = cameraCoverStyle,
                            centerX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                            centerY = cutoutCenterYDp - panelPlacement.top.dp,
                            diameter = cutoutDiameterDp,
                        )
                        when (expandedType) {
                            IslandType.CUSTOM -> customIsland?.let {
                                val expandedLeftAnchor = when {
                                    customWingLinks.leftCompactTarget == CustomWingSide.LEFT -> iconMotion?.expanded
                                    customWingLinks.rightCompactTarget == CustomWingSide.LEFT -> progressMotion?.expanded
                                    else -> null
                                }
                                val expandedRightAnchor = when {
                                    customWingLinks.rightCompactTarget == CustomWingSide.RIGHT -> progressMotion?.expanded
                                    customWingLinks.leftCompactTarget == CustomWingSide.RIGHT -> iconMotion?.expanded
                                    else -> null
                                }
                                ExpandedCustomIslandContent(
                                    it,
                                    expandedCutoutClearanceDp,
                                    interactive = isExpanded,
                                    modifier = Modifier.onSizeChanged { size ->
                                        measuredCustomHeight = with(density) { size.height.toDp() }
                                    },
                                    cameraCoverStyle = cameraCoverStyle,
                                    cutoutCenterX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                                    cutoutCenterY = cutoutCenterYDp - panelPlacement.top.dp,
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    leftWingAnchor = expandedLeftAnchor,
                                    rightWingAnchor = expandedRightAnchor,
                                )
                            }
                            IslandType.NOTIFICATION_ACTIVITY -> {
                                if (notificationActivity != null) {
                                    ExpandedNotificationActivityContent(
                                        activity = notificationActivity,
                                        cardWidth = cardWidth,
                                        onCollapse = onCollapse,
                                        topContentInset = expandedCutoutClearanceDp,
                                        spanIconLeftOfCutout = spanActivityIconLeftOfCutout,
                                        iconAnchor = iconMotion?.expanded,
                                        progressAnchor = progressMotion?.expanded,
                                    )
                                }
                            }
                            IslandType.FLASHLIGHT -> {
                                ExpandedFlashlightContent(
                                    topContentInset = expandedCutoutClearanceDp,
                                    iconAnchor = iconMotion?.expanded,
                                    progressAnchor = progressMotion?.expanded,
                                )
                            }
                            IslandType.MEDIA -> {
                                ExpandedIslandContent(
                                    mediaInfo = mediaInfo,
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    isExpanded = isExpanded,
                                    onCollapse = onCollapse,
                                    modifier = Modifier.onSizeChanged { size ->
                                        measuredMediaHeight = with(density) { size.height.toDp() }
                                    },
                                    cardWidth = expandedWidth,
                                    cutoutCenterX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                                    cutoutCenterY = cutoutCenterYDp - panelPlacement.top.dp,
                                    topContentInset = expandedCutoutClearanceDp,
                                    artworkAnchor = artworkMotion?.expanded,
                                    visualizerAnchor = visualizerMotion?.expanded,
                                    timestampPlacement = timestampPlacement,
                                )
                            }
                        }

                    }
                }
                if (artworkMotion != null && artwork != null) {
                    val minimizedAlbumArtStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
                    val minimizedShape by OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState()
                    val minimizedRotation by OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState()
                    val expandedShape by OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState()
                    val expandedRotation by OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState()
                    val compactUsesShape = minimizedAlbumArtStyle == OverlayPreferences.AlbumArtStyle.NESTED ||
                        (fromTinyDot && !compactFrame.isMiniPill)
                    val compactGlowShape = if (compactUsesShape) {
                        nestedAlbumArtShape(minimizedShape, minimizedRotation)
                    } else null
                    val compactGlowKey = if (compactUsesShape) {
                        AlbumArtGlowShapeKey.of(minimizedShape, minimizedRotation)
                    } else null
                    val expandedGlowShape = nestedAlbumArtShape(expandedShape, expandedRotation)
                    val expandedGlowKey = AlbumArtGlowShapeKey.of(expandedShape, expandedRotation)
                    TravelingArtwork(
                        bitmap = remember(artwork) { artwork.asImageBitmap() },
                        motion = artworkMotion,
                        compactGlow = rememberAlbumArtGlow(mediaInfo, compactGlowShape, compactGlowKey),
                        expandedGlow = rememberAlbumArtGlow(mediaInfo, expandedGlowShape, expandedGlowKey),
                        path = elementPath,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                for (motion in elementMotions) {
                    TravelingIslandElement(motion, elementPath, Modifier.matchParentSize())
                }
            }
        }
    }
}
