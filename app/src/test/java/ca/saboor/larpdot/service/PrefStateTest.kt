package ca.saboor.larpdot.service

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefStateTest {

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var cleared = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { cleared = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (cleared) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }

    private enum class SampleMode { ALPHA, BETA }

    @Test
    fun booleanPrefReadsPersistsAndUpdatesFlow() {
        val prefs = FakeSharedPreferences()
        val pref = BooleanPref("flag_key", default = false)
        assertFalse(pref.get(prefs))
        pref.set(prefs, true)
        assertTrue(pref.flow.value)
        assertEquals(true, prefs.data["flag_key"])
        pref.set(prefs, false)
        assertFalse(pref.flow.value)
        assertEquals(false, prefs.data["flag_key"])
    }

    @Test
    fun intAndFloatPrefsClampOutOfRangeAndNonFiniteValues() {
        val prefs = FakeSharedPreferences().apply {
            data["int_key"] = 500
            data["float_key"] = Float.NaN
        }
        val intPref = IntPref("int_key", default = 10, min = 0, max = 100)
        assertEquals(100, intPref.get(prefs))
        intPref.set(prefs, -20)
        assertEquals(0, intPref.flow.value)

        val floatPref = FloatPref("float_key", default = 0.5f, min = 0f, max = 1f)
        assertEquals(0.5f, floatPref.get(prefs), 0.0001f)
        floatPref.set(prefs, Float.POSITIVE_INFINITY)
        assertEquals(0.5f, floatPref.flow.value, 0.0001f)
        floatPref.set(prefs, 2.5f)
        assertEquals(1f, floatPref.flow.value, 0.0001f)
    }

    @Test
    fun enumPrefReadsAndPersistsCustomSerializer() {
        val prefs = FakeSharedPreferences().apply { data["mode"] = "beta" }
        val pref = EnumPref(
            key = "mode",
            default = SampleMode.ALPHA,
            parse = { name -> SampleMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SampleMode.ALPHA },
            serialize = { it.name.lowercase() },
        )
        assertEquals(SampleMode.BETA, pref.get(prefs))
        pref.set(prefs, SampleMode.ALPHA)
        assertEquals("alpha", prefs.data["mode"])
        assertEquals(SampleMode.ALPHA, pref.flow.value)
    }
}
