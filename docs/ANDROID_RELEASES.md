# Android release signing and publishing

The debug APK is for testing only. Public releases must be signed with one persistent private Android upload key. Never commit the keystore or its passwords to Git.

## 1. Create the release keystore (Termux or a computer)

In Termux:

```bash
pkg update
pkg install openjdk-17 coreutils
keytool -genkeypair -v \
  -keystore nono-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias nono-release
```

Choose strong passwords and keep them in a password manager. Do not send them in chat and do not lose the keystore: every future APK update must use this same key.

Encode the keystore as one line:

```bash
base64 nono-release.jks | tr -d '\n'
```

Copy the complete output. It is secret material; do not paste it into an issue, commit, or public message.

## 2. Add GitHub Actions secrets

Open the repository on GitHub → Settings → Secrets and variables → Actions → New repository secret. Add these four secrets:

| Secret name | Value |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Complete one-line Base64 output from step 1 |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | `nono-release` |
| `ANDROID_RELEASE_KEY_PASSWORD` | Password for the key alias |

The workflow intentionally fails without these secrets. It does not create a new signing key on each run because that would make upgrades impossible.

## 3. Enable Google sign-in for the release certificate

Before publishing the first APK, run GitHub Actions → **Android Release Certificate Fingerprint** → Run workflow. Open its run summary and copy the SHA-1 and SHA-256 fingerprints. In Firebase Console → Project settings → Your apps → Android app, add both fingerprints. Download the refreshed `google-services.json` and replace `app/google-services.json` in this branch. Commit that updated Firebase configuration.

Debug and release certificates are different. Adding only the debug SHA-1 does not enable Google sign-in in the release APK. The fingerprint workflow does not publish an APK.

## 4. Publish a release

After the signing secrets and Firebase release fingerprint are configured, create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions builds a signed release APK and publishes it to GitHub Releases with generated release notes and a SHA-256 checksum. The first release should use `v1.0.0`; use a new, higher semantic version for each later release (for example `v1.0.1`, then `v1.1.0`). Never replace the signing key or reuse an already-published versionCode.

## 5. Release update notices in Nono

The app checks the public GitHub latest-release endpoint at startup and every 12 hours. When a newer release tag is found, it posts an Android notification and provides the GitHub release page link. The Settings → App updates screen also supports a manual check.

The APK is downloaded from GitHub Releases. Android package installation still requires the user to approve the update, and the app must be signed with the same key as the installed version.
