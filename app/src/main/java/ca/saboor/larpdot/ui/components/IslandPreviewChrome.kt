package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.ui.overlay.squircleShape

/** Shared camera punch-hole indicator used inside in-app island previews. */
@Composable
internal fun PreviewCameraCutoutDot(
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color(0xFF0D0D0D))
            .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
    )
}

/** Resolves the expanded island preview card shape (squircle vs rounded rectangle). */
@Composable
internal fun previewCardShape(
    cornerRadiusDp: Dp,
    squircleEnabled: Boolean,
    blurEnabled: Boolean,
): Shape {
    if (!squircleEnabled || blurEnabled) {
        return RoundedCornerShape(cornerRadiusDp)
    }
    val radiusPx = with(LocalDensity.current) { cornerRadiusDp.toPx() }
    return squircleShape(radiusPx, 0.80f)
}
