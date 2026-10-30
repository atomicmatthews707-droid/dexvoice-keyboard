package com.tektomic.dexvoice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lets DeX Voice appear under Settings > Voice input and serve apps that use SpeechRecognizer.
 * Records until the client stops it, silence follows speech, or the time cap is hit.
 * Uses its own recorder, separate from [VoiceController], because results go to the caller's callback.
 */
class DexVoiceRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: WavRecorder? = null
    private var callback: Callback? = null
    private var job: Job? = null
    private var heardSpeech = false
    private var lastLoud = 0L
    private var finishing = false

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        if (listener == null) return
        resetSession()
        callback = listener
        val settings = Settings(this)
        val rec = WavRecorder(
            this, settings,
            onAmplitude = { level -> onLevel(level) },
            onAutoStopped = { f -> if (!finishing) finish(f) },
        )
        val err = rec.start()
        if (err != null) {
            fail(mapError(err)); return
        }
        recorder = rec
        safe { listener.readyForSpeech(Bundle()) }
    }

    override fun onStopListening(listener: Callback?) {
        if (recorder == null || finishing) return
        finish(recorder?.stop())
    }

    override fun onCancel(listener: Callback?) {
        resetSession()
    }

    override fun onDestroy() {
        resetSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun onLevel(level: Float) {
        val now = System.currentTimeMillis()
        if (level > SPEECH_LEVEL) {
            if (!heardSpeech) { heardSpeech = true; safe { callback?.beginningOfSpeech() } }
            lastLoud = now
        } else if (heardSpeech && !finishing && now - lastLoud > SILENCE_MS) {
            finish(recorder?.stop())
        }
    }

    private fun finish(file: File?) {
        if (finishing) return
        finishing = true
        val cb = callback ?: return
        safe { cb.endOfSpeech() }
        if (file == null) { fail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); return }
        val settings = Settings(this)
        val transcriber = TranscriberFactory.forSettings(settings, SecretStore.create(this))
        job = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { transcriber.transcribe(file, settings) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                file.delete()
            }
            result.onSuccess { text ->
                if (text.isBlank()) fail(SpeechRecognizer.ERROR_NO_MATCH)
                else {
                    val b = Bundle().apply {
                        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text.trim()))
                        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
                    }
                    safe { cb.results(b) }
                    clear()
                }
            }.onFailure { fail(SpeechRecognizer.ERROR_NETWORK) }
        }
    }

    private fun fail(code: Int) {
        val cb = callback
        clear()
        safe { cb?.error(code) }
    }

    private fun resetSession() {
        job?.cancel()
        try { recorder?.cancel() } catch (_: Exception) {}
        clear()
    }

    private fun clear() {
        recorder = null; callback = null; job = null
        heardSpeech = false; finishing = false; lastLoud = 0L
    }

    private fun mapError(e: RecorderError): Int = when (e) {
        is RecorderError.PermissionMissing -> SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        is RecorderError.MicBusy -> SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        is RecorderError.TooShort -> SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        else -> SpeechRecognizer.ERROR_AUDIO
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: Exception) { /* client went away */ }
    }

    companion object {
        private const val SPEECH_LEVEL = 0.04f
        private const val SILENCE_MS = 1800L
    }
}
