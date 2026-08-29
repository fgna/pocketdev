package de.fgna.pocketdev.transfer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TransferActivityRegistry {
    private val activeTransfers = ConcurrentHashMap.newKeySet<String>()
    private val listeners = ConcurrentHashMap.newKeySet<(Int) -> Unit>()

    fun begin(): String {
        val id = UUID.randomUUID().toString()
        activeTransfers += id
        notifyListeners()
        return id
    }

    fun end(id: String) {
        activeTransfers -= id
        notifyListeners()
    }

    fun activeCount(): Int = activeTransfers.size

    fun addListener(listener: (Int) -> Unit): () -> Unit {
        listeners += listener
        listener(activeCount())
        return {
            listeners -= listener
        }
    }

    private fun notifyListeners() {
        val count = activeCount()
        listeners.forEach { listener -> runCatching { listener(count) } }
    }
}
