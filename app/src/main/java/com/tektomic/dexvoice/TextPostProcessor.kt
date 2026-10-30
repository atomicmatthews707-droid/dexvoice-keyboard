package com.tektomic.dexvoice

/** Cleans up raw transcription output before it is typed into a field. */
object TextPostProcessor {
    private val HALLUCINATIONS = setOf(
        "thank you", "thank you.", "thanks for watching", "thanks for watching!", "thanks for watching.",
        "thank you for watching", "thank you for watching.", "thank you for watching!",
        "you", "you.", "bye", "bye.", "bye!", "."
    )

    fun process(raw: String?, trailingSpace: Boolean): String {
        if (raw == null) return ""
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return ""
        val key = cleaned.lowercase()
        if (key in HALLUCINATIONS) return ""
        // lone punctuation / symbols only
        if (cleaned.none { it.isLetterOrDigit() }) return ""
        return if (trailingSpace) "$cleaned " else cleaned
    }
}
