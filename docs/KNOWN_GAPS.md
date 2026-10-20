# Recipai -- known gaps

Open limitations and unresolved risks, stated plainly so nothing gets
silently forgotten. Update this file (add, resolve, or re-open items) as
part of the change that touches them -- see `CLAUDE.md`.

## Project state

- **The merged app has never been compiled by Android tooling.** This
  sandbox cannot reach Google's Maven repository or the Android SDK. M1
  passed one Test Release; everything since (the six modules) was
  checked only by `tools/desktop-check` (the shared Compose code against
  Compose Desktop 1.7.0, plus `:core` and `app/src/test` on the JVM).
  The Android-only files were reviewed by hand against the pinned
  library versions but never compiled: `MainActivity`, `MainViewModel`,
  `DataStoreSettings`, `Haptics`, `Fonts`, `Icons`, `ui/Platform.kt`,
  and everything in `platform/` (`nav/PageBackHandler`,
  `net/PageFetcher`, `timers/*`, `photos/*`, `backup/*`), plus the
  manifest. Expect a fix-up round on the first Test Release.
- **Nothing has run on a device.** Specifically unverified: haptic
  strength and feel; the back swipe; swipe rows; the rise and hero
  entrance animations; timers with the screen off, the foreground
  service, the notifications and the chime; the photo picker and EXIF
  handling on real camera photos; the photo viewer's gestures; keyboard
  and IME behavior in the editor; Google Drive connect, backup, restore
  and background backups; spacing, fonts and illustrations in both
  themes.
- **The pipeline has run once** (M1's Test Release). The Production
  Release workflow has never run.

## Release pipeline

- **Test and production builds share one signing key** (as in RideLink)
  and one `applicationId`, so a test build installs over a production
  install and vice versa. A separate `applicationIdSuffix` for test
  builds would allow both side by side but would need its own Google
  OAuth client (below). Not done.
- **Release builds are not minified** (as in RideLink). Revisit before
  a public release; note the backup module calls
  `GoogleAuthUtil.clearToken` by reflection, which R8 could strip.
- **No CI on pull requests.** Compilation and tests only run when the
  user dispatches Test Release. A broken PR is therefore found late.
- **GitHub Release notes** depend on PR labels; unlabeled PRs go under
  "Other Changes".
- **The signing keystore is a single point of failure.** If it's lost,
  installed copies can't be updated in place. `RELEASING.md` says where
  to keep an offline copy.

## Google Drive backup

- **Needs a Google Cloud OAuth client, with the Drive API enabled in
  that project**, created by the user: an Android client bound to the
  package name `com.blackchapel.recipai` and the **SHA-1 of the release
  signing key**, plus a consent screen. Until then "Connect Google
  account" shows "Google sign-in isn't set up for this build of Recipai
  yet (error 10 ...)"; without the Drive API, "The Google Drive API
  isn't turned on ...". Steps are in `RELEASING.md`. Test builds share
  the release key, so they share that client.
- **Consent-screen verification.** `drive.file` is a non-sensitive scope,
  so verification should not be required, but this is unconfirmed until
  the consent screen is configured.
- **Background backups are best-effort.** WorkManager defers work under
  Doze and battery saver, and periodic work drifts from the exact hour
  (a 24 h or 7 d period from the first run); "daily at 2 AM" means
  "roughly daily, when the OS allows". An overdue schedule is caught up
  once when the app opens, but only once a first backup exists.
- **Stale tokens** are cleared through `GoogleAuthUtil.clearToken`
  called by reflection (play-services-auth 21.2.0 has no
  `AuthorizationClient.clearToken`). If that class is missing or
  stripped, the retry falls back to asking the user to sign in again.
- **Disconnect revokes access at Google only** when a token can be had
  silently and the network is up; otherwise access stays granted at
  Google (removable in the Google account). The app forgets the
  connection either way.
- **Photos make backups large.** Only photos present in this phone's
  photo folder are backed up; a recipe whose photo file is missing is
  backed up without it, and restore drops photo names whose files are
  not in the backup (and says how many). A backup is built in the cache
  directory as one zip before upload, so it needs about its own size in
  free space; entries over 64 MB are refused when reading.
- **Restore replaces every recipe** (no merge). The "Before restore"
  backup is the undo, and it counts toward the six kept, so it can push
  the oldest backup out, but never the backup being restored.
- **Restore race (small).** The current recipes are read after the
  download; edits made while the "Before restore" backup is uploading
  are in neither that backup nor the restored library.
- **A manual backup or restore is cancelled when the app is left with
  Back** (it runs in the ViewModel scope and Back finishes the activity).
  A restore cancelled before the recipes are replaced leaves the
  extracted photos behind, which the next launch's photo cleanup removes;
  the library is unchanged.
- **Automatic backups have a time limit (ANDROID-3).** A WorkManager
  (non-foreground) worker must build and upload within about 10 minutes,
  the JobScheduler limit. If it takes longer the system stops it and the
  retry starts the zip and upload from scratch (the resumable session is
  not kept across runs), so a very large photo library on a slow uplink
  may never finish automatically. Workaround: "Back up now" in the app.
  A later fix could persist the resumable session URI and the built zip
  across attempts. `setForeground` is no answer: Android 12+ blocks
  starting foreground services from the background.
- **A reset WorkManager database loses the schedule.** If it is reset
  while `backup-status.json` says scheduled, the schedule is not
  re-registered until a backup setting changes. Hardening idea: check
  `WorkManager.getWorkInfosForUniqueWork` at startup.
- **Account details are cached.** The email and name come from Drive's
  about endpoint and are stored in `backup-status.json`; a display-name
  change shows after the next Connect.
- **Schedule times** are the prototype's three (Overnight 2 AM, Morning
  8 AM, Evening 8 PM); there is no free time picker. Times are
  formatted en-US (h:mm a).
- **Photos are only on the device until backed up**: `allowBackup` is
  false, which turns off cloud backup. On Android 12+ a phone-to-phone
  transfer still copies recipes, photos and settings. `backup-status.json`
  is deliberately excluded (`data_extraction_rules.xml`), so a
  transferred phone starts as "not connected" and the user connects once.

## Import

- **No AI in import (deliberate, for now).** Messy text, social-media
  captions and photos of recipes are handled only as well as the rules
  allow; photos of recipes aren't supported at all. LLM support is
  deferred, not rejected; see "AI / LLM features" in `SYSTEM_STATE.md`
  for the constraints (no API key in the app, proxy server needed for a
  cloud model).
- **Fetching pages from the device** hits sites that block non-browser
  clients, render recipes with JavaScript, or sit behind consent walls,
  bot protection or a login. The fallback order is: recipe card, then
  page text, then "Couldn't find a recipe ... paste the text instead",
  with Paste text instead and Edit the page text. Real-world hit rate is
  unknown; no cookies are kept between requests.
- **Only schema.org JSON-LD recipe cards are read.** Microdata/RDFa
  markup falls back to the page-text rules.
- **http-only sites fail**: links and redirects are upgraded to https
  because Android blocks cleartext traffic. They now fail with "doesn't
  seem to offer this page over a secure connection" (`HTTP_ONLY`, no Try
  again) instead of a network or redirect error; a timeout while
  connecting to an upgraded http link is still reported as a timeout.
  A link fetch has an overall 45 s limit.
- **Non-ASCII (IDN) host names are rejected** by `Links.normalize`.
- **Pages are capped at 4 MB.** A recipe card after the cap is lost,
  though cards are almost always in `<head>`.
- **The recipe card's image URL is ignored**; photos from a link would
  need the photos module to download and store the image.
- **HowToSection names are dropped** ("For the sauce"); steps are
  flattened because steps have no section headings in the model.
- **Prep, cook and total times go into the notes** as text, because the
  model only sums step times.
- **A servings label from a page's yield is used as given** ("1 loaf"
  still reads "loaf" when scaled to 2).
- **The link progress list's pacing** (about 320 ms per instant step)
  and its spinners don't honor the system remove-animations setting.
- **The heuristics are English-only** (units, headings, junk phrases,
  the recipe-card tag mapping, the page-text fallback).
- **Quantity ranges** ("2-3 tbsp") keep the first value and put the
  range in the note when imported; typed into the editor's quantity
  field, a range is flagged as not a number.

## Editor

- **The draft survives rotation but not process death.** If the OS
  kills the app in the background, unsaved edits are lost.
- **Reorder is up/down buttons only**; there is no drag handle.
- **The quantity field uses the decimal keypad**, and most have no "/":
  cooks type 1.5 rather than 1 1/2 (pasted and existing fractions still
  work).
- **Backspace on an empty tag field doesn't remove the last tag** (soft
  keyboards don't send that key reliably); the chip's x does.
- **TalkBack labels** for the editor's fields come from
  `contentDescription` (Compose 1.7 has no placeholder/label semantics
  for these fields); needs a device check.
- **The mini-timer pill still floats over the editor and Backup page**;
  only bottom clearance was added.
- **Accessibility semantics are untested on a real TalkBack** (names,
  selected / checked states, the detail Timers button's description). The
  hero image's tap target has only a click label ("View larger image"),
  not a content description.
- **Photos still copying from the detail page** are dropped if the
  editor for the same recipe is opened meanwhile and then saved; the
  files are cleaned up as unused.

## Photos

- **No pinch-to-zoom** in the viewer, and **no reordering** in the
  editor (add, set cover, remove only).
- **HEIC/HEIF can't be decoded on Android 8.x** (API 26-27;
  BitmapFactory gained HEIF in API 28); such photos are reported as
  "couldn't be read".
- **Unused photo files are deleted only at the next launch**, and only
  once they are an hour old, so a deleted recipe's photos stay on disk
  until then (this is what makes Undo work).
- **The Play-services photo-picker backport is not declared** in the
  manifest; phones without the system picker get the document picker,
  which works but looks different.

## Timers and cook mode

- **Timers are in memory only.** If Android kills the process (for
  example aggressive OEM battery management despite the foreground
  service), running timers are lost; the service returns
  `START_NOT_STICKY` because there is nothing to restore.
- **Doze and OEM power savers:** the service holds a partial wake lock
  while a timer runs (refreshed each pass, 10-minute timeout as a safety
  net) instead of an exact alarm, which would need
  SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM. Whether a finish fires on time
  in deep Doze, and the `specialUse` service type itself, need testing on
  Android 13/14/15. A wake lock held through a long simmer costs some
  battery.
- **A refused foreground-service start is swallowed** (Android 12+
  refuses starts from the background; timers only start from taps in the
  app, so this should not happen). The in-app ticker then still runs the
  timers while the app is open, with no screen-off guarantee.
- **Notification permission denied** (or asked once and dismissed): no
  ongoing countdown and no "Time's up" notification; the chime and the
  in-app haptic and toast still fire. The app doesn't ask again or link
  to settings.
- **Double buzz in the foreground:** a finish both plays the in-app
  warning haptic and posts a heads-up notification whose channel also
  vibrates.
- **The chime uses the alarm stream**, so it sounds in silent mode at
  alarm volume; there is no separate volume control.
- **IngredientUses is an English word matcher:** "salt and pepper" in a
  step also matches "red bell pepper", as in the prototype.
- **Cook mode doesn't honor reduce motion** (step slide, segment fill,
  pill pulse), like the rest of the app.
- **Timers of a deleted recipe are dropped** and don't come back on
  Undo. A timer whose step was removed in the editor keeps running and
  can be cancelled from the sheet.

## Storage

- **All recipes live in one JSON file**, read fully into memory and
  rewritten on every change. Fine for hundreds of recipes; revisit
  (Room, or one file per recipe) if libraries get much larger.
- **An unreadable recipe file is set aside, not repaired.** It is
  renamed to `recipes.json.unreadable-<time>` and the library starts
  empty in its place, with a toast. There is no in-app way to recover it yet
  (a Drive backup is the way back). Photo clean-up is skipped that run.
- **A failed save (e.g. disk full) only shows a toast.** The app keeps
  working from memory and retries on the next change; quitting before
  a save succeeds loses those changes.
- **Scale factor and checklists are per visit.** Leaving a recipe resets
  its scale, checked ingredients and done steps (as in the prototype).
  They survive rotation but not process death; cook mode's step index
  likewise.

## Scaling

- **Step-time scaling is a heuristic.** `partial` uses the square root of
  the factor, which is a reasonable default for simmering and roasting
  but not physics. Each step's mode is guessed from its text on import
  and can be changed in the editor.
- **Unit systems.** No metric/imperial conversion; amounts keep the unit
  they were entered in (volume and weight units step up/down within
  their own system only).

## Design

- **Back gesture.** The page follows the finger only on Android 14+ with
  gesture navigation. On older versions and with 3-button navigation,
  and for the modal editor, cook mode and stacks deeper than one page,
  back pops on release with the normal animation. There is no in-app
  edge drag, because the system owns the edge. While a page follows the
  finger the tab bar stays hidden; it slides back in after the pop.
- **Haptics** now use vibration compositions and predefined effects, and
  are untested on devices. Many non-Pixel phones lack primitive support
  and fall back to predefined effects (`EFFECT_CLICK` vs
  `EFFECT_HEAVY_CLICK` then carries the "Import and + softer than
  medium" difference), so the feel varies by OEM. The tuning values are
  first guesses from user feedback.
- **Swipe rows exist only in the list layout** (as designed); grid cards
  use press and hold. Unfavoriting a row in the Favorites tab removes it
  at once rather than collapsing it; only swipe-Delete collapses.
- **Motion and "remove animations".** The rise entrance and back swipe
  honor the system animation scale; the link progress list and cook
  mode don't yet.
- **No blur.** The design's bars are translucent and blurred; the app
  uses a near-opaque color (about 95%) without blur, so text behind the
  tab bar doesn't show through. A real blur would be a refinement.
- **Illustrations are simpler than the prototype's** (a seeded plate
  with generic items), and only recognize English ingredient names.
- **Bundled fonts add about 0.7 MB** to the APK (three variable TTFs).
- **Strings are English-only** throughout.
