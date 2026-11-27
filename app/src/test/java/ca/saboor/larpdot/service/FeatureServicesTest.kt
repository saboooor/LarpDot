package ca.saboor.larpdot.service

import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import org.junit.Assert.*
import org.junit.Test

class FeatureServicesTest {

    @Test fun audioOutputPrefersExternalRoutesInPriorityOrder() {
        val speaker = selectPrimaryAudioOutput(emptyList())
        assertEquals(AudioOutputKind.PHONE_SPEAKER, speaker.kind)
        assertFalse(speaker.isExternal)

        val usb = AudioOutputDevice("usb:1", "USB-C DAC", AudioOutputKind.USB_AUDIO, true)
        val buds = AudioOutputDevice("bt:1", "Pixel Buds Pro", AudioOutputKind.BLUETOOTH_AUDIO, true)
        val car = AudioOutputDevice("car:1", "Car Audio", AudioOutputKind.CAR_AUDIO, true)

        assertEquals(usb, selectPrimaryAudioOutput(listOf(usb)))
        assertEquals(buds, selectPrimaryAudioOutput(listOf(usb, buds)))
        assertEquals(car, selectPrimaryAudioOutput(listOf(usb, buds, car)))
    }

    @Test fun batteryFormatterHandlesUnitsTimeAndChargeLimit() {
        // Microamps (Pixel/standard HAL) and milliamps (legacy HAL)
        val microAmpWatts = BatteryStatusFormatter.estimateChargingWatts(-4_500_000, 4_200)!!
        assertEquals(18.9f, microAmpWatts, 0.2f)
        assertEquals("Fast Charging", BatteryStatusFormatter.classifyChargingTitle(microAmpWatts))

        val superFastWatts = BatteryStatusFormatter.estimateChargingWatts(7_000_000, 4_300)!!
        assertEquals("Super Fast Charging", BatteryStatusFormatter.classifyChargingTitle(superFastWatts))

        val milliAmpWatts = BatteryStatusFormatter.estimateChargingWatts(2_000, 4_000)!!
        assertEquals(8.0f, milliAmpWatts, 0.1f)
        assertEquals("Charging", BatteryStatusFormatter.classifyChargingTitle(milliAmpWatts))

        assertNull(BatteryStatusFormatter.estimateChargingWatts(0, 4_000))
        assertEquals("1h 15m left", BatteryStatusFormatter.formatTimeRemaining(75 * 60_000L))
        assertEquals("42m left", BatteryStatusFormatter.formatTimeRemaining(42 * 60_000L))
        assertEquals("< 1m left", BatteryStatusFormatter.formatTimeRemaining(20_000L))
        assertNull(BatteryStatusFormatter.formatTimeRemaining(-1L))

        assertEquals(
            "64% • 27W • 24m left",
            BatteryStatusFormatter.buildChargingSubtitle(64, 27.1f, 24 * 60_000L),
        )
        assertEquals(
            "64% • 27W • 24m left • 34°C",
            BatteryStatusFormatter.buildChargingSubtitle(64, 27.1f, 24 * 60_000L, 34.2f),
        )
        assertEquals(
            "82% • 18W • 43°C (Warm)",
            BatteryStatusFormatter.buildChargingSubtitle(82, 18.0f, -1L, 42.6f),
        )
        assertEquals(36.5f, BatteryStatusFormatter.tenthsCelsiusToCelsius(365)!!, 0.01f)
        assertNull(BatteryStatusFormatter.tenthsCelsiusToCelsius(0))
        assertNull(BatteryStatusFormatter.tenthsCelsiusToCelsius(9999))
        assertFalse(BatteryStatusFormatter.isBatteryWarm(38.0f))
        assertTrue(BatteryStatusFormatter.isBatteryWarm(42.0f))

        assertTrue(BatteryStatusFormatter.shouldTriggerChargeLimit(true, 80, 80, false))
        assertFalse(BatteryStatusFormatter.shouldTriggerChargeLimit(true, 79, 80, false))
        assertFalse(BatteryStatusFormatter.shouldTriggerChargeLimit(true, 85, 80, true))
        assertFalse(BatteryStatusFormatter.shouldTriggerChargeLimit(false, 90, 80, false))
    }

    @Test fun fullscreenDetectorClassifiesImmersiveAndGames() {
        assertTrue(FullscreenStateTracker.isWindowCoveringScreen(0, 0, 1080, 2400, 1080, 2400))
        assertFalse(FullscreenStateTracker.isWindowCoveringScreen(0, 100, 1080, 2400, 1080, 2400))
        assertTrue(FullscreenStateTracker.isFullscreenState(appCoversFullScreen = true, statusBarVisible = false, isGame = false))
        assertFalse(FullscreenStateTracker.isFullscreenState(appCoversFullScreen = true, statusBarVisible = true, isGame = false))
        assertTrue(FullscreenStateTracker.isFullscreenState(appCoversFullScreen = false, statusBarVisible = true, isGame = true))
    }

    @Test fun flashlightTileSubtitleReflectsAvailabilityBoostAndStrength() {
        assertEquals(
            "Unavailable",
            formatFlashlightTileSubtitle(false, false, false, true, 5, 10),
        )
        assertEquals(
            "Off",
            formatFlashlightTileSubtitle(true, false, false, true, 5, 10),
        )
        assertEquals(
            "50%",
            formatFlashlightTileSubtitle(true, true, false, true, 5, 10),
        )
        assertEquals(
            "Boost • 100%",
            formatFlashlightTileSubtitle(true, true, true, true, 10, 10),
        )
        assertEquals(
            "On",
            formatFlashlightTileSubtitle(true, true, false, false, 1, 1),
        )
    }

    @Test fun trackTimestampPlacementIsSavedIndependentlyOfPlayerLayout() {
        val legacy = IslandConfigurationBackup.fromJson("""{"expandedPlayerLayout":"IPHONE"}""")!!
        assertEquals(OverlayPreferences.ProgressTimestampPlacement.BESIDE, legacy.progressTimestampPlacement)

        val configured = legacy.copy(
            expandedPlayerLayout = OverlayPreferences.ExpandedPlayerLayout.MATERIAL_3_EXPRESSIVE,
            progressTimestampPlacement = OverlayPreferences.ProgressTimestampPlacement.BELOW,
        )
        assertEquals(configured, IslandConfigurationBackup.fromJson(IslandConfigurationBackup.toJson(configured)))
    }

    @Test fun themePresetsRoundTripThroughJson() {
        val original = IslandBackupConfig(
            bubbleStyle = OverlayPreferences.BubbleStyle.IPHONE,
            smallerBubbles = true,
            minimizedMaxWidthPercent = 84f,
            islandCornerRadius = 42f,
            islandSquircleEnabled = false,
            expandedBackgroundOpacity = 0.6f,
            backdropBlurEnabled = true,
            backdropBlurRadius = 36f,
            cameraCoverStyle = OverlayPreferences.CameraCoverStyle.CANOPY,
            islandFontStyle = OverlayPreferences.IslandFontStyle.ROUNDED,
            islandFontWeight = 700,
            minimizedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.PENTAGON,
            minimizedAlbumArtRotation = 45f,
            showExpandedAlbumArt = false,
            expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.DIAMOND,
            expandedAlbumArtRotation = 15f,
            showProgressSquiggles = false,
            progressBarAccentColor = true,
            playbackPlayPauseBg = false,
            playbackSkipBg = true,
            playbackCornerRadiusDp = 14f,
            playbackM3ButtonGroup = true,
            audioOutputStyle = OverlayPreferences.AudioOutputStyle.BUTTON,
            playbackPlayPauseAccent = true,
            trackInfoShowTitle = true,
            trackInfoShowArtist = false,
            trackInfoShowAlbum = true,
            trackInfoAlbumBesideArtist = true,
            trackInfoTitleAccent = true,
            showMinimizedTitle = true,
            minimizedMusicTimestamp = OverlayPreferences.MinimizedMusicTimestamp.REMAINING,
            expandedGlowLeft = false,
            expandedGlowRight = true,
            expandedGlowTop = true,
            expandedGlowBottom = false,
            minimizedBpmPulseEnabled = false,
            expandedBpmPulseEnabled = true,
            pulseAmount = 1.75f,
            waveformBandCount = 12,
            waveformBarWidth = 3.5f,
            waveformBarSpacing = 2.0f,
            flashlightExpandedStyle = OverlayPreferences.FlashlightExpandedStyle.SLIDER,
            flashlightBeamBlur = true,
            flashlightBeamAccentColor = false,
            musicPauseTimeoutSeconds = 30,
            musicQuickSeekSeconds = 15,
            tapToExpand = true,
            hapticsEnabled = false,
            expandedAutoCollapseSeconds = 15,
            holdToExpandDurationMs = 550,
            swipeDownSensitivity = ca.saboor.larpdot.ui.overlay.SwipeDownSensitivity.EASY,
            animationSpeed = OverlayPreferences.AnimationSpeed.SNAPPY,
            hideWhenScreenOff = false,
            hideOnLockScreen = true,
            hideInFullscreen = true,
            hideInLandscape = true,
            hideInDnd = true,
            hideMusicWhenAppOpen = false,
            showSongAnnouncement = false,
            expandedShowTrackInfo = false,
            expandedShowProgress = false,
            expandedShowMainControls = true,
            expandedShowAppActions = false,
            expandedShowVisualizer = false,
            expandedShowBpm = false,
            visualizerMode = OverlayPreferences.VisualizerMode.BPM,
            previewAudioSource = OverlayPreferences.PreviewAudioSource.ITUNES,
            activitiesEnabled = true,
            activityProgress = false,
            activityNavigation = true,
            activityCalls = true,
            activityTimers = false,
            activityBatteryCharging = true,
            activityBatteryLow = false,
            activitySoundMode = true,
            activityDnd = false,
            activityBluetooth = true,
            activityAudioOutput = false,
            activityChargeLimitAlert = true,
            activityChargeLimitPercent = 85,
            activityLowBatteryThresholdPercent = 25,
            activityPermanentCharging = true,
            activityPermanentLowBattery = false,
            activityPermanentSoundMode = true,
            activityPermanentDnd = true,
            activityPermanentBluetooth = true,
            activityPermanentAudioOutput = false,
            activityExpandShortTerm = true,
            activityProgressOutline = false,
            islandPriorityOrder = listOf(
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.NAVIGATION,
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.MEDIA,
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.SHORT_TERM_ALERTS,
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.LIVE_ACTIVITIES,
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.PINNED_SHORT_TERM,
                ca.saboor.larpdot.ui.overlay.IslandPriorityItem.FLASHLIGHT,
            ),
            cutoutManualEnabled = true,
            cutoutOffsetX = -4f,
            cutoutOffsetY = 6f,
            cutoutDiameterDp = 26f,
        )
        val json = IslandConfigurationBackup.toJson(original)
        val decoded = IslandConfigurationBackup.fromJson(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
        val legacy = json.trimEnd().dropLast(1) + ",\"flashlightTapToToggle\":false}"
        assertEquals(original, IslandConfigurationBackup.fromJson(legacy))
        assertNull(IslandConfigurationBackup.fromJson("not json"))
        assertNull(IslandConfigurationBackup.fromJson("""{"foo": "bar", "baz": 123}"""))

        // Verify bar spacing full 0..8dp range and charge limit 70..100% round-trip cleanly.
        val edgeSpacing = IslandBackupConfig(waveformBarSpacing = 0f, activityChargeLimitPercent = 70)
        assertEquals(edgeSpacing, IslandConfigurationBackup.fromJson(IslandConfigurationBackup.toJson(edgeSpacing)))
        val wideSpacing = IslandBackupConfig(waveformBarSpacing = 7.5f, activityChargeLimitPercent = 100)
        assertEquals(wideSpacing, IslandConfigurationBackup.fromJson(IslandConfigurationBackup.toJson(wideSpacing)))

        // Symmetric minimized music and flashlight side configs round-trip and migrate legacy keys
        val swappedSides = IslandBackupConfig(
            minimizedMusicLeftSide = OverlayPreferences.MinimizedMusicSideContent.VISUALIZER,
            minimizedMusicRightSide = OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART,
            minimizedFlashlightLeftSide = OverlayPreferences.MinimizedFlashlightSideContent.PERCENTAGE,
            minimizedFlashlightRightSide = OverlayPreferences.MinimizedFlashlightSideContent.ICON,
        )
        assertEquals(swappedSides, IslandConfigurationBackup.fromJson(IslandConfigurationBackup.toJson(swappedSides)))

        val legacySides = IslandConfigurationBackup.fromJson(
            """{"showMinimizedTitle":true,"showMinimizedFlashlightOutline":false}""",
        )!!
        assertEquals(OverlayPreferences.MinimizedMusicSideContent.ALBUM_ART, legacySides.minimizedMusicLeftSide)
        assertEquals(OverlayPreferences.MinimizedMusicSideContent.SONG_TITLE, legacySides.minimizedMusicRightSide)
        assertEquals(OverlayPreferences.MinimizedFlashlightSideContent.ICON, legacySides.minimizedFlashlightLeftSide)
        assertEquals(OverlayPreferences.MinimizedFlashlightSideContent.PERCENTAGE, legacySides.minimizedFlashlightRightSide)

        // Animation speed scaling clamps cleanly
        assertEquals(259, ca.saboor.larpdot.ui.theme.LarpMotion.scaleIslandDuration(360, OverlayPreferences.AnimationSpeed.SNAPPY.durationMultiplier))
        assertEquals(360, ca.saboor.larpdot.ui.theme.LarpMotion.scaleIslandDuration(360, OverlayPreferences.AnimationSpeed.DEFAULT.durationMultiplier))
        assertEquals(475, ca.saboor.larpdot.ui.theme.LarpMotion.scaleIslandDuration(360, OverlayPreferences.AnimationSpeed.SMOOTH.durationMultiplier))
        assertEquals(60, ca.saboor.larpdot.ui.theme.LarpMotion.scaleIslandDuration(50, 0.5f))
    }

    @Test
    fun configurationBackupValidatesTheFullJsonDocument() {
        assertNull(IslandConfigurationBackup.fromJson("""{"waveformBarSpacing": 3 bad}"""))
        assertNull(IslandConfigurationBackup.fromJson("""{"waveformBarSpacing": 3} {}"""))
        assertNull(IslandConfigurationBackup.fromJson("""{"unknown": {"waveformBarSpacing": 3}}"""))

        val config = IslandConfigurationBackup.fromJson(
            """{"waveformBarSpacing": 3e0, "cutoutOffsetX": 1e100, "cutoutOffsetY": "NaN", "unknown": [1, 2]}""",
        )
        assertNotNull(config)
        assertEquals(3f, config?.waveformBarSpacing)
        assertNull(config?.cutoutOffsetX)
        assertNull(config?.cutoutOffsetY)
    }

    @Test fun fullscreenAccessibilityThrottleCoalescesRapidBursts() {
        assertEquals(0L, DotAccessibilityService.fullscreenCheckDelayMs(nowMs = 1_000L, lastRunMs = 0L))
        assertEquals(0L, DotAccessibilityService.fullscreenCheckDelayMs(nowMs = 1_120L, lastRunMs = 1_000L))
        assertEquals(80L, DotAccessibilityService.fullscreenCheckDelayMs(nowMs = 1_040L, lastRunMs = 1_000L))
        assertEquals(120L, DotAccessibilityService.fullscreenCheckDelayMs(nowMs = 1_000L, lastRunMs = 1_000L))
    }

    @Test fun musicPauseGraceDelayResolvesTimeoutAndKeepForever() {
        assertEquals(5_000L, ca.saboor.larpdot.media.MediaPlaybackState.resolvePauseGraceDelayMs(5))
        assertEquals(30_000L, ca.saboor.larpdot.media.MediaPlaybackState.resolvePauseGraceDelayMs(30))
        assertEquals(60_000L, ca.saboor.larpdot.media.MediaPlaybackState.resolvePauseGraceDelayMs(60))
        assertNull(ca.saboor.larpdot.media.MediaPlaybackState.resolvePauseGraceDelayMs(0))
        assertNull(ca.saboor.larpdot.media.MediaPlaybackState.resolvePauseGraceDelayMs(-5))

        assertNull(resolveExpandedAutoCollapseDelayMs(0))
        assertNull(resolveExpandedAutoCollapseDelayMs(-10))
        assertEquals(10_000L, resolveExpandedAutoCollapseDelayMs(10))
    }

    @Test fun playPauseIconTintAdaptsToAccentLuminance() {
        assertEquals(
            androidx.compose.ui.graphics.Color.White,
            ca.saboor.larpdot.ui.overlay.playPauseIconTint(
                hasBackground = true,
                useAccentColor = true,
                accentColor = androidx.compose.ui.graphics.Color(0xFF1A1025),
            ),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF1B1A1E),
            ca.saboor.larpdot.ui.overlay.playPauseIconTint(
                hasBackground = true,
                useAccentColor = true,
                accentColor = androidx.compose.ui.graphics.Color(0xFFFFE082),
            ),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF4CAF50),
            ca.saboor.larpdot.ui.overlay.playPauseIconTint(
                hasBackground = false,
                useAccentColor = true,
                accentColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            ),
        )
    }

    @Test fun bluetoothFilterHidesFindHubTrackersPhonesAndUnbondedPeers() {
        // Real accessories are shown: earbuds (even with Find Hub FMDN UUID), watches, controllers, BLE HID, LE Audio.
        assertTrue(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
                uuids = listOf(
                    "0000110b-0000-1000-8000-00805f9b34fb",
                    "0000feaa-0000-1000-8000-00805f9b34fb",
                ),
                displayName = "Pixel Buds Pro 2",
            ),
        )
        assertTrue(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.WEARABLE,
                uuids = emptyList(),
                displayName = "Saboor's Pixel Watch 5",
            ),
        )
        assertTrue(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.PERIPHERAL,
                uuids = emptyList(),
                displayName = "DualSense Wireless Controller",
            ),
        )
        assertTrue(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.UNCATEGORIZED,
                uuids = listOf("00001812-0000-1000-8000-00805f9b34fb"),
                displayName = "MX Master 3S",
            ),
        )
        assertTrue(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = null,
                uuids = listOf("0000184e-0000-1000-8000-00805f9b34fb"),
                displayName = "LE Audio Buds",
            ),
        )

        // Find Hub items (both unbonded BLE check-ins and bonded GATT-only trackers) are hidden.
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_NONE,
                majorDeviceClass = BluetoothClass.Device.Major.UNCATEGORIZED,
                uuids = listOf("0000feaa-0000-1000-8000-00805f9b34fb"),
                displayName = "Keys",
            ),
        )
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.UNCATEGORIZED,
                uuids = listOf(
                    "0000fe2c-0000-1000-8000-00805f9b34fb",
                    "0000feaa-0000-1000-8000-00805f9b34fb",
                    "15190001-12f4-c226-88ed-2ac5579f2a85",
                ),
                displayName = "Moto Tag",
            ),
        )
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.PERIPHERAL,
                uuids = listOf("15190001-12f4-c226-88ed-2ac5579f2a85"),
                displayName = "Pebblebee Clip",
            ),
        )

        // Phones, Phone Hub laptops, and unnamed / raw-MAC BLE devices are hidden.
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.PHONE,
                uuids = listOf("0000110a-0000-1000-8000-00805f9b34fb"),
                displayName = "Pixel 9 Pro",
            ),
        )
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.PHONE,
                uuids = emptyList(),
                displayName = "Pixel 9 Pro",
                isProfileAudioEvent = true,
            ),
        )
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.COMPUTER,
                uuids = emptyList(),
                displayName = "Chromebook",
            ),
        )
        assertFalse(
            shouldSurfaceBluetoothDevice(
                bondState = BluetoothDevice.BOND_BONDED,
                majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
                uuids = emptyList(),
                displayName = "AA:BB:CC:DD:EE:FF",
            ),
        )
    }

    @Test fun dndFilterAndVolumePercentHelpersClassifyStatesCleanly() {
        val off = classifyDndFilter(NotificationManager.INTERRUPTION_FILTER_ALL)!!
        assertFalse(off.isActive)
        assertEquals("Do Not Disturb Off", off.title)

        val priority = classifyDndFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)!!
        assertTrue(priority.isActive)
        assertEquals("Do Not Disturb", priority.title)
        assertEquals("Priority interruptions only", priority.subtitle)

        val alarms = classifyDndFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)!!
        assertTrue(alarms.isActive)
        assertEquals("Alarms only", alarms.subtitle)

        val silence = classifyDndFilter(NotificationManager.INTERRUPTION_FILTER_NONE)!!
        assertTrue(silence.isActive)
        assertEquals("Total Silence", silence.title)

        assertNull(classifyDndFilter(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
    }

    @Test fun lowBatteryThresholdTriggersAndResetsWithHysteresis() {
        assertTrue(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = false, percent = 20, thresholdPercent = 20, alreadyNotified = false))
        assertTrue(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = false, percent = 14, thresholdPercent = 20, alreadyNotified = false))
        assertFalse(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = true, percent = 15, thresholdPercent = 20, alreadyNotified = false))
        assertFalse(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = false, percent = 21, thresholdPercent = 20, alreadyNotified = false))
        assertFalse(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = false, percent = 15, thresholdPercent = 20, alreadyNotified = true))
        assertFalse(BatteryStatusFormatter.shouldTriggerLowBattery(isCharging = false, percent = 0, thresholdPercent = 20, alreadyNotified = false))

        assertTrue(BatteryStatusFormatter.shouldResetLowBatteryAlert(isCharging = true, percent = 10, thresholdPercent = 20))
        assertTrue(BatteryStatusFormatter.shouldResetLowBatteryAlert(isCharging = false, percent = 23, thresholdPercent = 20))
        assertFalse(BatteryStatusFormatter.shouldResetLowBatteryAlert(isCharging = false, percent = 22, thresholdPercent = 20))
    }

    @Test fun islandTileAndSystemHealthSummariesClassifyAllStates() {
        assertEquals("Permission needed", formatIslandTileSubtitle(hasPermission = false, enabled = true, pausedByDnd = false))
        assertEquals("Hidden", formatIslandTileSubtitle(hasPermission = true, enabled = false, pausedByDnd = false))
        assertEquals("Paused (DND)", formatIslandTileSubtitle(hasPermission = true, enabled = true, pausedByDnd = true))
        assertEquals("Active", formatIslandTileSubtitle(hasPermission = true, enabled = true, pausedByDnd = false))

        val ready = ca.saboor.larpdot.ui.components.buildSystemHealthSummary(
            overlayEnabled = true,
            accessibilityConnected = true,
            overlayServiceRunning = false,
            accessibilityGranted = true,
            overlayGranted = true,
            notificationAccessGranted = true,
            listenerConnected = true,
            optionalGrants = listOf(true, false, true),
        )
        assertTrue(ready.isCoreReady)
        assertEquals("Accessibility (Shade & Lock)", ready.overlayEngineLabel)
        assertEquals("Connected", ready.listenerLabel)
        assertEquals(5, ready.grantedCount)
        assertEquals(6, ready.totalCount)

        val missing = ca.saboor.larpdot.ui.components.buildSystemHealthSummary(
            overlayEnabled = true,
            accessibilityConnected = false,
            overlayServiceRunning = false,
            accessibilityGranted = false,
            overlayGranted = false,
            notificationAccessGranted = false,
            listenerConnected = false,
            optionalGrants = listOf(false, false),
        )
        assertFalse(missing.isCoreReady)
        assertEquals("Permission needed", missing.overlayEngineLabel)
        assertEquals("Not granted", missing.listenerLabel)
        assertEquals(0, missing.grantedCount)
        assertEquals(5, missing.totalCount)
    }

    @Test fun notificationTextDeduplicationSkipsTitleCopiesAndBlanks() {
        assertEquals(
            "45% complete",
            ca.saboor.larpdot.notification.NotificationActivityState.deduplicateNotificationText(
                "Downloading",
                "Downloading",
                "  downloading  ",
                "45% complete",
            ),
        )
        assertNull(
            ca.saboor.larpdot.notification.NotificationActivityState.deduplicateNotificationText(
                "Timer",
                "Timer",
                "   ",
                null,
            ),
        )
        assertEquals(
            "Turn right on Oak St",
            ca.saboor.larpdot.notification.NotificationActivityState.deduplicateNotificationText(
                "5 min • 1.2 mi",
                null,
                "Turn right on Oak St",
            ),
        )
    }

    @Test fun soundModeClassificationAndTitleParsingRoundTrip() {
        val ring = classifySoundMode(android.media.AudioManager.RINGER_MODE_NORMAL)
        val vibrate = classifySoundMode(android.media.AudioManager.RINGER_MODE_VIBRATE)
        val silent = classifySoundMode(android.media.AudioManager.RINGER_MODE_SILENT)
        assertEquals("Ring Mode", ring.first)
        assertEquals("Vibrate Mode", vibrate.first)
        assertEquals("Silent Mode", silent.first)
        assertEquals(android.media.AudioManager.RINGER_MODE_NORMAL, ca.saboor.larpdot.ui.overlay.parseSoundModeFromTitle(ring.first))
        assertEquals(android.media.AudioManager.RINGER_MODE_VIBRATE, ca.saboor.larpdot.ui.overlay.parseSoundModeFromTitle(vibrate.first))
        assertEquals(android.media.AudioManager.RINGER_MODE_SILENT, ca.saboor.larpdot.ui.overlay.parseSoundModeFromTitle(silent.first))
    }
}
