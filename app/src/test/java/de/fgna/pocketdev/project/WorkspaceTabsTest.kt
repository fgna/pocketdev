package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkspaceTabsTest {
    private fun project(id: String, name: String, profile: String) = ProjectConfig(
        id = id,
        name = name,
        remotePath = "/srv/$id",
        testCommand = "./gradlew test",
        buildCommand = "./gradlew assembleDebug",
        actionProfileId = profile,
    )

    @Test fun duplicateProjectTabsHaveDistinctIdentitiesAndNumberedTitles() {
        val projects = listOf(project("android", "LightScenes", "android-gradle"))
        val first = WorkspaceTabs.open(emptyList(), "android", "tab-a")
        val both = WorkspaceTabs.open(first, "android", "tab-b")
        assertNotEquals(both[0].id, both[1].id)
        assertEquals(listOf("LightScenes 1", "LightScenes 2"), WorkspaceTabs.labels(both, projects).map { it.title })
        assertEquals(listOf("LightScenes"), WorkspaceTabs.labels(WorkspaceTabs.close(both, "tab-a"), projects).map { it.title })
    }

    @Test fun distinctProfilesCanCoexistAndReassignChangesOnlyOneTab() {
        val projects = listOf(
            project("android", "LightScenes", "android-gradle"),
            project("compose", "Community", "docker-compose"),
        )
        val first = WorkspaceTabs.open(emptyList(), "android", "tab-a")
        val both = WorkspaceTabs.open(first, "compose", "tab-b")
        assertEquals(listOf("LightScenes", "Community"), WorkspaceTabs.labels(both, projects).map { it.title })
        val reassigned = WorkspaceTabs.reassign(both, "tab-b", "android")
        assertEquals(listOf("LightScenes 1", "LightScenes 2"), WorkspaceTabs.labels(reassigned, projects).map { it.title })
        assertEquals("android", reassigned[0].projectId)
        assertEquals("android", reassigned[1].projectId)
        assertEquals("compose", both[1].projectId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTabIdentityIsRejected() {
        WorkspaceTabs.open(listOf(WorkspaceTab("tab-a", "android")), "android", "tab-a")
    }
}
