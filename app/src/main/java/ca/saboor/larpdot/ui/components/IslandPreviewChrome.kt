package ca.saboor.larpdot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.saboor.larpdot.ui.overlay.drawCameraCutoutOutline
import ca.saboor.larpdot.ui.overlay.squircleShape
import ca.saboor.larpdot.ui.theme.LarpIcons

@Composable
internal fun IslandPreviewInspectorHeader(title: String, icon: ImageVector, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(LarpIcons.Close, contentDescription = "Close $title settings", modifier = Modifier.size(18.dp))
        }
    }
    LarpDivider()
}

/** Shared camera punch-hole indicator used inside in-app island previews. */
@Composable
internal fun PreviewCameraCutoutDot(
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(diameter)) {
        val radius = size.minDimension / 2f
        drawCircle(Color(0xFF0D0D0D), radius = radius)
        drawCameraCutoutOutline(center, radius)
    }
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
