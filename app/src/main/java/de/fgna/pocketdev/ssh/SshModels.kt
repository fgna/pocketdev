package de.fgna.pocketdev.ssh

data class SshProfile(
    val host: String,
    val port: Int = 22,
    val username: String,
    val hostKeySha256: String,
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
