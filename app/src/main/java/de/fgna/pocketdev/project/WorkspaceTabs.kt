package de.fgna.pocketdev.project

/**
 * A workspace tab is a distinct view of a saved project. Multiple tabs may reference the
 * same project, but each tab has its own stable identity for command and artifact state.
 */
data class WorkspaceTab(
    val id: String,
    val projectId: String,
    val customTitle: String? = null,
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
/**
 * Immutable navigation state. Tab IDs stay stable across selections, while a tab may target
 * a different saved project. Closing the active tab selects a surviving neighbour.
 */
data class WorkspaceTabSelection(
    val tabs: List<WorkspaceTab>,
    val activeTabId: String?,
) {
    init {
        require(tabs.map { it.id }.distinct().size == tabs.size) { "Duplicate tab IDs" }
        require(activeTabId == null || tabs.any { it.id == activeTabId }) { "Active tab must exist" }
    }

    val activeTab: WorkspaceTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    fun select(tabId: String): WorkspaceTabSelection {
        require(tabs.any { it.id == tabId }) { "Unknown tab" }
        return copy(activeTabId = tabId)
    }

    fun add(projectId: String, newTabId: String): WorkspaceTabSelection =
        WorkspaceTabSelection(WorkspaceTabs.open(tabs, projectId, newTabId), newTabId)

    fun rename(tabId: String, title: String): WorkspaceTabSelection =
        copy(tabs = WorkspaceTabs.rename(tabs, tabId, title))

    fun moveActiveTo(projectId: String): WorkspaceTabSelection {
        val active = activeTab ?: return this
        return copy(tabs = WorkspaceTabs.reassign(tabs, active.id, projectId))
    }

    fun close(tabId: String): WorkspaceTabSelection {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return this
        val remaining = WorkspaceTabs.close(tabs, tabId)
        val nextActive = if (activeTabId == tabId) {
            remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id
        } else {
            activeTabId
        }
        return WorkspaceTabSelection(remaining, nextActive)
    }

    fun retainProjects(validProjectIds: Set<String>): WorkspaceTabSelection {
        val remaining = tabs.filter { it.projectId in validProjectIds }
        val selected = activeTabId?.takeIf { id -> remaining.any { it.id == id } }
            ?: remaining.firstOrNull()?.id
        return WorkspaceTabSelection(remaining, selected)
    }

    companion object {
        /** Initial migration: retain the previous one-tab-per-project layout. */
        fun fromProjects(projects: List<ProjectConfig>, activeProjectId: String?): WorkspaceTabSelection {
            val tabs = projects.map { WorkspaceTab(it.id, it.id) }
            val active = activeProjectId?.takeIf { id -> tabs.any { it.id == id } }
                ?: tabs.firstOrNull()?.id
            return WorkspaceTabSelection(tabs, active)
        }
    }
}

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

    fun rename(tabs: List<WorkspaceTab>, tabId: String, title: String): List<WorkspaceTab> {
        require(tabs.any { it.id == tabId }) { "Unknown tab" }
        val normalized = title.trim().take(60).ifBlank { null }
        return tabs.map { if (it.id == tabId) it.copy(customTitle = normalized) else it }
    }

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
                title = tab.customTitle ?: if (counts.getValue(tab.projectId) > 1) "$name $ordinal" else name,
            )
        }
    }
}
