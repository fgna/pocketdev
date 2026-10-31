package de.fgna.pocketdev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.fgna.pocketdev.project.ProjectConfig

@Composable
internal fun CommandFavorites(
    project: ProjectConfig,
    command: String,
    canLoad: Boolean,
    onFavoriteChange: (String, String, Boolean) -> Unit,
    onLoad: (String, String) -> Unit,
) {
    var open by remember(project.id) { mutableStateOf(false) }
    val saved = command in project.commandFavorites
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        IconToggleButton(
            checked = saved,
            onCheckedChange = { onFavoriteChange(project.id, command, it) },
            enabled = command.isNotBlank(),
        ) {
            Icon(
                imageVector = if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (saved) "Remove command from favorites" else "Save command to favorites",
            )
        }
        TextButton(onClick = { open = true }) { Text("Favorites (${project.commandFavorites.size})") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Project favorites") },
            text = {
                if (project.commandFavorites.isEmpty()) {
                    Text("Tap the star beside a command to save it for this project.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(project.commandFavorites, key = { it }) { favorite ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = canLoad,
                                    onClick = {
                                        onLoad(project.id, favorite)
                                        open = false
                                    },
                                ) {
                                    Text(favorite, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onFavoriteChange(project.id, favorite, false) }) {
                                    Icon(Icons.Filled.Star, contentDescription = "Remove favorite: $favorite")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
