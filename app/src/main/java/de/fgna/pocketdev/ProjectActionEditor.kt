package de.fgna.pocketdev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fgna.pocketdev.project.ActionDefinition
import de.fgna.pocketdev.project.ActionProfile
import de.fgna.pocketdev.project.ActionProfileResolver
import de.fgna.pocketdev.project.ActionProfileTemplates
import java.util.UUID

@Composable
internal fun ProjectDialog(
    state: ProjectEditorState,
    editingExisting: Boolean,
    profiles: List<ActionProfile>,
    profileUseCount: Int,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (String, Boolean) -> Unit,
    onChange: ((ProjectEditorState) -> ProjectEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var switchTo by remember { mutableStateOf<ActionProfile?>(null) }
    var confirmProfileUpdate by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ActionDefinition?>(null) }
    val selectedProfile = profiles.firstOrNull { it.id == state.actionProfileId }
    var profileName by remember(state.actionProfileId) { mutableStateOf(selectedProfile?.name.orEmpty()) }
    val remotePathValid = state.remotePath.trim().let { it.startsWith('/') && it != "/" && '\u0000' !in it }
    val actionsValid = runCatching {
        state.actions.forEach(ActionProfileResolver::validate)
        require(state.actions.map { it.id }.distinct().size == state.actions.size)
    }.isSuccess
    val repositoryValid = state.githubRepository.isBlank() || Regex("^[^/\\s]+/[^/\\s]+$").matches(state.githubRepository.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingExisting) "Edit project" else "New project") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.name, onValueChange = { value -> onChange { it.copy(name = value) } }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = state.remotePath,
                    onValueChange = { value -> onChange { it.copy(remotePath = value) } },
                    label = { Text("Remote path") },
                    supportingText = { Text("Absolute project directory on the SSH server, e.g. /srv/service") },
                    isError = state.remotePath.isNotBlank() && !remotePathValid,
                )
                OutlinedTextField(
                    value = state.githubRepository,
                    onValueChange = { value -> onChange { it.copy(githubRepository = value) } },
                    label = { Text("GitHub repository (optional)") },
                    supportingText = { Text("owner/name; leave empty for an existing directory") },
                    isError = !repositoryValid, singleLine = true,
                )
                HorizontalDivider()
                Text("Action profile", style = MaterialTheme.typography.titleSmall)
                profiles.forEach { profile ->
                    OutlinedButton(onClick = { switchTo = profile }, enabled = profile.id != state.actionProfileId) {
                        Text(profile.name + if (profile.id == state.actionProfileId) " (selected)" else "")
                    }
                }
                Text("Actions for this project", style = MaterialTheme.typography.titleSmall)
                Text("Edit commands, turn buttons off, or move them up and down.", style = MaterialTheme.typography.bodySmall)
                state.actions.forEachIndexed { index, action ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = action.enabled, onCheckedChange = { enabled -> onChange { editor ->
                                editor.copy(actions = editor.actions.map { if (it.id == action.id) it.copy(enabled = enabled) else it })
                            } })
                            Text(action.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        }
                        Row {
                            TextButton(onClick = { draft = action }) { Text("Edit") }
                            TextButton(enabled = index > 0, onClick = { onChange { editor ->
                                editor.copy(actions = editor.actions.toMutableList().apply { add(index - 1, removeAt(index)) })
                            } }) { Text("Up") }
                            TextButton(enabled = index < state.actions.lastIndex, onClick = { onChange { editor ->
                                editor.copy(actions = editor.actions.toMutableList().apply { add(index + 1, removeAt(index)) })
                            } }) { Text("Down") }
                        }
                        if (selectedProfile?.actions?.none { it.id == action.id } == true) {
                            TextButton(onClick = { onChange { editor -> editor.copy(actions = editor.actions.filterNot { it.id == action.id }) } }) { Text("Remove") }
                        }
                        HorizontalDivider()
                    }
                }
                OutlinedButton(onClick = {
                    draft = ActionDefinition("custom-${UUID.randomUUID()}", "", "", requiresConfirmation = true)
                }) { Text("Add action") }
                Text("Reuse these actions", style = MaterialTheme.typography.titleSmall)
                Text("Save a profile to make it available to other projects. Built-in profiles stay unchanged.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Profile name") }, singleLine = true)
                OutlinedButton(enabled = profileName.isNotBlank() && selectedProfile != null && actionsValid, onClick = { onSaveProfile(profileName, false) }) { Text("Save as new profile") }
                if (selectedProfile != null && ActionProfileTemplates.find(selectedProfile.id) == null) {
                    OutlinedButton(enabled = profileName.isNotBlank() && actionsValid, onClick = { confirmProfileUpdate = true }) { Text("Update shared profile") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.name.isNotBlank() && remotePathValid && repositoryValid && selectedProfile != null && actionsValid) { Text("Save project") } },
        dismissButton = {
            Row {
                if (editingExisting) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    switchTo?.let { profile ->
        AlertDialog(
            onDismissRequest = { switchTo = null }, title = { Text("Use ${profile.name}?") },
            text = { Text("This replaces the action list in this editor. Save the project to apply it.") },
            confirmButton = { TextButton(onClick = { onSelectProfile(profile.id); switchTo = null }) { Text("Use profile") } },
            dismissButton = { TextButton(onClick = { switchTo = null }) { Text("Cancel") } },
        )
    }
    if (confirmProfileUpdate) {
        AlertDialog(
            onDismissRequest = { confirmProfileUpdate = false }, title = { Text("Update shared profile?") },
            text = { Text("$profileUseCount saved project(s) use this profile. They will receive the updated actions. Project-specific edits remain in place.") },
            confirmButton = { TextButton(onClick = { onSaveProfile(profileName, true); confirmProfileUpdate = false }) { Text("Update profile") } },
            dismissButton = { TextButton(onClick = { confirmProfileUpdate = false }) { Text("Cancel") } },
        )
    }
    draft?.let { action ->
        ActionEditDialog(action, onDismiss = { draft = null }, onSave = { edited ->
            onChange { editor ->
                val exists = editor.actions.any { it.id == edited.id }
                editor.copy(actions = if (exists) editor.actions.map { if (it.id == edited.id) edited else it } else editor.actions + edited)
            }
            draft = null
        })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, title = { Text("Delete project?") },
            text = { Text("This removes the saved PocketDev configuration. It does not delete the remote repository.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionEditDialog(action: ActionDefinition, onDismiss: () -> Unit, onSave: (ActionDefinition) -> Unit) {
    var edited by remember(action) { mutableStateOf(action) }
    val valid = runCatching { ActionProfileResolver.validate(edited) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Edit action") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = edited.label, onValueChange = { edited = edited.copy(label = it) }, label = { Text("Button label") }, singleLine = true)
                OutlinedTextField(value = edited.command, onValueChange = { edited = edited.copy(command = it) }, label = { Text("Remote shell command") }, minLines = 3, maxLines = 6)
                Row {
                    Checkbox(checked = edited.requiresConfirmation, onCheckedChange = { edited = edited.copy(requiresConfirmation = it) })
                    Text("Confirm before running")
                }
                Text("Runs on your SSH server. Use confirmation for actions that stop or change services.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(edited) }) { Text("Save action") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
