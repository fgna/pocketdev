package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPortForwardStateTest {
    private val config = LocalPortForwardConfig(
        localPort = 14_555,
        remoteHost = "127.0.0.1",
        remotePort = 14_555,
    )

    @Test
    fun startBecomesActiveOnlyAfterListenerConfirmation() {
        val starting = reduceLocalPortForwardState(
            LocalPortForwardState.Stopped,
            LocalPortForwardEvent.StartRequested(config),
        )
        assertEquals(LocalPortForwardState.Starting(config), starting)

        val active = reduceLocalPortForwardState(
            starting,
            LocalPortForwardEvent.ListenerStarted,
        )
        assertEquals(LocalPortForwardState.Active(config), active)
    }

    @Test
    fun listenerConfirmationCannotActivateStoppedTunnel() {
        val result = reduceLocalPortForwardState(
            LocalPortForwardState.Stopped,
            LocalPortForwardEvent.ListenerStarted,
        )

        assertEquals(LocalPortForwardState.Stopped, result)
    }

    @Test
    fun bindFailureIsTypedAndPreservesValidatedConfig() {
        val starting = LocalPortForwardState.Starting(config)
        val result = reduceLocalPortForwardState(
            starting,
            LocalPortForwardEvent.StartFailed(
                LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE,
            ),
        )

        assertEquals(
            LocalPortForwardState.Failed(
                config,
                LocalPortForwardFailure.LOCAL_PORT_UNAVAILABLE,
            ),
            result,
        )
    }

    @Test
    fun disconnectFailsAnActiveTunnelWithoutRawErrorText() {
        val result = reduceLocalPortForwardState(
            LocalPortForwardState.Active(config),
            LocalPortForwardEvent.SessionDisconnected,
        )

        assertEquals(
            LocalPortForwardState.Failed(
                config,
                LocalPortForwardFailure.SSH_DISCONNECTED,
            ),
            result,
        )
    }

    @Test
    fun stopIsIdempotentFromEveryState() {
        val states = listOf(
            LocalPortForwardState.Stopped,
            LocalPortForwardState.Starting(config),
            LocalPortForwardState.Active(config),
            LocalPortForwardState.Failed(
                config,
                LocalPortForwardFailure.LISTENER_FAILED,
            ),
        )

        states.forEach { state ->
            assertEquals(
                LocalPortForwardState.Stopped,
                reduceLocalPortForwardState(
                    state,
                    LocalPortForwardEvent.StopRequested,
                ),
            )
        }
    }

    @Test
    fun duplicateStartDoesNotReplaceOwnedActiveSession() {
        val otherConfig = LocalPortForwardConfig(
            localPort = 14_556,
            remoteHost = "localhost",
            remotePort = 14_556,
        )
        val active = LocalPortForwardState.Active(config)

        val result = reduceLocalPortForwardState(
            active,
            LocalPortForwardEvent.StartRequested(otherConfig),
        )

        assertEquals(active, result)
    }
}
