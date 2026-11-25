package ca.saboor.larpdot.ui.overlay

import ca.saboor.larpdot.ui.theme.LarpIcons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.EarbudBatteryLevels
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.service.isPermanentShortTermAlert
import ca.saboor.larpdot.service.resolveFriendlyBluetoothName
import ca.saboor.larpdot.service.selectAutoExpandShortTermAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActivityContentTest {

    @Test
    fun onlyAvailableEarbudUsesTheRegularGaugeAndItsSideIcon() {
        val device = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Pixel Buds Pro",
            text = "Earbuds • Connected",
        ).copy(bluetoothDeviceType = "Earbuds", bluetoothBatteryPercent = 85)
        val rightOnly = device.copy(earbudBatteryLevels = EarbudBatteryLevels(left = null, right = 100))
        assertNull(rightOnly.pairedEarbudBatteryLevels)
        assertEquals(100, batteryPercentOf(rightOnly))
        assertEquals("100%", compactStatusFallback(rightOnly))
        assertEquals(LarpIcons.RightEarbud, activityIcon(rightOnly))
        assertTrue(hasCompactStatusContent(rightOnly))

        val leftOnly = device.copy(earbudBatteryLevels = EarbudBatteryLevels(left = 0, right = null))
        assertNull(leftOnly.pairedEarbudBatteryLevels)
        assertEquals(0, batteryPercentOf(leftOnly))
        assertEquals(LarpIcons.LeftEarbud, activityIcon(leftOnly))

        val both = device.copy(earbudBatteryLevels = EarbudBatteryLevels(left = 90, right = 100))
        assertEquals(both.earbudBatteryLevels, both.pairedEarbudBatteryLevels)
        assertNull(batteryPercentOf(both))
        assertEquals(85 to Color.Blue, secondaryDotRadialProgress(both, progressRingEnabled = true, primaryColor = Color.Blue))
        assertEquals(
            90 to Color.Blue,
            secondaryDotRadialProgress(both.copy(bluetoothBatteryPercent = null), progressRingEnabled = true, primaryColor = Color.Blue),
        )
        assertNull(
            secondaryDotRadialProgress(both.copy(text = "Earbuds • Disconnected"), progressRingEnabled = true, primaryColor = Color.Blue),
        )
        assertEquals(LarpIcons.Earbuds, activityIcon(both))
        assertTrue(hasCompactStatusContent(both))
    }

    private fun createActivity(
        kind: NotificationActivityKind,
        title: String = "Test Title",
        text: String? = "This is a very long notification description that should never appear on the minimized island because it cannot fit.",
        packageName: String = "com.test.app",
        progress: Int? = null,
        progressMax: Int? = null,
        progressIndeterminate: Boolean = false,
        chronometerBase: Long? = null,
    ): NotificationActivityInfo {
        return NotificationActivityInfo(
            key = "test_key",
            packageName = packageName,
            appName = "Test App",
            title = title,
            text = text,
            kind = kind,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = progressIndeterminate,
            chronometerBaseElapsedRealtime = chronometerBase,
            chronometerCountsDown = false,
            postedAtMillis = 1000L,
            contentIntent = null,
            actions = emptyList(),
        )
    }

    @Test
    fun bluetoothStatusShowsPercentageOnlyAndUsesDeviceSpecificIcons() {
        val headphones = createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "WH-1000XM5", text = "Headphones • Connected")
            .copy(bluetoothDeviceType = "Headphones", bluetoothBatteryPercent = 72)
        assertEquals("72%", compactStatusFallback(headphones))
        assertEquals("", compactStatusFallback(headphones.copy(bluetoothBatteryPercent = null)))
        assertEquals("", compactStatusFallback(headphones.copy(text = "Headphones • Disconnected")))
        assertEquals(LarpIcons.Headphones, activityIcon(headphones))

        val earbuds = createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Pixel Buds Pro", text = "Earbuds • Connected")
            .copy(bluetoothDeviceType = "Earbuds", bluetoothBatteryPercent = 85)
        assertEquals("85%", compactStatusFallback(earbuds))
        assertEquals(LarpIcons.Earbuds, activityIcon(earbuds))

        val watch = createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Pixel Watch 3", text = "Watch • Connected")
            .copy(bluetoothDeviceType = "Watch", bluetoothBatteryPercent = 64)
        assertEquals("64%", compactStatusFallback(watch))
        assertEquals(LarpIcons.Watch, activityIcon(watch))

        assertEquals(LarpIcons.Speaker, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "JBL Flip").copy(bluetoothDeviceType = "Speaker")))
        assertEquals(LarpIcons.DirectionsCar, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Car Audio").copy(bluetoothDeviceType = "Car audio")))
        assertEquals(LarpIcons.SportsEsports, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "DualSense").copy(bluetoothDeviceType = "Controller")))
        assertEquals(LarpIcons.Keyboard, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Keychron K2").copy(bluetoothDeviceType = "Keyboard")))
        assertEquals(LarpIcons.BluetoothConnected, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Unknown Device", text = "Bluetooth • Connected")))
        assertEquals(LarpIcons.BluetoothDisabled, activityIcon(createActivity(kind = NotificationActivityKind.BLUETOOTH, title = "Unknown Device", text = "Bluetooth • Disconnected")))
    }

    @Test
    fun minimizedIslandOmitsLongDescriptionsForNavigation() {
        val activity = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            title = "Turn right on Main St",
            text = "In 500 ft turn right on Main St then continue for 2.5 miles. Total 15 min remaining.",
        )
        val status = compactStatusFallback(activity)
        assertEquals("15 min", status)
        assertFalse(status.contains("In 500 ft turn right"))
    }

    @Test
    fun minimizedIslandShowsPercentageForDeterminateProgress() {
        val activity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Downloading File",
            text = "34.5 MB of 100 MB downloaded",
            progress = 35,
            progressMax = 100,
        )
        val status = compactStatusFallback(activity)
        assertEquals("35%", status)
    }

    @Test
    fun minimizedIslandExtractsPercentageFromTextWhenProgressNotSet() {
        val activity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Cloud Sync",
            text = "Backing up photos - 68% complete",
            progress = null,
            progressMax = null,
        )
        val status = compactStatusFallback(activity)
        assertEquals("68%", status)
    }

    @Test
    fun minimizedIslandFallsBackToShortLabelForCall() {
        val activity = createActivity(
            kind = NotificationActivityKind.CALL,
            title = "Alice Smith",
            text = "Call in progress with Alice Smith on speakerphone",
        )
        val status = compactStatusFallback(activity)
        assertEquals("Call", status)
    }

    @Test
    fun minimizedIslandFallsBackToEmptyForNavigationWithoutEta() {
        val activity = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            title = "Route Navigation",
            text = "Follow highlighted path on road",
        )
        val status = compactStatusFallback(activity)
        assertEquals("", status)
    }

    @Test
    fun navigationNeverShowsPercentageOnTheRight() {
        val navWithProgressFraction = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            title = "Google Maps",
            text = "Driving to Destination",
            progress = 35,
            progressMax = 100,
        )
        assertEquals("", compactStatusFallback(navWithProgressFraction))
        assertNull(activityRadialProgress(navWithProgressFraction, progressRingEnabled = true, primaryColor = Color.White))

        val navWithPercentText = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            title = "Navigation 45%",
            text = "Turn right in 500ft (50% route complete)",
        )
        assertEquals("", compactStatusFallback(navWithPercentText))

        val navWithEtaAndPercent = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            title = "Navigation (60%)",
            text = "15 min remaining (12 miles)",
        )
        assertEquals("15 min", compactStatusFallback(navWithEtaAndPercent))
        assertEquals("15m", formatCompactActivityDisplayStatus(NotificationActivityKind.NAVIGATION, "15 min"))
        assertEquals("1h25m", formatCompactActivityDisplayStatus(NotificationActivityKind.NAVIGATION, "1 hr 25 min"))
        assertEquals("2.5mi", formatCompactActivityDisplayStatus(NotificationActivityKind.NAVIGATION, "2.5 miles"))
        assertEquals("3.2km", formatCompactActivityDisplayStatus(NotificationActivityKind.NAVIGATION, "3.2 km"))
        assertEquals("15 min", formatCompactActivityDisplayStatus(NotificationActivityKind.TIMER, "15 min"))
    }

    @Test
    fun minimizedIslandFallsBackToEmptyForIndeterminateProgress() {
        val activity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Loading data",
            text = "Please wait while your content is being prepared",
            progress = null,
            progressMax = null,
            progressIndeterminate = true,
        )
        val status = compactStatusFallback(activity)
        assertEquals("", status)
    }

    @Test
    fun minimizedIslandFallsBackToEmptyWhenProgressHasNoPercentage() {
        val activity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Syncing",
            text = "Sync in progress",
            progress = null,
            progressMax = null,
            progressIndeterminate = false,
        )
        val status = compactStatusFallback(activity)
        assertEquals("", status)
    }

    @Test
    fun calculateCompactExtraDpExpandsDynamicallyWithTextWidth() {
        val defaultExtra = calculateActivityCompactExtraDp(0f)
        assertEquals(52f, defaultExtra, 0.001f)

        val smallExtra = calculateActivityCompactExtraDp(14f)
        assertEquals(52f, smallExtra, 0.001f)

        val largeExtra = calculateActivityCompactExtraDp(65f)
        assertEquals(146f, largeExtra, 0.001f)
        assertTrue(largeExtra > smallExtra)

        assertEquals(52f, compactNavigationExtraDp(0f), 0.001f)
        assertEquals(64f, compactNavigationExtraDp(24f), 0.001f)
        assertEquals(136f, compactNavigationExtraDp(80f), 0.001f)
    }

    @Test
    fun hasCompactStatusContentReturnsExpectedValue() {
        val emptyIndeterminate = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            progressIndeterminate = true,
            title = "Updating...",
            text = "Please wait",
        )
        assertFalse(hasCompactStatusContent(emptyIndeterminate))

        val nullProgress = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Syncing",
            text = "Working on it",
        )
        assertFalse(hasCompactStatusContent(nullProgress))

        val determinate = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            progress = 50,
            progressMax = 100,
        )
        assertTrue(hasCompactStatusContent(determinate))

        val textPercent = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            text = "Downloading 82% complete",
        )
        assertTrue(hasCompactStatusContent(textPercent))

        val call = createActivity(
            kind = NotificationActivityKind.CALL,
        )
        assertTrue(hasCompactStatusContent(call))

        val nav = createActivity(
            kind = NotificationActivityKind.NAVIGATION,
            text = "10 min remaining",
        )
        assertTrue(hasCompactStatusContent(nav))

        assertFalse(hasCompactStatusContent(null))
    }

    @Test
    fun isCertainDownloadIdentifiesActualDownloadsOnly() {
        val downloadInTitle = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Downloading file.zip",
            text = "45 MB / 100 MB",
        )
        assertTrue(isCertainDownload(downloadInTitle))

        val downloadInText = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Podcast App",
            text = "3 episodes downloaded",
        )
        assertTrue(isCertainDownload(downloadInText))

        val downloadPackage = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            packageName = "com.android.providers.downloads",
            title = "Transfer in progress",
            text = "50%",
        )
        assertTrue(isCertainDownload(downloadPackage))

        val uploadActivity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Uploading video",
            text = "32% uploaded",
        )
        assertFalse(isCertainDownload(uploadActivity))

        val syncActivity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Google Drive",
            text = "Syncing 14 files",
        )
        assertFalse(isCertainDownload(syncActivity))

        val exportActivity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Video Editor",
            text = "Exporting project... 55%",
        )
        assertFalse(isCertainDownload(exportActivity))

        val backupActivity = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Backup",
            text = "Backing up chats",
        )
        assertFalse(isCertainDownload(backupActivity))

        val nonProgressKind = createActivity(
            kind = NotificationActivityKind.CALL,
            title = "Downloading something",
        )
        assertFalse(isCertainDownload(nonProgressKind))
    }

    @Test
    fun activityIconReturnsDownloadIconOnlyWhenCertain() {
        val certainDownload = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Downloading offline maps",
        )
        assertEquals(LarpIcons.Download, activityIcon(certainDownload))

        val genericProgress = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Rendering scene",
            text = "75%",
        )
        // Generic progress that is not a download falls back to Notifications icon (when appIcon is not drawn)
        assertEquals(LarpIcons.Notifications, activityIcon(genericProgress))

        val callActivity = createActivity(kind = NotificationActivityKind.CALL)
        assertEquals(LarpIcons.Call, activityIcon(callActivity))

        val timerActivity = createActivity(kind = NotificationActivityKind.TIMER)
        assertEquals(LarpIcons.Timer, activityIcon(timerActivity))

        val navActivity = createActivity(kind = NotificationActivityKind.NAVIGATION)
        assertEquals(LarpIcons.Navigation, activityIcon(navActivity))
    }

    @Test
    fun formatCompactStatusAppliesSmallerFontSizeToPercent() {
        val formatted = formatCompactStatus("75%")
        assertEquals("75%", formatted.text)
        assertEquals(1, formatted.spanStyles.size)
        val span = formatted.spanStyles[0]
        assertEquals(2, span.start)
        assertEquals(3, span.end)
        assertEquals(8.sp, span.item.fontSize)

        val nonPercent = formatCompactStatus("15 min")
        assertEquals("15 min", nonPercent.text)
        assertTrue(nonPercent.spanStyles.isEmpty())
    }

    @Test
    fun activityIconTintUsesMaterialYouForNonSemanticKinds() {
        val materialYou = androidx.compose.ui.graphics.Color(0xFFD0BCFF)
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.BLUETOOTH, materialYou))
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.AUDIO_OUTPUT, materialYou))
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.TIMER, materialYou))
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.NAVIGATION, materialYou))
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.PROGRESS, materialYou))
        assertEquals(materialYou, activityIconTint(NotificationActivityKind.SCREENSHOT, materialYou))

        // Semantic status colors stay distinct
        assertEquals(androidx.compose.ui.graphics.Color(0xFF4CAF50), activityIconTint(NotificationActivityKind.CALL, materialYou))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF4CAF50), activityIconTint(NotificationActivityKind.BATTERY_CHARGING, materialYou))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF5252), activityIconTint(NotificationActivityKind.BATTERY_LOW, materialYou))
    }

    @Test
    fun bluetoothExpandedSubtitleOmitsRedundantBluetoothConnectedText() {
        val earbudsWithBattery = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Pixel Buds Pro",
            text = "Earbuds • Connected",
        ).copy(bluetoothDeviceType = "Earbuds", bluetoothBatteryPercent = 85)
        assertEquals("", activitySubtitle(earbudsWithBattery))
        assertEquals(85, batteryPercentOf(earbudsWithBattery))

        val genericWithBattery = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Audio Device",
            text = "Bluetooth • Connected",
        ).copy(bluetoothDeviceType = "Bluetooth", bluetoothBatteryPercent = 90)
        assertEquals("", activitySubtitle(genericWithBattery))
        assertEquals(90, batteryPercentOf(genericWithBattery))

        val genericNoBattery = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Audio Device",
            text = "Bluetooth • Connected",
        ).copy(bluetoothDeviceType = "Bluetooth", bluetoothBatteryPercent = null)
        assertEquals("", activitySubtitle(genericNoBattery))
        assertEquals(null, batteryPercentOf(genericNoBattery))

        val disconnected = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Pixel Buds Pro",
            text = "Earbuds • Disconnected",
        ).copy(bluetoothDeviceType = "Earbuds", bluetoothBatteryPercent = 85)
        assertEquals("Disconnected", activitySubtitle(disconnected))
        assertEquals(null, batteryPercentOf(disconnected))

        val charging = createActivity(
            kind = NotificationActivityKind.BATTERY_CHARGING,
            title = "Fast Charging",
            text = "72% • 25W • 28m left",
            progress = 72,
            progressMax = 100,
        )
        assertEquals(72, batteryPercentOf(charging))
        assertEquals("25W • 28m left", activitySubtitle(charging))

        val lowBattery = createActivity(
            kind = NotificationActivityKind.BATTERY_LOW,
            title = "Low Battery",
            text = "15% remaining • Connect charger",
            progress = 15,
            progressMax = 100,
        )
        assertEquals(15, batteryPercentOf(lowBattery))
        assertEquals("Connect charger", activitySubtitle(lowBattery))

        val download = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Downloading file.zip",
            text = "50%",
            progress = 50,
            progressMax = 100,
        )
        assertEquals(null, batteryPercentOf(download))
    }

    @Test
    fun shortTermAutoExpandSelectsNewAndModeChangedAlertsOnly() {
        val bt = createActivity(
            kind = NotificationActivityKind.BLUETOOTH,
            title = "Pixel Buds Pro",
            text = "Earbuds • Connected",
        ).copy(key = "system_bluetooth_1", postedAtMillis = 2000L)
        val soundRing = createActivity(
            kind = NotificationActivityKind.SOUND_MODE,
            title = "Ring Mode",
            text = "Calls and alerts will ring",
        ).copy(key = "system_sound_mode", postedAtMillis = 3000L)
        val soundSilent = soundRing.copy(title = "Silent Mode", postedAtMillis = 4000L)
        val charging = createActivity(
            kind = NotificationActivityKind.BATTERY_CHARGING,
            title = "Charging",
            text = "51%",
        ).copy(key = "system_battery_charging", postedAtMillis = 5000L)

        // Disabled setting never auto-expands
        assertEquals(null, selectAutoExpandShortTermAlert(listOf(bt), emptyMap(), expandEnabled = false) { false })

        // Brand-new alert triggers expansion
        assertEquals(bt, selectAutoExpandShortTermAlert(listOf(bt), emptyMap(), expandEnabled = true) { false })

        // Already-seen charging update with same title does not re-expand
        assertEquals(
            null,
            selectAutoExpandShortTermAlert(
                listOf(charging.copy(text = "52%")),
                mapOf("system_battery_charging" to "Charging"),
                expandEnabled = true,
            ) { false },
        )

        // Sound mode title change triggers expansion even when key already existed
        assertEquals(
            soundSilent,
            selectAutoExpandShortTermAlert(
                listOf(soundSilent),
                mapOf("system_sound_mode" to "Ring Mode"),
                expandEnabled = true,
            ) { false },
        )

        // Permanent short-term detection respects per-alert pin flags
        val pinnedSettings = NotificationActivitySettings(permanentCharging = true, permanentBluetooth = false)
        assertTrue(isPermanentShortTermAlert(charging, pinnedSettings))
        assertFalse(isPermanentShortTermAlert(bt, pinnedSettings))
    }

    @Test
    fun expandedActivityIconSpansLeftOfCutoutOnlyWhenSpaceExistsInPortrait() {
        // Centered portrait punch-hole (e.g. Pixel / Galaxy)
        assertTrue(shouldSpanActivityIconLeftOfCutout(isLandscape = false, topInsetDp = 33f, cutoutCenterXDp = 206f, cutoutDiameterDp = 26f))
        // Landscape sits beside the cutout (topInset = 0)
        assertFalse(shouldSpanActivityIconLeftOfCutout(isLandscape = true, topInsetDp = 0f, cutoutCenterXDp = 206f, cutoutDiameterDp = 26f))
        // Top-left corner punch-hole doesn't have room to its left
        assertFalse(shouldSpanActivityIconLeftOfCutout(isLandscape = false, topInsetDp = 30f, cutoutCenterXDp = 28f, cutoutDiameterDp = 24f))
    }

    @Test
    fun resolveFriendlyBluetoothNamePrefersAndroidAliasOverRawHardwareName() {
        assertEquals(
            "Saboor's Pixel Watch 5",
            resolveFriendlyBluetoothName(alias = "Saboor's Pixel Watch 5", rawName = "Pixel Watch 5 806S"),
        )
        assertEquals(
            "WH-1000XM5",
            resolveFriendlyBluetoothName(alias = "LE_WH-1000XM5", rawName = "LE_WH-1000XM5"),
        )
        assertEquals(
            "Pixel Buds Pro",
            resolveFriendlyBluetoothName(alias = null, rawName = "Pixel Buds Pro"),
        )
        assertEquals(
            "Bluetooth Device",
            resolveFriendlyBluetoothName(alias = "  ", rawName = null),
        )
    }

    @Test
    fun shapeProgressStartFindsTwelveOClockAndWindingDirection() {
        val center = androidx.compose.ui.geometry.Offset(50f, 50f)
        // Clockwise square starting at top-left: index 1 is top-center (12 o'clock).
        val cwSquare = listOf(
            androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset(50f, 0f),
            androidx.compose.ui.geometry.Offset(100f, 0f),
            androidx.compose.ui.geometry.Offset(100f, 50f),
            androidx.compose.ui.geometry.Offset(100f, 100f),
            androidx.compose.ui.geometry.Offset(50f, 100f),
            androidx.compose.ui.geometry.Offset(0f, 100f),
            androidx.compose.ui.geometry.Offset(0f, 50f),
        )
        val cwStart = findShapeTopStartAndWinding(cwSquare, center)
        assertEquals(1, cwStart.startIndex)
        assertTrue(cwStart.isClockwise)

        // Counter-clockwise square: top-center is at index 7, winding is CCW.
        val ccwSquare = cwSquare.reversed()
        val ccwStart = findShapeTopStartAndWinding(ccwSquare, center)
        assertEquals(6, ccwStart.startIndex)
        assertFalse(ccwStart.isClockwise)
    }

    @Test
    fun inlineNavigationActionSelectsEndExitStopOrFallsBackToFirst() {
        val navNoActions = createActivity(kind = NotificationActivityKind.NAVIGATION)
        assertNull(inlineNavigationAction(navNoActions))

        val nonNav = createActivity(kind = NotificationActivityKind.TIMER)
        assertNull(inlineNavigationAction(nonNav))
    }

    @Test
    fun dndAndVolumeHudRenderIconsRadialProgressAndPinBehavior() {
        val dndActive = createActivity(
            kind = NotificationActivityKind.DND,
            title = "Do Not Disturb",
            text = "Priority interruptions only",
        ).copy(key = "system_dnd")
        val dndOff = dndActive.copy(title = "Do Not Disturb Off", text = "Notifications resumed")

        assertEquals(LarpIcons.DoNotDisturbOn, activityIcon(dndActive))
        assertEquals("", compactStatusFallback(dndActive))
        assertEquals(Color(0xFF9C7CF8), activityIconTint(NotificationActivityKind.DND, Color.White))

        val dndPinnedSettings = NotificationActivitySettings(permanentDnd = true)
        assertTrue(isPermanentShortTermAlert(dndActive, dndPinnedSettings))
        assertFalse(isPermanentShortTermAlert(dndOff, dndPinnedSettings))
        assertEquals(
            dndOff,
            selectAutoExpandShortTermAlert(
                listOf(dndOff),
                mapOf("system_dnd" to "Do Not Disturb"),
                expandEnabled = true,
            ) { false },
        )

        val volMid = createActivity(
            kind = NotificationActivityKind.VOLUME,
            title = "Media Volume",
            text = "60% • Slide to adjust",
            progress = 60,
            progressMax = 100,
        ).copy(key = "system_volume")
        val volMuted = volMid.copy(title = "Media Muted", text = "Muted • Slide to unmute", progress = 0)

        assertEquals(LarpIcons.VolumeUp, activityIcon(volMid))
        assertEquals(LarpIcons.VolumeOff, activityIcon(volMuted))
        assertEquals("60%", compactStatusFallback(volMid))
        assertEquals(60 to Color.Cyan, activityRadialProgress(volMid, progressRingEnabled = false, primaryColor = Color.Cyan))
        assertEquals(0 to Color(0xFFFF5252), activityRadialProgress(volMuted, progressRingEnabled = false, primaryColor = Color.Cyan))
    }
}
