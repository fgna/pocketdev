package de.fgna.pocketdev

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.project.ProjectAction
import de.fgna.pocketdev.ssh.AuthMode
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
    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.runCommand()
        } else {
            Toast.makeText(
                context,
                "Nearby devices permission is required for SSH to local-network servers.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val runWithLanPermission = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            nearbyPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            vm.runCommand()
        }
    }
    val runProjectAction: (ProjectAction) -> Unit = { action ->
        if (vm.prepareProjectAction(action)) runWithLanPermission()
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PocketDevHome(
                state = state,
                onConfigure = vm::openEditor,
                onConfigureProject = vm::openProjectEditor,
                onProjectAction = runProjectAction,
                onDownloadArtifact = vm::downloadLatestApk,
                onOpenArtifact = { path -> openApk(context, path) },
                onCommandChange = vm::setCommand,
                onRun = runWithLanPermission,
                onCopy = { text -> copyText(context, text) },
            )
        }
    }

    if (state.editorOpen) {
        ProfileDialog(
            state = state.editor,
            onChange = vm::updateEditor,
            onDismiss = vm::closeEditor,
            onSave = vm::saveEditor,
        )
    }

    if (state.projectEditorOpen) {
        ProjectDialog(
            state = state.projectEditor,
            onChange = vm::updateProjectEditor,
            onDismiss = vm::closeProjectEditor,
            onSave = vm::saveProjectEditor,
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
            confirmButton = {
                TextButton(onClick = vm::trustPendingHostKey) { Text("Trust and connect") }
            },
            dismissButton = {
                TextButton(onClick = vm::rejectPendingHostKey) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PocketDevHome(
    state: PocketDevState,
    onConfigure: () -> Unit,
    onConfigureProject: () -> Unit,
    onProjectAction: (ProjectAction) -> Unit,
    onDownloadArtifact: () -> Unit,
    onOpenArtifact: (String) -> Unit,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val profile = state.profile
    val project = state.project
    val execution = state.command
    val artifact = state.artifact

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PocketDev", style = MaterialTheme.typography.headlineMedium)
        Text("Mobile control surface for a remote development server")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SSH server", style = MaterialTheme.typography.titleMedium)
                if (profile == null) {
                    Text("Not configured")
                } else {
                    Text("${profile.username}@${profile.host}:${profile.port}")
                    Text(if (profile.hostKeySha256.isBlank()) "Server identity: not trusted yet" else "Server identity: trusted")
                    Text(if (state.hasStoredSecret) "Authentication configured" else "Authentication secret missing")
                }
                Button(onClick = onConfigure) { Text(if (profile == null) "Configure" else "Edit") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Project", style = MaterialTheme.typography.titleMedium)
                if (project == null) {
                    Text("Not configured")
                } else {
                    Text(project.name)
                    Text(project.remotePath, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onProjectAction(ProjectAction.GIT_STATUS) }, enabled = !execution.running && !artifact.downloading) { Text("Git status") }
                        OutlinedButton(onClick = { onProjectAction(ProjectAction.TEST) }, enabled = !execution.running && !artifact.downloading && project.testCommand.isNotBlank()) { Text("Test") }
                        OutlinedButton(onClick = { onProjectAction(ProjectAction.BUILD) }, enabled = !execution.running && !artifact.downloading && project.buildCommand.isNotBlank()) { Text("Build") }
                    }
                }
                Button(onClick = onConfigureProject) { Text(if (project == null) "Configure project" else "Edit project") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("APK artifact", style = MaterialTheme.typography.titleMedium)
                when {
                    artifact.downloading -> Text("Finding and downloading latest APK…")
                    artifact.error != null -> Text("Artifact error: ${artifact.error}")
                    artifact.localPath != null -> {
                        Text("Downloaded: ${File(artifact.localPath).name}")
                        artifact.remotePath?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    else -> Text("No APK downloaded yet.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDownloadArtifact,
                        enabled = project != null && profile != null && profile.hostKeySha256.isNotBlank() && state.hasStoredSecret && !execution.running && !artifact.downloading,
                    ) { Text(if (artifact.downloading) "Downloading…" else "Download latest APK") }
                    OutlinedButton(
                        onClick = { artifact.localPath?.let(onOpenArtifact) },
                        enabled = artifact.localPath != null && !artifact.downloading,
                    ) { Text("Open APK") }
                }
            }
        }

        OutlinedTextField(
            value = execution.command,
            onValueChange = onCommandChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Command") },
            singleLine = true,
            enabled = !execution.running && !artifact.downloading,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCommandChange("pwd") }, enabled = !execution.running && !artifact.downloading) { Text("pwd") }
            OutlinedButton(onClick = { onCommandChange("git status --short --branch") }, enabled = !execution.running && !artifact.downloading) { Text("git status") }
        }

        Button(
            onClick = onRun,
            enabled = profile != null && state.hasStoredSecret && execution.command.isNotBlank() && !execution.running && !artifact.downloading,
        ) { Text(if (execution.running) "Running…" else "Run") }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Output", style = MaterialTheme.typography.titleMedium)
                when {
                    execution.running -> Text("Running ${execution.command}")
                    execution.connectionError != null -> Text("Connection error: ${execution.connectionError}")
                    execution.exitCode != null -> Text(if (execution.exitCode == 0) "Completed successfully (exit 0)" else "Remote command failed (exit ${execution.exitCode})")
                    else -> Text("Ready")
                }
                Text(execution.combinedOutput.ifBlank { "No output yet." }, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCopy(execution.command) }, enabled = execution.command.isNotBlank()) { Text("Copy command") }
                    OutlinedButton(onClick = { onCopy(execution.combinedOutput) }, enabled = execution.combinedOutput.isNotBlank()) { Text("Copy output") }
                }
            }
        }
    }
}

@Composable
private fun ProjectDialog(
    state: ProjectEditorState,
    onChange: ((ProjectEditorState) -> ProjectEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.name, onValueChange = { value -> onChange { it.copy(name = value) } }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = state.remotePath, onValueChange = { value -> onChange { it.copy(remotePath = value) } }, label = { Text("Remote path") }, supportingText = { Text("Absolute directory on the SSH server, e.g. /home/freya/Projects/my-taskOS") })
                OutlinedTextField(value = state.testCommand, onValueChange = { value -> onChange { it.copy(testCommand = value) } }, label = { Text("Test command") }, singleLine = true)
                OutlinedTextField(value = state.buildCommand, onValueChange = { value -> onChange { it.copy(buildCommand = value) } }, label = { Text("Build command") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.name.isNotBlank() && state.remotePath.isNotBlank()) { Text("Save") }
        },
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
                Text("On the first connection PocketDev will show the server fingerprint for confirmation. You do not need to enter it manually.", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChange { it.copy(authMode = AuthMode.PASSWORD) } }, enabled = state.authMode != AuthMode.PASSWORD) { Text("Password") }
                    OutlinedButton(onClick = { onChange { it.copy(authMode = AuthMode.PRIVATE_KEY) } }, enabled = state.authMode != AuthMode.PRIVATE_KEY) { Text("Private key") }
                }

                OutlinedTextField(
                    value = state.secret,
                    onValueChange = { value -> onChange { it.copy(secret = value) } },
                    label = { Text(if (state.authMode == AuthMode.PASSWORD) "Password" else "Private key (PEM)") },
                    visualTransformation = if (state.authMode == AuthMode.PASSWORD) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    supportingText = { Text("Leave empty to keep the existing stored secret.") },
                    minLines = if (state.authMode == AuthMode.PRIVATE_KEY) 4 else 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.host.isNotBlank() && state.username.isNotBlank() && state.port.toIntOrNull() != null) { Text("Save") }
        },
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
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Android could not open this APK: ${it.message}", Toast.LENGTH_LONG).show()
        }
}
