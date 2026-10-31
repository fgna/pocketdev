package de.fgna.pocketdev.data

import de.fgna.pocketdev.project.ProjectConfig
import de.fgna.pocketdev.project.ActionProfileTemplates

internal object ProjectConfigCodec {
    fun encode(projects: List<ProjectConfig>): String = projects.joinToString("\n") { project ->
        listOf(
            project.id, project.name, project.remotePath, project.testCommand,
            project.buildCommand, project.githubRepository, project.actionProfileId,
            ActionDefinitionCodec.encode(project.customActions), project.actionOrder.joinToString(","),
            project.commandFavorites.joinToString(",", transform = ::encodeField),
        ).joinToString("|", transform = ::encodeField)
    }

    fun decode(value: String): List<ProjectConfig> = value.lineSequence()
        .filter { it.isNotBlank() }.mapNotNull(::decodeProject).toList()

    private fun decodeProject(row: String): ProjectConfig? = runCatching {
        val fields = row.split('|').map(::decodeField)
        require(fields.size in listOf(6, 8, 9, 10) && fields[0].isNotBlank())
        ProjectConfig(
            id = fields[0], name = fields[1], remotePath = fields[2],
            testCommand = fields[3], buildCommand = fields[4], githubRepository = fields[5],
            actionProfileId = if (fields.size >= 8) fields[6] else ActionProfileTemplates.android.id,
            customActions = if (fields.size >= 8) ActionDefinitionCodec.decode(fields[7]) else emptyList(),
            actionOrder = if (fields.size >= 9 && fields[8].isNotBlank()) fields[8].split(",") else emptyList(),
            commandFavorites = if (fields.size == 10 && fields[9].isNotEmpty()) {
                fields[9].split(',').map(::decodeField).filter { it.isNotBlank() }.distinct()
            } else emptyList(),
        ).also { require(it.actionOrder.distinct().size == it.actionOrder.size) }
    }.getOrNull()
}

