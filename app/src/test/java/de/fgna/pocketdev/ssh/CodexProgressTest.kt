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

    @Test fun duplicateProjectTabsKeepIndependentProgressAndInputState() {
        val first = CommandStateReducer.reduce(CommandUiState(command = "codex exec --json first"), CommandEvent.Connecting)
        val second = CommandStateReducer.reduce(CommandUiState(command = "codex exec --json second"), CommandEvent.Connecting)
        val firstOutput = CommandStateReducer.reduce(
            first,
            CommandEvent.Output(OutputStreamKind.STDOUT, "{\"type\":\"item.started\",\"item\":{\"type\":\"command_execution\"}}\\n".replace("\\n", "\n")),
        )
        assertEquals("Executing command", firstOutput.codexAction)
        assertEquals("Connecting", second.codexAction)
        assertEquals("", second.stdout)
        assertEquals("", second.pendingCodexLine)
    }

    @Test fun elapsedAndIdleTimesUseRealOutputNotAssumedHeartbeats() {
        val connected = CommandStateReducer.reduce(CommandUiState(command = "codex exec --json hello"), CommandEvent.Connecting)
        assertNotNull(connected.startedAtMs)
        assertNull(connected.lastActivityAtMs)
        val withOutput = CommandStateReducer.reduce(connected, CommandEvent.Output(OutputStreamKind.STDOUT, "partial"))
        assertNotNull(withOutput.lastActivityAtMs)
        val finished = CommandStateReducer.reduce(withOutput, CommandEvent.Completed(1))
        assertEquals(withOutput.lastActivityAtMs, finished.lastActivityAtMs)
        assertEquals(1, finished.exitCode)
    }

    @Test fun codexLoggingShellInheritsTheSameSudoPromptDefinition() {
        val shim = SshjCommandExecutor.interactiveSudoPrelude()
        assertTrue(shim.contains("exec 3<&0"))
        assertTrue(shim.contains("sudo() {"))
        assertTrue(shim.contains("sudo -S -p"))
        assertTrue(shim.contains("sudo -n"))
    }

    @Test fun genericCommandsKeepRawStreaming() {
        val state = CommandStateReducer.reduce(CommandUiState(command = "echo hello"), CommandEvent.Output(OutputStreamKind.STDOUT, "hello"))
        assertEquals("hello", state.stdout)
        val bigGeneric = CommandStateReducer.reduce(
            CommandUiState(command = "cat large.log"),
            CommandEvent.Output(OutputStreamKind.STDOUT, "x".repeat(150_000)),
        )
        assertEquals(150_000, bigGeneric.stdout.length)
        assertNull(state.codexAction)
        assertFalse(CodexProgress.enabled("codex exec hello"))
        assertTrue(CodexProgress.enabled("codex exec --json hello"))
    }
}
