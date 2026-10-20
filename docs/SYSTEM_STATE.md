# Recipai -- system state

A technical snapshot of what actually exists in this repo right now.
`README.md` is the user/build-facing doc; this one is for picking up
the codebase quickly. Keep it current alongside every change (see
`CLAUDE.md`).

## What exists today

All six milestones of `docs/BUILD_PLAN.md` are built and merged on
`feature/app-scaffold` (M1 library and scaling, M2 editor, M3 timers and
cook mode, M4 link import, M5 photos, M6 Google Drive backup, plus the
polish module: haptics, gestures, motion). None of it has been compiled
by the Android toolchain or run on a device since the modules landed;
the next step is the release gate in `BUILD_PLAN.md` (Test Release,
independent review, the user's device test).

- **Recipes / Favorites tabs:** large collapsing title, search field,
  tag chips with counts plus
  "Under 30 min", sort (Recent / A to Z / Quickest), grid/list toggle
  (remembered), favorite hearts, empty states. Cards and rows play the
  prototype's `rise` entrance (staggered) on the collection's first
  appearance and after a layout switch; later adds, removes and
  reorders animate item placement. **Swipe rows** (list layout): swipe
  left to reveal Favorite and Delete (84 dp each, rubber band, a
  selection tick crossing the action point, one row open at a time,
  TalkBack custom actions, mirrored in RTL); Delete collapses the row,
  then deletes with Undo. Press and hold a card or row for the preview
  sheet: Open, Start cooking, Edit, Add photo, Favorite, Delete.
- **Recipe detail:** hero with the cover photo (or the generated plate
  illustration) and parallax, a photo-count badge, and an entrance
  animation once per visit (the illustration plays the prototype's
  `plate-in`; a photo eases in from 1.1×); glass top bar; tags (tap to
  filter Recipes), title, description, source line, stats (time,
  servings with - / +, items); a photo strip (thumbnails pick the hero
  photo or open the viewer, Add photo tile, spinner tiles while copying);
  scale presets (½× to 3×) and the "Scaled 1.5× to match ..." banner;
  ingredient check-off; steps with done-marking, live **step time chips**
  (tap to start, pause/resume, Done, clear) showing scaled times with the
  original struck through and the scaling mode; notes. A sticky bottom
  bar: the timers button (soonest countdown, count badge, Done state) and
  the flame **Start cooking** button. More menu: Edit recipe, Add photo,
  Add/Remove favorite, Delete (confirm sheet naming the photo count, then
  Undo in a toast). Items run hero, head, stats, photos, ingredients,
  method, notes. `DetailPage` keeps `scrollIndex` / `scrollOffset` and
  `DetailScreen` restores them after cook mode or the editor covered it.
- **Amount sheet:** tap any amount, type or step a new value; the
  preview shows the factor, servings and total time; Scale recipe
  applies it. Quick chips for ½×, Original, 2×, 3×.
- **Editor** (modal page sliding up; from +, Search "New recipe", Edit
  recipe, and import Review): Cancel / Save top bar with a "Discard your
  changes?" sheet when dirty (also on system back); the photo strip (add,
  set cover, remove; the illustration until a photo exists); Basics
  (title required, description, servings, "Counted as"); Tags (chips,
  Enter or comma adds, up to 8 suggestions from the recipe text, the
  library and common tags); Ingredients (quantity, unit with unit-chip
  suggestions, name, note; section headings; move up/down; remove;
  multi-line paste turns into rows); Method (text, minutes, a scaling
  mode pill opening a sheet; "Found 12 min · use it" chip; move; remove);
  Notes. Save validates inline (title, amounts, amount-without-name,
  minutes), focuses the first problem, stores through
  `RecipeRepository.upsert` with a fresh `updatedAt`, and slides down
  onto the recipe's detail page. The draft survives rotation. Photos
  picked in the editor are copied in `AppController.photoScope` and
  merged by `EditorState.addPhotos` into the live draft;
  `EditorState.photoAdding` holds the pending tiles and the error, so
  they survive rotation. Saving a shared import (`saveImport`,
  `saveLinkImport`) while an editor is open keeps the stack and pushes
  the detail page over the editor (Back returns to the draft); `showTag`
  over an open editor only toasts "Save or discard your draft first."
  The mini-timer pill still floats over the editor and Backup page;
  `pushedPageBottomPadding` (`ui/timers/TimerParts.kt`, sharing
  `PushedPillOffset`, 84 dp, with `TimerOverlay`) gives them clearance.
- **Timers and cook mode:** timers are process-wide (`TimerStore`), shown
  in the Timers sheet (ring, pause/resume, +1 min, cancel) and the
  mini-timer pill (above the tab bar, beside + on Recipes/Favorites,
  84 dp up on pushed pages, hidden on detail and in cook mode). Screens
  with long lists leave room for the pill while a timer exists. A
  finishing timer plays the warning haptic, a toast, the prototype's
  chime on the alarm stream (Timer sound on) and a heads-up "Time's up"
  notification. A `specialUse` foreground service with an ongoing
  chronometer notification keeps timers running with the screen off.
  **Cook mode:** progress segments, one step large, the step's scaled
  ingredients as chips, the big timer button (shared with the detail
  chip), swipe or buttons, Finish on the last step; marks steps done on
  the detail page underneath; keeps the screen on (setting).
- **Import sheet** (center tab button): **Paste text** (clipboard button,
  "Try a sample", free typing) with a live parsed preview; **From a
  link**: URL field, Paste link, Convert, the five-step progress list,
  error box with Paste text instead / Edit the page text / Try again, a
  "Share links straight to Recipai" card, and a saffron note when the
  recipe came from page text rather than a recipe card. Save keeps the
  source and opens the recipe; Review opens the editor. A link shared to
  Recipai opens this tab and converts at once (medium haptic).
- **Search tab:** browse tiles (Under 30 minutes, Favorites, one per
  tag); with a query, Recipes (title, then ingredient "Uses ...", then
  tag matches), Tags, and Actions (New recipe, Import from text / link,
  Back up now, Backup settings).
- **Settings tab:** Backup (Google Drive row with live status, Back up
  now), Appearance (theme), Cooking (Haptic feedback, Keep screen on in
  cook mode, Timer sound), Library (recipe and photo counts), About with
  the version name.
- **Backup page** (pushed from Settings): not connected (explainer with
  floating recipe pictures, Connect Google account, a waiting state
  while Google's consent screen is up); connected (account row with
  Connected / Sign in again and Disconnect behind a confirm; Last backup
  card with counts, estimated size, Back up now with progress; Automatic
  backups Daily / Weekly with a weekday picker / Only when I tap, time of
  day Overnight / Morning / Evening and the next run; Include photos and
  Wi-Fi only; the six most recent backups with Restore behind a
  confirm).
- **Back navigation:** system back closes a sheet, pops pushed pages,
  then returns to Recipes (the home tab) from any other tab; back on
  Recipes exits. Only sideways pages (detail, Backup) shift the tab
  behind them; the editor and cook mode slide up over a still page.
  Going back is silent.
- **Back gesture:** on Android 14+ a sideways page (detail, Backup) over
  a tab follows the finger during the system back swipe, and the tab
  underneath slides back with its dim fading. The modal editor, cook
  mode and deeper stacks pop normally on release.
- **Persistence:** recipes in one JSON file, photos in
  `filesDir/photos`, settings in DataStore, backup status in
  `filesDir/backup-status.json`. The library starts empty: the app ships
  no sample recipes, and `RecipeRepository.withoutExamples` strips any
  old sample recipes (without photos) that earlier builds saved.
- **Haptics** for every interaction, through one wrapper, with the
  in-app switch and the system Touch feedback setting (see
  `DESIGN.md`, "Haptics").
- The approved prototype `docs/design/recipai-prototype.html` remains
  the design reference.
- The release pipeline (below), `app/version.properties`
  (`VERSION_NAME=0.1`; nothing has been released yet, so the modules'
  MINOR bumps are folded into the first release), and the docs.

## Architecture (as built)

Offline-first, single Activity, Compose UI, two Gradle modules. No
backend. The network is used only to fetch a page the user asked to
import and for Google Drive backup.

Identifiers: `applicationId` / namespace `com.blackchapel.recipai`,
minSdk 26, target/compile SDK 35, JDK 17. Toolchain: AGP 8.7.2, Kotlin
2.0.21, Gradle 8.9, Compose BOM 2024.10.01. Libraries: activity-compose
1.9.2, lifecycle 2.8.6, core-ktx 1.13.1, DataStore 1.1.1, Coil 2.7.0,
ExifInterface 1.3.7, play-services-auth 21.2.0, WorkManager 2.9.1.

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose. Material 3 only as the base (sheets, menus); every visible color/type comes from the design tokens in `ui/theme` |
| Navigation | A small hand-rolled stack in `AppController`: four tab roots (kept with `rememberSaveableStateHolder`), pages pushed over them with `AnimatedContent` (detail and Backup slide in from the right with the tab underneath shifting left and dimming; the editor and cook mode slide up). System back for pages goes through `PageBackHandler` (`platform/nav`, `PredictiveBackHandler`); on Android 14+ a sideways page over a tab follows the finger (`PageBackSwipe` in `ui/BackGesture.kt`); the manifest sets `enableOnBackInvokedCallback`. Page-level `BackHandler`s (editor, photo viewer) are registered later and take priority |
| State | `AppController`: Compose snapshot state owned by the activity's `MainViewModel`, so it survives rotation (the recipes themselves are not owned there: see Storage). Each pushed `DetailPage` carries its own scale factor and checklists; each `EditorPage` its draft (`EditorState`); the link conversion (`LinkImport`) and the backup controller live there too. Module members sit in `// ---- <module> ----` sections at the end of the class |
| Storage | `RecipeRepository`: all recipes in memory, saved to `filesDir/recipes.json` (`RecipeJson`, versioned, kotlinx.serialization) with an fsync'd temp-file-then-atomic-move write on a single-threaded dispatcher. **One repository per process:** `platform/storage/RecipeRuntime` holds it (like `BackupRuntime.jobs`), built lazily from the application context on a process-lifetime scope (`SupervisorJob` + `Dispatchers.Main.immediate`) with `Dispatchers.IO` as the writer, so queued saves outlive the activity and two ViewModels never hold two copies of the file. `MainViewModel` / `AppController` only view it; tests construct `RecipeRepository` directly. **Durable writes:** `core/Durable.kt` fsyncs before every rename into place (`recipes.json`, picked photos, restored photos, `backup-status.json`). An unreadable file is renamed aside, never overwritten. `replaceAll` for restore. Settings in DataStore Preferences (`DataStoreSettings`) |
| Photos | `filesDir/photos/<uuid>.jpg`, written through a temp file then renamed (`PhotoStore`), decoded with a sample size, EXIF-uprighted, scaled to 2048 px max and saved as JPEG 88 (`PhotoImporter`). `Recipe.photos` holds the names, `coverIndex` the cover. Loaded with Coil at the drawn size. Unreferenced files are deleted once per process (`RecipeRepository.claimPhotoCleanUp`) after the recipes load, never when the recipe file was unreadable, never under an hour old, never names the app didn't write |
| Timers | In memory and process-wide (`TimerStore.shared`, flows), kept alive by the `TimerService` foreground service (specialUse, partial wake lock only while a timer runs). Whoever ticks first (the on-screen ticker every 250 ms, or the service loop) finishes a due timer, and it is reported exactly once |
| Link import | `WebPageFetcher` (java.net, in `:core` so it is unit-tested on the JVM; `platform/net/PageFetcher` runs it on `Dispatchers.IO` with a socket stats tag), with a 45 s overall deadline (a daemon watchdog disconnects the connection; `FetchAbort`, which `PageFetcher` triggers when the calling coroutine is cancelled), then `PageRecipeReader`: the JSON-LD recipe card, else the page text through the text rules, else "no recipe" |
| Backup | One `.zip` per backup (`backup.json` + `photos/`), Drive REST v3 over `HttpURLConnection` with the `drive.file` scope, authorization through Play services' `AuthorizationClient` (no stored tokens), WorkManager periodic work for automatic backups. Details below |
| DI | None; `MainViewModel` gets the process-wide repository from `RecipeRuntime` and attaches the backup runtime. Add Hilt only if the graph grows |
| Accessibility | `Modifier.tap` has `description` (the node's content description, the name TalkBack reads), `isSelected` and `isChecked`; `label` is only the click-action hint ("double-tap to ..."). Icon-only controls pass `description`; `IconButton` and `GlassButton` name themselves from their label. `Segmented` has `itemLabel`, `FilterChip` has `toggle` (false for plain buttons such as the quick scale amounts), `Cell` has `checked`; switch rows, ingredient rows and step buttons report checked, segmented items and tag chips report selected. Not yet checked with a real TalkBack |
| Sheets | `ModalBottomSheet` wrapped in `RSheet` (grabber, title, close; drag down or scrim to dismiss; light haptic). The photo viewer is a full-screen overlay with its own back handler |
| Haptics | `haptics/Haptics.kt` drives the `Vibrator` directly: tuned `VibrationEffect` compositions (API 30+, when the primitives are supported), predefined effects (API 29), `View.performHapticFeedback` fallback (API 26-28 or no vibrator). Touch usage, so the system's touch intensity applies; needs VIBRATE (declared); respects the in-app switch and the system Touch feedback setting. The tuning is data in `HapticPatterns.kt`. Compose's own `LocalHapticFeedback` is a no-op so nothing bypasses the switch |
| Fonts | Bricolage Grotesque, Geist and Geist Mono bundled as variable fonts in `res/font` (OFL, licenses in `third_party/fonts/`), so they work offline from first launch |
| Icons | The prototype's line icons as vector drawables (`res/drawable/ic_*.xml`), used through `Ico` / `RIcon` |
| Illustrations | `RecipeArt`: a seeded Compose `Canvas` plate (table, plate, a base, and items picked from ingredient names), simpler than the prototype's but stable per recipe; shown wherever a recipe has no (readable) cover photo |
| Import | `TextRecipeParser` in `:core` behind the `RecipeImporter` interface, with the JSON-LD reader in front of it for links. Rule-based only (see "AI / LLM features") |

### Backup in detail

- `:core`: `BackupSnapshot` / `BackupFormat` (`backup.json`: app
  "recipai", format version 1, createdAt, appVersion, kind, recipes,
  settings, photo list, includesPhotos; unknown keys ignored; a newer
  version is refused with a message), `BackupArchive` (zip with
  `backup.json` then `photos/`, safe photo names, 64 MB per-entry cap,
  temp-file-then-rename extraction), `HttpTransport` /
  `UrlConnectionTransport`, `DriveClient` (about, folder find/create,
  resumable upload that resumes after a dropped connection, list,
  download, delete, revoke; backoff retries on 429/5xx/network errors;
  error-to-message mapping such as "Your Google Drive is full"),
  `BackupEngine` (pack, upload, verify size and MD5, keep the newest 6,
  download, restore with missing photos dropped and the cover
  re-pointed), `BackupStatusStore` (`backup-status.json`: account name
  and email, folder id, last backup, fingerprint, cached list, last
  error, needsReconnect, schedule signature; no tokens), `BackupTiming`
  (next run, period, signature) and `BackupText` (the prototype's
  wording).
- App: `ui/backup/BackupJobs` is the process-wide runner (one mutex for
  the page and the worker, progress `StateFlow`, clear-and-retry-once on
  a refused token, failures recorded). `BackupController` (in
  `AppController`'s backup section) holds the page's state, actions and
  the consent bridge. `BackupHost` at the root plays backup haptics and
  shows Google's consent screen. `platform/backup` has
  `AndroidBackupPlatform` / `BackupRuntime` (AuthorizationClient, files,
  WorkManager), `BackupConsentHost`, `BackupScheduler` (unique periodic
  work "recipai-automatic-backup" plus the one-time catch-up
  "recipai-catch-up-backup") and `BackupWorker`.
- Restore downloads and checks the backup, then reads the current
  recipes and settings (after the download, so edits made while it
  downloaded are kept), saves a "Before restore" backup (always with
  photos; `BackupEngine.backUp(protect = ...)` never prunes the backup
  being restored), extracts photos into `filesDir/photos`
  before replacing the recipes (so the photo clean-up never sees them as
  orphans), then applies the appearance and cooking settings. The phone
  keeps its own backup options.
- `BackupFingerprint.of(recipes, includePhotos)`: photos on is the plain
  recipe hash (stored fingerprints stay valid), photos off appends
  `|no-photos`, so turning Include photos on counts as a change.
  `BackupEngine` clears leftover `backup-*.zip` at the start of
  `backUp` and `restore-*.zip` at the start of `download`;
  `Photos.TEMP` also matches `.jpg.restore.tmp`.
- Automatic backups skip the upload when the recipes haven't changed.
  When the app opens and a schedule is overdue by more than its period
  plus 2 hours, one catch-up backup is queued.
- Network: `www.googleapis.com` and `oauth2.googleapis.com`, with the
  user's token only. Tokens never touch disk or logs.

### Backup and device transfer

`allowBackup="false"` turns off Android cloud backup.
`res/xml/data_extraction_rules.xml` (manifest `dataExtractionRules`)
excludes every domain from cloud backup and, for a phone-to-phone
transfer, excludes `backup-status.json` (and its `.tmp`) only, so
recipes, photos and settings move but the new phone starts as "not
connected" to Drive.

### Permissions

INTERNET, ACCESS_NETWORK_STATE (link import, Drive), POST_NOTIFICATIONS
(asked once, the first time a timer starts, Android 13+),
FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE (the timer
service), WAKE_LOCK (timers with the screen off), VIBRATE (haptics). No
storage permission: photos come through the system photo picker.

## Module map

`core/` -- **pure Kotlin JVM module, no Android imports.** Unit-tested
by `./gradlew :core:test`.

- `Model.kt` -- `Recipe`, `Ingredient`, `Step`, `TimeScale`
  (serializable).
- `Quantities.kt` -- parse `2`, `1.5`, `1/2`, `1 1/2`, `1½`; format as
  fractions.
- `Scaling.kt` -- scaled amounts with tsp/tbsp/cup and g/kg, ml/l steps
  and plurals; factor from a typed amount; step-time scaling (fixed,
  partial = square root, linear); time/servings formatting.
- `TextRecipeParser.kt` (with `canonicalUnit`), `RecipeImporter.kt`
  (`ParsedRecipe`, now with `source` and `servingsLabel`) -- the text
  importer and its interface; `Importing.kt` turns a parse into a
  `Recipe`; `ImportSamples.kt` holds two realistic copied pages.
- Link import: `HtmlText.kt` (entities, tag stripping, JSON-LD blocks,
  visible page text, found by a linear `indexOf` scan because Android's
  ICU regex overflows on lazy spans), `JsonLdRecipeReader.kt` (recipe
  card reader plus `Durations` and `RecipeCardTags`),
  `PageRecipeReader.kt` (card, else page text, else none;
  `LinkMessages`), `WebPageFetcher.kt` (browser User-Agent, 15/20 s
  timeouts, 45 s overall deadline, 4 MB cap, 6 redirects followed by hand, http upgraded to
  https, gzip, charset; `FetchResult` / `FetchFailure`), `Links.kt`
  (find, normalize and describe links).
- `RecipeDraft.kt` -- the editor's draft as typed: validation,
  `toRecipe`, pasted lines, reorder, the detected-time chip, tag
  suggestions.
- `Timers.kt` (`CookTimer`, `TimerBoard`, `StepTap`: the immutable board
  and the prototype's tap cycle), `IngredientUses.kt` (which ingredients
  a step uses), `Chime.kt` (PCM chime synth).
- `Photos.kt` (`PhotoSet` and cover bookkeeping, `isPhotoName`, sample
  size and fit-within, EXIF orientation, `unreferenced()` with a 1 h
  grace, `PhotoImport`, `Recipe.coverPhoto` / `photoSet` / `withPhotos`),
  `PhotoStore.kt` (the photo folder in java.io: newPhoto/commit via a
  temp file, delete, cleanUp).
- Backup: `BackupSnapshot.kt`, `BackupArchive.kt`, `BackupEngine.kt`,
  `BackupStatus.kt`, `BackupText.kt`, `BackupTiming.kt`,
  `DriveClient.kt`, `HttpTransport.kt` (see "Backup in detail").
- `Durable.kt` -- `Durable.sync` / `Durable.write`: fsync before a rename.
- `Library.kt` -- filter / sort / tag counts / search ranking.
- `Tags.kt` -- tag normalizing and stable colors.
- `RecipeJson.kt` -- the versioned recipe file format.
- `SampleRecipes.kt` (in `core/src/test`) -- recipes generated from the
  prototype, used only as test fixtures. The app ships no sample
  recipes; the library starts empty, and files written by earlier
  builds have their saved examples removed on load.

Note: `:core` does network I/O (plain `java.net`) for link import and
Drive, so it can be unit-tested on the JVM; the plan had put fetching in
`platform/net`, which is now a thin Android wrapper.

`app/src/main/java/com/blackchapel/recipai/`

- `MainActivity.kt` -- edge-to-edge, provides haptics, hosts
  `RecipaiApp`, routes `ACTION_SEND` text to `handleSharedText`. It is
  `launchMode="singleTask"`: shares and timer-notification taps reach
  the one instance in `onNewIntent` (which calls `setIntent`); a SEND
  intent with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (the root intent
  re-delivered from Recents) is ignored, and `EXTRA_TEXT` is read as a
  `CharSequence` (styled text). `MainViewModel.kt` -- owns
  `AppController` over the shared repository, attaches the backup
  runtime.
- `data/` -- `RecipeRepository` (JSON file), `Settings.kt`
  (`AppSettings`, `SettingsStore`), `DataStoreSettings`.
- `haptics/` -- `HapticType`, `LocalHaptics`, `HapticPatterns` (the
  tuning table), the Android `Haptics`.
- `ui/AppController.kt` -- tabs, page stack, filters, open sheet, toast,
  actions, and the module sections (link-import, timers-cook, photos,
  editor, backup).
- `ui/RecipaiApp.kt` -- the root: tab roots, pushed pages, the back
  swipe, tab bar, + button, mini timer, photo host, toasts, the sheet
  host (actions, timers, photo viewer, delete confirm), backup host.
  `ui/BackGesture.kt` -- `PageBackSwipe`.
- `ui/Platform.kt` -- `BackHandler` and status-bar icon color.
- `ui/library/` (`LibraryScreen`, `SwipeRow`), `ui/detail/` (detail page,
  amount sheet), `ui/editor/` (`EditorScreen`, `EditorState`,
  `EditorFields`, `EditorRows`), `ui/timers/` (`TimerStore`, chip,
  sheet, overlay, parts), `ui/cook/` (`CookScreen`, `DetailBottomBar`,
  `CookingSettingsRows`), `ui/importer/` (`ImportSheet`, `LinkImport`
  state machine, `LinkSource` tab UI), `ui/photos/` (`RecipeImage`,
  `DetailHero`, `DetailPhotoStrip`, `EditorPhotoStrip`, `PhotoViewer`,
  `PhotoHost`, `PhotoTiles`), `ui/backup/` (`BackupController`,
  `BackupJobs`, `BackupScreen`, `BackupParts`, `BackupSettingsSection`,
  `BackupHost`, `BackupPlatform`), `ui/search/`, `ui/settings/`.
- `ui/components/` -- controls, chrome (top bar, large title, tab bar,
  + button, toasts), sheets (action sheet, delete confirm), `Motion.kt`
  (`rise`, `rememberRiseStart`, `EaseOutSoft`, `EaseNav`), `RecipeArt`,
  icons.
- `ui/theme/` -- color tokens (dark/light), type scale, fonts, theme.
- `platform/` (Android-only, never compiled by the desktop check) --
  `nav/PageBackHandler`, `net/PageFetcher`, `timers/` (`TimerSystem` and
  `KeepScreenOn`, `TimerService`, `TimerNotifications`, `TimerChime`),
  `photos/` (`PhotoPicker`, `PhotoImporter`, `PhotoImage`,
  `PhotoStorage`), `storage/RecipeRuntime`, `backup/` (`AndroidBackupPlatform` / `BackupRuntime`,
  `BackupConsentHost`, `BackupScheduler`, `BackupWorker`).

`tools/desktop-check/` -- compiles the shared code against Compose
Desktop 1.7.0 and runs `:core` and `app/src/test` on the JVM, for
sandboxes without the Android SDK. Android-only files are excluded from
the app's sources; each one shared code calls has a same-API stub at the
same package path under `stubs/`.

Unit tests: `core/src/test` (scaling, quantities, parser, tags, library,
JSON, recipe draft, timers, ingredient uses, chime, photos, photo store,
JSON-LD reader, page reader, HTML text and links, page fetcher against a
local HTTP server, backup archive, engine, snapshot, timing, Drive
client against a fake Drive) and `app/src/test` (`RecipeRepositoryTest`,
`HapticPatternsTest`, `RowSwipeTest`, `EditorFlowTest`,
`LinkImportTest`, `TimerStoreTest`, `BackupJobsTest`,
`IntegrationFlowTest`); the `:core` and `:ui` totals are 171 and 60.

## Roadmap

- **M1 -- Library, detail with live scaling, paste import:** done.
- **M2 -- Editor:** done.
- **M3 -- Timers and cook mode:** done.
- **M4 -- Link import and share target:** done.
- **M5 -- Photos:** done.
- **M6 -- Google Drive backup:** done.
- **Polish -- haptics, swipe rows, back swipe, entrance motion:** done.
- Next: the release gate (Test Release, independent review, device
  test), then fixes from it. Later candidates are in `KNOWN_GAPS.md`.

## Reference implementations ported from the prototype

The prototype's JavaScript scaling engine, text importer, ingredient-use
matcher, timer tap cycle and chime were ported to `:core` with unit
tests. Prototype bugs fixed on the way: a vulgar fraction with no whole
number swallowed the space before it; the "drinks" tag fired on any
recipe whose steps mention lemon juice (it now only comes from the
title); the detail hero defaulted to the first photo rather than the
cover; the ingredient matcher missed an ingredient's own plural.

## AI / LLM features: deferred

Decision: import is rule-based for now, with AI added later. That
means no AI API key and no server, and the app works fully offline.
Import is the text rules (`TextRecipeParser`) with a JSON-LD recipe-card
reader in front of them for links. Nothing calls a language model. (The
network is used only to fetch a page the user asked to import and for
Drive backup.)

LLM support (for example importing messy text or photos of recipe
cards, tag suggestions, substitutions) may be added later. When it is:

- `RecipeImporter` (in `:core`) is the extension point: rules first, an
  LLM only as a fallback or opt-in step.
- A cloud model's API key must **never** ship inside the APK (it can be
  extracted). It needs a small proxy server that holds the key and
  rate-limits per user; that server and its secret are new
  infrastructure, outside this repo's current pipeline.
- An on-device model (Gemini Nano via Android's AI APIs) needs no key
  but only runs on recent high-end phones, so it can only ever be an
  enhancement.

## Release pipeline (as it exists)

Two manual (`workflow_dispatch`) workflows, mirroring the RideLink repo.
Neither runs automatically; merging to `main` releases nothing.

**Test Release** -- `.github/workflows/build-apk.yml`
- Input: `branch`. Builds a signed APK from that branch.
- Version name: `<MAJOR.MINOR>-<branch>.<shortsha>`; version code: the
  commit count of that branch.
- Runs `:core:test testDebugUnitTest`, then `assembleRelease`.
- Uploads the APK as a workflow artifact named
  `recipai-test-<branch>-<version>`. Does not tag or publish.

**Production Release** -- `.github/workflows/release.yml`
- Fails immediately unless run on `main`.
- Version name: `<MAJOR.MINOR>.<PATCH>` where PATCH is one more than the
  highest existing `v<MAJOR.MINOR>.*` tag (0 if none). Refuses to reuse
  an existing tag. Version code: the commit count of `main`.
- Runs `:core:test testDebugUnitTest`, `assembleRelease`, then creates the GitHub
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
- `./gradlew :core:test testDebugUnitTest` and `./gradlew assembleRelease` work and
  write `app/build/outputs/apk/release/app-release.apk`.

Differences from RideLink's pipeline: the production workflow refuses to
run off `main`; both run unit tests first; both skip Discord when the
webhook secret is absent; the Test Release job has read-only permissions;
the released APK is named `recipai-v<version>.apk` rather than
`app-release.apk`.

Signing and secrets: see `RELEASING.md`. Google Drive backup also needs
the Google Cloud OAuth Android client and the Drive API (same file).
