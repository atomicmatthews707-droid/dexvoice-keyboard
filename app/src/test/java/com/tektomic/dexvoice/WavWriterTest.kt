package com.tektomic.dexvoice

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {
    @Test fun headerBytesAreCorrect() {
        val h = WavWriter.header(32000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(h, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 32000, b.getInt(4))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(32000, b.getInt(40))
    }

    @Test fun silenceFileSizes() {
        val wav = WavWriter.silence(0.5)
        assertEquals(44 + 16000, wav.size)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, b.getInt(40))
        assertEquals(wav.size - 8, b.getInt(4))
        assertEquals(0, wav[100].toInt())
    }

    @Test fun placeholderThenPatch() {
        val f = File.createTempFile("wav", ".wav")
        try {
            f.outputStream().use { WavWriter.writePlaceholder(it); it.write(ByteArray(1000)) }
            WavWriter.patchHeader(f, 1000)
            val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1044, f.length().toInt())
            assertEquals(1000, b.getInt(40))
            assertEquals(1036, b.getInt(4))
        } finally { f.delete() }
    }

    @Test fun placeholderIsZeroLength() {
        val out = ByteArrayOutputStream()
        WavWriter.writePlaceholder(out)
        assertEquals(44, out.size())
    }
}
