# Stable debug signing for PocketDev APK updates

Android only permits an installed application to be updated by an APK signed with the same signing certificate.

PocketDev builds projects on a remote development server. Android Gradle debug builds normally use `~/.android/debug.keystore`, so building the same application on two different development machines can produce APKs with different signatures. In that case Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE` even when the APK is otherwise valid and its package/version are correct.

For projects installed through PocketDev, use one stable debug signing key across every machine that is expected to build update-compatible APKs. Do not generate a new debug keystore on each server.

Recommended development setup:

1. Choose one existing debug keystore as the canonical development signing key.
2. Copy it securely to the remote PocketDev build server at `~/.android/debug.keystore` with file mode `600`.
3. Keep the keystore out of Git and backups that are shared publicly.
4. Rebuild the APK on the server and install it through PocketDev.

If an app already installed on the phone was signed by another key, either make the build server use that original key or uninstall the existing app once before installing the new signing lineage. Uninstalling deletes application-local data, so aligning the signing key is preferable when data matters.

PocketDev's SHA-256 artifact verification checks transfer integrity; it cannot make an APK signed by a different key update-compatible.