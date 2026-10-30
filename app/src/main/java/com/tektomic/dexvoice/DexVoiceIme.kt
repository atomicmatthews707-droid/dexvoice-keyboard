package com.tektomic.dexvoice

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Voice-first keyboard. Text from [VoiceController] arrives through [ImeOutputSink]. */
class DexVoiceIme : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var controller: VoiceController
    private lateinit var settings: Settings
    private var micButton: Button? = null
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        controller = VoiceController.get(this)
        instance = WeakReference(this)
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Show the on-screen keyboard even when a hardware keyboard is attached (user setting). */
    override fun onEvaluateInputViewShown(): Boolean {
        if (!::settings.isInitialized) settings = Settings(this)
        return if (settings.alwaysShowKeyboard) true else super.onEvaluateInputViewShown()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        instance = WeakReference(this)
        inputStartListener?.invoke(this)
    }

    override fun onCreateInputView(): View {
        instance = WeakReference(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val statusView = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
            text = LABEL_IDLE
        }
        status = statusView
        root.addView(statusView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val mic = Button(this).apply {
            isAllCaps = false; textSize = 22f; setTextColor(Color.BLACK)
            text = LABEL_IDLE
            setOnClickListener { controller.toggle() }
        }
        micButton = mic
        root.addView(mic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun key(label: String, desc: String, weight: Float, click: () -> Unit) = Button(this).apply {
            text = label; contentDescription = desc; isAllCaps = false; textSize = 20f
            setTextColor(Color.WHITE)
            background = rounded(Color.parseColor("#2B2B2B"))
            setOnClickListener { click() }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, dp(56), weight).apply { setMargins(dp(3), dp(6), dp(3), 0) }) }

        key("🌐", "Switch keyboard", 1f) { switchKeyboard() }
        val back = key("⌫", "Backspace", 1f) { deleteOnce() }
        installRepeat(back)
        key("Space", "Space", 2.5f) { currentInputConnection?.commitText(" ", 1) }
        key("↵", "Enter", 1f) { pressEnter() }
        key("⚙", "DeX Voice settings", 1f) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        root.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        scope.launch {
            controller.state.collect { render(it) }
        }
        return root
    }

    private fun render(s: VoiceState) {
        val (label, color, msg) = when (s) {
            is VoiceState.Idle -> Triple(LABEL_IDLE, "#4FC3F7", LABEL_IDLE)
            is VoiceState.Recording -> Triple("Listening… tap to stop", "#EF5350", "Listening…")
            is VoiceState.Transcribing -> Triple("Transcribing…", "#FFB300", "Transcribing…")
            is VoiceState.Error -> Triple("Tap to try again", "#FF8A65", s.message)
        }
        micButton?.apply { text = label; background = rounded(Color.parseColor(color)) }
        status?.text = msg
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat(); setColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun switchKeyboard() {
        try {
            if (!switchToNextInputMethod(false)) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
            }
        } catch (e: Exception) { /* nothing sensible to do */ }
    }

    private fun deleteOnce() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
    }

    private fun installRepeat(b: Button) {
        val repeat = object : Runnable {
            override fun run() { deleteOnce(); handler.postDelayed(this, 60) }
        }
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; deleteOnce(); handler.postDelayed(repeat, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; handler.removeCallbacks(repeat) }
            }
            true
        }
    }

    private fun pressEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val multiLine = ((info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val noEnterAction = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!multiLine && !noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    companion object {
        private const val LABEL_IDLE = "Tap to talk"
        @Volatile private var instance: WeakReference<DexVoiceIme>? = null

        /** Called from onStartInputView; the bubble uses this to follow the focused display. */
        @Volatile var inputStartListener: ((DexVoiceIme) -> Unit)? = null

        fun active(): DexVoiceIme? = instance?.get()
    }
}
