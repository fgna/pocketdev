package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.CommandEvent
import de.fgna.pocketdev.ssh.CommandUiState
import de.fgna.pocketdev.ssh.OutputStreamKind
import de.fgna.pocketdev.ssh.SshProfile
import de.fgna.pocketdev.ssh.SshjCommandExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditorState(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val hostKeySha256: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val secret: String = "",
)

data class PocketDevState(
    val profile: SshProfile? = null,
    val hasStoredSecret: Boolean = false,
    val editorOpen: Boolean = false,
    val editor: ProfileEditorState = ProfileEditorState(),
    val command: CommandUiState = CommandUiState(),
)

class PocketDevViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SshProfileRepository(application)
    private val executor = SshjCommandExecutor()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<PocketDevState> = _state.asStateFlow()

    fun openEditor() {
        val current = _state.value.profile
        _state.update {
            it.copy(
                editorOpen = true,
                editor = if (current == null) ProfileEditorState() else ProfileEditorState(
                    host = current.host,
                    port = current.port.toString(),
                    username = current.username,
                    hostKeySha256 = current.hostKeySha256,
                    authMode = current.authMode,
                ),
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editorOpen = false) }

    fun updateEditor(transform: (ProfileEditorState) -> ProfileEditorState) =
        _state.update { it.copy(editor = transform(it.editor)) }

    fun saveEditor() {
        val editor = _state.value.editor
        val port = editor.port.toIntOrNull() ?: 22
        val profile = SshProfile(
            host = editor.host.trim(),
            port = port,
            username = editor.username.trim(),
            hostKeySha256 = editor.hostKeySha256.trim(),
            authMode = editor.authMode,
        )
        repository.save(profile, editor.secret.ifBlank { null })
        _state.update {
            it.copy(
                profile = profile,
                hasStoredSecret = !repository.loadSecret().isNullOrBlank(),
                editorOpen = false,
                editor = editor.copy(secret = ""),
            )
        }
    }

    fun setCommand(command: String) = _state.update {
        it.copy(command = it.command.copy(command = command))
    }

    fun runCommand() {
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) {
            _state.update {
                it.copy(command = it.command.copy(connectionError = "No authentication secret is stored."))
            }
            return
        }
        val commandText = _state.value.command.command.trim()
        if (commandText.isEmpty()) return

        _state.update {
            it.copy(
                command = it.command.copy(
                    running = true,
                    stdout = "",
                    stderr = "",
                    exitCode = null,
                    connectionError = null,
                ),
            )
        }

        viewModelScope.launch {
            executor.execute(profile, secret, commandText).collect { event ->
                _state.update { state -> state.copy(command = reduce(state.command, event)) }
            }
        }
    }

    private fun reduce(current: CommandUiState, event: CommandEvent): CommandUiState = when (event) {
        CommandEvent.Connecting -> current.copy(running = true, connectionError = null)
        CommandEvent.Connected -> current.copy(running = true)
        is CommandEvent.Output -> when (event.stream) {
            OutputStreamKind.STDOUT -> current.copy(stdout = current.stdout + event.text)
            OutputStreamKind.STDERR -> current.copy(stderr = current.stderr + event.text)
        }
        is CommandEvent.Completed -> current.copy(running = false, exitCode = event.exitCode)
        is CommandEvent.ConnectionFailed -> current.copy(running = false, connectionError = event.message)
    }

    private fun loadInitialState(): PocketDevState {
        val stored = repository.load()
        return PocketDevState(profile = stored?.profile, hasStoredSecret = stored?.hasSecret == true)
    }
}
