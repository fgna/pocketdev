package de.fgna.pocketdev.ssh

import java.util.concurrent.ConcurrentHashMap

/** Tracks live SSH PTYs independently of ordinary commands and file transfers. */
object PtyActivityRegistry {
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val listeners = ConcurrentHashMap.newKeySet<(Int) -> Unit>()

    fun begin(id: String) {
        active += id
        notifyListeners()
    }

    fun end(id: String) {
        active -= id
        notifyListeners()
    }

    fun activeCount(): Int = active.size

    fun addListener(listener: (Int) -> Unit): () -> Unit {
        listeners += listener
        listener(activeCount())
        return { listeners -= listener }
    }

    private fun notifyListeners() {
        val count = activeCount()
        listeners.forEach { listener -> runCatching { listener(count) } }
    }
}
