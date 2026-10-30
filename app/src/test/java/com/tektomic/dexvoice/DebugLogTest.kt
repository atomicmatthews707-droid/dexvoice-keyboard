package com.tektomic.dexvoice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {
    @Test fun redactsKnownAndPatternKeys() {
        val t = DebugLog.redact("key=mysecretvalue99 and gsk_abcdefghijklmnop and Bearer abcdefghij12345", listOf("mysecretvalue99"))
        assertFalse(t.contains("mysecretvalue99"))
        assertFalse(t.contains("gsk_abcdefghijklmnop"))
        assertFalse(t.contains("abcdefghij12345"))
        assertTrue(t.contains("[REDACTED]"))
    }
}
