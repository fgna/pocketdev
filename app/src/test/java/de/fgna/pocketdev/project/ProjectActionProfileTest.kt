package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectActionProfileTest {
    private fun project(profileId: String = "docker-compose") = ProjectConfig(
        name = "Example",
        remotePath = "/srv/example",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
        actionProfileId = profileId,
    )

    @Test fun dockerProjectUsesDockerCommands() {
        assertEquals("docker compose ps", ProjectCommandBuilder.command(project(), "status"))
        assertEquals("docker compose logs --tail=100", ProjectCommandBuilder.command(project(), "logs"))
    }

    @Test fun projectCanOverridePresetAndAddAction() {
        val configured = project().copy(customActions = listOf(
            ActionDefinition("start", "Start custom", "docker compose up -d --wait"),
            ActionDefinition("deploy", "Deploy", "./deploy.sh"),
        ))
        assertEquals("docker compose up -d --wait", ProjectCommandBuilder.command(configured, "start"))
        assertEquals("./deploy.sh", ProjectCommandBuilder.command(configured, "deploy"))
        assertEquals(6, ProjectCommandBuilder.availableActions(configured).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownActionIsRejected() {
        ProjectCommandBuilder.command(project(), "nonexistent")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownProfileIsRejected() {
        ProjectCommandBuilder.availableActions(project("unknown"))
    }
}
