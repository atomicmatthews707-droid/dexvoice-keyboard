package com.tektomic.dexvoice

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small rotating crash/debug log in filesDir/logs. Never stores API keys or dictated text. */
object DebugLog {
    private const val MAX_BYTES = 64 * 1024L
    private const val KEEP_BYTES = 40_000

    fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }
    private fun file(context: Context) = File(dir(context), "dexvoice.log")

    /** Replaces anything that looks like an API key and the saved key itself. */
    fun redact(text: String, secrets: List<String> = emptyList()): String {
        var t = text
        for (s in secrets) if (s.length >= 6) t = t.replace(s, "[REDACTED]")
        t = t.replace(Regex("(gsk_|sk-|sk_|wf_|key-)[A-Za-z0-9_\\-]{8,}"), "[REDACTED]")
        t = t.replace(Regex("(?i)(bearer|authorization:?)\\s+[A-Za-z0-9._\\-]{8,}"), "$1 [REDACTED]")
        return t
    }

    fun log(context: Context, tag: String, message: String, t: Throwable? = null) {
        try {
            val f = file(context)
            if (f.length() > MAX_BYTES) {
                val old = File(f.parentFile, "dexvoice.log.1")
                old.delete(); f.renameTo(old)
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sb = StringBuilder("$stamp [$tag] $message\n")
            if (t != null) sb.append(t.stackTraceToString().take(4000)).append('\n')
            f.appendText(redact(sb.toString()))
        } catch (_: Throwable) { /* logging must never crash the app */ }
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log(app, "CRASH", "Uncaught on ${thread.name}", e)
            previous?.uncaughtException(thread, e)
        }
    }

    /** Last part of the log, redacted, with a short device header. */
    fun read(context: Context, secrets: List<String> = emptyList()): String {
        val header = "DeX Voice debug log\nAndroid ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n"
        val body = try {
            val parts = listOf(File(dir(context), "dexvoice.log.1"), file(context))
                .filter { it.exists() }.joinToString("") { it.readText() }
            if (parts.isBlank()) "(no log entries yet)" else parts.takeLast(KEEP_BYTES)
        } catch (e: Exception) { "(could not read log)" }
        return redact(header + body, secrets)
    }
}
