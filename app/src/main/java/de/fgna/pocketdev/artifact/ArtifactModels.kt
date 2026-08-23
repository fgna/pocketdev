package de.fgna.pocketdev.artifact

data class ArtifactDownloadState(
    val downloading: Boolean = false,
    val remotePath: String? = null,
    val localPath: String? = null,
    val error: String? = null,
)
