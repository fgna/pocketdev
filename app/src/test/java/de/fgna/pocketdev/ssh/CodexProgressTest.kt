package de.fgna.pocketdev.ssh

import org.junit.Assert.*
import org.junit.Test

class CodexProgressTest {
    @Test fun parsesKnownEventsAndIgnoresUnknown() {
        assertEquals("Executing command", CodexProgress.parse("""{"type":"item.started","item":{"type":"command_execution"}}""")?.action)
        assertEquals("Files updated", CodexProgress.parse("""{"type":"item.completed","item":{"type":"file_change"}}""")?.action)
        assertNull(CodexProgress.parse("""{"type":"new.future.event"}"""))
        assertNull(CodexProgress.parse("invalid JSON"))
    }

    @Test fun partialAndMalformedLinesRetainReadableState() {
        var state = CommandUiState(command = "codex exec --json hello")
        state = CommandStateReducer.reduce(state, CommandEvent.Connecting)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDOUT, """{"type":"item.started","item":"""))
        assertEquals("Connecting", state.codexAction)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDOUT, """{"type":"command_execution"}}""" + "\n"))
        assertEquals("Executing command", state.codexAction)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDOUT, "bad json\n"))
        assertEquals("Executing command", state.codexAction)
    }

    @Test fun retainsLogPathAndBoundsLargeOutput() {
        var state = CommandStateReducer.reduce(CommandUiState(command = "codex exec --json hello"), CommandEvent.Connecting)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDOUT, "POCKETDEV_JOB_LOG:/tmp/job123\n"))
        assertEquals("/tmp/job123", state.remoteLogPrefix)
        state = CommandStateReducer.reduce(state, CommandEvent.Output(OutputStreamKind.STDERR, "a".repeat(200000)))
        assertEquals(128 * 1024, state.stderr.length)
        state = CommandStateReducer.reduce(state, CommandEvent.Completed(130))
        assertFalse(state.running)
        assertEquals(130, state.exitCode)
    }

    @Test fun genericCommandsKeepRawStreaming() {
        val state = CommandStateReducer.reduce(CommandUiState(command = "echo hello"), CommandEvent.Output(OutputStreamKind.STDOUT, "hello"))
        assertEquals("hello", state.stdout)
        assertNull(state.codexAction)
        assertFalse(CodexProgress.enabled("codex exec hello"))
        assertTrue(CodexProgress.enabled("codex exec --json hello"))
    }
}
