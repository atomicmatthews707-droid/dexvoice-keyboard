package com.tektomic.dexvoice

import android.view.KeyEvent

/** Pure hotkey logic (no Android calls) so it can be unit tested. */
object HotkeyMatcher {
    const val MODIFIER_MASK =
        KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON

    /** Default shortcut: Ctrl+Shift+Space. */
    const val DEFAULT_KEY = KeyEvent.KEYCODE_SPACE
    const val DEFAULT_META = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON

    private val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
        KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK,
        KeyEvent.KEYCODE_FUNCTION,
    )

    fun isModifierKey(keyCode: Int) = keyCode in MODIFIER_KEYS

    /** Keep only Ctrl/Shift/Alt/Meta bits (drops left/right and lock-state bits). */
    fun normalize(metaState: Int) = metaState and MODIFIER_MASK

    /** True when the pressed key plus modifiers is exactly the wanted combo. */
    fun matches(keyCode: Int, metaState: Int, wantKey: Int, wantMeta: Int): Boolean {
        if (wantKey == 0 || isModifierKey(keyCode)) return false
        return keyCode == wantKey && normalize(metaState) == normalize(wantMeta)
    }

    /** Combos that are used by Android/Samsung/common apps; warn but still allow. */
    fun conflictWarning(keyCode: Int, metaState: Int): String? {
        val m = normalize(metaState)
        val ctrl = m and KeyEvent.META_CTRL_ON != 0
        val alt = m and KeyEvent.META_ALT_ON != 0
        val meta = m and KeyEvent.META_META_ON != 0
        return when {
            m == 0 && keyCode !in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
                "Without Ctrl, Alt or Win this key would stop working for normal typing. Add a modifier."
            ctrl && !alt && !meta && m == KeyEvent.META_CTRL_ON && keyCode in COMMON_CTRL_KEYS ->
                "Ctrl+that key is a common app shortcut (copy, paste, etc.). Pick another."
            alt && keyCode == KeyEvent.KEYCODE_TAB -> "Alt+Tab switches apps. Pick another."
            alt && keyCode == KeyEvent.KEYCODE_F4 -> "Alt+F4 closes windows. Pick another."
            meta && m == KeyEvent.META_META_ON -> "Win/Meta shortcuts are used by Samsung DeX. Pick another if it does not work."
            ctrl && alt && keyCode == KeyEvent.KEYCODE_DEL -> "Ctrl+Alt+Delete is reserved. Pick another."
            else -> null
        }
    }

    private val COMMON_CTRL_KEYS = setOf(
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_X,
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_F,
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_P,
    )

    fun describe(keyCode: Int, metaState: Int): String {
        val m = normalize(metaState)
        val parts = mutableListOf<String>()
        if (m and KeyEvent.META_CTRL_ON != 0) parts += "Ctrl"
        if (m and KeyEvent.META_ALT_ON != 0) parts += "Alt"
        if (m and KeyEvent.META_SHIFT_ON != 0) parts += "Shift"
        if (m and KeyEvent.META_META_ON != 0) parts += "Win"
        parts += KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").lowercase().replaceFirstChar { it.uppercase() }
        return parts.joinToString("+")
    }
}
