package ca.saboor.larpdot.service

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

    @Test fun screenshotCandidateMatchesPathsAndNames() {
        assertTrue(ScreenshotPocketManager.isScreenshotCandidate("Pictures/Screenshots/", "IMG_001.png"))
        assertTrue(ScreenshotPocketManager.isScreenshotCandidate("DCIM/", "Screenshot_20261123.png"))
        assertFalse(ScreenshotPocketManager.isScreenshotCandidate("DCIM/Camera/", "PXL_20261123.jpg"))
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
            minimizedNestedBlurredBg = true,
            showExpandedAlbumArt = false,
            expandedAlbumArtShape = OverlayPreferences.NestedAlbumArtShape.DIAMOND,
            expandedAlbumArtRotation = 15f,
            showProgressSquiggles = false,
            progressBarAccentColor = true,
            playbackPlayPauseBg = false,
            playbackSkipBg = true,
            playbackCornerRadiusDp = 14f,
            playbackM3ButtonGroup = true,
            playbackPlayPauseAccent = true,
            trackInfoShowTitle = true,
            trackInfoShowArtist = false,
            trackInfoShowAlbum = true,
            trackInfoTitleAccent = true,
            showMinimizedTitle = true,
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
            flashlightTapToToggle = false,
            musicPauseTimeoutSeconds = 30,
            activitiesEnabled = true,
            activityProgress = false,
            activityNavigation = true,
            activityCalls = true,
            activityTimers = false,
            activityBatteryCharging = true,
            activityBatteryLow = false,
            activitySoundMode = true,
            activityBluetooth = true,
            activityAudioOutput = false,
            activityScreenshotPocket = true,
            activityChargeLimitAlert = true,
            activityChargeLimitPercent = 85,
            activityPermanentCharging = true,
            activityPermanentLowBattery = false,
            activityPermanentSoundMode = true,
            activityPermanentBluetooth = true,
            activityPermanentAudioOutput = false,
            activityPermanentScreenshotPocket = true,
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
        val json = IslandThemePresets.toJson(original)
        val decoded = IslandThemePresets.fromJson(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
        assertNull(IslandThemePresets.fromJson("not json"))
        assertNull(IslandThemePresets.fromJson("""{"foo": "bar", "baz": 123}"""))

        // Verify bar spacing full 0..8dp range and charge limit 70..100% round-trip cleanly.
        val edgeSpacing = IslandBackupConfig(waveformBarSpacing = 0f, activityChargeLimitPercent = 70)
        assertEquals(edgeSpacing, IslandThemePresets.fromJson(IslandThemePresets.toJson(edgeSpacing)))
        val wideSpacing = IslandBackupConfig(waveformBarSpacing = 7.5f, activityChargeLimitPercent = 100)
        assertEquals(wideSpacing, IslandThemePresets.fromJson(IslandThemePresets.toJson(wideSpacing)))
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
}
