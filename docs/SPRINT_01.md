# Sprint 1: Prove the mobile SSH command loop

## Sprint goal

Prove the smallest useful PocketDev workflow on a physical Android phone:

> Configure one SSH server -> connect -> run a harmless remote command -> stream readable output -> see the exit result -> copy useful output.

This sprint intentionally stops before project automation, builds, artifact download, GitHub issue creation, and full terminal emulation.

## Product question being validated

Can a native Android app using direct SSH provide a reliable, low-friction mobile control surface for an existing development server?

This is the core technical and UX assumption behind PocketDev. It should be proven before higher-level Git/build workflows are added.

## In scope

1. Bootstrap a native Android app using Kotlin and Jetpack Compose.
2. Define a small, testable SSH execution abstraction.
3. Model command execution as structured state with command, stdout, stderr, running/completed/failed state, and exit code.
4. Configure and persist one SSH connection profile.
5. Protect secret material appropriately on Android and keep it out of logs.
6. Connect and disconnect from one real development server.
7. Execute harmless remote commands such as `pwd` and `git status --short --branch`.
8. Stream output into a touch-friendly Compose UI while commands run.
9. Distinguish SSH/authentication failures from remote non-zero exit codes.
10. Copy the executed command and output.
11. Validate the complete flow on a physical Android device and record observed limitations.

## Out of scope

Do not add these in Sprint 1:

- project catalog or project configuration model;
- `git pull` workflow;
- dedicated Test or Build buttons;
- APK discovery or download;
- full interactive terminal emulation;
- diagnostic bundle generation;
- GitHub issue creation;
- a PocketDev server-side daemon;
- multiple SSH profile management;
- cloud sync.

## Architecture direction

Keep the vertical slice small:

```text
Compose UI
   |
ViewModel / application state
   |
SSH execution interface
   |
Android-compatible SSH implementation
   |
Existing development server
```

The SSH implementation must not be coupled to Compose. UI should consume structured command state rather than raw terminal text only.

Do not introduce speculative abstractions for future server agents or a broad plugin system in this sprint.

## Security baseline

- Never log passwords, tokens, or private-key contents.
- Do not store raw private keys in plaintext app files or normal preferences.
- Make the destination host and executed command visible to the user.
- Only run a command after an explicit user action.
- Keep Sprint 1 commands non-destructive.

## Issue sequence

### #1 S1-01 Bootstrap the Android/Compose application

Establish the Android project, Compose shell, dependencies, tests, and basic build documentation.

### #2 S1-02 Implement SSH connection and command execution abstraction

Build the non-UI SSH layer with streaming stdout/stderr, structured execution state, exit codes, and test seams.

### #3 S1-03 Add one secure SSH profile and connection flow

Allow one real server to be configured, persisted safely, connected, disconnected, and retried from the app.

### #4 S1-04 Build the mobile command runner and streaming output UI

Connect the SSH layer to Compose with explicit run actions, readable live output, result state, and copy support.

### #5 S1-05 Validate the SSH vertical slice on a real development server

Install on a physical Android phone and validate the end-to-end workflow against a real server and repository.

### #6 Sprint 1 tracking issue

Umbrella issue containing the sprint checklist, definition of done, non-goals, and expected Sprint 2 direction.

## Definition of done

Sprint 1 is complete when a debug APK on a physical Android phone can:

1. configure one real development server;
2. connect through PocketDev without opening a separate SSH client;
3. execute `pwd`;
4. execute a harmless Git command in a real repository;
5. display stdout/stderr incrementally while the command runs;
6. show successful completion and non-zero remote exit clearly;
7. distinguish connection/authentication failure from command failure;
8. copy the exact command and output;
9. avoid exposing credentials in UI output or logs.

The validation result and any limitations must be recorded so Sprint 2 is driven by observed behavior.

## Expected Sprint 2 direction

If the direct-SSH slice works reliably, Sprint 2 should introduce one configured project and one-tap project actions such as Git status, test, and build. Project commands should remain configuration-driven rather than hard-coded to Android or Gradle.

The exact Sprint 2 scope should be adjusted based on findings from issue #5.