package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandStateReducerTest {
    @Test
    fun `streams stdout and stderr independently then completes`() {
        var state = CommandUiState(command = "test")
        state = CommandStateReducer.reduce(state, CommandEvent.Connecting)
        assertTrue(state.running)

        state = CommandStateReducer.reduce(
            state,
            CommandEvent.Output(OutputStreamKind.STDOUT, "hello\n"),
        )
        state = CommandStateReducer.reduce(
            state,
            CommandEvent.Output(OutputStreamKind.STDERR, "warning\n"),
        )
        state = CommandStateReducer.reduce(state, CommandEvent.Completed(0))

        assertFalse(state.running)
        assertEquals(0, state.exitCode)
        assertEquals("hello\n", state.stdout)
        assertEquals("warning\n", state.stderr)
        assertTrue(state.succeeded)
    }

    @Test
    fun `connection failure is distinct from remote non zero exit`() {
        val connectionFailure = CommandStateReducer.reduce(
            CommandUiState(running = true),
            CommandEvent.ConnectionFailed("refused"),
        )
        assertEquals("refused", connectionFailure.connectionError)
        assertEquals(null, connectionFailure.exitCode)

        val remoteFailure = CommandStateReducer.reduce(
            CommandUiState(running = true),
            CommandEvent.Completed(7),
        )
        assertEquals(7, remoteFailure.exitCode)
        assertEquals(null, remoteFailure.connectionError)
    }
}
