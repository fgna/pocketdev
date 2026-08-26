package de.fgna.pocketdev.artifact

data class ArtifactDownloadState(
    val downloading: Boolean = false,
    val remotePath: String? = null,
    val localPath: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val verified: Boolean = false,
    val error: String? = null,
)
