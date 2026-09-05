package de.fgna.pocketdev.artifact

import java.io.File

internal object ArtifactDownloadHistory {
    const val LAST_SUCCESSFUL_SHA256_FILE = ".last-successful-apk.sha256"
    const val UNCHANGED_MARKER_SUFFIX = ".unchanged-since-last-download"

    fun recordSuccessfulDownload(
        destinationDir: File,
        localFile: File,
        sha256: String,
    ) {
        require(SHA256.matches(sha256)) { "Invalid APK SHA-256." }
        destinationDir.mkdirs()

        val normalizedSha256 = sha256.lowercase()
        val previousSha256 = readLastSuccessfulSha256(destinationDir)
        val unchanged = previousSha256?.equals(normalizedSha256, ignoreCase = true) == true

        File(destinationDir, LAST_SUCCESSFUL_SHA256_FILE).writeText(normalizedSha256)
        val marker = File(localFile.absolutePath + UNCHANGED_MARKER_SUFFIX)
        if (unchanged) {
            marker.writeText(normalizedSha256)
        } else {
            marker.delete()
        }
    }

    fun warningFor(localPath: String?): String? {
        if (localPath.isNullOrBlank()) return null
        val marker = File(localPath + UNCHANGED_MARKER_SUFFIX)
        return if (marker.isFile) "unchanged since last download." else null
    }

    fun shouldPreserveDuringCleanup(file: File): Boolean =
        file.name == LAST_SUCCESSFUL_SHA256_FILE

    private fun readLastSuccessfulSha256(destinationDir: File): String? {
        val stored = File(destinationDir, LAST_SUCCESSFUL_SHA256_FILE)
            .takeIf(File::isFile)
            ?.readText()
            ?.trim()
            .orEmpty()
        return stored.takeIf(SHA256::matches)?.lowercase()
    }

    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
}
