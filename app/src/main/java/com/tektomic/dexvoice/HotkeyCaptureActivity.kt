package com.tektomic.dexvoice

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** "Press your shortcut now": stores the pressed combo in [Settings]. */
class HotkeyCaptureActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var current: TextView
    private lateinit var warning: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply { text = "Press your shortcut now"; textSize = 26f; gravity = Gravity.CENTER })
        current = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setPadding(0, pad, 0, pad) }
        warning = TextView(this).apply { setTextColor(Color.parseColor("#E65100")); gravity = Gravity.CENTER }
        col.addView(current); col.addView(warning)
        col.addView(TextView(this).apply {
            text = "Hold the modifier keys (Ctrl, Alt, Shift, Win) and press one more key."
            gravity = Gravity.CENTER
        })
        col.addView(Button(this).apply {
            text = "Reset to Ctrl+Shift+Space"
            setOnClickListener { settings.setHotkey(0, 0); show() }
        })
        col.addView(Button(this).apply { text = "Done"; setOnClickListener { finish() } })
        setContentView(col)
        show()
    }

    private fun show() {
        current.text = "Current: " + if (settings.hasHotkey)
            HotkeyMatcher.describe(settings.hotkeyKeyCode, settings.hotkeyMetaState)
        else "Ctrl+Shift+Space (default)"
        warning.text = ""
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE && event.metaState == 0) {
            return super.onKeyDown(keyCode, event)
        }
        if (HotkeyMatcher.isModifierKey(keyCode)) return true
        val meta = HotkeyMatcher.normalize(event.metaState)
        settings.setHotkey(keyCode, meta)
        show()
        warning.text = HotkeyMatcher.conflictWarning(keyCode, meta) ?: ""
        return true
    }
}
