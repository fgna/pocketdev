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
import java.io.FileInputStream
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.artifact.ApkInstaller
import de.fgna.pocketdev.artifact.SshArtifactRetriever
import de.fgna.pocketdev.diagnostics.DiagnosticDraftBuilder
import de.fgna.pocketdev.diagnostics.DiagnosticIssueDraft
import de.fgna.pocketdev.project.ActionProfileTemplates
import de.fgna.pocketdev.project.ActionDefinition
import de.fgna.pocketdev.project.ProjectCommandBuilder
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.project.WorkspaceTabSelection
import de.fgna.pocketdev.project.WorkspaceTabs
import de.fgna.pocketdev.ssh.AuthMode
import de.fgna.pocketdev.ui.PocketDevCompactMeta
import de.fgna.pocketdev.ui.PocketDevContextStrip
import de.fgna.pocketdev.ui.PocketDevHairlineSurface
import de.fgna.pocketdev.ui.PocketDevIndicatorState
import de.fgna.pocketdev.ui.PocketDevSectionLabel
import de.fgna.pocketdev.ui.PocketDevTheme
import java.io.File
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketDevApp() }
    }
}

@Composable
fun PocketDevApp(
    vm: PocketDevViewModel = viewModel(),
    onFiles: (() -> Unit)? = null,
    onTerminal: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var issueDraft by remember { mutableStateOf<DiagnosticIssueDraft?>(null) }
    var projectSwitcherOpen by remember { mutableStateOf(false) }
    var pendingRunTabId by remember { mutableStateOf<String?>(null) }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val originalTab = pendingRunTabId
        pendingRunTabId = null
        if (granted && originalTab != null) vm.runCommand(originalTab)
        else if (!granted) Toast.makeText(context, "Nearby devices permission is required for SSH to local-network servers.", Toast.LENGTH_LONG).show()
    }

    val runWithLanPermission = {
        val originalTab = state.activeTabId
        if (originalTab != null) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingRunTabId = originalTab
                nearbyPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else vm.runCommand(originalTab)
        }
    }
    val runProjectAction: (String) -> Unit = { actionId ->
        if (vm.prepareProjectAction(actionId)) runWithLanPermission()
    }

    PocketDevTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PocketDevHome(
                state = state,
                onConfigure = vm::openEditor,
                onProjects = { projectSwitcherOpen = true },
                onFiles = onFiles,
                onTerminal = onTerminal,
                onSelectProject = { tabId ->
                    vm.selectTab(tabId)
                    issueDraft = null
                },
                onOpenTab = vm::openTab,
                onCloseTab = vm::closeTab,
                onRenameTab = vm::renameTab,
                onProjectAction = runProjectAction,
                onDownloadArtifact = vm::downloadLatestApk,
                onOpenArtifact = { path -> openApk(context, path) },
                onCommandChange = vm::setCommand,
                onCommandFavoriteChange = vm::setCommandFavorite,
                onLoadCommandFavorite = vm::loadCommandFavorite,
                onRun = runWithLanPermission,
                onProjectHome = { if (vm.prepareProjectHome()) runWithLanPermission() },
                onParentDirectory = { if (vm.prepareParentDirectory()) runWithLanPermission() },
                onCopy = { text -> copyText(context, text) },
                onUnlockGitKey = vm::openGitKeyUnlock,
                onRefreshGitKey = vm::refreshGitKeyStatus,
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
                    vm.openQuickImport()
                },
                onEdit = {
                    projectSwitcherOpen = false
                    vm.openProjectEditor()
                },
                onDismiss = { projectSwitcherOpen = false },
            )
        }

        if (state.quickImportOpen) {
            var repositoryInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = vm::closeQuickImport,
                title = { Text("Add from GitHub") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = repositoryInput,
                            onValueChange = { repositoryInput = it },
                            label = { Text("GitHub repository") },
                            placeholder = { Text("fgna/ae") },
                            singleLine = true,
                            enabled = !state.quickImportBusy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Checks ~/Projects on the SSH server. Reuses a matching checkout or clones a missing one.", style = MaterialTheme.typography.bodySmall)
                        state.quickImportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (state.quickImportBusy) Text("Checking server…")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { vm.importFromGitHub(repositoryInput) },
                        enabled = repositoryInput.isNotBlank() && !state.quickImportBusy,
                    ) { Text("Add project") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            vm.closeQuickImport()
                            vm.openNewProjectEditor()
                        }, enabled = !state.quickImportBusy) { Text("Advanced") }
                        TextButton(onClick = vm::closeQuickImport, enabled = !state.quickImportBusy) { Text("Cancel") }
                    }
                },
            )
        }

        if (state.projectEditorOpen) {
            ProjectDialog(
                state = state.projectEditor,
                editingExisting = state.projectEditorId != null,
                profiles = state.actionProfiles,
                profileUseCount = state.projects.count { it.actionProfileId == state.projectEditor.actionProfileId },
                onSelectProfile = vm::selectActionProfile,
                onSaveProfile = vm::saveActionProfile,
                onChange = vm::updateProjectEditor,
                onDismiss = vm::closeProjectEditor,
                onSave = vm::saveProjectEditor,
                onDelete = vm::deleteCurrentProject,
            )
        }

        state.pendingProjectCreate?.let { request ->
            val project = state.projects.firstOrNull { it.id == request.projectId }
            if (project != null) {
                ProjectCreateDialog(
                    project = project,
                    gitKeyStatus = state.gitKeyStatus,
                    onDismiss = vm::dismissProjectCreate,
                    onCreate = vm::createPendingProject,
                )
            }
        }

        if (state.gitKeyUnlockOpen) {
            GitKeyUnlockDialog(
                onDismiss = vm::closeGitKeyUnlock,
                onUnlock = vm::unlockGitKey,
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
    onFiles: (() -> Unit)?,
    onTerminal: (() -> Unit)?,
    onSelectProject: (String) -> Unit,
    onOpenTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onRenameTab: (String, String) -> Unit,
    onProjectAction: (String) -> Unit,
    onDownloadArtifact: () -> Unit,
    onOpenArtifact: (String) -> Unit,
    onCommandChange: (String) -> Unit,
    onCommandFavoriteChange: (String, String, Boolean) -> Unit,
    onLoadCommandFavorite: (String, String) -> Unit,
    onRun: () -> Unit,
    onProjectHome: () -> Unit,
    onParentDirectory: () -> Unit,
    onCopy: (String) -> Unit,
    onUnlockGitKey: () -> Unit,
    onRefreshGitKey: () -> Unit,
    onIssueDraft: () -> Unit,
) {
    val profile = state.profile
    val project = state.project
    val execution = state.command
    val artifact = state.artifact
    val busy = execution.running || artifact.downloading
    var renamingTabId by remember { mutableStateOf<String?>(null) }
    var tabName by remember { mutableStateOf("") }
    var navigationMenuOpen by remember { mutableStateOf(false) }

    if (renamingTabId != null) {
        AlertDialog(
            onDismissRequest = { renamingTabId = null },
            title = { Text("Rename tab") },
            text = {
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it.take(60) },
                    label = { Text("Tab name") },
                    supportingText = { Text("Leave empty to use the project name.") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renamingTabId?.let { onRenameTab(it, tabName) }
                    renamingTabId = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamingTabId = null }) { Text("Cancel") } },
        )
    }

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
                            Text(WorkspaceTabs.labels(state.tabSelection.tabs, state.projects).firstOrNull { it.tabId == state.activeTabId }?.title ?: "PocketDev", style = MaterialTheme.typography.titleLarge)
                            PocketDevContextStrip(headerLabel, state = headerState)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { navigationMenuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open navigation menu",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = navigationMenuOpen,
                                onDismissRequest = { navigationMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Server") },
                                    onClick = { navigationMenuOpen = false; onConfigure() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Projects") },
                                    onClick = { navigationMenuOpen = false; onProjects() },
                                )
                                if (onFiles != null) {
                                    DropdownMenuItem(
                                        text = { Text("Files") },
                                        onClick = { navigationMenuOpen = false; onFiles() },
                                    )
                                }
                                if (onTerminal != null) {
                                    DropdownMenuItem(
                                        text = { Text("Interactive SSH terminal") },
                                        enabled = !execution.running && profile != null && project != null && state.hasStoredSecret,
                                        onClick = { navigationMenuOpen = false; onTerminal() },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            BottomProjectTabs(
                projects = state.projects,
                tabs = state.tabSelection,
                sessions = state.sessions,
                onSelect = onSelectProject,
                onOpenTab = onOpenTab,
                onAddProject = onProjects,
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
                        state.activeTabId?.let { activeTabId ->
                            TextButton(onClick = {
                                tabName = state.tabSelection.activeTab?.customTitle.orEmpty()
                                renamingTabId = activeTabId
                            }) { Text("Rename", style = MaterialTheme.typography.labelSmall) }

                            val tabBusy = state.sessions[activeTabId]?.let { it.command.running || it.artifact.downloading } == true
                            TextButton(
                                onClick = { onCloseTab(activeTabId) },
                                enabled = !tabBusy && state.pendingProjectCreate?.tabId != activeTabId &&
                                    state.pendingHostKeyProjectId != activeTabId,
                            ) { Text("Close tab", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    when {
                        profile == null -> Text("Configure an SSH server to start.", style = MaterialTheme.typography.bodyMedium)
                        project == null -> {
                            PocketDevCompactMeta("Server", "${profile.username}@${profile.host}:${profile.port}")
                            Text("Add a remote project to enable one-tap actions.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = onProjects) { Text("Add first project") }
                        }
                        else -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    Text(project.remotePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    state.currentGitBranch?.let { branch ->
                                        Text("branch · $branch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("${profile.username}@${profile.host}", style = MaterialTheme.typography.bodySmall)
                                    Text(if (profile.hostKeySha256.isNotBlank() && state.hasStoredSecret) "SSH ready" else "SSH incomplete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    when (state.gitKeyStatus) {
                                        GitKeyStatus.UNKNOWN -> "Git key · unknown"
                                        GitKeyStatus.CHECKING -> "Git key · checking…"
                                        GitKeyStatus.LOCKED -> "Git key · locked"
                                        GitKeyStatus.READY -> "Git key · ready"
                                        GitKeyStatus.ERROR -> "Git key · check failed"
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.gitKeyStatus == GitKeyStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when (state.gitKeyStatus) {
                                    GitKeyStatus.LOCKED -> TextButton(onClick = onUnlockGitKey) { Text("Unlock") }
                                    GitKeyStatus.UNKNOWN, GitKeyStatus.ERROR -> TextButton(onClick = onRefreshGitKey) { Text("Check") }
                                    else -> Unit
                                }
                            }
                            state.gitKeyMessage?.let { message ->
                                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }

                            val actionsResult = remember(project, state.actionProfiles) {
                                runCatching { ProjectCommandBuilder.availableActions(project, state.actionProfiles) }
                            }
                            val actions = actionsResult.getOrDefault(emptyList())
                            val actionProfile = state.actionProfiles.firstOrNull { it.id == project.actionProfileId }
                            Text(actionProfile?.name ?: "Unavailable action profile", style = MaterialTheme.typography.labelMedium)
                            actionsResult.exceptionOrNull()?.let {
                                Text("Select an available profile in Projects → Edit.", color = MaterialTheme.colorScheme.error)
                            }
                            var pendingAction by remember(project.id, project.actionProfileId, project.customActions, project.actionOrder, state.actionProfiles) { mutableStateOf<ActionDefinition?>(null) }
                            actions.chunked(3).forEach { group ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    group.forEach { action ->
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (action.requiresConfirmation) pendingAction = action
                                                else onProjectAction(action.id)
                                            },
                                            enabled = !busy,
                                        ) { Text(action.label) }
                                    }
                                }
                            }
                            pendingAction?.let { action ->
                                AlertDialog(
                                    onDismissRequest = { pendingAction = null },
                                    title = { Text("Run ${action.label}?") },
                                    text = { Text("${profile.username}@${profile.host}\n${state.projects.firstOrNull { it.id == project.id }?.remotePath ?: project.remotePath}\n\n${action.command}") },
                                    confirmButton = {
                                        TextButton(enabled = !busy, onClick = {
                                            pendingAction = null
                                            onProjectAction(action.id)
                                        }) { Text("Run") }
                                    },
                                    dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
                                )
                            }

                            if (actionProfile?.supportsApk == true) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDownloadArtifact, enabled = profile.hostKeySha256.isNotBlank() && state.hasStoredSecret && !busy) { Text(if (artifact.downloading) "Downloading…" else "Get APK") }
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = { artifact.localPath?.let(onOpenArtifact) }, enabled = artifact.localPath != null && !artifact.downloading) { Text("Open APK") }
                            }
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
                        TextButton(onClick = onParentDirectory, enabled = project != null && profile != null && state.hasStoredSecret && !busy) { Text("cd ..") }
                        TextButton(
                            onClick = onProjectHome,
                            enabled = project != null && profile != null && state.hasStoredSecret && !busy,
                        ) { Text("cd Project home") }
                        TextButton(onClick = { onCommandChange("") }, enabled = !busy && execution.command.isNotEmpty()) { Text("Clear") }
                    }
                    state.currentWorkingDirectory?.let { workingDirectory ->
                        Text(
                            "cwd · $workingDirectory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (project != null) {
                        CommandFavorites(
                            project = project,
                            command = execution.command,
                            canLoad = !busy,
                            onFavoriteChange = onCommandFavoriteChange,
                            onLoad = onLoadCommandFavorite,
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
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
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
                        if (execution.exitCode != null && execution.exitCode != 0) TextButton(onClick = onIssueDraft) { Text("Git Issue") }
                        TextButton(onClick = { onCopy(execution.combinedOutput) }, enabled = execution.combinedOutput.isNotBlank()) { Text("Copy") }
                    }
                    if (execution.codexAction != null) {
                        var now by remember(execution.startedAtMs) { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(execution.running, execution.startedAtMs) {
                            while (execution.running) {
                                now = System.currentTimeMillis()
                                delay(1000)
                            }
                        }
                        val elapsed = execution.startedAtMs?.let { (now - it).coerceAtLeast(0) / 1000 } ?: 0
                        val idle = execution.lastActivityAtMs?.let { (now - it).coerceAtLeast(0) / 1000 }
                        Text("Codex · ${execution.codexAction} · elapsed ${elapsed}s", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (idle == null) "No output received yet" else "Last output ${idle}s ago" +
                                if (execution.running && idle > 60) " · quiet, stall unconfirmed" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (execution.codexNeedsInput) Text("Codex requested input or approval. Review on the server.", color = MaterialTheme.colorScheme.error)
                        execution.remoteLogPrefix?.let { prefix ->
                            Text("Server logs: ${prefix}.stdout / .stderr", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onCopy("${prefix}.stdout\n${prefix}.stderr") }) { Text("Copy log paths") }
                        }
                    }
                    Text(execution.combinedOutput.ifBlank { "No output yet." }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProjectCreateDialog(
    project: ProjectConfig,
    gitKeyStatus: GitKeyStatus,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val willClone = project.githubRepository.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create project on server?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PocketDev could not find the configured project folder:")
                Text(project.remotePath, style = MaterialTheme.typography.bodySmall)
                if (willClone) {
                    Text("Create it by cloning ${project.githubRepository} from GitHub. The Git SSH key will be unlocked first if needed.")
                    if (gitKeyStatus != GitKeyStatus.READY) {
                        Text("Git key is not ready yet; Create will ask for its passphrase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("No GitHub repository is configured, so PocketDev will create an empty directory.")
                }
                Text("The original command will continue automatically after creation succeeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GitKeyUnlockDialog(
    onDismiss: () -> Unit,
    onUnlock: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock Git key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the passphrase for the GitHub SSH key on the development server. PocketDev sends it only to ssh-add and does not store it.")
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = passphrase
                    passphrase = ""
                    onUnlock(value)
                },
                enabled = passphrase.isNotBlank(),
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BottomProjectTabs(
    projects: List<ProjectConfig>,
    tabs: WorkspaceTabSelection,
    sessions: Map<String, ProjectSessionState>,
    onSelect: (String) -> Unit,
    onOpenTab: (String) -> Unit,
    onAddProject: () -> Unit,
) {
    val titles = WorkspaceTabs.labels(tabs.tabs, projects)
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Tabs · ${tabs.tabs.size}", modifier = Modifier.padding(start = 12.dp, top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                titles.forEach { tab ->
                    val session = sessions[tab.tabId]
                    val running = session?.command?.running == true || session?.artifact?.downloading == true
                    val failed = session?.command?.exitCode?.let { it != 0 } == true ||
                        session?.command?.connectionError != null || session?.artifact?.error != null
                    val label = buildString {
                        if (running) append("● ") else if (failed) append("! ")
                        append(tab.title)
                    }
                    if (tab.tabId == tabs.activeTabId) {
                        Button(onClick = { onSelect(tab.tabId) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { onSelect(tab.tabId) }) { Text(label) }
                    }
                 }
                val currentProjectId = tabs.activeTab?.projectId
                if (currentProjectId != null) {
                    OutlinedButton(onClick = { onOpenTab(currentProjectId) }) { Text("+ Tab") }
                }
                TextButton(onClick = onAddProject) { Text(if (projects.isEmpty()) "Add project" else "Projects") }
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Checkbox(checked = state.loginShell, onCheckedChange = { enabled -> onChange { it.copy(loginShell = enabled) } })
                    Text("Login shell (load server PATH from ~/.profile)")
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

    val marker = File(path + SshArtifactRetriever.VERIFIED_SUFFIX)
    val verifiedHash = runCatching { marker.readText().trim() }.getOrNull()
    if (!marker.isFile || verifiedHash == null || !verifiedHash.matches(Regex("^[0-9a-f]{64}$"))) {
        Toast.makeText(context, "APK is not verified. Tap Get APK again before installing.", Toast.LENGTH_LONG).show()
        return
    }

    val actualHash = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()
    if (actualHash == null || !actualHash.equals(verifiedHash, ignoreCase = true)) {
        Toast.makeText(context, "APK verification failed: file changed after download.", Toast.LENGTH_LONG).show()
        return
    }

    runCatching { ApkInstaller.install(context, file) }
        .onFailure { error ->
            Toast.makeText(context, "Could not start APK install: ${error.message}", Toast.LENGTH_LONG).show()
        }
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

