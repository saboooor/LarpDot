package ca.saboor.larpdot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.saboor.larpdot.cutout.CutoutInfo

enum class IslandPreviewType {
    MUSIC,
    FLASHLIGHT,
}

@Composable
fun IslandPreview(
    cutoutInfo: CutoutInfo,
    type: IslandPreviewType,
    modifier: Modifier = Modifier,
) {
    when (type) {
        IslandPreviewType.MUSIC -> ExpandedIslandPreview(
            cutoutInfo = cutoutInfo,
            modifier = modifier,
        )

        IslandPreviewType.FLASHLIGHT -> FlashlightIslandPreview(
            cutoutInfo = cutoutInfo,
            modifier = modifier,
        )
    }
}
