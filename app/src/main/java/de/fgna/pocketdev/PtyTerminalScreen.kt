package de.fgna.pocketdev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
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
import de.fgna.pocketdev.ui.PocketDevSectionLabel
import de.fgna.pocketdev.ui.PocketDevHairlineSurface

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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidthPx = textMeasurer.measure("M", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)).size.width.coerceAtLeast(1)
    val horizontalPaddingPx = with(density) { 16.dp.roundToPx() }
    LaunchedEffect(profile, projectPath, tabId) { vm.start(profile, projectPath) }
    LaunchedEffect(state.output) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Terminal · ${profile.host}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.close(); onBack() }) { Text("Close") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PocketDevHairlineSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                PocketDevSectionLabel("Workspace")
                Text(projectPath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PocketDevSectionLabel("Live output")
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()
            .background(Color(0xFF171A1B), MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .onSizeChanged { size ->
                val columns = ((size.width - horizontalPaddingPx).coerceAtLeast(charWidthPx) / charWidthPx).coerceIn(20, 160)
                vm.resizeToViewport(columns)
            }) {
            Text(
                text = state.output.ifEmpty { if (state.running) "Connecting to terminal..." else "Terminal closed" },
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).horizontalScroll(horizontalScroll).padding(8.dp),
                color = Color(0xFFF0F2F2),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                softWrap = false,
            )
        }
        PocketDevSectionLabel("Input")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            enabled = state.running,
            placeholder = { Text("Command or reply") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(enabled = state.running, onClick = { vm.send(input + "\n") { input = "" } }) { Text("Send") }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u001b[A") }) { Text("↑") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u001b[B") }) { Text("↓") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u001b[D") }) { Text("←") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u001b[C") }) { Text("→") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\t") }) { Text("Tab") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u001b") }) { Text("Esc") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = state.running, onClick = { vm.send("\n") }) { Text("Enter") }
            OutlinedButton(enabled = state.running, onClick = vm::interrupt) { Text("Ctrl-C") }
            OutlinedButton(enabled = state.running, onClick = { vm.send("\u0004") }) { Text("Ctrl-D") }
        }
        }
    }
}
