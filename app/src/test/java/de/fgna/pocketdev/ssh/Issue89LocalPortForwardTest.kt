package de.fgna.pocketdev.ssh

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real loopback sockets, deterministic transport failures; no Android device or SSH secrets. */
class Issue89LocalPortForwardTest {
    private val profile = SshProfile("server.invalid", username = "user", hostKeySha256 = "SHA256:pinned")

    private class Transport : LocalForwardTransport {
        val closes = AtomicInteger()
        val enteredConnect = CountDownLatch(1)
        val allowConnect = CountDownLatch(1)
        val listening = CountDownLatch(1)
        val failListener = CountDownLatch(1)
        var blockConnect = false
        var connectError = false
        var listenerError = false
        var closeAccepted = true
        @Volatile override var connected = false
        @Volatile var socket: ServerSocket? = null
        @Volatile var accepted: Socket? = null
        @Volatile var destination: LocalPortForwardConfig? = null

        override fun connect(profile: SshProfile, secret: String) {
            enteredConnect.countDown()
            if (blockConnect) check(allowConnect.await(3, TimeUnit.SECONDS))
            if (connectError) error("private-token-that-must-not-escape")
            connected = closes.get() == 0
        }

        override fun listen(config: LocalPortForwardConfig, socket: ServerSocket) {
            this.socket = socket
            destination = config
            listening.countDown()
            if (listenerError) {
                check(failListener.await(3, TimeUnit.SECONDS))
                error("http://127.0.0.1/callback?code=private-token-that-must-not-escape")
            }
            // Echo opaque bytes at the transport seam to verify the bound socket is handed over.
            socket.accept().use { peer ->
                accepted = peer
                val input = peer.getInputStream()
                val output = peer.getOutputStream()
                while (true) {
                    val byte = input.read()
                    if (byte < 0) break
                    output.write(byte)
                    output.flush()
                }
            }
            // A browser connection ending must not end the tunnel's listener.
            while (!socket.isClosed) socket.accept().close()
        }

        override fun close() {
            closes.incrementAndGet()
            connected = false
            allowConnect.countDown()
            failListener.countDown()
            if (closeAccepted) accepted?.close()
        }
    }

    private class Running(val session: LocalPortForwardSession, val worker: Thread) : AutoCloseable {
        override fun close() { session.close(); worker.join(3_000); assertFalse(worker.isAlive) }
    }

    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun config(port: Int = freePort()) = LocalPortForwardConfig(port, "destination.internal", 1455)
    private fun waitFor(check: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (!check() && System.nanoTime() < until) Thread.sleep(10)
        assertTrue("Timed out waiting for tunnel state", check())
    }
    private fun run(
        config: LocalPortForwardConfig,
        transport: Transport,
        states: MutableList<LocalPortForwardState>,
    ): Running {
        val session = LocalPortForwardSession(config, { transport }, { states.add(it) })
        val worker = Thread { session.run(profile, "protected-secret") }.apply { isDaemon = true; start() }
        return Running(session, worker)
    }
    private fun assertPortReleased(port: Int) {
        ServerSocket().use {
            it.reuseAddress = true
            it.bind(java.net.InetSocketAddress("127.0.0.1", port))
            assertEquals(port, it.localPort)
        }
    }

    @Test fun validatesPortsAndRejectsUrlsOrControlCharacters() {
        for (port in listOf(-1, 0, 65536)) {
            assertThrows(IllegalArgumentException::class.java) { LocalPortForwardConfig(port, "localhost", 1455) }
            assertThrows(IllegalArgumentException::class.java) { LocalPortForwardConfig(1455, "localhost", port) }
        }
        for (host in listOf("", " host", "host\nname", "host\u0000", "https://host/callback?token=x", "user@host")) {
            assertThrows(IllegalArgumentException::class.java) { LocalPortForwardConfig(1455, host, 1455) }
        }
        assertEquals("127.0.0.1", LocalPortForwardConfig(1, "::1", 65535).localHost)
    }

    @Test fun rejectsUnverifiedHostAndMissingCredentialsBeforeAllocatingTransport() {
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        val c = config()
        var created = false
        val session = LocalPortForwardSession(c, { created = true; Transport() }, { states.add(it) })
        session.run(profile.copy(hostKeySha256 = ""), "secret")
        assertFalse(created)
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.SSH_HOST_UNVERIFIED), states.last())
        assertPortReleased(c.localPort)
        val other = LocalPortForwardSession(c, { created = true; Transport() }, { states.add(it) })
        other.run(profile, "")
        assertFalse(created)
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.SSH_CREDENTIALS_MISSING), states.last())
    }

    @Test fun realBindCollisionFailsWithoutFallbackOrSshConnection() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupied ->
            val c = config(occupied.localPort)
            val states = CopyOnWriteArrayList<LocalPortForwardState>()
            var connected = false
            LocalPortForwardSession(c, { connected = true; Transport() }, { states.add(it) }).run(profile, "secret")
            assertFalse(connected)
            assertEquals(listOf(LocalPortForwardState.Starting(c),
                LocalPortForwardState.Failed(c, LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE)), states.toList())
            assertFalse(occupied.isClosed)
        }
    }

    @Test fun bindsOnlyLoopbackAndHandsOpaqueTrafficAndDestinationToTransport() {
        val c = config()
        val t = Transport()
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use {
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            waitFor { states.lastOrNull() is LocalPortForwardState.Active }
            assertEquals("127.0.0.1", t.socket!!.inetAddress.hostAddress)
            assertFalse(t.socket!!.inetAddress.isAnyLocalAddress)
            assertEquals(c, t.destination)
            Socket("127.0.0.1", c.localPort).use { browser ->
                browser.soTimeout = 2_000
                val data = byteArrayOf(0, 1, 127, -1)
                browser.getOutputStream().write(data)
                val received = ByteArray(data.size)
                java.io.DataInputStream(browser.getInputStream()).readFully(received)
                assertArrayEquals(data, received)
            }
            assertEquals(LocalPortForwardState.Active(c), states.last())
        }
        assertEquals(LocalPortForwardState.Stopped, states.last())
        assertTrue(t.socket!!.isClosed)
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun stopImmediatelyClosesAcceptedBrowserSocketsAsWellAsTheListener() {
        val c = config()
        val t = Transport().apply { closeAccepted = false }
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use { running ->
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            Socket("127.0.0.1", c.localPort).use { browser ->
                browser.soTimeout = 2_000
                browser.getOutputStream().write(42)
                assertEquals(42, browser.getInputStream().read())
                running.session.close()
                assertTrue(t.accepted!!.isClosed)
                assertEquals(-1, browser.getInputStream().read())
            }
        }
        assertEquals(LocalPortForwardState.Stopped, states.last())
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun connectionFailureClosesSshAndSocketAndDoesNotExposeExceptionText() {
        val c = config()
        val t = Transport().apply { connectError = true }
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use { it.worker.join(3_000) }
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.SSH_CONNECTION_FAILED), states.last())
        assertFalse(states.toString().contains("private-token"))
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun listenerFailureClosesEverythingAndDiscardsCallbackDetails() {
        val c = config()
        val t = Transport().apply { listenerError = true }
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use {
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            t.failListener.countDown()
            waitFor { states.lastOrNull() is LocalPortForwardState.Failed }
        }
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.LISTENER_FAILED), states.last())
        assertFalse(states.toString().contains("private-token"))
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun detectsIdleDisconnectionWithoutAnotherBrowserRequest() {
        val c = config()
        val t = Transport()
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use {
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            t.connected = false
            waitFor { states.lastOrNull() is LocalPortForwardState.Failed }
        }
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.SSH_DISCONNECTED), states.last())
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun stopDuringConnectCannotPublishActiveOrLeakResources() {
        val c = config()
        val t = Transport().apply { blockConnect = true }
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use {
            assertTrue(t.enteredConnect.await(3, TimeUnit.SECONDS))
            it.session.close()
            it.session.close()
        }
        assertEquals(LocalPortForwardState.Stopped, states.last())
        assertFalse(states.any { it is LocalPortForwardState.Active })
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun stopBeforeWorkerStartsAndRepeatedRunAreIdempotent() {
        val c = config()
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        var created = 0
        val session = LocalPortForwardSession(c, { created++; Transport() }, { states.add(it) })
        session.close()
        session.run(profile, "secret")
        session.close()
        assertEquals(0, created)
        assertEquals(listOf(LocalPortForwardState.Stopped), states.toList())
        assertPortReleased(c.localPort)
    }

    @Test fun restartOnSamePortUsesFreshOwnershipAndOldStopCannotCloseReplacement() {
        val c = config()
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        val old = run(c, Transport(), states)
        waitFor { states.lastOrNull() is LocalPortForwardState.Active }
        old.close()
        val t = Transport()
        run(c, t, states).use {
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            waitFor { states.lastOrNull() is LocalPortForwardState.Active }
            old.session.close()
            assertEquals(LocalPortForwardState.Active(c), states.last())
            assertFalse(t.socket!!.isClosed)
            // Duplicate run cannot bind again or replace the live listener.
            it.session.run(profile, "secret")
            assertEquals(0, t.closes.get())
        }
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }
    @Test fun backgroundShutdownRetiresSocketAndTransportWithAnExplicitFailure() {
        val c = config()
        val t = Transport()
        val states = CopyOnWriteArrayList<LocalPortForwardState>()
        run(c, t, states).use {
            assertTrue(t.listening.await(3, TimeUnit.SECONDS))
            it.session.fail(LocalPortForwardFailure.BACKGROUND_STOPPED)
        }
        assertEquals(LocalPortForwardState.Failed(c, LocalPortForwardFailure.BACKGROUND_STOPPED), states.last())
        assertEquals(1, t.closes.get())
        assertPortReleased(c.localPort)
    }

    @Test fun eachFailureHasAnActionableStoppedMessage() {
        for (failure in LocalPortForwardFailure.entries) {
            val text = de.fgna.pocketdev.localPortForwardStatus(LocalPortForwardState.Failed(config(), failure))
            assertTrue(text, text.startsWith("Tunnel stopped"))
            assertTrue(text, listOf("stop the", "choose", "trust", "save", "check", "restart", "return")
                .any { text.contains(it, ignoreCase = true) })
        }
    }

    @Test fun tunnelEnablesResponseCheckedKeepaliveBeforeConnectAndCommandsKeepTheirDefaults() {
        SshConnectionFactory.createClient(keepAlive = true).use {
            assertEquals(15, it.connection.keepAlive.keepAliveInterval)
            assertTrue(it.connection.keepAlive is net.schmizz.keepalive.KeepAliveRunner)
        }
        SshConnectionFactory.createClient().use {
            assertEquals(0, it.connection.keepAlive.keepAliveInterval)
        }
    }

    private class AuthClient : net.schmizz.sshj.SSHClient() {
        var passwords = 0
        var keys = 0
        override fun authPassword(username: String, password: String) {
            assertEquals("user", username)
            assertEquals("protected-secret", password)
            passwords++
        }
        override fun authPublickey(username: String, vararg providers: net.schmizz.sshj.userauth.keyprovider.KeyProvider) {
            assertEquals("user", username)
            // Forces SSHJ to actually parse the in-memory key, without SSH or a key file.
            assertNotNull(providers.single().private)
            keys++
        }
    }

    @Test fun sharedAuthenticationRefusesUnpinnedHostAndSupportsPasswordAndInMemoryPrivateKey() {
        AuthClient().use { client ->
            assertThrows(IllegalArgumentException::class.java) {
                SshConnectionFactory.authenticate(client, profile.copy(hostKeySha256 = ""), "protected-secret")
            }
            assertEquals(0, client.passwords)
            SshConnectionFactory.authenticate(client, profile, "protected-secret")
            assertEquals(1, client.passwords)
            val key = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val encoded = java.util.Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(key.private.encoded)
            val pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n"
            SshConnectionFactory.authenticate(client, profile.copy(authMode = AuthMode.PRIVATE_KEY), pem)
            assertEquals(1, client.keys)
        }
    }

}
