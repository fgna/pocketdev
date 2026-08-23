package de.fgna.llmbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkMathTest {
    @Test
    fun `ttft is measured from request start to first delta`() {
        assertEquals(420L, BenchmarkMath.ttftMs(startNs = 1_000_000_000L, firstTokenNs = 1_420_000_000L))
    }

    @Test
    fun `generation rate excludes time before first token`() {
        val rate = BenchmarkMath.tokensPerSecond(
            outputTokens = 40,
            firstTokenNs = 2_000_000_000L,
            finishedNs = 6_000_000_000L,
        )
        assertEquals(10.0, rate, 0.001)
    }

    @Test
    fun `token estimate is nonzero for normal text`() {
        assertTrue(BenchmarkMath.estimateTokens("Das ist eine kurze Testantwort mit mehreren Wörtern.") > 0)
    }

    @Test
    fun `empty output has zero estimated tokens`() {
        assertEquals(0, BenchmarkMath.estimateTokens(""))
    }
}
