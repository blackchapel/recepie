# Recipai -- known gaps

Open limitations and unresolved risks, stated plainly so nothing gets
silently forgotten. Update this file (add, resolve, or re-open items) as
part of the change that touches them -- see `CLAUDE.md`.

## Project state

- **The Android code has never been compiled by Android tooling.** This
  sandbox cannot reach Google's Maven repository or the Android SDK, so
  the first real compile is the first Test Release run. Mitigations:
  `:core` and the repository tests run locally; the shared Compose code
  was compile-checked against Compose Desktop (the same APIs). The
  Android-only files (`MainActivity`, `MainViewModel`,
  `DataStoreSettings`, `Haptics`, `Fonts`, `Icons`, `ui/Platform.kt`)
  and the Gradle/AGP wiring were only reviewed. Expect a possible
  fix-up round.
- **The pipeline has never run.** The workflows are adapted from
  RideLink's, which have run, but these copies are unexercised. The
  first Test Release is also the first test of the signing secrets.
- **Not yet built** (see the roadmap in `SYSTEM_STATE.md`): the recipe
  editor (the + button and Search's "New recipe" show a toast that
  offers Import instead), timers and cook mode (step times are shown and
  scaled but can't be started; there is no Start cooking bar), link
  import and the share target (the "From a link" tab explains this),
  photos, Google Drive backup (no Backup group in Settings yet), and
  swiping list rows. The detail page's more menu and the press-and-hold
  sheet offer only what exists (Open, Favorite, Delete).
- **The UI has not been seen on a device.** Spacing, font rendering of
  the variable fonts, the generated illustrations and animation feel
  need a pass on real phones in both themes.

## Release pipeline

- **Test and production builds share one signing key** (as in RideLink)
  and one `applicationId`, so a test build installs over a production
  install and vice versa. A separate `applicationIdSuffix` for test
  builds would allow both side by side but would need its own Google
  OAuth client (below). Not done; decide when the first real test build
  is installed.
- **Release builds are not minified** (as in RideLink). Revisit before
  a public release.
- **No CI on pull requests.** Compilation and tests only run when the
  user dispatches Test Release. A broken PR is therefore found late.
- **GitHub Release notes** depend on PR labels; unlabeled PRs go under
  "Other Changes".
- **The signing keystore is a single point of failure.** If it's lost,
  installed copies can't be updated in place. `RELEASING.md` says where
  to keep an offline copy.

## Google Drive backup

- **Needs a Google Cloud OAuth client**, created by the user: an Android
  client bound to the package name and the **SHA-1 of the release
  signing key**, plus a consent screen. Without it, "Connect Google
  account" cannot work. Steps are in `RELEASING.md`.
- **Consent-screen verification.** `drive.file` is a non-sensitive scope,
  so verification should not be required, but this is unconfirmed until
  the consent screen is configured.
- **Background backups are best-effort.** WorkManager defers work under
  Doze and battery saver; "daily at 2 AM" means "roughly daily, when the
  OS allows". The Wi-Fi-only option adds a network constraint.
- **Backup format.** The on-device recipe file (`RecipeJson`) already
  carries a `version` and ignores unknown keys; the Drive backup should
  build on it. A change older versions can't read is a MAJOR bump (see
  `CLAUDE.md`).
- **Photos make backups large.** The prototype assumes the user
  accepts that (there is an Include photos switch). Upload size limits
  and resumable uploads are not designed yet.

## Import

- **No AI in import (deliberate, for now).** Messy text, social-media
  captions and photos of recipes are handled only as well as the rules
  allow; photos aren't supported at all. LLM support is deferred, not
  rejected; see "AI / LLM features" in `SYSTEM_STATE.md` for the
  constraints (no API key in the app, proxy server needed for a cloud
  model).

- **Fetching pages from the device** will hit sites that block non-browser
  clients or render recipes client-side. Fallback is JSON-LD, then text
  heuristics, then a clear "couldn't read this page, paste the text
  instead" message. Real-world hit rate is unknown.
- **The prototype's heuristics are English-only** (units, headings, junk
  phrases).
- **Quantity ranges** ("2-3 tbsp") currently keep the first value and put
  the range in the note.

## Storage

- **All recipes live in one JSON file**, read fully into memory and
  rewritten on every change. Fine for hundreds of recipes; revisit
  (Room, or one file per recipe) if libraries get much larger or photos
  move into the model.
- **An unreadable recipe file is set aside, not repaired.** It is
  renamed to `recipes.json.unreadable-<time>` and the examples load in
  its place, with a toast. There is no in-app way to recover it yet.
- **A failed save (e.g. disk full) only shows a toast.** The app keeps
  working from memory and retries on the next change; quitting before
  a save succeeds loses those changes.
- **Scale factor and checklists are per visit.** Leaving a recipe resets
  its scale, checked ingredients and done steps (as in the prototype).
  They survive rotation but not process death.

## Scaling

- **Step-time scaling is a heuristic.** `partial` uses the square root of
  the factor, which is a reasonable default for simmering and roasting
  but not physics. Each step's mode is guessed from its text; changing
  it needs the editor (M2).
- **Unit systems.** No metric/imperial conversion; amounts keep the unit
  they were entered in (volume and weight units step up/down within
  their own system only).

## Timers

- **Android background limits.** A foreground service keeps timers alive,
  but on some OEM builds aggressive battery management still kills them.
  The foreground-service type and notification-permission flow (Android
  13+) need real-device testing.

## Design

- **Back gesture.** Swipe from the left edge is Android's own back
  gesture (handled with `BackHandler`); there is no in-app "page follows
  the finger" drag, and predictive back's preview animation is not
  wired up.
- **No blur.** The design's bars are translucent and blurred; the app
  uses the translucent color without blur (the design's stated solid
  fallback).
- **Haptics** are untested on devices. `SEGMENT_TICK` needs API 34 and
  `CONFIRM`/`REJECT` API 30; older versions fall back to `CLOCK_TICK`
  and `LONG_PRESS`.
- **Illustrations are simpler than the prototype's** (a seeded plate
  with generic items), and only recognize English ingredient names.
- **Bundled fonts add about 0.7 MB** to the APK (three variable TTFs).
