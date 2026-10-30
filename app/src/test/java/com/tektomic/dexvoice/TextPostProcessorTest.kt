package com.tektomic.dexvoice

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {
    @Test fun hallucinationsDropped() {
        for (s in listOf("Thank you.", "Thanks for watching!", "you", ".", "Bye.", "  ", "...", ""))
            assertEquals("'$s'", "", TextPostProcessor.process(s, true))
        assertEquals("", TextPostProcessor.process(null, true))
    }
    @Test fun whitespaceCollapsedAndTrailingSpace() {
        assertEquals("hello big world ", TextPostProcessor.process("  hello \n big   world ", true))
        assertEquals("hello big world", TextPostProcessor.process("  hello \n big   world ", false))
    }
    @Test fun realSentenceContainingThankYouKept() {
        assertEquals("Thank you for the help", TextPostProcessor.process("Thank you for the help", false))
    }
}
