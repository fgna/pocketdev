package de.fgna.pocketdev.ssh

/**
 * Conservative detection of visible, unfinished line-input prompts.
 * An arbitrary silent stdin wait cannot be identified from SSH output.
 * Only short, recognizable prompts at the very end of a stream qualify.
 */
internal object InputPromptDetector {
    private val prompt = Regex(
        """(?i)^(?:enter(?:\\s+|$)|please\\s+(?:enter|type|provide)\\s+|name\\b|reply\\b|input\\b|password\\b|passphrase\\b|username\\b|verification\\s+code\\b)[^\\r\\n]{0,96}[:?]\\s?$"""
    )

    fun detect(output: String): String? {
        if (output.isEmpty() || output.last() == '\n' || output.last() == '\r') return null
        val line = output.substringAfterLast('\n').substringAfterLast('\r')
        if (line.length > 120) return null
        return line.takeIf { prompt.matches(it) }?.trim()
    }
}
