package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginShellCommandTest {
    @Test fun loginShellQuotesMultilineCommandsWithoutExpansion() {
        val command = "echo 'single' \"double\" \$HOME\n" + "printf '%s\\n' \"\$PATH\""
        val wrapped = RemoteShellCommand.build(command, loginShell = true, interactiveSudo = false)
        assertEquals("bash -lc '" + command.replace("'", "'\\''") + "'", wrapped)
    }

    @Test fun disabledLoginShellPreservesOriginalCommand() {
        val command = "echo 'raw' && printf \"%s\" \$HOME\n"
        assertEquals(command, RemoteShellCommand.build(command, loginShell = false, interactiveSudo = false))
    }

    @Test fun loginShellKeepsInteractiveSudoAndOriginalCommand() {
        val command = "sudo id\necho 'done'"
        val wrapped = RemoteShellCommand.build(command, loginShell = true, interactiveSudo = true)
        assertTrue(wrapped.startsWith("bash -lc '"))
        assertTrue(wrapped.contains("exec 3<&0"))
        assertTrue(wrapped.contains("command sudo -S"))
        assertTrue(wrapped.contains("sudo id"))
        assertTrue(wrapped.contains("echo "))
        // Cancellation still targets the SSH command/session by the original command ID.
        assertFalse(wrapped.contains("kill -"))
    }

    @Test fun exit127ProvidesEnvironmentHint() {
        val state = CommandStateReducer.reduce(CommandUiState(stderr = "bash: tool: command not found\n"), CommandEvent.Completed(127))
        assertEquals(127, state.exitCode)
        assertTrue(state.combinedOutput.contains("Login shell"))
        assertTrue(state.combinedOutput.contains("~/.local/bin"))
    }
}
