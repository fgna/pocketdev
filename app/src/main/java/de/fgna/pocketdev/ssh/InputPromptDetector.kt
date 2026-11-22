package de.fgna.pocketdev.ssh

/**
 * Conservative detection of visible, unfinished line-input prompts.
 * An arbitrary silent stdin wait cannot be identified from SSH output.
 * Only short, recognizable prompts at the very end of a stream qualify.
 */
internal object InputPromptDetector {
    private val prompt = Regex(
        """(?i)^(?:enter(?:\s+|$)|please\s+(?:enter|type|provide)\s+|name\b|reply\b|input\b|password\b|passphrase\b|username\b|verification\s+code\b)[^\r\n]{0,96}[:?]\s?$"""
    )

    // apt prints a confirmation prompt ending in [Y/n] or [y/N], without a trailing colon.
    private val aptConfirmation = Regex(
        """(?i)^do you want to continue\\?\\s*\\[y/n\\]\\s?$"""
    )

    fun detect(output: String): String? {
        if (output.isEmpty() || output.last() == '\n' || output.last() == '\r') return null
        val tail = output.takeLast(121)
        val separator = tail.lastIndexOfAny(charArrayOf('\n', '\r'))
        if (separator < 0 && output.length > tail.length) return null
        val line = tail.substring(separator + 1)
        if (line.length > 120) return null
        return line.takeIf { prompt.matches(it) || aptConfirmation.matches(it) }?.trim()
    }
}
