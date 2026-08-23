package de.fgna.llmbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val client = remember { OpenAiBenchmarkClient() }

    var baseUrl by remember { mutableStateOf("http://127.0.0.1:8080") }
    var models by remember { mutableStateOf("qwen3-1.7b,qwen3.5-2b") }
    var apiKey by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf(emptyList<BenchmarkResult>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LLM Bench", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Identische Prompts, identische API, vergleichbare Messwerte auf diesem Gerät.")

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("OpenAI-kompatible Base URL") },
            singleLine = true,
            enabled = !running,
        )
        OutlinedTextField(
            value = models,
            onValueChange = { models = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Modelle, durch Komma getrennt") },
            supportingText = { Text("Beispiel: gemma,qwen3-1.7b,qwen3.5-2b") },
            enabled = !running,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API-Key, optional") },
            singleLine = true,
            enabled = !running,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running && models.split(',').any { it.isNotBlank() },
                onClick = {
                    val selectedModels = models.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    total = selectedModels.size * BenchmarkSuite.defaultPrompts.size
                    completed = 0
                    results = emptyList()
                    running = true
                    scope.launch {
                        try {
                            selectedModels.forEach { model ->
                                BenchmarkSuite.defaultPrompts.forEach { prompt ->
                                    val result = client.run(
                                        profile = BenchmarkProfile(baseUrl = baseUrl, model = model, apiKey = apiKey),
                                        prompt = prompt,
                                    )
                                    results = results + result
                                    completed += 1
                                }
                            }
                        } finally {
                            running = false
                        }
                    }
                },
            ) {
                Text(if (running) "Läuft …" else "Benchmark starten")
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
        }

        Text("Fortschritt: $completed / $total")
        Text("Tokens/s nutzt API-Usage, wenn geliefert, sonst eine gekennzeichnete Schätzung.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(2.dp))

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
                Text(result.output.take(500))
            }
        }
    }
}
