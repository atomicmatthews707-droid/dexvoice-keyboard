package com.tektomic.dexvoice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Records 16 kHz mono PCM16 to a WAV file in cacheDir/recordings on a background thread.
 * Callbacks are delivered on the main thread.
 */
class WavRecorder(
    context: Context,
    private val settings: Settings,
    private val onAmplitude: ((Float) -> Unit)? = null,
    private val onError: ((RecorderError) -> Unit)? = null,
    private val onAutoStopped: ((File?) -> Unit)? = null,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var stopFlag = false
    @Volatile private var autoStopped = false
    @Volatile private var result: File? = null
    @Volatile var peakAmplitude: Float = 0f
        private set

    val isRecording: Boolean get() = thread?.isAlive == true && !stopFlag

    /** Starts recording. Returns a [RecorderError] if it could not start, else null. */
    @Synchronized
    fun start(): RecorderError? {
        if (isRecording) return RecorderError.AlreadyRecording
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return fail(RecorderError.PermissionMissing)
        }
        cleanOldRecordings()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return fail(RecorderError.InitFailed)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 2) // >= 0.25 s
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: SecurityException) {
            return fail(RecorderError.PermissionMissing)
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord ctor failed: ${e.javaClass.simpleName}")
            return fail(RecorderError.InitFailed)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return fail(RecorderError.InitFailed)
        }
        val dir = File(app.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.wav")
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return fail(RecorderError.MicBusy)
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return fail(RecorderError.MicBusy)
        }
        requestFocus()
        recorder = rec
        stopFlag = false
        autoStopped = false
        result = null
        peakAmplitude = 0f
        val maxBytes = settings.maxRecordSeconds.toLong() * SAMPLE_RATE * 2
        thread = Thread({ pump(rec, file, bufSize, maxBytes) }, "dexvoice-recorder").also { it.start() }
        return null
    }

    /** Stops and returns the WAV file, or null if too short / failed. Safe to call when idle. */
    fun stop(): File? {
        val t = thread ?: return result
        stopFlag = true
        try { t.join(3000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        thread = null
        return result
    }

    /** Stops and deletes whatever was recorded. */
    fun cancel() {
        stop()?.delete()
        result = null
    }

    private fun pump(rec: AudioRecord, file: File, bufSize: Int, maxBytes: Long) {
        var written = 0L
        var ok = true
        val buf = ShortArray(bufSize / 2)
        val bytes = ByteArray(buf.size * 2)
        var lastAmpPost = 0L
        try {
            FileOutputStream(file).use { out ->
                WavWriter.writePlaceholder(out)
                while (!stopFlag) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) { ok = false; postError(RecorderError.Io("read error $n")); break }
                    if (n == 0) continue
                    var peak = 0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        bytes[2 * i] = (s and 0xFF).toByte()
                        bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    out.write(bytes, 0, n * 2)
                    written += n * 2
                    val level = (peak / 32768f).coerceIn(0f, 1f)
                    if (level > peakAmplitude) peakAmplitude = level
                    val now = System.currentTimeMillis()
                    if (now - lastAmpPost > 60) {
                        lastAmpPost = now
                        onAmplitude?.let { cb -> main.post { cb(level) } }
                    }
                    if (written >= maxBytes) { autoStopped = true; break }
                }
            }
        } catch (e: Exception) {
            ok = false
            postError(RecorderError.Io(e.javaClass.simpleName))
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            recorder = null
            abandonFocus()
        }
        val minBytes = (MIN_SECONDS * SAMPLE_RATE * 2).toInt()
        if (!ok) {
            file.delete()
            result = null
        } else if (written < minBytes) {
            file.delete()
            result = null
        } else {
            try {
                WavWriter.patchHeader(file, written.toInt())
                result = file
            } catch (e: Exception) {
                file.delete(); result = null
                postError(RecorderError.Io(e.javaClass.simpleName))
            }
        }
        if (autoStopped) {
            stopFlag = true
            val f = result
            main.post { onAutoStopped?.invoke(f) }
        }
    }

    private fun fail(e: RecorderError): RecorderError {
        onError?.let { cb -> main.post { cb(e) } }
        return e
    }

    private fun postError(e: RecorderError) { fail(e) }

    private fun cleanOldRecordings() {
        try {
            val cutoff = System.currentTimeMillis() - ONE_DAY_MS
            File(app.cacheDir, "recordings").listFiles()?.forEach {
                if (it.isFile && it.lastModified() < cutoff) it.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanup failed: ${e.javaClass.simpleName}")
        }
    }

    private fun requestFocus() {
        try {
            val am = audioManager ?: return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            am.requestAudioFocus(req)
            focusRequest = req
        } catch (e: Exception) {
            Log.w(TAG, "audio focus failed: ${e.javaClass.simpleName}")
        }
    }

    private fun abandonFocus() {
        try {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        focusRequest = null
    }

    companion object {
        private const val TAG = "WavRecorder"
        const val SAMPLE_RATE = 16000
        const val MIN_SECONDS = 0.4
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}
