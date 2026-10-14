# Project action profiles

Open **Projects → Edit** or create a new project. Enter an absolute remote project
directory and optionally a GitHub `owner/name`. A GitHub repository is not required
for an existing directory.

Choose Android / Gradle, Docker Compose, General project, or one of your saved
profiles. Selecting a different profile replaces the action list in the editor;
save the project to apply it.

For each action, use **Edit** to change its button label, multiline shell command,
and confirmation setting. Use the checkbox to hide/show the button and **Up/Down**
to reorder it. Additional actions can be removed. Inherited actions can be disabled.
New actions request confirmation by default. Built-in Docker Stop also requests
confirmation; cancelling that dialog executes nothing.

**Save project** stores project-specific edits. **Save as new profile** creates a
reusable copy of the current action list, including order, disabled actions, and
confirmation flags. Select that profile in another project to reuse it. **Update
shared profile** updates an existing custom profile after confirmation. Projects
using it receive those changes, while their explicit local action overrides remain
in place. Saving a shared profile is immediate, even if the project editor is later
cancelled. Built-in templates cannot be overwritten.

Action buttons start in the project's configured remote directory, including when
an earlier manual command changed the remembered cwd. Arbitrary commands in the
Command field continue to use their remembered directory. Output, exit status,
stdin Input, sudo, Stop/Ctrl-C and background protection use the existing SSH flow.

Docker Compose provides `ps`, `up -d`, `stop`, `build`, and bounded `logs --tail=100`.
It requires Docker Compose on the SSH server. APK controls appear for Android
profiles and their copies. Existing Android projects retain their stored test and
build commands. Older six-field project records and the earlier profile format
migrate when next saved; no manual conversion is needed.

## Device acceptance for #93 / PR #94

Build `./gradlew testDebugUnitTest assembleDebug`, retrieve the debug APK through
PocketDev, verify/install it with the usual installer workflow, then:

1. Upgrade over an existing configuration. Confirm project IDs, names, directories,
   repository references and customized Android test/build commands remain intact.
   Run Test/Build and verify APK retrieval, digest checking and installation.
2. Configure a disposable existing Compose project with Docker Compose selected.
   Save, reopen the editor, and restart the app. Confirm the selected profile and
   its buttons survive. Run Status, Start, Build and Logs; verify output and exit
   status. Test Stop's confirmation first with Cancel, then with Run.
3. Edit Logs, disable Build, reorder actions and add a harmless multiline command
   such as `printf 'first\n'` followed by `pwd`. Save, restart and verify the settings
   and execution. Removing and adding custom actions must not create duplicate IDs.
4. Save a custom profile and assign it to a second project. Customize one action
   only in that project. Update the shared profile, and verify both projects get
   shared changes while the explicit local edit stays in place.
5. Run `cd /tmp` manually, then a project action that prints `pwd`; it must execute
   in the configured project root. Switch tabs between projects and verify actions,
   output and cwd belong to the selected project. Include a path containing spaces.
6. Confirm Docker/General profiles have no APK controls, while copied Android
   profiles retain them. Check blank labels/commands, relative paths, root `/`, and
   invalid repository references cannot be saved.
7. Run a waiting read/sudo command, send Input, switch apps and return, then use
   Stop/Ctrl-C. Confirm editing/deleting a busy project cannot disrupt its run.

Use disposable services for commands that start, stop or rebuild containers.
