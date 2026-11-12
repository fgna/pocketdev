package de.fgna.pocketdev.ssh

import com.google.gson.JsonParser

/** Incremental JSONL adapter. Raw bytes continue to be shown and stored independently. */
object CodexProgress {
    data class Update(val action: String, val needsInput: Boolean = false)

    fun parse(line: String): Update? {
        val root = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: return null
        val type = root.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val item = root.get("item")?.takeIf { it.isJsonObject }?.asJsonObject
        val itemType = item?.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return when (type) {
            "thread.started" -> Update("Session started")
            "turn.started" -> Update("Working")
            "turn.completed" -> Update("Turn completed")
            "turn.failed", "error" -> Update("Codex reported an error")
            "item.started", "item.updated", "item.completed" -> when (itemType) {
                "command_execution" -> Update(if (type == "item.completed") "Command finished" else "Executing command")
                "file_change" -> Update(if (type == "item.completed") "Files updated" else "Editing files")
                "web_search" -> Update("Searching")
                "agent_message" -> Update(if (type == "item.completed") "Response ready" else "Writing response")
                "reasoning" -> Update("Reasoning")
                else -> null
            }
            "approval.requested", "request_user_input" -> Update("Input or approval required", true)
            else -> null
        }
    }

    fun enabled(command: String): Boolean =
        Regex("""\bcodex\s+exec\b""").containsMatchIn(command) &&
            Regex("""(?:^|\s)--json(?:\s|$)""").containsMatchIn(command)
}
