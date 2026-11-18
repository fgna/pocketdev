package de.fgna.pocketdev.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import java.net.ServerSocket

/** Uses the same pinned host and in-memory authentication path as ordinary commands. */
internal class SshjLocalForwardTransport(
    private val clientFactory: () -> SSHClient = { SshConnectionFactory.createClient(keepAlive = true) },
) : LocalForwardTransport {
    private val lock = Any()
    @Volatile private var closed = false
    @Volatile private var client: SSHClient? = null

    override fun connect(profile: SshProfile, secret: String) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust the SSH host first" }
        val ssh = SshConnectionFactory.connect(profile, keepAlive = true, onClient = { candidate ->
            synchronized(lock) {
                check(!closed) { "Tunnel stopped" }
                candidate.connectTimeout = 15_000
                // Keep socket reads unbounded; response-checked keepalives detect a dead peer.
                candidate.timeout = 0
                client = candidate
            }
        }, clientFactory = clientFactory)
        if (closed) {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            error("Tunnel stopped")
        }
        SshConnectionFactory.authenticate(ssh, profile, secret)
    }

    override val connected: Boolean
        get() = !closed && client?.let { it.isConnected && it.isAuthenticated } == true

    override fun listen(config: LocalPortForwardConfig, socket: ServerSocket) {
        check(connected) { "SSH disconnected" }
        val ssh = checkNotNull(client)
        ssh.newLocalPortForwarder(
            Parameters(config.localHost, config.localPort, config.remoteHost, config.remotePort), socket,
        ).listen()
    }

    override fun close() {
        val ssh = synchronized(lock) {
            if (closed) return
            closed = true
            client.also { client = null }
        }
        runCatching { ssh?.disconnect() }
        runCatching { ssh?.close() }
    }
}
