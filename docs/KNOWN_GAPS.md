# Mise -- known gaps

Open limitations and unresolved risks, stated plainly so nothing gets
silently forgotten. Update this file (add, resolve, or re-open items) as
part of the change that touches them -- see `CLAUDE.md`.

## Project state

- **No app code yet.** Only the design, docs and CI exist. Both
  workflows deliberately fail with "No Gradle project" until the
  scaffold PR lands.
- **The pipeline has never run.** The workflows are adapted from
  RideLink's, which have run, but these copies are unexercised. Expect
  one fix-up round after the first real run.
- **Proposed identifiers are unconfirmed:** `com.blackchapel.mise`,
  minSdk 26, targetSdk 35.

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
- **Backup format is unversioned until written.** `BackupSnapshot` must
  carry a `version` from day one; a breaking change is a MAJOR bump
  (see `CLAUDE.md`).
- **Photos make backups large.** The prototype assumes the user
  accepts that (there is an Include photos switch). Upload size limits
  and resumable uploads are not designed yet.

## Import

- **Fetching pages from the device** will hit sites that block non-browser
  clients or render recipes client-side. Fallback is JSON-LD, then text
  heuristics, then a clear "couldn't read this page, paste the text
  instead" message. Real-world hit rate is unknown.
- **The prototype's heuristics are English-only** (units, headings, junk
  phrases).
- **Quantity ranges** ("2-3 tbsp") currently keep the first value and put
  the range in the note.

## Scaling

- **Step-time scaling is a heuristic.** `partial` uses the square root of
  the factor, which is a reasonable default for simmering and roasting
  but not physics. Users can change each step's mode.
- **Unit systems.** No metric/imperial conversion; amounts keep the unit
  they were entered in (volume and weight units step up/down within
  their own system only).

## Timers

- **Android background limits.** A foreground service keeps timers alive,
  but on some OEM builds aggressive battery management still kills them.
  The foreground-service type and notification-permission flow (Android
  13+) need real-device testing.

## Design

- **The prototype is a web page.** Gestures (edge swipe back, row swipe,
  press and hold, sheet drag) must be rebuilt with Compose gesture APIs
  and Android's predictive back; feel on a real device is unverified.
- **Haptics** in the prototype are a vibration approximation; the mapping
  to `HapticFeedbackConstants` in `DESIGN.md` is untested on devices,
  and some constants need newer API levels (fall back on older ones).
- **Fonts** (Bricolage Grotesque, Geist) load through the Google Fonts
  provider; a first-launch offline fallback to the system font is needed.
