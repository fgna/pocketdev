# Sprint 3 — retrieve the built APK

## Goal

Extend the validated project build loop with the first artifact handoff:

> Build -> find newest APK -> download to phone -> open/share through Android.

## Included

- discover the newest `.apk` below the configured project, restricted to Gradle-style `build/outputs/apk` paths
- download the discovered APK over the already trusted/authenticated SSH connection using SFTP
- store the artifact in PocketDev's private cache
- expose download state, remote path and local filename
- open the downloaded APK through Android's package/file handling using a `FileProvider`
- preserve the existing project actions and generic command runner

## Excluded

- multi-artifact browser
- release/signing management
- automatic silent installation
- GitHub releases or Actions artifacts
- diagnostic bundle / GitHub issue creation

## Definition of done

Sprint 3 is complete when a physical Android device can run a project build, download the newest generated APK from the remote project with one tap, and hand the local APK to Android for opening/installing without a separate SSH/file-transfer app.
