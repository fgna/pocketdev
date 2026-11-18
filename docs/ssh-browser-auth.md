# SSH browser authentication tunnel

PocketDev can forward one explicit phone TCP port through SSH for remote CLI
browser authentication. Open the workspace navigation menu → **SSH tunnel**.
The default is phone `127.0.0.1:1455` → server `127.0.0.1:1455`.
You can change both ports and the destination host. Destination addresses are
resolved from the SSH server's network; the local address is always IPv4 loopback.
There is no wildcard/public bind or automatic port/address fallback.

The tunnel uses the saved Server profile, pinned host key and Android-protected
credentials. First connect and explicitly trust the host through the existing
Server flow. The tunnel cannot discover/trust a new host on its own. Commands
and tunnels share connection/authentication code. Each command already has its
own SSH connection; the tunnel owns another connection to the same profile so a
command completing or being stopped does not retire the tunnel. Private keys
are loaded directly into SSHJ memory, without a temporary/exported key file.

A dedicated foreground service starts from **Start tunnel** while PocketDev is
visible. Its notification shows Starting/Active and **Stop tunnel**; that action
only owns this tunnel. Closing the dialog, switching projects, rotating the phone
or foregrounding the browser keeps the tunnel running. **Stop tunnel**, changing
the saved SSH profile, removing the PocketDev task, SSH disconnection or service
shutdown releases the listener and SSH connection. A subsequent start uses fresh
resources. Failed/stopped status remains visible in the dialog and navigation
menu; normal service completion also leaves a stopped notification when Android
notification permission is granted.

The service uses Android's existing `dataSync` permission/type. Android 15+ limits
this type to six background hours per 24 hours, shared with other PocketDev data
sync services. A timeout closes the tunnel and reports that Android ended
background protection. No tunnel is automatically restored after process death,
force-stop or reboot. On reopening PocketDev it starts as **Stopped**. Android and
manufacturer background restrictions can still end work; inspect the state and
restart explicitly. A foreground service is not a permanent uptime guarantee.

The forwarding code handles opaque TCP bytes. It does not parse HTTP, collect
callback URLs/query strings, log tokens, capture payloads, or add them to GitHub
diagnostics. Transport failures use fixed messages rather than exception text.
Any app on the phone that can access loopback can reach the chosen local port:
start explicitly, use it for the intended login, and stop it when done.

## Codex login walkthrough (physical Android test)

1. Use the personal Android profile and an existing saved, host-key-pinned SSH
   Server profile. Confirm the remote Codex CLI is installed and available in the
   login shell. Record the exact tested commit/APK and Android version.
2. In the workspace command field, run `codex login` on the remote server. Leave
   it running; its browser callback listener is on the server's port 1455.
3. Open **SSH tunnel**, set local port `1455`, destination host `127.0.0.1`, and
   destination port `1455`. Tap **Start tunnel**, grant notification permission
   if requested, and wait for **Active**. Close the dialog with **Done**.
4. Select/copy the sign-in URL printed by Codex and open it in the Android browser
   in the same Android profile. Use the printed URL as-is; do not replace it with
   the callback URL or change OAuth parameters. Complete sign-in. The redirect to
   `http://127.0.0.1:1455/auth/callback` now travels through SSH to the remote CLI.
   Confirm the browser succeeds and the remote command reports login completion.
   Do not save screenshots/logs containing URLs, tokens or authorization codes.
5. Return to PocketDev and run `codex login status`; confirm the **remote** CLI
   is authenticated. Open **SSH tunnel** and tap **Stop tunnel**. Confirm
   **Tunnel stopped** and that the local port no longer accepts connections.

Keep local port 1455 for this flow because the remote CLI's redirect uses it.
Changing only PocketDev's local port does not change the OAuth redirect. If 1455
is occupied, stop its existing owner; a different port works only when the CLI
and authentication provider support that redirect too. Keep the remote listener
bound to the server's loopback; no public firewall opening is needed. The SSH
server must permit `direct-tcpip` local forwarding (`AllowTcpForwarding` and any
`PermitOpen` restrictions must allow the selected destination).

## Additional hardware acceptance

- Rotate the phone while the tunnel is active, dismiss/reopen the controls, switch
  projects, then open the browser and briefly turn the screen off. Confirm the
  tunnel and its foreground notification remain active and the callback works.
- Start another listener on phone port 1455. Confirm PocketDev reports local port
  unavailable, never changes the address/port, and succeeds after that listener
  is stopped. Check invalid ports and a URL entered as destination are rejected.
- With an idle active tunnel, disconnect the phone's network or terminate its SSH
  connection on the server. Wait for SSH keepalive detection. Confirm Failed /
  stopped, an actionable reconnect message and no remaining local listener.
- Test an incorrect pinned fingerprint and incorrect credentials. Neither may
  result in Active forwarding. Restore the trusted profile and retry explicitly.
- Stop from the notification with the browser open; confirm only the tunnel stops.
  Start/stop repeatedly on the same port. Removing PocketDev from Recents closes
  it; relaunch after process termination shows Stopped, without a phantom listener.
- On Android 15+, exercise the documented foreground-service timeout mechanism
  in a disposable test setup. Confirm an explicit background-stopped state and
  successful manual restart on returning to PocketDev. Restore device settings.
- Run an ordinary streaming command and a Files transfer with the tunnel active.
  Stopping the tunnel must preserve both; stopping commands must preserve the
  tunnel. Repeat APK retrieval and verify the existing SHA-256/install flow.

These checks require Android hardware and a real SSH host; JVM tests do not prove
browser/Android lifecycle acceptance. Carry pending physical checks and eventual
exact revision/PR/build evidence to the existing [acceptance register #141](https://github.com/fgna/pocketdev/issues/141)
after the coordinator's engineering gates and review pass.

## Automated verification and implementation references

`./gradlew test` includes `Issue89LocalPortForwardTest`: configuration validation,
real loopback binding/collision, byte handoff to the transport seam, connection and
listener failures, idle disconnect, stop during connect, idempotent cleanup,
background shutdown, repeated starts on the same port, pre-connect keepalive
configuration, and shared password/in-memory-key authentication with unpinned-host
rejection. Its issue-scoped Python
entry point is `python3 -m unittest discover -s tests -p 'test_issue_89_*.py'`.
Existing command and file-transfer regressions remain in the normal Gradle suite.
The tests use a fake SSH transport; real SSH forwarding and host-key mismatch
checks are included in hardware acceptance above.

- [SSHJ 0.40.0 LocalPortForwarder](https://github.com/hierynomus/sshj/blob/v0.40.0/src/main/java/net/schmizz/sshj/connection/channel/direct/LocalPortForwarder.java):
  blocking listener over `direct-tcpip`; socket close plus listener interruption,
  and closing the owned SSH connection retires forwarding channels.
- [SSHJ in-memory key loading](https://github.com/hierynomus/sshj/blob/v0.40.0/src/main/java/net/schmizz/sshj/SSHClient.java).
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
  and [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).
