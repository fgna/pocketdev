package de.fgna.pocketdev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.fgna.pocketdev.data.SshProfileRepository
import de.fgna.pocketdev.ssh.PtyTerminalSession
import de.fgna.pocketdev.ssh.SshProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First vertical PTY slice: real interactive shell with bounded readable output
 * and character input. Full-screen cursor-addressing terminal emulation is follow-up.
 */
@Composable
fun PtyTerminalScreen(
    profile: SshProfile,
    projectPath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val terminal = remember { PtyTerminalSession() }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()

    DisposableEffect(terminal) { onDispose { terminal.close() } }

    LaunchedEffect(profile, projectPath) {
        try {
            val secret = withContext(Dispatchers.IO) { SshProfileRepository(context).loadSecret() }
                ?: error("No SSH credentials stored")
            val code = terminal.run(profile, secret, projectPath) { chunk ->
                // Display best-effort plain text; PTY itself preserves interactive input.
                val readable = chunk.replace(Regex("\\u001B\\[[0-?]*[ -/]*[@-~]"), "")
                    .replace(Regex("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)"), "")
                output = (output + readable).takeLast(100_000)
            }
            output += "\n[Session ended: $code]"
        } catch (error: Exception) {
            output += "\n[Terminal error: ${error.message}]"
        } finally {
            running = false
        }
    }
    LaunchedEffect(output.length) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SSH PTY · ${profile.host}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { terminal.close(); onBack() }) { Text("Close") }
        }
        Text(projectPath, style = MaterialTheme.typography.bodySmall)
        Text(
            text = output.ifEmpty { "Connecting to terminal..." },
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(scroll).padding(8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            enabled = running,
            label = { Text("Terminal input") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = running, onClick = {
                terminal.send(input + "\n")
                input = ""
            }) { Text("Send / Enter") }
            TextButton(enabled = running, onClick = { terminal.send("\n") }) { Text("Enter") }
            TextButton(enabled = running, onClick = { terminal.interrupt() }) { Text("Ctrl-C") }
            TextButton(enabled = running, onClick = { terminal.send("\u0004") }) { Text("Ctrl-D") }
        }
    }
}
