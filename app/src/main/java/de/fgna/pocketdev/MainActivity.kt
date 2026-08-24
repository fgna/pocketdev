package de.fgna.pocketdev

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.diagnostics.DiagnosticDraftBuilder
import de.fgna.pocketdev.diagnostics.DiagnosticIssueDraft
import de.fgna.pocketdev.project.ProjectAction
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ui.PocketDevCompactMeta
import de.fgna.pocketdev.ui.PocketDevContextStrip
import de.fgna.pocketdev.ui.PocketDevHairlineSurface
import de.fgna.pocketdev.ui.PocketDevIndicatorState
import de.fgna.pocketdev.ui.PocketDevSectionLabel
import de.fgna.pocketdev.ui.PocketDevTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketDevApp() }
    }
}

@Composable
fun PocketDevApp(vm: PocketDevViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var issueDraft by remember { mutableStateOf<DiagnosticIssueDraft?>(null) }
    var projectSwitcherOpen by remember { mutableStateOf(false) }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.runCommand()
        else Toast.makeText(context, "Nearby devices permission is required for SSH to local-network servers.", Toast.LENGTH_LONG).show()
    }

    val runWithLanPermission = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) nearbyPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        else vm.runCommand()
    }
    val runProjectAction: (ProjectAction) -> Unit = { action ->
        if (vm.prepareProjectAction(action)) runWithLanPermission()
    }

    PocketDevTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PocketDevHome(
                state = state,
                onConfigure = vm::openEditor,
                onProjects = { projectSwitcherOpen = true },
                onSelectProject = { projectId ->
                    vm.selectProject(projectId)
                    issueDraft = null
                },
                onProjectAction = runProjectAction,
                onDownloadArtifact = vm::downloadLatestApk,
                onOpenArtifact = { path -> openApk(context, path) },
                onCommandChange = vm::setCommand,
                onRun = runWithLanPermission,
                onCopy = { text -> copyText(context, text) },
                onIssueDraft = {
                    val project = state.project
                    val exitCode = state.command.exitCode
                    if (project != null && exitCode != null && exitCode != 0) {
                        issueDraft = DiagnosticDraftBuilder.build(project, state.command)
                    }
                },
            )
        }

        if (state.editorOpen) {
            ProfileDialog(state.editor, vm::updateEditor, vm::closeEditor, vm::saveEditor)
        }

        if (projectSwitcherOpen) {
            ProjectSwitcherDialog(
                projects = state.projects,
                activeProjectId = state.project?.id,
                onSelect = { projectId ->
                    vm.selectProject(projectId)
                    issueDraft = null
                    projectSwitcherOpen = false
                },
                onNew = {
                    projectSwitcherOpen = false
                    vm.openNewProjectEditor()
                },
                onEdit = {
                    projectSwitcherOpen = false
                    vm.openProjectEditor()
                },
                onDismiss = { projectSwitcherOpen = false },
            )
        }

        if (state.projectEditorOpen) {
            ProjectDialog(
                state = state.projectEditor,
                editingExisting = state.projectEditorId != null,
                onChange = vm::updateProjectEditor,
                onDismiss = vm::closeProjectEditor,
                onSave = vm::saveProjectEditor,
                onDelete = vm::deleteCurrentProject,
            )
        }

        issueDraft?.let { draft ->
            val repository = state.project?.githubRepository.orEmpty()
            IssueDraftDialog(
                draft = draft,
                repository = repository,
                onDismiss = { issueDraft = null },
                onOpenGitHub = { title, body ->
                    openGitHubIssueDraft(context, repository, title, body)
                    issueDraft = null
                },
            )
        }

        state.pendingHostKeyFingerprint?.let { fingerprint ->
            AlertDialog(
                onDismissRequest = vm::rejectPendingHostKey,
                title = { Text("Trust this server?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This is the first connection to this SSH server. PocketDev has not sent your password yet.")
                        Text("Server fingerprint:")
                        Text(fingerprint, style = MaterialTheme.typography.bodySmall)
                        Text("Only trust it if this is the server you intended to connect to. PocketDev will remember this fingerprint and reject future changes.")
                    }
                },
                confirmButton = { TextButton(onClick = vm::trustPendingHostKey) { Text("Trust and connect") } },
                dismissButton = { TextButton(onClick = vm::rejectPendingHostKey) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun PocketDevHome(
    state: PocketDevState,
    onConfigure: () -> Unit,
    onProjects: () -> Unit,
    onSelectProject: (String) -> Unit,
    onProjectAction: (ProjectAction) -> Unit,
    onDownloadArtifact: () -> Unit,
    onOpenArtifact: (String) -> Unit,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit,
    onCopy: (String) -> Unit,
    onIssueDraft: () -> Unit,
) {
    val profile = state.profile
    val project = state.project
    val execution = state.command
    val artifact = state.artifact
    val busy = execution.running || artifact.downloading

    val headerState = when {
        artifact.error != null || execution.connectionError != null || (execution.exitCode != null && execution.exitCode != 0) -> PocketDevIndicatorState.Warning
        busy -> PocketDevIndicatorState.Neutral
        profile != null && project != null && profile.hostKeySha256.isNotBlank() && state.hasStoredSecret -> PocketDevIndicatorState.Positive
        else -> PocketDevIndicatorState.Neutral
    }
    val headerLabel = when {
        artifact.downloading -> "Downloading APK"
        execution.running -> "Running command"
        execution.connectionError != null -> "Connection error"
        execution.exitCode != null && execution.exitCode != 0 -> "Exit ${execution.exitCode}"
        execution.exitCode == 0 -> "Last command passed"
        profile == null -> "SSH setup required"
        project == null -> "Project setup required"
        else -> "Ready"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("PocketDev", style = MaterialTheme.typography.titleLarge)
                            PocketDevContextStrip(headerLabel, state = headerState)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onConfigure) { Text("Server") }
                        TextButton(onClick = onProjects) { Text("Projects") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            BottomProjectTabs(
                projects = state.projects,
                activeProjectId = project?.id,
                sessions = state.sessions,
                onSelect = onSelectProject,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PocketDevHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PocketDevSectionLabel("Workspace", modifier = Modifier.weight(1f))
                        if (state.projects.size > 1) Text("${state.projects.size} projects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        profile == null -> Text("Configure an SSH server to start.", style = MaterialTheme.typography.bodyMedium)
                        project == null -> {
                            PocketDevCompactMeta("Server", "${profile.username}@${profile.host}:${profile.port}")
                            Text("Add a remote project to enable one-tap actions.", style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    Text(project.remotePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("${profile.username}@${profile.host}", style = MaterialTheme.typography.bodySmall)
                                    Text(if (profile.hostKeySha256.isNotBlank() && state.hasStoredSecret) "SSH ready" else "SSH incomplete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = { onProjectAction(ProjectAction.GIT_STATUS) }, enabled = !busy) { Text("Status") }
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = { onProjectAction(ProjectAction.TEST) }, enabled = !busy && project.testCommand.isNotBlank()) { Text("Test") }
                                Button(modifier = Modifier.weight(1f), onClick = { onProjectAction(ProjectAction.BUILD) }, enabled = !busy && project.buildCommand.isNotBlank()) { Text("Build") }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDownloadArtifact, enabled = profile.hostKeySha256.isNotBlank() && state.hasStoredSecret && !busy) { Text(if (artifact.downloading) "Downloading…" else "Get APK") }
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = { artifact.localPath?.let(onOpenArtifact) }, enabled = artifact.localPath != null && !artifact.downloading) { Text("Open APK") }
                            }

                            when {
                                artifact.error != null -> Text("APK: ${artifact.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                artifact.localPath != null -> Text("APK: ${File(artifact.localPath).name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            PocketDevHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PocketDevSectionLabel("Command", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onCommandChange("pwd") }, enabled = !busy) { Text("pwd") }
                        TextButton(onClick = { onCommandChange("") }, enabled = !busy && execution.command.isNotEmpty()) { Text("Clear") }
                    }
                    project?.remotePath?.let { remotePath ->
                        Text(
                            "cwd · $remotePath",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = execution.command,
                        onValueChange = onCommandChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 5,
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = onRun, enabled = profile != null && state.hasStoredSecret && execution.command.isNotBlank() && !busy) { Text(if (execution.running) "Running…" else "Run") }
                        OutlinedButton(onClick = { onCopy(execution.command) }, enabled = execution.command.isNotBlank()) { Text("Copy") }
                    }
                }
            }

            PocketDevHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PocketDevSectionLabel("Output")
                            Text(
                                when {
                                    execution.running -> "Running"
                                    execution.connectionError != null -> "Connection error"
                                    execution.exitCode == 0 -> "Completed · exit 0"
                                    execution.exitCode != null -> "Failed · exit ${execution.exitCode}"
                                    else -> "Ready"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    execution.connectionError != null || (execution.exitCode != null && execution.exitCode != 0) -> MaterialTheme.colorScheme.error
                                    execution.exitCode == 0 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        if (execution.exitCode != null && execution.exitCode != 0) TextButton(onClick = onIssueDraft) { Text("Issue draft") }
                        TextButton(onClick = { onCopy(execution.combinedOutput) }, enabled = execution.combinedOutput.isNotBlank()) { Text("Copy") }
                    }
                    Text(execution.combinedOutput.ifBlank { "No output yet." }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BottomProjectTabs(
    projects: List<ProjectConfig>,
    activeProjectId: String?,
    sessions: Map<String, ProjectSessionState>,
    onSelect: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Projects · ${projects.size}", modifier = Modifier.padding(start = 12.dp, top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (projects.isEmpty()) {
                    TextButton(onClick = {}) { Text("No projects") }
                } else {
                    projects.forEach { tabProject ->
                        val session = sessions[tabProject.id]
                        val running = session?.command?.running == true || session?.artifact?.downloading == true
                        val failed = session?.command?.exitCode?.let { it != 0 } == true || session?.command?.connectionError != null || session?.artifact?.error != null
                        val label = buildString {
                            if (running) append("● ") else if (failed) append("! ")
                            append(tabProject.name)
                        }
                        if (tabProject.id == activeProjectId) Button(onClick = { onSelect(tabProject.id) }) { Text(label) }
                        else OutlinedButton(onClick = { onSelect(tabProject.id) }) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectSwitcherDialog(
    projects: List<ProjectConfig>,
    activeProjectId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projects") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (projects.isEmpty()) Text("No projects configured yet.", style = MaterialTheme.typography.bodyMedium)
                else projects.forEach { project ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onSelect(project.id) }) { Text(if (project.id == activeProjectId) "✓ ${project.name}" else project.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (activeProjectId != null) TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun ProjectDialog(
    state: ProjectEditorState,
    editingExisting: Boolean,
    onChange: ((ProjectEditorState) -> ProjectEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val remotePathValid = state.remotePath.trim().startsWith("/")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingExisting) "Edit project" else "New project") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.name, onValueChange = { value -> onChange { it.copy(name = value) } }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = state.remotePath,
                    onValueChange = { value -> onChange { it.copy(remotePath = value) } },
                    label = { Text("Remote path") },
                    supportingText = { Text(if (state.remotePath.isNotBlank() && !remotePathValid) "Must start with /, e.g. /home/user/Projects/app" else "Absolute directory on the SSH server") },
                    isError = state.remotePath.isNotBlank() && !remotePathValid,
                )
                OutlinedTextField(value = state.testCommand, onValueChange = { value -> onChange { it.copy(testCommand = value) } }, label = { Text("Test command") }, singleLine = true)
                OutlinedTextField(value = state.buildCommand, onValueChange = { value -> onChange { it.copy(buildCommand = value) } }, label = { Text("Build command") }, singleLine = true)
                OutlinedTextField(value = state.githubRepository, onValueChange = { value -> onChange { it.copy(githubRepository = value.trim()) } }, label = { Text("GitHub repository") }, supportingText = { Text("Optional owner/name, e.g. fgna/pocketdev") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.name.isNotBlank() && remotePathValid) { Text("Save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (editingExisting) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete project?") },
            text = { Text("This removes the saved PocketDev project configuration. It does not delete the remote repository.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IssueDraftDialog(
    draft: DiagnosticIssueDraft,
    repository: String,
    onDismiss: () -> Unit,
    onOpenGitHub: (String, String) -> Unit,
) {
    var title by remember(draft) { mutableStateOf(draft.title) }
    var body by remember(draft) { mutableStateOf(draft.body) }
    val repositoryValid = Regex("^[^/\\s]+/[^/\\s]+$").matches(repository.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub issue draft") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (repositoryValid) repository else "Configure a GitHub repository as owner/name first.", style = MaterialTheme.typography.bodySmall, color = if (repositoryValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 12)
                Text("Review the redacted diagnostic before opening GitHub. PocketDev does not submit the issue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onOpenGitHub(title.trim(), body) }, enabled = repositoryValid && title.isNotBlank() && body.isNotBlank()) { Text("Open in GitHub") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileDialog(
    state: ProfileEditorState,
    onChange: ((ProfileEditorState) -> ProfileEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH profile") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.host, onValueChange = { value -> onChange { it.copy(host = value) } }, label = { Text("Host") }, singleLine = true)
                OutlinedTextField(value = state.port, onValueChange = { value -> onChange { it.copy(port = value.filter(Char::isDigit)) } }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(value = state.username, onValueChange = { value -> onChange { it.copy(username = value) } }, label = { Text("Username") }, singleLine = true)
                Text("The first connection asks you to confirm the server fingerprint.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChange { it.copy(authMode = AuthMode.PASSWORD) } }, enabled = state.authMode != AuthMode.PASSWORD) { Text("Password") }
                    OutlinedButton(onClick = { onChange { it.copy(authMode = AuthMode.PRIVATE_KEY) } }, enabled = state.authMode != AuthMode.PRIVATE_KEY) { Text("Private key") }
                }
                OutlinedTextField(value = state.secret, onValueChange = { value -> onChange { it.copy(secret = value) } }, label = { Text(if (state.authMode == AuthMode.PASSWORD) "Password" else "Private key (PEM)") }, visualTransformation = if (state.authMode == AuthMode.PASSWORD) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, supportingText = { Text("Leave empty to keep the stored secret.") }, minLines = if (state.authMode == AuthMode.PRIVATE_KEY) 4 else 1)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.host.isNotBlank() && state.username.isNotBlank() && state.port.toIntOrNull() != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PocketDev", text))
}

private fun openApk(context: Context, path: String) {
    val file = File(path)
    if (!file.isFile) {
        Toast.makeText(context, "Downloaded APK is no longer available.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "Android could not open this APK: ${it.message}", Toast.LENGTH_LONG).show() }
}

private fun openGitHubIssueDraft(context: Context, repository: String, title: String, body: String) {
    val repo = repository.trim()
    if (!Regex("^[^/\\s]+/[^/\\s]+$").matches(repo)) {
        Toast.makeText(context, "Configure the GitHub repository as owner/name first.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = Uri.Builder().scheme("https").authority("github.com").appendPath(repo.substringBefore('/')).appendPath(repo.substringAfter('/')).appendPath("issues").appendPath("new").appendQueryParameter("title", title).appendQueryParameter("body", body).build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "Could not open GitHub: ${it.message}", Toast.LENGTH_LONG).show() }
}