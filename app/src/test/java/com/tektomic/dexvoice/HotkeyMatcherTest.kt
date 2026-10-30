package com.tektomic.dexvoice

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyMatcherTest {
    private val ctrlShift = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON

    @Test fun exactComboMatches() {
        assertTrue(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, ctrlShift, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
    }

    @Test fun sidedModifierBitsAreIgnored() {
        val withSides = ctrlShift or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        assertTrue(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, withSides, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
    }

    @Test fun lockStateBitsAreIgnored() {
        val caps = ctrlShift or KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_NUM_LOCK_ON
        assertTrue(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, caps, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
    }

    @Test fun missingOrExtraModifierDoesNotMatch() {
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, ctrlShift or KeyEvent.META_ALT_ON, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_SPACE, 0, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
    }

    @Test fun wrongKeyDoesNotMatch() {
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_ENTER, ctrlShift, HotkeyMatcher.DEFAULT_KEY, HotkeyMatcher.DEFAULT_META))
    }

    @Test fun modifierKeysNeverMatchAndUnsetNeverMatches() {
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_CTRL_LEFT, ctrlShift, KeyEvent.KEYCODE_CTRL_LEFT, ctrlShift))
        assertFalse(HotkeyMatcher.matches(KeyEvent.KEYCODE_A, 0, 0, 0))
        assertTrue(HotkeyMatcher.isModifierKey(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertFalse(HotkeyMatcher.isModifierKey(KeyEvent.KEYCODE_SPACE))
    }

    @Test fun conflictWarnings() {
        assertNull(HotkeyMatcher.conflictWarning(KeyEvent.KEYCODE_SPACE, ctrlShift))
        assertNotNull(HotkeyMatcher.conflictWarning(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON))
        assertNotNull(HotkeyMatcher.conflictWarning(KeyEvent.KEYCODE_TAB, KeyEvent.META_ALT_ON))
        assertNotNull(HotkeyMatcher.conflictWarning(KeyEvent.KEYCODE_A, 0))
        assertNull(HotkeyMatcher.conflictWarning(KeyEvent.KEYCODE_F9, 0))
    }
}
