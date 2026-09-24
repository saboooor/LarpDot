package ca.saboor.larpdot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectSmall
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.service.AudioCaptureService
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.ExtractionState
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.LiveTrackVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.overlay.nestedAlbumArtShape
import kotlin.math.roundToInt

private enum class ShapeCategory(val label: String) {
    ALL("All (35)"),
    GEOMETRIC("Geometric"),
    COOKIES("Cookies"),
    STARS("Stars"),
    EXPRESSIVE("Expressive"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapePickerSection(
    title: String,
    subtitle: String,
    selectedShape: OverlayPreferences.NestedAlbumArtShape,
    rotationDegrees: Float,
    onShapeSelected: (OverlayPreferences.NestedAlbumArtShape) -> Unit,
    onRotationChanged: (Float) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(ShapeCategory.ALL) }

    val geometricShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.ROUNDED_SQUARE,
            OverlayPreferences.NestedAlbumArtShape.CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.SEMI_CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.OVAL,
            OverlayPreferences.NestedAlbumArtShape.PILL,
            OverlayPreferences.NestedAlbumArtShape.SLANTED,
            OverlayPreferences.NestedAlbumArtShape.TRIANGLE,
            OverlayPreferences.NestedAlbumArtShape.DIAMOND,
            OverlayPreferences.NestedAlbumArtShape.PENTAGON,
            OverlayPreferences.NestedAlbumArtShape.GEM,
        )
    }

    val cookieShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.COOKIE,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_6,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_7,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_9,
            OverlayPreferences.NestedAlbumArtShape.COOKIE_12,
            OverlayPreferences.NestedAlbumArtShape.CLOVER,
            OverlayPreferences.NestedAlbumArtShape.CLOVER_8,
        )
    }

    val starShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.SUNNY,
            OverlayPreferences.NestedAlbumArtShape.VERY_SUNNY,
            OverlayPreferences.NestedAlbumArtShape.BURST,
            OverlayPreferences.NestedAlbumArtShape.SOFT_BURST,
            OverlayPreferences.NestedAlbumArtShape.BOOM,
            OverlayPreferences.NestedAlbumArtShape.SOFT_BOOM,
        )
    }

    val expressiveShapes = remember {
        listOf(
            OverlayPreferences.NestedAlbumArtShape.HEART,
            OverlayPreferences.NestedAlbumArtShape.FLOWER,
            OverlayPreferences.NestedAlbumArtShape.GHOSTISH,
            OverlayPreferences.NestedAlbumArtShape.BUN,
            OverlayPreferences.NestedAlbumArtShape.PUFFY,
            OverlayPreferences.NestedAlbumArtShape.PUFFY_DIAMOND,
            OverlayPreferences.NestedAlbumArtShape.PIXEL_CIRCLE,
            OverlayPreferences.NestedAlbumArtShape.PIXEL_TRIANGLE,
            OverlayPreferences.NestedAlbumArtShape.ARCH,
            OverlayPreferences.NestedAlbumArtShape.FAN,
            OverlayPreferences.NestedAlbumArtShape.ARROW,
            OverlayPreferences.NestedAlbumArtShape.CLAM_SHELL,
        )
    }

    val displayedShapes = when (selectedCategory) {
        ShapeCategory.ALL -> OverlayPreferences.NestedAlbumArtShape.entries
        ShapeCategory.GEOMETRIC -> geometricShapes
        ShapeCategory.COOKIES -> cookieShapes
        ShapeCategory.STARS -> starShapes
        ShapeCategory.EXPRESSIVE -> expressiveShapes
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(nestedAlbumArtShape(selectedShape, rotationDegrees))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Active: ${selectedShape.label} ($subtitle)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Category Filter ButtonGroups
        val catRow1 = listOf(ShapeCategory.ALL, ShapeCategory.GEOMETRIC, ShapeCategory.COOKIES)
        val catRow2 = listOf(ShapeCategory.STARS, ShapeCategory.EXPRESSIVE)

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            catRow1.forEach { category ->
                toggleableItem(
                    checked = selectedCategory == category,
                    label = category.label,
                    onCheckedChange = { selectedCategory = category },
                    weight = 1f,
                )
            }
        }

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            catRow2.forEach { category ->
                toggleableItem(
                    checked = selectedCategory == category,
                    label = category.label,
                    onCheckedChange = { selectedCategory = category },
                    weight = 1f,
                )
            }
        }

        // Horizontal list of shapes ensuring shapes are never squished or distorted
        val shapeListState = rememberLazyListState()

        LaunchedEffect(selectedShape, selectedCategory) {
            val index = displayedShapes.indexOf(selectedShape)
            if (index >= 0) {
                shapeListState.animateScrollToItem(index)
            }
        }

        LazyRow(
            state = shapeListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        ) {
            items(displayedShapes, key = { it.name }) { shapeOption ->
                val isSelected = selectedShape == shapeOption
                Surface(
                    selected = isSelected,
                    onClick = { onShapeSelected(shapeOption) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(54.dp)
                        .semantics { contentDescription = shapeOption.label },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(nestedAlbumArtShape(shapeOption, rotationDegrees))
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Rotation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Shape Rotation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${rotationDegrees.roundToInt()}°",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Quick Rotation Presets ButtonGroup
        val rotationPresets = listOf(0f, 45f, 90f, 180f, 270f)
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        ) {
            rotationPresets.forEach { preset ->
                val isSelected = (((rotationDegrees % 360f) + 360f) % 360f).roundToInt() == preset.toInt()
                toggleableItem(
                    checked = isSelected,
                    label = "${preset.toInt()}°",
                    onCheckedChange = { onRotationChanged(preset) },
                    weight = 1f,
                )
            }
        }

        // Rotation Slider with Minus & Plus buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    val next = (((rotationDegrees - 15f) % 360f) + 360f) % 360f
                    onRotationChanged(next.roundToInt().toFloat())
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Rotate Counter-Clockwise",
                    modifier = Modifier.size(18.dp),
                )
            }

            Slider(
                value = (((rotationDegrees % 360f) + 360f) % 360f),
                onValueChange = { onRotationChanged(it.roundToInt().toFloat()) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f),
            )

            FilledTonalIconButton(
                onClick = {
                    val next = (rotationDegrees + 15f) % 360f
                    onRotationChanged(next.roundToInt().toFloat())
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Rotate Clockwise",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showMinimizedProgressOutline by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val showExpandedProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val hideMusicWhenAppOpen by OverlayPreferences.hideMusicWhenAppOpenFlow.collectAsState()
    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val expandedStyle by OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showProgressSquiggles by OverlayPreferences.showProgressSquigglesFlow.collectAsState()
    val showMinimizedVisualizer by OverlayPreferences.showMinimizedVisualizerFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val minimizedShape by OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState()
    val expandedShape by OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState()
    val minimizedRotation by OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState()
    val expandedRotation by OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState()
    val showMinimizedDominantGlow by OverlayPreferences.showMinimizedDominantColorGlowFlow.collectAsState()
    val showExpandedDominantGlow by OverlayPreferences.showExpandedDominantColorGlowFlow.collectAsState()
    val cameraCoverStyle by OverlayPreferences.cameraCoverStyleFlow.collectAsState()
    val waveformBandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val waveformBarWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val waveformBarSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val visualizerMode by OverlayPreferences.visualizerModeFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val detectedBpmTrack by BpmDetector.detectedTrack.collectAsState()
    val isBpmDetecting by BpmDetector.isDetecting.collectAsState()
    val hqVisualizerEnabled by OverlayPreferences.hqVisualizerEnabledFlow.collectAsState()
    var showHqWarningDialog by remember { mutableStateOf(false) }
    val extractionState by AudioPreviewExtractor.extractionState.collectAsState()
    val isPreviewPlaying by PreviewAudioPlayer.isPlaying.collectAsState()
    val previewPositionMs by PreviewAudioPlayer.currentPositionMs.collectAsState()
    val previewDurationMs by PreviewAudioPlayer.durationMs.collectAsState()
    val previewSource by OverlayPreferences.previewAudioSourceFlow.collectAsState()
    val liveAmplitudes by LiveAudioVisualizer.liveAmplitudes.collectAsState()
    val isLiveCapturing by LiveAudioVisualizer.isCapturing.collectAsState()
    val liveErrorMessage by LiveAudioVisualizer.errorMessage.collectAsState()
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.DEVICE_AUDIO)
            PreviewAudioPlayer.stop()
            AudioCaptureService.start(context, result.resultCode, result.data!!, waveformBandCount)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            PreviewAudioPlayer.stop()
        }
    }

    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()

    LaunchedEffect(nowPlaying.title, nowPlaying.artist, visualizerMode, waveformBandCount, previewSource) {
        val title = if (nowPlaying.hasMedia) nowPlaying.title else "Starboy"
        val artist = if (nowPlaying.hasMedia) nowPlaying.artist else "The Weeknd"
        if (visualizerMode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW) {
            AudioPreviewExtractor.syncTrack(context, title, artist, true, waveformBandCount, previewSource)
        } else {
            AudioPreviewExtractor.syncTrack(context, title, artist, false, waveformBandCount, previewSource)
        }
        if (visualizerMode == OverlayPreferences.VisualizerMode.BPM) {
            BpmDetector.syncTrack(title, artist)
        }
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.isShowMinimizedProgressOutlineEnabled(context)
        OverlayPreferences.isShowExpandedProgressOutlineEnabled(context)
        OverlayPreferences.isHideMusicWhenAppOpenEnabled(context)
        OverlayPreferences.isShowMinimizedTitleEnabled(context)
        OverlayPreferences.getMinimizedAlbumArtStyle(context)
        OverlayPreferences.getExpandedAlbumArtStyle(context)
        OverlayPreferences.isShowExpandedAlbumArtEnabled(context)
        OverlayPreferences.getExpandedPlayerLayout(context)
        OverlayPreferences.isShowProgressSquigglesEnabled(context)
        OverlayPreferences.isShowMinimizedVisualizerEnabled(context)
        OverlayPreferences.getExpandedElementVisibility(context)
        OverlayPreferences.getMinimizedAlbumArtShape(context)
        OverlayPreferences.getExpandedAlbumArtShape(context)
        OverlayPreferences.getMinimizedAlbumArtRotation(context)
        OverlayPreferences.getExpandedAlbumArtRotation(context)
        OverlayPreferences.isShowMinimizedDominantColorGlowEnabled(context)
        OverlayPreferences.isShowExpandedDominantColorGlowEnabled(context)
        OverlayPreferences.getCameraCoverStyle(context)
        OverlayPreferences.getWaveformBandCount(context)
        OverlayPreferences.getWaveformBarWidth(context)
        OverlayPreferences.getWaveformBarSpacing(context)
        OverlayPreferences.isHqVisualizerEnabled(context)
        OverlayPreferences.getVisualizerMode(context)
        OverlayPreferences.getPreviewAudioSource(context)
    }

    val minimizedStyles = listOf(
        OverlayPreferences.AlbumArtStyle.BASIC_FADED,
        OverlayPreferences.AlbumArtStyle.BLENDED,
        OverlayPreferences.AlbumArtStyle.NESTED,
    )
    val expandedStyleRow1 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.NONE,
        OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED,
        OverlayPreferences.ExpandedBackgroundStyle.BLENDED,
    )
    val expandedStyleRow2 = listOf(
        OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND,
        OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IslandPreview(cutoutInfo = cutoutInfo, type = IslandPreviewType.MUSIC)
        }

        item {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("Minimized") },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("Expanded") },
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { onTabSelected(2) },
                    text = { Text("Overview") },
                )
            }
        }

        // Minimized Island Presentation
        if (selectedTabIndex == 0) {
            item {
            SectionHeader(title = "Minimized Island Art")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoSizeSelectSmall,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Minimized Style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (minimizedStyle) {
                                    OverlayPreferences.AlbumArtStyle.BASIC_FADED -> "Linear edge fade into capsule pill"
                                    OverlayPreferences.AlbumArtStyle.BLENDED -> "Smooth gradient blend with ambient glow"
                                    OverlayPreferences.AlbumArtStyle.NESTED -> "Crisp nested album art thumbnail"
                                    OverlayPreferences.AlbumArtStyle.FULL_BACKGROUND -> "Artwork fills the pill wing"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        minimizedStyles.forEach { style ->
                            toggleableItem(
                                checked = minimizedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setMinimizedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = minimizedStyle == OverlayPreferences.AlbumArtStyle.NESTED,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShapePickerSection(
                            title = "Minimized Shape",
                            subtitle = "minimized island",
                            selectedShape = minimizedShape,
                            rotationDegrees = minimizedRotation,
                            onShapeSelected = {
                                OverlayPreferences.setMinimizedAlbumArtShape(context, it)
                            },
                            onRotationChanged = {
                                OverlayPreferences.setMinimizedAlbumArtRotation(context, it)
                            },
                        )
                    }
                }
            }
            }
        } else if (selectedTabIndex == 1) {

            // Expanded Island Presentation
            item {
                SectionHeader(title = "Expanded Island Art")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "Expanded Background",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (expandedStyle) {
                                    OverlayPreferences.ExpandedBackgroundStyle.NONE -> "Plain black island background"
                                    OverlayPreferences.ExpandedBackgroundStyle.BASIC_FADED -> "Linear faded background card cover"
                                    OverlayPreferences.ExpandedBackgroundStyle.BLENDED -> "Curved gradient blend with dominant backdrop"
                                    OverlayPreferences.ExpandedBackgroundStyle.FULL_BACKGROUND -> "Artwork fills the entire island card"
                                    OverlayPreferences.ExpandedBackgroundStyle.BLURRED_FULL_BACKGROUND -> "Blurred artwork fills the entire island card"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        expandedStyleRow1.forEach { style ->
                            toggleableItem(
                                checked = expandedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                    ) {
                        expandedStyleRow2.forEach { style ->
                            toggleableItem(
                                checked = expandedStyle == style,
                                label = style.label,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedAlbumArtStyle(context, style)
                                },
                                weight = 1f,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Album Art", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Show the cover beside the track title",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showExpandedAlbumArt,
                            onCheckedChange = {
                                OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it)
                            },
                        )
                    }

                    AnimatedVisibility(
                        visible = showExpandedAlbumArt,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShapePickerSection(
                            title = "Album Art Shape",
                            subtitle = "expanded album art",
                            selectedShape = expandedShape,
                            rotationDegrees = expandedRotation,
                            onShapeSelected = {
                                OverlayPreferences.setExpandedAlbumArtShape(context, it)
                            },
                            onRotationChanged = {
                                OverlayPreferences.setExpandedAlbumArtRotation(context, it)
                            },
                        )
                    }
                    }
                }
            }

            item {
                SectionHeader(title = "Player Layout")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = when (expandedPlayerLayout) {
                                OverlayPreferences.ExpandedPlayerLayout.ANDROID_MEDIA_CONTROLS ->
                                    "Familiar Android media controls with a prominent play button"
                                OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE ->
                                    "Expressive shapes, connected controls, and a compact hierarchy"
                                OverlayPreferences.ExpandedPlayerLayout.IPHONE ->
                                    "Artwork-first layout with centered Apple-style playback controls"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                        ) {
                            OverlayPreferences.ExpandedPlayerLayout.entries.forEach { layout ->
                                toggleableItem(
                                    checked = expandedPlayerLayout == layout,
                                    label = layout.label,
                                    onCheckedChange = {
                                        OverlayPreferences.setExpandedPlayerLayout(context, layout)
                                    },
                                    weight = 1f,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Progress Squiggles", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Animate the progress bar with a wavy line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = showProgressSquiggles,
                                onCheckedChange = {
                                    OverlayPreferences.setShowProgressSquigglesEnabled(context, it)
                                },
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Expanded Cutout")
                LarpCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Camera Cover",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = cameraCoverStyle.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                        ) {
                            OverlayPreferences.CameraCoverStyle.entries.forEach { style ->
                                toggleableItem(
                                    checked = cameraCoverStyle == style,
                                    label = style.label,
                                    onCheckedChange = { OverlayPreferences.setCameraCoverStyle(context, style) },
                                    weight = 1f,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedTabIndex != 2) item {
            SectionHeader(title = if (selectedTabIndex == 0) "Minimized Elements" else "Expanded Elements")
            LarpCard {
                val visibilityOptions: List<Triple<String, Boolean, (Boolean) -> Unit>> =
                    if (selectedTabIndex == 0) {
                        listOf(
                            Triple("Song Title", showMinimizedTitle) { enabled ->
                                OverlayPreferences.setShowMinimizedTitleEnabled(context, enabled)
                            },
                            Triple("Song Change Announcement", showSongAnnouncement) { enabled ->
                                OverlayPreferences.setShowSongAnnouncementEnabled(context, enabled)
                            },
                            Triple("Visualizer", showMinimizedVisualizer) { enabled ->
                                OverlayPreferences.setShowMinimizedVisualizerEnabled(context, enabled)
                            },
                            Triple("Progress Outline", showMinimizedProgressOutline) { enabled ->
                                OverlayPreferences.setShowMinimizedProgressOutlineEnabled(context, enabled)
                            },
                            Triple("Dominant Color Glow", showMinimizedDominantGlow) { enabled ->
                                OverlayPreferences.setShowMinimizedDominantColorGlowEnabled(context, enabled)
                            },
                        )
                    } else {
                        listOf(
                            Triple("Track Information", expandedElementVisibility.trackInfo) { enabled ->
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    expandedElementVisibility.copy(trackInfo = enabled),
                                )
                            },
                            Triple("Visualizer", expandedElementVisibility.visualizer) { enabled ->
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    expandedElementVisibility.copy(visualizer = enabled),
                                )
                            },
                            Triple("Progress Bar", expandedElementVisibility.progress) { enabled ->
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    expandedElementVisibility.copy(progress = enabled),
                                )
                            },
                            Triple("Main Player Controls", expandedElementVisibility.mainControls) { enabled ->
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    expandedElementVisibility.copy(mainControls = enabled),
                                )
                            },
                            Triple("App Action Buttons", expandedElementVisibility.appActions) { enabled ->
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    expandedElementVisibility.copy(appActions = enabled),
                                )
                            },
                            Triple("Progress Outline", showExpandedProgressOutline) { enabled ->
                                OverlayPreferences.setShowExpandedProgressOutlineEnabled(context, enabled)
                            },
                            Triple("Dominant Color Glow", showExpandedDominantGlow) { enabled ->
                                OverlayPreferences.setShowExpandedDominantColorGlowEnabled(context, enabled)
                            },
                        )
                    }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibilityOptions.forEachIndexed { index, (label, checked, onCheckedChange) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Switch(checked = checked, onCheckedChange = onCheckedChange)
                        }
                        if (index < visibilityOptions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        if (selectedTabIndex == 2) {
        // Display Options Card
        item {
            SectionHeader(title = "Display Options")
            LarpCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Hide in Music App
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide in Music App",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Hide the music island while the active music player application is open",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Switch(
                            checked = hideMusicWhenAppOpen,
                            onCheckedChange = { isChecked ->
                                OverlayPreferences.setHideMusicWhenAppOpenEnabled(context, isChecked)
                            },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Waveform Equalizer Bands
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column {
                                    Text(
                                        text = "Waveform Bands",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Number of animated equalizer frequency bars (default: 5)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(
                                text = if (waveformBandCount == 0) {
                                    "Off"
                                } else if (waveformBandCount == OverlayPreferences.DEFAULT_WAVEFORM_BAND_COUNT) {
                                    "5 (Default)"
                                } else {
                                    "$waveformBandCount Bands"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Slider(
                            value = waveformBandCount.toFloat(),
                            onValueChange = {
                                OverlayPreferences.setWaveformBandCount(context, it.roundToInt())
                            },
                            valueRange = 0f..30f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Bar Width",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = "${"%.1f".format(waveformBarWidth)} dp",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = waveformBarWidth,
                                onValueChange = {
                                    OverlayPreferences.setWaveformBarWidth(
                                        context,
                                        (it * 10f).roundToInt() / 10f,
                                    )
                                },
                                valueRange = 0.5f..6f,
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Bar Spacing",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = "${"%.1f".format(waveformBarSpacing)} dp",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = waveformBarSpacing,
                                onValueChange = {
                                    OverlayPreferences.setWaveformBarSpacing(
                                        context,
                                        (it * 10f).roundToInt() / 10f,
                                    )
                                },
                                valueRange = 0f..8f,
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Visualizer Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when (visualizerMode) {
                                    OverlayPreferences.VisualizerMode.SIMULATED -> Icons.Default.GraphicEq
                                    OverlayPreferences.VisualizerMode.BPM -> Icons.Default.Speed
                                    OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> Icons.Default.GraphicEq
                                    OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> Icons.Default.Audiotrack
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = "Visualizer Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = when (visualizerMode) {
                                        OverlayPreferences.VisualizerMode.SIMULATED -> "Sinusoidal wave oscillation"
                                        OverlayPreferences.VisualizerMode.BPM -> "BPM tempo-locked rhythm section (0% CPU)"
                                        OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> "Real-time internal playback stream directly from phone (0ms latency)"
                                        OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> "30s preview dynamic frequency bands"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // ButtonGroup for 4 modes
                        val visualizerModes = listOf(
                            OverlayPreferences.VisualizerMode.SIMULATED,
                            OverlayPreferences.VisualizerMode.BPM,
                            OverlayPreferences.VisualizerMode.DEVICE_AUDIO,
                            OverlayPreferences.VisualizerMode.AUDIO_PREVIEW,
                        )
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                        ) {
                            visualizerModes.forEach { mode ->
                                toggleableItem(
                                    checked = visualizerMode == mode,
                                    label = when (mode) {
                                        OverlayPreferences.VisualizerMode.SIMULATED -> "Simulated"
                                        OverlayPreferences.VisualizerMode.BPM -> "BPM"
                                        OverlayPreferences.VisualizerMode.DEVICE_AUDIO -> "Phone Audio"
                                        OverlayPreferences.VisualizerMode.AUDIO_PREVIEW -> "Preview"
                                    },
                                    onCheckedChange = {
                                        if (mode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW) {
                                            if (visualizerMode != mode) {
                                                showHqWarningDialog = true
                                            }
                                        } else if (mode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO) {
                                            if (!isLiveCapturing) {
                                                val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                                                if (captureIntent != null) {
                                                    screenCaptureLauncher.launch(captureIntent)
                                                }
                                            } else {
                                                OverlayPreferences.setVisualizerMode(context, mode)
                                                PreviewAudioPlayer.stop()
                                            }
                                        } else {
                                            OverlayPreferences.setVisualizerMode(context, mode)
                                            PreviewAudioPlayer.stop()
                                            LiveAudioVisualizer.stop(context)
                                        }
                                    },
                                    weight = 1f,
                                )
                            }
                        }

                        // BPM Status Card
                        AnimatedVisibility(
                            visible = visualizerMode == OverlayPreferences.VisualizerMode.BPM,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Tempo Beat Sync (${currentBpm.toInt()} BPM)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = if (isBpmDetecting) "Detecting track tempo..." else (detectedBpmTrack ?: "Standard tempo active"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isBpmDetecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Synthesizes kick drums on bass bars, snares on center bars, and hi-hats on treble bars in lockstep with the song tempo. Smooth, lightweight, and zero battery drain.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Direct Device Audio (Screen/Audio Capture via MediaProjection) Card
                        AnimatedVisibility(
                            visible = visualizerMode == OverlayPreferences.VisualizerMode.DEVICE_AUDIO,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (isLiveCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Internal Playback Capture (Digital Loopback)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = if (isLiveCapturing) "Direct internal audio stream active • 0ms latency"
                                                else (liveErrorMessage ?: "Screen/Audio recording permission required"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (liveErrorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isLiveCapturing) {
                                            LiveTrackVisualizer(
                                                amplitudes = liveAmplitudes,
                                                isPlaying = true,
                                                accentColor = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(width = 44.dp, height = 24.dp),
                                                barCount = waveformBandCount,
                                            )
                                        }
                                    }

                                    if (!isLiveCapturing) {
                                        Button(
                                            onClick = {
                                                val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                                                if (captureIntent != null) {
                                                    screenCaptureLauncher.launch(captureIntent)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("Grant Permission & Start Live Capture", modifier = Modifier.padding(start = 8.dp))
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                LiveAudioVisualizer.stop(context)
                                                OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.SIMULATED)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("Stop Live Capture", modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }

                                    Text(
                                        text = "Captures internal digital audio playback (Android 10+) using the system screen/audio recording method. Directly streams from Spotify, YouTube Music, Apple Music, or any app with zero mic noise, zero 30s limit, and 0ms latency.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Song Processing Status & Audio Preview Player
                        AnimatedVisibility(
                            visible = visualizerMode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Preview Source Selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Preview Audio Source",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        val previewSources = listOf(
                                            OverlayPreferences.PreviewAudioSource.AUTO,
                                            OverlayPreferences.PreviewAudioSource.DEEZER,
                                            OverlayPreferences.PreviewAudioSource.ITUNES,
                                            OverlayPreferences.PreviewAudioSource.DEVICE,
                                        )
                                        ButtonGroup(
                                            modifier = Modifier.fillMaxWidth(),
                                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                                        ) {
                                            previewSources.forEach { source ->
                                                toggleableItem(
                                                    checked = previewSource == source,
                                                    label = source.title,
                                                    onCheckedChange = {
                                                        if (source == OverlayPreferences.PreviewAudioSource.DEVICE) {
                                                            if (!isLiveCapturing) {
                                                                val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                                                                if (captureIntent != null) {
                                                                    screenCaptureLauncher.launch(captureIntent)
                                                                }
                                                            } else {
                                                                OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.DEVICE_AUDIO)
                                                                PreviewAudioPlayer.stop()
                                                            }
                                                        } else {
                                                            OverlayPreferences.setPreviewAudioSource(context, source)
                                                            PreviewAudioPlayer.stop()
                                                            val curTitle = if (nowPlaying.hasMedia) nowPlaying.title else "Starboy"
                                                            val curArtist = if (nowPlaying.hasMedia) nowPlaying.artist else "The Weeknd"
                                                            AudioPreviewExtractor.syncTrack(context, curTitle, curArtist, true, waveformBandCount, source)
                                                        }
                                                    },
                                                    weight = 1f,
                                                )
                                            }
                                        }
                                        Text(
                                            text = previewSource.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                    when (val state = extractionState) {
                                        is ExtractionState.Idle -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.HourglassEmpty,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = "Waiting for song playback to analyze audio...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        is ExtractionState.Processing -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Column {
                                                    Text(
                                                        text = "Processing: ${state.trackTitle}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        text = "Downloading 30s preview & decomposing ${state.bandCount} frequency bands offline",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        is ExtractionState.Success -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Column {
                                                    Text(
                                                        text = "Successfully Processed: ${state.trackTitle}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        text = "Synced from ${state.sourceName} • ${state.frameCount} frames • ${state.bandCount} acoustic bands",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }

                                            val audioSource = state.audioFilePath ?: state.previewUrl
                                            if (audioSource != null) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                PreviewAudioPlayer.togglePlayback(context, audioSource)
                                                            },
                                                            modifier = Modifier.size(36.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                                contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }

                                                        Column {
                                                            Text(
                                                                text = if (isPreviewPlaying) "Playing 30s Audio Preview" else "Play 30s iTunes Preview",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                            Text(
                                                                text = "${formatPreviewTime(previewPositionMs)} / ${formatPreviewTime(previewDurationMs)}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }

                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = null,
                                                        tint = if (isPreviewPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }

                                                LinearProgressIndicator(
                                                    progress = {
                                                        if (previewDurationMs > 0) (previewPositionMs.toFloat() / previewDurationMs).coerceIn(0f, 1f) else 0f
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(2.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                )
                                            }
                                        }
                                        is ExtractionState.Failed -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Column {
                                                    Text(
                                                        text = "Could not process: ${state.trackTitle}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        text = "${state.reason} • Falling back to simulated waveform",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showHqWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHqWarningDialog = false },
            title = {
                Text(
                    text = "Performance Warning",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Generating audio-accurate visuals decodes raw preview data directly on your device. This process consumes significant CPU power and battery. Lower-end devices may experience temporary lag or device warming.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.AUDIO_PREVIEW)
                        showHqWarningDialog = false
                    }
                ) {
                    Text(
                        text = "Enable Anyway",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHqWarningDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatPreviewTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
