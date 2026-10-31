package de.fgna.pocketdev.project

data class ProjectConfig(
    val name: String,
    val remotePath: String,
    val testCommand: String,
    val buildCommand: String,
    val githubRepository: String = "",
    val id: String = "",
    val actionProfileId: String = ActionProfileTemplates.android.id,
    val customActions: List<ActionDefinition> = emptyList(),
    val actionOrder: List<String> = emptyList(),
    val commandFavorites: List<String> = emptyList(),
) {
    fun withCommandFavorite(command: String, saved: Boolean): ProjectConfig {
        if (command.isBlank()) return this
        val remaining = commandFavorites.filterNot { it == command }
        return copy(commandFavorites = if (saved) (commandFavorites + command).distinct() else remaining)
    }
}

enum class ProjectAction {
    GIT_STATUS,
    TEST,
    BUILD,
}

object ProjectCommandBuilder {
    fun actionProfile(project: ProjectConfig, profiles: List<ActionProfile> = ActionProfileTemplates.all): ActionProfile =
        profiles.firstOrNull { it.id == project.actionProfileId }
            ?: throw IllegalArgumentException("Unknown action profile: ${project.actionProfileId}")

    fun configuredActions(project: ProjectConfig, profiles: List<ActionProfile> = ActionProfileTemplates.all): List<ActionDefinition> {
        val profile = actionProfile(project, profiles)
        val compatible = if (profile.id == ActionProfileTemplates.android.id) profile.copy(
            actions = profile.actions.map { action ->
                val legacy = when (action.id) {
                    "test" -> project.testCommand
                    "build" -> project.buildCommand
                    else -> action.command
                }
                action.copy(command = legacy.ifBlank { action.command }, enabled = action.enabled && legacy.isNotBlank())
            },
        ) else profile
        return ActionProfileResolver.resolve(compatible, project.customActions, project.actionOrder)
    }

    fun availableActions(project: ProjectConfig, profiles: List<ActionProfile> = ActionProfileTemplates.all): List<ActionDefinition> =
        configuredActions(project, profiles).filter { it.enabled }

    fun withEditedActions(project: ProjectConfig, actions: List<ActionDefinition>, profiles: List<ActionProfile> = ActionProfileTemplates.all): ProjectConfig {
        actions.forEach(ActionProfileResolver::validate)
        require(actions.map { it.id }.distinct().size == actions.size) { "Duplicate action IDs" }
        val inherited = configuredActions(project.copy(customActions = emptyList(), actionOrder = emptyList()), profiles)
        val byId = inherited.associateBy { it.id }
        val order = actions.map { it.id }
        return project.copy(
            customActions = actions.filter { it != byId[it.id] },
            actionOrder = if (order == inherited.map { it.id }) emptyList() else order,
        )
    }

    fun command(project: ProjectConfig, actionId: String, profiles: List<ActionProfile> = ActionProfileTemplates.all): String {
        val action = availableActions(project, profiles).firstOrNull { it.id == actionId }
            ?: throw IllegalArgumentException("Unknown project action: $actionId")
        ActionProfileResolver.validate(action)
        return action.command.trim()
    }

    fun validate(project: ProjectConfig, profiles: List<ActionProfile> = ActionProfileTemplates.all) {
        require(project.name.isNotBlank()) { "Project name is required" }
        require(project.remotePath.startsWith("/") && project.remotePath != "/" && '\u0000' !in project.remotePath) {
            "Use an absolute project directory other than /"
        }
        require(project.githubRepository.isBlank() || Regex("^[^/\\s]+/[^/\\s]+$").matches(project.githubRepository)) {
            "GitHub repository must use owner/name format"
        }
        configuredActions(project, profiles)
    }

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

