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
    selectedMusicElement: MusicPreviewElement? = null,
    onMusicElementClick: ((MusicPreviewElement) -> Unit)? = null,
    selectedFlashlightElement: FlashlightPreviewElement? = null,
    onFlashlightElementClick: ((FlashlightPreviewElement) -> Unit)? = null,
) {
    when (type) {
        IslandPreviewType.MUSIC -> ExpandedIslandPreview(
            cutoutInfo = cutoutInfo,
            modifier = modifier,
            selectedElement = selectedMusicElement,
            onElementClick = onMusicElementClick,
        )

        IslandPreviewType.FLASHLIGHT -> FlashlightIslandPreview(
            cutoutInfo = cutoutInfo,
            modifier = modifier,
            selectedElement = selectedFlashlightElement,
            onElementClick = onFlashlightElementClick,
        )
    }
}
