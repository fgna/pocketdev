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
    fun favoritesSurviveReloadWithExactTextAndProjectIsolation() {
        val command = "  printf '%s\\n' 'a|b,c;d'\n\n# Grüße ★\n  "
        val first = ProjectConfig("First", "/srv/first", "", "", id = "first")
            .withCommandFavorite(command, true)
            .withCommandFavorite(command, true)
        val second = ProjectConfig("Second", "/srv/second", "", "", id = "second")
            .withCommandFavorite("echo second", true)
        val reloaded = ProjectConfigCodec.decode(ProjectConfigCodec.encode(listOf(first, second)))
        assertEquals(listOf(command), reloaded[0].commandFavorites)
        assertEquals(listOf("echo second"), reloaded[1].commandFavorites)
        val removed = reloaded[0].withCommandFavorite(command, false)
        val afterRemoval = ProjectConfigCodec.decode(ProjectConfigCodec.encode(listOf(removed, reloaded[1])))
        assertEquals(emptyList<String>(), afterRemoval[0].commandFavorites)
        assertEquals(second, afterRemoval[1])
        assertEquals(first.copy(commandFavorites = emptyList()), afterRemoval[0])
    }

    @Test
    fun removingMissingFavoriteAndSavingBlankDoNotChangeProject() {
        val project = ProjectConfig("Service", "/srv/service", "test", "build", id = "service")
            .withCommandFavorite("echo keep", true)
        assertEquals(project, project.withCommandFavorite("echo missing", false))
        assertEquals(project, project.withCommandFavorite(" \n ", true))
        assertEquals(project, project.withCommandFavorite(" \n ", false))
    }

    @Test
    fun legacyNineFieldRowsPreserveOrderAndDefaultToNoFavorites() {
        val project = ProjectConfig(
            "Service", "/srv/service", "test", "build", id = "service",
            actionOrder = listOf("build", "test"),
        )
        val row = ProjectConfigCodec.encode(listOf(project)).split('|').take(9).joinToString("|")
        assertEquals(listOf(project), ProjectConfigCodec.decode(row))
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

