package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PtyTerminalSessionTest {
    @Test fun startupChangesToProjectAndExitsWhenMissing() {
        val script = PtyTerminalSession.projectStartupCommand("/home/dev/My Project")
        assertTrue(script.startsWith("cd '/home/dev/My Project' || "))
        assertTrue(script.contains("project directory unavailable"))
        assertTrue(script.contains("exit 1;"))
        assertTrue(script.endsWith("\n"))
    }

    @Test fun startupSafelyQuotesSingleQuotesInProjectPath() {
        val script = PtyTerminalSession.projectStartupCommand("/home/dev/it's-work")
        assertTrue(script.startsWith("cd '/home/dev/it'\\''s-work' || "))
    }

    @Test fun rejectsRelativeAndMultilineProjectPaths() {
        for (directory in listOf("../repo", "repo", "/tmp\nbad", "/tmp\rbad")) {
            assertThrows(IllegalArgumentException::class.java) {
                PtyTerminalSession.projectStartupCommand(directory)
            }
        }
    }
}
