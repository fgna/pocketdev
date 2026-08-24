package de.fgna.pocketdev.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitSshAgentTest {
    @Test
    fun wrappedCommandsExportPersistentAgentSocket() {
        val command = GitSshAgent.wrap("git status")

        assertTrue(command.contains(".cache/pocketdev/ssh-agent.sock"))
        assertTrue(command.contains("export SSH_AUTH_SOCK"))
        assertTrue(command.endsWith("git status"))
    }

    @Test
    fun unlockReadsPassphraseFromStdinInsteadOfEmbeddingItInCommand() {
        val command = GitSshAgent.unlockCommand()

        assertTrue(command.contains("IFS= read -r POCKETDEV_KEY_PASSPHRASE"))
        assertTrue(command.contains("SSH_ASKPASS_REQUIRE=force"))
        assertFalse(command.contains("supersecret"))
    }
}
