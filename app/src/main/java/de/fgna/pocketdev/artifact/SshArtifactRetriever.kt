package de.fgna.pocketdev.artifact

import com.hierynomus.sshj.key.KeyAlgorithms
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.SshProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

interface ArtifactRetriever {
    suspend fun downloadLatestApk(
        profile: SshProfile,
        secret: String,
        project: ProjectConfig,
        destinationDir: File,
    ): DownloadedArtifact
}

data class DownloadedArtifact(
    val remotePath: String,
    val localFile: File,
)

class SshArtifactRetriever : ArtifactRetriever {
    override suspend fun downloadLatestApk(
        profile: SshProfile,
        secret: String,
        project: ProjectConfig,
        destinationDir: File,
    ): DownloadedArtifact = withContext(Dispatchers.IO) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust the SSH server before downloading artifacts." }

        val ssh = connect(profile)
        try {
            authenticate(ssh, profile, secret)
            val relativePath = discoverLatestApk(ssh, project)
            val remotePath = project.remotePath.trimEnd('/') + "/" + relativePath.removePrefix("./")
            destinationDir.mkdirs()
            destinationDir.listFiles()?.forEach { old -> if (old.isFile) old.delete() }

            // Use a fresh local filename for every retrieval. Android's package installer may
            // reuse/carry cached state for an identical FileProvider URI even after the backing
            // cache file has been replaced, which can result in installing an older APK.
            val remoteName = remotePath.substringAfterLast('/').ifBlank { "pocketdev-build.apk" }
            val baseName = remoteName.removeSuffix(".apk")
            val uniqueName = "$baseName-${System.currentTimeMillis()}.apk"
            val localFile = File(destinationDir, uniqueName)

            ssh.newSFTPClient().use { sftp ->
                sftp.get(remotePath, localFile.absolutePath)
            }
            require(localFile.isFile && localFile.length() > 0L) { "APK download completed without a readable local file." }
            DownloadedArtifact(remotePath = remotePath, localFile = localFile)
        } finally {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
        }
    }

    private fun connect(profile: SshProfile): SSHClient {
        val failures = mutableListOf<String>()
        for (target in resolveTargets(profile.host)) {
            val candidate = createAndroidCompatibleClient().apply {
                addHostKeyVerifier(profile.hostKeySha256.trim())
            }
            try {
                candidate.connect(target, profile.port)
                return candidate
            } catch (error: Exception) {
                failures += "$target:${profile.port} -> ${error.message ?: error::class.java.simpleName}"
                runCatching { candidate.disconnect() }
                runCatching { candidate.close() }
            }
        }
        error("Could not connect to ${profile.host}:${profile.port}. Tried: ${failures.joinToString(" | ")}")
    }

    private fun authenticate(ssh: SSHClient, profile: SshProfile, secret: String) {
        when (profile.authMode) {
            AuthMode.PASSWORD -> ssh.authPassword(profile.username, secret)
            AuthMode.PRIVATE_KEY -> {
                val keyFile = kotlin.io.path.createTempFile("pocketdev-key", ".pem").toFile()
                try {
                    keyFile.writeText(secret)
                    ssh.authPublickey(profile.username, ssh.loadKeys(keyFile.absolutePath))
                } finally {
                    keyFile.delete()
                }
            }
        }
    }

    private fun discoverLatestApk(ssh: SSHClient, project: ProjectConfig): String {
        val quotedPath = ProjectCommandBuilder.shellQuote(project.remotePath)
        val command = "cd $quotedPath && find . -type f -name '*.apk' -path '*/build/outputs/apk/*' -printf '%T@ %p\\n' | sort -nr | head -n 1 | cut -d' ' -f2-"
        ssh.startSession().use { session ->
            val remote = session.exec(command)
            val stdout = remote.inputStream.bufferedReader().readText().trim()
            val stderr = remote.errorStream.bufferedReader().readText().trim()
            remote.join()
            val exit = remote.exitStatus ?: -1
            require(exit == 0) { stderr.ifBlank { "APK discovery failed with exit $exit." } }
            require(stdout.isNotBlank()) { "No APK found under build/outputs/apk in ${project.remotePath}. Run Build first." }
            return stdout.lineSequence().first().trim()
        }
    }

    private fun createAndroidCompatibleClient(): SSHClient {
        val config = DefaultSecurityProviderConfig().apply {
            setKeyAlgorithms(listOf(KeyAlgorithms.RSASHA512(), KeyAlgorithms.RSASHA256()))
        }
        return SSHClient(config)
    }

    private fun resolveTargets(host: String): List<String> = InetAddress.getAllByName(host)
        .toList()
        .sortedByDescending { it is Inet4Address }
        .mapNotNull { it.hostAddress }
        .distinct()
        .ifEmpty { listOf(host) }
}
