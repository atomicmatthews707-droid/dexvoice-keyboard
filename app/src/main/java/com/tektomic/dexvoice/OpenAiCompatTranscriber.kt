package com.tektomic.dexvoice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Multipart POST to `{base}/audio/transcriptions` (Groq, OpenAI, or any compatible server). */
class OpenAiCompatTranscriber(
    private val keyProvider: () -> String,
    private val client: OkHttpClient = defaultClient(),
    private val backoffMs: List<Long> = listOf(1000L, 3000L),
    private val postProcess: Boolean = true
) : Transcriber {

    override suspend fun transcribe(wav: File, settings: Settings): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(run(wav, settings))
            } catch (e: TranscriptionException) {
                Result.failure(e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(TranscriptionException("Transcription failed (${e.javaClass.simpleName})"))
            }
        }

    private suspend fun run(wav: File, settings: Settings): String {
        val key = keyProvider().trim()
        if (key.isEmpty()) throw TranscriptionException("No API key set, open Settings")
        if (!wav.exists() || wav.length() == 0L) throw TranscriptionException("Recording is empty")
        val url = settings.apiBaseUrl.trim().trimEnd('/') + "/audio/transcriptions"
        val httpUrl = url.toHttpUrlOrNull() ?: throw TranscriptionException("API address is invalid, check Settings")

        var attempt = 0
        while (true) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", wav.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", settings.model)
                .addFormDataPart("response_format", "json")
                .apply {
                    if (settings.language.isNotBlank()) addFormDataPart("language", settings.language)
                    if (settings.vocabularyHint.isNotBlank()) addFormDataPart("prompt", settings.vocabularyHint)
                }
                .build()
            val req = Request.Builder().url(httpUrl).header("Authorization", "Bearer $key").post(body).build()

            var retryable = false
            var failure: TranscriptionException
            try {
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val t = try { JSONObject(text).optString("text", "") } catch (e: Exception) {
                            throw TranscriptionException("Unexpected reply from the server")
                        }
                        return if (postProcess) TextPostProcessor.process(t, settings.trailingSpace) else t
                    }
                    retryable = resp.code == 429 || resp.code >= 500
                    failure = TranscriptionException(messageFor(resp.code))
                }
            } catch (e: TranscriptionException) {
                throw e
            } catch (e: IOException) {
                retryable = true
                failure = TranscriptionException(
                    if (e is java.net.SocketTimeoutException) "Server took too long, try again" else "No internet"
                )
            }
            if (!retryable || attempt >= backoffMs.size) throw failure
            delay(backoffMs[attempt])
            attempt++
        }
    }

    companion object {
        fun messageFor(code: Int): String = when (code) {
            400 -> "Server rejected the audio or settings (check model name)"
            401, 403 -> "API key rejected, check Settings"
            404 -> "API address or model not found, check Settings"
            413 -> "Recording too long"
            429 -> "Rate limited, try again in a moment"
            in 500..599 -> "Transcription server error, try again"
            else -> "Transcription failed (HTTP $code)"
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
