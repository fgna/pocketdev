# Parallel project tabs — acceptance checklist

PocketDev keeps a shared SSH connection and distinct project configurations. Each saved project selects a reusable Android/Gradle, Docker Compose, General or custom action profile. Multiple tabs may refer to the same project, but each tab has a different session identity.

## Device acceptance for #118

Build `./gradlew testDebugUnitTest assembleDebug` on the remote development host, then retrieve and install the resulting APK with PocketDev's verified APK flow.

1. **First project / onboarding:** With no saved projects, configure SSH and use **Add first project**. Add a GitHub checkout or use **Advanced** to set an absolute directory. Run an action against a missing directory and confirm the existing **Create project on server?** dialog still offers clone or empty directory creation. Confirm it only reports success after the remote operation succeeds.
2. **Multiple tabs for one project:** Select an Android project and tap **+ Tab** twice. Tabs must be titled `Example 1`, `Example 2`, `Example 3` with distinct selected indicators. Run `sleep 20; echo first` in the first and `echo second` in the second. The first command continues; neither tab shows the other's output, command text or exit status.
3. **Input and Stop:** In one tab run `read answer; printf '%s\\n' "\\$answer"`. Use Input, send a value and confirm it appears only in that tab. Run an additional long command in another tab; Stop must cancel only the selected tab's command. Repeat with a sudo prompt if configured.
4. **Project context and profiles:** Open a Docker Compose project in another tab. The header, tab title, current directory, buttons and APK availability must switch to that project's saved profile. Android actions and Get APK must remain available only in Android-capable profiles. Confirm the Docker actions target its own project root.
5. **APK isolation:** Build two distinct Android apps in parallel tabs and retrieve each APK independently. Each downloaded file must be verified against its matching remote SHA-256 and open with the installer. A project without an APK must show a clear error rather than retrieving another project's APK.
6. **Closing/renumbering and lifecycle:** Close an idle duplicate tab; remaining duplicates must renumber and keep their own command state. Running/download tabs should not close. Switch to the background and back and check tab selection, output and paths. Recreate the Activity to verify tab restoration. After full process termination, any previously running session must not falsely claim to be running.
7. **Migration:** Upgrade over a previous PocketDev configuration and confirm all saved projects, custom actions, profile assignments, directory paths and previous output remain accessible without re-entering settings.

## Remaining engineering gate

A physical test and a passing `testDebugUnitTest assembleDebug` run are necessary before the feature can be accepted. Do not treat a successful source commit or static review as APK/device validation.
