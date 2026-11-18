package de.fgna.pocketdev.data

import android.content.Context
import de.fgna.pocketdev.project.ActionProfile
import de.fgna.pocketdev.project.ActionProfileTemplates

class ActionProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pocketdev-action-profiles", Context.MODE_PRIVATE)

    fun load(): List<ActionProfile> = ActionProfileTemplates.all +
        ActionProfileCodec.decode(prefs.getString("profiles.v1", "").orEmpty())

    fun upsert(profile: ActionProfile): List<ActionProfile> {
        val custom = load().filter { ActionProfileTemplates.find(it.id) == null && it.id != profile.id } + profile
        val encoded = ActionProfileCodec.encode(custom)
        check(prefs.edit().putString("profiles.v1", encoded).commit()) { "Could not save action profile" }
        return ActionProfileTemplates.all + custom
    }
}
