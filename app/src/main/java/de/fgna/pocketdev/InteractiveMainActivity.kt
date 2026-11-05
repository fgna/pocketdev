package de.fgna.pocketdev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.ssh.SshjCommandExecutor
import de.fgna.pocketdev.ssh.CommandInputHandle
import de.fgna.pocketdev.transfer.FileTransferScreen
import de.fgna.pocketdev.transfer.TransferActivityRegistry
import de.fgna.pocketdev.ui.PocketDevTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InteractiveMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PocketDevViewModel = viewModel()
            PocketDevTheme {
                PocketDevInteractiveApp(vm)
            }
        }
    }

    override fun onPause() {
        // Protect real SSH commands and real data transfers before Android backgrounds the
        // Activity. Idle app switching must not create a phantom foreground notification.
        val commands = SshjCommandExecutor.activeUserCommandCount()
        val transfers = TransferActivityRegistry.activeCount()
        if (commands > 0 || transfers > 0) {
            CommandKeepAliveService.ensureRunning(this, transferHint = commands == 0 && transfers > 0)
        }
        super.onPause()
    }
}

@Composable
private fun PocketDevInteractiveApp(vm: PocketDevViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val command = state.command
    val activeProjectId = state.project?.id.orEmpty()
    var inputTarget by remember(activeProjectId) { mutableStateOf<CommandInputHandle?>(null) }
    val anyCommandRunning = state.sessions.values.any { it.command.running }
    val anyArtifactDownloading = state.sessions.values.any { it.artifact.downloading }
    var showFiles by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* A foreground service can still run if notification permission is declined. */ }

    LaunchedEffect(anyCommandRunning, anyArtifactDownloading) {
        if (anyCommandRunning || anyArtifactDownloading) {
            CommandKeepAliveService.ensureRunning(
                context,
                transferHint = anyArtifactDownloading && !anyCommandRunning,
            )
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    suspend fun cancelActiveCommand(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            activeProjectId.isNotBlank() && SshjCommandExecutor.cancelMatching(activeProjectId)
        }.getOrDefault(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showTerminal && state.profile != null && state.project != null) {
            PtyTerminalScreen(
                profile = state.profile!!,
                projectPath = state.project!!.remotePath,
                onBack = { showTerminal = false },
            )
        } else if (showFiles) {
            FileTransferScreen(onBack = { showFiles = false })
        } else {
            PocketDevApp(vm)

            TextButton(
                onClick = { showFiles = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 166.dp, top = 9.dp),
            ) {
                Text("Files")
            }

            if (!command.running && state.profile != null && state.project != null && state.hasStoredSecret) {
                TextButton(
                    onClick = { showTerminal = true },
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 104.dp),
                ) { Text("Terminal · PTY") }
            }

            if (command.running) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 104.dp),
                ) {
                    Button(onClick = {
                        inputTarget = vm.captureCommandInput(activeProjectId)
                        if (inputTarget == null) {
                            Toast.makeText(context, "Command input is not available yet.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Input") }
                    Button(onClick = {
                        scope.launch {
                            val stopped = cancelActiveCommand()
                            if (!stopped) {
                                Toast.makeText(context, "Active remote command was not found.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Stop · Ctrl-C") }
                }
            }
        }
    }

    LaunchedEffect(command.running) {
        if (!command.running) inputTarget = null
    }
    // Automatic sudo prompts take precedence over an already open manual input dialog.
    // Preserve the manual dialog across normal output/status recompositions.
    if (command.running && command.awaitingSudoPassword) {
        key(activeProjectId, "sudo") {
            UnifiedInputDialog(
                sudo = true,
                onSubmit = { password ->
                    scope.launch {
                        val sent = withContext(Dispatchers.IO) {
                            runCatching {
                                activeProjectId.isNotBlank() && SshjCommandExecutor.sendInputTo(activeProjectId, password)
                            }.getOrDefault(false)
                        }
                        if (!sent) {
                            Toast.makeText(context, "The sudo prompt is no longer active.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { scope.launch { cancelActiveCommand() } },
            )
        }
    } else {
        inputTarget?.takeIf { command.running && it.isActive }?.let { target ->
            key(target) {
                UnifiedInputDialog(
                    sudo = false,
                    onSubmit = { reply ->
                        inputTarget = null
                        scope.launch {
                            val sent = withContext(Dispatchers.IO) { target.sendLine(reply) }
                            if (!sent) {
                                Toast.makeText(context, "Input could not be sent. The command may have ended.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = { inputTarget = null },
                )
            }
        }
    }
}

@Composable
private fun UnifiedInputDialog(
    sudo: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reply by remember(sudo) { mutableStateOf("") }
    var passwordVisible by remember(sudo) { mutableStateOf(false) }
    // Sudo credentials are masked by default. Generic command input can also be masked.
    var sensitive by remember(sudo) { mutableStateOf(sudo) }
    val masked = sensitive && !passwordVisible
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sudo) "Input · sudo password" else "Command input") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (sudo) "The running command requests sudo authentication."
                    else "Send a reply to the running command. An empty reply sends Enter."
                )
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    label = { Text(if (sudo) "Password" else "Reply") },
                    visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = if (sensitive) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                    singleLine = sudo,
                    minLines = if (sudo) 1 else 2,
                    maxLines = if (sudo) 1 else 5,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (!sensitive) sensitive = true
                            else passwordVisible = !passwordVisible
                        }) {
                            Icon(
                                imageVector = if (masked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (masked) "Show input" else "Hide input",
                            )
                        }
                    },
                )
                if (!sudo) {
                    TextButton(onClick = {
                        sensitive = !sensitive
                        passwordVisible = false
                    }) {
                        Text(if (sensitive) "Password mode on" else "Password mode off")
                    }
                }
                Text(
                    if (sudo) "Cancel stops the waiting command. The password is not stored by PocketDev."
                    else "PocketDev does not save replies. The remote program may echo ordinary input.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sudo || reply.isNotEmpty(),
                onClick = {
                    val value = reply
                    reply = ""
                    passwordVisible = false
                    onSubmit(value)
                },
            ) { Text(if (sudo) "Continue" else "Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (sudo) "Cancel command" else "Cancel") }
        },
    )
}
