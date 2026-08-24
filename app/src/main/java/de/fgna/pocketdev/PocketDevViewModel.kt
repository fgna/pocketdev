package de.fgna.pocketdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.fgna.pocketdev.artifact.ArtifactDownloadState
import de.fgna.pocketdev.artifact.SshArtifactRetriever
import de.fgna.pocketdev.data.ProjectConfigRepository
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.project.ProjectAction
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.project.ProjectProvisioning
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ssh.CommandEvent
import de.fgna.pocketdev.ssh.CommandStateReducer
import de.fgna.pocketdev.ssh.CommandUiState
import de.fgna.pocketdev.ssh.GitSshAgent
import de.fgna.pocketdev.ssh.OutputStreamKind
import de.fgna.pocketdev.ssh.SshProfile
import de.fgna.pocketdev.ssh.SshjCommandExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val githubRepository: String = "",
)

data class ProjectSessionState(
    val command: CommandUiState = CommandUiState(command = "pwd"),
    val artifact: ArtifactDownloadState = ArtifactDownloadState(),
)

data class ProjectCreateRequest(
    val projectId: String,
    val command: String,
)

enum class GitKeyStatus {
    UNKNOWN,
    CHECKING,
    LOCKED,
    READY,
    ERROR,
}

data class PocketDevState(
    val profile: SshProfile? = null,
    val hasStoredSecret: Boolean = false,
    val editorOpen: Boolean = false,
    val editor: ProfileEditorState = ProfileEditorState(),
    val projects: List<ProjectConfig> = emptyList(),
    val project: ProjectConfig? = null,
    val sessions: Map<String, ProjectSessionState> = emptyMap(),
    val projectEditorOpen: Boolean = false,
    val projectEditorId: String? = null,
    val projectEditor: ProjectEditorState = ProjectEditorState(),
    val pendingHostKeyFingerprint: String? = null,
    val pendingHostKeyProjectId: String? = null,
    val gitKeyStatus: GitKeyStatus = GitKeyStatus.UNKNOWN,
    val gitKeyMessage: String? = null,
    val gitKeyUnlockOpen: Boolean = false,
    val pendingProjectCreate: ProjectCreateRequest? = null,
) {
    val command: CommandUiState
        get() = project?.let { sessions[it.id]?.command } ?: CommandUiState(command = "pwd")

    val artifact: ArtifactDownloadState
        get() = project?.let { sessions[it.id]?.artifact } ?: ArtifactDownloadState()
}

class PocketDevViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = SshProfileRepository(application)
    private val projectRepository = ProjectConfigRepository(application)
    private val executor = SshjCommandExecutor()
    private val artifactRetriever = SshArtifactRetriever()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<PocketDevState> = _state.asStateFlow()

    init {
        refreshGitKeyStatus()
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
                gitKeyStatus = GitKeyStatus.UNKNOWN,
                gitKeyMessage = null,
            )
        }
        refreshGitKeyStatus()
    }

    fun openProjectEditor() {
        val current = _state.value.project
        if (current == null) {
            openNewProjectEditor()
            return
        }
        updateState {
            it.copy(
                projectEditorOpen = true,
                projectEditorId = current.id,
                projectEditor = ProjectEditorState(
                    name = current.name,
                    remotePath = current.remotePath,
                    testCommand = current.testCommand,
                    buildCommand = current.buildCommand,
                    githubRepository = current.githubRepository,
                ),
            )
        }
    }

    fun openNewProjectEditor() = updateState {
        it.copy(
            projectEditorOpen = true,
            projectEditorId = null,
            projectEditor = ProjectEditorState(),
        )
    }

    fun closeProjectEditor() = updateState { it.copy(projectEditorOpen = false) }

    fun updateProjectEditor(transform: (ProjectEditorState) -> ProjectEditorState) =
        updateState { it.copy(projectEditor = transform(it.projectEditor)) }

    fun saveProjectEditor() {
        val state = _state.value
        val editor = state.projectEditor
        val project = ProjectConfig(
            id = state.projectEditorId.orEmpty(),
            name = editor.name.trim(),
            remotePath = editor.remotePath.trim(),
            testCommand = editor.testCommand.trim(),
            buildCommand = editor.buildCommand.trim(),
            githubRepository = editor.githubRepository.trim(),
        )
        val collection = projectRepository.upsert(project, makeActive = true)
        val active = collection.activeProject
        updateState {
            it.copy(
                projects = collection.projects,
                project = active,
                sessions = ensureSessions(collection.projects, it.sessions),
                projectEditorOpen = false,
                projectEditorId = null,
            )
        }
    }

    fun selectProject(projectId: String) {
        if (_state.value.project?.id == projectId) return
        val collection = projectRepository.setActive(projectId)
        updateState {
            it.copy(
                projects = collection.projects,
                project = collection.activeProject,
                sessions = ensureSessions(collection.projects, it.sessions),
            )
        }
    }

    fun deleteCurrentProject() {
        val projectId = _state.value.project?.id ?: return
        val collection = projectRepository.delete(projectId)
        updateState {
            it.copy(
                projects = collection.projects,
                project = collection.activeProject,
                sessions = ensureSessions(collection.projects, it.sessions - projectId),
                projectEditorOpen = false,
                projectEditorId = null,
                pendingProjectCreate = it.pendingProjectCreate?.takeUnless { request -> request.projectId == projectId },
            )
        }
        clearPersistedSession(projectId)
    }

    fun prepareProjectAction(action: ProjectAction): Boolean {
        val project = _state.value.project ?: return false
        val command = runCatching { ProjectCommandBuilder.command(project, action) }
            .getOrElse { error ->
                updateSession(project.id) { session ->
                    session.copy(command = session.command.copy(connectionError = error.message ?: "Project action is not configured."))
                }
                return false
            }
        setCommand(command)
        return true
    }

    fun downloadLatestApk() {
        val profile = _state.value.profile ?: return
        val project = _state.value.project ?: return
        val projectId = project.id
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) {
            updateSession(projectId) { it.copy(artifact = it.artifact.copy(error = "No authentication secret is stored.")) }
            return
        }
        if (profile.hostKeySha256.isBlank()) {
            updateSession(projectId) { it.copy(artifact = it.artifact.copy(error = "Trust the SSH server before downloading artifacts.")) }
            return
        }

        updateSession(projectId) { it.copy(artifact = ArtifactDownloadState(downloading = true)) }
        viewModelScope.launch {
            runCatching {
                artifactRetriever.downloadLatestApk(
                    profile = profile,
                    secret = secret,
                    project = project,
                    destinationDir = File(getApplication<Application>().cacheDir, "artifacts/$projectId"),
                )
            }.onSuccess { downloaded ->
                updateSession(projectId) {
                    it.copy(
                        artifact = ArtifactDownloadState(
                            downloading = false,
                            remotePath = downloaded.remotePath,
                            localPath = downloaded.localFile.absolutePath,
                        ),
                    )
                }
            }.onFailure { error ->
                updateSession(projectId) {
                    it.copy(
                        artifact = ArtifactDownloadState(
                            downloading = false,
                            error = error.message ?: error::class.java.simpleName,
                        ),
                    )
                }
            }
        }
    }

    fun trustPendingHostKey() {
        val fingerprint = _state.value.pendingHostKeyFingerprint ?: return
        val profile = _state.value.profile ?: return
        val projectId = _state.value.pendingHostKeyProjectId
        val trusted = profile.copy(hostKeySha256 = fingerprint)
        repository.save(trusted, null)
        updateState { it.copy(profile = trusted, pendingHostKeyFingerprint = null, pendingHostKeyProjectId = null) }
        refreshGitKeyStatus()
        if (projectId != null) runCommandFor(projectId)
    }

    fun rejectPendingHostKey() = updateState {
        it.copy(pendingHostKeyFingerprint = null, pendingHostKeyProjectId = null)
    }

    fun openGitKeyUnlock() = updateState {
        it.copy(gitKeyUnlockOpen = true, gitKeyMessage = null)
    }

    fun closeGitKeyUnlock() = updateState { it.copy(gitKeyUnlockOpen = false) }

    fun refreshGitKeyStatus() {
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret() ?: return
        if (secret.isBlank() || profile.hostKeySha256.isBlank()) return
        if (_state.value.gitKeyStatus == GitKeyStatus.CHECKING) return

        updateState { it.copy(gitKeyStatus = GitKeyStatus.CHECKING, gitKeyMessage = null) }
        viewModelScope.launch {
            var stdout = ""
            var stderr = ""
            var connectionError: String? = null
            executor.execute(profile, secret, GitSshAgent.statusCommand()).collect { event ->
                when (event) {
                    is CommandEvent.Output -> when (event.stream) {
                        OutputStreamKind.STDOUT -> stdout += event.text
                        OutputStreamKind.STDERR -> stderr += event.text
                    }
                    is CommandEvent.ConnectionFailed -> connectionError = event.message
                    else -> Unit
                }
            }
            val status = when {
                connectionError != null -> GitKeyStatus.ERROR
                stdout.contains("POCKETDEV_GIT_KEY_READY") -> GitKeyStatus.READY
                stdout.contains("POCKETDEV_GIT_KEY_LOCKED") -> GitKeyStatus.LOCKED
                else -> GitKeyStatus.ERROR
            }
            updateState {
                it.copy(
                    gitKeyStatus = status,
                    gitKeyMessage = when (status) {
                        GitKeyStatus.ERROR -> connectionError ?: stderr.trim().ifBlank { "Could not inspect the remote SSH agent." }
                        else -> null
                    },
                )
            }
        }
    }

    fun unlockGitKey(passphrase: String) {
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret() ?: return
        if (passphrase.isBlank() || profile.hostKeySha256.isBlank()) return

        updateState {
            it.copy(
                gitKeyUnlockOpen = false,
                gitKeyStatus = GitKeyStatus.CHECKING,
                gitKeyMessage = null,
            )
        }
        viewModelScope.launch {
            var stdout = ""
            var stderr = ""
            var exitCode: Int? = null
            var connectionError: String? = null
            executor.execute(
                profile = profile,
                secret = secret,
                command = GitSshAgent.unlockCommand(),
                stdinText = passphrase,
            ).collect { event ->
                when (event) {
                    is CommandEvent.Output -> when (event.stream) {
                        OutputStreamKind.STDOUT -> stdout += event.text
                        OutputStreamKind.STDERR -> stderr += event.text
                    }
                    is CommandEvent.Completed -> exitCode = event.exitCode
                    is CommandEvent.ConnectionFailed -> connectionError = event.message
                    else -> Unit
                }
            }
            val ready = exitCode == 0 && stdout.contains("POCKETDEV_GIT_KEY_READY")
            updateState {
                it.copy(
                    gitKeyStatus = if (ready) GitKeyStatus.READY else GitKeyStatus.LOCKED,
                    gitKeyMessage = if (ready) null else connectionError ?: stderr.trim().ifBlank { "The Git key could not be unlocked." },
                )
            }
            if (ready && _state.value.pendingProjectCreate != null) createPendingProject()
        }
    }

    fun dismissProjectCreate() = updateState { it.copy(pendingProjectCreate = null) }

    fun createPendingProject() {
        val request = _state.value.pendingProjectCreate ?: return
        val project = _state.value.projects.firstOrNull { it.id == request.projectId } ?: run {
            dismissProjectCreate()
            return
        }
        if (project.githubRepository.isNotBlank() && _state.value.gitKeyStatus != GitKeyStatus.READY) {
            updateState { it.copy(gitKeyUnlockOpen = true, gitKeyMessage = null) }
            return
        }
        val profile = _state.value.profile ?: return
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) return
        val command = runCatching { ProjectProvisioning.createCommand(project) }.getOrElse { error ->
            updateSession(project.id) { session ->
                session.copy(command = session.command.copy(connectionError = error.message ?: "Project could not be created."))
            }
            dismissProjectCreate()
            return
        }

        updateState { it.copy(pendingProjectCreate = null) }
        updateSession(project.id) {
            it.copy(command = it.command.copy(running = true, stdout = "", stderr = "", exitCode = null, connectionError = null))
        }
        viewModelScope.launch {
            var stdout = ""
            var exitCode: Int? = null
            var connectionError: String? = null
            executor.execute(profile, secret, GitSshAgent.wrap(command)).collect { event ->
                when (event) {
                    is CommandEvent.Output -> {
                        if (event.stream == OutputStreamKind.STDOUT) stdout += event.text
                        updateSession(project.id) { session ->
                            session.copy(command = CommandStateReducer.reduce(session.command, event))
                        }
                    }
                    is CommandEvent.Completed -> {
                        exitCode = event.exitCode
                        updateSession(project.id) { session ->
                            session.copy(command = CommandStateReducer.reduce(session.command, event))
                        }
                    }
                    is CommandEvent.ConnectionFailed -> {
                        connectionError = event.message
                        updateSession(project.id) { session ->
                            session.copy(command = CommandStateReducer.reduce(session.command, event))
                        }
                    }
                    else -> Unit
                }
            }
            refreshGitKeyStatus()
            if (connectionError == null && exitCode == 0 && stdout.contains(ProjectProvisioning.CREATED_MARKER)) {
                updateSession(project.id) { session ->
                    session.copy(command = session.command.copy(command = request.command, running = false))
                }
                runCommandFor(project.id, skipProjectCheck = true)
            }
        }
    }

    fun setCommand(command: String) {
        val projectId = _state.value.project?.id ?: return
        updateSession(projectId) { it.copy(command = it.command.copy(command = command)) }
    }

    fun runCommand() {
        val projectId = _state.value.project?.id ?: return
        runCommandFor(projectId)
    }

    private fun runCommandFor(projectId: String, skipProjectCheck: Boolean = false) {
        val profile = _state.value.profile ?: return
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        val secret = repository.loadSecret()
        if (secret.isNullOrBlank()) {
            updateSession(projectId) { it.copy(command = it.command.copy(connectionError = "No authentication secret is stored.")) }
            return
        }
        val session = _state.value.sessions[projectId] ?: return
        if (session.command.running) return
        val commandText = session.command.command.trim()
        if (commandText.isEmpty()) return

        if (!skipProjectCheck) {
            updateSession(projectId) {
                it.copy(command = it.command.copy(running = true, stdout = "", stderr = "", exitCode = null, connectionError = null))
            }
            viewModelScope.launch {
                var stdout = ""
                var stderr = ""
                var exitCode: Int? = null
                var connectionError: String? = null
                var hostKeyPending = false
                executor.execute(profile, secret, GitSshAgent.wrap(ProjectProvisioning.checkCommand(project))).collect { event ->
                    when (event) {
                        is CommandEvent.Output -> when (event.stream) {
                            OutputStreamKind.STDOUT -> stdout += event.text
                            OutputStreamKind.STDERR -> stderr += event.text
                        }
                        is CommandEvent.Completed -> exitCode = event.exitCode
                        is CommandEvent.ConnectionFailed -> connectionError = event.message
                        is CommandEvent.HostKeyTrustRequired -> {
                            hostKeyPending = true
                            updateState {
                                it.copy(
                                    pendingHostKeyFingerprint = event.fingerprint,
                                    pendingHostKeyProjectId = projectId,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                updateSession(projectId) { current -> current.copy(command = current.command.copy(running = false)) }
                when {
                    hostKeyPending -> Unit
                    connectionError != null -> updateSession(projectId) { current ->
                        current.copy(command = current.command.copy(connectionError = connectionError))
                    }
                    exitCode != 0 -> updateSession(projectId) { current ->
                        current.copy(command = current.command.copy(connectionError = stderr.trim().ifBlank { "Could not inspect the project folder." }))
                    }
                    stdout.contains(ProjectProvisioning.MISSING_MARKER) -> updateState {
                        it.copy(pendingProjectCreate = ProjectCreateRequest(projectId = projectId, command = commandText))
                    }
                    stdout.contains(ProjectProvisioning.EXISTS_MARKER) -> runCommandFor(projectId, skipProjectCheck = true)
                    else -> updateSession(projectId) { current ->
                        current.copy(command = current.command.copy(connectionError = "Could not determine whether the project folder exists."))
                    }
                }
            }
            return
        }

        updateSession(projectId) {
            it.copy(command = it.command.copy(running = true, stdout = "", stderr = "", exitCode = null, connectionError = null))
        }

        viewModelScope.launch {
            executor.execute(profile, secret, GitSshAgent.wrap(commandText)).collect { event ->
                if (event is CommandEvent.HostKeyTrustRequired) {
                    updateState {
                        it.copy(
                            pendingHostKeyFingerprint = event.fingerprint,
                            pendingHostKeyProjectId = projectId,
                        )
                    }
                    updateSession(projectId) { sessionState ->
                        sessionState.copy(command = sessionState.command.copy(running = false))
                    }
                } else {
                    updateSession(projectId) { sessionState ->
                        sessionState.copy(command = CommandStateReducer.reduce(sessionState.command, event))
                    }
                }
            }
            refreshGitKeyStatus()
        }
    }

    private fun updateState(transform: (PocketDevState) -> PocketDevState) {
        _state.update(transform)
    }

    private fun updateSession(projectId: String, transform: (ProjectSessionState) -> ProjectSessionState) {
        _state.update { state ->
            val current = state.sessions[projectId] ?: ProjectSessionState()
            state.copy(sessions = state.sessions + (projectId to transform(current)))
        }
        _state.value.sessions[projectId]?.let { persistSession(projectId, it) }
    }

    private fun ensureSessions(
        projects: List<ProjectConfig>,
        current: Map<String, ProjectSessionState>,
    ): Map<String, ProjectSessionState> = projects.associate { project ->
        project.id to (current[project.id] ?: restoreSession(project.id))
    }

    private fun persistSession(projectId: String, session: ProjectSessionState) {
        val prefix = "session.$projectId."
        savedStateHandle[prefix + "command"] = session.command.command
        savedStateHandle[prefix + "running"] = session.command.running
        savedStateHandle[prefix + "stdout"] = session.command.stdout
        savedStateHandle[prefix + "stderr"] = session.command.stderr
        savedStateHandle[prefix + "exitCode"] = session.command.exitCode
        savedStateHandle[prefix + "connectionError"] = session.command.connectionError
        savedStateHandle[prefix + "artifactRemote"] = session.artifact.remotePath
        savedStateHandle[prefix + "artifactLocal"] = session.artifact.localPath
        savedStateHandle[prefix + "artifactError"] = session.artifact.error
    }

    private fun restoreSession(projectId: String): ProjectSessionState {
        val prefix = "session.$projectId."
        val wasRunning = savedStateHandle.get<Boolean>(prefix + "running") == true
        return ProjectSessionState(
            command = CommandUiState(
                command = savedStateHandle.get<String>(prefix + "command") ?: "pwd",
                running = false,
                stdout = savedStateHandle.get<String>(prefix + "stdout").orEmpty(),
                stderr = savedStateHandle.get<String>(prefix + "stderr").orEmpty(),
                exitCode = savedStateHandle.get<Int>(prefix + "exitCode"),
                connectionError = if (wasRunning) {
                    "Command was interrupted because PocketDev was stopped while in the background. Run it again to continue."
                } else {
                    savedStateHandle.get<String>(prefix + "connectionError")
                },
            ),
            artifact = ArtifactDownloadState(
                downloading = false,
                remotePath = savedStateHandle.get<String>(prefix + "artifactRemote"),
                localPath = savedStateHandle.get<String>(prefix + "artifactLocal"),
                error = savedStateHandle.get<String>(prefix + "artifactError"),
            ),
        )
    }

    private fun clearPersistedSession(projectId: String) {
        val prefix = "session.$projectId."
        listOf(
            "command", "running", "stdout", "stderr", "exitCode", "connectionError",
            "artifactRemote", "artifactLocal", "artifactError",
        ).forEach { key -> savedStateHandle.remove<Any>(prefix + key) }
    }

    private fun loadInitialState(): PocketDevState {
        val stored = repository.load()
        val projectCollection = projectRepository.load()
        val sessions = projectCollection.projects.associate { project -> project.id to restoreSession(project.id) }
        return PocketDevState(
            profile = stored?.profile,
            hasStoredSecret = stored?.hasSecret == true,
            projects = projectCollection.projects,
            project = projectCollection.activeProject,
            sessions = sessions,
        )
    }
}
