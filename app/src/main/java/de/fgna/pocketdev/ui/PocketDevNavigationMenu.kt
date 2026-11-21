package de.fgna.pocketdev.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Navigation shared by secondary PocketDev screens; the host owns destination changes. */
@Composable
fun PocketDevNavigationMenu(
    onNavigate: (String) -> Unit,
    terminalEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open navigation menu",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Workspace", "Server", "Projects", "Files", "Interactive SSH terminal").forEach { destination ->
                DropdownMenuItem(
                    text = { Text(destination) },
                    enabled = destination != "Interactive SSH terminal" || terminalEnabled,
                    onClick = {
                        expanded = false
                        onNavigate(destination)
                    },
                )
            }
        }
    }
}
