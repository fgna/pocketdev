package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkArtifactPathTest {
    @Test fun explicitPathWinsRegardlessOfCandidateTimesOrVariant() {
        val configured = "android/app/build/outputs/apk/debug/app-debug.apk"
        assertEquals(configured, ApkArtifactPath.select(configured, listOf(
            "./android/app/build/outputs/apk/release/app-release.apk",
            "./android/app/build/outputs/apk/debug/app-debug.apk",
        )))
    }

    @Test fun oneDiscoveredArtifactIsAccepted() {
        assertEquals("app/build/outputs/apk/debug/app-debug.apk",
            ApkArtifactPath.select("", listOf("./app/build/outputs/apk/debug/app-debug.apk")))
    }

    @Test fun severalBuildOutputsAreNeverChosenByModificationTime() {
        val candidates = listOf(
            "./android/app/build/outputs/apk/release/app-release.apk",
            "./android/app/build/outputs/apk/debug/app-debug.apk",
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            ApkArtifactPath.select("", candidates)
        }
        assert(error.message!!.contains("Multiple APKs"))
        assert(error.message!!.contains("Edit project"))
    }

    @Test fun noArtifactGivesActionableError() {
        assertThrows(IllegalArgumentException::class.java) {
            ApkArtifactPath.select("", emptyList())
        }
    }

    @Test fun pathWithSpacesIsAllowedWhenProjectRelative() {
        assertEquals("android/my app/build/outputs/apk/debug/my app.apk",
            ApkArtifactPath.select("android/my app/build/outputs/apk/debug/my app.apk", emptyList()))
    }

    @Test fun invalidConfiguredPathsFailClosed() {
        for (path in listOf(
            "/tmp/other.apk", "../outside.apk", "android/../release.apk",
            "android//app.apk", "android/./app.apk", "app.zip", "app.apk\\suffix",
            "app.apk\nanything", " app.apk"
        )) {
            assertThrows(path, IllegalArgumentException::class.java) {
                ApkArtifactPath.validateOptional(path)
            }
        }
    }

    @Test fun duplicateCandidateNamesAreDeduplicatedBeforeSelection() {
        assertEquals("android/app/build/outputs/apk/debug/app-debug.apk",
            ApkArtifactPath.select("", listOf(
                "./android/app/build/outputs/apk/debug/app-debug.apk",
                "android/app/build/outputs/apk/debug/app-debug.apk",
            )))
    }
}
