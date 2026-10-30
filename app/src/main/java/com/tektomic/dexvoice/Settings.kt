package com.tektomic.dexvoice

import android.content.Context
import android.content.SharedPreferences

/** Plain (non-secret) preferences. Secrets live in [SecretStore]. */
class Settings(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    var provider: String
        get() = prefs.getString(K_PROVIDER, PROVIDER_OPENAI_COMPAT) ?: PROVIDER_OPENAI_COMPAT
        set(v) = prefs.edit().putString(K_PROVIDER, v).apply()

    var apiBaseUrl: String
        get() = prefs.getString(K_BASE_URL, DEFAULT_BASE_URL)?.ifBlank { DEFAULT_BASE_URL } ?: DEFAULT_BASE_URL
        set(v) = prefs.edit().putString(K_BASE_URL, v.trim()).apply()

    var model: String
        get() = prefs.getString(K_MODEL, DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString(K_MODEL, v.trim()).apply()

    /** ISO code such as "en"; empty string means auto-detect. */
    var language: String
        get() = prefs.getString(K_LANGUAGE, "") ?: ""
        set(v) = prefs.edit().putString(K_LANGUAGE, v.trim()).apply()

    /** Vocabulary hint passed to Whisper as the `prompt` field. */
    var vocabularyHint: String
        get() = prefs.getString(K_HINT, "") ?: ""
        set(v) = prefs.edit().putString(K_HINT, v).apply()

    var trailingSpace: Boolean
        get() = prefs.getBoolean(K_TRAILING_SPACE, true)
        set(v) = prefs.edit().putBoolean(K_TRAILING_SPACE, v).apply()

    var alwaysShowKeyboard: Boolean
        get() = prefs.getBoolean(K_ALWAYS_SHOW_KB, true)
        set(v) = prefs.edit().putBoolean(K_ALWAYS_SHOW_KB, v).apply()

    /** Hotkey key code; 0 = not set. */
    var hotkeyKeyCode: Int
        get() = prefs.getInt(K_HOTKEY_CODE, 0)
        set(v) = prefs.edit().putInt(K_HOTKEY_CODE, v).apply()

    var hotkeyMetaState: Int
        get() = prefs.getInt(K_HOTKEY_META, 0)
        set(v) = prefs.edit().putInt(K_HOTKEY_META, v).apply()

    fun setHotkey(keyCode: Int, metaState: Int) {
        prefs.edit().putInt(K_HOTKEY_CODE, keyCode).putInt(K_HOTKEY_META, metaState).apply()
    }

    val hasHotkey: Boolean get() = hotkeyKeyCode != 0

    var maxRecordSeconds: Int
        get() = prefs.getInt(K_MAX_SECONDS, DEFAULT_MAX_SECONDS).coerceIn(1, 600)
        set(v) = prefs.edit().putInt(K_MAX_SECONDS, v.coerceIn(1, 600)).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(K_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(K_HAPTICS, v).apply()

    var sounds: Boolean
        get() = prefs.getBoolean(K_SOUNDS, false)
        set(v) = prefs.edit().putBoolean(K_SOUNDS, v).apply()

    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(K_BUBBLE, false)
        set(v) = prefs.edit().putBoolean(K_BUBBLE, v).apply()

    var bubbleOnBoot: Boolean
        get() = prefs.getBoolean(K_BUBBLE_BOOT, false)
        set(v) = prefs.edit().putBoolean(K_BUBBLE_BOOT, v).apply()

    /** Saved bubble position for a display as (x, y) in pixels, or null if never moved. */
    fun bubblePosition(displayId: Int): Pair<Int, Int>? {
        val x = prefs.getInt("bubble_x_$displayId", Int.MIN_VALUE)
        val y = prefs.getInt("bubble_y_$displayId", Int.MIN_VALUE)
        return if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) null else x to y
    }

    fun setBubblePosition(displayId: Int, x: Int, y: Int) {
        prefs.edit().putInt("bubble_x_$displayId", x).putInt("bubble_y_$displayId", y).apply()
    }

    companion object {
        const val PREFS_NAME = "dexvoice_settings"
        const val PROVIDER_OPENAI_COMPAT = "openai_compat"
        const val PROVIDER_WISPR = "wispr"
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_MAX_SECONDS = 120

        const val K_PROVIDER = "provider"
        const val K_BASE_URL = "api_base_url"
        const val K_MODEL = "model"
        const val K_LANGUAGE = "language"
        const val K_HINT = "vocabulary_hint"
        const val K_TRAILING_SPACE = "trailing_space"
        const val K_ALWAYS_SHOW_KB = "always_show_keyboard"
        const val K_HOTKEY_CODE = "hotkey_key_code"
        const val K_HOTKEY_META = "hotkey_meta_state"
        const val K_MAX_SECONDS = "max_record_seconds"
        const val K_HAPTICS = "haptics"
        const val K_SOUNDS = "sounds"
        const val K_BUBBLE = "bubble_enabled"
        const val K_BUBBLE_BOOT = "bubble_on_boot"
    }
}
