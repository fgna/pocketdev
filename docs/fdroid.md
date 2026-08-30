# F-Droid submission

PocketDev is prepared for submission to the official F-Droid repository.

## Readiness

- Public source repository: `https://github.com/fgna/pocketdev`
- Application ID: `de.fgna.pocketdev`
- License: GPL-3.0-or-later
- Build system: Gradle wrapper, JDK 17
- Current release version: `versionName 1.0.0`, `versionCode 2`
- Store metadata: `fastlane/metadata/android/en-US/`
- fdroiddata template: `fdroid/de.fgna.pocketdev.yml`

The Android dependencies are AndroidX/Jetpack Compose, Kotlin coroutines and SSHJ. No Firebase, Google Play Services, Crashlytics, advertising SDK or analytics SDK is intentionally included. SSHJ is Apache-2.0 and distributed through Maven Central.

## Local verification

Run the same command-line release path expected by F-Droid before submitting:

```bash
./gradlew clean testDebugUnitTest assembleRelease
```

The unsigned release APK should be produced under `app/build/outputs/apk/release/`.

## Release convention

Every public release must have a unique monotonically increasing `versionCode`. Create an annotated or lightweight Git tag matching `v<versionName>` only after the release commit contains the matching `versionName` and `versionCode`.

Example for the next release:

```text
versionName = "1.0.1"
versionCode = 3
tag = v1.0.1
```

Fastlane changelogs use the Android version code as filename, for example `fastlane/metadata/android/en-US/changelogs/3.txt` for versionCode 3.

## Official submission

The YAML in `fdroid/de.fgna.pocketdev.yml` is a source template for the eventual `fdroiddata` merge request. The official submission still happens in the F-Droid `fdroiddata` project, where the metadata file is added as `metadata/de.fgna.pocketdev.yml` and the F-Droid CI/reviewer checks are run.

The initial build recipe points to an immutable commit containing versionName 1.0.0, versionCode 2 and the GPL-3.0-or-later license. Future releases should use immutable `v<versionName>` tags.

Before opening that merge request:

1. Ensure the release build succeeds from a clean checkout.
2. Confirm that the immutable revision referenced by the YAML is the intended first F-Droid version.
3. Run F-Droid lint/build locally if the packaging toolchain is available.
4. Copy the YAML into a fork of `fdroid/fdroiddata` and open the merge request.

If F-Droid flags a transitive dependency, inspect the dependency tree with `./gradlew :app:dependencies` and document or remove the dependency before submission.
