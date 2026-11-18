package de.fgna.pocketdev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.fgna.pocketdev.ssh.LocalPortForwardConfig
import de.fgna.pocketdev.ssh.LocalPortForwardState
import de.fgna.pocketdev.ssh.SshProfile

@Composable
internal fun LocalPortForwardDialog(
    profile: SshProfile?,
    hasStoredSecret: Boolean,
    state: LocalPortForwardState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val config = when (state) {
        is LocalPortForwardState.Starting -> state.config
        is LocalPortForwardState.Active -> state.config
        is LocalPortForwardState.Failed -> state.config
        else -> LocalPortForwardConfig(1455, "127.0.0.1", 1455)
    }
    var localPort by rememberSaveable { mutableStateOf(config.localPort.toString()) }
    var remoteHost by rememberSaveable { mutableStateOf(config.remoteHost) }
    var remotePort by rememberSaveable { mutableStateOf(config.remotePort.toString()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = state is LocalPortForwardState.Starting || state is LocalPortForwardState.Active
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH browser authentication tunnel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(profile?.let { "${it.username}@${it.host}:${it.port}" } ?: "Configure Server first.")
                Text(localPortForwardStatus(state), color = if (state is LocalPortForwardState.Failed)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("Phone 127.0.0.1 only → server destination. Keep this tunnel active while signing in through your browser. Other apps on this phone can access the local port.")
                OutlinedTextField(localPort, { localPort = it }, label = { Text("Phone local port") },
                    enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(remoteHost, { remoteHost = it }, label = { Text("Destination host on server network") },
                    enabled = !busy, singleLine = true)
                OutlinedTextField(remotePort, { remotePort = it }, label = { Text("Destination port") },
                    enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (profile?.hostKeySha256.isNullOrBlank() || !hasStoredSecret) {
                    Text("Connect and trust the host, and save SSH credentials in Server before starting.")
                }
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Codex: run codex login, start 1455 → 127.0.0.1:1455, then open its printed sign-in URL in your browser. Return and run codex login status. Stop the tunnel when finished.")
                Text("Android may stop background work. Tunnel state resets to Stopped after app process death; restart explicitly.")
                if (busy) OutlinedButton(onClick = { LocalPortForwardService.stop(context) }) { Text("Stop tunnel") }
                else Button(
                    enabled = profile != null && profile.hostKeySha256.isNotBlank() && hasStoredSecret,
                    onClick = {
                        val requested = runCatching {
                            LocalPortForwardConfig(
                                localPort.toIntOrNull() ?: throw IllegalArgumentException("Local port must be in 1..65535"),
                                remoteHost,
                                remotePort.toIntOrNull() ?: throw IllegalArgumentException("Remote port must be in 1..65535"),
                            )
                        }
                        requested.onSuccess {
                            validationError = null
                            LocalPortForwardService.start(context, it)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.onFailure { validationError = it.message }
                    },
                ) { Text("Start tunnel") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
