package ca.saboor.larpdot.ui.components

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.flashlight.FlashlightController
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.overlay.CompactFlashlightContent
import ca.saboor.larpdot.ui.overlay.compactRadialGaugeExtraDp
import ca.saboor.larpdot.ui.overlay.islandCameraGeometry
import ca.saboor.larpdot.ui.overlay.islandFluidProgressBorder
import ca.saboor.larpdot.ui.overlay.rememberCompactFlashlightStatus
import ca.saboor.larpdot.ui.overlay.rememberCompactStatusExtraDp
import ca.saboor.larpdot.ui.overlay.ExpandedFlashlightContent

enum class FlashlightPreviewElement {
    MINIMIZED_PILL,
    MINIMIZED_RADIAL,
    EXPANDED_BEAM,
}

/**
 * Authentic live preview of the Flashlight Dynamic Island
 * (both compact pill and morphing expanded card) directly inside the app.
 */
@Composable
fun FlashlightIslandPreview(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
    selectedElement: FlashlightPreviewElement? = null,
    onElementClick: ((FlashlightPreviewElement) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val isFlashlightOn by FlashlightController.isFlashlightOn.collectAsState()
    val isSimulated by FlashlightController.isSimulated.collectAsState()
    val leftSide by OverlayPreferences.minimizedFlashlightLeftSideFlow.collectAsState()
    val rightSide by OverlayPreferences.minimizedFlashlightRightSideFlow.collectAsState()
    val hasRadial = leftSide == OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS ||
        rightSide == OverlayPreferences.MinimizedFlashlightSideContent.RADIAL_PROGRESS
    val expandedStyle by OverlayPreferences.flashlightExpandedStyleFlow.collectAsState()
    val cornerRadius by OverlayPreferences.islandCornerRadius.collectAsState()
    val squircleEnabled by OverlayPreferences.islandSquircleEnabled.collectAsState()
    val blurEnabled by OverlayPreferences.backdropBlurEnabled.collectAsState()
    val backgroundOpacity by OverlayPreferences.expandedBackgroundOpacity.collectAsState()
    val islandOutlineMode by OverlayPreferences.islandOutlineModeFlow.collectAsState()
    val showIslandOutline = ca.saboor.larpdot.service.resolveIslandOutlineVisible(
        islandOutlineMode,
        ca.saboor.larpdot.service.isPitchBlackPixel(MaterialTheme.colorScheme.background.toArgb()),
    )

    val cameraGeometry = islandCameraGeometry(cutoutInfo.radiusPx, density.density)
    val cutoutDiameterDp = cameraGeometry.diameter.dp
    val flashlightStatus = rememberCompactFlashlightStatus()
    val compactExtraDp = maxOf(
        if (hasRadial) compactRadialGaugeExtraDp(cutoutDiameterDp) else 0.dp,
        rememberCompactStatusExtraDp(flashlightStatus).coerceAtLeast(56.dp),
    )
    val compactWidth = cutoutDiameterDp + compactExtraDp

    val screenWidthDp = configuration.screenWidthDp.dp
    val wideFlashlight = expandedStyle == OverlayPreferences.FlashlightExpandedStyle.SLIDER
    val previewCardWidth = if (wideFlashlight) screenWidthDp - 32.dp else minOf(screenWidthDp - 96.dp, 220.dp)
    val previewCardHeight = if (wideFlashlight) cutoutDiameterDp + 64.dp else 290.dp
    val previewHorizontalMarginDp = (screenWidthDp - previewCardWidth) / 2f

    val expandedCornerRadiusDp = cornerRadius.dp.coerceAtMost(minOf(previewCardWidth, previewCardHeight) / 2f)
    val containerShape = previewCardShape(expandedCornerRadiusDp, squircleEnabled, blurEnabled)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Compact Pill Live Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
                // The camera spans the full thickness; padding belongs to the wing content.
                val compactHeight = cutoutDiameterDp
                val compactCornerRadius = compactHeight / 2f
                Surface(
                    modifier = Modifier
                        .width(compactWidth)
                        .height(compactHeight)
                        .islandFluidProgressBorder(
                            progressFraction = 0f,
                            cornerRadius = compactCornerRadius,
                            shape = RoundedCornerShape(compactCornerRadius),
                            strokeWidth = if (selectedElement == FlashlightPreviewElement.MINIMIZED_PILL) 1.5.dp else 0.75.dp,
                            trackColor = when {
                                selectedElement == FlashlightPreviewElement.MINIMIZED_PILL -> MaterialTheme.colorScheme.primary
                                showIslandOutline -> Color(0x30FFFFFF)
                                else -> Color.Transparent
                            },
                            progressColor = Color.Transparent,
                        ),
                    shape = RoundedCornerShape(compactCornerRadius),
                    color = Color.Black,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactFlashlightContent(
                            cutoutDiameterDp = cutoutDiameterDp,
                            onExpand = {},
                            onFlashlightToggle = { FlashlightController.toggleFlashlight() },
                            status = flashlightStatus,
                            leftSide = leftSide,
                            rightSide = rightSide,
                            onIconClick = onElementClick?.let { callback ->
                                { callback(FlashlightPreviewElement.MINIMIZED_PILL) }
                            },
                            onRadialClick = onElementClick?.let { callback ->
                                { callback(FlashlightPreviewElement.MINIMIZED_RADIAL) }
                            },
                            onBackgroundClick = onElementClick?.let { callback ->
                                { callback(FlashlightPreviewElement.MINIMIZED_PILL) }
                            },
                            highlightIcon = selectedElement == FlashlightPreviewElement.MINIMIZED_PILL,
                            highlightRadial = selectedElement == FlashlightPreviewElement.MINIMIZED_RADIAL,
                        )
                        // Hardware camera cutout visualizer centered between wings
                        PreviewCameraCutoutDot(diameter = cutoutDiameterDp)
                    }
                }
        }

        // Expanded Card Live Preview
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(previewCardWidth)
                    .height(previewCardHeight)
                    .islandFluidProgressBorder(
                        progressFraction = 0f,
                        cornerRadius = expandedCornerRadiusDp,
                        shape = containerShape,
                        strokeWidth = if (selectedElement == FlashlightPreviewElement.EXPANDED_BEAM) 1.5.dp else 0.75.dp,
                        trackColor = when {
                            selectedElement == FlashlightPreviewElement.EXPANDED_BEAM -> MaterialTheme.colorScheme.primary
                            showIslandOutline -> Color(0x30FFFFFF)
                            else -> Color.Transparent
                        },
                        progressColor = Color.Transparent,
                    ),
                shape = containerShape,
                color = Color.Black.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
                shadowElevation = 14.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    ExpandedFlashlightContent(
                        onBeamClick = onElementClick?.let { callback ->
                            { callback(FlashlightPreviewElement.EXPANDED_BEAM) }
                        },
                    )

                        // Hardware camera cutout punch hole positioned accurately
                        val orientedCenterXDp = with(density) { cutoutInfo.centerX.toDp() } - previewHorizontalMarginDp
                        val punchHoleCenterXDp = orientedCenterXDp.coerceIn(
                            cutoutDiameterDp / 2f,
                            previewCardWidth - (cutoutDiameterDp / 2f),
                        )
                        val punchHoleCenterYDp = 18.dp

                        PreviewCameraCutoutDot(
                            diameter = cutoutDiameterDp,
                            modifier = Modifier.offset(
                                x = punchHoleCenterXDp - (cutoutDiameterDp / 2f),
                                y = punchHoleCenterYDp - (cutoutDiameterDp / 2f),
                            ),
                        )
                    }
                }
        }

        // Action Controls: Quick Toggle and Simulation Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { FlashlightController.toggleFlashlight() },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) LarpIcons.FlashlightOff else LarpIcons.FlashlightOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isFlashlightOn) "Turn Off" else "Turn On",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            OutlinedButton(
                onClick = {
                    FlashlightController.setSimulated(!isSimulated)
                },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = LarpIcons.Science,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isSimulated) "Hardware Mode" else "Simulate Mode",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (onElementClick != null) {
                Text(
                    text = "Tap the minimized pill or beam to customize; drag to adjust brightness",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
