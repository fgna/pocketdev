package de.fgna.pocketdev

import de.fgna.pocketdev.project.ActionProfileTemplates
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.project.WorkspaceTab
import de.fgna.pocketdev.project.WorkspaceTabSelection
import de.fgna.pocketdev.ssh.CommandUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDevTabStateTest {
    private val project = ProjectConfig(
        name = "LightScenes", id = "lightscenes", remotePath = "/srv/light scenes",
        testCommand = "./gradlew test", buildCommand = "./gradlew assembleDebug",
        actionProfileId = ActionProfileTemplates.android.id,
    )
    private val tabs = listOf(
        WorkspaceTab("tab-first", project.id),
        WorkspaceTab("tab-second", project.id),
    )
    private val sessions = mapOf(
        "tab-first" to ProjectSessionState(
            command = CommandUiState(command = "echo first", stdout = "first output", running = true),
            workingDirectory = project.remotePath,
            gitBranch = "main",
        ),
        "tab-second" to ProjectSessionState(
            command = CommandUiState(command = "echo second", stdout = "second output"),
            workingDirectory = "/srv/light scenes/android",
            gitBranch = "feature",
        ),
    )

    @Test fun selectingDuplicateTabShowsOnlyThatTabsCommandAndDirectory() {
        val first = PocketDevState(
            projects = listOf(project), project = project, sessions = sessions,
            tabSelection = WorkspaceTabSelection(tabs, "tab-first"),
        )
        val second = first.copy(tabSelection = first.tabSelection.select("tab-second"))
        assertEquals("first output", first.command.stdout)
        assertEquals("second output", second.command.stdout)
        assertEquals("echo first", first.command.command)
        assertEquals("echo second", second.command.command)
        assertEquals(project.remotePath, first.currentWorkingDirectory)
        assertEquals("/srv/light scenes/android", second.currentWorkingDirectory)
        assertEquals("main", first.currentGitBranch)
        assertEquals("feature", second.currentGitBranch)
        assertTrue(first.command.running)
        assertFalse(second.command.running)
        assertEquals(project.actionProfileId, second.project?.actionProfileId)
    }

    @Test fun duplicateTabIdsGenerateDistinctSafeCommandMarkers() {
        val first = ProjectCommandBuilder.trackedInDirectory(
            project.remotePath, "pwd", "tab-first",
        )
        val second = ProjectCommandBuilder.trackedInDirectory(
            project.remotePath, "pwd", "tab-second",
        )
        assertTrue(first.contains("__POCKETDEV_CWD__tab-first:"))
        assertTrue(second.contains("__POCKETDEV_CWD__tab-second:"))
        assertFalse(first.contains("__POCKETDEV_CWD__tab-second:"))
        assertTrue(first.contains("cd '/srv/light scenes'"))
        assertTrue(second.contains("cd '/srv/light scenes'"))
    }

    @Test fun closedActiveTabCannotReadAnotherTabsSession() {
        val state = PocketDevState(
            projects = listOf(project), project = project,
            sessions = sessions - "tab-second",
            tabSelection = WorkspaceTabSelection(tabs, "tab-second").close("tab-second"),
        )
        assertEquals("first output", state.command.stdout)
        assertEquals("tab-first", state.activeTabId)
    }
}
