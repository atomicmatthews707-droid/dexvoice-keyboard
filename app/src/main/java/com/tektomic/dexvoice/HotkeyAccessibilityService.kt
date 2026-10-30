package com.tektomic.dexvoice

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Listens ONLY for the dictation shortcut. It does not read the screen, window content
 * or text, and it injects nothing. Every other key passes through untouched.
 */
class HotkeyAccessibilityService : AccessibilityService() {

    private var consumedKeyCode = -1

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val settings = Settings(this)
        val wantKey = if (settings.hasHotkey) settings.hotkeyKeyCode else HotkeyMatcher.DEFAULT_KEY
        val wantMeta = if (settings.hasHotkey) settings.hotkeyMetaState else HotkeyMatcher.DEFAULT_META
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!HotkeyMatcher.matches(event.keyCode, event.metaState, wantKey, wantMeta)) return false
                if (event.repeatCount == 0) {
                    consumedKeyCode = event.keyCode
                    try { VoiceController.get(this).toggle() } catch (_: Exception) {}
                }
                return true // swallow the combo and its key repeats
            }
            KeyEvent.ACTION_UP -> {
                if (event.keyCode == consumedKeyCode) { consumedKeyCode = -1; return true }
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally empty */ }

    override fun onInterrupt() { /* nothing to interrupt */ }
}
