package de.fgna.pocketdev.data

import android.content.Context
import de.fgna.pocketdev.project.ProjectConfig
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class ProjectCollection(
    val projects: List<ProjectConfig>,
    val activeProjectId: String?,
) {
    val activeProject: ProjectConfig?
        get() = projects.firstOrNull { it.id == activeProjectId } ?: projects.firstOrNull()
}

class ProjectConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pocketdev-projects", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("pocketdev-project", Context.MODE_PRIVATE)

    fun load(): ProjectCollection {
        migrateLegacyIfNeeded()
        val projects = ProjectConfigCodec.decode(prefs.getString(KEY_PROJECTS, "").orEmpty())
        val requestedActiveId = prefs.getString(KEY_ACTIVE_PROJECT_ID, null)
        val activeId = requestedActiveId
            ?.takeIf { id -> projects.any { it.id == id } }
            ?: projects.firstOrNull()?.id
        if (activeId != requestedActiveId) {
            prefs.edit().putString(KEY_ACTIVE_PROJECT_ID, activeId).apply()
        }
        return ProjectCollection(projects = projects, activeProjectId = activeId)
    }

    fun upsert(project: ProjectConfig, makeActive: Boolean = true): ProjectCollection {
        val current = load()
        val storedProject = project.copy(id = project.id.ifBlank { UUID.randomUUID().toString() })
        val projects = current.projects.toMutableList()
        val index = projects.indexOfFirst { it.id == storedProject.id }
        if (index >= 0) projects[index] = storedProject else projects += storedProject
        val activeId = if (makeActive) storedProject.id else current.activeProjectId
        saveCollection(projects, activeId)
        return ProjectCollection(projects, activeId)
    }

    fun setActive(projectId: String): ProjectCollection {
        val current = load()
        require(current.projects.any { it.id == projectId }) { "Unknown project." }
        prefs.edit().putString(KEY_ACTIVE_PROJECT_ID, projectId).apply()
        return current.copy(activeProjectId = projectId)
    }

    fun delete(projectId: String): ProjectCollection {
        val current = load()
        val projects = current.projects.filterNot { it.id == projectId }
        val activeId = when {
            current.activeProjectId != projectId && projects.any { it.id == current.activeProjectId } -> current.activeProjectId
            else -> projects.firstOrNull()?.id
        }
        saveCollection(projects, activeId)
        return ProjectCollection(projects, activeId)
    }

    private fun saveCollection(projects: List<ProjectConfig>, activeId: String?) {
        prefs.edit()
            .putString(KEY_PROJECTS, ProjectConfigCodec.encode(projects))
            .putString(KEY_ACTIVE_PROJECT_ID, activeId)
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val name = legacyPrefs.getString("name", null)
        val remotePath = legacyPrefs.getString("remotePath", null)
        val legacy = if (name != null && remotePath != null) {
            ProjectConfig(
                name = name,
                remotePath = remotePath,
                testCommand = legacyPrefs.getString("testCommand", "").orEmpty(),
                buildCommand = legacyPrefs.getString("buildCommand", "").orEmpty(),
                githubRepository = legacyPrefs.getString("githubRepository", "").orEmpty(),
                id = UUID.randomUUID().toString(),
            )
        } else {
            null
        }
        saveCollection(legacy?.let(::listOf).orEmpty(), legacy?.id)
    }

    private companion object {
        const val KEY_PROJECTS = "projects.v1"
        const val KEY_ACTIVE_PROJECT_ID = "activeProjectId"
        const val KEY_MIGRATED = "legacyMigrated"
    }
}

internal object ProjectConfigCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(projects: List<ProjectConfig>): String = projects.joinToString("\n") { project ->
        listOf(
            project.id,
            project.name,
            project.remotePath,
            project.testCommand,
            project.buildCommand,
            project.githubRepository,
        ).joinToString("|", transform = ::encodeField)
    }

    fun decode(value: String): List<ProjectConfig> = value
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeProject)
        .toList()

    private fun decodeProject(row: String): ProjectConfig? = runCatching {
        val fields = row.split('|').map(::decodeField)
        if (fields.size != 6 || fields[0].isBlank()) return@runCatching null
        ProjectConfig(
            id = fields[0],
            name = fields[1],
            remotePath = fields[2],
            testCommand = fields[3],
            buildCommand = fields[4],
            githubRepository = fields[5],
        )
    }.getOrNull()

    private fun encodeField(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)
}
