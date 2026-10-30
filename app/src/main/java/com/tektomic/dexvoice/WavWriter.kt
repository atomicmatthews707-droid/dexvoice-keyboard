package com.tektomic.dexvoice

import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal WAV (RIFF, PCM16) header helper. */
object WavWriter {
    const val HEADER_SIZE = 44

    fun header(dataBytes: Int, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val b = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(36 + dataBytes)
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1) // PCM
        b.putShort(channels.toShort())
        b.putInt(sampleRate)
        b.putInt(byteRate)
        b.putShort(blockAlign.toShort())
        b.putShort(bitsPerSample.toShort())
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(dataBytes)
        return b.array()
    }

    /** Writes a placeholder header; call [patchHeader] once the data length is known. */
    fun writePlaceholder(out: OutputStream) = out.write(header(0))

    /** Rewrites the header of [file] for the number of PCM bytes actually written. */
    fun patchHeader(file: File, dataBytes: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header(dataBytes))
        }
    }

    /** Builds a complete WAV of [seconds] of silence (handy for tests and self-checks). */
    fun silence(seconds: Double, sampleRate: Int = 16000): ByteArray {
        val data = (seconds * sampleRate).toInt() * 2
        return header(data, sampleRate) + ByteArray(data)
    }
}
