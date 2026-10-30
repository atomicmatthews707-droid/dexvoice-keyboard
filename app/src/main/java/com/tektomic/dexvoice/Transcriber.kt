package com.tektomic.dexvoice

import java.io.File

/** Turns a recorded WAV file into text. Implementations must never throw; return a failed Result. */
interface Transcriber {
    suspend fun transcribe(wav: File, settings: Settings): Result<String>
}

/** Failure with a short message that is safe to show to the user (never contains the API key). */
class TranscriptionException(val userMessage: String, cause: Throwable? = null) :
    Exception(userMessage, cause)
