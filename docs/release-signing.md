# PocketDev release signing

PocketDev uses one long-lived release signing key for developer-signed APKs that F-Droid can verify as reproducible builds.

The private keystore and passwords must never be committed to Git. Keep at least one independent backup of the keystore and its credentials. Losing the key means future APKs cannot continue the same Android signing lineage.

## One-time key setup

Run this on a trusted machine with JDK `keytool` and GitHub CLI `gh` installed:

```bash
set -euo pipefail
umask 077

mkdir -p "$HOME/.local/share/pocketdev-signing"
cd "$HOME/.local/share/pocketdev-signing"

keytool -genkeypair   -keystore pocketdev-release.jks   -storetype PKCS12   -alias pocketdev   -keyalg RSA   -keysize 4096   -validity 10000   -dname "CN=PocketDev Release, O=PocketDev, C=DE"

echo
echo "Release certificate SHA-256 fingerprint:"
keytool -list -v   -keystore pocketdev-release.jks   -alias pocketdev   | sed -n 's/^[[:space:]]*SHA256: //p'   | tr -d ':'   | tr '[:upper:]' '[:lower:]'

echo
echo "Configuring GitHub Actions secrets for fgna/pocketdev..."
base64 < pocketdev-release.jks | tr -d '\n'   | gh secret set POCKETDEV_RELEASE_KEYSTORE_BASE64 --repo fgna/pocketdev

printf '%s' 'pocketdev'   | gh secret set POCKETDEV_RELEASE_KEY_ALIAS --repo fgna/pocketdev

read -rsp "Release keystore password: " RELEASE_PASSWORD
echo
printf '%s' "$RELEASE_PASSWORD"   | gh secret set POCKETDEV_RELEASE_STORE_PASSWORD --repo fgna/pocketdev
printf '%s' "$RELEASE_PASSWORD"   | gh secret set POCKETDEV_RELEASE_KEY_PASSWORD --repo fgna/pocketdev
unset RELEASE_PASSWORD

echo
echo "Secrets configured. Back up this file securely:"
printf '  %s\n' "$HOME/.local/share/pocketdev-signing/pocketdev-release.jks"
```

PKCS12 normally uses the same password for the keystore and private key, which is why the setup stores the same password in both GitHub secrets.

After running the commands, make at least one independent backup of `pocketdev-release.jks` and the password. Do not rely on GitHub Actions secrets as the only copy: secrets cannot be downloaded later as a backup.

## GitHub Actions secrets

The release workflow expects these repository secrets:

- `POCKETDEV_RELEASE_KEYSTORE_BASE64`
- `POCKETDEV_RELEASE_KEY_ALIAS`
- `POCKETDEV_RELEASE_STORE_PASSWORD`
- `POCKETDEV_RELEASE_KEY_PASSWORD`

## Release behavior

When a GitHub Release for a `v*` tag is published, `.github/workflows/publish-signed-release.yml`:

1. checks out that exact release tag;
2. runs the unit tests and builds the unsigned release APK;
3. zip-aligns the APK;
4. signs it with Android SDK `apksigner`;
5. verifies the APK and prints the signing certificate SHA-256 fingerprint;
6. uploads `PocketDev-<version>.apk` to the existing GitHub Release.

The workflow intentionally does not overwrite an existing release asset. A duplicate asset therefore fails rather than silently replacing a binary that F-Droid may already have verified.

## F-Droid metadata

Once the first signed APK is published, update fdroiddata with a versioned binary URL such as:

```yaml
Binaries: https://github.com/fgna/pocketdev/releases/download/v%v/PocketDev-%v.apk
AllowedAPKSigningKeys: <lowercase SHA-256 certificate fingerprint>
```

F-Droid requires the signing-key value as lowercase hexadecimal without colons.

Do not add `Binaries` before the corresponding release APKs actually exist, because F-Droid would then try to fetch missing binaries for configured builds.
