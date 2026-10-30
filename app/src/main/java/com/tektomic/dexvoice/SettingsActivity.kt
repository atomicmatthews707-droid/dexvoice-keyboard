package com.tektomic.dexvoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/** Simple vertical settings form. Never shows the stored API key. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var secrets: SecretStore

    private lateinit var providerSpinner: Spinner
    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var language: EditText
    private lateinit var apiKey: EditText
    private lateinit var keyStatus: TextView
    private lateinit var showBtn: Button
    private lateinit var hint: EditText
    private lateinit var micResult: TextView
    private var keyVisible = false
    private var recorder: WavRecorder? = null

    private val providers = listOf(
        Settings.PROVIDER_OPENAI_COMPAT to "OpenAI-compatible (Groq, OpenAI)",
        Settings.PROVIDER_WISPR to "Wispr Flow API",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        secrets = SecretStore.create(this)
        setContentView(buildUi())
        loadValues()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    override fun onDestroy() {
        recorder?.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 14f
            setPadding(0, dp(14), 0, dp(2))
        }.also { col.addView(it) }
        fun edit(type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
            inputType = type; setSingleLine(); }.also { col.addView(it, matchWrap()) }

        col.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 24f })

        label("Transcription provider")
        providerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, providers.map { it.second })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    refreshKeyStatus(providers[pos].first)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        col.addView(providerSpinner, matchWrap())

        label("API base URL")
        baseUrl = edit(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        label("Model")
        model = edit()
        label("Language code (blank = auto-detect, e.g. en)")
        language = edit()

        label("API key")
        keyStatus = TextView(this).also { col.addView(it) }
        apiKey = edit(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            hint = "Paste a new key to replace the saved one"
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        showBtn = Button(this).apply {
            text = "Show"
            setOnClickListener {
                keyVisible = !keyVisible
                val sel = apiKey.selectionStart
                apiKey.inputType = InputType.TYPE_CLASS_TEXT or
                    if (keyVisible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                text = if (keyVisible) "Hide" else "Show"
                if (sel >= 0) apiKey.setSelection(sel)
            }
        }
        col.addView(showBtn, ViewGroup.LayoutParams.WRAP_CONTENT.let { LinearLayout.LayoutParams(it, it) })

        label("Vocabulary hint (names, jargon; sent as the Whisper prompt)")
        hint = edit().apply { setSingleLine(false); minLines = 2 }

        col.addView(toggle("Add a trailing space after dictated text", settings.trailingSpace) { settings.trailingSpace = it })
        col.addView(toggle("Always show the on-screen keyboard (even with a physical keyboard)", settings.alwaysShowKeyboard) { settings.alwaysShowKeyboard = it })
        col.addView(toggle("Show floating mic bubble", settings.bubbleEnabled) { settings.bubbleEnabled = it })

        label("Microphone")
        col.addView(Button(this).apply {
            text = "Test microphone (2 s)"
            setOnClickListener { testMic() }
        })
        micResult = TextView(this).also { col.addView(it) }

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

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun toggle(text: String, initial: Boolean, onChange: (Boolean) -> Unit) =
        Switch(this).apply {
            this.text = text
            isChecked = initial
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }

    private fun loadValues() {
        providerSpinner.setSelection(providers.indexOfFirst { it.first == settings.provider }.coerceAtLeast(0))
        baseUrl.setText(settings.apiBaseUrl)
        model.setText(settings.model)
        language.setText(settings.language)
        hint.setText(settings.vocabularyHint)
        refreshKeyStatus(settings.provider)
    }

    private fun selectedProvider() = providers[providerSpinner.selectedItemPosition.coerceAtLeast(0)].first

    private fun refreshKeyStatus(provider: String) {
        keyStatus.text = if (secrets.hasKeyFor(provider)) "Key saved" else "No key saved yet"
        apiKey.setText("")
    }

    private fun save() {
        if (!::providerSpinner.isInitialized) return
        val provider = selectedProvider()
        settings.provider = provider
        settings.apiBaseUrl = baseUrl.text.toString()
        settings.model = model.text.toString()
        settings.language = language.text.toString()
        settings.vocabularyHint = hint.text.toString()
        val newKey = apiKey.text.toString()
        if (newKey.isNotBlank()) {
            secrets.setKeyFor(provider, newKey)
            apiKey.setText("")
            keyStatus.text = "Key saved"
        }
    }

    private fun testMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            micResult.text = "Allow the microphone, then tap the test again."
            return
        }
        if (recorder?.isRecording == true) return
        val rec = WavRecorder(
            this, settings,
            onError = { micResult.text = it.userMessage },
        )
        recorder = rec
        val err = rec.start()
        if (err != null) { micResult.text = err.userMessage; return }
        micResult.text = "Recording... speak now"
        micResult.postDelayed({
            val f: File? = rec.stop()
            val peak = rec.peakAmplitude
            f?.delete()
            micResult.text = if (f == null) "No audio captured." else
                "Peak level: ${(peak * 100).toInt()}% " + when {
                    peak < 0.02f -> "(silent: check the microphone)"
                    peak < 0.1f -> "(quiet, but working)"
                    else -> "(good)"
                }
        }, 2000)
    }

    companion object { private const val REQ_MIC = 1 }
}
