package de.fgna.pocketdev.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.InputStream
import java.security.PublicKey

interface SshCommandExecutor {
    fun execute(profile: SshProfile, secret: String, command: String): Flow<CommandEvent>
}

class SshjCommandExecutor : SshCommandExecutor {
    override fun execute(profile: SshProfile, secret: String, command: String): Flow<CommandEvent> = channelFlow {
        val ssh = SSHClient()
        send(CommandEvent.Connecting)

        try {
            var discoveredFingerprint: String? = null
            if (profile.hostKeySha256.isBlank()) {
                ssh.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                        discoveredFingerprint = SecurityUtils.getFingerprint(key)
                        return hostname == profile.host && port == profile.port
                    }

                    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
                })
            } else {
                ssh.addHostKeyVerifier(profile.hostKeySha256.trim())
            }

            withContext(Dispatchers.IO) {
                ssh.connect(profile.host, profile.port)
            }

            if (profile.hostKeySha256.isBlank()) {
                val fingerprint = discoveredFingerprint
                    ?: throw IllegalStateException("Server host key fingerprint could not be read.")
                send(CommandEvent.HostKeyTrustRequired(fingerprint))
                return@channelFlow
            }

            withContext(Dispatchers.IO) {
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
            send(CommandEvent.Connected)

            val session = withContext(Dispatchers.IO) { ssh.startSession() }
            val remote = withContext(Dispatchers.IO) { session.exec(command) }

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
                runCatching { ssh.disconnect() }
                runCatching { ssh.close() }
            }
        }
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
