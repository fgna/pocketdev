package de.fgna.pocketdev.ssh

import com.hierynomus.sshj.key.KeyAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.security.PublicKey

interface SshCommandExecutor {
    fun execute(profile: SshProfile, secret: String, command: String, stdinText: String? = null): Flow<CommandEvent>
}

class SshjCommandExecutor : SshCommandExecutor {
    override fun execute(
        profile: SshProfile,
        secret: String,
        command: String,
        stdinText: String?,
    ): Flow<CommandEvent> = channelFlow {
        var ssh: SSHClient? = null
        var discoveredFingerprint: String? = null
        send(CommandEvent.Connecting)

        try {
            val targets = withContext(Dispatchers.IO) { resolveTargets(profile.host) }
            val failures = mutableListOf<String>()

            for (target in targets) {
                val candidate = createAndroidCompatibleClient()
                configureHostKeyVerifier(candidate, profile) { fingerprint ->
                    discoveredFingerprint = fingerprint
                }

                try {
                    withContext(Dispatchers.IO) {
                        candidate.connect(target, profile.port)
                    }
                    ssh = candidate
                    break
                } catch (error: Exception) {
                    failures += "$target:${profile.port} -> ${error.message ?: error::class.java.simpleName}"
                    withContext(Dispatchers.IO) {
                        runCatching { candidate.disconnect() }
                        runCatching { candidate.close() }
                    }
                }
            }

            val connected = ssh ?: throw IllegalStateException(
                "Could not connect to ${profile.host}:${profile.port}. Tried: ${failures.joinToString(" | ")}",
            )

            if (profile.hostKeySha256.isBlank()) {
                val fingerprint = discoveredFingerprint
                    ?: throw IllegalStateException("Server host key fingerprint could not be read.")
                send(CommandEvent.HostKeyTrustRequired(fingerprint))
                return@channelFlow
            }

            withContext(Dispatchers.IO) {
                when (profile.authMode) {
                    AuthMode.PASSWORD -> connected.authPassword(profile.username, secret)
                    AuthMode.PRIVATE_KEY -> {
                        val keyFile = kotlin.io.path.createTempFile("pocketdev-key", ".pem").toFile()
                        try {
                            keyFile.writeText(secret)
                            connected.authPublickey(profile.username, connected.loadKeys(keyFile.absolutePath))
                        } finally {
                            keyFile.delete()
                        }
                    }
                }
            }
            send(CommandEvent.Connected)

            val session = withContext(Dispatchers.IO) { connected.startSession() }
            val remote = withContext(Dispatchers.IO) { session.exec(command) }

            if (stdinText != null) {
                withContext(Dispatchers.IO) {
                    remote.outputStream.bufferedWriter().use { writer ->
                        writer.write(stdinText)
                        writer.newLine()
                        writer.flush()
                    }
                }
            }

            val stdoutJob = launch(Dispatchers.IO) {
                stream(remote.inputStream) { text -> trySend(CommandEvent.Output(OutputStreamKind.STDOUT, text)) }
            }
            val stderrJob = launch(Dispatchers.IO) {
                stream(remote.errorStream) { text -> trySend(CommandEvent.Output(OutputStreamKind.STDERR, text)) }
            }

            withContext(Dispatchers.IO) { remote.join() }
            stdoutJob.join()
            stderrJob.join()
            trySend(CommandEvent.Completed(remote.exitStatus ?: -1))
            withContext(Dispatchers.IO) { session.close() }
        } catch (error: Exception) {
            trySend(CommandEvent.ConnectionFailed(error.message ?: error::class.java.simpleName))
        } finally {
            withContext(Dispatchers.IO) {
                ssh?.let { client ->
                    runCatching { client.disconnect() }
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun createAndroidCompatibleClient(): SSHClient {
        val config = DefaultSecurityProviderConfig().apply {
            // SSHJ 0.40.0 is not compatible with Android Conscrypt's Ed25519
            // public-key representation and also asks Android for KeyFactory("ECDSA").
            // Restrict host-key negotiation to modern RSA-SHA2 algorithms, which
            // Android handles through the standard RSA KeyFactory.
            setKeyAlgorithms(
                listOf(
                    KeyAlgorithms.RSASHA512(),
                    KeyAlgorithms.RSASHA256(),
                ),
            )
        }
        return SSHClient(config)
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        profile: SshProfile,
        onDiscoveredFingerprint: (String) -> Unit,
    ) {
        if (profile.hostKeySha256.isBlank()) {
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    onDiscoveredFingerprint(SecurityUtils.getFingerprint(key))
                    return true
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            })
        } else {
            ssh.addHostKeyVerifier(profile.hostKeySha256.trim())
        }
    }

    private fun resolveTargets(host: String): List<String> {
        val addresses = InetAddress.getAllByName(host).toList()
        return addresses
            .sortedByDescending { it is Inet4Address }
            .mapNotNull { it.hostAddress }
            .distinct()
            .ifEmpty { listOf(host) }
    }

    private fun stream(input: InputStream, onChunk: (String) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            onChunk(buffer.decodeToString(0, count))
        }
    }
}
