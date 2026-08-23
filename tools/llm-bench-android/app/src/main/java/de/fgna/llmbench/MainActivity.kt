package de.fgna.llmbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BenchmarkScreen()
                }
            }
        }
    }
}

@Composable
private fun BenchmarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modelReady by remember { mutableStateOf(LocalModelStore.readyFile(context) != null) }
    var modelLabel by remember { mutableStateOf("Lokales LiteRT-LM Modell") }
    var modelInfo by remember {
        mutableStateOf(
            LocalModelStore.readyFile(context)?.let { "Modell vorhanden · ${formatMb(it.length())} MB" }
                ?: "Kein Modell importiert",
        )
    }
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf(emptyList<BenchmarkResult>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                running = true
                error = null
                modelInfo = "Modell wird importiert …"
                runCatching { LocalModelStore.import(context, uri) }
                    .onSuccess { bytes ->
                        modelReady = true
                        modelInfo = "Modell importiert · ${formatMb(bytes)} MB"
                        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { modelLabel = it }
                        results = emptyList()
                    }
                    .onFailure { t ->
                        modelReady = false
                        modelInfo = "Kein Modell importiert"
                        error = t.message ?: t::class.java.simpleName
                    }
                running = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LLM Bench", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Direkter On-Device-Benchmark für .litertlm-Modelle mit derselben LiteRT-LM-Runtime wie TaskOS.")

        Text(modelInfo)
        OutlinedTextField(
            value = modelLabel,
            onValueChange = { modelLabel = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Modellname für Ergebnis/Export") },
            enabled = !running,
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = { picker.launch("*/*") }) {
                Text(".litertlm importieren")
            }
            Button(
                enabled = modelReady && !running,
                onClick = {
                    val model = LocalModelStore.readyFile(context) ?: return@Button
                    total = BenchmarkSuite.defaultPrompts.size
                    completed = 0
                    results = emptyList()
                    error = null
                    running = true
                    scope.launch {
                        try {
                            val engine = LiteRtBenchmarkEngine(model.absolutePath, modelLabel.ifBlank { "Unbenanntes Modell" })
                            BenchmarkSuite.defaultPrompts.forEach { prompt ->
                                results = results + engine.run(prompt)
                                completed += 1
                            }
                        } finally {
                            running = false
                        }
                    }
                },
            ) {
                Text(if (running) "Läuft …" else "Benchmark starten")
            }
        }

        Button(
            enabled = results.isNotEmpty() && !running,
            onClick = {
                val csv = BenchmarkCsv.export(results)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "LLM Bench Ergebnisse")
                    putExtra(Intent.EXTRA_TEXT, csv)
                }
                context.startActivity(Intent.createChooser(intent, "CSV exportieren"))
            },
        ) {
            Text("CSV exportieren")
        }

        Text("Fortschritt: $completed / $total")
        Text("Erster Lauf ist cold, weitere Prompts nutzen das geladene Modell warm. GPU wird bevorzugt; bei Fehlern erfolgt CPU-Fallback.", style = MaterialTheme.typography.bodySmall)
        Text("Tokenzahl und tok/s sind derzeit tokenizer-unabhängige Schätzwerte.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text("Fehler: $it", color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results.asReversed(), key = { "${it.model}-${it.prompt.id}" }) { result ->
                ResultCard(result)
            }
        }
    }
}

@Composable
private fun ResultCard(result: BenchmarkResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${result.model} · ${result.prompt.category}", fontWeight = FontWeight.SemiBold)
            Text(result.prompt.text, style = MaterialTheme.typography.bodySmall)
            Text(
                "TTFT ${result.ttftMs} ms · Gesamt ${result.totalMs} ms · " +
                    "${String.format(Locale.US, "%.1f", result.tokensPerSecond)} tok/s · " +
                    "${result.outputTokens} Tokens (${result.tokenCountSource.name.lowercase()})",
            )
            if (result.error != null) {
                Text("Fehler: ${result.error}", color = MaterialTheme.colorScheme.error)
            } else {
                Text(result.output.take(700))
            }
        }
    }
}

private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)
