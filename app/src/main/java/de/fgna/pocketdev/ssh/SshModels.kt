package de.fgna.pocketdev.ssh

data class SshProfile(
    val host: String,
    val port: Int = 22,
    val username: String,
    val hostKeySha256: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
)

enum class AuthMode {
    PASSWORD,
    PRIVATE_KEY,
}

enum class OutputStreamKind {
    STDOUT,
    STDERR,
}

sealed interface CommandEvent {
    data object Connecting : CommandEvent
    data object Connected : CommandEvent
    data object SudoPasswordRequired : CommandEvent
    data object SudoPasswordSubmitted : CommandEvent
    data class HostKeyTrustRequired(val fingerprint: String) : CommandEvent
    data class Output(val stream: OutputStreamKind, val text: String) : CommandEvent
    data class Completed(val exitCode: Int) : CommandEvent
    data class ConnectionFailed(val message: String) : CommandEvent
}

data class CommandUiState(
    val command: String = "pwd",
    val running: Boolean = false,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val connectionError: String? = null,
    val awaitingSudoPassword: Boolean = false,
) {
    val combinedOutput: String
        get() = buildString {
            append(stdout)
            if (stderr.isNotBlank()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append(stderr)
            }
        }

    val succeeded: Boolean
        get() = !running && connectionError == null && exitCode == 0
}

object CommandStateReducer {
    fun reduce(current: CommandUiState, event: CommandEvent): CommandUiState = when (event) {
        CommandEvent.Connecting -> current.copy(running = true, connectionError = null, awaitingSudoPassword = false)
        CommandEvent.Connected -> current.copy(running = true)
        CommandEvent.SudoPasswordRequired -> current.copy(running = true, awaitingSudoPassword = true)
        CommandEvent.SudoPasswordSubmitted -> current.copy(running = true, awaitingSudoPassword = false)
        is CommandEvent.HostKeyTrustRequired -> current.copy(running = false, awaitingSudoPassword = false)
        is CommandEvent.Output -> when (event.stream) {
            OutputStreamKind.STDOUT -> current.copy(stdout = current.stdout + event.text)
            OutputStreamKind.STDERR -> current.copy(stderr = current.stderr + event.text)
        }
        is CommandEvent.Completed -> current.copy(running = false, exitCode = event.exitCode, awaitingSudoPassword = false)
        is CommandEvent.ConnectionFailed -> current.copy(running = false, connectionError = event.message, awaitingSudoPassword = false)
    }
}
