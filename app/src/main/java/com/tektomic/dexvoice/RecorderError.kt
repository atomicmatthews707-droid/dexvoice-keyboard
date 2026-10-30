package com.tektomic.dexvoice

/** Typed, human-readable recorder failures. */
sealed class RecorderError(val userMessage: String) {
    object PermissionMissing : RecorderError("Microphone permission is missing. Open DeX Voice and allow the microphone.")
    object MicBusy : RecorderError("The microphone is busy. Close other apps that are recording and try again.")
    object InitFailed : RecorderError("Could not start the microphone.")
    object TooShort : RecorderError("Recording was too short.")
    class Io(detail: String) : RecorderError("Could not save the recording: $detail")
    object AlreadyRecording : RecorderError("Already recording.")
}
