package de.fgna.pocketdev.ssh

import com.hierynomus.sshj.key.KeyAlgorithms
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.net.Inet4Address
import java.net.InetAddress
import java.security.PublicKey

/** Shared command/tunnel trust and authentication boundary. Discovery never authenticates. */
internal object SshConnectionFactory {
    fun connect(
        profile: SshProfile,
        onClient: (SSHClient) -> Unit = {},
        onDiscoveredFingerprint: (String) -> Unit = {},
        keepAlive: Boolean = false,
        clientFactory: () -> SSHClient = { createClient(keepAlive) },
    ): SSHClient {
        val targets = InetAddress.getAllByName(profile.host).toList()
            .sortedByDescending { it is Inet4Address }.mapNotNull { it.hostAddress }.distinct()
            .ifEmpty { listOf(profile.host) }
        val failures = mutableListOf<String>()
        for (target in targets) {
            val candidate = clientFactory()
            try {
                // Register ownership before any blocking network operation, including connect.
                onClient(candidate)
                if (profile.hostKeySha256.isBlank()) {
                    candidate.addHostKeyVerifier(object : HostKeyVerifier {
                        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                            onDiscoveredFingerprint(SecurityUtils.getFingerprint(key))
                            return true
                        }
                        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
                    })
                } else {
                    candidate.addHostKeyVerifier(profile.hostKeySha256.trim())
                }
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

    internal fun createClient(keepAlive: Boolean = false): SSHClient =
        SSHClient(DefaultSecurityProviderConfig().apply {
            setKeyAlgorithms(listOf(KeyAlgorithms.RSASHA512(), KeyAlgorithms.RSASHA256()))
            if (keepAlive) setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE)
        }).also { client ->
            // SSHJ starts this thread in onConnect only when already enabled.
            if (keepAlive) client.connection.keepAlive.keepAliveInterval = 15
        }

    fun authenticate(client: SSHClient, profile: SshProfile, secret: String) {
        require(profile.hostKeySha256.isNotBlank()) { "Trust this SSH host in PocketDev first." }
        when (profile.authMode) {
            AuthMode.PASSWORD -> client.authPassword(profile.username, secret)
            // SSHJ accepts key contents directly; no exported or temporary private-key file.
            AuthMode.PRIVATE_KEY -> client.authPublickey(profile.username, client.loadKeys(secret, null, null))
        }
    }
}
