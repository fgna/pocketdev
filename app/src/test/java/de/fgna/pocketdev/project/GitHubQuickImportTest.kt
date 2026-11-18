package de.fgna.pocketdev.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubQuickImportTest {
    @Test fun parsesRepositoryAndUrl() {
        assertEquals("fgna/ae", GitHubQuickImport.parse("fgna/ae"))
        assertEquals("fgna/ae", GitHubQuickImport.parse(" https://github.com/fgna/ae.git "))
        assertEquals("fgna/ae", GitHubQuickImport.parse("https://github.com/fgna/ae/"))
    }

    @Test fun rejectsDangerousOrMalformedRepositoryNames() {
        listOf("../ae", "fgna/../ae", "fgna/ae;rm -rf /", "github.com/fgna/ae",
            "https://evil.example/fgna/ae", "fgna/ae/extra", "fgna/..", "fgna/.").forEach {
            assertThrows(IllegalArgumentException::class.java) { GitHubQuickImport.parse(it) }
        }
    }

    @Test fun commandVerifiesExistingCheckoutAndNeverOverwrites() {
        val command = GitHubQuickImport.command("fgna/ae")
        assertTrue(command.contains("target=\"\$HOME/Projects/ae\""))
        assertTrue(command.contains("git -C \"\$target\" remote get-url origin"))
        assertTrue(command.contains("POCKETDEV_IMPORT_CONFLICT"))
        assertTrue(command.contains("git clone 'git@github.com:fgna/ae.git' \"\$target\""))
        assertTrue(!command.contains("git reset") && !command.contains("rm -rf"))
    }
}
