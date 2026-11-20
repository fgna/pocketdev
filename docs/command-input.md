# Replies to running commands

While a command is running, tap **Input**, enter the reply, and tap **Send**.
PocketDev sends the text followed by a newline to that execution's stdin. An empty
reply sends Enter. Open Input again for additional replies. Cancel dismisses the
dialog without sending anything or stopping the command; Stop/Ctrl-C still stops
the command.

Use **Hide input** for a password or passphrase. Replies are ephemeral and are not
saved in PocketDev's command history, saved state, or diagnostics. The remote
program can still echo input into its output. The existing automatic sudo password
dialog remains available.

The dialog is bound to the execution that opened it. Switching projects dismisses
it, and an expired dialog cannot write to a subsequent command in the same project.
Input is available after the SSH command has started. This is line input over SSH,
not a terminal emulator: programs requiring a TTY, raw keys, or terminal menus may
need a different workflow. PocketDev detects a limited set of short, visible, unfinished prompts such as `Name: `, `Reply 2: `, `Enter value: ` and `Password: `, and automatically opens Input with keyboard focus. Password/passphrase prompts start masked. The manual Input button remains available for other prompts or quiet commands. An arbitrary silent stdin wait cannot be detected reliably.

## Device acceptance for #97

Build with `./gradlew testDebugUnitTest assembleDebug`, retrieve the debug APK
through PocketDev, and install it with the normal Android installer workflow.

1. Run `printf 'Name: ' >&2; IFS= read -r name; printf 'Hello %s\n' "$name"`.
   Confirm the short prompt appears immediately. Input → `Grüße` → Send must print
   `Hello Grüße` and complete successfully.
2. Run `for n in 1 2; do printf 'Reply %s: ' "$n" >&2; IFS= read -r reply; printf '<%s>\n' "$reply"; done`.
   Send a normal reply, then an empty reply. Verify two distinct replies and exit 0.
3. Run a read command, type a disposable value with Hide input selected, then Cancel.
   Confirm nothing is sent, the command stays running, and Input can be reopened.
4. Open Input during one project's read command, switch projects and verify the
   dialog closes. Run another read command in the second project and verify each
   reply reaches only its own project. Stop a waiting command and start a new one;
   an earlier input dialog must not send to the new command.
5. Repeat a reply after app switching/screen off. Verify Stop/Ctrl-C still terminates
   a waiting read command and another command can run immediately.
6. Run `sudo -k; sudo sh -c 'echo ok'`, answer its automatic password dialog, and
   confirm the password is absent from visible output. Cancel a second sudo prompt
   and verify cancellation still works.

Do not use real secrets for tests that intentionally echo their input.

## Device acceptance for #144

1. Run `printf 'Name: ' >&2; IFS= read -r name; printf 'Hello %s\n' "$name"`. Verify the Input dialog opens automatically, displays `Name:`, focuses the input field, and the reply completes the same command.
2. Run `for n in 1 2; do printf 'Reply %s: ' "$n" >&2; IFS= read -r reply; printf '<%s>\n' "$reply"; done`. Verify the second prompt opens again after the first reply, and normal output does not close the dialog while typing.
3. Run `printf 'Password: ' >&2; IFS= read -r secret; printf 'received\n'`. Verify hidden input is the default and no entered secret appears in diagnostics or history.
4. Verify cancellation sends nothing, manual Input still works for an unrecognized prompt, a stopped command cannot accept stale input, switching project dismisses the dialog, and sudo prompts continue to work.
