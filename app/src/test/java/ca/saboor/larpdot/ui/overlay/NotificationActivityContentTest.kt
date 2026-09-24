package ca.saboor.larpdot.ui.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.notification.NotificationActivityInfo
import ca.saboor.larpdot.notification.NotificationActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActivityContentTest {

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
        assertEquals(80f, defaultExtra, 0.001f)

        val smallExtra = calculateActivityCompactExtraDp(20f)
        assertEquals(80f, smallExtra, 0.001f)

        val largeExtra = calculateActivityCompactExtraDp(65f)
        assertEquals(166f, largeExtra, 0.001f)
        assertTrue(largeExtra > smallExtra)
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
        assertEquals(Icons.Default.Download, activityIcon(certainDownload))

        val genericProgress = createActivity(
            kind = NotificationActivityKind.PROGRESS,
            title = "Rendering scene",
            text = "75%",
        )
        // Generic progress that is not a download falls back to Notifications icon (when appIcon is not drawn)
        assertEquals(Icons.Default.Notifications, activityIcon(genericProgress))

        val callActivity = createActivity(kind = NotificationActivityKind.CALL)
        assertEquals(Icons.Default.Call, activityIcon(callActivity))

        val timerActivity = createActivity(kind = NotificationActivityKind.TIMER)
        assertEquals(Icons.Default.Timer, activityIcon(timerActivity))

        val navActivity = createActivity(kind = NotificationActivityKind.NAVIGATION)
        assertEquals(Icons.Default.Navigation, activityIcon(navActivity))
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
}
