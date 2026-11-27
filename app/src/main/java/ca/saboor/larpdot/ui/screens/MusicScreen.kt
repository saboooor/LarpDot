package ca.saboor.larpdot.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.saboor.larpdot.cutout.CutoutInfo
import ca.saboor.larpdot.media.MediaPlaybackState
import ca.saboor.larpdot.service.AudioCaptureService
import ca.saboor.larpdot.service.ForegroundAppTracker
import ca.saboor.larpdot.service.OverlayPreferences
import ca.saboor.larpdot.ui.components.LarpButtonOption
import ca.saboor.larpdot.ui.components.LarpConnectedButtons
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.MusicPreviewElement
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.motionItem
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer

private enum class MusicSettingsSection(val label: String) {
    LAYOUT("Layout"), ELEMENTS("Elements"), PLAYBACK("Playback"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settingsSection by rememberSaveable { mutableStateOf<MusicSettingsSection?>(MusicSettingsSection.LAYOUT) }
    val showMinimizedProgressOutline by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val showExpandedProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val hideMusicWhenAppOpen by OverlayPreferences.hideMusicWhenAppOpenFlow.collectAsState()
    val musicPauseTimeoutSeconds by OverlayPreferences.musicPauseTimeoutSecondsFlow.collectAsState()
    val musicQuickSeekSeconds by OverlayPreferences.musicQuickSeekSecondsFlow.collectAsState()
    val usageAccessGranted by ForegroundAppTracker.hasUsageAccess.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        ForegroundAppTracker.refreshUsageAccess(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ForegroundAppTracker.refreshUsageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showSongAnnouncement by OverlayPreferences.showSongAnnouncementFlow.collectAsState()
    val minimizedStyle by OverlayPreferences.minimizedAlbumArtStyleFlow.collectAsState()
    val expandedStyle by OverlayPreferences.expandedAlbumArtStyleFlow.collectAsState()
    val showExpandedAlbumArt by OverlayPreferences.showExpandedAlbumArtFlow.collectAsState()
    val albumArtGlow by OverlayPreferences.albumArtGlowFlow.collectAsState()
    val expandedPlayerLayout by OverlayPreferences.expandedPlayerLayoutFlow.collectAsState()
    val showProgressSquiggles by OverlayPreferences.showProgressSquigglesFlow.collectAsState()
    val progressBarAccentColor by OverlayPreferences.progressBarAccentColorFlow.collectAsState()
    val playbackControlsStyle by OverlayPreferences.playbackControlsStyleFlow.collectAsState()
    val trackInfoStyle by OverlayPreferences.trackInfoStyleFlow.collectAsState()
    val expandedElementVisibility by OverlayPreferences.expandedElementVisibilityFlow.collectAsState()
    val minimizedShape by OverlayPreferences.minimizedAlbumArtShapeFlow.collectAsState()
    val expandedShape by OverlayPreferences.expandedAlbumArtShapeFlow.collectAsState()
    val minimizedRotation by OverlayPreferences.minimizedAlbumArtRotationFlow.collectAsState()
    val expandedRotation by OverlayPreferences.expandedAlbumArtRotationFlow.collectAsState()
    val showMinimizedDominantGlow by OverlayPreferences.showMinimizedDominantColorGlowFlow.collectAsState()
    val showExpandedDominantGlow by OverlayPreferences.showExpandedDominantColorGlowFlow.collectAsState()
    val expandedGlowSides by OverlayPreferences.expandedGlowSidesFlow.collectAsState()
    val minimizedBpmPulseEnabled by OverlayPreferences.minimizedBpmPulseEnabledFlow.collectAsState()
    val expandedBpmPulseEnabled by OverlayPreferences.expandedBpmPulseEnabledFlow.collectAsState()
    val pulseEntireIslandEnabled by OverlayPreferences.pulseEntireIslandEnabledFlow.collectAsState()
    val waveformBandCount by OverlayPreferences.waveformBandCountFlow.collectAsState()
    val waveformBarWidth by OverlayPreferences.waveformBarWidthFlow.collectAsState()
    val waveformBarSpacing by OverlayPreferences.waveformBarSpacingFlow.collectAsState()
    val visualizerMode by OverlayPreferences.visualizerModeFlow.collectAsState()
    val currentBpm by BpmDetector.currentBpm.collectAsState()
    val isBpmResolved by BpmDetector.isResolved.collectAsState()
    val detectedBpmTrack by BpmDetector.detectedTrack.collectAsState()
    val detectedSource by BpmDetector.detectedSource.collectAsState()
    val isBpmDetecting by BpmDetector.isDetecting.collectAsState()
    var showHqWarningDialog by remember { mutableStateOf(false) }
    var selectedPreviewElement by remember { mutableStateOf<MusicPreviewElement?>(null) }
    val extractionState by AudioPreviewExtractor.extractionState.collectAsState()
    val isPreviewPlaying by PreviewAudioPlayer.isPlaying.collectAsState()
    val previewPositionMs by PreviewAudioPlayer.currentPositionMs.collectAsState()
    val previewDurationMs by PreviewAudioPlayer.durationMs.collectAsState()
    val previewSource by OverlayPreferences.previewAudioSourceFlow.collectAsState()
    val pulseAmount by OverlayPreferences.pulseAmountFlow.collectAsState()
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
        AudioPreviewExtractor.setDemoPreviewVisible(true)
        onDispose {
            AudioPreviewExtractor.setDemoPreviewVisible(false)
            PreviewAudioPlayer.stop()
        }
    }

    val nowPlaying by MediaPlaybackState.currentTrack.collectAsState()

    LaunchedEffect(
        nowPlaying.title,
        nowPlaying.artist,
        visualizerMode,
        minimizedBpmPulseEnabled,
        expandedBpmPulseEnabled,
        pulseEntireIslandEnabled,
    ) {
        val title = if (nowPlaying.hasMedia) nowPlaying.title else "Starboy"
        val artist = if (nowPlaying.hasMedia) nowPlaying.artist else "The Weeknd"
        if (visualizerMode == OverlayPreferences.VisualizerMode.BPM || minimizedBpmPulseEnabled || expandedBpmPulseEnabled || pulseEntireIslandEnabled) {
            BpmDetector.syncTrack(title, artist)
        }
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.initializeMusicSettings(context)
    }

    val scrollState = rememberLazyListState()
    val inspectorHeaderHeight = with(LocalDensity.current) { 56.dp.roundToPx() }
    LaunchedEffect(selectedPreviewElement) {
        val element = selectedPreviewElement ?: return@LaunchedEffect
        withFrameNanos { }
        val layout = scrollState.layoutInfo
        val inspector = layout.visibleItemsInfo.firstOrNull { it.key == "preview_inspector_${element.name}" }
        if (inspector == null) {
            val preview = layout.visibleItemsInfo.firstOrNull { it.key == "preview" }
            if (preview != null) scrollState.animateScrollToItem(preview.index + 1)
        } else if (layout.viewportEndOffset - inspector.offset < inspectorHeaderHeight) {
            scrollState.animateScrollToItem(inspector.index)
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "settings_sections") {
            LarpConnectedButtons(
                options = MusicSettingsSection.entries.map { section ->
                    LarpButtonOption(section.label, checked = settingsSection == section) { checked ->
                        settingsSection = section.takeIf { checked }
                    }
                },
                singleSelection = false,
            )
        }
        val section = settingsSection
        if (section == MusicSettingsSection.LAYOUT) {
            item(key = "player_layout") {
                LarpConnectedButtons(
                    options = OverlayPreferences.ExpandedPlayerLayout.entries.map { layout ->
                        LarpButtonOption(
                            checked = expandedPlayerLayout == layout,
                            label = layout.label,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedPlayerLayout(context, layout)
                            },
                        )
                    },
                )
            }
        } else if (section != null) {
            musicSettingsItems(
                section = section,
                context = context,
                showSongAnnouncement = showSongAnnouncement,
                pulseEntireIslandEnabled = pulseEntireIslandEnabled,
                showExpandedAlbumArt = showExpandedAlbumArt,
                expandedElementVisibility = expandedElementVisibility,
                hideMusicWhenAppOpen = hideMusicWhenAppOpen,
                musicPauseTimeoutSeconds = musicPauseTimeoutSeconds,
                musicQuickSeekSeconds = musicQuickSeekSeconds,
                usageAccessGranted = usageAccessGranted,
                pulseAmount = pulseAmount,
            )
        }

        item(key = "preview") {
            IslandPreview(
                cutoutInfo = cutoutInfo,
                type = IslandPreviewType.MUSIC,
                selectedMusicElement = selectedPreviewElement,
                onMusicElementClick = { element ->
                    selectedPreviewElement = if (selectedPreviewElement == element) null else element
                },
            )
        }

        selectedPreviewElement?.let { activeElement ->
            motionItem(key = "preview_inspector_${activeElement.name}") {
                MusicPreviewInspectorCard(
                    element = activeElement,
                    onDismiss = { selectedPreviewElement = null },
                    context = context,
                    minimizedStyle = minimizedStyle,
                    minimizedShape = minimizedShape,
                    minimizedRotation = minimizedRotation,
                    showMinimizedProgressOutline = showMinimizedProgressOutline,
                    showExpandedAlbumArt = showExpandedAlbumArt,
                    expandedShape = expandedShape,
                    expandedRotation = expandedRotation,
                    showExpandedProgressOutline = showExpandedProgressOutline,
                    albumArtGlow = albumArtGlow,
                    expandedElementVisibility = expandedElementVisibility,
                    showProgressSquiggles = showProgressSquiggles,
                    progressBarAccentColor = progressBarAccentColor,
                    playbackControlsStyle = playbackControlsStyle,
                    trackInfoStyle = trackInfoStyle,
                    expandedStyle = expandedStyle,
                    showMinimizedDominantGlow = showMinimizedDominantGlow,
                    minimizedBpmPulseEnabled = minimizedBpmPulseEnabled,
                    showExpandedDominantGlow = showExpandedDominantGlow,
                    expandedGlowSides = expandedGlowSides,
                    expandedBpmPulseEnabled = expandedBpmPulseEnabled,
                    waveformBandCount = waveformBandCount,
                    waveformBarWidth = waveformBarWidth,
                    waveformBarSpacing = waveformBarSpacing,
                    visualizerMode = visualizerMode,
                    previewSource = previewSource,
                    extractionState = extractionState,
                    isPreviewPlaying = isPreviewPlaying,
                    previewPositionMs = previewPositionMs,
                    previewDurationMs = previewDurationMs,
                    currentBpm = currentBpm,
                    isBpmResolved = isBpmResolved,
                    isBpmDetecting = isBpmDetecting,
                    detectedBpmTrack = detectedBpmTrack,
                    detectedSource = detectedSource,
                    isLiveCapturing = isLiveCapturing,
                    liveAmplitudes = liveAmplitudes,
                    liveErrorMessage = liveErrorMessage,
                    mediaProjectionManager = mediaProjectionManager,
                    screenCaptureLauncher = screenCaptureLauncher,
                    onRequestAudioPreviewWarning = { showHqWarningDialog = true },
                )
            }
        }
    }

    if (showHqWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHqWarningDialog = false },
            title = {
                Text(
                    text = "Battery Usage",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Audio preview analysis decodes song previews on your device and uses more battery.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        OverlayPreferences.setVisualizerMode(context, OverlayPreferences.VisualizerMode.AUDIO_PREVIEW)
                        showHqWarningDialog = false
                    },
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHqWarningDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.musicSettingsItems(
    section: MusicSettingsSection,
    context: Context,
    showSongAnnouncement: Boolean,
    pulseEntireIslandEnabled: Boolean,
    showExpandedAlbumArt: Boolean,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    hideMusicWhenAppOpen: Boolean,
    musicPauseTimeoutSeconds: Int,
    musicQuickSeekSeconds: Int,
    usageAccessGranted: Boolean,
    pulseAmount: Float,
) {
    when (section) {
        MusicSettingsSection.LAYOUT -> Unit
        MusicSettingsSection.ELEMENTS -> {
            motionItem(key = "minimized_elements") {
                Text("Minimized", style = MaterialTheme.typography.labelLarge)
                LarpConnectedButtons(
                    singleSelection = false,
                    scrollable = true,
                    options = listOf(
                        LarpButtonOption(
                            label = "Song Change",
                            icon = LarpIcons.NotificationsActive,
                            checked = showSongAnnouncement,
                            onCheckedChange = {
                                OverlayPreferences.setShowSongAnnouncementEnabled(context, it)
                            },
                        ),
                        LarpButtonOption(
                            label = "Pulse Island",
                            icon = LarpIcons.Vibration,
                            checked = pulseEntireIslandEnabled,
                            onCheckedChange = {
                                OverlayPreferences.setPulseEntireIslandEnabled(context, it)
                            },
                        ),
                    ),
                )
            }

            motionItem(key = "expanded_elements") {
                Text("Expanded", style = MaterialTheme.typography.labelLarge)
                LarpConnectedButtons(
                    singleSelection = false,
                    scrollable = true,
                    options = listOf(
                        LarpButtonOption(
                            label = "Album Art",
                            icon = LarpIcons.PhotoLibrary,
                            checked = showExpandedAlbumArt,
                            onCheckedChange = {
                                OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it)
                            },
                        ),
                        LarpButtonOption(
                            label = "Track Info",
                            icon = LarpIcons.MusicNote,
                            checked = expandedElementVisibility.trackInfo,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(trackInfo = it),
                                )
                            },
                        ),
                        LarpButtonOption(
                            label = "Visualizer",
                            icon = LarpIcons.GraphicEq,
                            checked = expandedElementVisibility.visualizer,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(visualizer = it),
                                )
                            },
                        ),
                        LarpButtonOption(
                            label = "Progress Bar",
                            icon = LarpIcons.Tune,
                            checked = expandedElementVisibility.progress,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(progress = it),
                                )
                            },
                        ),
                        LarpButtonOption(
                            label = "Player Controls",
                            icon = LarpIcons.PlayArrow,
                            checked = expandedElementVisibility.mainControls,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(mainControls = it),
                                )
                            },
                        ),
                        LarpButtonOption(
                            label = "App Actions",
                            icon = LarpIcons.Apps,
                            checked = expandedElementVisibility.appActions,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(appActions = it),
                                )
                            },
                        ),
                        LarpButtonOption(
                            label = "BPM Chip",
                            icon = LarpIcons.Speed,
                            checked = expandedElementVisibility.bpm,
                            onCheckedChange = {
                                OverlayPreferences.setExpandedElementVisibility(
                                    context,
                                    OverlayPreferences.expandedElementVisibilityFlow.value.copy(bpm = it),
                                )
                            },
                        ),
                    ),
                )
            }

            motionItem(key = "pulse_amount") {
                LarpSliderRow(
                    title = "Pulse Amount",
                    valueLabel = "%.2fx".format(pulseAmount),
                    value = pulseAmount,
                    valueRange = 0.25f..3.0f,
                    steps = 10,
                    onValueChange = { OverlayPreferences.setPulseAmount(context, it) },
                )
            }
        }
        MusicSettingsSection.PLAYBACK -> {
            motionItem(key = "overview") {
                SectionHeader(title = "Playback")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LarpSwitchRow(
                            title = "Hide in Music App",
                            checked = hideMusicWhenAppOpen,
                            onCheckedChange = { OverlayPreferences.setHideMusicWhenAppOpenEnabled(context, it) },
                        )

                        if (hideMusicWhenAppOpen && !usageAccessGranted) {
                            Text(
                                "Allow usage access to detect when your music app is open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { ForegroundAppTracker.openUsageAccessSettings(context) }) {
                                Text("Grant usage access")
                            }
                        }

                        LarpDivider()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Pause Timeout",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val timeoutOptions = listOf(
                                5 to "5s",
                                15 to "15s",
                                30 to "30s",
                                60 to "1m",
                                0 to "Always",
                            )
                            LarpConnectedButtons(
                                options = timeoutOptions.map { (seconds, label) ->
                                    LarpButtonOption(
                                        checked = musicPauseTimeoutSeconds == seconds,
                                        label = label,
                                        onCheckedChange = {
                                            OverlayPreferences.setMusicPauseTimeoutSeconds(context, seconds)
                                        },
                                    )
                                },
                            )
                        }

                        LarpDivider()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Quick Seek Step",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Hold Previous or Next to seek",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val quickSeekOptions = listOf(
                                5 to "5s",
                                10 to "10s",
                                15 to "15s",
                                30 to "30s",
                            )
                            LarpConnectedButtons(
                                options = quickSeekOptions.map { (seconds, label) ->
                                    LarpButtonOption(
                                        checked = musicQuickSeekSeconds == seconds,
                                        label = label,
                                        onCheckedChange = {
                                            OverlayPreferences.setMusicQuickSeekSeconds(context, seconds)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
