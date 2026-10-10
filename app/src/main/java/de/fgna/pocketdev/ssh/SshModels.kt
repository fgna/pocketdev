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
    val codexAction: String? = null,
    val codexNeedsInput: Boolean = false,
    val startedAtMs: Long? = null,
    val lastActivityAtMs: Long? = null,
    val pendingCodexLine: String = "",
    val remoteLogPrefix: String? = null,
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
        CommandEvent.Connecting -> current.copy(running = true, connectionError = null, awaitingSudoPassword = false, startedAtMs = System.currentTimeMillis(), lastActivityAtMs = null, codexAction = if (CodexProgress.enabled(current.command)) "Connecting" else null, codexNeedsInput = false, pendingCodexLine = "", remoteLogPrefix = null)
        CommandEvent.Connected -> current.copy(running = true)
        CommandEvent.SudoPasswordRequired -> current.copy(running = true, awaitingSudoPassword = true)
        CommandEvent.SudoPasswordSubmitted -> current.copy(running = true, awaitingSudoPassword = false)
        is CommandEvent.HostKeyTrustRequired -> current.copy(running = false, awaitingSudoPassword = false)
        is CommandEvent.Output -> appendOutput(current, event)
        is CommandEvent.Completed -> current.copy(running = false, exitCode = event.exitCode, awaitingSudoPassword = false)
        is CommandEvent.ConnectionFailed -> current.copy(running = false, connectionError = event.message, awaitingSudoPassword = false)
    }
    private const val MAX_VISIBLE_OUTPUT = 128 * 1024
    private const val MAX_PENDING_LINE = 64 * 1024
    private fun appendOutput(current: CommandUiState, event: CommandEvent): CommandUiState {
        val now = System.currentTimeMillis()
        val prefix = "POCKETDEV_JOB_LOG:"
        val text = event.text
        val updated = when (event.stream) {
            OutputStreamKind.STDOUT -> current.copy(stdout = (current.stdout + text).takeLast(MAX_VISIBLE_OUTPUT))
            OutputStreamKind.STDERR -> current.copy(stderr = (current.stderr + text).takeLast(MAX_VISIBLE_OUTPUT))
        }.copy(lastActivityAtMs = now)
        if (event.stream != OutputStreamKind.STDOUT || !CodexProgress.enabled(current.command)) return updated
        val merged = current.pendingCodexLine + text
        val lines = merged.split('\n')
        val complete = lines.dropLast(1)
        val pending = lines.last().takeLast(MAX_PENDING_LINE)
        var action = updated.codexAction
        var input = updated.codexNeedsInput
        var log = updated.remoteLogPrefix
        complete.forEach { line ->
            if (line.startsWith(prefix)) log = line.removePrefix(prefix).trim()
            CodexProgress.parse(line.trim())?.let { update ->
                action = update.action
                input = update.needsInput
            }
        }
        return updated.copy(codexAction = action, codexNeedsInput = input, pendingCodexLine = pending, remoteLogPrefix = log)
    }
}
