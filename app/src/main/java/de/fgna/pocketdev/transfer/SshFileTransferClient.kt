package de.fgna.pocketdev.transfer

import com.hierynomus.sshj.key.KeyAlgorithms
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.SshProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

data class RemoteTransferFile(
    val name: String,
    val sizeBytes: Long,
    val directory: Boolean,
)

data class RemoteDirectoryListing(
    val absolutePath: String,
    val files: List<RemoteTransferFile>,
)

class SshFileTransferClient {
    suspend fun list(
        profile: SshProfile,
        secret: String,
        configuredPath: String,
    ): RemoteDirectoryListing = withContext(Dispatchers.IO) {
        withClient(profile, secret) { ssh ->
            val path = resolveDirectory(ssh, configuredPath)
            val files = ssh.newSFTPClient().use { sftp ->
                sftp.ls(path)
                    .filter { it.name != "." && it.name != ".." }
                    .map { item ->
                        RemoteTransferFile(
                            name = item.name,
                            sizeBytes = item.attributes.size,
                            directory = item.isDirectory,
                        )
                    }
                    .sortedWith(compareBy<RemoteTransferFile> { !it.directory }.thenBy { it.name.lowercase() })
            }
            RemoteDirectoryListing(path, files)
        }
    }

    suspend fun upload(
        profile: SshProfile,
        secret: String,
        configuredPath: String,
        files: List<File>,
        onProgress: (Int, Int, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        withClient(profile, secret) { ssh ->
            val path = resolveDirectory(ssh, configuredPath)
            ssh.newSFTPClient().use { sftp ->
                files.forEachIndexed { index, file ->
                    require(file.isFile) { "Local transfer source is not a file: ${file.name}" }
                    val name = safeName(file.name)
                    onProgress(index + 1, files.size, name)
                    sftp.put(file.absolutePath, "$path/$name")
                }
            }
            path
        }
    }

    suspend fun download(
        profile: SshProfile,
        secret: String,
        configuredPath: String,
        names: List<String>,
        destinationDir: File,
        onProgress: (Int, Int, String) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        withClient(profile, secret) { ssh ->
            val path = resolveDirectory(ssh, configuredPath)
            destinationDir.deleteRecursively()
            destinationDir.mkdirs()
            ssh.newSFTPClient().use { sftp ->
                names.mapIndexed { index, rawName ->
                    val name = safeName(rawName)
                    onProgress(index + 1, names.size, name)
                    val target = File(destinationDir, name)
                    sftp.get("$path/$name", target.absolutePath)
                    require(target.isFile) { "Downloaded file is not readable: $name" }
                    target
                }
            }
        }
    }

    suspend fun deleteFile(
        profile: SshProfile,
        secret: String,
        configuredPath: String,
        rawName: String,
    ): String = withContext(Dispatchers.IO) {
        withClient(profile, secret) { ssh ->
            val path = resolveDirectory(ssh, configuredPath)
            val name = safeName(rawName)
            ssh.newSFTPClient().use { sftp ->
                val target = "$path/$name"
                val attributes = sftp.stat(target)
                require(!attributes.type.isDirectory) { "Directories cannot be deleted here." }
                sftp.rm(target)
            }
            path
        }
    }

    private fun resolveDirectory(ssh: SSHClient, configuredPath: String): String {
        val requested = configuredPath.trim().ifBlank { DEFAULT_SERVER_PATH }
        val quoted = ProjectCommandBuilder.shellQuote(requested)
        val command = "p=$quoted; case \"\$p\" in '~') p=\"\$HOME\" ;; '~/'*) p=\"\$HOME/\${p#\\~/}\" ;; esac; mkdir -p -- \"\$p\" && cd -- \"\$p\" && pwd -P"
        ssh.startSession().use { session ->
            val remote = session.exec(command)
            val stdout = remote.inputStream.bufferedReader().readText().trim()
            val stderr = remote.errorStream.bufferedReader().readText().trim()
            remote.join()
            val exit = remote.exitStatus ?: -1
            require(exit == 0 && stdout.startsWith("/")) {
                stderr.ifBlank { "Could not open server transfer directory." }
            }
            return stdout.lineSequence().last().trim()
        }
    }

    private fun <T> withClient(profile: SshProfile, secret: String, block: (SSHClient) -> T): T {
        require(profile.hostKeySha256.isNotBlank()) { "Trust the SSH server before transferring files." }
        val ssh = connect(profile)
        try {
            authenticate(ssh, profile, secret)
            return block(ssh)
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

    private fun safeName(name: String): String {
        val value = name.trim()
        require(value.isNotBlank() && value != "." && value != ".." && '/' !in value && '\\' !in value) {
            "Unsafe transfer filename: $name"
        }
        return value
    }

    companion object {
        const val DEFAULT_SERVER_PATH = "~/Downloads/PocketDevTransfer"
    }
}
