package com.tektomic.dexvoice

import java.io.File

/**
 * Wispr Flow developer API provider.
 *
 * TODO(HUMAN-ONLY): the official docs (https://api-docs.wisprflow.ai) could not be fetched in the
 * unattended build, and third-party pages disagree on endpoints, so nothing is guessed here.
 * Until someone reads the docs and implements auth/endpoint/audio format exactly, this provider
 * reports "not configured". Use the Groq/OpenAI-compatible provider meanwhile.
 */
class WisprTranscriber(@Suppress("unused") private val keyProvider: () -> String = { "" }) : Transcriber {
    override suspend fun transcribe(wav: File, settings: Settings): Result<String> =
        Result.failure(TranscriptionException("Wispr provider not configured yet, use Groq/OpenAI in Settings"))

    companion object {
        const val HELP_TEXT = "Wispr: not implemented yet (TODO). Choose Groq/OpenAI-compatible."
    }
}
