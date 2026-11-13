# Command and terminal environments

PocketDev has two SSH execution modes:

- **Command mode** runs the command field, favorites, and project actions in a fresh SSH exec session. By default, PocketDev wraps each command with `bash -lc`, so Bash reads the server's login environment (for example `~/.profile`). On Debian and Ubuntu, that commonly adds `~/.local/bin` to `PATH`.
- **PTY terminal mode** starts an interactive shell. The shell can read different startup files, such as `~/.bashrc`, so its environment may differ from command mode.

Configure command mode under **Server → Use login shell**. The setting is enabled by default. Disable it only when the remote command must be sent exactly as entered; in that mode every SSH exec starts with the SSH server's non-interactive environment.

## Setting PATH

Prefer persistent login-environment configuration:

```bash
mkdir -p "$HOME/.local/bin"
printf '%s\n' 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.profile"
```

A change made with `source ~/.bashrc` affects only that one process. It cannot persist into PocketDev's next fresh SSH command session.

If a command exits with status 127, PocketDev adds a hint pointing to the login-shell setting. Absolute paths remain a reliable fallback when a host intentionally uses different command and interactive environments.
