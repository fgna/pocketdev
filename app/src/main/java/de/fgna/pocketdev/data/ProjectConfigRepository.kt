package de.fgna.pocketdev.data

import android.content.Context
import de.fgna.pocketdev.project.ProjectConfig

class ProjectConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pocketdev-project", Context.MODE_PRIVATE)

    fun load(): ProjectConfig? {
        val name = prefs.getString("name", null) ?: return null
        val remotePath = prefs.getString("remotePath", null) ?: return null
        return ProjectConfig(
            name = name,
            remotePath = remotePath,
            testCommand = prefs.getString("testCommand", "").orEmpty(),
            buildCommand = prefs.getString("buildCommand", "").orEmpty(),
            githubRepository = prefs.getString("githubRepository", "").orEmpty(),
        )
    }

    fun save(project: ProjectConfig) {
        prefs.edit()
            .putString("name", project.name)
            .putString("remotePath", project.remotePath)
            .putString("testCommand", project.testCommand)
            .putString("buildCommand", project.buildCommand)
            .putString("githubRepository", project.githubRepository)
            .apply()
    }
}
