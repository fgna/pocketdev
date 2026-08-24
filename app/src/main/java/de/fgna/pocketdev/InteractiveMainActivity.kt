package de.fgna.pocketdev

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.artifact.ApkInstaller
import de.fgna.pocketdev.artifact.SshArtifactRetriever
import de.fgna.pocketdev.ssh.SshjCommandExecutor
import de.fgna.pocketdev.ui.PocketDevTheme
import java.io.File

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
}

@Composable
private fun PocketDevInteractiveApp(vm: PocketDevViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val command = state.command
    val projectPath = state.project?.remotePath.orEmpty()
    val artifactPath = state.artifact.localPath
    val artifactVerified = artifactPath?.let { path ->
        val apk = File(path)
        val marker = File(path + SshArtifactRetriever.VERIFIED_SUFFIX)
        apk.isFile && marker.isFile && marker.readText().trim().matches(Regex("^[0-9a-f]{64}$"))
    } == true

    Box(modifier = Modifier.fillMaxSize()) {
        PocketDevApp(vm)

        if (artifactVerified && artifactPath != null && !command.running && !state.artifact.downloading) {
            OutlinedButton(
                onClick = {
                    runCatching { ApkInstaller.install(context, File(artifactPath)) }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                "Could not start APK install: ${error.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 104.dp),
            ) {
                Text("Install verified APK")
            }
        }

        if (command.running && !command.awaitingSudoPassword) {
            Button(
                onClick = {
                    val stopped =
                        (projectPath.isNotBlank() && SshjCommandExecutor.cancelMatching(projectPath)) ||
                            SshjCommandExecutor.cancelMatching(command.command)
                    if (!stopped) {
                        Toast.makeText(context, "Active remote command was not found.", Toast.LENGTH_SHORT).show()
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
                val sent =
                    (projectPath.isNotBlank() && SshjCommandExecutor.sendInputTo(projectPath, password)) ||
                        SshjCommandExecutor.sendInputTo(command.command, password)
                if (!sent) {
                    Toast.makeText(context, "The sudo prompt is no longer active.", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = {
                (projectPath.isNotBlank() && SshjCommandExecutor.cancelMatching(projectPath)) ||
                    SshjCommandExecutor.cancelMatching(command.command)
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
