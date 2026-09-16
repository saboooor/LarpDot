package ca.saboor.larpdot.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayPreferences {
    private const val PREFS_NAME = "larpdot_overlay_prefs"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"

    private val _isEnabledFlow = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOverlayEnabled(context: Context): Boolean {
        if (!isInitialized) {
            val enabled = getPrefs(context).getBoolean(KEY_OVERLAY_ENABLED, false)
            _isEnabledFlow.value = enabled
            isInitialized = true
        }
        return _isEnabledFlow.value
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _isEnabledFlow.value = enabled
        isInitialized = true
    }
}

