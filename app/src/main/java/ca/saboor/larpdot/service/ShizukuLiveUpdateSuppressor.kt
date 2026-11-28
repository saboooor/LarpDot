package ca.saboor.larpdot.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.edit
import java.lang.reflect.Method
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

internal enum class ShizukuLiveUpdateStatus {
    UNSUPPORTED_API,
    NOT_RUNNING,
    NEEDS_PERMISSION,
    PERMISSION_DENIED,
    READY,
}

internal sealed class PromotedRestoreToken {
    data object NotificationManagerAllow : PromotedRestoreToken()
    data class AppOpMode(val mode: Int) : PromotedRestoreToken()

    fun serialize(): String = when (this) {
        NotificationManagerAllow -> "nm:allow"
        is AppOpMode -> "op:$mode"
    }

    companion object {
        fun deserialize(raw: String): PromotedRestoreToken? = when {
            raw == "nm:allow" -> NotificationManagerAllow
            raw.startsWith("op:") -> raw.removePrefix("op:").toIntOrNull()?.let(::AppOpMode)
            else -> null
        }
    }
}

internal data class MinimizedChannelRestoreToken(
    val originalImportance: Int,
    val wasUserLockedImportance: Boolean,
)

internal interface LiveUpdatePromotionBackend {
    fun isReady(): Boolean
    fun revokePromotion(packageName: String, uid: Int): PromotedRestoreToken?
    fun restorePromotion(packageName: String, uid: Int, token: PromotedRestoreToken): Boolean
    fun minimizeChannel(packageName: String, uid: Int, channelId: String): MinimizedChannelRestoreToken? = null
    fun restoreChannel(
        packageName: String,
        uid: Int,
        channelId: String,
        token: MinimizedChannelRestoreToken,
    ): Boolean = true
    fun lastErrorMessage(): String? = null
}

internal data class PersistedSuppressedPackage(
    val packageName: String,
    val uid: Int,
    val restoreToken: PromotedRestoreToken,
)

internal data class PersistedMinimizedChannel(
    val packageName: String,
    val uid: Int,
    val channelId: String,
    val restoreToken: MinimizedChannelRestoreToken,
)

internal interface SuppressedPackageStore {
    fun load(): List<PersistedSuppressedPackage>
    fun save(entries: List<PersistedSuppressedPackage>)
    fun loadMinimizedChannels(): List<PersistedMinimizedChannel> = emptyList()
    fun saveMinimizedChannels(entries: List<PersistedMinimizedChannel>) {}
}

/**
 * Tracks promoted Live Update notification keys per package (and optionally per notification
 * channel) so revoking promotion or minimizing the channel does not trigger an immediate re-enable
 * when Android reposts the demoted notification under the same key.
 */
internal class LiveUpdateSuppressionTracker(
    private val ownPackageName: String,
    private val backend: LiveUpdatePromotionBackend,
    private val store: SuppressedPackageStore? = null,
) {
    private data class SuppressedEntry(
        val uid: Int,
        val restoreToken: PromotedRestoreToken,
    )

    private data class ChannelKey(
        val packageName: String,
        val channelId: String,
    )

    private data class MinimizedChannelEntry(
        val uid: Int,
        val restoreToken: MinimizedChannelRestoreToken,
    )

    private enum class ChannelMinimizeReason {
        LIVE_UPDATE,
        CUSTOM_ISLAND,
    }

    private val lock = Any()
    private val trackedKeysByPackage = mutableMapOf<String, MutableSet<String>>()
    private val packageByKey = mutableMapOf<String, String>()
    private val suppressedPackages = mutableMapOf<String, SuppressedEntry>()

    private val trackedKeysByChannel = mutableMapOf<ChannelKey, MutableSet<String>>()
    private val channelByKey = mutableMapOf<String, ChannelKey>()
    private val channelReasonsByKey = mutableMapOf<String, MutableSet<ChannelMinimizeReason>>()
    private val minimizedChannels = mutableMapOf<ChannelKey, MinimizedChannelEntry>()

    init {
        store?.load()?.forEach { entry ->
            suppressedPackages[entry.packageName] = SuppressedEntry(entry.uid, entry.restoreToken)
        }
        store?.loadMinimizedChannels()?.forEach { entry ->
            minimizedChannels[ChannelKey(entry.packageName, entry.channelId)] =
                MinimizedChannelEntry(entry.uid, entry.restoreToken)
        }
    }

    fun markPromotedKey(key: String, packageName: String, channelId: String = "") {
        if (packageName.isBlank() || packageName == ownPackageName) return
        synchronized(lock) {
            trackedKeysByPackage.getOrPut(packageName) { mutableSetOf() }.add(key)
            packageByKey[key] = packageName
            if (channelId.isNotBlank()) {
                val ck = ChannelKey(packageName, channelId)
                trackedKeysByChannel.getOrPut(ck) { mutableSetOf() }.add(key)
                channelByKey[key] = ck
                channelReasonsByKey.getOrPut(key) { mutableSetOf() }.add(ChannelMinimizeReason.LIVE_UPDATE)
            }
        }
    }

    private fun removeChannelReasonLocked(
        key: String,
        reason: ChannelMinimizeReason,
    ): Triple<ChannelKey, Int, MinimizedChannelRestoreToken>? {
        val reasons = channelReasonsByKey[key] ?: return null
        if (!reasons.remove(reason)) return null
        if (reasons.isNotEmpty()) return null
        channelReasonsByKey.remove(key)
        val ck = channelByKey.remove(key) ?: return null
        val keys = trackedKeysByChannel[ck]
        keys?.remove(key)
        if (keys.isNullOrEmpty()) {
            trackedKeysByChannel.remove(ck)
            return minimizedChannels[ck]?.let { Triple(ck, it.uid, it.restoreToken) }
        }
        return null
    }

    fun onNotificationPosted(
        key: String,
        packageName: String,
        uid: Int,
        isPromoted: Boolean,
        enabled: Boolean,
        channelId: String = "",
        minimizeChannel: Boolean = false,
        minimizeForCustomIsland: Boolean = false,
    ) {
        if (packageName.isBlank() || packageName == ownPackageName) return
        if (!enabled && !minimizeForCustomIsland) {
            val channelToRestore = synchronized(lock) {
                removeChannelReasonLocked(key, ChannelMinimizeReason.CUSTOM_ISLAND)
            }
            if (channelToRestore != null) {
                val (ck, chUid, token) = channelToRestore
                if (backend.restoreChannel(ck.packageName, chUid, ck.channelId, token)) {
                    synchronized(lock) {
                        if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                            minimizedChannels.remove(ck)
                            persistChannelsLocked()
                        }
                    }
                }
            }
            if (suppressedPackages.isNotEmpty()) {
                restorePromotionsOnly()
            }
            return
        }

        var channelToRestore: Triple<ChannelKey, Int, MinimizedChannelRestoreToken>? = null
        val (shouldRevokePromotion, shouldMinimizeChannel) = synchronized(lock) {
            val alreadyTrackedForLiveUpdate = key in packageByKey
            val trackForLiveUpdate = enabled && (isPromoted || alreadyTrackedForLiveUpdate)
            if (trackForLiveUpdate) {
                trackedKeysByPackage.getOrPut(packageName) { mutableSetOf() }.add(key)
                packageByKey[key] = packageName
            }

            val wantsLiveUpdateChannel = trackForLiveUpdate && minimizeChannel && channelId.isNotBlank()
            val wantsCustomChannel = minimizeForCustomIsland && channelId.isNotBlank()

            if (!wantsLiveUpdateChannel) {
                channelToRestore = removeChannelReasonLocked(key, ChannelMinimizeReason.LIVE_UPDATE) ?: channelToRestore
            }
            if (!wantsCustomChannel) {
                channelToRestore = removeChannelReasonLocked(key, ChannelMinimizeReason.CUSTOM_ISLAND) ?: channelToRestore
            }

            val ck = if (wantsLiveUpdateChannel || wantsCustomChannel) {
                ChannelKey(packageName, channelId).also {
                    val previousCk = channelByKey[key]
                    if (previousCk != null && previousCk != it) {
                        trackedKeysByChannel[previousCk]?.remove(key)
                        if (trackedKeysByChannel[previousCk].isNullOrEmpty()) {
                            trackedKeysByChannel.remove(previousCk)
                        }
                    }
                    trackedKeysByChannel.getOrPut(it) { mutableSetOf() }.add(key)
                    channelByKey[key] = it
                    val reasons = channelReasonsByKey.getOrPut(key) { mutableSetOf() }
                    if (wantsLiveUpdateChannel) reasons.add(ChannelMinimizeReason.LIVE_UPDATE)
                    if (wantsCustomChannel) reasons.add(ChannelMinimizeReason.CUSTOM_ISLAND)
                }
            } else null

            val revoke = trackForLiveUpdate && packageName !in suppressedPackages
            val minimize = ck != null && ck !in minimizedChannels
            revoke to minimize
        }

        if (channelToRestore != null) {
            val (ck, chUid, token) = channelToRestore!!
            if (backend.restoreChannel(ck.packageName, chUid, ck.channelId, token)) {
                synchronized(lock) {
                    if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                        minimizedChannels.remove(ck)
                        persistChannelsLocked()
                    }
                }
            }
        }

        if ((!shouldRevokePromotion && !shouldMinimizeChannel) || !backend.isReady()) return

        if (shouldRevokePromotion) {
            val token = backend.revokePromotion(packageName, uid)
            if (token != null) {
                synchronized(lock) {
                    val stillTracked = trackedKeysByPackage[packageName]?.isNotEmpty() == true
                    if (!stillTracked) {
                        backend.restorePromotion(packageName, uid, token)
                    } else {
                        suppressedPackages[packageName] = SuppressedEntry(uid, token)
                        persistLocked()
                    }
                }
            }
        }

        if (shouldMinimizeChannel && channelId.isNotBlank()) {
            val ck = ChannelKey(packageName, channelId)
            val channelToken = backend.minimizeChannel(packageName, uid, channelId)
            if (channelToken != null) {
                synchronized(lock) {
                    val stillTracked = trackedKeysByChannel[ck]?.isNotEmpty() == true
                    if (!stillTracked) {
                        backend.restoreChannel(packageName, uid, channelId, channelToken)
                    } else {
                        minimizedChannels[ck] = MinimizedChannelEntry(uid, channelToken)
                        persistChannelsLocked()
                    }
                }
            }
        }
    }

    fun onNotificationRemoved(key: String) {
        val (packageToRestore, channelToRestore) = synchronized(lock) {
            val pkg = packageByKey.remove(key)
            val pkgRestore = if (pkg != null) {
                val keys = trackedKeysByPackage[pkg]
                keys?.remove(key)
                if (keys.isNullOrEmpty()) {
                    trackedKeysByPackage.remove(pkg)
                    suppressedPackages[pkg]?.let { Triple(pkg, it.uid, it.restoreToken) }
                } else null
            } else null

            channelReasonsByKey.remove(key)
            val ck = channelByKey.remove(key)
            val chRestore = if (ck != null) {
                val keys = trackedKeysByChannel[ck]
                keys?.remove(key)
                if (keys.isNullOrEmpty()) {
                    trackedKeysByChannel.remove(ck)
                    minimizedChannels[ck]?.let { Triple(ck, it.uid, it.restoreToken) }
                } else null
            } else null

            pkgRestore to chRestore
        }

        if (channelToRestore != null) {
            val (ck, uid, token) = channelToRestore
            if (backend.restoreChannel(ck.packageName, uid, ck.channelId, token)) {
                synchronized(lock) {
                    if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                        minimizedChannels.remove(ck)
                        persistChannelsLocked()
                    }
                }
            }
        }

        if (packageToRestore != null) {
            val (pkg, uid, token) = packageToRestore
            if (backend.restorePromotion(pkg, uid, token)) {
                synchronized(lock) {
                    if (trackedKeysByPackage[pkg].isNullOrEmpty()) {
                        suppressedPackages.remove(pkg)
                        persistLocked()
                    }
                }
            }
        }
    }

    fun reconcileActiveKeys(
        activeKeys: Set<String>,
        enabled: Boolean,
        minimizeChannel: Boolean = true,
        customIslandMinimizedKeys: Set<String> = emptySet(),
    ) {
        if (!enabled && customIslandMinimizedKeys.isEmpty()) {
            restoreAll()
            return
        }
        val packagesToRestore = mutableListOf<Triple<String, Int, PromotedRestoreToken>>()
        val channelsToRestore = mutableListOf<Triple<ChannelKey, Int, MinimizedChannelRestoreToken>>()
        synchronized(lock) {
            if (!enabled) {
                trackedKeysByPackage.clear()
                packageByKey.clear()
            } else {
                val stalePackageKeys = packageByKey.keys.filter { it !in activeKeys }
                stalePackageKeys.forEach { staleKey ->
                    val pkg = packageByKey.remove(staleKey)
                    if (pkg != null) {
                        val keys = trackedKeysByPackage[pkg]
                        keys?.remove(staleKey)
                        if (keys.isNullOrEmpty()) trackedKeysByPackage.remove(pkg)
                    }
                }
            }

            val trackedChannelKeys = channelByKey.keys.toList()
            trackedChannelKeys.forEach { trackedKey ->
                val reasons = channelReasonsByKey[trackedKey]
                if (trackedKey !in activeKeys) {
                    channelReasonsByKey.remove(trackedKey)
                    val ck = channelByKey.remove(trackedKey)
                    if (ck != null) {
                        trackedKeysByChannel[ck]?.remove(trackedKey)
                        if (trackedKeysByChannel[ck].isNullOrEmpty()) trackedKeysByChannel.remove(ck)
                    }
                } else {
                    if (!enabled || !minimizeChannel) {
                        reasons?.remove(ChannelMinimizeReason.LIVE_UPDATE)
                    }
                    if (trackedKey !in customIslandMinimizedKeys) {
                        reasons?.remove(ChannelMinimizeReason.CUSTOM_ISLAND)
                    }
                    if (reasons.isNullOrEmpty()) {
                        channelReasonsByKey.remove(trackedKey)
                        val ck = channelByKey.remove(trackedKey)
                        if (ck != null) {
                            trackedKeysByChannel[ck]?.remove(trackedKey)
                            if (trackedKeysByChannel[ck].isNullOrEmpty()) trackedKeysByChannel.remove(ck)
                        }
                    }
                }
            }

            suppressedPackages.forEach { (pkg, entry) ->
                if (trackedKeysByPackage[pkg].isNullOrEmpty()) {
                    packagesToRestore += Triple(pkg, entry.uid, entry.restoreToken)
                }
            }
            minimizedChannels.forEach { (ck, entry) ->
                if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                    channelsToRestore += Triple(ck, entry.uid, entry.restoreToken)
                }
            }
        }
        channelsToRestore.forEach { (ck, uid, token) ->
            if (backend.restoreChannel(ck.packageName, uid, ck.channelId, token)) {
                synchronized(lock) {
                    if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                        minimizedChannels.remove(ck)
                        persistChannelsLocked()
                    }
                }
            }
        }
        packagesToRestore.forEach { (pkg, uid, token) ->
            if (backend.restorePromotion(pkg, uid, token)) {
                synchronized(lock) {
                    if (trackedKeysByPackage[pkg].isNullOrEmpty()) {
                        suppressedPackages.remove(pkg)
                        persistLocked()
                    }
                }
            }
        }
    }

    private fun restorePromotionsOnly() {
        val pkgEntries = synchronized(lock) {
            trackedKeysByPackage.clear()
            packageByKey.clear()
            suppressedPackages.entries.map { (pkg, entry) -> Triple(pkg, entry.uid, entry.restoreToken) }
        }
        if (pkgEntries.isEmpty()) return
        val restoredPackages = mutableSetOf<String>()
        pkgEntries.forEach { (pkg, uid, token) ->
            if (backend.restorePromotion(pkg, uid, token)) {
                restoredPackages += pkg
            }
        }
        if (restoredPackages.isNotEmpty()) {
            synchronized(lock) {
                restoredPackages.forEach { suppressedPackages.remove(it) }
                persistLocked()
            }
        }
    }

    fun restoreLiveUpdateChannels() {
        val channelsToRestore = mutableListOf<Triple<ChannelKey, Int, MinimizedChannelRestoreToken>>()
        synchronized(lock) {
            channelByKey.keys.toList().forEach { key ->
                val reasons = channelReasonsByKey[key]
                reasons?.remove(ChannelMinimizeReason.LIVE_UPDATE)
                if (reasons.isNullOrEmpty()) {
                    channelReasonsByKey.remove(key)
                    val ck = channelByKey.remove(key)
                    if (ck != null) {
                        trackedKeysByChannel[ck]?.remove(key)
                        if (trackedKeysByChannel[ck].isNullOrEmpty()) trackedKeysByChannel.remove(ck)
                    }
                }
            }
            minimizedChannels.forEach { (ck, entry) ->
                if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                    channelsToRestore += Triple(ck, entry.uid, entry.restoreToken)
                }
            }
        }
        channelsToRestore.forEach { (ck, uid, token) ->
            if (backend.restoreChannel(ck.packageName, uid, ck.channelId, token)) {
                synchronized(lock) {
                    if (trackedKeysByChannel[ck].isNullOrEmpty()) {
                        minimizedChannels.remove(ck)
                        persistChannelsLocked()
                    }
                }
            }
        }
    }

    fun restoreAllChannels() {
        val entries = synchronized(lock) {
            trackedKeysByChannel.clear()
            channelByKey.clear()
            channelReasonsByKey.clear()
            minimizedChannels.entries.map { (ck, entry) -> Triple(ck, entry.uid, entry.restoreToken) }
        }
        if (entries.isEmpty()) return

        val restored = mutableSetOf<ChannelKey>()
        entries.forEach { (ck, uid, token) ->
            if (backend.restoreChannel(ck.packageName, uid, ck.channelId, token)) {
                restored += ck
            }
        }
        if (restored.isNotEmpty()) {
            synchronized(lock) {
                restored.forEach { minimizedChannels.remove(it) }
                persistChannelsLocked()
            }
        }
    }

    fun restoreAll() {
        val (pkgEntries, channelEntries) = synchronized(lock) {
            trackedKeysByPackage.clear()
            packageByKey.clear()
            trackedKeysByChannel.clear()
            channelByKey.clear()
            channelReasonsByKey.clear()
            val pkgs = suppressedPackages.entries.map { (pkg, entry) -> Triple(pkg, entry.uid, entry.restoreToken) }
            val chs = minimizedChannels.entries.map { (ck, entry) -> Triple(ck, entry.uid, entry.restoreToken) }
            pkgs to chs
        }

        if (channelEntries.isNotEmpty()) {
            val restoredChannels = mutableSetOf<ChannelKey>()
            channelEntries.forEach { (ck, uid, token) ->
                if (backend.restoreChannel(ck.packageName, uid, ck.channelId, token)) {
                    restoredChannels += ck
                }
            }
            if (restoredChannels.isNotEmpty()) {
                synchronized(lock) {
                    restoredChannels.forEach { minimizedChannels.remove(it) }
                    persistChannelsLocked()
                }
            }
        }

        if (pkgEntries.isNotEmpty()) {
            val restoredPackages = mutableSetOf<String>()
            pkgEntries.forEach { (pkg, uid, token) ->
                if (backend.restorePromotion(pkg, uid, token)) {
                    restoredPackages += pkg
                }
            }
            if (restoredPackages.isNotEmpty()) {
                synchronized(lock) {
                    restoredPackages.forEach { suppressedPackages.remove(it) }
                    persistLocked()
                }
            }
        }
    }

    fun suppressedPackageNames(): Set<String> = synchronized(lock) { suppressedPackages.keys.toSet() }

    fun minimizedChannelIds(): Set<Pair<String, String>> =
        synchronized(lock) { minimizedChannels.keys.map { it.packageName to it.channelId }.toSet() }

    fun isTrackedKey(key: String): Boolean = synchronized(lock) { key in packageByKey }

    private fun persistLocked() {
        store?.save(
            suppressedPackages.map { (pkg, entry) ->
                PersistedSuppressedPackage(pkg, entry.uid, entry.restoreToken)
            },
        )
    }

    private fun persistChannelsLocked() {
        store?.saveMinimizedChannels(
            minimizedChannels.map { (ck, entry) ->
                PersistedMinimizedChannel(ck.packageName, entry.uid, ck.channelId, entry.restoreToken)
            },
        )
    }
}

internal object ShizukuLiveUpdateSuppressor {
    private const val TAG = "ShizukuLiveUpdates"
    private const val PERMISSION_REQUEST = 111
    private const val PREFS_NAME = "larpdot_live_update_suppression"
    private const val KEY_SUPPRESSED_ENTRIES = "suppressed_entries"
    private const val KEY_MINIMIZED_CHANNELS = "minimized_channels"
    private const val USER_LOCKED_IMPORTANCE = 0x00000004
    private const val PERMISSION_POST_PROMOTED_NOTIFICATIONS =
        "android.permission.POST_PROMOTED_NOTIFICATIONS"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    // Notification.FLAG_PROMOTED_ONGOING = 262144 (0x00040000) in Android 16 (API 36).
    private const val FLAG_PROMOTED_ONGOING = 0x00040000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuLiveUpdateStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuLiveUpdateStatus> = _status.asStateFlow()

    private val _suppressedCount = MutableStateFlow(0)
    val suppressedCount: StateFlow<Int> = _suppressedCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val cachedChannels = ConcurrentHashMap<Pair<String, String>, NotificationChannel>()

    @Volatile
    private var initialized = false

    @Volatile
    private var tracker: LiveUpdateSuppressionTracker? = null

    fun isSupportedApi(): Boolean = Build.VERSION.SDK_INT >= 36

    fun isChannelMinimizeSupportedApi(): Boolean = true

    fun isTrackedKey(key: String): Boolean = tracker?.isTrackedKey(key) == true

    private fun publishState() {
        _suppressedCount.value = tracker?.suppressedPackageNames()?.size ?: 0
        _lastError.value = ShizukuPromotionBackend.lastErrorMessage()
    }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tracker = LiveUpdateSuppressionTracker(
            ownPackageName = appContext.packageName,
            backend = ShizukuPromotionBackend,
            store = PrefsSuppressedPackageStore(prefs),
        )
        publishState()

        Shizuku.addBinderReceivedListenerSticky(::refreshStatus)
        Shizuku.addBinderDeadListener {
            _status.value = ShizukuLiveUpdateStatus.NOT_RUNNING
        }
        Shizuku.addRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST) refreshStatus()
        }
        refreshStatus()

        scope.launch {
            combine(
                OverlayPreferences.isEnabledFlow,
                OverlayPreferences.notificationActivitySettingsFlow,
                CustomIslandStore.settings,
                status,
            ) { overlayEnabled, activitySettings, customSettings, shizukuStatus ->
                listOf(overlayEnabled, activitySettings, customSettings, shizukuStatus)
            }.collect { values ->
                val overlayEnabled = values[0] as Boolean
                val activitySettings = values[1] as ca.saboor.larpdot.notification.NotificationActivitySettings
                val customSettings = values[2] as CustomIslandSettings
                val shizukuStatus = values[3] as ShizukuLiveUpdateStatus

                val liveUpdatesActive = isSupportedApi() && isSuppressionEnabled(
                    overlayEnabled,
                    activitySettings.enabled,
                    activitySettings.hideStatusBarLiveUpdates,
                )
                val customMinimizeActive = overlayEnabled && customSettings.activeIslands.any { island ->
                    island.visibility == CustomIslandVisibility.CONDITIONAL &&
                        island.condition == CustomIslandCondition.NOTIFICATION &&
                        island.notificationRule.minimizeNotification
                }

                if (!liveUpdatesActive && !customMinimizeActive) {
                    ShizukuPromotionBackend.clearError()
                    if (shizukuStatus == ShizukuLiveUpdateStatus.READY) {
                        tracker?.restoreAll()
                    }
                    publishState()
                } else if (shizukuStatus == ShizukuLiveUpdateStatus.READY) {
                    if (!liveUpdatesActive || !activitySettings.minimizeStatusBarLiveUpdates) {
                        tracker?.restoreLiveUpdateChannels()
                        publishState()
                    }
                    NotificationAccessService.reconcileLiveUpdates()
                }
            }
        }
    }

    fun refreshStatus() {
        _status.value = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuLiveUpdateStatus.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuLiveUpdateStatus.UNSUPPORTED_API
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    if (Shizuku.shouldShowRequestPermissionRationale()) ShizukuLiveUpdateStatus.PERMISSION_DENIED
                    else ShizukuLiveUpdateStatus.NEEDS_PERMISSION
                else -> ShizukuLiveUpdateStatus.READY
            }
        }.getOrDefault(ShizukuLiveUpdateStatus.NOT_RUNNING)
    }

    fun requestPermission() {
        refreshStatus()
        if (_status.value == ShizukuLiveUpdateStatus.NEEDS_PERMISSION) {
            runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }.onFailure { refreshStatus() }
        }
    }

    private fun shouldMinimizeForCustomIsland(overlayEnabled: Boolean, key: String): Boolean {
        if (!overlayEnabled) return false
        val customNotification = CustomIslandNotifications.active.value[key] ?: return false
        return matchesMinimizedCustomIsland(customNotification, CustomIslandStore.settings.value.activeIslands)
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        initialize(context)
        val overlayEnabled = OverlayPreferences.isOverlayEnabled(context)
        val settings = OverlayPreferences.getNotificationActivitySettings(context)
        val enabled = isSupportedApi() && isSuppressionEnabled(
            overlayEnabled = overlayEnabled,
            activitiesEnabled = settings.enabled,
            hideStatusBarLiveUpdates = settings.hideStatusBarLiveUpdates,
        )
        val minimizeChannel = enabled && settings.minimizeStatusBarLiveUpdates
        val minimizeForCustom = shouldMinimizeForCustomIsland(overlayEnabled, sbn.key)
        val promoted = if (isSupportedApi()) isPromotedLiveUpdate(sbn.notification) else false
        val key = sbn.key
        val pkg = sbn.packageName
        val channelId = sbn.notification?.channelId.orEmpty()
        if (channelId.isNotBlank() && (minimizeChannel || minimizeForCustom)) {
            NotificationAccessService.resolveNotificationChannel(key)?.let { ch ->
                cachedChannels[pkg to channelId] = ch
            }
        }
        if (enabled && promoted) {
            tracker?.markPromotedKey(key, pkg, channelId)
        }
        val uid = resolvePackageUid(context, sbn) ?: return
        scope.launch {
            tracker?.onNotificationPosted(
                key = key,
                packageName = pkg,
                uid = uid,
                isPromoted = promoted,
                enabled = enabled,
                channelId = channelId,
                minimizeChannel = minimizeChannel,
                minimizeForCustomIsland = minimizeForCustom,
            )
            publishState()
        }
    }

    fun onNotificationRemoved(context: Context, key: String) {
        initialize(context)
        scope.launch {
            tracker?.onNotificationRemoved(key)
            publishState()
        }
    }

    fun reconcileActiveNotifications(context: Context, activeNotifications: Array<StatusBarNotification>?) {
        initialize(context)
        val overlayEnabled = OverlayPreferences.isOverlayEnabled(context)
        val settings = OverlayPreferences.getNotificationActivitySettings(context)
        val enabled = isSupportedApi() && isSuppressionEnabled(
            overlayEnabled = overlayEnabled,
            activitiesEnabled = settings.enabled,
            hideStatusBarLiveUpdates = settings.hideStatusBarLiveUpdates,
        )
        val minimizeChannel = enabled && settings.minimizeStatusBarLiveUpdates
        val activeKeys = activeNotifications?.map { it.key }?.toSet().orEmpty()
        val customMinimizedKeys = activeNotifications
            ?.filter { shouldMinimizeForCustomIsland(overlayEnabled, it.key) }
            ?.map { it.key }
            ?.toSet()
            .orEmpty()
        if (enabled || customMinimizedKeys.isNotEmpty()) {
            activeNotifications?.forEach { sbn ->
                val chId = sbn.notification?.channelId.orEmpty()
                val forCustom = sbn.key in customMinimizedKeys
                if (chId.isNotBlank() && (minimizeChannel || forCustom)) {
                    NotificationAccessService.resolveNotificationChannel(sbn.key)?.let { ch ->
                        cachedChannels[sbn.packageName to chId] = ch
                    }
                }
                if (enabled && isPromotedLiveUpdate(sbn.notification)) {
                    tracker?.markPromotedKey(sbn.key, sbn.packageName, chId)
                }
            }
        }
        scope.launch {
            tracker?.reconcileActiveKeys(
                activeKeys = activeKeys,
                enabled = enabled,
                minimizeChannel = minimizeChannel,
                customIslandMinimizedKeys = customMinimizedKeys,
            )
            if (enabled || customMinimizedKeys.isNotEmpty()) {
                activeNotifications?.forEach { sbn ->
                    val promoted = if (enabled) isPromotedLiveUpdate(sbn.notification) else false
                    val forCustom = sbn.key in customMinimizedKeys
                    if ((enabled && (promoted || isTrackedKey(sbn.key))) || forCustom) {
                        val uid = resolvePackageUid(context, sbn) ?: return@forEach
                        tracker?.onNotificationPosted(
                            key = sbn.key,
                            packageName = sbn.packageName,
                            uid = uid,
                            isPromoted = promoted,
                            enabled = enabled,
                            channelId = sbn.notification?.channelId.orEmpty(),
                            minimizeChannel = minimizeChannel,
                            minimizeForCustomIsland = forCustom,
                        )
                    }
                }
            }
            publishState()
        }
    }

    fun restoreMinimizedChannels(context: Context) {
        initialize(context)
        scope.launch {
            tracker?.restoreLiveUpdateChannels()
            publishState()
        }
    }

    fun restoreAll(context: Context) {
        initialize(context)
        scope.launch {
            tracker?.restoreAll()
            ShizukuPromotionBackend.clearError()
            publishState()
        }
    }

    internal fun isSuppressionEnabled(
        overlayEnabled: Boolean,
        activitiesEnabled: Boolean,
        hideStatusBarLiveUpdates: Boolean,
    ): Boolean = overlayEnabled && activitiesEnabled && hideStatusBarLiveUpdates

    internal fun isPromotedLiveUpdate(notification: Notification?): Boolean {
        if (notification == null) return false
        val hasFlag = (notification.flags and FLAG_PROMOTED_ONGOING) != 0
        if (hasFlag) return true
        val extras = notification.extras
        if (extras?.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, false) == true) return true
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!isOngoing) return false
        val isProgressStyle = extras?.getString(Notification.EXTRA_TEMPLATE)
            ?.contains("ProgressStyle", ignoreCase = true) == true
        if (isProgressStyle) return true
        val hasCriticalText = (if (Build.VERSION.SDK_INT >= 36) {
            runCatching { !notification.shortCriticalText.isNullOrBlank() }.getOrDefault(false)
        } else false) || !extras?.getCharSequence("android.shortCriticalText").isNullOrBlank()
        return hasCriticalText
    }

    private fun resolvePackageUid(context: Context, sbn: StatusBarNotification): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sbn.uid > 0) {
            return sbn.uid
        }
        return runCatching {
            context.packageManager.getApplicationInfo(sbn.packageName, 0).uid
        }.getOrNull()
    }

    private class PrefsSuppressedPackageStore(
        private val prefs: SharedPreferences,
    ) : SuppressedPackageStore {
        override fun load(): List<PersistedSuppressedPackage> =
            prefs.getStringSet(KEY_SUPPRESSED_ENTRIES, emptySet()).orEmpty().mapNotNull { raw ->
                val parts = raw.split('|')
                if (parts.size != 3) return@mapNotNull null
                val uid = parts[1].toIntOrNull() ?: return@mapNotNull null
                val token = PromotedRestoreToken.deserialize(parts[2]) ?: return@mapNotNull null
                PersistedSuppressedPackage(parts[0], uid, token)
            }

        override fun save(entries: List<PersistedSuppressedPackage>) {
            val serialized = entries.mapTo(HashSet()) { entry ->
                "${entry.packageName}|${entry.uid}|${entry.restoreToken.serialize()}"
            }
            prefs.edit { putStringSet(KEY_SUPPRESSED_ENTRIES, serialized) }
        }

        override fun loadMinimizedChannels(): List<PersistedMinimizedChannel> =
            prefs.getStringSet(KEY_MINIMIZED_CHANNELS, emptySet()).orEmpty().mapNotNull { raw ->
                val parts = raw.split('|')
                if (parts.size != 5) return@mapNotNull null
                val uid = parts[1].toIntOrNull() ?: return@mapNotNull null
                val channelId = runCatching {
                    String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8)
                }.getOrNull()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val importance = parts[3].toIntOrNull() ?: return@mapNotNull null
                val locked = parts[4] == "1"
                PersistedMinimizedChannel(
                    packageName = parts[0],
                    uid = uid,
                    channelId = channelId,
                    restoreToken = MinimizedChannelRestoreToken(importance, locked),
                )
            }

        override fun saveMinimizedChannels(entries: List<PersistedMinimizedChannel>) {
            val serialized = entries.mapTo(HashSet()) { entry ->
                val encodedChannel = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(entry.channelId.toByteArray(Charsets.UTF_8))
                val lockedFlag = if (entry.restoreToken.wasUserLockedImportance) "1" else "0"
                "${entry.packageName}|${entry.uid}|$encodedChannel|${entry.restoreToken.originalImportance}|$lockedFlag"
            }
            prefs.edit { putStringSet(KEY_MINIMIZED_CHANNELS, serialized) }
        }
    }

    private object ShizukuPromotionBackend : LiveUpdatePromotionBackend {
        @Volatile
        private var lastError: String? = null

        override fun lastErrorMessage(): String? = lastError

        fun clearError() {
            lastError = null
        }

        private fun unwrap(error: Throwable): Throwable =
            if (error is java.lang.reflect.InvocationTargetException && error.cause != null) error.cause!! else error

        override fun isReady(): Boolean = runCatching {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        override fun revokePromotion(packageName: String, uid: Int): PromotedRestoreToken? {
            if (Build.VERSION.SDK_INT < 36 || !isReady()) return null
            var nmError: String? = null
            // Prefer NotificationManagerService.setCanBePromoted(pkg, uid, false, fromUser=true) so
            // Android updates the App Notifications -> Live Updates toggle and demotes the status-bar chip in-place.
            val viaNotificationManager = runCatching {
                val nm = ShizukuPromotedApi.notificationService()
                    ?: throw IllegalStateException("INotificationManager unavailable")
                val canPromote = ShizukuPromotedApi.canBePromoted(nm, packageName, uid)
                if (canPromote == false) {
                    Log.d(TAG, "$packageName already has Live Updates disabled; skipping revoke")
                    return null
                }
                if (!ShizukuPromotedApi.setCanBePromoted(nm, packageName, uid, promote = false)) {
                    throw NoSuchMethodException("setCanBePromoted not found on INotificationManager")
                }
                val after = ShizukuPromotedApi.canBePromoted(nm, packageName, uid)
                if (after == true) {
                    throw IllegalStateException("setCanBePromoted did not disable promotion for $packageName")
                }
                Log.i(TAG, "Revoked Live Updates via INotificationManager for $packageName (uid=$uid)")
                lastError = null
                PromotedRestoreToken.NotificationManagerAllow
            }.onFailure { err ->
                val root = unwrap(err)
                nmError = "${root.javaClass.simpleName}: ${root.message ?: "failed"}"
                Log.w(TAG, "INotificationManager.setCanBePromoted failed for $packageName, trying AppOps", root)
            }.getOrNull()

            if (viaNotificationManager != null) return viaNotificationManager

            return runCatching {
                val opCode = ShizukuPromotedApi.promotedNotificationsOpCode()
                    ?: throw IllegalStateException("POST_PROMOTED_NOTIFICATIONS app-op unavailable")
                val appOps = ShizukuPromotedApi.appOpsService()
                    ?: throw IllegalStateException("IAppOpsService unavailable")
                val currentMode = ShizukuPromotedApi.checkOperation(appOps, opCode, uid, packageName)
                    ?: AppOpsManager.MODE_DEFAULT
                if (currentMode == AppOpsManager.MODE_IGNORED || currentMode == AppOpsManager.MODE_ERRORED) {
                    return@runCatching null
                }
                val applied = ShizukuPromotedApi.setAppOpMode(appOps, opCode, uid, packageName, AppOpsManager.MODE_IGNORED)
                if (applied) {
                    Log.i(TAG, "Revoked Live Updates via AppOps for $packageName (uid=$uid, prev=$currentMode)")
                    lastError = null
                    PromotedRestoreToken.AppOpMode(currentMode)
                } else {
                    null
                }
            }.onFailure { err ->
                val root = unwrap(err)
                lastError = nmError ?: "${root.javaClass.simpleName}: ${root.message ?: "failed"}"
                Log.w(TAG, "Failed to revoke promoted notifications for $packageName", root)
            }.getOrNull()
        }

        override fun restorePromotion(
            packageName: String,
            uid: Int,
            token: PromotedRestoreToken,
        ): Boolean {
            if (Build.VERSION.SDK_INT < 36 || !isReady()) return false
            return when (token) {
                PromotedRestoreToken.NotificationManagerAllow -> runCatching {
                    val nm = ShizukuPromotedApi.notificationService() ?: return@runCatching false
                    val ok = ShizukuPromotedApi.setCanBePromoted(nm, packageName, uid, promote = true)
                    if (ok) {
                        Log.i(TAG, "Restored Live Updates via INotificationManager for $packageName (uid=$uid)")
                        lastError = null
                    }
                    ok
                }.onFailure { err ->
                    val root = unwrap(err)
                    lastError = "${root.javaClass.simpleName}: ${root.message ?: "restore failed"}"
                    Log.w(TAG, "Failed to restore promoted notifications via NM for $packageName", root)
                }.getOrDefault(false)

                is PromotedRestoreToken.AppOpMode -> runCatching {
                    val opCode = ShizukuPromotedApi.promotedNotificationsOpCode() ?: return@runCatching false
                    val appOps = ShizukuPromotedApi.appOpsService() ?: return@runCatching false
                    val ok = ShizukuPromotedApi.setAppOpMode(appOps, opCode, uid, packageName, token.mode)
                    if (ok) {
                        Log.i(TAG, "Restored Live Updates via AppOps for $packageName (uid=$uid, mode=${token.mode})")
                        lastError = null
                    }
                    ok
                }.onFailure { err ->
                    val root = unwrap(err)
                    lastError = "${root.javaClass.simpleName}: ${root.message ?: "restore failed"}"
                    Log.w(TAG, "Failed to restore promoted notifications via AppOps for $packageName", root)
                }.getOrDefault(false)
            }
        }

        override fun minimizeChannel(
            packageName: String,
            uid: Int,
            channelId: String,
        ): MinimizedChannelRestoreToken? {
            if (!isReady() || channelId.isBlank()) return null
            return runCatching {
                val nm = ShizukuPromotedApi.notificationService()
                    ?: throw IllegalStateException("INotificationManager unavailable")
                val channel = runCatching {
                    ShizukuPromotedApi.getNotificationChannel(nm, packageName, uid, channelId)
                }.getOrNull() ?: cachedChannels[packageName to channelId]
                    ?: return@runCatching null
                val originalImportance = channel.importance
                if (originalImportance == NotificationManager.IMPORTANCE_NONE ||
                    originalImportance == NotificationManager.IMPORTANCE_MIN
                ) {
                    return@runCatching null
                }
                val wasLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    channel.hasUserSetImportance()
                } else false
                channel.importance = NotificationManager.IMPORTANCE_MIN
                ShizukuPromotedApi.lockImportanceField(channel)
                if (!ShizukuPromotedApi.updateNotificationChannel(nm, packageName, uid, channel)) {
                    throw NoSuchMethodException("updateNotificationChannelForPackage not found")
                }
                cachedChannels[packageName to channelId] = channel
                Log.i(TAG, "Temporarily minimized channel $channelId for $packageName (orig=$originalImportance)")
                MinimizedChannelRestoreToken(
                    originalImportance = originalImportance,
                    wasUserLockedImportance = wasLocked,
                )
            }.onFailure { err ->
                val root = unwrap(err)
                lastError = "${root.javaClass.simpleName}: ${root.message ?: "minimize failed"}"
                Log.w(TAG, "Failed to minimize channel $channelId for $packageName", root)
            }.getOrNull()
        }

        override fun restoreChannel(
            packageName: String,
            uid: Int,
            channelId: String,
            token: MinimizedChannelRestoreToken,
        ): Boolean {
            if (!isReady() || channelId.isBlank()) return false
            return runCatching {
                val nm = ShizukuPromotedApi.notificationService() ?: return@runCatching false
                val channel = runCatching {
                    ShizukuPromotedApi.getNotificationChannel(nm, packageName, uid, channelId)
                }.getOrNull() ?: cachedChannels[packageName to channelId]
                    ?: return@runCatching true
                channel.importance = token.originalImportance
                if (!token.wasUserLockedImportance) {
                    ShizukuPromotedApi.unlockImportanceField(channel)
                }
                val ok = ShizukuPromotedApi.updateNotificationChannel(nm, packageName, uid, channel)
                if (ok) {
                    cachedChannels.remove(packageName to channelId)
                    Log.i(TAG, "Restored channel $channelId for $packageName (importance=${token.originalImportance})")
                }
                ok
            }.onFailure { err ->
                val root = unwrap(err)
                lastError = "${root.javaClass.simpleName}: ${root.message ?: "channel restore failed"}"
                Log.w(TAG, "Failed to restore channel $channelId for $packageName", root)
            }.getOrDefault(false)
        }
    }

    private object ShizukuPromotedApi {
        private val notificationStubAsInterface: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("android.app.INotificationManager\$Stub"),
                    "asInterface",
                    IBinder::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val canBePromotedMethod: Method? by lazy {
            runCatching {
                val methods = HiddenApiBypass.getDeclaredMethods(Class.forName("android.app.INotificationManager"))
                    .filterIsInstance<Method>()
                val preferredNames = listOf("appCanBePromoted", "canBePromoted", "canPostPromotedNotifications")
                preferredNames.firstNotNullOfOrNull { name ->
                    methods.firstOrNull {
                        it.name == name &&
                            it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java &&
                            it.parameterTypes[1] == Int::class.javaPrimitiveType
                    }
                }?.apply { isAccessible = true }
            }.getOrNull()
        }

        private val setCanBePromotedMethod: Method? by lazy {
            runCatching {
                val methods = HiddenApiBypass.getDeclaredMethods(Class.forName("android.app.INotificationManager"))
                    .filterIsInstance<Method>()
                val preferredNames = listOf("setCanBePromoted", "setCanPostPromotedNotifications")
                preferredNames.firstNotNullOfOrNull { name ->
                    methods
                        .filter {
                            it.name == name &&
                                it.parameterCount in 3..4 &&
                                it.parameterTypes[0] == String::class.java &&
                                it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                                it.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                                (it.parameterCount == 3 || it.parameterTypes[3] == Boolean::class.javaPrimitiveType)
                        }
                        .maxByOrNull { it.parameterCount }
                }?.apply { isAccessible = true }
            }.getOrNull()
        }

        private val getChannelForPackageMethod: Method? by lazy {
            runCatching {
                val methods = HiddenApiBypass.getDeclaredMethods(Class.forName("android.app.INotificationManager"))
                    .filterIsInstance<Method>()
                methods
                    .filter {
                        it.name == "getNotificationChannelForPackage" &&
                            it.parameterCount in 4..5 &&
                            it.parameterTypes[0] == String::class.java &&
                            it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                            it.parameterTypes[2] == String::class.java
                    }
                    .maxByOrNull { it.parameterCount }
                    ?.apply { isAccessible = true }
            }.getOrNull()
        }

        private val updateChannelForPackageMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("android.app.INotificationManager"),
                    "updateNotificationChannelForPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    NotificationChannel::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val lockChannelFieldsMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    NotificationChannel::class.java,
                    "lockFields",
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val unlockChannelFieldsMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    NotificationChannel::class.java,
                    "unlockFields",
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val appOpsStubAsInterface: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("com.android.internal.app.IAppOpsService\$Stub"),
                    "asInterface",
                    IBinder::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val appOpsCheckOperationMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("com.android.internal.app.IAppOpsService"),
                    "checkOperation",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val appOpsSetModeMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("com.android.internal.app.IAppOpsService"),
                    "setMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val appOpsSetUidModeMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    Class.forName("com.android.internal.app.IAppOpsService"),
                    "setUidMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val strOpToOpMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    AppOpsManager::class.java,
                    "strOpToOp",
                    String::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        private val permissionToOpCodeMethod: Method? by lazy {
            runCatching {
                HiddenApiBypass.getDeclaredMethod(
                    AppOpsManager::class.java,
                    "permissionToOpCode",
                    String::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

        fun notificationService(): Any? {
            val binder = SystemServiceHelper.getSystemService(Context.NOTIFICATION_SERVICE) ?: return null
            return notificationStubAsInterface?.invoke(null, ShizukuBinderWrapper(binder))
        }

        fun appOpsService(): Any? {
            val binder = SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE) ?: return null
            return appOpsStubAsInterface?.invoke(null, ShizukuBinderWrapper(binder))
        }

        fun canBePromoted(service: Any, packageName: String, uid: Int): Boolean? =
            runCatching { canBePromotedMethod?.invoke(service, packageName, uid) as? Boolean }.getOrNull()

        fun setCanBePromoted(service: Any, packageName: String, uid: Int, promote: Boolean): Boolean {
            val method = setCanBePromotedMethod ?: return false
            if (method.parameterCount == 4) {
                // Pass fromUser = true to match Android Settings (NotificationBackend / setCanPostPromotedNotifications)
                // so user-visible Live Updates toggles and active promoted notifications update immediately.
                method.invoke(service, packageName, uid, promote, true)
            } else {
                method.invoke(service, packageName, uid, promote)
            }
            return true
        }

        fun getNotificationChannel(
            service: Any,
            packageName: String,
            uid: Int,
            channelId: String,
        ): NotificationChannel? {
            val method = getChannelForPackageMethod ?: return null
            val result = if (method.parameterCount == 5) {
                method.invoke(service, packageName, uid, channelId, null, false)
            } else {
                method.invoke(service, packageName, uid, channelId, false)
            }
            return result as? NotificationChannel
        }

        fun updateNotificationChannel(
            service: Any,
            packageName: String,
            uid: Int,
            channel: NotificationChannel,
        ): Boolean {
            val method = updateChannelForPackageMethod ?: return false
            method.invoke(service, packageName, uid, channel)
            return true
        }

        fun lockImportanceField(channel: NotificationChannel) {
            runCatching { lockChannelFieldsMethod?.invoke(channel, USER_LOCKED_IMPORTANCE) }
        }

        fun unlockImportanceField(channel: NotificationChannel) {
            runCatching { unlockChannelFieldsMethod?.invoke(channel, USER_LOCKED_IMPORTANCE) }
        }

        fun promotedNotificationsOpCode(): Int? {
            val direct = runCatching {
                permissionToOpCodeMethod?.invoke(null, PERMISSION_POST_PROMOTED_NOTIFICATIONS) as? Int
            }.getOrNull()?.takeIf { it >= 0 }
            if (direct != null) return direct

            val opStr = AppOpsManager.permissionToOp(PERMISSION_POST_PROMOTED_NOTIFICATIONS)
                ?: "android:post_promoted_notifications"
            return runCatching {
                strOpToOpMethod?.invoke(null, opStr) as? Int
            }.getOrNull()?.takeIf { it >= 0 }
        }

        fun checkOperation(service: Any, opCode: Int, uid: Int, packageName: String): Int? =
            appOpsCheckOperationMethod?.invoke(service, opCode, uid, packageName) as? Int

        fun setAppOpMode(service: Any, opCode: Int, uid: Int, packageName: String, mode: Int): Boolean {
            var applied = false
            appOpsSetUidModeMethod?.let { method ->
                runCatching {
                    method.invoke(service, opCode, uid, mode)
                    applied = true
                }
            }
            appOpsSetModeMethod?.let { method ->
                runCatching {
                    method.invoke(service, opCode, uid, packageName, mode)
                    applied = true
                }
            }
            return applied
        }
    }
}
