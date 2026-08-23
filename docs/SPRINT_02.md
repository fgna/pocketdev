# Sprint 2 — one-tap project actions

## Goal

Turn the validated Sprint 1 SSH command loop into the first project-aware mobile development workflow:

> configure one remote project -> tap Git status / Test / Build -> stream exact output -> inspect exit result.

## Included

- one persisted project with display name and absolute remote path
- configurable test and build commands
- one-tap project-scoped Git status, Test and Build actions
- safe shell quoting of the project path
- exact generated command remains visible in the generic runner
- existing streaming output, copy actions, host-key verification and authentication path are reused
- unit coverage for command generation
- physical-device validation against one real repository

## Excluded

- multi-project catalog
- automatic project discovery
- git pull/push workflows
- APK/artifact retrieval
- GitHub issue creation
- interactive terminal
- persistent server daemon

## Definition of done

Sprint 2 is complete when a physical Android device can configure one real remote repository and successfully use Git status, Test and Build as one-tap actions, with exact commands visible and correct success/non-zero results.

## Issues

- #9 persisted project configuration
- #10 project-scoped actions
- #11 real-repository validation
- #13 command-generation unit tests
- #12 Sprint 2 umbrella
