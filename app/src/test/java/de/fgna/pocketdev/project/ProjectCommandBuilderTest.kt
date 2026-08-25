package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCommandBuilderTest {
    private val project = ProjectConfig(
        name = "TaskOS",
        remotePath = "/home/freya/Projects/my task's OS",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
    )

    @Test
    fun projectActionsReturnTheConfiguredCommandWithoutHardCodingCwd() {
        assertEquals("git status --short --branch", ProjectCommandBuilder.command(project, ProjectAction.GIT_STATUS))
        assertEquals("./gradlew test", ProjectCommandBuilder.command(project, ProjectAction.TEST))
        assertEquals("./gradlew assembleDebug", ProjectCommandBuilder.command(project, ProjectAction.BUILD))
    }

    @Test
    fun arbitraryCommandRunsInsideSafelyQuotedDirectory() {
        assertEquals(
            "cd '/home/freya/Projects/my task'\"'\"'s OS' && pwd",
            ProjectCommandBuilder.inProject(project, "pwd"),
        )
        assertEquals(
            "cd '/tmp/a b' && pwd",
            ProjectCommandBuilder.inDirectory("/tmp/a b", "pwd"),
        )
    }

    @Test
    fun trackedCommandReportsFinalPwdWithoutChangingExitCode() {
        val command = ProjectCommandBuilder.trackedInDirectory("/home/freya/Projects/taskos", "cd android", "project-1")
        assertTrue(command.contains("cd '/home/freya/Projects/taskos'"))
        assertTrue(command.contains("__POCKETDEV_CWD__project-1:"))
        assertTrue(command.contains("rc=\$?"))
        assertTrue(command.contains("exit \"\$rc\""))
    }

    @Test
    fun trackedOutputExtractsAndRemovesWorkingDirectoryMarker() {
        val stdout = "hello\n__POCKETDEV_CWD__project-1:/home/freya/Projects/taskos/android\n"
        assertEquals(
            "/home/freya/Projects/taskos/android",
            ProjectCommandBuilder.extractWorkingDirectory(stdout, "project-1"),
        )
        assertEquals("hello", ProjectCommandBuilder.stripWorkingDirectoryMarker(stdout, "project-1"))
    }

    @Test
    fun blankActionCommandIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.command(project.copy(testCommand = "   "), ProjectAction.TEST)
        }
    }

    @Test
    fun relativeWorkingDirectoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.inDirectory("Projects/taskos", "pwd")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.trackedInDirectory("Projects/taskos", "pwd", "project-1")
        }
    }
}
