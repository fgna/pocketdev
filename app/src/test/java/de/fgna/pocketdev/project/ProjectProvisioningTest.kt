package de.fgna.pocketdev.project

import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectProvisioningTest {
    private val project = ProjectConfig(
        id = "p1",
        name = "PocketDev test",
        remotePath = "/home/freya/Projects/new project",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
        githubRepository = "fgna/pocketdev",
    )

    @Test
    fun checkCommandReportsExistsOrMissing() {
        val command = ProjectProvisioning.checkCommand(project)
        assertTrue(command.contains(ProjectProvisioning.EXISTS_MARKER))
        assertTrue(command.contains(ProjectProvisioning.MISSING_MARKER))
        assertTrue(command.contains("'/home/freya/Projects/new project'"))
    }

    @Test
    fun createCommandClonesConfiguredRepositoryAndVerifiesFolder() {
        val command = ProjectProvisioning.createCommand(project)
        assertTrue(command.contains("git clone 'git@github.com:fgna/pocketdev.git' '/home/freya/Projects/new project'"))
        assertTrue(command.contains("mkdir -p '/home/freya/Projects'"))
        assertTrue(command.contains("test -d '/home/freya/Projects/new project'"))
        assertTrue(command.contains(ProjectProvisioning.CREATED_MARKER))
    }

    @Test
    fun createCommandMakesEmptyFolderWhenRepositoryIsBlank() {
        val command = ProjectProvisioning.createCommand(project.copy(githubRepository = ""))
        assertTrue(command.contains("mkdir -p '/home/freya/Projects/new project'"))
        assertTrue(command.contains("test -d '/home/freya/Projects/new project'"))
        assertTrue(command.contains(ProjectProvisioning.CREATED_MARKER))
    }
}
