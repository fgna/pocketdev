package de.fgna.pocketdev.ssh

/** Build the remote command while preserving original text and sudo stdin handling. */
internal object RemoteShellCommand {
    fun build(command: String, loginShell: Boolean, interactiveSudo: Boolean): String {
        val script = if (interactiveSudo) SshjCommandExecutor.interactiveSudoPrelude() + "\n" + command else command
        if (!loginShell) return script
        // A single quote is escaped for the SSH server's shell before Bash parses the script.
        val quoted = "'" + script.replace("'", "'\\''") + "'"
        return "bash -lc " + quoted
    }
}
