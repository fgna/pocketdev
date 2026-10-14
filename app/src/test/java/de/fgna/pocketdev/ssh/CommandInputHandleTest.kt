package de.fgna.pocketdev.ssh

import java.io.BufferedWriter
import java.io.StringWriter
import java.io.Writer
import org.junit.Assert.*
import org.junit.Test

class CommandInputHandleTest {
    @Test fun repliesIncludeUnicodeEmptyLinesAndRepeatedInput() {
        val output = StringWriter()
        var submitted = 0
        val handle = CommandInputHandle(output.buffered(), { true }, { submitted++ })
        assertTrue(handle.sendLine("Grüße 👋"))
        assertTrue(handle.sendLine(""))
        assertTrue(handle.sendLine("yes\nnext"))
        assertEquals("Grüße 👋\n\nyes\nnext\n", output.toString())
        assertEquals(3, submitted)
    }

    @Test fun expiredHandleCannotWriteToReplacementOrOtherProject() {
        val oldRun = Any()
        var currentRun: Any? = oldRun
        val oldOutput = StringWriter()
        val otherOutput = StringWriter()
        val oldHandle = CommandInputHandle(oldOutput.buffered(), { currentRun === oldRun }, {})
        val otherHandle = CommandInputHandle(otherOutput.buffered(), { true }, {})
        assertTrue(oldHandle.sendLine("first"))
        currentRun = Any() // Same project now runs a different execution.
        assertFalse(oldHandle.isActive)
        assertFalse(oldHandle.sendLine("secret"))
        assertTrue(otherHandle.sendLine("other project"))
        currentRun = null // Completion or cancellation.
        assertFalse(oldHandle.sendLine("late"))
        assertEquals("first\n", oldOutput.toString())
        assertEquals("other project\n", otherOutput.toString())
    }

    @Test fun failedFlushDoesNotReportSubmission() {
        var submitted = false
        val writer = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) = Unit
            override fun flush() { throw java.io.IOException("closed") }
            override fun close() = Unit
        }
        val handle = CommandInputHandle(BufferedWriter(writer), { true }, { submitted = true })
        assertFalse(handle.sendLine("reply"))
        assertFalse(submitted)
    }

    @Test fun concurrentRepliesAreNotInterleaved() {
        val output = StringWriter()
        val writer = output.buffered()
        val handles = List(20) { CommandInputHandle(writer, { true }, {}) }
        val threads = handles.mapIndexed { index, handle ->
            Thread { check(handle.sendLine("reply-$index")) }.apply { start() }
        }
        threads.forEach { it.join() }
        assertEquals((0 until 20).map { "reply-$it" }.toSet(), output.toString().trimEnd().lines().toSet())
        assertEquals(20, output.toString().lines().dropLast(1).size)
    }
}
