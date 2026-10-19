package de.fgna.pocketdev.data

import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.project.ActionDefinition
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectConfigCodecTest {
    @Test
    fun roundTripsMultipleProjectsAndArbitraryCommandText() {
        val projects = listOf(
            ProjectConfig(
                id = "one",
                name = "PocketDev",
                remotePath = "/home/freya/Projects/pocketdev",
                testCommand = "./gradlew test\n./gradlew lint",
                buildCommand = "./gradlew assembleDebug | tee build.log",
                githubRepository = "fgna/pocketdev",
            ),
            ProjectConfig(
                id = "two",
                name = "Task OS | Android",
                remotePath = "/home/freya/Projects/my-taskOS",
                testCommand = "./gradlew test",
                buildCommand = "./gradlew assembleDebug",
                githubRepository = "fgna/my-taskos",
            ),
        )

        assertEquals(projects, ProjectConfigCodec.decode(ProjectConfigCodec.encode(projects)))
    }

    @Test
    fun profileAndCustomActionsRoundTrip() {
        val project = ProjectConfig(
            id = "docker", name = "Service", remotePath = "/srv/service",
            testCommand = "", buildCommand = "", actionProfileId = "docker-compose",
            customActions = listOf(ActionDefinition("deploy", "Deploy, now", "echo a;b|c\\nnext", true)),
        )
        assertEquals(listOf(project), ProjectConfigCodec.decode(ProjectConfigCodec.encode(listOf(project))))
    }

    @Test
    fun legacySixFieldRowsKeepAndroidDefaults() {
        val project = ProjectConfig(
            id = "legacy", name = "Android", remotePath = "/srv/android",
            testCommand = "./gradlew test", buildCommand = "./gradlew assembleDebug",
        )
        val row = ProjectConfigCodec.encode(listOf(project)).split("|").take(6).joinToString("|")
        assertEquals(listOf(project), ProjectConfigCodec.decode(row))
    }

    @Test fun legacyEightFieldAndFourValueActionsStillLoad() {
        val oldActions = listOf("deploy", "Deploy", "printf 'old;|, command'", "true").joinToString(",", transform = ::encodeField)
        val row = listOf("old", "Service", "/srv/service", "", "", "", "docker-compose", oldActions)
            .joinToString("|", transform = ::encodeField)
        val loaded = ProjectConfigCodec.decode(row).single()
        assertEquals("docker-compose", loaded.actionProfileId)
        assertEquals(listOf(ActionDefinition("deploy", "Deploy", "printf 'old;|, command'", true)), loaded.customActions)
        assertEquals(emptyList<String>(), loaded.actionOrder)
    }

    @Test fun orderAndDisabledOverridesSurviveReload() {
        val project = ProjectConfig(
            id = "service", name = "Service", remotePath = "/srv/service", testCommand = "", buildCommand = "",
            actionProfileId = "docker-compose", customActions = listOf(ActionDefinition("start", "Start", "docker compose up -d", false, false)),
            actionOrder = listOf("logs", "start", "stop", "status", "build"),
        )
        assertEquals(listOf(project), ProjectConfigCodec.decode(ProjectConfigCodec.encode(listOf(project))))
    }

    @Test
    fun emptyCollectionRoundTrips() {
        assertEquals(emptyList<ProjectConfig>(), ProjectConfigCodec.decode(ProjectConfigCodec.encode(emptyList())))
    }

    @Test
    fun malformedRowsAreIgnored() {
        val encoded = ProjectConfigCodec.encode(
            listOf(
                ProjectConfig(
                    id = "valid",
                    name = "Valid",
                    remotePath = "/tmp/valid",
                    testCommand = "test",
                    buildCommand = "build",
                ),
            ),
        )

        assertEquals(1, ProjectConfigCodec.decode("not-valid\n$encoded").size)
    }
}
