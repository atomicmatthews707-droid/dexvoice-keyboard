package com.tektomic.dexvoice

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory SharedPreferences so Settings can be tested without Robolectric. */
class FakePrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(k: String, d: String?) = map[k] as String? ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = map[k] as Int? ?: d
    override fun getLong(k: String, d: Long) = map[k] as Long? ?: d
    override fun getFloat(k: String, d: Float) = map[k] as Float? ?: d
    override fun getBoolean(k: String, d: Boolean) = map[k] as Boolean? ?: d
    override fun contains(k: String) = map.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = Ed()
    inner class Ed : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { removed.add(k) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            removed.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }
}

class SettingsTest {
    private fun newSettings() = Settings(FakePrefs())

    @Test fun defaults() {
        val s = newSettings()
        assertEquals("openai_compat", s.provider)
        assertEquals("https://api.groq.com/openai/v1", s.apiBaseUrl)
        assertEquals("whisper-large-v3-turbo", s.model)
        assertEquals("", s.language)
        assertEquals("", s.vocabularyHint)
        assertTrue(s.trailingSpace)
        assertTrue(s.alwaysShowKeyboard)
        assertFalse(s.bubbleEnabled)
        assertEquals(120, s.maxRecordSeconds)
        assertFalse(s.hasHotkey)
    }

    @Test fun roundTrip() {
        val s = newSettings()
        s.provider = Settings.PROVIDER_WISPR
        s.language = " en "
        s.setHotkey(62, 4096)
        s.trailingSpace = false
        assertEquals("wispr", s.provider)
        assertEquals("en", s.language)
        assertTrue(s.hasHotkey)
        assertEquals(62, s.hotkeyKeyCode)
        assertEquals(4096, s.hotkeyMetaState)
        assertFalse(s.trailingSpace)
    }

    @Test fun blankUrlFallsBackToDefault() {
        val s = newSettings()
        s.apiBaseUrl = "   "
        assertEquals(Settings.DEFAULT_BASE_URL, s.apiBaseUrl)
        s.model = ""
        assertEquals(Settings.DEFAULT_MODEL, s.model)
    }

    @Test fun maxSecondsIsClamped() {
        val s = newSettings()
        s.maxRecordSeconds = 99999
        assertEquals(600, s.maxRecordSeconds)
        s.maxRecordSeconds = 0
        assertEquals(1, s.maxRecordSeconds)
    }

    @Test fun secretStoreKeysPerProvider() {
        val st = SecretStore(FakePrefs())
        assertFalse(st.hasKeyFor("openai_compat"))
        st.setKeyFor("openai_compat", "  k1 ")
        st.setKeyFor("wispr", "k2")
        assertEquals("k1", st.keyFor("openai_compat"))
        assertEquals("k2", st.keyFor("wispr"))
        st.setKeyFor("wispr", "")
        assertFalse(st.hasKeyFor("wispr"))
    }
}
