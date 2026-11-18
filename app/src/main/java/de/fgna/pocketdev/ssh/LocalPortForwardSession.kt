package de.fgna.pocketdev.ssh

import java.io.Closeable
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Transport seam: implementations forward opaque TCP bytes, never HTTP or callback URLs. */
internal interface LocalForwardTransport : Closeable {
    fun connect(profile: SshProfile, secret: String)
    val connected: Boolean
    fun listen(config: LocalPortForwardConfig, socket: ServerSocket)
}

/** One owned listener and SSH connection. run/close must execute off the Android main thread. */
internal class LocalPortForwardSession(
    val config: LocalPortForwardConfig,
    private val transportFactory: () -> LocalForwardTransport,
    private val onState: (LocalPortForwardState) -> Unit,
) : Closeable {
    private val lock = Any()
    private var started = false
    @Volatile private var closed = false
    private var listener: ServerSocket? = null
    private var transport: LocalForwardTransport? = null
    private var listenerThread: Thread? = null
    private var state: LocalPortForwardState = LocalPortForwardState.Stopped

    fun run(profile: SshProfile, secret: String) {
        synchronized(lock) {
            if (closed || started) return
            started = true
            publish(LocalPortForwardState.Starting(config))
        }
        var failure: LocalPortForwardFailure? = null
        var stage = LocalPortForwardFailure.SSH_CONNECTION_FAILED
        try {
            if (profile.hostKeySha256.isBlank()) {
                failure = LocalPortForwardFailure.SSH_HOST_UNVERIFIED
                return
            }
            if (secret.isBlank()) {
                failure = LocalPortForwardFailure.SSH_CREDENTIALS_MISSING
                return
            }
            val socket = OwnedListener()
            synchronized(lock) {
                if (closed) { socket.close(); return }
                listener = socket
            }
            // Never retry a failed bind on another address or an automatically chosen port.
            stage = LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE
            // Reuse TIME_WAIT endpoints after stop; an existing listener still causes a bind collision.
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(config.localHost), config.localPort))
            stage = LocalPortForwardFailure.SSH_CONNECTION_FAILED
            val connection = transportFactory()
            val owned = synchronized(lock) {
                if (closed) false else { transport = connection; true }
            }
            if (!owned) { connection.close(); return }
            connection.connect(profile, secret)
            check(connection.connected) { "SSH disconnected" }
            stage = LocalPortForwardFailure.LISTENER_FAILED
            val exited = CountDownLatch(1)
            val thread = Thread({
                try {
                    connection.listen(config, socket)
                } catch (_: Exception) {
                    // Deliberately discard exception text: no callback/token diagnostics.
                } finally {
                    exited.countDown()
                }
            }, "pocketdev-local-forward").apply { isDaemon = true }
            synchronized(lock) {
                if (closed) return
                listenerThread = thread
                thread.start()
                publish(LocalPortForwardState.Active(config))
            }
            // Poll also detects idle transport loss (without a new browser connection).
            while (!closed && !exited.await(250, TimeUnit.MILLISECONDS)) {
                if (!connection.connected) {
                    failure = LocalPortForwardFailure.SSH_DISCONNECTED
                    return
                }
            }
            if (!closed) failure = if (connection.connected) LocalPortForwardFailure.LISTENER_FAILED
                else LocalPortForwardFailure.SSH_DISCONNECTED
        } catch (_: BindException) {
            failure = LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE
        } catch (_: Exception) {
            failure = stage
        } finally {
            finish(failure)
        }
    }

    override fun close() = finish(null)

    fun fail(failure: LocalPortForwardFailure) = finish(failure)

    private fun finish(failure: LocalPortForwardFailure?) {
        val resources = synchronized(lock) {
            if (closed) return
            closed = true
            Triple(listener, listenerThread, transport)
        }
        // Closing the socket and interrupting handles SSHJ's blocking accept loop, including
        // stop before listen() registers its running thread. SSH close retires accepted channels.
        runCatching { resources.first?.close() }
        resources.second?.interrupt()
        runCatching { resources.third?.close() }
        if (resources.second !== Thread.currentThread()) runCatching { resources.second?.join(1_000) }
        synchronized(lock) {
            listener = null
            listenerThread = null
            transport = null
            publish(if (failure == null) LocalPortForwardState.Stopped else LocalPortForwardState.Failed(config, failure))
        }
    }

    /** SSHJ's stream monitor can otherwise retain accepted phone sockets briefly after SSH close. */
    private class OwnedListener : ServerSocket() {
        private val peers = mutableListOf<Socket>()

        override fun accept(): Socket {
            val peer = super.accept()
            synchronized(peers) {
                if (isClosed) peer.close()
                else {
                    peers.removeAll { it.isClosed }
                    peers.add(peer)
                }
            }
            return peer
        }

        override fun close() {
            super.close()
            val closing = synchronized(peers) { peers.toList().also { peers.clear() } }
            closing.forEach { runCatching { it.close() } }
        }
    }

    private fun publish(value: LocalPortForwardState) {
        state = value
        runCatching { onState(state) }
    }
}
