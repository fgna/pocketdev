package de.fgna.pocketdev.artifact

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactDownloadHistoryTest {
    @Test
    fun firstSuccessfulDownloadDoesNotWarn() {
        val directory = createTempDirectory("pocketdev-artifact-history").toFile()
        val apk = File(directory, "first.apk").apply { writeText("first") }

        ArtifactDownloadHistory.recordSuccessfulDownload(directory, apk, "a".repeat(64))

        assertNull(ArtifactDownloadHistory.warningFor(apk.absolutePath))
        assertTrue(File(directory, ArtifactDownloadHistory.LAST_SUCCESSFUL_SHA256_FILE).isFile)
    }

    @Test
    fun identicalSuccessfulDownloadWarns() {
        val directory = createTempDirectory("pocketdev-artifact-history").toFile()
        val firstApk = File(directory, "first.apk").apply { writeText("same") }
        val secondApk = File(directory, "second.apk").apply { writeText("same") }
        val sha256 = "b".repeat(64)

        ArtifactDownloadHistory.recordSuccessfulDownload(directory, firstApk, sha256)
        ArtifactDownloadHistory.recordSuccessfulDownload(directory, secondApk, sha256)

        assertEquals("unchanged since last download.", ArtifactDownloadHistory.warningFor(secondApk.absolutePath))
    }

    @Test
    fun changedSuccessfulDownloadDoesNotWarn() {
        val directory = createTempDirectory("pocketdev-artifact-history").toFile()
        val firstApk = File(directory, "first.apk").apply { writeText("first") }
        val secondApk = File(directory, "second.apk").apply { writeText("second") }

        ArtifactDownloadHistory.recordSuccessfulDownload(directory, firstApk, "c".repeat(64))
        ArtifactDownloadHistory.recordSuccessfulDownload(directory, secondApk, "d".repeat(64))

        assertNull(ArtifactDownloadHistory.warningFor(secondApk.absolutePath))
    }

    @Test
    fun onlyHistoryFileIsPreservedDuringArtifactCleanup() {
        val directory = createTempDirectory("pocketdev-artifact-history").toFile()
        val history = File(directory, ArtifactDownloadHistory.LAST_SUCCESSFUL_SHA256_FILE)
        val apk = File(directory, "build.apk")

        assertTrue(ArtifactDownloadHistory.shouldPreserveDuringCleanup(history))
        assertFalse(ArtifactDownloadHistory.shouldPreserveDuringCleanup(apk))
    }
}
