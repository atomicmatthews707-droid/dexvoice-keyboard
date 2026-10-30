package com.tektomic.dexvoice

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Live setup checklist. Every row re-checks itself in onResume. */
class OnboardingActivity : AppCompatActivity() {

    private class Row(
        val title: String,
        val detail: String,
        val optional: Boolean,
        val check: () -> Boolean,
        val fix: () -> Unit,
        val fixLabel: String = "Fix",
    ) {
        lateinit var mark: TextView
        lateinit var button: Button
    }

    private lateinit var settings: Settings
    private lateinit var secrets: SecretStore
    private val rows = mutableListOf<Row>()
    private lateinit var testBox: EditText
    private lateinit var talk: Button
    private lateinit var testStatus: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        secrets = SecretStore.create(this)
        buildRows()
        setContentView(buildUi())
        scope.launch { VoiceController.get(this@OnboardingActivity).state.collect { renderTest(it) } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ImeOutputSink.testTarget = WeakReference(testBox)
    }

    override fun onPause() {
        if (ImeOutputSink.testTarget?.get() === testBox) ImeOutputSink.testTarget = null
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun buildRows() {
        rows += Row("1. Microphone", "DeX Voice needs the microphone to hear you.", false,
            { granted(Manifest.permission.RECORD_AUDIO) },
            { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }, "Allow")
        rows += Row("2. Turn on the DeX Voice keyboard", "Switch \"DeX Voice\" on in the keyboard list.", false,
            { keyboardEnabled(this) },
            { open(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) })
        rows += Row("3. Choose DeX Voice as your keyboard", "Pick \"DeX Voice\" in the popup. Do this again any time you switch back to another keyboard.", false,
            { keyboardSelected(this) },
            { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }, "Choose")
        rows += Row("4. Save your API key", "Get a free key at console.groq.com and paste it in Settings.", false,
            { secrets.hasKeyFor(settings.provider) },
            { open(Intent(this, SettingsActivity::class.java)) }, "Settings")
        rows += Row("5. Floating bubble (optional)", "Lets the bubble float over other apps, including the DeX monitor.", true,
            { AndroidSettings.canDrawOverlays(this) },
            { open(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        rows += Row("6. Shortcut service (optional)", "Switch on \"DeX Voice\" under accessibility. It only listens for your dictation shortcut. It cannot read the screen or anything you type.", true,
            { accessibilityEnabled(this) },
            { open(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) })
        rows += Row("7. Notifications (optional)", "Shows the Start/Stop notification while the bubble is on.", true,
            { android.os.Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS) },
            { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF) }, "Allow")
        rows += Row("8. Keep running in the background (optional)", "Stops Android from closing DeX Voice to save battery.", true,
            { (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) },
            {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }, "Allow")
        rows += Row("9. Voice input provider (optional)", "Lists DeX Voice next to Google under voice input. Samsung's own keyboard may ignore this; use DeX Voice as the keyboard.", true,
            { voiceProviderSelected(this) },
            {
                try { startActivity(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS)) }
                catch (_: Exception) { open(Intent(AndroidSettings.ACTION_SETTINGS)) }
            }, "Open")
    }

    private fun open(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            try { startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        col.addView(TextView(this).apply { text = "DeX Voice setup"; textSize = 26f; setTextColor(Color.WHITE) })
        col.addView(TextView(this).apply {
            text = "Go down the list. Each item turns into a green check when it is done. Items marked optional can wait."
            textSize = 15f; setPadding(0, dp(6), 0, dp(10))
        })
        for (r in rows) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            r.mark = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER; minWidth = dp(40) }
            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(TextView(this).apply { this.text = r.title; textSize = 17f; setTextColor(Color.WHITE) })
            textCol.addView(TextView(this).apply { this.text = r.detail; textSize = 14f })
            r.button = Button(this).apply {
                text = r.fixLabel; isAllCaps = false; minHeight = dp(56); minimumHeight = dp(56)
                setOnClickListener { r.fix() }
            }
            line.addView(r.mark)
            line.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(r.button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(line)
        }

        col.addView(TextView(this).apply {
            text = "Test it"; textSize = 20f; setTextColor(Color.WHITE); setPadding(0, dp(20), 0, dp(4))
        })
        col.addView(TextView(this).apply {
            text = "Tap in the box, press Talk, say something, press the button again. Your words should appear."
            textSize = 14f
        })
        testBox = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3; gravity = Gravity.TOP
            hint = "Dictated text appears here"
        }
        col.addView(testBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        talk = Button(this).apply {
            text = "Talk"; textSize = 22f; isAllCaps = false
            setOnClickListener {
                if (!granted(Manifest.permission.RECORD_AUDIO)) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
                } else {
                    testBox.requestFocus()
                    VoiceController.get(this@OnboardingActivity).toggle()
                }
            }
        }
        col.addView(talk, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)))
        testStatus = TextView(this).apply { textSize = 15f; setPadding(0, dp(6), 0, 0) }
        col.addView(testStatus)

        col.addView(Button(this).apply {
            text = "Open settings"; isAllCaps = false; minHeight = dp(56)
            setOnClickListener { startActivity(Intent(this@OnboardingActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(col)
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        }
    }

    private fun refresh() {
        for (r in rows) {
            val ok = try { r.check() } catch (_: Exception) { false }
            r.mark.text = if (ok) "✓" else if (r.optional) "○" else "!"
            r.mark.setTextColor(if (ok) Color.parseColor("#66BB6A") else if (r.optional) Color.GRAY else Color.parseColor("#FF8A65"))
            r.button.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }

    private fun renderTest(s: VoiceState) {
        when (s) {
            is VoiceState.Idle -> { talk.text = "Talk"; testStatus.text = "" }
            is VoiceState.Recording -> { talk.text = "Listening… tap to stop"; testStatus.text = "Listening…" }
            is VoiceState.Transcribing -> { talk.text = "Transcribing…"; testStatus.text = "Transcribing…" }
            is VoiceState.Error -> { talk.text = "Talk"; testStatus.text = s.message }
        }
    }

    companion object {
        private const val REQ_MIC = 1
        private const val REQ_NOTIF = 2

        fun keyboardEnabled(c: Context): Boolean =
            (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.any { it.packageName == c.packageName }

        fun keyboardSelected(c: Context): Boolean =
            AndroidSettings.Secure.getString(c.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
                ?.startsWith(c.packageName + "/") == true

        fun accessibilityEnabled(c: Context): Boolean =
            AndroidSettings.Secure.getString(c.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(c.packageName + "/") == true

        fun voiceProviderSelected(c: Context): Boolean =
            AndroidSettings.Secure.getString(c.contentResolver, "voice_recognition_service")
                ?.startsWith(c.packageName + "/") == true

        /** Required items: microphone, keyboard enabled and selected, API key. */
        fun requiredDone(c: Context): Boolean {
            val s = Settings(c)
            return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                keyboardEnabled(c) && keyboardSelected(c) && SecretStore.create(c).hasKeyFor(s.provider)
        }
    }
}
