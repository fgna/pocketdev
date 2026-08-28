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
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest

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
    val sizeBytes: Long,
    val sha256: String,
)

class SshArtifactRetriever : ArtifactRetriever {
    override suspend fun downloadLatestApk(
        profile: SshProfile,
        secret: String,
        project: ProjectConfig,
        destinationDir: File,
    ): DownloadedArtifact = withContext(Dispatchers.IO) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust the SSH server before downloading artifacts." }

        var lastFailure: Exception? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                return@withContext downloadLatestApkOnce(profile, secret, project, destinationDir)
            } catch (error: Exception) {
                lastFailure = error
                cleanupDestination(destinationDir)
                if (attempt < MAX_DOWNLOAD_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        val cause = lastFailure
        error(
            "APK download failed after $MAX_DOWNLOAD_ATTEMPTS attempts: " +
                (cause?.message ?: cause?.javaClass?.simpleName ?: "unknown error"),
        )
    }

    private fun downloadLatestApkOnce(
        profile: SshProfile,
        secret: String,
        project: ProjectConfig,
        destinationDir: File,
    ): DownloadedArtifact {
        val ssh = connect(profile)
        try {
            authenticate(ssh, profile, secret)
            val relativePath = discoverLatestApk(ssh, project)
            val remotePath = project.remotePath.trimEnd('/') + "/" + relativePath.removePrefix("./")
            val remoteSha256 = remoteSha256(ssh, remotePath)

            cleanupDestination(destinationDir)
            destinationDir.mkdirs()

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

            val localSha256 = localSha256(localFile)
            require(localSha256.equals(remoteSha256, ignoreCase = true)) {
                "APK integrity check failed: server and downloaded SHA-256 differ."
            }
            File(localFile.absolutePath + VERIFIED_SUFFIX).writeText(localSha256)

            return DownloadedArtifact(
                remotePath = remotePath,
                localFile = localFile,
                sizeBytes = localFile.length(),
                sha256 = localSha256,
            )
        } finally {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
        }
    }

    private fun cleanupDestination(destinationDir: File) {
        destinationDir.mkdirs()
        destinationDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
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

    private fun remoteSha256(ssh: SSHClient, remotePath: String): String {
        val quotedPath = ProjectCommandBuilder.shellQuote(remotePath)
        ssh.startSession().use { session ->
            val remote = session.exec("sha256sum -- $quotedPath | cut -d' ' -f1")
            val stdout = remote.inputStream.bufferedReader().readText().trim()
            val stderr = remote.errorStream.bufferedReader().readText().trim()
            remote.join()
            val exit = remote.exitStatus ?: -1
            require(exit == 0 && SHA256.matches(stdout)) {
                stderr.ifBlank { "Could not calculate SHA-256 for the server APK." }
            }
            return stdout.lowercase()
        }
    }

    private fun localSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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

    companion object {
        const val VERIFIED_SUFFIX = ".sha256-verified"
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 750L
        private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
    }
}
