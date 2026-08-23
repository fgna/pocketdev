# Sprint 5 — Multiple saved projects and fast switching

## Goal

Move PocketDev from one configured remote project to a small saved project collection without losing the existing installation state.

## Included

- stable ID per saved project
- automatic migration of the legacy single-project SharedPreferences record
- persisted project collection and active project
- create, edit and delete project configurations
- compact `Projects` switcher in the existing GoodVibes-inspired header/workspace
- all Status/Test/Build/APK/diagnostic actions use the active project
- project switches reset transient command result and artifact state to avoid cross-project attribution
- absolute remote project paths are enforced

## Excluded

- parallel project tabs or simultaneous project sessions
- per-project SSH profiles
- remote project discovery
- background execution across multiple projects

Parallel tabs stay tracked in #27 and are the intended follow-up after this storage/switching layer is validated.

## Validation

1. Upgrade an existing install and verify its current project survives migration.
2. Add a second real project.
3. Switch between both projects and run Git status on each.
4. Run Test or Build where configured and verify the selected remote path is used.
5. Restart PocketDev and verify both projects and the active selection persist.
6. Verify old output/APK state does not follow a project switch.
7. Edit and delete a project without affecting the remote repository.

Issues: #27, #28, #29, #30, #31.
