# Releasing Recipai

How signing works, the secrets the workflows need, and how a release is
cut. See `SYSTEM_STATE.md` for what the workflows do step by step.

## One-time setup

You do these; Claude cannot create keys or set repository secrets.

### 1. Create the signing keystore (on your computer)

```sh
keytool -genkeypair -v \
  -keystore recipai-release.jks \
  -alias recipai \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong store password and key password (they may be the same).
Fill in the name fields however you like.

**Back it up.** Keep `recipai-release.jks` and both passwords in a password
manager or other offline storage. If you lose this key, you can never
publish an update that installs over existing copies. **Never commit
it** (`.gitignore` blocks `*.jks`).

### 2. Add the repository secrets

GitHub -> the repo -> Settings -> Secrets and variables -> Actions ->
New repository secret:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the keystore, base64-encoded: `base64 -w0 recipai-release.jks` (macOS: `base64 -i recipai-release.jks`) |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `recipai` (or whatever you passed to `-alias`) |
| `KEY_PASSWORD` | the key password |
| `DISCORD_WEBHOOK_URL` | a Discord channel webhook: the build and release notifications are posted to it (as in RideLink, the notify job fails without it) |

With the GitHub CLI:

```sh
base64 -w0 recipai-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

### 3. Get the signing key's SHA-1 (needed for Google Drive backup)

```sh
keytool -list -v -keystore recipai-release.jks -alias recipai | grep SHA1
```

### 4. Google Cloud project (needed for Drive backup)

1. console.cloud.google.com -> create a project -> enable the **Google
   Drive API**.
2. OAuth consent screen: app name Recipai, your support email; add the
   `.../auth/drive.file` scope. (This scope only lets the app see files
   it created.)
3. Credentials -> Create credentials -> OAuth client ID -> **Android**:
   package name `com.blackchapel.recipai` (confirm in the scaffold PR) and
   the SHA-1 from step 3.
4. While the consent screen is in "Testing", add your Google account as
   a test user.

Test and production builds share the same key and package, so one OAuth
client covers both.

## Day to day

1. Work on a feature branch (never `main`).
2. Push it. Actions -> **Test Release** -> Run workflow -> enter the
   branch. Download the APK from the run's **Artifacts** and install it.
3. When you've tested and approved it, a PR is opened into `main`
   (labelled `feature`, `fix`, `docs`, `chore` ... so release notes
   group correctly). Review and merge it yourself.
4. Repeat for more changes. Merging releases nothing.

## Cutting a release

Actions -> **Production Release** -> Run workflow, with **`main`**
selected as the branch. It:

1. Works out the version: `MAJOR.MINOR` from `app/version.properties`
   plus the next patch for that line (`0.1.0`, then `0.1.1`, ...).
2. Runs the unit tests and builds the signed APK.
3. Creates the tag `v<version>` and a GitHub Release with
   auto-generated notes and `recipai-v<version>.apk` attached.
4. Posts to Discord if configured.

Changing MAJOR.MINOR is done in the PR that earns it (see `CLAUDE.md`),
not at release time. The first release after a bump starts again at
patch 0.

## If something fails

- **"No Gradle project"** -- the app scaffold isn't on that branch yet.
- **"Tag ... already exists"** -- someone tagged by hand; delete the stray
  tag or bump MAJOR.MINOR.
- **"Production releases are cut from main only"** -- re-run with `main`.
- **Signing errors** -- check the four signing secrets; `KEYSTORE_BASE64`
  must be a single line with no newlines.
