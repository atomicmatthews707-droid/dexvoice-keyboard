package com.tektomic.dexvoice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Sends text to the active keyboard's input connection, or to the clipboard when none is active.
 * Never logs the text (it may be a password field).
 */
class ImeOutputSink(private val app: Context) : OutputSink {
    override fun deliver(text: String) {
        val ime = DexVoiceIme.active()
        val ic = ime?.currentInputConnection
        if (ime != null && ic != null && ime.currentInputEditorInfo != null) {
            if (ic.commitText(text, 1)) return
        }
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DeX Voice", text))
            Toast.makeText(app, "Copied, paste with Ctrl+V", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(app, "Could not insert or copy the text.", Toast.LENGTH_LONG).show()
        }
    }
}
