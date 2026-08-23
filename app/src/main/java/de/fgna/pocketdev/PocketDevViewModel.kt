package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.data.ProjectConfigRepository
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.project.ProjectAction
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.project.ProjectConfig
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

data class ProjectEditorState(
    val name: String = "",
    val remotePath: String = "",
    val testCommand: String = "./gradlew test",
    val buildCommand: String = "./gradlew assembleDebug",
)

data class PocketDevState(
    val profile: SshProfile? = null,
    val hasStoredSecret: Boolean = false,
    val editorOpen: Boolean = false,
    val editor: ProfileEditorState = ProfileEditorState(),
    val project: ProjectConfig? = null,
    val projectEditorOpen: Boolean = false,
    val projectEditor: ProjectEditorState = ProjectEditorState(),
    val pendingHostKeyFingerprint: String? = null,
    val command: CommandUiState = CommandUiState(),
)

class PocketDevViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = SshProfileRepository(application)
    private val projectRepository = ProjectConfigRepository(application)
    private val executor = SshjCommandExecutor()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<PocketDevState> = _state.asStateFlow()

    init {
        persistCommandState(_state.value.command)
    }

    fun openEditor() {
        val current = _state.value.profile
        updateState {
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

    fun closeEditor() = updateState { it.copy(editorOpen = false) }

    fun updateEditor(transform: (ProfileEditorState) -> ProfileEditorState) =
        updateState { it.copy(editor = transform(it.editor)) }

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
        updateState {
            it.copy(
                profile = profile,
                hasStoredSecret = !repository.loadSecret().isNullOrBlank(),
                editorOpen = false,
                editor = editor.copy(secret = ""),
            )
        }
    }

    fun openProjectEditor() {
        val current = _state.value.project
        updateState {
            it.copy(
                projectEditorOpen = true,
                projectEditor = if (current == null) ProjectEditorState() else ProjectEditorState(
                    name = current.name,
                    remotePath = current.remotePath,
                    testCommand = current.testCommand,
                    buildCommand = current.buildCommand,
                ),
            )
        }
    }

    fun closeProjectEditor() = updateState { it.copy(projectEditorOpen = false) }

    fun updateProjectEditor(transform: (ProjectEditorState) -> ProjectEditorState) =
        updateState { it.copy(projectEditor = transform(it.projectEditor)) }

    fun saveProjectEditor() {
        val editor = _state.value.projectEditor
        val project = ProjectConfig(
            name = editor.name.trim(),
            remotePath = editor.remotePath.trim(),
            testCommand = editor.testCommand.trim(),
            buildCommand = editor.buildCommand.trim(),
        )
        projectRepository.save(project)
        updateState { it.copy(project = project, projectEditorOpen = false) }
    }

    fun prepareProjectAction(action: ProjectAction): Boolean {
        val project = _state.value.project ?: return false
        val command = runCatching { ProjectCommandBuilder.command(project, action) }
            .getOrElse { error ->
                updateState {
                    it.copy(command = it.command.copy(connectionError = error.message ?: "Project action is not configured."))
                }
                return false
            }
        setCommand(command)
        return true
    }

    fun trustPendingHostKey() {
        val fingerprint = _state.value.pendingHostKeyFingerprint ?: return
        val profile = _state.value.profile ?: return
        val trusted = profile.copy(hostKeySha256 = fingerprint)
        repository.save(trusted, null)
        updateState { it.copy(profile = trusted, pendingHostKeyFingerprint = null) }
        runCommand()
    }

    fun rejectPendingHostKey() = updateState { it.copy(pendingHostKeyFingerprint = null) }

    fun setCommand(command: String) = updateState {
        it.copy(command = it.command.copy(command = command))
    }

    fun runCommand() {
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) {
            updateState { it.copy(command = it.command.copy(connectionError = "No authentication secret is stored.")) }
            return
        }
        val commandText = _state.value.command.command.trim()
        if (commandText.isEmpty()) return

        updateState {
            it.copy(command = it.command.copy(running = true, stdout = "", stderr = "", exitCode = null, connectionError = null))
        }

        viewModelScope.launch {
            executor.execute(profile, secret, commandText).collect { event ->
                if (event is CommandEvent.HostKeyTrustRequired) {
                    updateState {
                        it.copy(
                            pendingHostKeyFingerprint = event.fingerprint,
                            command = it.command.copy(running = false),
                        )
                    }
                } else {
                    updateState { state ->
                        state.copy(command = CommandStateReducer.reduce(state.command, event))
                    }
                }
            }
        }
    }

    private fun updateState(transform: (PocketDevState) -> PocketDevState) {
        _state.update(transform)
        persistCommandState(_state.value.command)
    }

    private fun persistCommandState(command: CommandUiState) {
        savedStateHandle[KEY_COMMAND] = command.command
        savedStateHandle[KEY_RUNNING] = command.running
        savedStateHandle[KEY_STDOUT] = command.stdout
        savedStateHandle[KEY_STDERR] = command.stderr
        savedStateHandle[KEY_EXIT_CODE] = command.exitCode
        savedStateHandle[KEY_CONNECTION_ERROR] = command.connectionError
    }

    private fun restoreCommandState(): CommandUiState {
        val wasRunning = savedStateHandle.get<Boolean>(KEY_RUNNING) == true
        return CommandUiState(
            command = savedStateHandle.get<String>(KEY_COMMAND) ?: "pwd",
            running = false,
            stdout = savedStateHandle.get<String>(KEY_STDOUT).orEmpty(),
            stderr = savedStateHandle.get<String>(KEY_STDERR).orEmpty(),
            exitCode = savedStateHandle.get<Int>(KEY_EXIT_CODE),
            connectionError = if (wasRunning) {
                "Command was interrupted because PocketDev was stopped while in the background. Run it again to continue."
            } else {
                savedStateHandle.get<String>(KEY_CONNECTION_ERROR)
            },
        )
    }

    private fun loadInitialState(): PocketDevState {
        val stored = repository.load()
        return PocketDevState(
            profile = stored?.profile,
            hasStoredSecret = stored?.hasSecret == true,
            project = projectRepository.load(),
            command = restoreCommandState(),
        )
    }

    private companion object {
        const val KEY_COMMAND = "command.text"
        const val KEY_RUNNING = "command.running"
        const val KEY_STDOUT = "command.stdout"
        const val KEY_STDERR = "command.stderr"
        const val KEY_EXIT_CODE = "command.exitCode"
        const val KEY_CONNECTION_ERROR = "command.connectionError"
    }
}
