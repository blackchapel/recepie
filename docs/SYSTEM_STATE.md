# Recipai -- system state

A technical snapshot of what actually exists in this repo right now.
`README.md` is the user/build-facing doc; this one is for picking up
the codebase quickly. Keep it current alongside every change (see
`CLAUDE.md`).

## What exists today

The first app milestone (M1): a working library with live scaling and
paste-text import, on sample data plus whatever the user imports.

- **Recipes / Favorites tabs:** large collapsing title, search field,
  dismissible "example recipes" banner, tag chips with counts plus
  "Under 30 min", sort (Recent / A to Z / Quickest), grid/list toggle
  (remembered), favorite hearts, empty states. Press and hold a card or
  row for a preview action sheet (Open, Favorite, Delete).
- **Recipe detail:** generated plate illustration with parallax, glass
  top bar that turns solid on scroll, tags (tap one to filter Recipes),
  title, description, source, stats (time, servings with - / +, items),
  scale presets (½× to 3×), "Scaled 1.5× to match ..." banner with
  Reset, ingredient check-off, step done-marking, scaled step times with
  the original struck through and the scaling mode, notes. More menu:
  favorite, delete (confirm sheet, then Undo in a toast).
- **Amount sheet:** tap any amount, type or step a new value; the
  preview shows the factor, servings and total time; Scale recipe
  applies it. Quick chips for ½×, Original, 2×, 3×.
- **Import sheet** (center tab button): Paste text (clipboard button,
  "Try a sample", free typing) with a live parsed preview (ingredients,
  steps, timers, servings, tags, skipped clutter), Save opens the new
  recipe. The "From a link" tab only explains that link import is
  coming (see `KNOWN_GAPS.md`).
- **Search tab:** browse tiles (Under 30 minutes, Favorites, one per
  tag); with a query, Recipes (title, then ingredient "Uses ...", then
  tag matches), Tags, and Actions (New recipe, Import from text / link).
- **Settings tab:** theme (Auto / Light / Dark), haptic feedback switch,
  recipe count, Restore example recipes, About with the version name.
- **Persistence:** recipes in one JSON file, settings in DataStore. The
  first launch seeds the 8 example recipes.
- **Haptics** for every interaction, through one wrapper, with the
  in-app switch (see `DESIGN.md`, "Haptics").
- The approved prototype `docs/design/recipai-prototype.html` remains
  the design reference.
- The release pipeline (below), `app/version.properties`
  (`VERSION_NAME=0.1`), and the docs.

Not built yet (in milestone order): the editor (the + button explains
this and offers Import), timers and cook mode, link import / share
target / JSON-LD, photos, Google Drive backup, swipe-to-act rows. See
"Roadmap" below and `KNOWN_GAPS.md`.

## Architecture (as built)

Offline-first, single Activity, Compose UI, two Gradle modules. No
backend and no network use at all yet.

Identifiers: `applicationId` / namespace `com.blackchapel.recipai`,
minSdk 26, target/compile SDK 35, JDK 17. Toolchain: AGP 8.7.2, Kotlin
2.0.21, Gradle 8.9, Compose BOM 2024.10.01.

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose. Material 3 only as the base (sheets, menus); every visible color/type comes from the design tokens in `ui/theme` |
| Navigation | A small hand-rolled stack in `AppController`: four tab roots (kept with `rememberSaveableStateHolder`), pages pushed over them with `AnimatedContent` (slide in from the right, the tab underneath shifts left and dims), system back via `BackHandler` |
| State | `AppController`: Compose snapshot state owned by the activity's `MainViewModel`, so it survives rotation. Each pushed `DetailPage` carries its own scale factor and checklists |
| Storage | `RecipeRepository`: all recipes in memory, saved to `filesDir/recipes.json` (`RecipeJson`, versioned, kotlinx.serialization) with an atomic temp-file-then-move write on a single-threaded dispatcher. An unreadable file is renamed aside, never overwritten. Settings in DataStore Preferences (`DataStoreSettings`) |
| DI | None; `MainViewModel` builds the two repositories. Add Hilt only if the graph grows |
| Sheets | `ModalBottomSheet` wrapped in `RSheet` (grabber, title, close; drag down or scrim to dismiss; light haptic) |
| Haptics | `haptics/Haptics.kt` maps the design's six types to `HapticFeedbackConstants` via `View.performHapticFeedback` (no VIBRATE permission). Compose's own `LocalHapticFeedback` is replaced with a no-op so nothing bypasses the switch |
| Fonts | Bricolage Grotesque, Geist and Geist Mono bundled as variable fonts in `res/font` (OFL, licenses in `third_party/fonts/`), so they work offline from first launch |
| Icons | The prototype's line icons as vector drawables (`res/drawable/ic_*.xml`), used through `Ico` / `RIcon` |
| Illustrations | `RecipeArt`: a seeded Compose `Canvas` plate (table, plate, a base, and items picked from ingredient names), simpler than the prototype's but stable per recipe |
| Import | `TextRecipeParser` in `:core` behind the `RecipeImporter` interface. Rule-based only (see "AI / LLM features") |

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
- `TextRecipeParser.kt`, `RecipeImporter.kt` -- the importer and its
  interface; `Importing.kt` turns a parse into a `Recipe`;
  `ImportSamples.kt` holds two realistic copied pages ("Try a sample",
  tests).
- `Library.kt` -- filter / sort / tag counts / search ranking.
- `Tags.kt` -- tag normalizing and stable colors.
- `RecipeJson.kt` -- the versioned recipe file format.
- `SampleRecipes.kt` -- the 8 examples, generated from the prototype.

`app/src/main/java/com/blackchapel/recipai/`

- `MainActivity.kt` -- edge-to-edge, provides haptics, hosts
  `RecipaiApp`. `MainViewModel.kt` -- owns `AppController`.
- `data/` -- `RecipeRepository` (JSON file), `Settings.kt`
  (`AppSettings`, `SettingsStore`), `DataStoreSettings`.
- `haptics/` -- `HapticType`, `LocalHaptics`, the Android `Haptics`
  mapping.
- `ui/AppController.kt` -- tabs, page stack, filters, open sheet, toast,
  actions (favorite, delete with undo, save import, restore examples).
- `ui/RecipaiApp.kt` -- the root: tab roots, pushed pages, tab bar, +
  button, toasts, the sheet host.
- `ui/Platform.kt` -- `BackHandler` and status-bar icon color (the only
  Android-specific UI calls).
- `ui/library/`, `ui/detail/` (detail page, amount sheet), `ui/search/`,
  `ui/settings/`, `ui/importer/`.
- `ui/components/` -- controls (buttons, segmented control, chips,
  tags, search field, cells, switch), chrome (top bar, large title, tab
  bar, + button, toasts), sheets (action sheet, delete confirm),
  `RecipeArt`, icons.
- `ui/theme/` -- color tokens (dark/light), type scale, fonts, theme.

Unit tests: `core/src/test` (scaling, quantities, parser, tags, library,
JSON) and `app/src/test` (`RecipeRepositoryTest`: first launch,
persistence across restarts, undo, restore examples, unreadable file).

## Roadmap

- **M2 -- Editor:** create/edit recipes (modal page from the bottom),
  sections, reorder, detected-time chips, validation; wire the + button
  and Edit actions.
- **M3 -- Timers and cook mode:** tap a step time to start a timer
  (foreground service + notification so it survives screen off), timers
  sheet, cook mode with keep-screen-on and timer sound settings.
- **M4 -- Link import:** fetch a page, read schema.org `Recipe` JSON-LD,
  fall back to the text rules; `ACTION_SEND` share target; the
  "From a link" tab.
- **M5 -- Photos:** photo picker, cover photo, viewer.
- **M6 -- Google Drive backup:** the Backup page from the design.
- Gestures still to add: swipe a list row for Favorite / Delete.

## Reference implementations ported from the prototype

The prototype's JavaScript scaling engine and text importer were ported
to `:core` with unit tests. Two prototype bugs were fixed on the way: a
vulgar fraction with no whole number swallowed the space before it, and
the "drinks" tag fired on any recipe whose steps mention lemon juice
(it now only comes from the title).

## AI / LLM features: deferred

Decision: import is rule-based for now, with AI added later. That
means no AI API key and no server, and the app works fully offline.
Today import is the text rules (`TextRecipeParser`); M4 adds a JSON-LD
reader in front of them. Nothing calls a language model.

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

Signing and secrets: see `RELEASING.md`.
