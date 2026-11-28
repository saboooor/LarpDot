package ca.saboor.larpdot.service

import android.app.AppOpsManager
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuLiveUpdateSuppressorTest {
    private class FakeBackend(
        var ready: Boolean = true,
        val tokenToReturn: PromotedRestoreToken? = PromotedRestoreToken.NotificationManagerAllow,
        val channelTokenToReturn: MinimizedChannelRestoreToken? =
            MinimizedChannelRestoreToken(NotificationManager.IMPORTANCE_DEFAULT, wasUserLockedImportance = false),
    ) : LiveUpdatePromotionBackend {
        val revoked = mutableListOf<Pair<String, Int>>()
        val restored = mutableListOf<Triple<String, Int, PromotedRestoreToken>>()
        val minimizedChannels = mutableListOf<Triple<String, Int, String>>()
        val restoredChannels = mutableListOf<Pair<Pair<String, String>, MinimizedChannelRestoreToken>>()

        override fun isReady(): Boolean = ready

        override fun revokePromotion(packageName: String, uid: Int): PromotedRestoreToken? {
            revoked += packageName to uid
            return tokenToReturn
        }

        override fun restorePromotion(
            packageName: String,
            uid: Int,
            token: PromotedRestoreToken,
        ): Boolean {
            restored += Triple(packageName, uid, token)
            return true
        }

        override fun minimizeChannel(
            packageName: String,
            uid: Int,
            channelId: String,
        ): MinimizedChannelRestoreToken? {
            minimizedChannels += Triple(packageName, uid, channelId)
            return channelTokenToReturn
        }

        override fun restoreChannel(
            packageName: String,
            uid: Int,
            channelId: String,
            token: MinimizedChannelRestoreToken,
        ): Boolean {
            restoredChannels += (packageName to channelId) to token
            return true
        }
    }

    private class MemoryStore(
        initial: List<PersistedSuppressedPackage> = emptyList(),
        initialChannels: List<PersistedMinimizedChannel> = emptyList(),
    ) : SuppressedPackageStore {
        var entries: List<PersistedSuppressedPackage> = initial
        var channels: List<PersistedMinimizedChannel> = initialChannels
        override fun load(): List<PersistedSuppressedPackage> = entries
        override fun save(entries: List<PersistedSuppressedPackage>) {
            this.entries = entries
        }
        override fun loadMinimizedChannels(): List<PersistedMinimizedChannel> = channels
        override fun saveMinimizedChannels(entries: List<PersistedMinimizedChannel>) {
            this.channels = entries
        }
    }

    @Test
    fun revokesOnLiveUpdateKeepsSuppressedWhenDemotedRepostArrivesAndRestoresOnRemove() {
        val backend = FakeBackend()
        val store = MemoryStore()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend, store)

        // Initial promoted live update arrives -> revoke once.
        tracker.onNotificationPosted("0|com.ubercab|1|null|10100", "com.ubercab", 10100, isPromoted = true, enabled = true)
        assertEquals(listOf("com.ubercab" to 10100), backend.revoked)
        assertTrue(backend.restored.isEmpty())
        assertEquals(setOf("com.ubercab"), tracker.suppressedPackageNames())
        assertEquals(1, store.entries.size)

        // Android demotes and reposts the same key without promoted flag -> must NOT re-enable yet!
        tracker.onNotificationPosted("0|com.ubercab|1|null|10100", "com.ubercab", 10100, isPromoted = false, enabled = true)
        assertEquals(1, backend.revoked.size)
        assertTrue(backend.restored.isEmpty())

        // Live update ends and notification is removed -> restore permission.
        tracker.onNotificationRemoved("0|com.ubercab|1|null|10100")
        assertEquals(
            listOf(Triple("com.ubercab", 10100, PromotedRestoreToken.NotificationManagerAllow)),
            backend.restored,
        )
        assertTrue(tracker.suppressedPackageNames().isEmpty())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun waitsUntilAllPromotedKeysForSamePackageDisappearBeforeRestoring() {
        val backend = FakeBackend(tokenToReturn = PromotedRestoreToken.AppOpMode(AppOpsManager.MODE_ALLOWED))
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend)

        tracker.onNotificationPosted("key-1", "com.google.android.deskclock", 10200, isPromoted = true, enabled = true)
        tracker.onNotificationPosted("key-2", "com.google.android.deskclock", 10200, isPromoted = true, enabled = true)
        assertEquals(1, backend.revoked.size)

        tracker.onNotificationRemoved("key-1")
        assertTrue(backend.restored.isEmpty())

        tracker.onNotificationRemoved("key-2")
        assertEquals(
            listOf(Triple("com.google.android.deskclock", 10200, PromotedRestoreToken.AppOpMode(AppOpsManager.MODE_ALLOWED))),
            backend.restored,
        )
    }

    @Test
    fun restoresPersistedSuppressedPackagesAfterProcessRestartWhenNoLongerActive() {
        val persisted = listOf(
            PersistedSuppressedPackage("com.google.android.apps.maps", 10300, PromotedRestoreToken.NotificationManagerAllow),
        )
        val backend = FakeBackend()
        val store = MemoryStore(persisted)
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend, store)

        tracker.reconcileActiveKeys(emptySet(), enabled = true)

        assertEquals(
            listOf(Triple("com.google.android.apps.maps", 10300, PromotedRestoreToken.NotificationManagerAllow)),
            backend.restored,
        )
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun restoreAllRestoresImmediatelyWhenFeatureIsDisabled() {
        val backend = FakeBackend()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend)

        tracker.onNotificationPosted("key-1", "com.ubercab", 10100, isPromoted = true, enabled = true)
        assertEquals(1, backend.revoked.size)

        tracker.reconcileActiveKeys(setOf("key-1"), enabled = false)
        assertEquals(1, backend.restored.size)
        assertTrue(tracker.suppressedPackageNames().isEmpty())
    }

    @Test
    fun markPromotedKeyTracksImmediatelyBeforeAsyncRevoke() {
        val backend = FakeBackend()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend)

        tracker.markPromotedKey("0|com.google.android.apps.maps|1|null|10400", "com.google.android.apps.maps")
        assertTrue(tracker.isTrackedKey("0|com.google.android.apps.maps|1|null|10400"))

        tracker.onNotificationPosted(
            key = "0|com.google.android.apps.maps|1|null|10400",
            packageName = "com.google.android.apps.maps",
            uid = 10400,
            isPromoted = true,
            enabled = true,
        )
        assertEquals(listOf("com.google.android.apps.maps" to 10400), backend.revoked)
    }

    @Test
    fun minimizesChannelTemporarilyAndRestoresWhenLiveUpdateEnds() {
        val backend = FakeBackend()
        val store = MemoryStore()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend, store)

        tracker.onNotificationPosted(
            key = "0|com.ubercab|1|null|10100",
            packageName = "com.ubercab",
            uid = 10100,
            isPromoted = true,
            enabled = true,
            channelId = "trip_updates",
            minimizeChannel = true,
        )
        assertEquals(listOf("com.ubercab" to 10100), backend.revoked)
        assertEquals(listOf(Triple("com.ubercab", 10100, "trip_updates")), backend.minimizedChannels)
        assertEquals(1, store.channels.size)

        // Demoted repost keeps both promotion and channel suppressed.
        tracker.onNotificationPosted(
            key = "0|com.ubercab|1|null|10100",
            packageName = "com.ubercab",
            uid = 10100,
            isPromoted = false,
            enabled = true,
            channelId = "trip_updates",
            minimizeChannel = true,
        )
        assertEquals(1, backend.minimizedChannels.size)
        assertTrue(backend.restoredChannels.isEmpty())

        // Removing the live update restores both the channel and live-update promotion.
        tracker.onNotificationRemoved("0|com.ubercab|1|null|10100")
        assertEquals(1, backend.restoredChannels.size)
        assertEquals("com.ubercab" to "trip_updates", backend.restoredChannels.first().first)
        assertEquals(1, backend.restored.size)
        assertTrue(store.channels.isEmpty())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun disablingMinimizeToggleRestoresChannelWhileKeepingLiveUpdateSuppressed() {
        val backend = FakeBackend()
        val store = MemoryStore()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend, store)

        tracker.onNotificationPosted(
            key = "key-1",
            packageName = "com.ubercab",
            uid = 10100,
            isPromoted = true,
            enabled = true,
            channelId = "trip_updates",
            minimizeChannel = true,
        )
        assertEquals(1, backend.minimizedChannels.size)

        // User turns off Minimize Notification while keeping Hide Status Bar Live Updates ON.
        tracker.reconcileActiveKeys(setOf("key-1"), enabled = true, minimizeChannel = false)
        assertEquals(1, backend.restoredChannels.size)
        assertTrue(backend.restored.isEmpty())
        assertEquals(setOf("com.ubercab"), tracker.suppressedPackageNames())
        assertTrue(tracker.minimizedChannelIds().isEmpty())

        // Re-enabling Minimize Notification mid-live-update minimizes the already-tracked notification's channel.
        tracker.onNotificationPosted(
            key = "key-1",
            packageName = "com.ubercab",
            uid = 10100,
            isPromoted = false,
            enabled = true,
            channelId = "trip_updates",
            minimizeChannel = true,
        )
        assertEquals(2, backend.minimizedChannels.size)
        assertEquals(setOf("com.ubercab" to "trip_updates"), tracker.minimizedChannelIds())
    }

    @Test
    fun customIslandMinimizesMatchingChannelWithoutRevokingPromotionAndRestoresOnRemoveOrDisable() {
        val backend = FakeBackend()
        val store = MemoryStore()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend, store)

        // Regular (non-promoted) notification matched by a custom island with minimizeNotification = true
        tracker.onNotificationPosted(
            key = "custom-key-1",
            packageName = "com.thetransitapp.droid",
            uid = 10500,
            isPromoted = false,
            enabled = false,
            channelId = "go_channel",
            minimizeChannel = false,
            minimizeForCustomIsland = true,
        )
        assertTrue(backend.revoked.isEmpty())
        assertEquals(listOf(Triple("com.thetransitapp.droid", 10500, "go_channel")), backend.minimizedChannels)
        assertEquals(setOf("com.thetransitapp.droid" to "go_channel"), tracker.minimizedChannelIds())
        assertEquals(1, store.channels.size)

        // Reposting the same notification keeps the channel minimized without re-calling Shizuku
        tracker.onNotificationPosted(
            key = "custom-key-1",
            packageName = "com.thetransitapp.droid",
            uid = 10500,
            isPromoted = false,
            enabled = false,
            channelId = "go_channel",
            minimizeChannel = false,
            minimizeForCustomIsland = true,
        )
        assertEquals(1, backend.minimizedChannels.size)
        assertTrue(backend.restoredChannels.isEmpty())

        // Disabling the custom island's minimize option restores the channel on reconcile
        tracker.reconcileActiveKeys(
            activeKeys = setOf("custom-key-1"),
            enabled = false,
            minimizeChannel = false,
            customIslandMinimizedKeys = emptySet(),
        )
        assertEquals(1, backend.restoredChannels.size)
        assertTrue(tracker.minimizedChannelIds().isEmpty())
        assertTrue(store.channels.isEmpty())

        // Re-enabling minimizes again, and removing the notification restores it
        tracker.onNotificationPosted(
            key = "custom-key-1",
            packageName = "com.thetransitapp.droid",
            uid = 10500,
            isPromoted = false,
            enabled = false,
            channelId = "go_channel",
            minimizeChannel = false,
            minimizeForCustomIsland = true,
        )
        assertEquals(2, backend.minimizedChannels.size)
        tracker.onNotificationRemoved("custom-key-1")
        assertEquals(2, backend.restoredChannels.size)
        assertTrue(tracker.minimizedChannelIds().isEmpty())
    }

    @Test
    fun sharedChannelBetweenLiveUpdateAndCustomIslandRestoresOnlyWhenBothRelease() {
        val backend = FakeBackend()
        val tracker = LiveUpdateSuppressionTracker("ca.saboor.larpdot", backend)

        tracker.onNotificationPosted(
            key = "shared-key",
            packageName = "com.ubercab",
            uid = 10100,
            isPromoted = true,
            enabled = true,
            channelId = "trip_updates",
            minimizeChannel = true,
            minimizeForCustomIsland = true,
        )
        assertEquals(1, backend.minimizedChannels.size)

        // Turning off Live Update channel minimization keeps the channel minimized for the Custom Island
        tracker.restoreLiveUpdateChannels()
        assertTrue(backend.restoredChannels.isEmpty())
        assertEquals(setOf("com.ubercab" to "trip_updates"), tracker.minimizedChannelIds())

        // Once the Custom Island stops matching too, the channel is restored
        tracker.reconcileActiveKeys(
            activeKeys = setOf("shared-key"),
            enabled = true,
            minimizeChannel = false,
            customIslandMinimizedKeys = emptySet(),
        )
        assertEquals(1, backend.restoredChannels.size)
        assertTrue(tracker.minimizedChannelIds().isEmpty())

        // Pure rule matching helper checks visibility, condition, and minimizeNotification flag
        val notif = CustomNotification(
            key = "k1",
            packageName = "com.thetransitapp.droid",
            channelId = "go_channel",
            title = "Get off in 2 stops",
            text = "Arriving soon",
            postedAt = 1000L,
            actions = emptyList(),
        )
        val enabledIsland = CustomIsland(
            id = "c1",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.NOTIFICATION,
            notificationRule = CustomNotificationRule(
                packageName = "com.thetransitapp.droid",
                channelId = "go_channel",
                minimizeNotification = true,
            ),
        )
        assertTrue(matchesMinimizedCustomIsland(notif, listOf(enabledIsland)))
        assertTrue(!matchesMinimizedCustomIsland(notif, listOf(enabledIsland.copy(notificationRule = enabledIsland.notificationRule.copy(minimizeNotification = false)))))
        assertTrue(!matchesMinimizedCustomIsland(notif, listOf(enabledIsland.copy(condition = CustomIslandCondition.MUSIC))))
    }
}
