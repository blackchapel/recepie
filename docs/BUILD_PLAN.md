# Recipai -- build plan to the first release

How the rest of the app is built: independent modules built in
parallel by separate agents, stitched together by a coordinator, then
reviewed by an independent reviewer before any release. Everything lands
on `feature/app-scaffold`; it merges into `main` only when it is ready
to release (the user decides).

## Status before this plan

M1 is done and passed its first Test Release: library, detail with live
scaling, paste-text import, search, settings, JSON storage, haptics.
User feedback on that build is folded into the modules below.

## Ground rules for every module

- **Read first:** `CLAUDE.md`, `docs/DESIGN.md`, the prototype
  `docs/design/recipai-prototype.html` (the reference for look and
  behavior; its JavaScript shows exactly how each feature behaves),
  `docs/SYSTEM_STATE.md`, `docs/KNOWN_GAPS.md`.
- **Own your files.** Each module owns the files listed for it. Touch
  shared files only at the listed touchpoints, keep those edits small
  and additive, and put new `AppController` members in a section at the
  end of the class headed `// ---- <module> ----`.
- **Do not edit** `README.md`, `docs/SYSTEM_STATE.md`,
  `docs/KNOWN_GAPS.md`, `docs/DESIGN.md`, `app/build.gradle.kts`,
  `core/build.gradle.kts`, `.github/`, `app/version.properties`. Report
  doc changes and gaps in your hand-back; the coordinator writes the docs.
  The dependencies and permissions every module needs are already in
  place (Coil, ExifInterface, play-services-auth, WorkManager; INTERNET,
  POST_NOTIFICATIONS, FOREGROUND_SERVICE(_SPECIAL_USE), VIBRATE, the
  SEND share target). Manifest additions you need (a `<service>`, a
  `<provider>`) are allowed; add only your own entries.
- **Pure logic goes in `:core`** with unit tests (`core/src/test`).
- **Android-only code goes in a `platform` package**
  (`com.blackchapel.recipai.platform...`) with a stub of the same public
  API in `tools/desktop-check/stubs/` (file named `<Name>Stub.kt`).
  Shared UI must stay compilable by the desktop check.
- **Verify before handing back** (this sandbox has no Android SDK):
  `./gradlew --no-daemon -p tools/desktop-check :core:test :ui:compileKotlin :ui:test`
  must pass. Android-only files can only be reviewed: check every
  import and signature against the library versions in
  `app/build.gradle.kts` (Compose BOM 2024.10.01, activity 1.9.2,
  lifecycle 2.8.6, core-ktx 1.13.1, play-services-auth 21.2.0, work
  2.9.1, coil 2.7.0, compileSdk 35, minSdk 26).
- **Haptics:** every interaction names a `HapticType` (see DESIGN.md).
- **Avoid** `List.removeLast()`/`removeFirst()` (crash on older Android);
  use `removeAt(lastIndex)`.

## Modules

### 1. Editor (M2)
Owns `ui/editor/**`, editor logic in `:core` (e.g. `RecipeDraft.kt`).
- Modal page (`EditorPage`, slides up; already routed): Cancel / Save
  top bar, Cancel with unsaved changes asks first (action sheet).
- Photo strip via `EditorPhotoStrip` (photos module implements it).
- Basics: title (required; inline error), description, servings,
  "counted as" (servings label). Tags: chips with remove, add field,
  suggestions (`TextRecipeParser.guessTags`, existing tags).
- Ingredients: quantity, unit, name, note rows; section headings; reorder
  (up/down or drag), remove; pasting several lines into a name field
  adds them all (`TextRecipeParser.parseIngredientLine`).
- Method: text, minutes, scaling mode (fixed / scales a little / scales
  with batch); reorder; remove; "Found 12 min, use it" chip from
  `TextRecipeParser.detectMinutes` / `guessTimeScale`.
- Notes. Save: new or update via `RecipeRepository.upsert` (bump
  `updatedAt`), success haptic, open/return to the detail page.
- `EditorPage.preset` (import "Review") prefills from a `ParsedRecipe`.
- Touchpoints: none beyond `AppController` additions if needed.

### 2. Timers and cook mode (M3)
Owns `ui/timers/**`, `ui/cook/**`, `platform/timers/**`, timer logic in
`:core` (e.g. `Timers.kt`, `IngredientUses.kt`).
- Step time chip (`StepTimeChip`): tap starts a timer (medium haptic),
  tap again pauses/resumes, finished shows Done (tap clears). Live
  countdown; uses the scaled minutes it is given.
- Timers sheet (`Sheet.Timers`): each timer with ring, pause/resume, +1
  min, cancel. Mini-timer pill (`TimerOverlay`) while any runs.
- Detail bottom bar (`DetailBottomBar`, set `DetailBottomBarHeight`):
  timers button (shows the soonest countdown) and the flame **Start
  cooking** button (`app.startCooking(recipeId, page.factor)`).
- Cook mode (`CookPage`): progress segments, one step large, the scaled
  ingredients that step uses, big timer button, swipe left/right and
  buttons, Finish on the last step; keeps the screen on when the setting
  is on.
- A finishing timer: warning haptic, a chime if Timer sound is on, and a
  notification. Timers keep running with the screen off: a foreground
  service (`specialUse`, subtype "cooking timers") with an ongoing
  notification; ask for POST_NOTIFICATIONS on Android 13+ the first time
  a timer starts.
- Settings rows (`CookingSettingsRows`): Keep screen on in cook mode,
  Timer sound.

### 3. Link import (M4)
Owns `ui/importer/**` (extend), `platform/net/**`, import logic in
`:core` (e.g. `JsonLdRecipeReader.kt`, `HtmlText.kt`).
- `:core`: read schema.org `Recipe` JSON-LD from page HTML (script
  `application/ld+json`, `@graph`, arrays, `HowToStep`/`HowToSection`,
  `recipeYield`, ISO 8601 durations, keywords/category to tags, image
  URL ignored for now); fall back to visible page text through
  `TextRecipeParser`. Unit tests with realistic fixtures.
- App: "From a link" tab per the prototype: URL field, Convert, the
  step-by-step progress list, clear errors ("couldn't read this page,
  paste the text instead"). Saved recipes keep the source (host + path).
- Page fetch in `platform/net` (HttpURLConnection, browser-like
  User-Agent, timeouts, size cap, redirects), off the main thread.
- Shared links: `MainActivity` already calls `app.handleSharedText`;
  make a shared link convert on its own (medium haptic).

### 4. Photos (M5) and the detail image animation
Owns `ui/photos/**`, `platform/photos/**`.
- Photo picker (`PickVisualMedia`, no storage permission), copy into
  `filesDir/photos/<uuid>.jpg` downscaled (max 2048 px, EXIF orientation
  applied). `Recipe.photos` holds those file names; `coverIndex` the
  cover. Clean up files no recipe references (on start).
- `RecipeImage` shows the cover photo (Coil) else the illustration;
  `DetailHero` the same, plus a photo count badge.
- **User feedback: the image animation when opening a recipe is
  missing.** Hero entrance like the prototype's `plate-in` (rotate -14
  degrees and scale 1.15 to rest, about 1.4 s, ease-out) for the
  illustration, a gentle scale-in for photos.
- `DetailPhotoStrip`: thumbnails (tap to select hero / open viewer) and
  an Add photo tile. `PhotoViewer` (`Sheet.PhotoViewer`): full screen,
  swipe between photos, tap to close.
- `EditorPhotoStrip`: add, set cover, remove; the illustration tile
  until a photo exists.
- More menu "Add photo" (detail page): add via an `AppController`
  addition and the SheetHost actions list.

### 5. Google Drive backup (M6)
Owns `ui/backup/**`, `platform/backup/**`, the backup format in `:core`
(e.g. `BackupSnapshot.kt`).
- Format: one `.zip` per backup with `backup.json` (versioned: recipes,
  settings, photo list) and `photos/`. Versioned from day one; tests.
- Auth: `Identity.getAuthorizationClient` with the `drive.file` scope
  (needs the user's Google Cloud OAuth client: package
  `com.blackchapel.recipai` + the release SHA-1). Account email from the
  Drive `about` endpoint.
- Drive REST v3 over HttpURLConnection: a "Recipai backups" folder,
  multipart/resumable upload, list, download, delete old ones (keep the
  newest few).
- Backup page per the design: not connected (explainer, Connect),
  connected (account + Disconnect, last backup + size, Back up now with
  progress, schedule Daily / Weekly with weekday / Only when I tap, time
  of day + next run, Include photos, Wi-Fi only, the six most recent
  backups with Restore). Restore first takes a "Before restore" backup.
- WorkManager periodic work for Daily/Weekly with the Wi-Fi constraint.
- `BackupSettingsSection`: the Google Drive row with status and Back up
  now. Search "Back up now" / "Backup settings" actions.

### 6. Polish: haptics, gestures, motion
Owns `haptics/**`, `ui/components/Controls.kt`, `ui/components/Chrome.kt`
(haptic choices only), `ui/library/**` (gestures/motion), the page
transition in `ui/RecipaiApp.kt` (back gesture only).
- **User feedback: vibrations feel too weak overall; the Import (center)
  and + buttons feel too strong.** Rework `Haptics.kt` to use
  `Vibrator` with `VibrationEffect` compositions/primitives (API 30+,
  with per-type scale) or predefined effects (API 29+), falling back to
  `performHapticFeedback`; respect the system touch-feedback setting
  and the in-app switch. Make everyday feedback a little stronger and
  the Import/+ buttons lighter (adjust their `HapticType`; update the
  DESIGN.md table via the hand-back).
- Swipe a list row left: Favorite and Delete (Delete with Undo), a
  selection tick passing the action point.
- Back gesture: the pushed page follows the finger (`PredictiveBackHandler`
  in activity-compose 1.9), with the system back still working.
- Card/row entrance motion like the prototype's `rise` (staggered),
  only on first appearance.

## Coordinator

Merges the module branches into `feature/app-scaffold` (order: polish,
link import, timers/cook, photos, editor, backup), resolves conflicts
(especially `AppController`, `RecipaiApp`, `DetailScreen`,
`SettingsScreen`, the manifest), makes the modules work together (e.g.
editor + photo strip, timers + cook mode, backup including photos),
removes leftover stubs/placeholders, runs the desktop check and all
tests, and updates `README.md`, `docs/SYSTEM_STATE.md`,
`docs/KNOWN_GAPS.md` and `docs/DESIGN.md` from the modules' hand-backs.

## Release gate

1. Test Release on `feature/app-scaffold` builds (the real Android
   compile); fix until green.
2. An independent reviewer (not one of the builders) reviews the whole
   app: Android compile/runtime risks, correctness, design conformance,
   security/privacy (network, Drive tokens, files). Fixes, then Test
   Release again.
3. The user tests the APK. Only on their go-ahead: PR into `main`
   (label `feature`), then the user runs Production Release.
