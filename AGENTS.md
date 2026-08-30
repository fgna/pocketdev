# PocketDev repository guidance

## Product purpose

PocketDev is a mobile-first Android control surface for an existing development server. It should make common remote development and debugging workflows practical from a phone without becoming a miniature desktop IDE.

The app connects directly over SSH, manages saved remote projects, runs commands with streaming output, retrieves and installs APKs, transfers files, and prepares reviewable GitHub issue diagnostics after failures.

## Current product state

PocketDev 1.x has completed its original end-to-end milestone. Development is now maintenance- and friction-driven, with concrete work tracked through GitHub issues.

Current accepted capabilities include:

- pinned-host SSH profile and protected secret storage;
- multiple project configurations and project switching;
- remembered remote working directory and current Git branch display;
- predefined Pull / Test / Build actions and arbitrary multi-line commands;
- streaming stdout/stderr and exit status;
- Stop/Ctrl-C and interactive sudo support;
- persistent remote `ssh-agent` integration for GitHub SSH keys;
- missing-project bootstrap/clone flow;
- SHA-256-verified APK retrieval and PackageInstaller flow;
- foreground protection for long-running commands and transfers;
- APK retrieval retry after transient SSH/SFTP failures;
- project-independent two-way Files transfer page;
- reviewable `Git Issue` diagnostic flow for failed commands.

## Architecture

Keep the architecture direct and testable:

```text
Jetpack Compose UI
        |
ViewModel / project and execution state
        |
SSH, artifact and file-transfer abstractions
        |
SSHJ
        |
Existing development server
```

Do not introduce a required server daemon unless a concrete limitation of direct SSH justifies it.

UI code should not own SSH implementation details. Project configuration should remain separate from transport code so non-Android remote projects can still be controlled.

## UX principles

PocketDev is a task-oriented mobile control panel. Preserve these priorities:

- minimize keyboard use;
- keep common actions one tap away;
- make project identity, branch and working directory clear;
- keep command output readable on a narrow display;
- preserve command state across normal interruptions;
- make failure actions obvious without overwhelming successful runs;
- keep destructive actions explicit;
- use symmetric, predictable UI patterns where two concepts are equivalent (for example server/phone transfer directories).

The arbitrary command field is an important power-user surface, but the app should not drift into being a full terminal emulator or source editor.

## SSH and background execution

Security and execution reliability are non-negotiable:

- never disable host-key verification;
- use the persisted pinned host fingerprint;
- never log secrets or put passphrases into command lines;
- keep Git SSH passphrases ephemeral and send them only through command stdin;
- preserve exact command IDs for cancellation;
- foreground-service notifications must correspond to real active work;
- Stop actions must not claim to cancel work they do not own;
- transfers and long-running commands should survive ordinary app switching/screen-off when Android permits it.

Do not claim guarantees across force-stop, reboot or process death unless specifically implemented and tested.

## APK handling

APK retrieval must keep the integrity chain intact:

1. discover the remote APK;
2. calculate the remote SHA-256;
3. download to a fresh local file;
4. calculate and compare the local SHA-256;
5. only then hand the verified APK to Android's installer.

Transient SSH/SFTP failures may be retried with fresh connections, but retries must never bypass integrity verification.

## File transfer

The Files page is project-independent. It uses the same trusted SSH profile but its own transfer directories.

- server transfer path is configurable;
- Android folder access uses the Storage Access Framework and persisted URI permission;
- do not request broad storage permissions merely for convenience;
- transfers should expose progress and clear success/error state;
- filenames and remote paths must remain safely handled/quoted.

## GitHub diagnostics

`Git Issue` means preparing a reviewable issue flow, not silently publishing data. Diagnostic content must avoid credentials, private keys, environment secrets and arbitrary source/diff uploads.

The user should be able to see what is being shared before leaving PocketDev for GitHub.

## Repository workflow

Use GitHub issues for concrete backlog work. Keep focused branches and PRs; merge only after the relevant local/device gate is satisfied.

Before claiming a build succeeded, use an actual build result. Do not infer CI/build success from code inspection.

For Android validation, the standard local gate is:

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r --user 0 app/build/outputs/apk/debug/app-debug.apk
```

When repository changes are documentation-only, device validation is generally unnecessary, but syntax/build-affecting maintenance still needs an appropriate local check.

## Non-goals

Unless a new issue explicitly establishes the need, avoid expanding PocketDev into:

- a full source-code editor or desktop IDE replacement;
- an embedded AI coding agent;
- a general-purpose CI/CD platform;
- a full Git GUI;
- a mandatory custom remote daemon;
- multi-user/team administration;
- complex deployment orchestration.

Keep new work tied to demonstrated friction in the existing phone-to-development-server workflow.
