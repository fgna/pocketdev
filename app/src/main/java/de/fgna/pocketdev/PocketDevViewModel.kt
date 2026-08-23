package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.CommandEvent
import de.fgna.pocketdev.ssh.CommandStateReducer
import de.fgna.pocketdev.ssh.CommandUiState
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
    val authMode: AuthMode = AuthMode.PASSWORD,
    val secret: String = "",
)

data class PocketDevState(
    val profile: SshProfile? = null,
    val hasStoredSecret: Boolean = false,
    val editorOpen: Boolean = false,
    val editor: ProfileEditorState = ProfileEditorState(),
    val pendingHostKeyFingerprint: String? = null,
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
        val existingFingerprint = _state.value.profile
            ?.takeIf { it.host == editor.host.trim() && it.port == (editor.port.toIntOrNull() ?: 22) }
            ?.hostKeySha256.orEmpty()
        val profile = SshProfile(
            host = editor.host.trim(),
            port = editor.port.toIntOrNull() ?: 22,
            username = editor.username.trim(),
            hostKeySha256 = existingFingerprint,
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

    fun trustPendingHostKey() {
        val fingerprint = _state.value.pendingHostKeyFingerprint ?: return
        val profile = _state.value.profile ?: return
        val trusted = profile.copy(hostKeySha256 = fingerprint)
        repository.save(trusted, null)
        _state.update { it.copy(profile = trusted, pendingHostKeyFingerprint = null) }
        runCommand()
    }

    fun rejectPendingHostKey() = _state.update { it.copy(pendingHostKeyFingerprint = null) }

    fun setCommand(command: String) = _state.update {
        it.copy(command = it.command.copy(command = command))
    }

    fun runCommand() {
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) {
            _state.update { it.copy(command = it.command.copy(connectionError = "No authentication secret is stored.")) }
            return
        }
        val commandText = _state.value.command.command.trim()
        if (commandText.isEmpty()) return

        _state.update {
            it.copy(command = it.command.copy(running = true, stdout = "", stderr = "", exitCode = null, connectionError = null))
        }

        viewModelScope.launch {
            executor.execute(profile, secret, commandText).collect { event ->
                if (event is CommandEvent.HostKeyTrustRequired) {
                    _state.update { it.copy(pendingHostKeyFingerprint = event.fingerprint, command = it.command.copy(running = false)) }
                } else {
                    _state.update { state -> state.copy(command = CommandStateReducer.reduce(state.command, event)) }
                }
            }
        }
    }

    private fun loadInitialState(): PocketDevState {
        val stored = repository.load()
        return PocketDevState(profile = stored?.profile, hasStoredSecret = stored?.hasSecret == true)
    }
}
