package de.fgna.pocketdev.project

/**
 * A workspace tab is a distinct view of a saved project. Multiple tabs may reference the
 * same project, but each tab has its own stable identity for command and artifact state.
 */
data class WorkspaceTab(
    val id: String,
    val projectId: String,
)

data class WorkspaceTabLabel(
    val tabId: String,
    val projectId: String,
    val title: String,
)

/**
 * This model only manages tab identities and labels. The execution layer must key independent
 * commands, input handles, cancellation and artifact state by tab ID before duplicate tabs
 * are exposed in the UI.
 */
object WorkspaceTabs {
    fun open(
        tabs: List<WorkspaceTab>,
        projectId: String,
        tabId: String,
    ): List<WorkspaceTab> {
        require(projectId.isNotBlank()) { "Project ID is required" }
        require(tabId.isNotBlank() && tabs.none { it.id == tabId }) { "Tab ID must be unique" }
        return tabs + WorkspaceTab(id = tabId, projectId = projectId)
    }

    fun close(tabs: List<WorkspaceTab>, tabId: String): List<WorkspaceTab> =
        tabs.filterNot { it.id == tabId }

    fun reassign(tabs: List<WorkspaceTab>, tabId: String, projectId: String): List<WorkspaceTab> {
        require(projectId.isNotBlank()) { "Project ID is required" }
        require(tabs.any { it.id == tabId }) { "Unknown tab" }
        return tabs.map { if (it.id == tabId) it.copy(projectId = projectId) else it }
    }

    fun labels(
        tabs: List<WorkspaceTab>,
        projects: List<ProjectConfig>,
    ): List<WorkspaceTabLabel> {
        require(tabs.map { it.id }.distinct().size == tabs.size) { "Duplicate tab IDs" }
        val projectsById = projects.associateBy { it.id }
        val counts = tabs.groupingBy { it.projectId }.eachCount()
        val ordinals = mutableMapOf<String, Int>()
        return tabs.map { tab ->
            val project = projectsById[tab.projectId]
            val name = project?.name?.takeIf { it.isNotBlank() }
                ?: project?.githubRepository?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "Project"
            val ordinal = ordinals.getOrDefault(tab.projectId, 0) + 1
            ordinals[tab.projectId] = ordinal
            WorkspaceTabLabel(
                tabId = tab.id,
                projectId = tab.projectId,
                title = if (counts.getValue(tab.projectId) > 1) "$name $ordinal" else name,
            )
        }
    }
}
