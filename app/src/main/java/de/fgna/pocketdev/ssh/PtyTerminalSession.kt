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
    private val activityId = java.util.UUID.randomUUID().toString()

    suspend fun run(
        profile: SshProfile,
        secret: String,
        directory: String,
        columns: () -> Int,
        onOutput: (String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust this SSH host in PocketDev first." }
        require(directory.startsWith("/")) { "Project path must be absolute." }
        check(active.compareAndSet(false, true)) { "PTY session is already running" }
        PtyActivityRegistry.begin(activityId)
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
            channel.allocatePTY("xterm", columns().coerceIn(20, 160), 24, 0, 0, emptyMap())
            val remote = channel.startShell()
            shell = remote
            input = remote.outputStream
            // A viewport measurement can arrive during SSH authentication.
            runCatching { remote.changeWindowDimensions(columns().coerceIn(20, 160), 24, 0, 0) }
            // Never leave users in $HOME when the project directory is missing.
            // A failed initial write also aborts the PTY instead of showing a misleading shell.
            check(send(projectStartupCommand(directory))) {
                "Could not initialize the remote project directory."
            }
            // Reader buffers split UTF-8 sequences across SSH network chunks.
            // Decoding each byte chunk independently corrupts Unicode terminal output.
            val reader = remote.inputStream.reader(Charsets.UTF_8)
            val buffer = CharArray(4096)
            while (active.get()) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count > 0) onOutput(String(buffer, 0, count))
            }
            remote.join()
            Unit // PTY shell exit status is not a reliable command result.
        } finally {
            active.set(false)
            PtyActivityRegistry.end(activityId)
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

    fun resize(columns: Int, rows: Int): Boolean {
        if (columns !in 20..500 || rows !in 5..300) return false
        val current = shell ?: return false
        return runCatching {
            current.changeWindowDimensions(columns, rows, 0, 0)
            true
        }.getOrDefault(false)
    }

    fun interrupt(): Boolean = send("\u0003")

    fun close() {
        active.set(false)
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { ssh?.disconnect() }
    }

    companion object {
        internal fun projectStartupCommand(directory: String): String {
            require(directory.startsWith("/") && '\n' !in directory && '\r' !in directory) {
                "Project directory must be a valid absolute path."
            }
            val quoted = "'" + directory.replace("'", "'\\''") + "'"
            return "cd $quoted || { echo 'PocketDev: project directory unavailable' >&2; exit 1; }\n"
        }
    }
}
