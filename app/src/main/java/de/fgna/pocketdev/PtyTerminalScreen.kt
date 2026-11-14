package de.fgna.pocketdev

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
    val horizontalScroll = rememberScrollState()
    LaunchedEffect(profile, projectPath, tabId) { vm.start(profile, projectPath) }
    LaunchedEffect(state.output) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Interactive SSH · ${profile.host}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.close(); onBack() }) { Text("Close") }
        }
        Text(projectPath, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(
            text = state.output.ifEmpty { if (state.running) "Connecting to terminal..." else "Terminal closed" },
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Color(0xFF171A1B))
                .verticalScroll(scroll).horizontalScroll(horizontalScroll).padding(8.dp),
            color = Color(0xFFF0F2F2),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            softWrap = false,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            enabled = state.running,
            placeholder = { Text("Command or reply") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
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
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(enabled = state.running, onClick = {
                vm.send(input + "\n") { input = "" }
            }) { Text("Send") }
            TextButton(enabled = state.running, onClick = { vm.send("\n") }) { Text("Enter") }
            TextButton(enabled = state.running, onClick = vm::interrupt) { Text("Ctrl-C") }
            TextButton(enabled = state.running, onClick = { vm.send("\u0004") }) { Text("Ctrl-D") }
        }
    }
}
