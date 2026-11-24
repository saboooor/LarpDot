package ca.saboor.larpdot.ui.overlay

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.ui.theme.motionBlur
import ca.saboor.larpdot.ui.theme.motionFraction
import ca.saboor.larpdot.ui.theme.rememberMotionBlur

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
    modifier: Modifier = Modifier,
) {
    val statusArrivalOffset = rememberStatusArrivalOffset(notificationActivity, !isExpanded && expandedType == IslandType.NOTIFICATION_ACTIVITY)
    val morphProgress = remember { Animatable(0f) }
    var collapseFrom by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            collapseFrom = null
            // Compose both artwork anchors before handing the compact surface to the morph.
            withFrameNanos { }
            onFirstFrameDrawn()
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.79f,
                    stiffness = 320f,
                    visibilityThreshold = 0.002f,
                ),
            )
        } else {
            collapseFrom = morphProgress.value.coerceIn(0f, 1f)
            morphProgress.animateTo(
                targetValue = 0f,
                animationSpec = LarpMotion.quickSpring(dampingRatio = 1f, stiffness = 440f, visibilityThreshold = 0.002f),
            )
            onExitFinished()
        }
    }

    val expansion = morphProgress.value.coerceIn(0f, 1f)
    val settleScale = 1f + (morphProgress.value - 1f).coerceIn(0f, 0.025f)
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
        compactPillThickness, announcementActive,
    )
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()

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
    val mediaCardSize = expandedMediaCardSize(
        mediaInfo = mediaInfo,
        playerLayout = expandedPlayerLayout,
        showExpandedAlbumArt = showExpandedAlbumArt,
        showAppActions = expandedElementVisibility.appActions,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        horizontalMarginDp = horizontalMarginDp,
        drawWindowWidthDp = drawWindowWidthDp,
        isLandscape = isLandscape,
    )
    val cardWidth = when (expandedType) {
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            if (isLandscape) minOf(drawWindowWidthDp - horizontalMarginDp - 14.dp, 420.dp)
            else screenWidthDp - (horizontalMarginDp * 2)
        } else minOf(screenWidthDp - 96.dp, 220.dp)
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.width
        IslandType.MEDIA -> mediaCardSize.width
    }
    val cardHeight = when (expandedType) {
        IslandType.FLASHLIGHT -> if (wideFlashlight) {
            (if (expandedCutoutClearanceDp > 0.dp) expandedCutoutClearanceDp else 8.dp) + 56.dp
        } else if (isLandscape) minOf(screenHeightDp - 36.dp, 290.dp) else 290.dp
        IslandType.NOTIFICATION_ACTIVITY -> activitySize.height
        IslandType.MEDIA -> mediaCardSize.height
    }
    val mediaCardHorizontalMarginDp = (screenWidthDp - cardWidth) / 2f

    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(cardWidth, cardHeight) / 2f)

    val showDotRightSideInfo by OverlayPreferences.showDotRightSideInfoFlow.collectAsState()
    val startWidth = with(density) { startBounds.width.toDp() }
    val startHeight = with(density) { startBounds.height.toDp() }
    val startOffsetX = with(density) { (startBounds.center.x - drawWindowWidthPx / 2f).toDp() }
    val startOffsetY = with(density) { startBounds.top.toDp() } - 14.dp
    val startCorner = minOf(startWidth, startHeight) / 2f

    val artwork = mediaInfo.albumArt?.takeIf { expandedType == IslandType.MEDIA && showExpandedAlbumArt }
    val artworkMotion = remember(fromTinyDot, artwork != null) {
        if (artwork != null) IslandArtworkMotion { morphProgress.value } else null
    }
    val expandedWidth by animateDpAsState(cardWidth, LarpMotion.resize(), label = "expanded_width")
    val expandedHeight by animateDpAsState(cardHeight, LarpMotion.resize(), label = "expanded_height")
    val currentWidth = androidx.compose.ui.unit.lerp(startWidth, expandedWidth, expansion)
    val currentHeight = androidx.compose.ui.unit.lerp(startHeight, expandedHeight, expansion)
    // Expand inward from the camera's edge, keeping the panel clear of the hole punch.
    val panelPlacement = islandPanelPlacement(
        drawWindowWidthDp.value, screenHeightDp.value, cutoutCenterXDp.value,
        cutoutCenterYDp.value, cutoutDiameterDp.value / 2f, expandedWidth.value, expandedHeight.value, isLandscape,
    )
    val expandedOffsetX = panelPlacement.offsetX.dp
    val expandedOffsetY = panelPlacement.top.dp - 14.dp
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

    val compactAlpha = 1f - motionFraction(expansion, 0.04f, 0.54f)
    val expandedAlpha = motionFraction(expansion, 0.23f, 0.85f)
    val expandedContentScale = 0.96f + (0.04f * expansion)
    val expandedContentOffsetY = 10.dp * (1f - expansion)
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
                    val totalScaleX = pressScale * (1f + kotlin.math.abs(stretch) * if (fromTinyDot) 0.22f else 0.18f)
                    val totalScaleY = pressScale
                    // Overshoot the complete clipped panel so artwork, blur and controls spring together.
                    scaleX = totalScaleX * settleScale
                    scaleY = totalScaleY * settleScale
                    transformOrigin = when {
                        stretch > 0.01f -> TransformOrigin(0f, 0.5f)
                        stretch < -0.01f -> TransformOrigin(1f, 0.5f)
                        settleScale > 1f && !isLandscape -> TransformOrigin(0.5f, 0f)
                        settleScale > 1f -> TransformOrigin(if (cutoutInfo.centerX < drawWindowWidthPx / 2f) 0f else 1f, 0.5f)
                        else -> TransformOrigin.Center
                    }
                    alpha = if (isExpanded || returnFrame != null) 1f else motionFraction(expansion, 0f, 0.3f)
                }
                .then(
                    Modifier.islandFluidProgressBorder(
                        progressFraction = { 0f },
                        cornerRadius = currentCornerRadius,
                        shape = containerShape,
                        strokeWidth = 0.75.dp,
                        trackColor = { Color(0x30FFFFFF) },
                        progressColor = { Color.Transparent },
                    )
                )
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
                if (compactAlpha > 0.01f || (artworkMotion != null && !isExpanded)) {
                    Box(
                        modifier = Modifier
                            .requiredSize(startWidth, startHeight)
                            .graphicsLayer {
                                alpha = compactAlpha
                                renderEffect = contentBlur.motionBlur(1f - compactAlpha)
                            }
                            .artworkContainer(artworkMotion?.compact),
                    ) {
                        if (fromTinyDot) {
                            SecondaryDotContent(
                                type = expandedType,
                                bubbleWidth = startWidth,
                                bubbleHeight = startHeight,
                                bubbleThickness = secondaryBubbleThickness,
                                isMiniPill = showDotRightSideInfo,
                                isLandscape = isLandscape,
                                beforeMain = hasBubbleAfter,
                                bubbleStyle = bubbleStyle,
                                notificationActivity = notificationActivity,
                                mediaInfo = mediaInfo,
                                notificationActivityStatus = notificationActivityStatus,
                                flashlightStatus = flashlightStatus,
                                artworkAnchor = artworkMotion?.compact,
                            )
                        } else {
                            IslandCompactContent(
                                model = IslandModel(expandedType, {}, presentation = presentations.getValue(expandedType)),
                                media = mediaInfo, activity = notificationActivity,
                                diameter = cutoutDiameterDp, landscape = isLandscape,
                                showTitle = showMinimizedTitle,
                                announcing = announcementActive, trailingWingFraction = compactFrame.trailingFraction,
                                artworkAnchor = artworkMotion?.compact,
                                onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                            )
                        }
                    }
                }

                if (expandedAlpha > 0.01f || (artworkMotion != null && isExpanded)) {
                    Box(
                        modifier = Modifier
                            .requiredSize(expandedWidth, expandedHeight)
                            .graphicsLayer {
                                alpha = expandedAlpha
                                renderEffect = contentBlur.motionBlur(1f - expandedAlpha)
                                scaleX = expandedContentScale
                                scaleY = expandedContentScale
                                translationY = with(density) { expandedContentOffsetY.toPx() }
                            }
                            .artworkContainer(artworkMotion?.expanded),
                    ) {
                        if (expandedType != IslandType.MEDIA) CameraCover(
                            style = cameraCoverStyle,
                            centerX = cutoutCenterXDp - ((drawWindowWidthDp - expandedWidth) / 2f + expandedOffsetX),
                            centerY = cutoutCenterYDp - panelPlacement.top.dp,
                            diameter = cutoutDiameterDp,
                        )
                        when (expandedType) {
                            IslandType.NOTIFICATION_ACTIVITY -> {
                                if (notificationActivity != null) {
                                    ExpandedNotificationActivityContent(
                                        activity = notificationActivity,
                                        onCollapse = onCollapse,
                                        topContentInset = expandedCutoutClearanceDp,
                                        spanIconLeftOfCutout = spanActivityIconLeftOfCutout,
                                    )
                                }
                            }
                            IslandType.FLASHLIGHT -> {
                                ExpandedFlashlightContent(
                                    topContentInset = expandedCutoutClearanceDp,
                                )
                            }
                            IslandType.MEDIA -> {
                                ExpandedIslandContent(
                                    mediaInfo = mediaInfo,
                                    cutoutDiameterDp = cutoutDiameterDp,
                                    isExpanded = isExpanded,
                                    onCollapse = onCollapse,
                                    cutoutInfo = cutoutInfo,
                                    cardHorizontalMarginDp = if (expandedPlayerLayout == OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE) {
                                        mediaCardHorizontalMarginDp
                                    } else {
                                        horizontalMarginDp
                                    },
                                    topContentInset = expandedCutoutClearanceDp,
                                    artworkAnchor = artworkMotion?.expanded,
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
                        isLandscape = isLandscape,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
