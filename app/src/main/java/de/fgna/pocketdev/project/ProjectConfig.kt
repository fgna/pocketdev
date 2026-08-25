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
    const val CWD_MARKER_PREFIX = "__POCKETDEV_CWD__"

    fun command(project: ProjectConfig, action: ProjectAction): String {
        val actionCommand = when (action) {
            ProjectAction.GIT_STATUS -> "git status --short --branch"
            ProjectAction.TEST -> project.testCommand.trim()
            ProjectAction.BUILD -> project.buildCommand.trim()
        }
        require(actionCommand.isNotBlank()) { "Project action command is blank." }
        return actionCommand
    }

    fun inProject(project: ProjectConfig, command: String): String =
        inDirectory(project.remotePath, command)

    fun inDirectory(directory: String, command: String): String {
        val path = directory.trim()
        require(path.startsWith("/")) { "Working directory must be absolute." }
        val trimmedCommand = command.trim()
        require(trimmedCommand.isNotBlank()) { "Project command is blank." }
        return "cd ${shellQuote(path)} && $trimmedCommand"
    }

    fun trackedInDirectory(directory: String, command: String, token: String): String {
        val path = directory.trim()
        require(path.startsWith("/")) { "Working directory must be absolute." }
        val trimmedCommand = command.trim()
        require(trimmedCommand.isNotBlank()) { "Project command is blank." }
        require(token.matches(Regex("^[A-Za-z0-9._-]+$"))) { "Working-directory token is invalid." }
        val marker = "$CWD_MARKER_PREFIX$token:"
        return """
            cd ${shellQuote(path)} && {
              trap 'rc=${'$'}?; printf "\\n$marker%s\\n" "${'$'}PWD"; exit "${'$'}rc"' EXIT
              $trimmedCommand
            }
        """.trimIndent()
    }

    fun extractWorkingDirectory(stdout: String, token: String): String? {
        val marker = "$CWD_MARKER_PREFIX$token:"
        return stdout.lineSequence()
            .lastOrNull { it.startsWith(marker) }
            ?.removePrefix(marker)
            ?.trim()
            ?.takeIf { it.startsWith("/") }
    }

    fun stripWorkingDirectoryMarker(stdout: String, token: String): String {
        val marker = Regex("(?m)^${Regex.escape("$CWD_MARKER_PREFIX$token:")}.*(?:\\r?\\n|$)")
        return stdout.replace(marker, "").trimEnd('\r', '\n')
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
