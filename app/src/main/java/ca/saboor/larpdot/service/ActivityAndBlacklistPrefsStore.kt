package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ca.saboor.larpdot.notification.NotificationActivitySettings
import ca.saboor.larpdot.notification.NotificationActivityState
import ca.saboor.larpdot.ui.overlay.DEFAULT_ISLAND_PRIORITY_ORDER
import ca.saboor.larpdot.ui.overlay.IslandPriorityItem
import ca.saboor.larpdot.ui.overlay.normalizeIslandPriorityOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ActivityAndBlacklistPrefsStore {
    private const val KEY_NOTIFICATION_ACTIVITIES_ENABLED = "notification_activities_enabled"
    private const val KEY_NOTIFICATION_LIVE_UPDATES_ENABLED = "notification_live_updates_enabled"
    private const val KEY_NOTIFICATION_PROGRESS_ENABLED = "notification_progress_enabled"
    private const val KEY_NOTIFICATION_NAVIGATION_ENABLED = "notification_navigation_enabled"
    private const val KEY_NOTIFICATION_CALLS_ENABLED = "notification_calls_enabled"
    private const val KEY_NOTIFICATION_TIMERS_ENABLED = "notification_timers_enabled"
    private const val KEY_NOTIFICATION_BATTERY_CHARGING_ENABLED = "notification_battery_charging_enabled"
    private const val KEY_NOTIFICATION_BATTERY_LOW_ENABLED = "notification_battery_low_enabled"
    private const val KEY_NOTIFICATION_SOUND_MODE_ENABLED = "notification_sound_mode_enabled"
    private const val KEY_NOTIFICATION_DND_ENABLED = "notification_dnd_enabled"
    private const val KEY_NOTIFICATION_BLUETOOTH_ENABLED = "notification_bluetooth_enabled"
    private const val KEY_NOTIFICATION_AUDIO_OUTPUT_ENABLED = "notification_audio_output_enabled"
    private const val KEY_NOTIFICATION_CHARGE_LIMIT_ALERT_ENABLED = "notification_charge_limit_alert_enabled"
    private const val KEY_NOTIFICATION_CHARGE_LIMIT_PERCENT = "notification_charge_limit_percent"
    private const val KEY_NOTIFICATION_LOW_BATTERY_THRESHOLD_PERCENT = "notification_low_battery_threshold_percent"
    private const val KEY_AUTO_EXPAND_LIVE_UPDATES = "auto_expand_live_updates"
    private const val KEY_AUTO_EXPAND_LIVE_UPDATES_ON_CHANGE = "auto_expand_live_updates_on_change"
    private const val KEY_AUTO_EXPAND_PROGRESS = "auto_expand_progress"
    private const val KEY_AUTO_EXPAND_NAVIGATION = "auto_expand_navigation"
    private const val KEY_AUTO_EXPAND_CALLS = "auto_expand_calls"
    private const val KEY_AUTO_EXPAND_TIMERS = "auto_expand_timers"
    private const val KEY_AUTO_EXPAND_CHARGING = "auto_expand_charging"
    private const val KEY_AUTO_EXPAND_LOW_BATTERY = "auto_expand_low_battery"
    private const val KEY_AUTO_EXPAND_CHARGE_LIMIT = "auto_expand_charge_limit"
    private const val KEY_AUTO_EXPAND_SOUND_MODE = "auto_expand_sound_mode"
    private const val KEY_AUTO_EXPAND_DND = "auto_expand_dnd"
    private const val KEY_AUTO_EXPAND_BLUETOOTH = "auto_expand_bluetooth"
    private const val KEY_AUTO_EXPAND_AUDIO_OUTPUT = "auto_expand_audio_output"
    private const val KEY_NOTIFICATION_PROGRESS_OUTLINE_ENABLED = "notification_progress_outline_enabled"
    private const val KEY_NOTIFICATION_HIDE_STATUS_BAR_LIVE_UPDATES = "notification_hide_status_bar_live_updates"
    private const val KEY_NOTIFICATION_MINIMIZE_STATUS_BAR_LIVE_UPDATES = "notification_minimize_status_bar_live_updates"
    private const val KEY_NOTIFICATION_USE_APP_COLOR_FOR_ICONS = "notification_use_app_color_for_icons"
    private const val KEY_APP_BLACKLIST_ENABLED = "app_blacklist_enabled"
    private const val KEY_APP_BLACKLIST = "app_blacklist"
    private const val KEY_ISLAND_PRIORITY_ORDER = "island_priority_order"

    private val _notificationActivitySettingsFlow = MutableStateFlow(NotificationActivitySettings())
    val notificationActivitySettingsFlow: StateFlow<NotificationActivitySettings> =
        _notificationActivitySettingsFlow.asStateFlow()
    private var isNotificationActivitySettingsInitialized = false

    private val _appBlacklistEnabledFlow = MutableStateFlow(true)
    val appBlacklistEnabledFlow: StateFlow<Boolean> = _appBlacklistEnabledFlow.asStateFlow()

    private val _blacklistedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedPackagesFlow: StateFlow<Set<String>> = _blacklistedPackagesFlow.asStateFlow()
    private var isAppBlacklistInitialized = false

    private val _islandPriorityOrderFlow = MutableStateFlow(DEFAULT_ISLAND_PRIORITY_ORDER)
    val islandPriorityOrderFlow: StateFlow<List<IslandPriorityItem>> =
        _islandPriorityOrderFlow.asStateFlow()
    private var isIslandPriorityOrderInitialized = false

    fun getNotificationActivitySettings(prefs: SharedPreferences): NotificationActivitySettings {
        if (!isNotificationActivitySettingsInitialized) {
            _notificationActivitySettingsFlow.value = NotificationActivitySettings(
                enabled = prefs.getBoolean(KEY_NOTIFICATION_ACTIVITIES_ENABLED, true),
                liveUpdates = prefs.getBoolean(KEY_NOTIFICATION_LIVE_UPDATES_ENABLED, true),
                progress = prefs.getBoolean(KEY_NOTIFICATION_PROGRESS_ENABLED, true),
                navigation = prefs.getBoolean(KEY_NOTIFICATION_NAVIGATION_ENABLED, true),
                calls = prefs.getBoolean(KEY_NOTIFICATION_CALLS_ENABLED, true),
                timers = prefs.getBoolean(KEY_NOTIFICATION_TIMERS_ENABLED, true),
                batteryCharging = prefs.getBoolean(KEY_NOTIFICATION_BATTERY_CHARGING_ENABLED, true),
                batteryLow = prefs.getBoolean(KEY_NOTIFICATION_BATTERY_LOW_ENABLED, true),
                soundMode = prefs.getBoolean(KEY_NOTIFICATION_SOUND_MODE_ENABLED, true),
                dnd = prefs.getBoolean(KEY_NOTIFICATION_DND_ENABLED, true),
                bluetooth = prefs.getBoolean(KEY_NOTIFICATION_BLUETOOTH_ENABLED, true),
                audioOutput = prefs.getBoolean(KEY_NOTIFICATION_AUDIO_OUTPUT_ENABLED, true),
                chargeLimitAlert = prefs.getBoolean(KEY_NOTIFICATION_CHARGE_LIMIT_ALERT_ENABLED, false),
                chargeLimitPercent = prefs.getInt(KEY_NOTIFICATION_CHARGE_LIMIT_PERCENT, 80).coerceIn(50, 100),
                lowBatteryThresholdPercent = prefs.getInt(KEY_NOTIFICATION_LOW_BATTERY_THRESHOLD_PERCENT, 15).coerceIn(10, 30),
                permanentCharging = prefs.getBoolean("permanent_charging", false),
                permanentLowBattery = prefs.getBoolean("permanent_lowbattery", false),
                permanentSoundMode = prefs.getBoolean("permanent_soundmode", false),
                permanentDnd = prefs.getBoolean("permanent_dnd", false),
                permanentBluetooth = prefs.getBoolean("permanent_bluetooth", false),
                permanentAudioOutput = prefs.getBoolean("permanent_audio_output", false),
                autoExpandLiveUpdates = prefs.getBoolean(KEY_AUTO_EXPAND_LIVE_UPDATES, false),
                autoExpandLiveUpdatesOnChange = prefs.getBoolean(KEY_AUTO_EXPAND_LIVE_UPDATES_ON_CHANGE, false),
                autoExpandProgress = prefs.getBoolean(KEY_AUTO_EXPAND_PROGRESS, false),
                autoExpandNavigation = prefs.getBoolean(KEY_AUTO_EXPAND_NAVIGATION, false),
                autoExpandCalls = prefs.getBoolean(KEY_AUTO_EXPAND_CALLS, false),
                autoExpandTimers = prefs.getBoolean(KEY_AUTO_EXPAND_TIMERS, false),
                autoExpandCharging = prefs.getBoolean(KEY_AUTO_EXPAND_CHARGING, false),
                autoExpandLowBattery = prefs.getBoolean(KEY_AUTO_EXPAND_LOW_BATTERY, false),
                autoExpandChargeLimit = prefs.getBoolean(KEY_AUTO_EXPAND_CHARGE_LIMIT, false),
                autoExpandSoundMode = prefs.getBoolean(KEY_AUTO_EXPAND_SOUND_MODE, false),
                autoExpandDnd = prefs.getBoolean(KEY_AUTO_EXPAND_DND, false),
                autoExpandBluetooth = prefs.getBoolean(KEY_AUTO_EXPAND_BLUETOOTH, false),
                autoExpandAudioOutput = prefs.getBoolean(KEY_AUTO_EXPAND_AUDIO_OUTPUT, false),
                progressOutline = prefs.getBoolean(KEY_NOTIFICATION_PROGRESS_OUTLINE_ENABLED, true),
                hideStatusBarLiveUpdates = prefs.getBoolean(KEY_NOTIFICATION_HIDE_STATUS_BAR_LIVE_UPDATES, false),
                minimizeStatusBarLiveUpdates = prefs.getBoolean(KEY_NOTIFICATION_MINIMIZE_STATUS_BAR_LIVE_UPDATES, false),
                useAppColorForIcons = prefs.getBoolean(KEY_NOTIFICATION_USE_APP_COLOR_FOR_ICONS, false),
            )
            isNotificationActivitySettingsInitialized = true
        }
        return _notificationActivitySettingsFlow.value.also(NotificationActivityState::configure)
    }

    fun setNotificationActivitySettings(
        context: Context,
        prefs: SharedPreferences,
        settings: NotificationActivitySettings,
    ) {
        prefs.edit {
            putBoolean(KEY_NOTIFICATION_ACTIVITIES_ENABLED, settings.enabled)
            putBoolean(KEY_NOTIFICATION_LIVE_UPDATES_ENABLED, settings.liveUpdates)
            putBoolean(KEY_NOTIFICATION_PROGRESS_ENABLED, settings.progress)
            putBoolean(KEY_NOTIFICATION_NAVIGATION_ENABLED, settings.navigation)
            putBoolean(KEY_NOTIFICATION_CALLS_ENABLED, settings.calls)
            putBoolean(KEY_NOTIFICATION_TIMERS_ENABLED, settings.timers)
            putBoolean(KEY_NOTIFICATION_BATTERY_CHARGING_ENABLED, settings.batteryCharging)
            putBoolean(KEY_NOTIFICATION_BATTERY_LOW_ENABLED, settings.batteryLow)
            putBoolean(KEY_NOTIFICATION_SOUND_MODE_ENABLED, settings.soundMode)
            putBoolean(KEY_NOTIFICATION_DND_ENABLED, settings.dnd)
            putBoolean(KEY_NOTIFICATION_BLUETOOTH_ENABLED, settings.bluetooth)
            putBoolean(KEY_NOTIFICATION_AUDIO_OUTPUT_ENABLED, settings.audioOutput)
            putBoolean(KEY_NOTIFICATION_CHARGE_LIMIT_ALERT_ENABLED, settings.chargeLimitAlert)
            putInt(KEY_NOTIFICATION_CHARGE_LIMIT_PERCENT, settings.chargeLimitPercent.coerceIn(50, 100))
            putInt(KEY_NOTIFICATION_LOW_BATTERY_THRESHOLD_PERCENT, settings.lowBatteryThresholdPercent.coerceIn(10, 30))
            putBoolean("permanent_charging", settings.permanentCharging)
            putBoolean("permanent_lowbattery", settings.permanentLowBattery)
            putBoolean("permanent_soundmode", settings.permanentSoundMode)
            putBoolean("permanent_dnd", settings.permanentDnd)
            putBoolean("permanent_bluetooth", settings.permanentBluetooth)
            putBoolean("permanent_audio_output", settings.permanentAudioOutput)
            putBoolean(KEY_AUTO_EXPAND_LIVE_UPDATES, settings.autoExpandLiveUpdates)
            putBoolean(KEY_AUTO_EXPAND_LIVE_UPDATES_ON_CHANGE, settings.autoExpandLiveUpdatesOnChange)
            putBoolean(KEY_AUTO_EXPAND_PROGRESS, settings.autoExpandProgress)
            putBoolean(KEY_AUTO_EXPAND_NAVIGATION, settings.autoExpandNavigation)
            putBoolean(KEY_AUTO_EXPAND_CALLS, settings.autoExpandCalls)
            putBoolean(KEY_AUTO_EXPAND_TIMERS, settings.autoExpandTimers)
            putBoolean(KEY_AUTO_EXPAND_CHARGING, settings.autoExpandCharging)
            putBoolean(KEY_AUTO_EXPAND_LOW_BATTERY, settings.autoExpandLowBattery)
            putBoolean(KEY_AUTO_EXPAND_CHARGE_LIMIT, settings.autoExpandChargeLimit)
            putBoolean(KEY_AUTO_EXPAND_SOUND_MODE, settings.autoExpandSoundMode)
            putBoolean(KEY_AUTO_EXPAND_DND, settings.autoExpandDnd)
            putBoolean(KEY_AUTO_EXPAND_BLUETOOTH, settings.autoExpandBluetooth)
            putBoolean(KEY_AUTO_EXPAND_AUDIO_OUTPUT, settings.autoExpandAudioOutput)
            putBoolean(KEY_NOTIFICATION_PROGRESS_OUTLINE_ENABLED, settings.progressOutline)
            putBoolean(KEY_NOTIFICATION_HIDE_STATUS_BAR_LIVE_UPDATES, settings.hideStatusBarLiveUpdates)
            putBoolean(KEY_NOTIFICATION_MINIMIZE_STATUS_BAR_LIVE_UPDATES, settings.minimizeStatusBarLiveUpdates)
            putBoolean(KEY_NOTIFICATION_USE_APP_COLOR_FOR_ICONS, settings.useAppColorForIcons)
        }
        _notificationActivitySettingsFlow.value = settings
        isNotificationActivitySettingsInitialized = true
        NotificationActivityState.configure(settings)
        SystemEventManager.syncDoNotDisturbState(context)
        if (settings.permanentBluetooth) {
            SystemEventManager.syncConnectedBluetoothDevices(context)
        }
    }

    private fun ensureAppBlacklistInitialized(prefs: SharedPreferences) {
        if (!isAppBlacklistInitialized) {
            _appBlacklistEnabledFlow.value = prefs.getBoolean(KEY_APP_BLACKLIST_ENABLED, true)
            _blacklistedPackagesFlow.value = prefs.getStringSet(KEY_APP_BLACKLIST, emptySet())?.toSet() ?: emptySet()
            isAppBlacklistInitialized = true
        }
    }

    fun isAppBlacklistEnabled(prefs: SharedPreferences): Boolean {
        ensureAppBlacklistInitialized(prefs)
        return _appBlacklistEnabledFlow.value
    }

    fun setAppBlacklistEnabled(prefs: SharedPreferences, enabled: Boolean) {
        ensureAppBlacklistInitialized(prefs)
        prefs.edit { putBoolean(KEY_APP_BLACKLIST_ENABLED, enabled) }
        _appBlacklistEnabledFlow.value = enabled
    }

    fun getBlacklistedPackages(prefs: SharedPreferences): Set<String> {
        ensureAppBlacklistInitialized(prefs)
        return _blacklistedPackagesFlow.value
    }

    fun setBlacklistedPackages(context: Context, prefs: SharedPreferences, packages: Set<String>) {
        ensureAppBlacklistInitialized(prefs)
        val filtered = packages.filter { it.isNotBlank() && it != context.packageName }.toSet()
        prefs.edit { putStringSet(KEY_APP_BLACKLIST, HashSet(filtered)) }
        _blacklistedPackagesFlow.value = filtered
    }

    fun addBlacklistedPackage(context: Context, prefs: SharedPreferences, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        ensureAppBlacklistInitialized(prefs)
        setBlacklistedPackages(context, prefs, _blacklistedPackagesFlow.value + packageName)
    }

    fun removeBlacklistedPackage(context: Context, prefs: SharedPreferences, packageName: String) {
        ensureAppBlacklistInitialized(prefs)
        setBlacklistedPackages(context, prefs, _blacklistedPackagesFlow.value - packageName)
    }

    fun toggleBlacklistedPackage(context: Context, prefs: SharedPreferences, packageName: String) {
        if (packageName.isBlank() || packageName == context.packageName) return
        ensureAppBlacklistInitialized(prefs)
        if (_blacklistedPackagesFlow.value.contains(packageName)) {
            removeBlacklistedPackage(context, prefs, packageName)
        } else {
            addBlacklistedPackage(context, prefs, packageName)
        }
    }

    fun clearBlacklistedPackages(context: Context, prefs: SharedPreferences) {
        setBlacklistedPackages(context, prefs, emptySet())
    }

    fun getIslandPriorityOrder(prefs: SharedPreferences): List<IslandPriorityItem> {
        if (!isIslandPriorityOrderInitialized) {
            val raw = prefs.getString(KEY_ISLAND_PRIORITY_ORDER, null)
            val parsed = raw?.split(",")
                ?.mapNotNull(IslandPriorityItem::fromName)
                .orEmpty()
            _islandPriorityOrderFlow.value = normalizeIslandPriorityOrder(parsed)
            isIslandPriorityOrderInitialized = true
        }
        return _islandPriorityOrderFlow.value
    }

    fun setIslandPriorityOrder(prefs: SharedPreferences, order: List<IslandPriorityItem>) {
        val normalized = normalizeIslandPriorityOrder(order)
        prefs.edit { putString(KEY_ISLAND_PRIORITY_ORDER, normalized.joinToString(",") { it.name }) }
        _islandPriorityOrderFlow.value = normalized
        isIslandPriorityOrderInitialized = true
    }
}
