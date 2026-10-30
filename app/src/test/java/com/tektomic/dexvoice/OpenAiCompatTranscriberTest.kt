package com.tektomic.dexvoice

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class OpenAiCompatTranscriberTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: Settings
    private lateinit var wav: File
    private val key = "gsk_SECRET_KEY_123"

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        settings = Settings(FakePrefs())
        settings.apiBaseUrl = server.url("/openai/v1").toString()
        settings.language = "en"
        settings.vocabularyHint = "Tektomic, DeX"
        wav = File.createTempFile("t", ".wav").also { it.writeBytes(ByteArray(2000) { 1 }); it.deleteOnExit() }
    }
    @After fun tearDown() { server.shutdown() }

    private fun t(k: String = key) = OpenAiCompatTranscriber({ k }, backoffMs = listOf(1L, 1L))
    private fun ok(text: String) = MockResponse().setResponseCode(200).setBody("""{"text":"$text"}""")

    @Test fun success() = runBlocking {
        server.enqueue(ok(" hello world "))
        val r = t().transcribe(wav, settings)
        assertEquals("hello world ", r.getOrThrow())
        val req = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", req.path)
        assertEquals("Bearer $key", req.getHeader("Authorization"))
    }

    @Test fun multipartFieldsPresent() = runBlocking {
        server.enqueue(ok("hi there"))
        t().transcribe(wav, settings)
        val body = server.takeRequest().body.readUtf8()
        for (f in listOf("name=\"file\"", "filename=\"audio.wav\"", "audio/wav", "name=\"model\"",
            "whisper-large-v3-turbo", "name=\"response_format\"", "json", "name=\"language\"",
            "name=\"prompt\"", "Tektomic, DeX"))
            assertTrue("missing $f", body.contains(f))
    }

    @Test fun unauthorizedMapped_noRetry_noKeyLeak() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad $key"}"""))
        val r = t().transcribe(wav, settings)
        val e = r.exceptionOrNull() as TranscriptionException
        assertEquals("API key rejected, check Settings", e.userMessage)
        assertFalse(e.message!!.contains(key))
        assertEquals(1, server.requestCount)
    }

    @Test fun retryOn429ThenSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(ok("worked"))
        assertEquals("worked ", t().transcribe(wav, settings).getOrThrow())
        assertEquals(2, server.requestCount)
    }

    @Test fun serverErrorExhaustsRetries() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(500)) }
        val e = t().transcribe(wav, settings).exceptionOrNull() as TranscriptionException
        assertEquals("Transcription server error, try again", e.userMessage)
        assertEquals(3, server.requestCount) // 1 + 2 retries
    }

    @Test fun badRequestAndTooLargeNotRetried() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(413))
        val e = t().transcribe(wav, settings).exceptionOrNull() as TranscriptionException
        assertEquals("Recording too long", e.userMessage)
        assertEquals(1, server.requestCount)
    }

    @Test fun hallucinationFiltered() = runBlocking {
        server.enqueue(ok("Thank you."))
        assertEquals("", t().transcribe(wav, settings).getOrThrow())
    }

    @Test fun missingKeyAndNoInternet() = runBlocking {
        val e1 = t("").transcribe(wav, settings).exceptionOrNull() as TranscriptionException
        assertTrue(e1.userMessage.contains("API key"))
        server.shutdown()
        val e2 = t().transcribe(wav, settings).exceptionOrNull() as TranscriptionException
        assertEquals("No internet", e2.userMessage)
        assertFalse(e2.message!!.contains(key))
    }

    @Test fun wisprReportsNotConfigured() = runBlocking {
        val e = WisprTranscriber().transcribe(wav, settings).exceptionOrNull() as TranscriptionException
        assertTrue(e.userMessage.contains("not configured"))
    }
}
