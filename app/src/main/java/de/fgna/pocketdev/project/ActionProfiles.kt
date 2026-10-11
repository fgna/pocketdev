package de.fgna.pocketdev.project

/**
 * A reusable set of remote commands. SSH connection credentials are managed separately.
 * Commands are executed in the selected project's remote working directory.
 */
data class ActionDefinition(
    val id: String,
    val label: String,
    val command: String,
    val requiresConfirmation: Boolean = false,
)

data class ActionProfile(
    val id: String,
    val name: String,
    val actions: List<ActionDefinition>,
)

object ActionProfileTemplates {
    val android = ActionProfile(
        id = "android-gradle",
        name = "Android / Gradle",
        actions = listOf(
            ActionDefinition("git-status", "Git status", "git status --short --branch"),
            ActionDefinition("test", "Test", "./gradlew test"),
            ActionDefinition("build", "Build", "./gradlew assembleDebug"),
        ),
    )

    val dockerCompose = ActionProfile(
        id = "docker-compose",
        name = "Docker Compose",
        actions = listOf(
            ActionDefinition("status", "Status", "docker compose ps"),
            ActionDefinition("start", "Start", "docker compose up -d"),
            ActionDefinition("stop", "Stop", "docker compose stop", requiresConfirmation = true),
            ActionDefinition("build", "Build", "docker compose build"),
            ActionDefinition("logs", "Logs", "docker compose logs --tail=100"),
        ),
    )

    val generic = ActionProfile(
        id = "generic",
        name = "General project",
        actions = listOf(ActionDefinition("git-status", "Git status", "git status --short --branch")),
    )

    val all: List<ActionProfile> = listOf(android, dockerCompose, generic)

    fun find(id: String): ActionProfile? = all.firstOrNull { it.id == id }
}

/** Resolve a template and project-specific additions without modifying shared templates. */
object ActionProfileResolver {
    fun resolve(profile: ActionProfile, overrides: List<ActionDefinition>): List<ActionDefinition> {
        require(overrides.map { it.id }.distinct().size == overrides.size) {
            "Duplicate action IDs"
        }
        overrides.forEach(::validate)
        val replacements = overrides.associateBy { it.id }
        return profile.actions.map { replacements[it.id] ?: it } +
            overrides.filter { candidate -> profile.actions.none { it.id == candidate.id } }
    }

    fun validate(action: ActionDefinition) {
        require(action.id.matches(Regex("[a-zA-Z0-9._-]+"))) { "Invalid action ID" }
        require(action.label.isNotBlank()) { "Action label is required" }
        require(action.command.isNotBlank()) { "Action command is required" }
    }
}
