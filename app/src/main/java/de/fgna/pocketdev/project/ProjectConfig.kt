package de.fgna.pocketdev.project

data class ProjectConfig(
    val name: String,
    val remotePath: String,
    val testCommand: String,
    val buildCommand: String,
    val githubRepository: String = "",
    val id: String = "",
)

enum class ProjectAction {
    GIT_STATUS,
    TEST,
    BUILD,
}

object ProjectCommandBuilder {
    fun command(project: ProjectConfig, action: ProjectAction): String {
        val remotePath = project.remotePath.trim()
        require(remotePath.startsWith("/")) { "Project path must be absolute." }
        val actionCommand = when (action) {
            ProjectAction.GIT_STATUS -> "git status --short --branch"
            ProjectAction.TEST -> project.testCommand.trim()
            ProjectAction.BUILD -> project.buildCommand.trim()
        }
        require(actionCommand.isNotBlank()) { "Project action command is blank." }
        return "cd ${shellQuote(remotePath)} && $actionCommand"
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
