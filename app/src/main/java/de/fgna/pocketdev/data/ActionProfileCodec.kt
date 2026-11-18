package de.fgna.pocketdev.data

import de.fgna.pocketdev.project.ActionDefinition
import de.fgna.pocketdev.project.ActionProfile
import de.fgna.pocketdev.project.ActionProfileResolver
import de.fgna.pocketdev.project.ActionProfileTemplates
import java.nio.charset.StandardCharsets
import java.util.Base64

internal fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

internal fun decodeField(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

internal object ActionDefinitionCodec {
    fun encode(actions: List<ActionDefinition>): String {
        validate(actions)
        return actions.joinToString(";") { action ->
            listOf(action.id, action.label, action.command, action.requiresConfirmation.toString(), action.enabled.toString())
                .joinToString(",", transform = ::encodeField)
        }
    }

    fun decode(value: String): List<ActionDefinition> {
        if (value.isEmpty()) return emptyList()
        return value.split(';').map { row ->
            val fields = row.split(',').map(::decodeField)
            require(fields.size in 4..5) { "Invalid action entry" }
            ActionDefinition(
                fields[0], fields[1], fields[2], fields[3].toBooleanStrict(),
                if (fields.size == 5) fields[4].toBooleanStrict() else true,
            )
        }.also(::validate)
    }

    private fun validate(actions: List<ActionDefinition>) {
        require(actions.map { it.id }.distinct().size == actions.size) { "Duplicate action IDs" }
        actions.forEach(ActionProfileResolver::validate)
    }
}

internal object ActionProfileCodec {
    fun encode(profiles: List<ActionProfile>): String {
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate profile IDs" }
        return profiles.joinToString("\n") { profile ->
            validate(profile)
            listOf(profile.id, profile.name, profile.supportsApk.toString(), ActionDefinitionCodec.encode(profile.actions))
                .joinToString("|", transform = ::encodeField)
        }
    }

    fun decode(value: String): List<ActionProfile> = value.lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
        runCatching {
            val fields = row.split('|').map(::decodeField)
            require(fields.size == 4) { "Invalid profile entry" }
            ActionProfile(fields[0], fields[1], ActionDefinitionCodec.decode(fields[3]), fields[2].toBooleanStrict())
                .also(::validate)
        }.getOrNull()
    }.distinctBy { it.id }.toList()

    private fun validate(profile: ActionProfile) {
        require(profile.id.matches(Regex("[a-zA-Z0-9._-]+"))) { "Invalid profile ID" }
        require(ActionProfileTemplates.find(profile.id) == null) { "Built-in profiles cannot be replaced" }
        require(profile.name.isNotBlank()) { "Profile name is required" }
        ActionDefinitionCodec.encode(profile.actions)
    }
}
