package de.fgna.pocketdev.project

import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectProvisioningValidationTest {
    @Test
    fun rejectsFilesystemRoot() {
        val project = ProjectConfig("Root", "/", "true", "true", "", "root")
        assertThrows(IllegalArgumentException::class.java) {
            ProjectProvisioning.createCommand(project)
        }
    }

    @Test
    fun rejectsInvalidGithubRepositoryFormat() {
        val project = ProjectConfig("Bad", "/home/user/Projects/bad", "true", "true", "not a repo", "bad")
        assertThrows(IllegalArgumentException::class.java) {
            ProjectProvisioning.createCommand(project)
        }
    }
}
