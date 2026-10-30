package com.tektomic.dexvoice

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceControllerTest {

    private class FakeCapture : AudioCapture {
        var recording = false
        var startError: RecorderError? = null
        var fileToReturn: File? = null
        var starts = 0
        override val isRecording get() = recording
        override fun start(onAmplitude: (Float) -> Unit, onAutoStopped: (File?) -> Unit): RecorderError? {
            starts++
            if (startError != null) return startError
            recording = true; return null
        }
        override fun stop(): File? { recording = false; return fileToReturn }
        override fun cancel() { recording = false }
    }

    private class FakeTranscriber(var result: Result<String>) : Transcriber {
        var calls = 0
        override suspend fun transcribe(wav: File, settings: Settings) = result.also { calls++ }
    }

    private class Env(scope: TestScope, val cap: FakeCapture, val tr: FakeTranscriber) {
        val delivered = mutableListOf<String>()
        var now = 0L
        val ctl = VoiceController(
            cap, { tr }, { Settings(FakePrefs()) }, OutputSink { delivered.add(it) },
            CoroutineScope(StandardTestDispatcher(scope.testScheduler)),
            StandardTestDispatcher(scope.testScheduler), { now },
        )
    }

    private fun wav(): File = File.createTempFile("dvtest", ".wav").apply { writeBytes(ByteArray(10)) }

    private fun TestScope.env(text: Result<String> = Result.success("hello world ")) =
        Env(this, FakeCapture().apply { fileToReturn = wav() }, FakeTranscriber(text))

    @Test fun toggleCycleDeliversText() = runTest {
        val e = env()
        e.ctl.toggle()
        assertEquals(VoiceState.Recording, e.ctl.state.value)
        e.ctl.toggle()
        assertEquals(VoiceState.Transcribing, e.ctl.state.value)
        runCurrent()
        assertEquals(VoiceState.Idle, e.ctl.state.value)
        assertEquals(listOf("hello world "), e.delivered)
    }

    @Test fun tooShortRecordingShowsError() = runTest {
        val e = env()
        e.cap.fileToReturn = null
        e.ctl.toggle(); e.ctl.toggle()
        assertTrue(e.ctl.state.value is VoiceState.Error)
        assertEquals(0, e.tr.calls)
        assertTrue(e.delivered.isEmpty())
    }

    @Test fun transcriptionErrorShowsMessageThenClears() = runTest {
        val e = env(Result.failure(TranscriptionException("Bad API key.")))
        e.ctl.toggle(); e.ctl.toggle(); runCurrent()
        assertEquals(VoiceState.Error("Bad API key."), e.ctl.state.value)
        assertTrue(e.delivered.isEmpty())
        advanceTimeBy(VoiceController.ERROR_TTL_MS + 1)
        assertEquals(VoiceState.Idle, e.ctl.state.value)
    }

    @Test fun doubleToggleWhileTranscribingIsIgnored() = runTest {
        val e = env()
        e.ctl.toggle(); e.ctl.toggle()
        e.ctl.toggle() // ignored: still transcribing
        e.ctl.toggle() // ignored
        assertEquals(VoiceState.Transcribing, e.ctl.state.value)
        runCurrent()
        assertEquals(1, e.cap.starts)
        assertEquals(1, e.tr.calls)
        assertEquals(1, e.delivered.size)
    }

    @Test fun micStartFailureShowsError() = runTest {
        val e = env()
        e.cap.startError = RecorderError.PermissionMissing
        e.ctl.toggle()
        assertEquals(VoiceState.Error(RecorderError.PermissionMissing.userMessage), e.ctl.state.value)
    }

    @Test fun staleRecordingRecovers() = runTest {
        val e = env()
        e.ctl.toggle()
        e.cap.recording = false // recorder died behind our back
        e.ctl.toggle() // recovers to idle then starts again
        assertEquals(VoiceState.Recording, e.ctl.state.value)
        assertEquals(2, e.cap.starts)
    }

    @Test fun blankResultIsAnError() = runTest {
        val e = env(Result.success("   "))
        e.ctl.toggle(); e.ctl.toggle(); runCurrent()
        assertTrue(e.ctl.state.value is VoiceState.Error)
        assertTrue(e.delivered.isEmpty())
    }

    @Test fun cancelReturnsToIdle() = runTest {
        val e = env()
        e.ctl.toggle(); e.ctl.cancel(); runCurrent()
        assertEquals(VoiceState.Idle, e.ctl.state.value)
        assertTrue(e.delivered.isEmpty())
    }
}
