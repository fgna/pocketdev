package de.fgna.pocketdev.data

import de.fgna.pocketdev.project.ActionDefinition
import de.fgna.pocketdev.project.ActionProfile
import de.fgna.pocketdev.project.ActionProfileTemplates
import org.junit.Assert.*
import org.junit.Test

class ActionProfileCodecTest {
    @Test fun reusableProfilesRoundTripAllActionSettingsAndMultilineText() {
        val profiles = listOf(
            ActionProfile("custom-compose", "Team | Compose", listOf(ActionDefinition("deploy", "Deploy; now", "printf 'a|b;c,d'\necho Grüße", true, false))),
            ActionProfileTemplates.android.copy(id = "custom-android", name = "My Android"),
        )
        assertEquals(profiles, ActionProfileCodec.decode(ActionProfileCodec.encode(profiles)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun builtInProfilesCannotBeOverwritten() { ActionProfileCodec.encode(listOf(ActionProfileTemplates.dockerCompose)) }

    @Test fun corruptedProfileDoesNotHideOtherProfiles() {
        val profile = ActionProfile("custom", "Custom", emptyList())
        val encoded = ActionProfileCodec.encode(listOf(profile))
        assertEquals(listOf(profile), ActionProfileCodec.decode("bad row\n$encoded"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateActionIdsAreRejectedBeforeSaving() {
        ActionProfileCodec.encode(listOf(ActionProfile("custom", "Custom", List(2) { ActionDefinition("same", "Same", "pwd") })))
    }
}
