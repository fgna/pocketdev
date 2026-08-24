package de.fgna.pocketdev.project

import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectProvisioningTest {
    private fun project(repo: String = "fgna/example") = ProjectConfig(
        id = "p1",
        name = "Example",
        remotePath = "/home/freya/Projects/example",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
        githubRepository = repo,
    )

    @Test
    fun checkCommandReportsMissingAndExistingMarkers() {
        val command = ProjectProvisioning.checkCommand(project())
        assertTrue(command.contains("[ -d '/home/freya/Projects/example' ]"))
        assertTrue(command.contains(ProjectProvisioning.EXISTS_MARKER))
        assertTrue(command.contains(ProjectProvisioning.MISSING_MARKER))
    }

    @Test
    fun createCommandClonesConfiguredGithubRepository() {
        val command = ProjectProvisioning.createCommand(project())
        assertTrue(command.contains("git clone 'git@github.com:fgna/example.git' '/home/freya/Projects/example'"))
        assertTrue(command.contains(ProjectProvisioning.CREATED_MARKER))
    }

    @Test
    fun createCommandOnlyMakesDirectoryWithoutRepository() {
        val command = ProjectProvisioning.createCommand(project(repo = ""))
        assertTrue(command.contains("mkdir -p '/home/freya/Projects/example'"))
        assertTrue(!command.contains("git clone"))
    }
}
