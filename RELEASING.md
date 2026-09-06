# Releasing PocketDev

PocketDev uses a simple Semantic Versioning release process. The public repository is self-contained: release rules, build instructions, tags and F-Droid metadata do not depend on the private project tracker.

## Version format

Use `MAJOR.MINOR.PATCH` for `versionName`:

- `PATCH` for bug fixes and small maintenance changes.
- `MINOR` for backwards-compatible user-visible features.
- `MAJOR` for incompatible changes or a major product reset.

Android `versionCode` is a monotonically increasing integer. Every published build gets a new value and values are never reused.

Example:

```kotlin
versionCode = 4
versionName = "1.1.0"
```

## Release source of truth

A release is identified by the exact commit tagged `v<versionName>`, for example `v1.1.0`.

The following must agree for every release:

1. `app/build.gradle.kts` `versionName`
2. `app/build.gradle.kts` `versionCode`
3. Git tag `v<versionName>`
4. GitHub Release version
5. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
6. F-Droid metadata

Published tags are immutable. Never move or replace an existing release tag. Fix a bad release with a new PATCH version.

## Release checklist

1. Confirm all intended issues/PRs are merged and the tree is clean.
2. Choose the next version and increment `versionCode`.
3. Update the Fastlane changelog for the new `versionCode`.
4. Run the release validation build:

```bash
./gradlew clean testDebugUnitTest assembleRelease
```

5. Merge the version/changelog change to `main`.
6. Tag that exact `main` commit and push the tag:

```bash
git checkout main
git pull --ff-only
git tag -a v1.1.0 -m "PocketDev 1.1.0"
git push origin v1.1.0
```

7. Create the GitHub Release from the same tag and use the changelog as the release notes basis.
8. For F-Droid, ensure the corresponding metadata points to that immutable tag and version code.

## Release cadence

PocketDev does not use a fixed calendar release cycle. Create a release when a coherent, tested set of changes is worth distributing. Routine development merges do not need a release.

## Current baseline

The current release baseline is defined by the highest published immutable SemVer tag together with the matching `versionName` and monotonically increasing `versionCode`. Never reuse or move an existing release tag; every new public build gets a new version and version code.
