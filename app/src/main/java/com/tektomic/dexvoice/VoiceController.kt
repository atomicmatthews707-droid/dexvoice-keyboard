package com.tektomic.dexvoice

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Microphone abstraction so the controller can be unit tested with a fake. */
interface AudioCapture {
    val isRecording: Boolean
    /** Returns an error if recording could not start, else null. */
    fun start(onAmplitude: (Float) -> Unit, onAutoStopped: (File?) -> Unit): RecorderError?
    /** Stops and returns the WAV, or null when too short or failed. */
    fun stop(): File?
    fun cancel()
}

/** Where transcribed text goes. Always called on the main thread. */
fun interface OutputSink {
    fun deliver(text: String)
}

sealed class VoiceState {
    object Idle : VoiceState()
    object Recording : VoiceState()
    object Transcribing : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * Process-wide dictation state machine. Call all methods on the main thread.
 * [scope] should run on the main dispatcher; transcription runs on [io].
 */
class VoiceController(
    private val capture: AudioCapture,
    private val transcriber: () -> Transcriber,
    private val settings: () -> Settings,
    private val sink: OutputSink,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private var job: Job? = null
    private var transcribingSince = 0L

    /** Tap behaviour: start, stop, or ignore while busy. */
    fun toggle() {
        recoverIfStale()
        when (_state.value) {
            is VoiceState.Idle, is VoiceState.Error -> start()
            is VoiceState.Recording -> stopAndTranscribe()
            is VoiceState.Transcribing -> Unit // double tap guard
        }
    }

    fun start() {
        recoverIfStale()
        val s = _state.value
        if (s is VoiceState.Recording || s is VoiceState.Transcribing) return
        _amplitude.value = 0f
        val err = try {
            capture.start({ _amplitude.value = it }, { f -> onAutoStopped(f) })
        } catch (e: Exception) {
            RecorderError.InitFailed
        }
        if (err != null) fail(err.userMessage) else _state.value = VoiceState.Recording
    }

    fun stopAndTranscribe() {
        if (_state.value !is VoiceState.Recording) return
        val file = try { capture.stop() } catch (e: Exception) { null }
        _amplitude.value = 0f
        transcribe(file)
    }

    /** Abort whatever is happening and go back to idle. */
    fun cancel() {
        job?.cancel(); job = null
        try { capture.cancel() } catch (_: Exception) {}
        _amplitude.value = 0f
        _state.value = VoiceState.Idle
    }

    fun clearError() {
        if (_state.value is VoiceState.Error) _state.value = VoiceState.Idle
    }

    private fun onAutoStopped(f: File?) {
        if (_state.value is VoiceState.Recording) transcribe(f)
    }

    private fun transcribe(file: File?) {
        if (file == null) { fail(RecorderError.TooShort.userMessage); return }
        _state.value = VoiceState.Transcribing
        transcribingSince = clock()
        job?.cancel()
        val my = scope.launch {
            val result = try {
                withContext(io) { transcriber().transcribe(file, settings()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(TranscriptionException("Transcription failed unexpectedly."))
            } finally {
                file.delete()
            }
            if (_state.value !is VoiceState.Transcribing) return@launch
            result.onSuccess { text ->
                if (text.isBlank()) fail("No speech detected. Try again.")
                else {
                    _state.value = VoiceState.Idle
                    try { sink.deliver(text) } catch (e: Exception) { fail("Could not insert the text.") }
                }
            }.onFailure { e ->
                fail((e as? TranscriptionException)?.userMessage ?: "Transcription failed.")
            }
        }
        job = my
    }

    private fun fail(message: String) {
        _state.value = VoiceState.Error(message)
        val err = _state.value
        scope.launch {
            delay(ERROR_TTL_MS)
            if (_state.value === err) _state.value = VoiceState.Idle
        }
    }

    private fun recoverIfStale() {
        when (_state.value) {
            is VoiceState.Recording -> if (!capture.isRecording) { _amplitude.value = 0f; _state.value = VoiceState.Idle }
            is VoiceState.Transcribing -> if (clock() - transcribingSince > STALE_TRANSCRIBE_MS) cancel()
            else -> Unit
        }
    }

    companion object {
        const val ERROR_TTL_MS = 5000L
        const val STALE_TRANSCRIBE_MS = 90_000L

        @Volatile private var instance: VoiceController? = null

        fun get(context: android.content.Context): VoiceController =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(app: android.content.Context): VoiceController {
            val settings = Settings(app)
            val secrets = SecretStore.create(app)
            return VoiceController(
                capture = WavAudioCapture(app, settings),
                transcriber = { TranscriberFactory.forSettings(settings, secrets) },
                settings = { settings },
                sink = ImeOutputSink(app),
                scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate),
                io = kotlinx.coroutines.Dispatchers.IO,
            )
        }
    }
}

/** [AudioCapture] backed by [WavRecorder] (which takes its callbacks in the constructor). */
class WavAudioCapture(private val context: android.content.Context, private val settings: Settings) : AudioCapture {
    private var rec: WavRecorder? = null
    override val isRecording: Boolean get() = rec?.isRecording == true

    override fun start(onAmplitude: (Float) -> Unit, onAutoStopped: (File?) -> Unit): RecorderError? {
        val r = WavRecorder(context, settings, onAmplitude = onAmplitude, onAutoStopped = onAutoStopped)
        val err = r.start()
        if (err == null) rec = r
        return err
    }

    override fun stop(): File? = rec?.stop().also { rec = null }
    override fun cancel() { rec?.cancel(); rec = null }
}
