package de.fgna.pocketdev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.ui.PocketDevTheme

class TabbedMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PocketDevViewModel = viewModel()
            val state by vm.state.collectAsState()
            PocketDevTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        PocketDevApp(vm)
                    }
                    BottomProjectTabs(
                        projects = state.projects,
                        activeProjectId = state.project?.id,
                        sessions = state.sessions,
                        onSelect = vm::selectProject,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomProjectTabs(
    projects: List<ProjectConfig>,
    activeProjectId: String?,
    sessions: Map<String, ProjectSessionState>,
    onSelect: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Projects · ${projects.size}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (projects.isEmpty()) {
                    Text(
                        "No saved projects",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    projects.forEach { project ->
                        val session = sessions[project.id]
                        val running = session?.command?.running == true || session?.artifact?.downloading == true
                        val failed = session?.command?.exitCode?.let { it != 0 } == true ||
                            session?.command?.connectionError != null || session?.artifact?.error != null
                        val label = buildString {
                            if (running) append("● ") else if (failed) append("! ")
                            append(project.name)
                        }
                        if (project.id == activeProjectId) {
                            Button(onClick = { onSelect(project.id) }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { onSelect(project.id) }) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}
