package de.fgna.llmbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiStreamParserTest {
    @Test
    fun `extracts content delta`() {
        val line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hallo\"}}]}"
        assertEquals("Hallo", OpenAiStreamParser.delta(line))
    }

    @Test
    fun `ignores done marker and empty frames`() {
        assertNull(OpenAiStreamParser.delta("data: [DONE]"))
        assertNull(OpenAiStreamParser.delta(": keep-alive"))
    }

    @Test
    fun `extracts usage token count when provided`() {
        val line = "data: {\"choices\":[],\"usage\":{\"completion_tokens\":42}}"
        assertEquals(42, OpenAiStreamParser.completionTokens(line))
    }
}
