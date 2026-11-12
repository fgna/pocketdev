package de.fgna.pocketdev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.ssh.SshProfile

/** Interactive PTY with a ViewModel-owned shell that survives Activity recreation. */
@Composable
fun PtyTerminalScreen(
    tabId: String,
    profile: SshProfile,
    projectPath: String,
    onBack: () -> Unit,
    vm: PtyTerminalViewModel = viewModel(key = "pty-$tabId"),
) {
    val state by vm.state.collectAsState()
    var input by rememberSaveable(tabId) { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(profile, projectPath, tabId) { vm.start(profile, projectPath) }
    LaunchedEffect(state.output) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SSH PTY · ${profile.host}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.close(); onBack() }) { Text("Close") }
        }
        Text(projectPath, style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(
            text = state.output.ifEmpty { if (state.running) "Connecting to terminal..." else "Terminal closed" },
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(scroll).padding(8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            enabled = state.running,
            label = { Text("Terminal input") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = state.running, onClick = { vm.send("\u001b[A") }) { Text("↑") }
            TextButton(enabled = state.running, onClick = { vm.send("\u001b[B") }) { Text("↓") }
            TextButton(enabled = state.running, onClick = { vm.send("\u001b[D") }) { Text("←") }
            TextButton(enabled = state.running, onClick = { vm.send("\u001b[C") }) { Text("→") }
            TextButton(enabled = state.running, onClick = { vm.send("\t") }) { Text("Tab") }
            TextButton(enabled = state.running, onClick = { vm.send("\u001b") }) { Text("Esc") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = state.running && state.columns > 40,
                onClick = { vm.resize(state.columns - 10, state.rows) }) { Text("− cols") }
            Text("${state.columns} × ${state.rows}", modifier = Modifier.padding(top = 12.dp))
            TextButton(enabled = state.running && state.columns < 160,
                onClick = { vm.resize(state.columns + 10, state.rows) }) { Text("+ cols") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = state.running, onClick = {
                vm.send(input + "\n") { input = "" }
            }) { Text("Send / Enter") }
            TextButton(enabled = state.running, onClick = { vm.send("\n") }) { Text("Enter") }
            TextButton(enabled = state.running, onClick = vm::interrupt) { Text("Ctrl-C") }
            TextButton(enabled = state.running, onClick = { vm.send("\u0004") }) { Text("Ctrl-D") }
        }
    }
}
