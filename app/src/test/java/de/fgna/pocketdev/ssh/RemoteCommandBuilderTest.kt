package de.fgna.pocketdev.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandBuilderTest {
    private val baseProfile = SshProfile(
        host = "example.test",
        username = "developer",
        hostKeySha256 = "SHA256:test",
    )

    @Test
    fun `login shell wraps and safely quotes arbitrary command text`() {
        val command = "printf '%s\\n' \"\$HOME\"\nprintf \"%s\\n\" \"it's \$PATH\""

        assertEquals(
            "bash -lc 'printf '\"'\"'%s\\n'\"'\"' \"\$HOME\"\\nprintf \"%s\\n\" \"it'\"'\"'s \$PATH\"'",
            SshjCommandExecutor.remoteCommand(baseProfile, command, interactiveSudo = false),
        )
    }

    @Test
    fun `disabled login shell sends command unchanged`() {
        val command = "echo \$PATH\nprintf done"
        val profile = baseProfile.copy(loginShell = false)

        assertEquals(command, SshjCommandExecutor.remoteCommand(profile, command, interactiveSudo = false))
    }

    @Test
    fun `interactive sudo prelude is inside login shell wrapper`() {
        val wrapped = SshjCommandExecutor.remoteCommand(baseProfile, "sudo id", interactiveSudo = true)

        assertTrue(wrapped.startsWith("bash -lc '"))
        assertTrue(wrapped.contains("exec 3<&0"))
        assertTrue(wrapped.contains("command sudo -S"))
        assertTrue(wrapped.endsWith("sudo id'"))
    }

    @Test
    fun `exit 127 returns environment hint only`() {
        assertTrue(SshjCommandExecutor.environmentHint(127)!!.contains("Use login shell"))
        assertNull(SshjCommandExecutor.environmentHint(126))
        assertNull(SshjCommandExecutor.environmentHint(130))
    }
}
