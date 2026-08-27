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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.ssh.SshjCommandExecutor
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
        // Start foreground protection before Android backgrounds the Activity, but only
        // when the executor actually owns a user command. Starting the service while idle
        // creates a misleading "command running" notification with nothing to control.
        if (SshjCommandExecutor.activeUserCommandCount() > 0) {
            CommandKeepAliveService.ensureRunning(this)
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
    val anyCommandRunning = state.sessions.values.any { it.command.running }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* A foreground service can still run if notification permission is declined. */ }

    LaunchedEffect(anyCommandRunning) {
        if (anyCommandRunning) {
            CommandKeepAliveService.ensureRunning(context)
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
        PocketDevApp(vm)

        if (command.running && !command.awaitingSudoPassword) {
            Button(
                onClick = {
                    scope.launch {
                        val stopped = cancelActiveCommand()
                        if (!stopped) {
                            Toast.makeText(context, "Active remote command was not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 104.dp),
            ) {
                Text("Stop · Ctrl-C")
            }
        }
    }

    if (command.awaitingSudoPassword) {
        SudoPasswordDialog(
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
            onCancel = {
                scope.launch {
                    cancelActiveCommand()
                }
            },
        )
    }
}

@Composable
private fun SudoPasswordDialog(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("sudo password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The remote command requested sudo authentication. The password is sent only to this running command and is not stored by PocketDev.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text(
                    "Cancel stops the waiting remote command.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = {
                    val value = password
                    password = ""
                    onSubmit(value)
                },
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel command") } },
    )
}
