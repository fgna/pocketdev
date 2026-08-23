package de.fgna.llmbench

import kotlin.math.ceil

internal object BenchmarkMath {
    fun ttftMs(startNs: Long, firstTokenNs: Long): Long =
        ((firstTokenNs - startNs).coerceAtLeast(0L) / 1_000_000L)

    fun totalMs(startNs: Long, finishedNs: Long): Long =
        ((finishedNs - startNs).coerceAtLeast(0L) / 1_000_000L)

    fun tokensPerSecond(outputTokens: Int, firstTokenNs: Long, finishedNs: Long): Double {
        if (outputTokens <= 0 || finishedNs <= firstTokenNs) return 0.0
        val seconds = (finishedNs - firstTokenNs) / 1_000_000_000.0
        return outputTokens / seconds
    }

    /**
     * Conservative tokenizer-independent fallback. Exact token counts require runtime usage metadata.
     * Roughly four UTF-8-ish text characters per token is sufficient for relative phone benchmarks.
     */
    fun estimateTokens(text: String): Int {
        val normalized = text.trim()
        if (normalized.isEmpty()) return 0
        return ceil(normalized.length / 4.0).toInt().coerceAtLeast(1)
    }
}
