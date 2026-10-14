package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionProfileTemplatesTest {
    @Test fun dockerCommandsAreAvailable() {
        val actions = ActionProfileTemplates.dockerCompose.actions
        assertEquals("docker compose up -d", actions.first { it.id == "start" }.command)
        assertTrue(actions.first { it.id == "stop" }.requiresConfirmation)
    }

    @Test fun overridesReplaceAndAppendWithoutMutatingTemplate() {
        val original = ActionProfileTemplates.generic
        val actions = ActionProfileResolver.resolve(
            original,
            listOf(
                ActionDefinition("git-status", "Branch", "git branch --show-current"),
                ActionDefinition("deploy", "Deploy", "./deploy.sh", true),
            ),
        )
        assertEquals(listOf("git-status", "deploy"), actions.map { it.id })
        assertEquals("git branch --show-current", actions[0].command)
        assertEquals("git status --short --branch", original.actions[0].command)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateOverridesRejected() {
        ActionProfileResolver.resolve(
            ActionProfileTemplates.generic,
            listOf(ActionDefinition("x", "X", "true"), ActionDefinition("x", "Y", "false")),
        )
    }
}
