# PocketDev

## Purpose

PocketDev is a mobile-first Android app for controlling a remote development environment from a phone.

The core idea is to make common vibe-coding and debugging workflows practical on a small touchscreen without turning the phone into a full IDE.

The app connects to an existing development server over SSH, runs project commands there, presents their output in a mobile-friendly way, retrieves build artifacts such as APK files, and helps turn failures into useful GitHub issues or diagnostic bundles.

## Product goal

A user should be able to pick up their phone and complete this loop with minimal typing:

1. connect to a configured development server via SSH;
2. select a project directory;
3. inspect Git state and pull current changes;
4. run predefined tests or build commands;
5. see readable command output and failures;
6. retrieve a generated APK or other build artifact to the phone;
7. package a failure with useful context and create a GitHub issue or copy/export the diagnostic information.

PocketDev should feel like a mobile control panel for an existing dev machine, not like a miniature desktop terminal.

## MVP

The first useful end-to-end version should support:

- Android app with a touch-friendly UI;
- SSH connection profiles;
- SSH key authentication where practical;
- configurable project entries pointing to directories on the server;
- project-level predefined commands;
- one-tap actions for common commands such as:
  - `git status`
  - `git pull`
  - tests
  - build
- an interactive terminal for commands that are not predefined;
- easy copy/paste of commands and output;
- streaming command output;
- clear success/failure state including exit code;
- readable extraction or highlighting of relevant error output;
- discovery of configured build artifacts, initially APK files;
- download of an APK to the Android device;
- preparation of a diagnostic package containing at least:
  - failed command;
  - exit code;
  - relevant logs;
  - current branch;
  - current commit;
  - `git status`;
- GitHub integration that can create an issue from that diagnostic package, or at minimum produce text that can be pasted into an issue.

## Suggested first platform

Use native Android with Kotlin and Jetpack Compose unless implementation evidence strongly suggests another choice.

For the MVP, prefer direct SSH execution from the Android app. Do not require a custom server daemon merely to get started.

The intended initial architecture is:

```text
Android / PocketDev
        |
       SSH
        |
Existing development server
        |
Git + Gradle/npm/etc. + project files
```

A small optional server-side agent may be introduced later if direct SSH becomes too limiting for reliable job management, artifact discovery, structured test results, reconnectable long-running builds, or richer live status.

## Mobile UX principles

The terminal is a fallback and power-user surface, not the primary interface.

Prefer large touch targets and task-oriented actions such as:

```text
my-taskOS
main · clean

[ Pull ]
[ Test ]
[ Build APK ]
[ Terminal ]

Last build
✓ assembleDebug
app-debug.apk     [ Download ]

Recent failures
✗ testDebugUnitTest
MainActivityTest.kt:83
[ Copy ] [ Create Issue ]
```

Important UX properties:

- minimize keyboard use;
- make frequent commands one tap;
- make command/output copy and paste easy;
- keep logs readable on a narrow display;
- allow expanding full raw output when needed;
- make failures more prominent than normal command noise;
- make project switching fast;
- preserve enough command history to recover context after interruptions.

## Project configuration

Prefer project-specific configuration rather than hard-coding Gradle or Android assumptions into the core domain.

A future configuration could express concepts like:

```yaml
name: my-taskOS
path: ~/Projects/my-taskOS
commands:
  status: git status --short --branch
  pull: git pull --ff-only
  test: ./gradlew testDebugUnitTest
  build: ./gradlew assembleDebug
artifacts:
  - android/app/build/outputs/apk/debug/*.apk
```

The exact format is not yet fixed. Keep the command and artifact model general enough to support non-Android projects later.

## GitHub integration

The useful unit is not merely "open GitHub". PocketDev should help create a reproducible issue from the current failure.

A generated issue/diagnostic bundle should make it easy to include:

- project name;
- branch and commit SHA;
- command that failed;
- exit code;
- concise relevant output;
- optionally full log attachment or expandable section;
- `git status`;
- optionally `git diff` when the user explicitly chooses to include it;
- build artifact or device context when relevant.

Do not silently upload source code, diffs, secrets, environment variables, SSH credentials, or arbitrary logs. The user must be able to review what is shared.

## Security constraints

Treat SSH credentials and GitHub tokens as sensitive secrets.

- use Android secure credential storage where appropriate;
- never persist private keys or tokens in plaintext application files or logs;
- never include credentials in diagnostic bundles;
- do not execute arbitrary server-provided commands without a clear user action;
- show the project and command being executed;
- make destructive commands distinguishable from normal actions if such commands are supported later.

## Non-goals for the first version

Do not expand the MVP into any of the following unless required to complete the core loop:

- full source-code editor;
- desktop IDE replacement;
- embedded AI coding agent;
- custom remote build farm;
- general CI/CD platform;
- Git GUI covering every Git operation;
- mandatory PocketDev server daemon;
- multi-user/team administration;
- complex deployment orchestration.

These may be explored later, but they must not block the first useful mobile workflow.

## First milestone

Prove one complete real workflow against one existing development server and one Android project:

1. save/connect to the SSH server;
2. configure one project path;
3. run `git status`;
4. run a real test command;
5. run a real APK build;
6. stream and display output correctly;
7. detect success/failure and exit code;
8. locate the resulting APK;
9. download it to the phone;
10. for a deliberately failing command, generate a clean diagnostic summary suitable for a GitHub issue.

### Milestone completion criterion

The milestone is complete when the entire workflow can be performed from the phone without opening a separate SSH client, manually browsing the server filesystem for the APK, or manually assembling the failure context.

## Implementation guidance for agents

When starting implementation:

1. keep the architecture small and testable;
2. establish the SSH execution abstraction first;
3. keep project/command configuration separate from UI code;
4. model command execution as structured state rather than raw terminal text only;
5. preserve stdout, stderr, exit code, start/end state, and the exact executed command;
6. build a narrow vertical slice before adding many screens;
7. use a real server/project for validation as early as practical;
8. avoid speculative abstractions for a future server daemon until direct SSH limitations are observed;
9. add tests around command state, error extraction, project configuration, and diagnostic bundle generation;
10. optimize for the phone workflow, not for feature parity with desktop developer tools.

If no implementation exists yet, the recommended first task is to create the smallest Android/Compose shell that can store one SSH profile and execute one harmless remote command while streaming its output into the UI.
