package de.fgna.pocketdev.data

import de.fgna.pocketdev.project.ProjectConfig
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
