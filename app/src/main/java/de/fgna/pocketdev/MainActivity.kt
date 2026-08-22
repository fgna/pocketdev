package de.fgna.pocketdev

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketDevApp()
        }
    }
}

@Composable
fun PocketDevApp() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PocketDevHome()
        }
    }
}

@Composable
private fun PocketDevHome() {
    var command by remember { mutableStateOf("pwd") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PocketDev", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Mobile control surface for a remote development server",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("SSH server", style = MaterialTheme.typography.titleMedium)
                Text("Not configured", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { /* Sprint 1 issue #3 */ }) {
                    Text("Configure")
                }
            }
        }

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Command") },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { command = "pwd" }) {
                Text("pwd")
            }
            Button(onClick = { command = "git status --short --branch" }) {
                Text("git status")
            }
        }

        Button(
            onClick = { /* SSH execution arrives in Sprint 1 issue #2/#4 */ },
            enabled = false,
        ) {
            Text("Run")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Output", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Connect to a server to run commands.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
