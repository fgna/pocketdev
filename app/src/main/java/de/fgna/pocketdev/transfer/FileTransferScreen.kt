package de.fgna.pocketdev.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FileTransferScreen(
    onBack: () -> Unit,
    vm: FileTransferViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmDeleteSelected by remember { mutableStateOf<DeleteSelectionTarget?>(null) }
    var serverDeleteQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var phoneDeleteQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedPhoneFolder = state.phoneTreeUri?.let { value ->
        runCatching {
            val uri = Uri.parse(value)
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
        }.getOrNull()
    }
    val choosePhoneFolder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data
        val uri = data?.data ?: return@rememberLauncherForActivityResult
        val grantedFlags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val requiredFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if ((grantedFlags and requiredFlags) != requiredFlags) {
            vm.reportPhoneFolderError("Android did not grant read/write access to this folder. Please choose another folder.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, grantedFlags)
        }.onSuccess {
            vm.setPhoneTree(uri)
        }.onFailure { error ->
            vm.reportPhoneFolderError(
                "Could not keep access to this folder: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshPhoneFiles()
        vm.refresh()
    }
    LaunchedEffect(state.phoneTreeUri) {
        if (state.phoneTreeUri != null) vm.refreshPhoneFiles()
    }
    LaunchedEffect(state.busy, state.error, serverDeleteQueue) {
        if (!state.busy && serverDeleteQueue.isNotEmpty()) {
            if (state.error != null) {
                serverDeleteQueue = emptyList()
            } else {
                val next = serverDeleteQueue.first()
                serverDeleteQueue = serverDeleteQueue.drop(1)
                vm.deleteServerFile(next)
            }
        }
    }
    LaunchedEffect(state.busy, state.error, phoneDeleteQueue) {
        if (!state.busy && phoneDeleteQueue.isNotEmpty()) {
            if (state.error != null) {
                phoneDeleteQueue = emptyList()
            } else {
                val next = phoneDeleteQueue.first()
                phoneDeleteQueue = phoneDeleteQueue.drop(1)
                vm.deletePhoneFile(next)
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onBack) { Text("Back") }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Files", style = MaterialTheme.typography.titleLarge)
                            Text("Project-independent transfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                vm.refreshPhoneFiles()
                                vm.refresh()
                            },
                            enabled = !state.busy,
                        ) { Text("Refresh") }
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
                if (state.selectedServerFiles.isNotEmpty()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDeleteSelected = DeleteSelectionTarget.SERVER },
                        enabled = !state.busy && serverDeleteQueue.isEmpty(),
                    ) {
                        Text("Delete selected (${state.selectedServerFiles.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
                FileList(
                    entries = state.serverFiles.map { Triple(it.name, it.sizeBytes, it.directory) },
                    selected = state.selectedServerFiles,
                    enabled = !state.busy,
                    onToggle = vm::toggleServerFile,
                    onDelete = vm::deleteServerFile,
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Transfer directory",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when {
                                state.phoneTreeUri == null -> "No folder selected"
                                !selectedPhoneFolder.isNullOrBlank() -> selectedPhoneFolder
                                else -> "Selected folder"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Text(
                    if (state.phoneTreeUri == null) "Choose a dedicated Android transfer folder." else "Saved Android transfer folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                            }
                            choosePhoneFolder.launch(intent)
                        },
                        enabled = !state.busy,
                    ) {
                        Text(if (state.phoneTreeUri == null) "Choose folder" else "Change folder")
                    }
                    Text(
                        "${state.selectedPhoneFiles.size} selected",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.selectedPhoneFiles.isNotEmpty()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDeleteSelected = DeleteSelectionTarget.PHONE },
                        enabled = !state.busy && phoneDeleteQueue.isEmpty(),
                    ) {
                        Text("Delete selected (${state.selectedPhoneFiles.size})", color = MaterialTheme.colorScheme.error)
                    }
                }
                FileList(
                    entries = state.phoneFiles.map { Triple(it.name, it.sizeBytes, it.directory) },
                    selected = state.selectedPhoneFiles,
                    keys = state.phoneFiles.map { it.uri },
                    enabled = !state.busy,
                    onToggle = vm::togglePhoneFile,
                    onDelete = vm::deletePhoneFile,
                )
            }
        }
    }

    confirmDeleteSelected?.let { target ->
        val selected = when (target) {
            DeleteSelectionTarget.SERVER -> state.selectedServerFiles.toList()
            DeleteSelectionTarget.PHONE -> state.selectedPhoneFiles.toList()
        }
        val location = if (target == DeleteSelectionTarget.SERVER) "server" else "phone"
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = null },
            title = { Text("Delete selected files?") },
            text = {
                Text(
                    "Delete ${selected.size} selected ${if (selected.size == 1) "file" else "files"} from the $location transfer directory?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSelected = null
                        when (target) {
                            DeleteSelectionTarget.SERVER -> {
                                serverDeleteQueue = selected.drop(1)
                                selected.firstOrNull()?.let(vm::deleteServerFile)
                            }
                            DeleteSelectionTarget.PHONE -> {
                                phoneDeleteQueue = selected.drop(1)
                                selected.firstOrNull()?.let(vm::deletePhoneFile)
                            }
                        }
                    },
                    enabled = selected.isNotEmpty() && !state.busy,
                ) { Text("Delete ${selected.size}", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = null }) { Text("Cancel") } },
        )
    }
}

private enum class DeleteSelectionTarget {
    SERVER,
    PHONE,
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
    onDelete: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        Text("No files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    entries.forEachIndexed { index, (name, size, directory) ->
        FileRow(
            name = name,
            size = size,
            directory = directory,
            key = keys[index],
            selected = keys[index] in selected,
            enabled = enabled,
            onToggle = onToggle,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun FileRow(
    name: String,
    size: Long,
    directory: Boolean,
    key: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDelete by remember(key) { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = { if (!directory) onToggle(key) },
            enabled = enabled && !directory,
        ) {
            Text(
                buildString {
                    append(if (selected) "[x] " else "[ ] ")
                    if (directory) append("DIR · ")
                    append(name)
                    if (!directory) append(" · ${formatBytes(size)}")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!directory) {
            TextButton(
                onClick = { confirmDelete = true },
                enabled = enabled,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete file?") },
            text = { Text("Delete $name from this transfer directory?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(key)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
