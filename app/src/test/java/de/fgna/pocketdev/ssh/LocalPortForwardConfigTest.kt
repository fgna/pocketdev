package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalPortForwardConfigTest {
    @Test
    fun localBindIsAlwaysIpv4Loopback() {
        val config = LocalPortForwardConfig(
            localPort = 14_536,
            remoteHost = "127.0.0.1",
            remotePort = 1_454,
        )

        assertEquals("127.0.0.1", config.localHost)
    }

    @Test
    fun validPortsAndRemoteHostArePreserved() {
        val config = LocalPortForwardConfig(
            localPort = 1,
            remoteHost = "localhost",
            remotePort = 65_535,
        )

        assertEquals(1, config.localPort)
        assertEquals("localhost", config.remoteHost)
        assertEquals(65_535, config.remotePort)
    }

    @Test
    fun rejectsInvalidLocalPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(0, "localhost", 1_454)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(65_536, "localhost", 1_454)
        }
    }

    @Test
    fun rejectsInvalidRemotePorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(14_536, "localhost", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(14_536, "localhost", 65_536)
        }
    }

    @Test
    fun rejectsBlankOrPaddedRemoteHosts() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(14_536, "   ", 1_454)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPortForwardConfig(14_536, " localhost ", 1_454)
        }
    }
}
