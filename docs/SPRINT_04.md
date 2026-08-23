# Sprint 4 — Failure diagnostics and GitHub issue draft

## Goal

Turn a failed remote project action into a compact, reviewable diagnostic draft without automatically publishing anything.

Core loop:

> failed Test/Build -> review command + exit code + relevant output -> edit title/body -> open pre-filled GitHub issue form -> user decides whether to submit

## Scope

- build a deterministic diagnostic package from the current failed command state
- include project name, exact command, exit code and bounded output
- redact obvious credential-like values before pre-filling external content
- add an optional GitHub repository (`owner/name`) to project configuration
- expose a compact `Issue draft` action only for failed commands
- show editable title/body before leaving PocketDev
- open GitHub's new-issue page with title/body pre-filled
- never submit an issue automatically

## Non-goals

- storing GitHub tokens
- GitHub API issue creation
- automatic log upload
- environment dumps
- secret scanning beyond conservative obvious-pattern redaction
- AI-generated diagnosis

## Definition of done

On a physical Android device, an intentionally failed project command can be converted into an editable issue draft. Opening the draft launches the configured GitHub repository's new-issue UI with the edited title/body pre-filled, and no issue is created until the user explicitly submits it in GitHub.
