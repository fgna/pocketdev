package de.fgna.pocketdev.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import java.io.InputStream

interface SshCommandExecutor {
    fun execute(profile: SshProfile, secret: String, command: String): Flow<CommandEvent>
}

class SshjCommandExecutor : SshCommandExecutor {
    override fun execute(profile: SshProfile, secret: String, command: String): Flow<CommandEvent> = channelFlow {
        val ssh = SSHClient()
        send(CommandEvent.Connecting)

        try {
            ssh.addHostKeyVerifier(normalizeFingerprint(profile.hostKeySha256))

            withContext(Dispatchers.IO) {
                ssh.connect(profile.host, profile.port)
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

    private fun normalizeFingerprint(value: String): String {
        val normalized = value.trim()
        return if (normalized.startsWith("SHA256:")) normalized else "SHA256:$normalized"
    }
}
