package de.fgna.pocketdev.transfer

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FileTransferScreen(
    onBack: () -> Unit,
    vm: FileTransferViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val choosePhoneFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setPhoneTree(uri)
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onBack) { Text("Back") }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Files", style = MaterialTheme.typography.titleLarge)
                            Text("Project-independent transfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Refresh") }
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }

            TransferSection(title = "Server") {
                OutlinedTextField(
                    value = state.serverPath,
                    onValueChange = vm::setServerPath,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Transfer directory") },
                    supportingText = {
                        Text(state.resolvedServerPath ?: "Default: ${SshFileTransferClient.DEFAULT_SERVER_PATH}")
                    },
                    enabled = !state.busy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::saveServerPath, enabled = !state.busy) { Text("Save path") }
                    Text(
                        "${state.selectedServerFiles.size} selected",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FileList(
                    entries = state.serverFiles.map { Triple(it.name, it.sizeBytes, it.directory) },
                    selected = state.selectedServerFiles,
                    enabled = !state.busy,
                    onToggle = vm::toggleServerFile,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = vm::downloadSelected,
                    enabled = !state.busy && state.selectedServerFiles.isNotEmpty() && state.phoneTreeUri != null,
                ) { Text("Server → Phone") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = vm::uploadSelected,
                    enabled = !state.busy && state.selectedPhoneFiles.isNotEmpty(),
                ) { Text("Phone → Server") }
            }

            TransferSection(title = "Phone") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { choosePhoneFolder.launch(null) }, enabled = !state.busy) {
                        Text(if (state.phoneTreeUri == null) "Choose folder" else "Change folder")
                    }
                    Text(
                        if (state.phoneTreeUri == null) "No transfer folder selected" else "Dedicated folder selected",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${state.selectedPhoneFiles.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FileList(
                    entries = state.phoneFiles.map { Triple(it.name, it.sizeBytes, it.directory) },
                    selected = state.selectedPhoneFiles,
                    keys = state.phoneFiles.map { it.uri },
                    enabled = !state.busy,
                    onToggle = vm::togglePhoneFile,
                )
            }
        }
    }
}

@Composable
private fun TransferSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FileList(
    entries: List<Triple<String, Long, Boolean>>,
    selected: Set<String>,
    enabled: Boolean,
    keys: List<String> = entries.map { it.first },
    onToggle: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        Text("No files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    entries.forEachIndexed { index, (name, size, directory) ->
        val key = keys[index]
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (!directory) onToggle(key) },
            enabled = enabled && !directory,
        ) {
            Text(
                buildString {
                    append(if (key in selected) "[x] " else "[ ] ")
                    if (directory) append("DIR · ")
                    append(name)
                    if (!directory) append(" · ${formatBytes(size)}")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
