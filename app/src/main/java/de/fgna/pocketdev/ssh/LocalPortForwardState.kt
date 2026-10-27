package de.fgna.pocketdev.ssh

/**
 * UI/service-visible lifecycle for the one explicit local forwarding session.
 *
 * Failures are deliberately typed instead of carrying raw exception messages so
 * callback URLs, query strings, credentials, or other transport details cannot
 * accidentally cross into logs or UI state.
 */
sealed interface LocalPortForwardState {
    data object Stopped : LocalPortForwardState

    data class Starting(
        val config: LocalPortForwardConfig,
    ) : LocalPortForwardState

    data class Active(
        val config: LocalPortForwardConfig,
    ) : LocalPortForwardState

    data class Failed(
        val config: LocalPortForwardConfig,
        val failure: LocalPortForwardFailure,
    ) : LocalPortForwardState
}

enum class LocalPortForwardFailure {
    LOCAL_PORT_UNAVAILABLE,
    SSH_CONNECTION_FAILED,
    SSH_DISCONNECTED,
    LISTENER_FAILED,
}

sealed interface LocalPortForwardEvent {
    data class StartRequested(
        val config: LocalPortForwardConfig,
    ) : LocalPortForwardEvent

    data object ListenerStarted : LocalPortForwardEvent

    data class StartFailed(
        val failure: LocalPortForwardFailure,
    ) : LocalPortForwardEvent

    data object StopRequested : LocalPortForwardEvent
    data object SessionDisconnected : LocalPortForwardEvent
}

/**
 * Pure, idempotent lifecycle reducer. Resource ownership stays in the future
 * tunnel-session implementation; this contract prevents UI/service layers from
 * inventing impossible active states.
 */
fun reduceLocalPortForwardState(
    state: LocalPortForwardState,
    event: LocalPortForwardEvent,
): LocalPortForwardState = when {
    event is LocalPortForwardEvent.StopRequested -> LocalPortForwardState.Stopped
    event is LocalPortForwardEvent.StartRequested &&
        (state is LocalPortForwardState.Stopped || state is LocalPortForwardState.Failed) ->
        LocalPortForwardState.Starting(event.config)

    state is LocalPortForwardState.Starting &&
        event is LocalPortForwardEvent.ListenerStarted ->
        LocalPortForwardState.Active(state.config)

    (state is LocalPortForwardState.Starting || state is LocalPortForwardState.Active) &&
        event is LocalPortForwardEvent.StartFailed -> {
        val config = when (state) {
            is LocalPortForwardState.Starting -> state.config
            is LocalPortForwardState.Active -> state.config
            else -> error("unreachable")
        }
        LocalPortForwardState.Failed(config, event.failure)
    }

    state is LocalPortForwardState.Active &&
        event is LocalPortForwardEvent.SessionDisconnected ->
        LocalPortForwardState.Failed(
            config = state.config,
            failure = LocalPortForwardFailure.SSH_DISCONNECTED,
        )

    else -> state
}
