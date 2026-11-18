package de.fgna.pocketdev.ssh

/**
 * Validated configuration for the one explicit local port-forwarding tunnel.
 *
 * The local bind address is deliberately not configurable. Keeping it fixed to
 * IPv4 loopback prevents accidental exposure on Wi-Fi, mobile, VPN, or other
 * interfaces while a browser-auth callback is active.
 */
data class LocalPortForwardConfig(
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
) {
    init {
        require(localPort in VALID_PORTS) { "Local port must be in 1..65535" }
        require(remotePort in VALID_PORTS) { "Remote port must be in 1..65535" }
        require(remoteHost.isNotBlank()) { "Remote host must not be blank" }
        require(remoteHost == remoteHost.trim()) {
            "Remote host must not contain leading or trailing whitespace"
        }
    }

    val localHost: String
        get() = IPV4_LOOPBACK

    companion object {
        const val IPV4_LOOPBACK: String = "127.0.0.1"
        private val VALID_PORTS = 1..65_535
    }
}
