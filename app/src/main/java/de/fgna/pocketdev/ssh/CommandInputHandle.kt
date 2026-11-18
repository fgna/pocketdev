package de.fgna.pocketdev.ssh

import java.io.BufferedWriter

/** An ephemeral lease on one execution's stdin, never a lookup of a later command. */
class CommandInputHandle internal constructor(
    private val writer: BufferedWriter,
    private val isCurrent: () -> Boolean,
    private val onSent: () -> Unit,
) {
    val isActive: Boolean get() = isCurrent()

    // Call from Dispatchers.IO. The writer lock also serializes sudo and normal replies.
    fun sendLine(text: String): Boolean = synchronized(writer) {
        if (!isCurrent()) return@synchronized false
        runCatching {
            writer.write(text)
            writer.write("\n")
            writer.flush()
            onSent()
        }.isSuccess
    }
}
