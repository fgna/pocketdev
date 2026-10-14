package de.fgna.pocketdev.project

import de.fgna.pocketdev.data.ActionProfileCodec
import de.fgna.pocketdev.data.ProjectConfigCodec
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ActionProfileExecutionTest {
    @Test fun savedComposeActionRunsWithItsArgumentsInQuotedProjectDirectory() {
        val directory = Files.createTempDirectory("pocketdev project's ").toFile()
        try {
            val custom = ActionProfileTemplates.dockerCompose.copy(id = "team-compose", name = "Team Compose")
            val profiles = ActionProfileTemplates.all + ActionProfileCodec.decode(ActionProfileCodec.encode(listOf(custom)))
            val saved = ProjectConfig(
                id = "test-run", name = "Service", remotePath = directory.absolutePath,
                testCommand = "", buildCommand = "", actionProfileId = custom.id,
                customActions = listOf(ActionDefinition("logs", "Recent logs", "docker compose logs --tail=5")),
                actionOrder = listOf("logs", "status", "start", "stop", "build"),
            )
            val reloaded = ProjectConfigCodec.decode(ProjectConfigCodec.encode(listOf(saved))).single()
            assertEquals("logs", ProjectCommandBuilder.availableActions(reloaded, profiles).first().id)
            val command = ProjectCommandBuilder.command(reloaded, "logs", profiles)
            val script = "docker() { printf 'arguments:%s\\n' \"\$*\"; pwd; };\n" +
                ProjectCommandBuilder.trackedInDirectory(reloaded.remotePath, command, reloaded.id)
            val process = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
            try {
                assertTrue("Command did not finish", process.waitFor(5, TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output, 0, process.exitValue())
                assertTrue(output.contains("arguments:compose logs --tail=5\n"))
                assertEquals(directory.absolutePath, ProjectCommandBuilder.extractWorkingDirectory(output, reloaded.id))
            } finally {
                process.destroyForcibly()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
