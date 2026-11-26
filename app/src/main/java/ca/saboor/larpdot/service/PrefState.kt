package ca.saboor.larpdot.service

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class BooleanPref(
    private val key: String,
    private val default: Boolean,
    private val fallbackKey: String? = null,
) {
    private val _flow = MutableStateFlow(default)
    val flow: StateFlow<Boolean> = _flow.asStateFlow()
    private var initialized = false

    fun reload(prefs: SharedPreferences): Boolean {
        val fallback = if (fallbackKey != null) prefs.getBoolean(fallbackKey, default) else default
        val value = prefs.getBoolean(key, fallback)
        _flow.value = value
        initialized = true
        return value
    }

    fun get(prefs: SharedPreferences): Boolean = if (!initialized) reload(prefs) else _flow.value

    fun set(prefs: SharedPreferences, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
        _flow.value = value
        initialized = true
    }

    fun forceSet(value: Boolean) {
        _flow.value = value
        initialized = true
    }
}

internal class FloatPref(
    private val key: String,
    private val default: Float,
    private val min: Float = -Float.MAX_VALUE,
    private val max: Float = Float.MAX_VALUE,
) {
    private val _flow = MutableStateFlow(default)
    val flow: StateFlow<Float> = _flow.asStateFlow()
    private var initialized = false

    private fun sanitize(raw: Float): Float =
        if (raw.isFinite()) raw.coerceIn(min, max) else default.coerceIn(min, max)

    fun reload(prefs: SharedPreferences): Float {
        val value = sanitize(prefs.getFloat(key, default))
        _flow.value = value
        initialized = true
        return value
    }

    fun get(prefs: SharedPreferences): Float = if (!initialized) reload(prefs) else _flow.value

    fun set(prefs: SharedPreferences, value: Float) {
        val clamped = sanitize(value)
        prefs.edit { putFloat(key, clamped) }
        _flow.value = clamped
        initialized = true
    }
}

internal class IntPref(
    private val key: String,
    private val default: Int,
    private val min: Int = Int.MIN_VALUE,
    private val max: Int = Int.MAX_VALUE,
) {
    private val _flow = MutableStateFlow(default)
    val flow: StateFlow<Int> = _flow.asStateFlow()
    private var initialized = false

    fun reload(prefs: SharedPreferences): Int {
        val value = prefs.getInt(key, default).coerceIn(min, max)
        _flow.value = value
        initialized = true
        return value
    }

    fun get(prefs: SharedPreferences): Int = if (!initialized) reload(prefs) else _flow.value

    fun set(prefs: SharedPreferences, value: Int) {
        val clamped = value.coerceIn(min, max)
        prefs.edit { putInt(key, clamped) }
        _flow.value = clamped
        initialized = true
    }
}

internal class EnumPref<T : Enum<T>>(
    private val key: String,
    private val default: T,
    private val parse: (String?) -> T,
    private val serialize: (T) -> String = { it.name },
    private val fallbackKey: String? = null,
) {
    private val _flow = MutableStateFlow(default)
    val flow: StateFlow<T> = _flow.asStateFlow()
    private var initialized = false

    fun reload(prefs: SharedPreferences): T {
        val fallback = fallbackKey?.let { prefs.getString(it, null) }
        val value = parse(prefs.getString(key, fallback))
        _flow.value = value
        initialized = true
        return value
    }

    fun get(prefs: SharedPreferences): T = if (!initialized) reload(prefs) else _flow.value

    fun set(prefs: SharedPreferences, value: T) {
        prefs.edit { putString(key, serialize(value)) }
        _flow.value = value
        initialized = true
    }
}
