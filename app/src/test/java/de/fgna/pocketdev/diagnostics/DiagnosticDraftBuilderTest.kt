package de.fgna.pocketdev.diagnostics

import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.ssh.CommandUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticDraftBuilderTest {
    private val project = ProjectConfig(
        name = "Example",
        remotePath = "/tmp/example",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
    )

    @Test
    fun buildsDraftFromFailedCommand() {
        val draft = DiagnosticDraftBuilder.build(
            project,
            CommandUiState(
                command = "./gradlew test",
                stderr = "tests failed",
                exitCode = 1,
            ),
        )

        assertTrue(draft.title.contains("exit 1"))
        assertTrue(draft.body.contains("./gradlew test"))
        assertTrue(draft.body.contains("tests failed"))
    }

    @Test
    fun redactsObviousSecretAssignments() {
        val draft = DiagnosticDraftBuilder.build(
            project,
            CommandUiState(
                command = "tool --token=abc123 password='hunter2'",
                stderr = "API_KEY=supersecret",
                exitCode = 7,
            ),
        )

        assertFalse(draft.body.contains("abc123"))
        assertFalse(draft.body.contains("hunter2"))
        assertFalse(draft.body.contains("supersecret"))
        assertTrue(draft.body.contains("<redacted>"))
    }

    @Test
    fun truncatesVeryLargeOutput() {
        val draft = DiagnosticDraftBuilder.build(
            project,
            CommandUiState(
                command = "false",
                stdout = "x".repeat(7000),
                exitCode = 1,
            ),
        )

        assertTrue(draft.body.contains("output truncated"))
        assertTrue(draft.body.length < 7000)
    }
}
