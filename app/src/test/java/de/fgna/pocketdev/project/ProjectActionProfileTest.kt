package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun androidKeepsExistingProjectCommands() {
        val old = project("android-gradle").copy(testCommand = "cd android && ./gradlew customTest", buildCommand = "./gradlew assembleRelease")
        assertEquals(old.testCommand, ProjectCommandBuilder.command(old, "test"))
        assertEquals(old.buildCommand, ProjectCommandBuilder.command(old, "build"))
        assertTrue(ProjectCommandBuilder.actionProfile(old).supportsApk)
        assertFalse(ProjectCommandBuilder.actionProfile(project()).supportsApk)
    }

    @Test fun blankLegacyCommandsStayDisabledUntilConfigured() {
        val old = project("android-gradle").copy(testCommand = "", buildCommand = "")
        assertEquals(listOf("git-status"), ProjectCommandBuilder.availableActions(old).map { it.id })
    }

    @Test fun reorderedAndDisabledActionsResolveWithoutChangingTemplate() {
        val configured = project().copy(
            customActions = listOf(ActionProfileTemplates.dockerCompose.actions.first { it.id == "start" }.copy(enabled = false)),
            actionOrder = listOf("logs", "build", "status", "stop", "start"),
        )
        assertEquals(listOf("logs", "build", "status", "stop"), ProjectCommandBuilder.availableActions(configured).map { it.id })
        assertEquals(5, ProjectCommandBuilder.configuredActions(configured).size)
        assertTrue(ActionProfileTemplates.dockerCompose.actions.first { it.id == "start" }.enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun disabledActionCannotBeExecutedById() {
        val configured = project().copy(customActions = listOf(ActionDefinition("stop", "Stop", "docker compose stop", true, false)))
        ProjectCommandBuilder.command(configured, "stop")
    }

    @Test fun sharedProfilesApplyToMultipleProjectsAndRespectLocalOverrides() {
        val shared = ActionProfile("team-compose", "Team Compose", listOf(ActionDefinition("status", "Status", "docker compose ps")))
        val a = project(shared.id)
        val b = project(shared.id).copy(id = "b", customActions = listOf(ActionDefinition("status", "Local", "docker compose ps --all")))
        val updated = shared.copy(actions = listOf(ActionDefinition("status", "New", "docker compose ps --format json")))
        val profiles = ActionProfileTemplates.all + updated
        assertEquals("docker compose ps --format json", ProjectCommandBuilder.command(a, "status", profiles))
        assertEquals("docker compose ps --all", ProjectCommandBuilder.command(b, "status", profiles))
    }

    @Test fun customAndroidProfileRetainsApkCapability() {
        val profile = ActionProfileTemplates.android.copy(id = "my-android", name = "My Android")
        val configured = project(profile.id).copy(testCommand = "ignored legacy")
        assertTrue(ProjectCommandBuilder.actionProfile(configured, listOf(profile)).supportsApk)
        assertEquals("./gradlew test", ProjectCommandBuilder.command(configured, "test", listOf(profile)))
    }

    @Test fun unchangedProjectFollowsSharedOrderButExplicitReorderingStaysLocal() {
        val shared = ActionProfileTemplates.dockerCompose.copy(id = "team-compose")
        val initial = project(shared.id)
        val unchanged = ProjectCommandBuilder.withEditedActions(initial, shared.actions, listOf(shared))
        val locallyOrdered = ProjectCommandBuilder.withEditedActions(initial, shared.actions.reversed(), listOf(shared))
        val updated = shared.copy(actions = shared.actions.drop(1) + shared.actions.first())
        assertTrue(unchanged.customActions.isEmpty())
        assertTrue(unchanged.actionOrder.isEmpty())
        assertEquals(updated.actions.map { it.id }, ProjectCommandBuilder.availableActions(unchanged, listOf(updated)).map { it.id })
        assertEquals(shared.actions.reversed().map { it.id }, ProjectCommandBuilder.availableActions(locallyOrdered, listOf(updated)).map { it.id })
    }
}
