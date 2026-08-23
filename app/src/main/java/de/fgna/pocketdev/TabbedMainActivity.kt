package de.fgna.pocketdev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
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
                Box(modifier = Modifier.fillMaxSize()) {
                    PocketDevApp(vm)
                    BottomProjectTabs(
                        projects = state.projects,
                        activeProjectId = state.project?.id,
                        sessions = state.sessions,
                        onSelect = vm::selectProject,
                        modifier = Modifier.align(Alignment.BottomCenter),
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
    modifier: Modifier = Modifier,
) {
    if (projects.size < 2) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        androidx.compose.foundation.layout.Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
