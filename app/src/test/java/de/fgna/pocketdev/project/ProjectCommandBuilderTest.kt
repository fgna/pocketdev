package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectCommandBuilderTest {
    private val project = ProjectConfig(
        name = "TaskOS",
        remotePath = "/home/freya/Projects/my task's OS",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
    )

    @Test
    fun gitStatusRunsInsideSafelyQuotedProjectDirectory() {
        assertEquals(
            "cd '/home/freya/Projects/my task'\"'\"'s OS' && git status --short --branch",
            ProjectCommandBuilder.command(project, ProjectAction.GIT_STATUS),
        )
    }

    @Test
    fun testAndBuildUseConfiguredCommands() {
        assertEquals(
            "cd '/home/freya/Projects/my task'\"'\"'s OS' && ./gradlew test",
            ProjectCommandBuilder.command(project, ProjectAction.TEST),
        )
        assertEquals(
            "cd '/home/freya/Projects/my task'\"'\"'s OS' && ./gradlew assembleDebug",
            ProjectCommandBuilder.command(project, ProjectAction.BUILD),
        )
    }

    @Test
    fun arbitraryCommandRunsInsideProjectDirectory() {
        assertEquals(
            "cd '/home/freya/Projects/my task'\"'\"'s OS' && pwd",
            ProjectCommandBuilder.inProject(project, "pwd"),
        )
    }

    @Test
    fun alreadyScopedCommandIsNotPrefixedTwice() {
        val scoped = ProjectCommandBuilder.command(project, ProjectAction.GIT_STATUS)
        assertEquals(scoped, ProjectCommandBuilder.inProject(project, scoped))
    }

    @Test
    fun blankActionCommandIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.command(project.copy(testCommand = "   "), ProjectAction.TEST)
        }
    }

    @Test
    fun relativeProjectPathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.command(project.copy(remotePath = "Projects/taskos"), ProjectAction.GIT_STATUS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandBuilder.inProject(project.copy(remotePath = "Projects/taskos"), "pwd")
        }
    }
}
