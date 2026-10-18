# RecipAI -- system state

A technical snapshot of what actually exists in this repo right now.
`README.md` is the user/build-facing doc; this one is for picking up
the codebase quickly. Keep it current alongside every change (see
`CLAUDE.md`).

## What exists today

- The approved UI prototype: `docs/design/recipai-prototype.html`
  (single self-contained HTML file, no build step). It is a design
  reference, **not** shipped code. Its JavaScript also contains working
  reference implementations of the scaling engine and the text importer,
  worth porting (see "Logic to port" below).
- The release pipeline: `.github/workflows/build-apk.yml` (Test
  Release), `.github/workflows/release.yml` (Production Release),
  `.github/release.yml` (release-note categories).
- `app/version.properties` (`VERSION_NAME=0.1`).
- Docs: this file, `KNOWN_GAPS.md`, `DESIGN.md`, `RELEASING.md`.

**There is no Android project yet** (no `gradlew`, no `app/build.gradle.kts`,
no Kotlin). Until the scaffold lands, both workflows stop at their
"Check the Gradle project exists" step with a clear error. That is
expected.

## Planned architecture

Offline-first, single module (`app`), single Activity, Compose UI. No
backend: Google Drive is the only network dependency besides fetching a
recipe page the user asked to import.

Proposed identifiers (confirm before the scaffold PR): `applicationId` /
namespace `com.blackchapel.recipai`, minSdk 26, target/compile SDK 35, JDK 17.

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 as the base, custom theme from `DESIGN.md` |
| Navigation | Navigation Compose: four tab roots, pushed detail/backup pages, editor as a modal route |
| Storage | Room (recipes, ingredients, steps, tags, photos metadata); photos as files in app-private storage; DataStore for settings |
| DI | Hilt |
| Images | Coil; photo picker (`PickVisualMedia`) so no storage permission is needed |
| Timers | Foreground service (`foregroundServiceType="specialUse"` or `shortService`, to confirm) + notification with pause / +1 min; survives screen off |
| Import | `ACTION_SEND` share target (`text/plain`); page fetch with OkHttp; parse schema.org `Recipe` JSON-LD first, then fall back to the text heuristics |
| Backup | Google Sign-In / Credential Manager + Drive REST v3, `drive.file` scope (app can only see files it created), WorkManager periodic work for daily/weekly |
| Haptics | `HapticFeedbackConstants` through one `Haptics` wrapper (see mapping in `DESIGN.md`) |
| Fonts | Bricolage Grotesque, Geist, Geist Mono via Google Fonts provider |

## Planned module map

`app/src/main/java/com/blackchapel/recipai/` (none of this exists yet)

- `MainActivity.kt`, `MiseApplication.kt` -- single activity; app-level
  setup, notification channels (timers, backup).
- `data/` -- Room database, DAOs, entities, repository; `BackupSnapshot`
  (the JSON backup format, versioned).
- `domain/scaling/` -- pure Kotlin: quantity parsing and formatting,
  unit step-up/step-down, per-step time scaling. **No Android imports.**
- `domain/importer/` -- pure Kotlin: text parser, JSON-LD reader, tag
  suggestions. **No Android imports.**
- `ui/library/`, `ui/detail/`, `ui/editor/`, `ui/search/`,
  `ui/settings/`, `ui/backup/`, `ui/cook/` -- screens and view models.
- `ui/components/` -- tab bar, floating action button, bottom sheets,
  action sheets, tag chips, swipeable rows, timer pill.
- `ui/theme/` -- color, type, shape, motion tokens.
- `timer/` -- timer manager + foreground service.
- `backup/` -- Drive client, `BackupWorker`, restore.
- `share/ShareReceiverActivity.kt` -- receives shared links/text.
- `haptics/Haptics.kt`.

## Logic to port from the prototype

These were verified in the prototype and should be ported with unit
tests (JVM, run by CI):

- **Scaling:** quantities accept `2`, `1.5`, `1/2`, `1 1/2` and vulgar
  fractions; output uses fractions (¼ ⅓ ½ ...) for cooking units and
  rounded numbers for g/ml; tsp->tbsp->cup and g->kg, ml->l conversions
  when scaling crosses a threshold; singular/plural unit names.
- **Step time modes:** `fixed` (oven, resting), `partial` (scales with
  the square root of the factor, e.g. simmering), `linear` (scales with
  the batch, e.g. chopping). Defaults are guessed from the step text.
- **Typing one amount scales the rest:** new factor = typed amount
  (converted back to the stored unit) / stored quantity.
- **Text import:** section headings (Ingredients / Method / Notes), junk
  line filtering (ads, ratings, "jump to recipe"), ingredient line
  parsing (quantity, unit, name, note, "optional"), sub-headings such as
  "For the sauce", step time detection (`10-12 minutes`, `1 hour 20
  minutes`), tag suggestions.

## Release pipeline (as it exists)

Two manual (`workflow_dispatch`) workflows, mirroring the RideLink repo.
Neither runs automatically; merging to `main` releases nothing.

**Test Release** -- `.github/workflows/build-apk.yml`
- Input: `branch`. Builds a signed APK from that branch.
- Version name: `<MAJOR.MINOR>-<branch>.<shortsha>`; version code: the
  commit count of that branch.
- Runs `testDebugUnitTest`, then `assembleRelease`.
- Uploads the APK as a workflow artifact named
  `recipai-test-<branch>-<version>`. Does not tag or publish.

**Production Release** -- `.github/workflows/release.yml`
- Fails immediately unless run on `main`.
- Version name: `<MAJOR.MINOR>.<PATCH>` where PATCH is one more than the
  highest existing `v<MAJOR.MINOR>.*` tag (0 if none). Refuses to reuse
  an existing tag. Version code: the commit count of `main`.
- Runs `testDebugUnitTest`, `assembleRelease`, then creates the GitHub
  Release `v<version>` with generated notes (grouped by PR label via
  `.github/release.yml`) and the APK `recipai-v<version>.apk` attached.

Both post a Discord embed if `DISCORD_WEBHOOK_URL` is set, and skip it
silently if not.

**Contract the Gradle project must honor** (the workflows depend on it):
- `app/version.properties` with `VERSION_NAME=MAJOR.MINOR`.
- `-PappVersionCode` and `-PappVersionName` override `versionCode` /
  `versionName` (local builds fall back to `1` and the file's value).
- The `release` signing config reads `KEYSTORE_PATH`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from the environment.
- `./gradlew testDebugUnitTest` and `./gradlew assembleRelease` work and
  write `app/build/outputs/apk/release/app-release.apk`.

Differences from RideLink's pipeline: the production workflow refuses to
run off `main`; both run unit tests first; both skip Discord when the
webhook secret is absent; the Test Release job has read-only permissions;
the released APK is named `recipai-v<version>.apk` rather than
`app-release.apk`.

Signing and secrets: see `RELEASING.md`.
