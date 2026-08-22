# PocketDev

PocketDev is a mobile-first Android control surface for a remote development server. See `AGENTS.md` for the product brief and `docs/SPRINT_01.md` for the current sprint.

## Current state

Sprint 1 bootstrap is in progress. The app currently provides the native Android/Jetpack Compose shell that later Sprint 1 issues will connect to the SSH execution layer.

## Requirements

- JDK 17
- Android SDK with API 37 available
- a Gradle version compatible with Android Gradle Plugin 9.3.0

The repository does not yet contain a checked-in Gradle wrapper. Until it is generated and committed, use a compatible local Gradle installation or open the project in a current Android Studio version and generate the wrapper.

## Build

```bash
gradle :app:assembleDebug
```

## Unit tests

```bash
gradle :app:testDebugUnitTest
```

## Sprint 1 architecture

Keep the first slice deliberately small:

```text
Compose UI
   |
command/connection state
   |
SSH execution abstraction
   |
SSHJ
   |
remote development server
```

The UI must not own SSH implementation details. Issue #2 introduces the execution abstraction, issue #3 adds secure profile persistence, and issue #4 wires both into the mobile command runner.

## Security baseline

- never commit credentials or private keys;
- never log secrets;
- show the exact host and command before execution;
- keep SSH and GitHub credentials out of ordinary plaintext preferences.
