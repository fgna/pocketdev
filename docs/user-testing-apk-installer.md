# APK installer user-testing follow-up

Observed on device:

- the temporary `Install verified APK` control was rendered too low and could be obscured by the output/bottom area
- the existing Workspace `Open APK` control is the correct permanent location for installation
- a verified server-built FreyaHealth APK reached Android `PackageInstaller` and failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

Permanent UX direction:

- keep `Get APK` for download + SHA-256 verification
- reuse the existing Workspace `Open APK` button as the verified PackageInstaller action (label may become `Install APK` once validated)
- remove the temporary extra installer control
- do not offer PackageInstaller for cached artifacts without a `.verified.sha256` marker
- surface PackageInstaller result as today

The observed `UPDATE_INCOMPATIBLE` failure is a signing-certificate mismatch, not transfer corruption. See `docs/apk-signing.md`.