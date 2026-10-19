package de.fgna.pocketdev.ssh

import com.hierynomus.sshj.key.KeyAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.OutputStream
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated SSH PTY lifecycle. The non-PTY command runner is deliberately unchanged.
 * A shell with a real SSH PTY allows interactive programs to read from /dev/tty.
 */
class PtyTerminalSession {
    private var ssh: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var input: OutputStream? = null
    private val active = AtomicBoolean(false)

    suspend fun run(
        profile: SshProfile,
        secret: String,
        directory: String,
        onOutput: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust this SSH host in PocketDev first." }
        require(directory.startsWith("/")) { "Project path must be absolute." }
        check(active.compareAndSet(false, true)) { "PTY session is already running" }
        try {
            val client = SSHClient(DefaultSecurityProviderConfig().apply {
                setKeyAlgorithms(listOf(KeyAlgorithms.RSASHA512(), KeyAlgorithms.RSASHA256()))
            })
            ssh = client
            client.addHostKeyVerifier(profile.hostKeySha256.trim())
            client.connect(profile.host, profile.port)
            when (profile.authMode) {
                AuthMode.PASSWORD -> client.authPassword(profile.username, secret)
                AuthMode.PRIVATE_KEY -> {
                    val key = kotlin.io.path.createTempFile("pocketdev-pty-key", ".pem").toFile()
                    try {
                        key.writeText(secret)
                        client.authPublickey(profile.username, client.loadKeys(key.absolutePath))
                    } finally {
                        key.delete()
                    }
                }
            }
            val channel = client.startSession()
            session = channel
            channel.allocateDefaultPTY()
            val remote = channel.startShell()
            shell = remote
            input = remote.outputStream
            // Enter the project's directory in the remote login shell.
            send("cd " + quote(directory) + "\n")
            val buffer = ByteArray(4096)
            while (active.get()) {
                val count = remote.inputStream.read(buffer)
                if (count < 0) break
                if (count > 0) onOutput(String(buffer, 0, count, Charsets.UTF_8))
            }
            remote.join()
            remote.exitStatus ?: 0
        } finally {
            active.set(false)
            runCatching { shell?.close() }
            runCatching { session?.close() }
            runCatching { ssh?.disconnect() }
            runCatching { ssh?.close() }
            input = null
            shell = null
            session = null
            ssh = null
        }
    }

    @Synchronized fun send(text: String): Boolean {
        if (!active.get()) return false
        return runCatching { input?.apply { write(text.toByteArray(Charsets.UTF_8)); flush() } != null }.getOrDefault(false)
    }

    fun interrupt(): Boolean = send("\u0003")

    fun close() {
        active.set(false)
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { ssh?.disconnect() }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
