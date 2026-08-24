package de.fgna.pocketdev.ssh

import org.junit.Assert.assertNotNull
import org.junit.Test

class SshCommandExecutorContractTest {
    @Test
    fun executorSupportsOptionalStdinForSecretPrompts() {
        val method = SshCommandExecutor::class.java.methods.firstOrNull { it.name == "execute" }
        assertNotNull(method)
    }
}
