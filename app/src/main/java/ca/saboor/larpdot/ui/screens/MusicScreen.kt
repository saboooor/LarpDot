package ca.saboor.larpdot.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
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
import ca.saboor.larpdot.ui.components.IslandPreview
import ca.saboor.larpdot.ui.components.IslandPreviewType
import ca.saboor.larpdot.ui.components.LarpCard
import ca.saboor.larpdot.ui.components.LarpDivider
import ca.saboor.larpdot.ui.components.LarpSliderRow
import ca.saboor.larpdot.ui.components.LarpSwitchRow
import ca.saboor.larpdot.ui.components.LarpToggleTile
import ca.saboor.larpdot.ui.components.MusicPreviewElement
import ca.saboor.larpdot.ui.components.SectionHeader
import ca.saboor.larpdot.ui.theme.LarpIcons
import ca.saboor.larpdot.ui.theme.LarpMotion
import ca.saboor.larpdot.ui.theme.motionItem
import ca.saboor.larpdot.visualizer.AudioPreviewExtractor
import ca.saboor.larpdot.visualizer.BpmDetector
import ca.saboor.larpdot.visualizer.LiveAudioVisualizer
import ca.saboor.larpdot.visualizer.PreviewAudioPlayer

private data class MusicElementToggle(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicScreen(
    cutoutInfo: CutoutInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slideTravel = with(LocalDensity.current) { 24.dp.roundToPx() }
    var showMusicSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showMusicSettings) {
        showMusicSettings = false
    }
    val showMinimizedProgressOutline by OverlayPreferences.showMinimizedProgressOutlineFlow.collectAsState()
    val showExpandedProgressOutline by OverlayPreferences.showExpandedProgressOutlineFlow.collectAsState()
    val hideMusicWhenAppOpen by OverlayPreferences.hideMusicWhenAppOpenFlow.collectAsState()
    val musicPauseTimeoutSeconds by OverlayPreferences.musicPauseTimeoutSecondsFlow.collectAsState()
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

    val showMinimizedTitle by OverlayPreferences.showMinimizedTitleFlow.collectAsState()
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
    val minimizedNestedBlurredBg by OverlayPreferences.minimizedNestedBlurredBgFlow.collectAsState()
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

    LaunchedEffect(
        nowPlaying.title,
        nowPlaying.artist,
        visualizerMode,
        waveformBandCount,
        previewSource,
        minimizedBpmPulseEnabled,
        expandedBpmPulseEnabled,
        pulseEntireIslandEnabled,
    ) {
        val title = if (nowPlaying.hasMedia) nowPlaying.title else "Starboy"
        val artist = if (nowPlaying.hasMedia) nowPlaying.artist else "The Weeknd"
        if (visualizerMode == OverlayPreferences.VisualizerMode.AUDIO_PREVIEW) {
            AudioPreviewExtractor.syncTrack(context, title, artist, true, waveformBandCount, previewSource)
        } else {
            AudioPreviewExtractor.syncTrack(context, title, artist, false, waveformBandCount, previewSource)
        }
        if (visualizerMode == OverlayPreferences.VisualizerMode.BPM || minimizedBpmPulseEnabled || expandedBpmPulseEnabled || pulseEntireIslandEnabled) {
            BpmDetector.syncTrack(title, artist)
        }
    }

    LaunchedEffect(Unit) {
        OverlayPreferences.initializeMusicSettings(context)
    }

    AnimatedContent(
        targetState = showMusicSettings,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        label = "music_screen_view",
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            val enter = fadeIn(LarpMotion.timing(250, delayMillis = 20, easing = LarpMotion.standard)) +
                slideInHorizontally(LarpMotion.timing(280, easing = LarpMotion.settle)) { direction * slideTravel }
            val exit = fadeOut(LarpMotion.fade(160)) +
                slideOutHorizontally(LarpMotion.fade(200)) { -direction * slideTravel / 2 }
            (enter togetherWith exit).using(null)
        },
    ) { isSettingsOpen ->
        if (!isSettingsOpen) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "preview_top_bar") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = { showMusicSettings = true },
                        ) {
                            Icon(
                                imageVector = LarpIcons.Settings,
                                contentDescription = "Music Settings",
                            )
                        }
                    }
                }

                item(key = "player_layout") {
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
                            minimizedNestedBlurredBg = minimizedNestedBlurredBg,
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
                            nowPlaying = nowPlaying,
                            mediaProjectionManager = mediaProjectionManager,
                            screenCaptureLauncher = screenCaptureLauncher,
                            onRequestAudioPreviewWarning = { showHqWarningDialog = true },
                        )
                    }
                }
            }
        } else {
            MusicSettingsPage(
                context = context,
                showMinimizedTitle = showMinimizedTitle,
                showSongAnnouncement = showSongAnnouncement,
                pulseEntireIslandEnabled = pulseEntireIslandEnabled,
                showExpandedAlbumArt = showExpandedAlbumArt,
                expandedElementVisibility = expandedElementVisibility,
                hideMusicWhenAppOpen = hideMusicWhenAppOpen,
                musicPauseTimeoutSeconds = musicPauseTimeoutSeconds,
                usageAccessGranted = usageAccessGranted,
                pulseAmount = pulseAmount,
                onBack = { showMusicSettings = false },
            )
        }
    }

    if (showHqWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHqWarningDialog = false },
            title = {
                Text(
                    text = "Performance Warning",
                    style = MaterialTheme.typography.titleLarge,
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
                    },
                ) {
                    Text(
                        text = "Enable Anyway",
                        color = MaterialTheme.colorScheme.error,
                    )
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
@Composable
private fun MusicSettingsPage(
    context: Context,
    showMinimizedTitle: Boolean,
    showSongAnnouncement: Boolean,
    pulseEntireIslandEnabled: Boolean,
    showExpandedAlbumArt: Boolean,
    expandedElementVisibility: OverlayPreferences.ExpandedElementVisibility,
    hideMusicWhenAppOpen: Boolean,
    musicPauseTimeoutSeconds: Int,
    usageAccessGranted: Boolean,
    pulseAmount: Float,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = LarpIcons.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Column {
                Text(
                    text = "Music Settings",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Minimized, expanded, and playback options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            motionItem(key = "minimized_right_side") {
                SectionHeader(title = "Minimized Right Side")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (showMinimizedTitle) {
                                "Scrolling track title on the right side of the minimized pill"
                            } else {
                                "Animated audio bars on the right side of the minimized pill"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                        ) {
                            toggleableItem(
                                checked = !showMinimizedTitle,
                                label = "Visualizer",
                                onCheckedChange = {
                                    OverlayPreferences.setShowMinimizedTitleEnabled(context, false)
                                    OverlayPreferences.setShowMinimizedVisualizerEnabled(context, true)
                                },
                                weight = 1f,
                            )
                            toggleableItem(
                                checked = showMinimizedTitle,
                                label = "Song Title",
                                onCheckedChange = {
                                    OverlayPreferences.setShowMinimizedTitleEnabled(context, true)
                                    OverlayPreferences.setShowMinimizedVisualizerEnabled(context, false)
                                },
                                weight = 1f,
                            )
                        }
                    }
                }
            }

            motionItem(key = "minimized_elements") {
                SectionHeader(title = "Minimized Elements")
                LarpCard {
                    MusicElementToggleGrid(
                        items = listOf(
                            MusicElementToggle(
                                title = "Song Change",
                                subtitle = "Announce new track",
                                icon = LarpIcons.NotificationsActive,
                                checked = showSongAnnouncement,
                                onCheckedChange = {
                                    OverlayPreferences.setShowSongAnnouncementEnabled(context, it)
                                },
                            ),
                            MusicElementToggle(
                                title = "Pulse Island",
                                subtitle = "Beat-sync pill scale",
                                icon = LarpIcons.Vibration,
                                checked = pulseEntireIslandEnabled,
                                onCheckedChange = {
                                    OverlayPreferences.setPulseEntireIslandEnabled(context, it)
                                },
                            ),
                        ),
                    )
                }
            }

            motionItem(key = "expanded_elements") {
                SectionHeader(title = "Expanded Elements")
                LarpCard {
                    MusicElementToggleGrid(
                        items = listOf(
                            MusicElementToggle(
                                title = "Album Art",
                                subtitle = "Show cover artwork",
                                icon = LarpIcons.PhotoLibrary,
                                checked = showExpandedAlbumArt,
                                onCheckedChange = {
                                    OverlayPreferences.setShowExpandedAlbumArtEnabled(context, it)
                                },
                            ),
                            MusicElementToggle(
                                title = "Track Info",
                                subtitle = "Title & artist",
                                icon = LarpIcons.MusicNote,
                                checked = expandedElementVisibility.trackInfo,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(trackInfo = it),
                                    )
                                },
                            ),
                            MusicElementToggle(
                                title = "Visualizer",
                                subtitle = "Audio bars",
                                icon = LarpIcons.GraphicEq,
                                checked = expandedElementVisibility.visualizer,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(visualizer = it),
                                    )
                                },
                            ),
                            MusicElementToggle(
                                title = "Progress Bar",
                                subtitle = "Seekable scrubber",
                                icon = LarpIcons.Tune,
                                checked = expandedElementVisibility.progress,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(progress = it),
                                    )
                                },
                            ),
                            MusicElementToggle(
                                title = "Player Controls",
                                subtitle = "Prev, play & next",
                                icon = LarpIcons.PlayArrow,
                                checked = expandedElementVisibility.mainControls,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(mainControls = it),
                                    )
                                },
                            ),
                            MusicElementToggle(
                                title = "App Actions",
                                subtitle = "Like, shuffle & more",
                                icon = LarpIcons.Apps,
                                checked = expandedElementVisibility.appActions,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(appActions = it),
                                    )
                                },
                            ),
                            MusicElementToggle(
                                title = "BPM Chip",
                                subtitle = "Track tempo badge",
                                icon = LarpIcons.Speed,
                                checked = expandedElementVisibility.bpm,
                                onCheckedChange = {
                                    OverlayPreferences.setExpandedElementVisibility(
                                        context,
                                        expandedElementVisibility.copy(bpm = it),
                                    )
                                },
                            ),
                        ),
                    )
                }
            }

            motionItem(key = "overview") {
                SectionHeader(title = "Display Options")
                LarpCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LarpSwitchRow(
                            title = "Hide in Music App",
                            subtitle = "Hide the music island while the active music player application is open",
                            checked = hideMusicWhenAppOpen,
                            onCheckedChange = { OverlayPreferences.setHideMusicWhenAppOpenEnabled(context, it) },
                        )

                        if (hideMusicWhenAppOpen) {
                            Text(
                                if (usageAccessGranted) "Usage access is enabled. The music island returns when you leave the player."
                                else "Allow usage access to detect when your active player is open without accessibility access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { ForegroundAppTracker.openUsageAccessSettings(context) }) {
                                Text(if (usageAccessGranted) "Manage usage access" else "Grant usage access")
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
                            Text(
                                text = if (musicPauseTimeoutSeconds == 0) {
                                    "Keep the music island visible when paused until you dismiss it"
                                } else {
                                    "Hide the music island ${musicPauseTimeoutSeconds}s after playback pauses"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val timeoutOptions = listOf(
                                5 to "5s",
                                15 to "15s",
                                30 to "30s",
                                60 to "1m",
                                0 to "Always",
                            )
                            ButtonGroup(
                                modifier = Modifier.fillMaxWidth(),
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            ) {
                                timeoutOptions.forEach { (seconds, label) ->
                                    toggleableItem(
                                        checked = musicPauseTimeoutSeconds == seconds,
                                        label = label,
                                        onCheckedChange = {
                                            OverlayPreferences.setMusicPauseTimeoutSeconds(context, seconds)
                                        },
                                        weight = 1f,
                                    )
                                }
                            }
                        }

                        LarpDivider()

                        LarpSliderRow(
                            title = "Pulse Amount",
                            subtitle = "Scale factor for BPM pulse intensity (0.25x – 3.0x)",
                            valueLabel = "%.2fx".format(pulseAmount),
                            value = pulseAmount,
                            valueRange = 0.25f..3.0f,
                            steps = 10,
                            onValueChange = { OverlayPreferences.setPulseAmount(context, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicElementToggleGrid(
    items: List<MusicElementToggle>,
    columns: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    LarpToggleTile(
                        title = item.title,
                        subtitle = item.subtitle,
                        icon = item.icon,
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
