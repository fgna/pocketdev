package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PtyActivityRegistryTest {
    @Test fun tracksRunningTerminalIndependentlyAndNotifiesOnClose() {
        val before = PtyActivityRegistry.activeCount()
        val observed = mutableListOf<Int>()
        val remove = PtyActivityRegistry.addListener { observed += it }
        val id = UUID.randomUUID().toString()
        try {
            PtyActivityRegistry.begin(id)
            assertEquals(before + 1, PtyActivityRegistry.activeCount())
        } finally {
            PtyActivityRegistry.end(id)
            remove()
        }
        assertEquals(before, PtyActivityRegistry.activeCount())
        assertEquals(listOf(before, before + 1, before), observed)
    }
}
