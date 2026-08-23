package de.fgna.llmbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkDataTest {
    @Test
    fun `default suite contains twenty prompts across core categories`() {
        val prompts = BenchmarkSuite.defaultPrompts
        assertEquals(20, prompts.size)
        assertTrue(prompts.map { it.category }.toSet().containsAll(setOf("Weltwissen", "Deutsch", "Reasoning", "TaskOS", "Beratung")))
    }

    @Test
    fun `csv export escapes quotes and includes timing fields`() {
        val result = BenchmarkResult(
            model = "demo",
            prompt = BenchmarkPrompt("x", "Weltwissen", "Sag \"Hallo\""),
            ttftMs = 120,
            totalMs = 1000,
            outputTokens = 20,
            tokensPerSecond = 22.7,
            tokenCountSource = TokenCountSource.ESTIMATED,
            output = "Antwort, mit Komma",
            error = null,
        )
        val csv = BenchmarkCsv.export(listOf(result))
        assertTrue(csv.contains("ttft_ms,total_ms"))
        assertTrue(csv.contains("\"Sag \"\"Hallo\"\"\""))
        assertTrue(csv.contains("\"Antwort, mit Komma\""))
    }
}
