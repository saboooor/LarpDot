package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import ca.saboor.larpdot.service.OverlayPreferences
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
private val islandMinimizeStretch = keyframes {
    durationMillis = 525
    0f at 50 using minimizeStretchEasing
    0.12f at 325 using minimizeStretchEasing
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
    onExpandedBoundsChanged: (Rect) -> Unit = {},
    compactPressScale: Float,
    compactStretch: Float,
    customIsland: CustomIsland? = null,
    interactiveProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val statusArrivalOffset = rememberStatusArrivalOffset(notificationActivity, !isExpanded && expandedType == IslandType.NOTIFICATION_ACTIVITY)
    val morphProgress = remember { Animatable(0f) }
    val expansion by remember {
        derivedStateOf {
            val progress = morphProgress.value.coerceIn(0f, 1f)
            if (progress <= 0.5f) easeMorphDistance(progress, 0.1f)
            else 1f - easeMorphDistance(1f - progress, 0.1f)
        }
    }
    val contentProgress = remember { Animatable(0f) }
    val minimizeStretch = remember { Animatable(0f) }
    var collapseFrom by remember { mutableStateOf<Float?>(null) }
    val isInteractive = interactiveProgress != null

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
        val target = if (isExpanded) 1f else 0f
        val currentClamped = morphProgress.value.coerceIn(0f, 1f)
        val remaining = (if (isExpanded) 1f - currentClamped else currentClamped).coerceIn(0.28f, 1f)
        val contentDuration = (ISLAND_MORPH_DURATION_MS * remaining).toInt().coerceAtLeast(140)
        val shellDuration = (ISLAND_SHELL_MORPH_DURATION_MS * remaining).toInt().coerceAtLeast(180)
        val contentAnimation = launch {
            contentProgress.animateTo(
                targetValue = target,
                animationSpec = LarpMotion.islandVisibility(durationMillis = contentDuration),
            )
        }
        val stretchAnimation = if (!isExpanded && currentClamped > 0.6f) launch {
            minimizeStretch.animateTo(0f, animationSpec = islandMinimizeStretch)
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
    val hasBubbleBefore = compactFrame.hasBubbleBefore
    val hasBubbleAfter = compactFrame.hasBubbleAfter

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val drawWindowWidthPx = LocalView.current.width.takeIf { it > 0 }?.toFloat()
        ?: with(density) { screenWidthDp.toPx() }

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val compactPillThickness = cameraGeometry.thickness.dp
    val bubbleStyle by OverlayPreferences.bubbleStyleFlow.collectAsState()
    val smallerBubbles by OverlayPreferences.smallerBubblesFlow.collectAsState()
    val secondaryBubbleThickness = secondaryBubbleThicknessDp(compactPillThickness.value, smallerBubbles).dp
    val cutoutCenterYDp = with(density) { cutoutInfo.centerY.toDp() }
    // The expanded card starts 14dp below the screen top; the camera dot itself is the top inset.
    val expandedCutoutClearanceDp = if (isLandscape) 0.dp else
        (cutoutCenterYDp + cutoutDiameterDp / 2f - 14.dp).coerceAtLeast(0.dp)

    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
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
    val customSpanFull = customIsland?.main?.spanFullIsland == true
    val cardWidth = when (expandedType) {
        IslandType.CUSTOM -> minOf(
            (customIsland?.main?.width ?: 300).dp + if (customSpanFull) 0.dp else customIslandCardPadding * 2,
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
    val customPadding = customIslandExpandedPadding(expandedCutoutClearanceDp, customSpanFull)
    val cardHeight = when (expandedType) {
        IslandType.CUSTOM -> minOf(
            (customIsland?.main?.height ?: 150).dp +
                customPadding.calculateTopPadding() + customPadding.calculateBottomPadding(),
            (screenHeightDp - 28.dp).coerceAtLeast(1.dp),
        )
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            (if (expandedCutoutClearanceDp > 0.dp) expandedCutoutClearanceDp else 8.dp) + 56.dp
        } else if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.height
        IslandType.MEDIA -> measuredMediaHeight ?: with(density) { sourceFrame.bounds.height.toDp() }
    }
    val mediaCardHorizontalMarginDp = (screenWidthDp - cardWidth) / 2f

    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(cardWidth, cardHeight) / 2f)

    val showMinimizedVisualizer by OverlayPreferences.showMinimizedVisualizerFlow.collectAsState()
    val visualizerMotion = if (expandedType == IslandType.MEDIA && expandedElementVisibility.visualizer &&
        (if (fromTinyDot) compactFrame.isMiniPill else showMinimizedVisualizer)
    ) {
        rememberIslandElementMotion { expansion }
    } else null
    val hasActivityMotion = expandedType == IslandType.FLASHLIGHT || expandedType == IslandType.NOTIFICATION_ACTIVITY
    val iconMotion = if (hasActivityMotion) rememberIslandElementMotion { expansion } else null
    val progressMotion = if (hasActivityMotion) rememberIslandElementMotion { expansion } else null
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
    val expandedHeight = if (expandedType == IslandType.MEDIA && measuredMediaHeight == null) {
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
    val cornerMix = if (useRoundedCorners) motionFraction(expansion, 0f, 0.12f) else expansion
    val innerCorner = androidx.compose.ui.unit.lerp(
        (startCorner * 0.3f).coerceAtMost(6.dp),
        currentCornerRadius,
        cornerMix,
    )
    val grouped = bubbleStyle == OverlayPreferences.BubbleStyle.MATERIAL_3
    val beforeCorner = if (grouped && hasBubbleBefore) innerCorner else currentCornerRadius
    val afterCorner = if (grouped && hasBubbleAfter) innerCorner else currentCornerRadius
    val blurRadius by OverlayPreferences.backdropBlurRadius.collectAsState()
    val blurAvailable = blurEnabled && rememberBackdropBlurStatus() == BackdropBlurStatus.AVAILABLE
    val blurReveal = motionFraction(expansion, 0.12f, 0.88f)
    val useBackdropBlur = blurAvailable && blurReveal > 0f
    // Native backdrop blur can only follow evenly rounded corners.
    val containerShape = if (useRoundedCorners && (!grouped || expansion >= 0.12f)) {
        RoundedCornerShape(currentCornerRadius)
    } else squircleShape(
        radiusPx = with(density) { currentCornerRadius.toPx() },
        curvatureFactor = if (useRoundedCorners) 0.5522848f else 0.5522848f + 0.2477152f * expansion,
        topLeftRadiusPx = with(density) { beforeCorner.toPx() },
        topRightRadiusPx = with(density) { (if (isLandscape) beforeCorner else afterCorner).toPx() },
        bottomRightRadiusPx = with(density) { afterCorner.toPx() },
        bottomLeftRadiusPx = with(density) { (if (isLandscape) afterCorner else beforeCorner).toPx() },
    )

    val contentExpansion = contentProgress.value.coerceIn(0f, 1f)
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
                    val rawProgress = morphProgress.value
                    val overshoot = (rawProgress - rawProgress.coerceIn(0f, 1f)).coerceIn(-0.035f, 0.035f)
                    val shellScale = pressScale * (1f + easeMorphDistance(overshoot.coerceAtLeast(0f), 0.01f))
                    val upwardStretch = minimizeStretch.value
                    val horizontalSqueeze = upwardStretch * 0.5f
                    val swipeScaleX = 1f + kotlin.math.abs(stretch) * if (fromTinyDot) 0.22f else 0.18f
                    scaleX = shellScale * swipeScaleX * (1f - horizontalSqueeze)
                    val upwardTravel = startHeight.toPx() * upwardStretch
                    scaleY = shellScale + upwardTravel / size.height.coerceAtLeast(1f)
                    translationY = -upwardTravel / 2f
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        overshoot > 0f && !isLandscape -> TransformOrigin(0.5f, 0f)
                        overshoot > 0f -> TransformOrigin(if (cutoutInfo.centerX < drawWindowWidthPx / 2f) 0f else 1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    alpha = when {
                        isExpanded || returnFrame != null -> 1f
                        isInteractive -> (expansion * 4f).coerceIn(0f, 1f)
                        else -> expansion
                    }
                }
                .islandOutline(containerShape, currentCornerRadius)
                .shadow(currentElevation, containerShape, clip = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clip(containerShape),
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
                                beforeMain = hasBubbleAfter,
                                bubbleStyle = bubbleStyle,
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
                                onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                            )
                        }
                    }
                }

                if (expandedAlpha > 0.01f || (expandedType == IslandType.MEDIA && isExpanded) || (expandedType == IslandType.CUSTOM && isInteractive) || keepExpandedElements) {
                    val expandedContentSize = if (expandedType == IslandType.MEDIA) {
                        Modifier.requiredWidth(expandedWidth)
                            .wrapContentHeight(unbounded = true)
                            .heightIn(max = maxMediaHeight)
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
                        if (expandedType != IslandType.MEDIA) CameraCover(
                            style = cameraCoverStyle,
                            centerX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                            centerY = cutoutCenterYDp - panelPlacement.top.dp,
                            diameter = cutoutDiameterDp,
                        )
                        when (expandedType) {
                            IslandType.CUSTOM -> customIsland?.let {
                                ExpandedCustomIslandContent(it, expandedCutoutClearanceDp, interactive = isExpanded)
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
                                    cutoutInfo = cutoutInfo,
                                    cardHorizontalMarginDp = if (expandedPlayerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE) {
                                        mediaCardHorizontalMarginDp
                                    } else {
                                        horizontalMarginDp
                                    },
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
                    TravelingArtwork(
                        bitmap = remember(artwork) { artwork.asImageBitmap() },
                        motion = artworkMotion,
                        glow = rememberAlbumArtGlow(mediaInfo),
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
